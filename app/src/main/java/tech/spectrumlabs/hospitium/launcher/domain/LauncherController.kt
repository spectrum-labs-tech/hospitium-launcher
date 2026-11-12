// SPDX-License-Identifier: Apache-2.0

package tech.spectrumlabs.hospitium.launcher.domain

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import tech.spectrumlabs.hospitium.launcher.data.CachedConfig
import tech.spectrumlabs.hospitium.launcher.data.DeviceStore
import tech.spectrumlabs.hospitium.launcher.net.ApiException
import tech.spectrumlabs.hospitium.launcher.net.LauncherApi
import tech.spectrumlabs.hospitium.launcher.protocol.PairingStatus
import java.time.Duration
import java.time.Instant

/** Why the screen is showing something other than fresh content. */
enum class Problem { OFFLINE, RATE_LIMITED, SERVER, TOKEN_ALREADY_TAKEN }

sealed interface LauncherState {
    data object Starting : LauncherState

    /** No control-plane address yet: the setup screen. */
    data class NeedsServer(val suggested: String, val error: String? = null) : LauncherState

    /** Showing a pairing code (null while one is being requested) for an administrator to claim. */
    data class Pairing(
        val serverUrl: String,
        val code: String? = null,
        val expiresAt: Instant? = null,
        val problem: Problem? = null,
    ) : LauncherState

    /** Paired. [cached] is null until the first config arrives; [stale] after a failed refresh. */
    data class Home(val cached: CachedConfig?, val stale: Boolean = false) : LauncherState
}

/**
 * The launcher's whole lifecycle, independent of Android so it is unit-tested with virtual time:
 * setup → pairing → home, and back to pairing if the device is revoked.
 */
