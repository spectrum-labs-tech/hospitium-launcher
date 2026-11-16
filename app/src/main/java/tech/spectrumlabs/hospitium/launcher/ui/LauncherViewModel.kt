// SPDX-License-Identifier: Apache-2.0

package tech.spectrumlabs.hospitium.launcher.ui

import android.app.Application
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import tech.spectrumlabs.hospitium.launcher.BuildConfig
import tech.spectrumlabs.hospitium.launcher.data.AndroidDeviceStore
import tech.spectrumlabs.hospitium.launcher.domain.Backgrounds
import tech.spectrumlabs.hospitium.launcher.domain.LauncherController
import tech.spectrumlabs.hospitium.launcher.domain.LauncherState
import tech.spectrumlabs.hospitium.launcher.domain.UsageCounters
import tech.spectrumlabs.hospitium.launcher.domain.UsageRecorder
import tech.spectrumlabs.hospitium.launcher.net.LauncherClient
import java.time.Duration
import java.time.Instant

class LauncherViewModel(app: Application) : AndroidViewModel(app) {
    private val catalog = AppCatalog(app)

    // No device identifiers in the User-Agent: the app version and Android release only.
    private val userAgent = "hospitium-launcher/${BuildConfig.VERSION_NAME} (Android ${Build.VERSION.RELEASE})"

    private val store = AndroidDeviceStore(app)

    /** Anonymous hourly counts of screens opened and apps started (protocol v0.6.0). */
    private val usage = UsageRecorder(store, Instant::now).apply {
        store.cachedConfig()?.let { offset = Duration.ofMillis(it.offsetMillis) }
    }

    val controller = LauncherController(
        scope = viewModelScope,
        store = store,
        apiFor = { url -> LauncherClient(url, userAgent) },
        now = Instant::now,
        defaultServerUrl = BuildConfig.DEFAULT_SERVER_URL,
        allowLocalHttp = BuildConfig.DEBUG,
        usage = usage,
    ).also { it.start() }

    private val backgrounds = BackgroundLoader(app.cacheDir, controller::media)
    private val _background = MutableStateFlow<BackgroundImage?>(null)

    /** The home screen's photo, or null for the plain brand color (none chosen, or not loaded yet). */
    val background: StateFlow<BackgroundImage?> = _background.asStateFlow()

    init {
        // Follow the config: load a background when a different one is chosen, drop it when removed.
        viewModelScope.launch {
            controller.state
                .map { (it as? LauncherState.Home)?.cached?.config?.property?.branding }
                .filterNotNull()
                .map { Backgrounds.choose(it, app.resources.displayMetrics.widthPixels) }
                .distinctUntilChanged()
                .collectLatest { choice ->
                    if (choice == null) {
                        _background.value = null
                    } else if (_background.value?.sha256 != choice.sha256) {
                        val m = app.resources.displayMetrics
                        backgrounds.load(choice, m.widthPixels, m.heightPixels)?.let { _background.value = it }
                    }
                }
        }
    }

    private val _apps = MutableStateFlow<List<TvApp>>(emptyList())
    val apps: StateFlow<List<TvApp>> = _apps.asStateFlow()

    /** Called on every resume: apps may have been installed or removed, and the TV may have slept. */
    fun onResume() {
        if (controller.state.value is LauncherState.Home) usage.record(UsageCounters.HOME)
        controller.refreshNow()
        viewModelScope.launch(Dispatchers.IO) { _apps.value = catalog.installed() }
    }

    fun launch(packageName: String) {
        usage.record(UsageCounters.app(packageName))
        catalog.launch(packageName)
    }

    /** A home-screen card was opened. */
    fun opened(cardKey: String) {
        UsageCounters.forCard(cardKey)?.let(usage::record)
    }
}
