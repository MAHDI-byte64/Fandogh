package com.v2ray.ang.ui.fandogh

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.platform.LocalConfiguration
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
 * A row's minimum height, and the pitch the list height is computed from.
 *
 * A row is a name over a protocol badge and an address, inside 16dp of padding, so it
 * needs more than the flag tile alone — pinning it to the tile's height clipped the
 * second line. This is a floor, not a cap: the row may grow, and the list simply
 * scrolls a little sooner than the estimate suggested.
 */
private val SERVER_ROW_HEIGHT = 76.dp
private val SERVER_ROW_PITCH = 84.dp

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
    onTestAll: () -> Unit,
    onAutoSelect: () -> Unit,
    onAddSubscription: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }

    // Screen-derived, so it never depends on what the list measures to.
    val maxListHeight = (LocalConfiguration.current.screenHeightDp * 0.52f).dp

    val filtered = remember(servers, query) {
        val q = query.trim()
        if (q.isEmpty()) servers
        else servers.filter {
            it.name.contains(q, ignoreCase = true) || it.address.contains(q, ignoreCase = true)
        }
    }

    val listHeight = remember(filtered.size, maxListHeight) {
        val rows = filtered.size.coerceAtLeast(1)
        val exact = SERVER_ROW_PITCH * rows
        minOf(exact, maxListHeight)
    }

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
        Column(Modifier.padding(horizontal = FandoghSpace.xl)) {
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
                        stringResource(R.string.fandogh_server_count, servers.size),
                        color = FandoghColors.TextSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                if (servers.isNotEmpty()) {
                    Spacer(Modifier.width(FandoghSpace.md))
                    PillAction(
                        // Showing the remaining count is what makes a slow batch read as
                        // "working" rather than "ignored me".
                        label = when {
                            testing && progressText.isNotBlank() -> progressText
                            testing -> stringResource(R.string.fandogh_testing)
                            else -> stringResource(R.string.fandogh_test_all)
                        },
                        accent = FandoghColors.AccentGreen,
                        enabled = !testing,
                        onClick = onTestAll
                    )
                }
            }

            // Most people do not want to read a latency table, they want the best one.
            if (servers.size > 1) {
                Spacer(Modifier.height(FandoghSpace.lg))
                AutoSelectRow(enabled = !testing, onClick = onAutoSelect)
            }

            if (servers.size > 6) {
                Spacer(Modifier.height(FandoghSpace.lg))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = {
                        Text(
                            stringResource(R.string.fandogh_search_servers),
                            color = FandoghColors.TextTertiary,
                            fontSize = 14.sp
                        )
                    },
                    keyboardOptions = KeyboardOptions.Default,
                    shape = RoundedCornerShape(FandoghRadius.tile),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = FandoghColors.TextPrimary,
                        unfocusedTextColor = FandoghColors.TextPrimary,
                        focusedBorderColor = FandoghColors.AccentBlue,
                        unfocusedBorderColor = FandoghColors.Border,
                        cursorColor = FandoghColors.AccentBlueBright,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent
                    )
                )
            }

            Spacer(Modifier.height(FandoghSpace.lg))

            when {
                servers.isEmpty() -> EmptyServers(onAddSubscription)
                filtered.isEmpty() -> NoMatches(query)
                // Deliberately an exact height rather than heightIn(max): a lazy list
                // that wraps its content inside a bottom sheet feeds its measured height
                // back into the sheet, which resizes, which changes how many rows are
                // visible — the sheet then hunts up and down instead of settling. Rows
                // are a fixed height, so the total can be computed outright and the
                // sheet has nothing left to chase.
                else -> LazyColumn(
                    modifier = Modifier.height(listHeight),
                    verticalArrangement = Arrangement.spacedBy(FandoghSpace.sm)
                ) {
                    items(filtered, key = { it.guid }) { server ->
                        ServerRow(
                            server = server,
                            selected = server.guid == selectedGuid,
                            onClick = { onSelect(server.guid) }
                        )
                    }
                }
            }

            Spacer(Modifier.height(FandoghSpace.xxl))
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
private fun NoMatches(query: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = FandoghSpace.xxl),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            stringResource(R.string.fandogh_no_matches, query),
            color = FandoghColors.TextSecondary,
            fontSize = 15.sp,
            textAlign = TextAlign.Center
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
