// SPDX-License-Identifier: Apache-2.0

package tech.spectrumlabs.hospitium.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.tv.material3.darkColorScheme
import kotlinx.coroutines.delay
import tech.spectrumlabs.hospitium.launcher.R
import tech.spectrumlabs.hospitium.launcher.domain.LauncherState
import java.time.Instant

val Ink = Color(0xFF1F2A44)
val Accent = Color(0xFFE8B04B)

/** Parses a branding color like "#1F2A44"; anything else falls back to the default. */
fun brandColor(value: String?, fallback: Color): Color {
    val hex = value?.trim()?.removePrefix("#") ?: return fallback
    if (hex.length != 6 || hex.any { it.digitToIntOrNull(16) == null }) return fallback
    return Color(0xFF000000 or hex.toLong(16))
}

/** The local clock, ticking every [periodMillis], so expiries apply while the screen is up. */
@Composable
fun rememberNow(periodMillis: Long = 15_000): Instant {
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(periodMillis) {
        while (true) {
            delay(periodMillis)
            now = Instant.now()
        }
    }
    return now
}

@Composable
fun LauncherApp(vm: LauncherViewModel) {
    val state by vm.controller.state.collectAsStateWithLifecycle()
    val apps by vm.apps.collectAsStateWithLifecycle()
    val primary = (state as? LauncherState.Home)?.cached?.config?.property?.branding?.get("primary_color")
    val brand = brandColor(primary, Ink)

    MaterialTheme(colorScheme = darkColorScheme(primary = Accent, background = brand, surface = brand)) {
        Box(Modifier.fillMaxSize().background(brand)) {
            when (val s = state) {
                LauncherState.Starting -> Centered(stringResource(R.string.connecting))
                is LauncherState.NeedsServer -> SetupScreen(s, onSubmit = vm.controller::submitServer)
                is LauncherState.Pairing -> PairingScreen(s, rememberNow(1_000), onChangeServer = vm.controller::changeServer)
                is LauncherState.Home -> {
                    val cached = s.cached
                    if (cached == null) {
                        Centered(stringResource(R.string.connecting))
                    } else {
                        HomeScreen(cached, s.stale, apps, rememberNow(), onLaunch = { vm.launch(it) })
                    }
                }
            }
        }
    }
}

@Composable
fun Centered(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.headlineSmall, color = Color.White)
    }
}
