// SPDX-License-Identifier: Apache-2.0

package tech.spectrumlabs.hospitium.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Button
import androidx.tv.material3.Card
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tech.spectrumlabs.hospitium.launcher.R
import tech.spectrumlabs.hospitium.launcher.data.CachedConfig
import tech.spectrumlabs.hospitium.launcher.domain.Clocks
import tech.spectrumlabs.hospitium.launcher.domain.HomePolicy
import tech.spectrumlabs.hospitium.launcher.domain.WifiQr
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
/** A non-app card in the "Your stay" row; [qr] is a code to show beside the text (Wi-Fi). */
private data class StayCard(val key: String, val title: String, val lines: List<String>, val qr: String? = null)

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
        if (wifi != null && wifiNetwork != null) {
            add(StayCard("wifi", wifiTitle, listOfNotNull(wifiNetwork, wifiPassword), qr = WifiQr.payload(wifi.ssid, wifi.password)))
        }
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

    var opened by remember { mutableStateOf<StayCard?>(null) }
    opened?.let { card -> StayDetails(card, onDismiss = { opened = null }) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
    val sizes = LayoutSizes.forWidth(maxWidth.value)
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
                    maxLines = 2, // a long name at a large font size wraps rather than vanishing into "…"
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
            item { AppRow(stringResource(R.string.row_featured), featured, sizes, onLaunch, firstFocus.takeIf { focusRow == 0 }) }
        }

        if (stay.isNotEmpty()) {
            item {
                Section(stringResource(R.string.row_your_stay)) {
                    LazyRow(
                        contentPadding = RowPadding,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        itemsIndexed(stay, key = { _, c -> c.key }) { i, c ->
                            val m = if (i == 0 && focusRow == 1) Modifier.focusRequester(firstFocus) else Modifier
                            InfoCard(c, sizes, onOpen = { opened = c }, modifier = m)
                        }
                    }
                }
            }
        }

        if (installed.isNotEmpty()) {
            item { AppRow(stringResource(R.string.row_all_apps), installed, sizes, onLaunch, firstFocus.takeIf { focusRow == 2 }) }
        }
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
private fun AppRow(title: String, apps: List<TvApp>, sizes: LayoutSizes, onLaunch: (String) -> Unit, firstFocus: FocusRequester?) {
    Section(title) {
        LazyRow(contentPadding = RowPadding, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            itemsIndexed(apps, key = { _, a -> a.packageName }) { i, app ->
                val m = if (i == 0 && firstFocus != null) Modifier.focusRequester(firstFocus) else Modifier
                Card(onClick = { onLaunch(app.packageName) }, modifier = m.size(width = sizes.appTileWidth, height = sizes.appTileHeight)) {
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

/**
 * Grows with its text between a minimum and a maximum height, so a short note and a long one sit
 * in the same row. When the text doesn't fit, it says so, and OK opens [StayDetails] with all of it.
 */
@Composable
private fun InfoCard(card: StayCard, sizes: LayoutSizes, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    var clipped by remember(card) { mutableStateOf(card.lines.size > MAX_CARD_LINES) }
    val width = if (card.qr != null) sizes.infoCardWidth * 1.45f else sizes.infoCardWidth
    Card(
        onClick = onOpen,
        modifier = modifier.width(width).heightIn(min = sizes.infoCardMinHeight, max = sizes.infoCardMaxHeight),
    ) {
        // No inner background: the card's own surface fills it at whatever height the text needs.
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
            Text(
                card.title,
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { if (it.hasVisualOverflow) clipped = true },
            )
            Spacer(Modifier.height(4.dp))
            for (line in card.lines.take(MAX_CARD_LINES)) {
                Text(
                    line,
                    style = MaterialTheme.typography.bodySmall,
                    color = Dim,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    onTextLayout = { if (it.hasVisualOverflow) clipped = true },
                )
            }
            if (clipped) {
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.more), style = MaterialTheme.typography.labelSmall, color = Accent)
            }
            }
            card.qr?.let {
                Spacer(Modifier.width(10.dp))
                QrCodeImage(it, 92.dp, stringResource(R.string.wifi_qr_description))
            }
        }
    }
}

private const val MAX_CARD_LINES = 4

/** The whole card, scrollable, over the home screen. Back or Close dismisses it. */
@Composable
private fun StayDetails(card: StayCard, onDismiss: () -> Unit) {
    val close = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .widthIn(max = 560.dp)
                .heightIn(max = 420.dp)
                .background(Ink, RoundedCornerShape(16.dp))
                .padding(24.dp),
        ) {
            Text(card.title, style = MaterialTheme.typography.headlineSmall, color = Color.White)
            Spacer(Modifier.height(12.dp))
            card.qr?.let {
                // Large enough to scan from across the room.
                QrCodeImage(it, 200.dp, stringResource(R.string.wifi_qr_description), Modifier.align(Alignment.CenterHorizontally))
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.wifi_qr_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Dim,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                Spacer(Modifier.height(12.dp))
            }
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                // Each line is focusable so the D-pad moves through long text; focus brings it into view.
                for (line in card.lines) {
                    Text(line, style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.85f), modifier = Modifier.focusable())
                    Spacer(Modifier.height(6.dp))
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(onClick = onDismiss, modifier = Modifier.align(Alignment.End).focusRequester(close)) {
                Text(stringResource(R.string.close))
            }
        }
    }
    LaunchedEffect(card) { runCatching { close.requestFocus() } }
}
