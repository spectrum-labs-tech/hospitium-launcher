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

// Overscan-safe side margin. Rows apply it as content padding, so a focused (scaled) card
// at either end grows into the margin instead of being clipped by the row.
private val Gutter = 48.dp
private val RowPadding = PaddingValues(horizontal = Gutter, vertical = 8.dp)

/** A non-app card in the "Your stay" row. */
private data class StayCard(val key: String, val title: String, val lines: List<String>)

// TV layouts are designed on a 960x540dp canvas; 48dp/27dp keeps content inside overscan.
@Composable
fun HomeScreen(cached: CachedConfig, stale: Boolean, installed: List<TvApp>, now: Instant, onLaunch: (String) -> Unit) {
    BackHandler { /* this IS home: Back has nowhere to go */ }

    val config = cached.config
    val clocks = Clocks(now, Duration.ofMillis(cached.offsetMillis))
    val view = HomePolicy.view(config, clocks)
    val zone = remember(config.property.timezone) { runCatching { ZoneId.of(config.property.timezone) }.getOrDefault(ZoneId.systemDefault()) }
    val time = remember(zone) { DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withZone(zone) }
    val dateTime = remember(zone) { DateTimeFormatter.ofPattern("EEE, MMM d · h:mm a").withZone(zone) }
    val featured = remember(config.apps, installed) { AppCatalog.featured(config.apps, installed) }

    val wifi = config.property.wifi
    val wifiTitle = stringResource(R.string.wifi_title)
    val wifiNetwork = wifi?.let { stringResource(R.string.wifi_network, it.ssid) }
    val wifiPassword = wifi?.password?.takeIf { it.isNotEmpty() }?.let { stringResource(R.string.wifi_password, it) }
    val rulesTitle = stringResource(R.string.row_house_rules)
    val amenitiesTitle = stringResource(R.string.row_amenities)
    val stay = buildList {
        view.announcements.forEach { add(StayCard("a-${it.id}", it.title, listOf(it.body))) }
        if (wifiNetwork != null) add(StayCard("wifi", wifiTitle, listOfNotNull(wifiNetwork, wifiPassword)))
        if (config.property.houseRules.isNotEmpty()) add(StayCard("rules", rulesTitle, config.property.houseRules))
        if (config.property.amenities.isNotEmpty()) add(StayCard("amenities", amenitiesTitle, config.property.amenities))
    }

    // Initial focus goes to the first card of the topmost row, which is always on screen.
    val firstFocus = remember { FocusRequester() }
    val focusRow = when {
        featured.isNotEmpty() -> 0
        stay.isNotEmpty() -> 1
        else -> 2
    }
    val focusReady = featured.isNotEmpty() || stay.isNotEmpty() || installed.isNotEmpty()
    LaunchedEffect(focusRow, focusReady) { if (focusReady) runCatching { firstFocus.requestFocus() } }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 27.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = Gutter), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(config.property.name, style = MaterialTheme.typography.titleMedium, color = Dim)
                    config.unit?.let { Text(it.name, style = MaterialTheme.typography.bodySmall, color = Dim) }
                }
                if (stale) {
                    Text(
                        stringResource(R.string.stale),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.Black,
                        modifier = Modifier.background(Accent, RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                    Spacer(Modifier.width(16.dp))
                }
                Text(time.format(clocks.corrected), style = MaterialTheme.typography.titleLarge, color = Color.White)
            }
        }

        item {
            Column(Modifier.padding(horizontal = Gutter)) {
                val guest = view.guest
                Text(
                    if (guest != null) stringResource(R.string.welcome_guest, guest.displayName) else stringResource(R.string.welcome),
                    style = MaterialTheme.typography.displaySmall,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (guest != null) {
                    Text(stringResource(R.string.stay_until, dateTime.format(guest.checkOut)), style = MaterialTheme.typography.titleMedium, color = Dim)
                }
                if (view.showCheckoutReminder && guest != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        stringResource(R.string.checkout_reminder, time.format(guest.checkOut)),
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.Black,
                        modifier = Modifier.background(Accent, RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
        }

        if (featured.isNotEmpty()) {
            item { AppRow(stringResource(R.string.row_featured), featured, onLaunch, firstFocus.takeIf { focusRow == 0 }) }
        }

        if (stay.isNotEmpty()) {
            item {
                Section(stringResource(R.string.row_your_stay)) {
                    LazyRow(contentPadding = RowPadding, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        itemsIndexed(stay, key = { _, c -> c.key }) { i, c ->
                            val m = if (i == 0 && focusRow == 1) Modifier.focusRequester(firstFocus) else Modifier
                            InfoCard(c.title, c.lines, m)
                        }
                    }
                }
            }
        }

        if (installed.isNotEmpty()) {
            item { AppRow(stringResource(R.string.row_all_apps), installed, onLaunch, firstFocus.takeIf { focusRow == 2 }) }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column {
        Text(title, style = MaterialTheme.typography.titleSmall, color = Dim, modifier = Modifier.padding(horizontal = Gutter))
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun AppRow(title: String, apps: List<TvApp>, onLaunch: (String) -> Unit, firstFocus: FocusRequester?) {
    Section(title) {
        LazyRow(contentPadding = RowPadding, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            itemsIndexed(apps, key = { _, a -> a.packageName }) { i, app ->
                val m = if (i == 0 && firstFocus != null) Modifier.focusRequester(firstFocus) else Modifier
                Card(onClick = { onLaunch(app.packageName) }, modifier = m.size(width = 160.dp, height = 90.dp)) {
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
                Image(bmp.asImageBitmap(), null, Modifier.size(36.dp))
                Spacer(Modifier.height(4.dp))
                Text(app.label, style = MaterialTheme.typography.labelMedium, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            else -> Text(app.label, style = MaterialTheme.typography.titleSmall, color = Color.White)
        }
    }
}

/** Focusable (so the D-pad can reach and scroll to it) but does nothing when clicked. */
@Composable
private fun InfoCard(title: String, lines: List<String>, modifier: Modifier = Modifier) {
    Card(onClick = {}, modifier = modifier.width(240.dp).height(120.dp)) {
        Column(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.08f)).padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            for (line in lines.take(4)) {
                Text(line, style = MaterialTheme.typography.bodySmall, color = Dim, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
