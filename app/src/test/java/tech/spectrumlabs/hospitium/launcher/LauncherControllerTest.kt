// SPDX-License-Identifier: Apache-2.0

package tech.spectrumlabs.hospitium.launcher

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tech.spectrumlabs.hospitium.launcher.data.InMemoryDeviceStore
import tech.spectrumlabs.hospitium.launcher.domain.LauncherController
import tech.spectrumlabs.hospitium.launcher.domain.LauncherState
import tech.spectrumlabs.hospitium.launcher.domain.Problem
import tech.spectrumlabs.hospitium.launcher.net.ApiException
import tech.spectrumlabs.hospitium.launcher.net.LauncherApi
import tech.spectrumlabs.hospitium.launcher.protocol.LauncherConfig
import tech.spectrumlabs.hospitium.launcher.protocol.PairingStartResponse
import tech.spectrumlabs.hospitium.launcher.protocol.PairingStatusResponse
import java.io.IOException
import java.time.Duration

/** Scripted server: each call takes the next queued answer (or repeats the last). */
private class FakeApi : LauncherApi {
    val starts = ArrayDeque<() -> PairingStartResponse>()
    val statuses = ArrayDeque<() -> PairingStatusResponse>()
    val configs = ArrayDeque<() -> LauncherConfig>()
    val calls = mutableListOf<String>()

    private fun <T> next(q: ArrayDeque<() -> T>): T = (if (q.size > 1) q.removeFirst() else q.first())()

    override suspend fun startPairing(): PairingStartResponse { calls += "start"; return next(starts) }
    override suspend fun pairingStatus(code: String, pollToken: String): PairingStatusResponse {
        calls += "status $code $pollToken"; return next(statuses)
    }
    override suspend fun config(deviceToken: String): LauncherConfig { calls += "config $deviceToken"; return next(configs) }
}

private fun code(c: String) = PairingStartResponse(c, "hsp_$c", T0.plus(Duration.ofMinutes(15)))

@OptIn(ExperimentalCoroutinesApi::class)
class LauncherControllerTest {
    private val api = FakeApi()

    private fun TestScope.controller(store: InMemoryDeviceStore) =
        LauncherController(backgroundScope, store, apiFor = { api }, now = { T0 }, defaultServerUrl = "https://default.example")

    @Test
    fun `no server address shows setup with the build's default`() = runTest {
        val c = controller(InMemoryDeviceStore())
        c.start(); runCurrent()
        assertEquals(LauncherState.NeedsServer("https://default.example"), c.state.value)
    }

    @Test
    fun `an invalid address stays on setup with a reason`() = runTest {
        val store = InMemoryDeviceStore()
        val c = controller(store)
        c.submitServer("http://hospitium.example")
        val s = c.state.value as LauncherState.NeedsServer
        assertTrue(s.error!!.contains("https"))
        assertNull(store.serverUrl())
    }

    @Test
    fun `pairs, stores the token before anything else, then shows home`() = runTest {
        val store = InMemoryDeviceStore()
        api.starts += { code("ABC234") }
        api.statuses += { PairingStatusResponse("pending") }
        api.statuses += { PairingStatusResponse("claimed", deviceToken = "hsd_tok", deviceId = 7) }
        api.configs += { config(serverTime = T0.plusSeconds(30)) }
        val c = controller(store)

        c.submitServer("hospitium.example"); runCurrent()
        assertEquals("https://hospitium.example", store.serverUrl())
        assertEquals("ABC234", (c.state.value as LauncherState.Pairing).code)

        advanceTimeBy(LauncherController.PAIRING_POLL.toMillis() * 2 + 1)
        assertEquals("hsd_tok", store.deviceToken())
        val home = c.state.value as LauncherState.Home
        assertEquals("Example Lodge", home.cached!!.config.property.name)
        assertEquals(30_000, home.cached.offsetMillis) // server 30 s ahead
        assertEquals(store.cachedConfig(), home.cached)
        assertEquals(listOf("start", "status ABC234 hsp_ABC234", "status ABC234 hsp_ABC234", "config hsd_tok"), api.calls)
    }

    @Test
    fun `an expired code is replaced with a new one`() = runTest {
        api.starts += { code("AAAAAA") }
        api.starts += { code("BBBBBB") }
        api.statuses += { PairingStatusResponse("expired") }
        api.statuses += { PairingStatusResponse("pending") }
        val c = controller(InMemoryDeviceStore(url = "https://h.example"))
        c.start(); runCurrent()
        advanceTimeBy(LauncherController.PAIRING_POLL.toMillis() + 1)
        assertEquals("BBBBBB", (c.state.value as LauncherState.Pairing).code)
    }

