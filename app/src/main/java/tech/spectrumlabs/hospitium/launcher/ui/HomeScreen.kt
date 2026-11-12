// SPDX-License-Identifier: Apache-2.0

package tech.spectrumlabs.hospitium.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tech.spectrumlabs.hospitium.launcher.R
import tech.spectrumlabs.hospitium.launcher.data.CachedConfig
import tech.spectrumlabs.hospitium.launcher.domain.Clocks
import tech.spectrumlabs.hospitium.launcher.domain.HomePolicy
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val Dim = Color.White.copy(alpha = 0.7f)

@Composable
fun HomeScreen(cached: CachedConfig, stale: Boolean, installed: List<TvApp>, now: Instant, onLaunch: (String) -> Unit) {
    BackHandler { /* this IS home: Back has nowhere to go */ }

    val config = cached.config
    val view = HomePolicy.view(config, Clocks(now, Duration.ofMillis(cached.offsetMillis)))
    val zone = remember(config.property.timezone) { runCatching { ZoneId.of(config.property.timezone) }.getOrDefault(ZoneId.systemDefault()) }
    val time = remember(zone) { DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withZone(zone) }
    val dateTime = remember(zone) { DateTimeFormatter.ofPattern("EEE, MMM d · h:mm a").withZone(zone) }
    val featured = remember(config.apps, installed) { AppCatalog.featured(config.apps, installed) }
    val firstApp = remember { FocusRequester() }
    LaunchedEffect(featured.isNotEmpty() || installed.isNotEmpty()) { runCatching { firstApp.requestFocus() } }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 58.dp, vertical = 36.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(config.property.name, style = MaterialTheme.typography.titleLarge, color = Dim)
                    config.unit?.let { Text(it.name, style = MaterialTheme.typography.bodyMedium, color = Dim) }
                }
                if (stale) {
                    Text(
                        stringResource(R.string.stale),
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.Black,
                        modifier = Modifier.background(Accent, RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                    Spacer(Modifier.width(24.dp))
                }
                Text(time.format(Clocks(now, Duration.ofMillis(cached.offsetMillis)).corrected), style = MaterialTheme.typography.headlineMedium, color = Color.White)
            }
        }

        item {
            Column {
                val guest = view.guest
                Text(
                    if (guest != null) stringResource(R.string.welcome_guest, guest.displayName) else stringResource(R.string.welcome),
                    style = MaterialTheme.typography.displayMedium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (guest != null) {
                    Text(stringResource(R.string.stay_until, dateTime.format(guest.checkOut)), style = MaterialTheme.typography.titleLarge, color = Dim)
                }
                if (view.showCheckoutReminder && guest != null) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        stringResource(R.string.checkout_reminder, time.format(guest.checkOut)),
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.Black,
                        modifier = Modifier.background(Accent, RoundedCornerShape(12.dp)).padding(horizontal = 20.dp, vertical = 12.dp),
                    )
                }
            }
        }

        if (featured.isNotEmpty()) {
            item { AppRow(stringResource(R.string.row_featured), featured, onLaunch, firstApp) }
        }

        if (view.announcements.isNotEmpty()) {
            item {
                Section(stringResource(R.string.row_announcements)) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        items(view.announcements, key = { it.id }) { a -> InfoCard(a.title, listOf(a.body)) }
                    }
                }
            }
        }

        val wifi = config.property.wifi
        if (wifi != null || config.property.houseRules.isNotEmpty() || config.property.amenities.isNotEmpty()) {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    if (wifi != null) {
                        item {
                            InfoCard(
                                stringResource(R.string.wifi_title),
                                listOfNotNull(
                                    stringResource(R.string.wifi_network, wifi.ssid),
                                    wifi.password?.takeIf { it.isNotEmpty() }?.let { stringResource(R.string.wifi_password, it) },
                                ),
                            )
                        }
                    }
                    if (config.property.houseRules.isNotEmpty()) {
                        item { InfoCard(stringResource(R.string.row_house_rules), config.property.houseRules) }
                    }
                    if (config.property.amenities.isNotEmpty()) {
                        item { InfoCard(stringResource(R.string.row_amenities), config.property.amenities) }
                    }
                }
            }
        }

        if (installed.isNotEmpty()) {
            item { AppRow(stringResource(R.string.row_all_apps), installed, onLaunch, if (featured.isEmpty()) firstApp else null) }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column {
        Text(title, style = MaterialTheme.typography.titleMedium, color = Dim)
        Spacer(Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun AppRow(title: String, apps: List<TvApp>, onLaunch: (String) -> Unit, firstFocus: FocusRequester?) {
    Section(title) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            itemsIndexed(apps, key = { _, a -> a.packageName }) { i, app ->
                val m = if (i == 0 && firstFocus != null) Modifier.focusRequester(firstFocus) else Modifier
                Card(onClick = { onLaunch(app.packageName) }, modifier = m.size(width = 224.dp, height = 126.dp)) {
                    AppTile(app)
                }
            }
        }
    }
}

@Composable
private fun AppTile(app: TvApp) {
    val bmp = app.banner
    Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
        when {
            bmp != null && app.hasBanner -> Image(bmp.asImageBitmap(), app.label, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            bmp != null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Image(bmp.asImageBitmap(), null, Modifier.size(56.dp))
                Spacer(Modifier.height(8.dp))
                Text(app.label, style = MaterialTheme.typography.labelLarge, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            else -> Text(app.label, style = MaterialTheme.typography.titleMedium, color = Color.White)
        }
    }
}

/** Focusable (so the D-pad can reach and scroll to it) but does nothing when clicked. */
@Composable
private fun InfoCard(title: String, lines: List<String>) {
    Card(onClick = {}, modifier = Modifier.width(380.dp).height(200.dp)) {
        Column(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.08f)).padding(20.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(8.dp))
            for (line in lines.take(5)) {
                Text(line, style = MaterialTheme.typography.bodyLarge, color = Dim, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
