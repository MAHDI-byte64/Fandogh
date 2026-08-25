package com.v2ray.ang.ui.fandogh

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.v2ray.ang.R

data class PickableServer(
    val guid: String,
    val name: String,
    val protocol: String,
    val address: String,
    val delayMillis: Long
)

/**
 * A row's minimum height.
 *
 * A row is a name over a protocol badge and an address inside 16dp of padding, so it
 * needs more than the flag tile alone — pinning it to the tile's height clipped the
 * second line. A floor rather than a fixed size, so a long name can still grow.
 */
private val SERVER_ROW_HEIGHT = 76.dp

/**
 * Server chooser shown by the home screen's server card.
 *
 * Latency is only rendered when the server has actually been tested — an untested server
 * shows nothing rather than a zero that would read as "instant". Search appears only once
 * the list is long enough to need it, so a two-server subscription is not made to look
 * like a database.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerPickerSheet(
    servers: List<PickableServer>,
    selectedGuid: String?,
    testing: Boolean,
    progressText: String = "",
    onSelect: (String) -> Unit,
    // Both act on the visible tab rather than the whole subscription: the tab is a
    // filter, and testing servers the user cannot see would be surprising.
    onTestAll: (List<PickableServer>) -> Unit,
    onAutoSelect: (List<PickableServer>) -> Unit,
    onAddSubscription: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val tabs = remember(servers) { ServerTabs.tabsFor(servers) }
    var selectedTab by remember(tabs) { mutableStateOf<ServerTabs.Tab>(ServerTabs.Tab.All) }
    val shown = remember(servers, selectedTab) { ServerTabs.filter(servers, selectedTab) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = FandoghColors.BackgroundMid,
        contentColor = FandoghColors.TextPrimary,
        shape = RoundedCornerShape(
            topStart = FandoghRadius.sheet,
            topEnd = FandoghRadius.sheet
        ),
        dragHandle = {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(top = FandoghSpace.md, bottom = FandoghSpace.xs),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier
                        .width(38.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(FandoghRadius.pill))
                        .background(Color.White.copy(alpha = 0.22f))
                )
            }
        }
    ) {
        // One lazy list for the whole sheet, header and tabs included, rather than a
        // lazy list nested inside the sheet's own scrolling column. Nesting the two
        // makes them fight over the same drag: the sheet resizes, the list re-measures,
        // and the sheet hunts up and down without ever settling. A single scrollable
        // region has nothing to fight with, and it is the arrangement ModalBottomSheet
        // is built for.
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(
                start = FandoghSpace.xl,
                end = FandoghSpace.xl,
                bottom = FandoghSpace.xxl
            ),
            verticalArrangement = Arrangement.spacedBy(FandoghSpace.sm)
        ) {
            item(key = "header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.fandogh_change_profile),
                            color = FandoghColors.TextPrimary,
                            fontSize = 23.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (shown.size == 1) {
                                stringResource(R.string.fandogh_one_server)
                            } else {
                                stringResource(R.string.fandogh_server_count, shown.size)
                            },
                            color = FandoghColors.TextSecondary,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    if (servers.isNotEmpty()) {
                        Spacer(Modifier.width(FandoghSpace.md))
                        PillAction(
                            // Showing the remaining count is what makes a slow batch read
                            // as "working" rather than "ignored me".
                            label = when {
                                testing && progressText.isNotBlank() -> progressText
                                testing -> stringResource(R.string.fandogh_testing)
                                else -> stringResource(R.string.fandogh_test_all)
                            },
                            accent = FandoghColors.AccentGreen,
                            enabled = !testing,
                            onClick = { onTestAll(shown) }
                        )
                    }
                }
            }

            // Most people do not want to read a latency table, they want the best one.
            if (shown.size > 1) {
                item(key = "auto") {
                    AutoSelectRow(enabled = !testing, onClick = { onAutoSelect(shown) })
                }
            }

            // Only worth showing when there is more than one bucket to move between.
            if (tabs.size > 1) {
                item(key = "tabs") {
                    TabStrip(
                        tabs = tabs,
                        selected = selectedTab,
                        onSelect = { selectedTab = it }
                    )
                }
            }

            if (servers.isEmpty()) {
                item(key = "empty") { EmptyServers(onAddSubscription) }
            } else {
                items(shown, key = { it.guid }) { server ->
                    ServerRow(
                        server = server,
                        selected = server.guid == selectedGuid,
                        onClick = { onSelect(server.guid) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PillAction(
    label: String,
    accent: Color,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val tint = if (enabled) accent else FandoghColors.TextTertiary
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(FandoghRadius.pill))
            .background(tint.copy(alpha = 0.14f))
            .border(
                BorderStroke(1.dp, tint.copy(alpha = 0.38f)),
                RoundedCornerShape(FandoghRadius.pill)
            )
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = FandoghSpace.lg, vertical = FandoghSpace.sm)
    ) {
        Text(label, color = tint, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun EmptyServers(onAddSubscription: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = FandoghSpace.xxl),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(FandoghColors.AccentBlue.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            GlobeGlyph(Modifier.size(36.dp))
        }
        Spacer(Modifier.height(FandoghSpace.lg))
        Text(
            stringResource(R.string.fandogh_no_servers_yet),
            color = FandoghColors.TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(FandoghSpace.xs))
        Text(
            stringResource(R.string.fandogh_no_servers_yet_hint),
            color = FandoghColors.TextSecondary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(FandoghSpace.xl))
        GradientButton(
            text = stringResource(R.string.fandogh_go_to_profile),
            onClick = onAddSubscription,
            modifier = Modifier.fillMaxWidth(0.72f)
        )
    }
}

@Composable
private fun ServerRow(server: PickableServer, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(FandoghRadius.card)
    val accent = if (selected) FandoghColors.AccentBlue else FandoghColors.Border
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = SERVER_ROW_HEIGHT)
            .clip(shape)
            .background(
                if (selected) FandoghColors.AccentBlue.copy(alpha = 0.14f) else FandoghColors.Surface
            )
            .border(BorderStroke(1.dp, accent), shape)
            .clickable(onClick = onClick)
            .padding(FandoghSpace.lg),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ServerFlagTile(serverName = server.name, size = 40.dp)
        Spacer(Modifier.width(FandoghSpace.md))

        Column(Modifier.weight(1f)) {
            Text(
                remember(server.name) { CountryFlags.stripFlag(server.name) },
                color = FandoghColors.TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 3.dp)) {
                ProtocolBadge(server.protocol)
                Spacer(Modifier.width(FandoghSpace.sm))
                Text(
                    server.address,
                    color = FandoghColors.TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (server.delayMillis > 0) {
            Spacer(Modifier.width(FandoghSpace.sm))
            Text(
                text = "${server.delayMillis}",
                color = latencyColor(server.delayMillis),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "ms",
                color = latencyColor(server.delayMillis).copy(alpha = 0.7f),
                fontSize = 10.sp,
                modifier = Modifier.padding(start = 2.dp, top = 3.dp)
            )
        }

        if (selected) {
            Spacer(Modifier.width(FandoghSpace.sm))
            CheckGlyph(Modifier.size(18.dp))
        }
    }
}

/** Compact protocol tag, so the row reads at a glance without a second full line. */
@Composable
private fun ProtocolBadge(protocol: String) {
    Box(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(FandoghColors.AccentBlue.copy(alpha = 0.16f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = protocol.uppercase(),
            color = FandoghColors.AccentBlueBright,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp
        )
    }
}

@Composable
private fun CheckGlyph(modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier) {
        val stroke = androidx.compose.ui.graphics.drawscope.Stroke(
            width = 2.4.dp.toPx(),
            cap = androidx.compose.ui.graphics.StrokeCap.Round,
            join = androidx.compose.ui.graphics.StrokeJoin.Round
        )
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(size.width * 0.18f, size.height * 0.52f)
            lineTo(size.width * 0.42f, size.height * 0.76f)
            lineTo(size.width * 0.84f, size.height * 0.24f)
        }
        drawPath(path, FandoghColors.AccentBlueBright, style = stroke)
    }
}