    @Test
    fun `a claim whose token was already collected starts over and says why`() = runTest {
        api.starts += { code("AAAAAA") }
        api.starts += { code("BBBBBB") }
        api.statuses += { PairingStatusResponse("claimed", deviceId = 7) }
        api.statuses += { PairingStatusResponse("pending") }
        val store = InMemoryDeviceStore(url = "https://h.example")
        val c = controller(store)
        c.start(); runCurrent()
        advanceTimeBy(LauncherController.PAIRING_POLL.toMillis() * 2 + 1)
        val s = c.state.value as LauncherState.Pairing
        assertEquals("BBBBBB", s.code)
        assertEquals(Problem.TOKEN_ALREADY_TAKEN, s.problem)
        assertNull(store.deviceToken())
    }

    @Test
    fun `offline while pairing says so and retries`() = runTest {
        api.starts += { throw ApiException.Network(IOException("down")) }
        api.starts += { code("ABC234") }
        api.statuses += { PairingStatusResponse("pending") }
        val c = controller(InMemoryDeviceStore(url = "https://h.example"))
        c.start(); runCurrent()
        assertEquals(Problem.OFFLINE, (c.state.value as LauncherState.Pairing).problem)
        advanceTimeBy(LauncherController.PAIRING_RETRY.toMillis() + 1)
        val s = c.state.value as LauncherState.Pairing
        assertEquals("ABC234", s.code)
        assertNull(s.problem)
    }

    @Test
    fun `offline at boot shows the cached config, marked stale`() = runTest {
        val cachedCfg = tech.spectrumlabs.hospitium.launcher.data.CachedConfig(config(), 0)
        api.configs += { throw ApiException.Network(IOException("down")) }
        val c = controller(InMemoryDeviceStore(url = "https://h.example", token = "hsd_tok", cached = cachedCfg))
        c.start(); runCurrent()
        assertEquals(LauncherState.Home(cachedCfg, stale = true), c.state.value)
    }

    @Test
    fun `refreshes on the server's interval`() = runTest {
        api.configs += { config(refresh = 60) }
        val c = controller(InMemoryDeviceStore(url = "https://h.example", token = "hsd_tok"))
        c.start(); runCurrent()
        advanceTimeBy(60_000 * 3 + 1)
        assertEquals(4, api.calls.count { it.startsWith("config") })
    }

    @Test
    fun `refreshNow skips the wait`() = runTest {
        api.configs += { config(refresh = 3600) }
        val c = controller(InMemoryDeviceStore(url = "https://h.example", token = "hsd_tok"))
        c.start(); runCurrent()
        c.refreshNow(); runCurrent()
        assertEquals(2, api.calls.count { it.startsWith("config") })
    }

    @Test
    fun `one refusal does not unpair, repeated refusals do`() = runTest {
        val store = InMemoryDeviceStore(url = "https://h.example", token = "hsd_tok")
        api.configs += { config() }
        api.configs += { throw ApiException.Unauthorized() }
        api.starts += { code("NEWONE") }
        api.statuses += { PairingStatusResponse("pending") }
        val c = controller(store)
        c.start(); runCurrent()

        c.refreshNow(); runCurrent() // first refusal
        assertEquals("hsd_tok", store.deviceToken())
        assertTrue((c.state.value as LauncherState.Home).stale)

        advanceTimeBy(Duration.ofMinutes(5).toMillis())
        assertNull(store.deviceToken())
        assertNull(store.cachedConfig())
        assertEquals("NEWONE", (c.state.value as LauncherState.Pairing).code)
        assertEquals("https://h.example", store.serverUrl())
    }

    @Test
    fun `a success between refusals resets the count`() = runTest {
        val store = InMemoryDeviceStore(url = "https://h.example", token = "hsd_tok")
        api.configs += { throw ApiException.Unauthorized() }
        api.configs += { throw ApiException.Unauthorized() }
        api.configs += { config(refresh = 15) }
        api.configs += { throw ApiException.Unauthorized() }
        api.configs += { throw ApiException.Unauthorized() }
        api.configs += { config(refresh = 15) }
        val c = controller(store)
        c.start()
        advanceTimeBy(Duration.ofMinutes(10).toMillis())
        assertEquals("hsd_tok", store.deviceToken())
    }

    @Test
    fun `change server is refused once paired`() = runTest {
        api.configs += { config() }
        val store = InMemoryDeviceStore(url = "https://h.example", token = "hsd_tok")
        val c = controller(store)
        c.start(); runCurrent()
        c.changeServer()
        assertEquals("https://h.example", store.serverUrl())
        assertTrue(c.state.value is LauncherState.Home)
    }
}
