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
import kotlinx.coroutines.launch
import tech.spectrumlabs.hospitium.launcher.BuildConfig
import tech.spectrumlabs.hospitium.launcher.data.AndroidDeviceStore
import tech.spectrumlabs.hospitium.launcher.domain.LauncherController
import tech.spectrumlabs.hospitium.launcher.net.LauncherClient
import java.time.Instant

class LauncherViewModel(app: Application) : AndroidViewModel(app) {
    private val catalog = AppCatalog(app)

    // No device identifiers in the User-Agent: the app version and Android release only.
    private val userAgent = "hospitium-launcher/${BuildConfig.VERSION_NAME} (Android ${Build.VERSION.RELEASE})"

    val controller = LauncherController(
        scope = viewModelScope,
        store = AndroidDeviceStore(app),
        apiFor = { url -> LauncherClient(url, userAgent) },
        now = Instant::now,
        defaultServerUrl = BuildConfig.DEFAULT_SERVER_URL,
        allowLocalHttp = BuildConfig.DEBUG,
    ).also { it.start() }

    private val _apps = MutableStateFlow<List<TvApp>>(emptyList())
    val apps: StateFlow<List<TvApp>> = _apps.asStateFlow()

    /** Called on every resume: apps may have been installed or removed, and the TV may have slept. */
    fun onResume() {
        controller.refreshNow()
        viewModelScope.launch(Dispatchers.IO) { _apps.value = catalog.installed() }
    }

    fun launch(packageName: String) = catalog.launch(packageName)
}