/**
 * One tap for "just give me the best server": tests everything, then connects to
 * whichever answered fastest.
 */
@Composable
private fun AutoSelectRow(enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(FandoghRadius.tile)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                Brush.horizontalGradient(
                    listOf(
                        FandoghColors.AccentBlue.copy(alpha = if (enabled) 0.30f else 0.12f),
                        FandoghColors.AccentGreen.copy(alpha = if (enabled) 0.26f else 0.10f)
                    )
                )
            )
            .border(BorderStroke(1.dp, FandoghColors.AccentGreen.copy(alpha = 0.36f)), shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = FandoghSpace.lg, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BoltGlyph(Modifier.size(20.dp))
        Spacer(Modifier.width(FandoghSpace.md))
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.fandogh_auto_select),
                color = FandoghColors.TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                stringResource(R.string.fandogh_auto_select_hint),
                color = FandoghColors.TextSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 1.dp)
            )
        }
    }
}

/** Lightning bolt for the automatic pick. */
@Composable
private fun BoltGlyph(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.56f, 0f)
            lineTo(w * 0.18f, h * 0.56f)
            lineTo(w * 0.46f, h * 0.56f)
            lineTo(w * 0.40f, h)
            lineTo(w * 0.82f, h * 0.40f)
            lineTo(w * 0.52f, h * 0.40f)
            close()
        }
        drawPath(path, FandoghColors.AccentGreen)
    }
}