class LauncherController(
    private val scope: CoroutineScope,
    private val store: DeviceStore,
    private val apiFor: (serverUrl: String) -> LauncherApi,
    private val now: () -> Instant,
    private val defaultServerUrl: String = "",
    private val allowLocalHttp: Boolean = false,
) {
    private val _state = MutableStateFlow<LauncherState>(LauncherState.Starting)
    val state: StateFlow<LauncherState> = _state.asStateFlow()

    private val wake = Channel<Unit>(Channel.CONFLATED)
    private var job: Job? = null

    fun start() {
        job?.cancel()
        job = scope.launch { run() }
    }

    /** Refresh immediately (e.g. the TV woke from standby) instead of waiting out the interval. */
    fun refreshNow() {
        wake.trySend(Unit)
    }

    /** The setup screen's submit. */
    fun submitServer(input: String) {
        ServerUrl.normalize(input, allowLocalHttp).fold(
            onSuccess = { url ->
                store.saveServerUrl(url)
                start()
            },
            onFailure = { e -> _state.value = LauncherState.NeedsServer(input, e.message) },
        )
    }

    /** Only offered while unpaired: a paired TV is reset by clearing the app's data. */
    fun changeServer() {
        if (store.deviceToken() != null) return
        job?.cancel()
        val previous = store.serverUrl() ?: defaultServerUrl
        store.clearAll()
        _state.value = LauncherState.NeedsServer(previous)
    }

    private suspend fun run() {
        val url = store.serverUrl()
        if (url == null) {
            _state.value = LauncherState.NeedsServer(defaultServerUrl)
            return
        }
        val api = apiFor(url)
        while (true) {
            val token = store.deviceToken() ?: pair(api, url)
            home(api, token)
        }
    }

    /** Requests codes and polls until an administrator claims one; returns the stored token. */
    private suspend fun pair(api: LauncherApi, url: String): String {
        var failures = 0
        var problem: Problem? = null // transient: cleared by the next successful call
        var notice: Problem? = null // sticky: shown alongside the next code
        while (true) {
            _state.value = LauncherState.Pairing(url, problem = problem ?: notice)
            val start = try {
                api.startPairing()
            } catch (e: ApiException) {
                failures++
                _state.value = LauncherState.Pairing(url, problem = classify(e))
                pause(Backoff.afterFailures(failures, PAIRING_RETRY, PAIRING_RETRY_MAX))
                continue
            }
            failures = 0
            problem = null
            var pollFailures = 0
            poll@ while (true) {
                _state.value = LauncherState.Pairing(url, start.code, start.expiresAt, problem ?: notice)
                pause(if (pollFailures == 0) PAIRING_POLL else Backoff.afterFailures(pollFailures, PAIRING_POLL, PAIRING_RETRY_MAX))
                val res = try {
                    api.pairingStatus(start.code, start.pollToken)
                } catch (_: ApiException.Unauthorized) {
                    break@poll // the pairing is gone server-side; get a new code
                } catch (e: ApiException) {
                    pollFailures++
                    problem = classify(e)
                    continue@poll
                }
                pollFailures = 0
                problem = null
                when (res.status) {
                    PairingStatus.PENDING -> continue@poll
                    PairingStatus.CLAIMED -> {
                        val token = res.deviceToken
                        if (token.isNullOrEmpty()) {
                            // Claimed, but the one-time token was already collected (a previous run
                            // crashed before saving it). Unrecoverable here: start a fresh pairing.
                            notice = Problem.TOKEN_ALREADY_TAKEN
                            break@poll
                        }
                        store.saveDeviceToken(token)
                        return token
                    }
                    else -> break@poll // expired, or a status this version doesn't know
                }
            }
        }
    }

    /** Shows the cache, refreshes on the server's interval; returns only when the device is revoked. */
    private suspend fun home(api: LauncherApi, token: String) {
        var cached = store.cachedConfig()
        _state.value = LauncherState.Home(cached)
        var failures = 0
        var refusals = 0
        while (true) {
            val wait = try {
                val cfg = api.config(token)
                val offset = Clocks.offsetAt(cfg.serverTime, now())
                cached = CachedConfig(cfg, offset.toMillis()).also(store::saveCachedConfig)
                failures = 0
                refusals = 0
                _state.value = LauncherState.Home(cached)
                Backoff.refresh(cfg.refreshAfterSeconds)
            } catch (_: ApiException.Unauthorized) {
                // Several consecutive refusals, not one: a single bad response from a degraded
                // server must not wipe a TV's pairing and strand it until someone visits.
                refusals++
                if (refusals >= REVOKED_AFTER) {
                    store.clearPairing()
                    return
                }
                _state.value = LauncherState.Home(cached, stale = true)
                Backoff.afterFailures(refusals, REFRESH_RETRY, REFRESH_RETRY_MAX)
            } catch (_: ApiException) {
                failures++
                _state.value = LauncherState.Home(cached, stale = true)
                Backoff.afterFailures(failures, REFRESH_RETRY, REFRESH_RETRY_MAX)
            }
            pause(wait)
        }
    }

    /** Waits [d], or less if [refreshNow] is called. */
    private suspend fun pause(d: Duration) {
        withTimeoutOrNull(d.toMillis()) { wake.receive() }
    }

    private fun classify(e: ApiException): Problem = when (e) {
        is ApiException.Network -> Problem.OFFLINE
        is ApiException.Http -> if (e.status == 429) Problem.RATE_LIMITED else Problem.SERVER
        else -> Problem.SERVER
    }

    companion object {
        /** Pairing polls are rate limited per IP; several TVs may pair behind one router. */
        val PAIRING_POLL: Duration = Duration.ofSeconds(5)
        val PAIRING_RETRY: Duration = Duration.ofSeconds(5)
        val PAIRING_RETRY_MAX: Duration = Duration.ofMinutes(2)
        val REFRESH_RETRY: Duration = Duration.ofSeconds(15)
        val REFRESH_RETRY_MAX: Duration = Duration.ofMinutes(10)
        const val REVOKED_AFTER = 3
    }
}