/**
 * Horizontal tab strip over the server list.
 *
 * Scrollable rather than evenly divided: the number of tabs depends on how many
 * continents the subscription covers, and squeezing six labels into the sheet's width
 * would make each unreadable.
 */
@Composable
private fun TabStrip(
    tabs: List<ServerTabs.Tab>,
    selected: ServerTabs.Tab,
    onSelect: (ServerTabs.Tab) -> Unit
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(FandoghSpace.sm)) {
        items(tabs, key = { it.toString() }) { tab ->
            TabChip(
                label = tabLabel(tab),
                selected = tab == selected,
                onClick = { onSelect(tab) }
            )
        }
    }
}

@Composable
private fun TabChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(FandoghRadius.pill)
    val accent = if (selected) FandoghColors.AccentBlueBright else FandoghColors.TextSecondary
    val background by animateColorAsState(
        targetValue = if (selected) {
            FandoghColors.AccentBlue.copy(alpha = 0.22f)
        } else {
            Color.White.copy(alpha = 0.05f)
        },
        label = "tabBackground"
    )
    Box(
        modifier = Modifier
            .clip(shape)
            .background(background)
            .border(
                BorderStroke(
                    1.dp,
                    if (selected) {
                        FandoghColors.AccentBlue.copy(alpha = 0.5f)
                    } else {
                        FandoghColors.Border
                    }
                ),
                shape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = FandoghSpace.lg, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = accent,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1
        )
    }
}

@Composable
private fun tabLabel(tab: ServerTabs.Tab): String = when (tab) {
    ServerTabs.Tab.All -> stringResource(R.string.fandogh_tab_all)
    ServerTabs.Tab.Tunnel -> stringResource(R.string.fandogh_tab_tunnel)
    is ServerTabs.Tab.Region -> stringResource(
        when (tab.continent) {
            ServerTabs.Continent.Europe -> R.string.fandogh_continent_europe
            ServerTabs.Continent.Asia -> R.string.fandogh_continent_asia
            ServerTabs.Continent.NorthAmerica -> R.string.fandogh_continent_north_america
            ServerTabs.Continent.SouthAmerica -> R.string.fandogh_continent_south_america
            ServerTabs.Continent.Africa -> R.string.fandogh_continent_africa
            ServerTabs.Continent.Oceania -> R.string.fandogh_continent_oceania
        }
    )
}
