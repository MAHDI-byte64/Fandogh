package com.v2ray.ang.ui.fandogh

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.v2ray.ang.R

/** Everything the home screen needs, resolved by the activity from the real services. */
data class HomeState(
    val connected: Boolean = false,
    val connecting: Boolean = false,
    val statusText: String = "",
    val detailText: String? = null,
    val protocol: String? = null,
    val serverName: String? = null,
    val serverDetail: String? = null,
    val sessionSeconds: Long = 0,
    val downSpeed: Long = 0,
    val upSpeed: Long = 0,
    /** Null while a measurement is in flight, negative when the last one failed. */
    val latencyMillis: Long? = null
)

@Composable
fun HomeTab(
    state: HomeState,
    onToggle: () -> Unit,
    onOpenSettings: () -> Unit,
    onPickServer: () -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val metrics = rememberFandoghMetrics(maxHeight, maxWidth)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = FandoghSpace.xl)
        ) {
            Spacer(Modifier.height(FandoghSpace.md))
            HomeHeader(metrics = metrics, onOpenSettings = onOpenSettings)

            Spacer(Modifier.height(FandoghSpace.lg))
            StatusCard(state, metrics)

            // The dial owns whatever vertical space the fixed blocks leave, and is sized
            // from that rather than pinned, so nothing is pushed off a short screen.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                ConnectButton(
                    connected = state.connected,
                    connecting = state.connecting,
                    diameter = metrics.connectDiameter,
                    label = if (state.connected) {
                        stringResource(R.string.fandogh_disconnect)
                    } else {
                        stringResource(R.string.fandogh_connect)
                    },
                    onClick = onToggle
                )
            }

            // Figures are meaningless while disconnected, so the row folds away rather
            // than sitting there as a bank of zeroes.
            AnimatedVisibility(
                visible = state.connected,
                enter = fadeIn(tween(220)) + expandVertically(tween(260)),
                exit = fadeOut(tween(160)) + shrinkVertically(tween(200))
            ) {
                Column {
                    LiveTiles(state)
                    Spacer(Modifier.height(FandoghSpace.md))
                }
            }

            ServerCard(state, metrics, onPickServer)
            Spacer(Modifier.height(FandoghSpace.lg))
        }
    }
}

@Composable
private fun HomeHeader(metrics: FandoghMetrics, onOpenSettings: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FandoghLogo(Modifier.size(if (metrics.compact) 38.dp else 44.dp))
        Spacer(Modifier.width(FandoghSpace.md))
        Text(
            text = stringResource(R.string.app_name),
            color = FandoghColors.TextPrimary,
            fontSize = metrics.titleSize,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
        IconTile(onClick = onOpenSettings) { GearGlyph(Modifier.size(22.dp)) }
    }
}

/** Square tap target used for header actions. */
@Composable
private fun IconTile(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(46.dp)
            .clip(RoundedCornerShape(FandoghRadius.tile))
            .background(FandoghColors.SurfaceStrong)
            .border(
                BorderStroke(1.dp, FandoghColors.Border),
                RoundedCornerShape(FandoghRadius.tile)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
        content = { content() }
    )
}

@Composable
private fun StatusCard(state: HomeState, metrics: FandoghMetrics) {
    val dotColor by animateColorAsState(
        targetValue = if (state.connected) FandoghColors.StatusOn else FandoghColors.StatusOff,
        animationSpec = tween(400),
        label = "statusDot"
    )
    val titleColor by animateColorAsState(
        targetValue = if (state.connected) FandoghColors.AccentGreen else FandoghColors.TextPrimary,
        animationSpec = tween(400),
        label = "statusTitle"
    )

    GlassCard(
        contentPadding = PaddingValues(
            horizontal = FandoghSpace.xl,
            vertical = FandoghSpace.lg
        )
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Canvas(Modifier.size(9.dp)) { drawCircle(dotColor) }
                    Spacer(Modifier.width(FandoghSpace.sm))
                    Text(
                        text = state.statusText,
                        color = titleColor,
                        fontSize = metrics.cardTitleSize,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
                if (state.detailText != null) {
                    Text(
                        text = state.detailText,
                        color = FandoghColors.TextSecondary,
                        fontSize = metrics.bodySize,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp, start = 17.dp)
                    )
                }
            }

            if (state.protocol != null) {
                Spacer(Modifier.width(FandoghSpace.md))
                StatusMetric(
                    label = stringResource(R.string.fandogh_protocol),
                    value = state.protocol,
                    compact = metrics.compact
                )
            }
            if (state.connected) {
                Spacer(Modifier.width(FandoghSpace.lg))
                StatusMetric(
                    label = stringResource(R.string.fandogh_session),
                    value = formatDuration(state.sessionSeconds),
                    compact = metrics.compact
                )
            }
        }
    }
}

@Composable
private fun StatusMetric(label: String, value: String, compact: Boolean) {
    Column(horizontalAlignment = Alignment.End) {
        Text(
            text = label.uppercase(),
            color = FandoghColors.TextTertiary,
            fontSize = 10.sp,
            letterSpacing = 1.1.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
        Text(
            text = value,
            color = FandoghColors.TextPrimary,
            fontSize = if (compact) 15.sp else 17.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            modifier = Modifier.padding(top = 1.dp)
        )
    }
}

/** hh:mm:ss, as the reference status card shows it. */
private fun formatDuration(seconds: Long): String {
    val s = seconds.coerceAtLeast(0)
    return String.format("%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)
}

@Composable
private fun LiveTiles(state: HomeState) {
    // Samples land every few seconds; easing between them reads as a live meter rather
    // than a counter that lurches.
    val animatedDown by animateFloatAsState(
        targetValue = state.downSpeed.toFloat(),
        animationSpec = tween(700),
        label = "downSpeed"
    )
    val animatedUp by animateFloatAsState(
        targetValue = state.upSpeed.toFloat(),
        animationSpec = tween(700),
        label = "upSpeed"
    )

    Row(horizontalArrangement = Arrangement.spacedBy(FandoghSpace.md)) {
        val down = formatBytes(animatedDown.toLong())
        LiveTile(
            modifier = Modifier.weight(1f),
            label = stringResource(R.string.fandogh_download),
            value = down.first,
            unit = "${down.second}/s",
            accent = FandoghColors.DownloadAccent
        ) { tint -> TileArrow(down = true, color = tint, modifier = Modifier.size(18.dp)) }

        val up = formatBytes(animatedUp.toLong())
        LiveTile(
            modifier = Modifier.weight(1f),
            label = stringResource(R.string.fandogh_upload),
            value = up.first,
            unit = "${up.second}/s",
            accent = FandoghColors.UploadAccent
        ) { tint -> TileArrow(down = false, color = tint, modifier = Modifier.size(18.dp)) }

        val latency = state.latencyMillis
        LiveTile(
            modifier = Modifier.weight(1f),
            label = stringResource(R.string.fandogh_latency),
            value = when {
                latency == null -> "···"
                latency < 0 -> "—"
                else -> latency.toString()
            },
            unit = "ms",
            accent = latencyColor(latency)
        ) { tint -> PulseGlyph(tint, Modifier.size(18.dp)) }
    }
}

internal fun latencyColor(latency: Long?): Color = when {
    latency == null || latency < 0 -> FandoghColors.TextTertiary
    latency < 300 -> FandoghColors.AccentGreen
    latency < 800 -> FandoghColors.Warning
    else -> FandoghColors.Danger
}

@Composable
private fun LiveTile(
    label: String,
    value: String,
    unit: String,
    accent: Color,
    modifier: Modifier = Modifier,
    icon: @Composable (Color) -> Unit
) {
    GlassCard(modifier = modifier, contentPadding = PaddingValues(FandoghSpace.md)) {
        Box(
            Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(accent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) { icon(accent) }

        Spacer(Modifier.height(FandoghSpace.sm))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                color = FandoghColors.TextPrimary,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Spacer(Modifier.width(3.dp))
            Text(
                text = unit,
                color = accent,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                modifier = Modifier.padding(bottom = 3.dp)
            )
        }
        Text(
            text = label.uppercase(),
            color = FandoghColors.TextTertiary,
            fontSize = 9.sp,
            letterSpacing = 0.9.sp,
            maxLines = 1,
            modifier = Modifier.padding(top = 1.dp)
        )
    }
}

@Composable
private fun ServerCard(state: HomeState, metrics: FandoghMetrics, onPickServer: () -> Unit) {
    GlassCard(onClick = onPickServer, contentPadding = PaddingValues(FandoghSpace.lg)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(if (metrics.compact) 44.dp else 50.dp)
                    .clip(RoundedCornerShape(FandoghRadius.tile))
                    .background(FandoghColors.AccentBlue.copy(alpha = 0.14f))
                    .border(
                        BorderStroke(1.dp, FandoghColors.AccentBlue.copy(alpha = 0.32f)),
                        RoundedCornerShape(FandoghRadius.tile)
                    ),
                contentAlignment = Alignment.Center
            ) {
                GlobeGlyph(Modifier.size(24.dp))
            }
            Spacer(Modifier.width(FandoghSpace.lg))
            Column(Modifier.weight(1f)) {
                Text(
                    text = state.serverName ?: stringResource(R.string.fandogh_no_server),
                    color = FandoghColors.TextPrimary,
                    fontSize = metrics.cardTitleSize,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = state.serverDetail ?: stringResource(R.string.fandogh_no_server_hint),
                    color = FandoghColors.TextSecondary,
                    fontSize = metrics.bodySize,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Spacer(Modifier.width(FandoghSpace.sm))
            ChevronGlyph(Modifier.size(18.dp))
        }
    }
}

/** Settings cog, drawn so no extended-icon dependency is needed. */
@Composable
fun GearGlyph(modifier: Modifier = Modifier, color: Color = FandoghColors.TextPrimary) {
    Canvas(modifier) {
        val c = androidx.compose.ui.geometry.Offset(size.width / 2, size.height / 2)
        val outer = size.minDimension * 0.44f
        val inner = size.minDimension * 0.28f
        repeat(8) { i ->
            val angle = (Math.PI * 2 / 8 * i).toFloat()
            drawCircle(
                color = color,
                radius = size.minDimension * 0.08f,
                center = androidx.compose.ui.geometry.Offset(
                    c.x + kotlin.math.cos(angle) * outer,
                    c.y + kotlin.math.sin(angle) * outer
                )
            )
        }
        drawCircle(color, radius = inner, center = c)
        drawCircle(Color(0xFF16294A), radius = inner * 0.42f, center = c)
    }
}

/** Simple globe used as the server tile glyph. */
@Composable
fun GlobeGlyph(modifier: Modifier = Modifier, color: Color = FandoghColors.AccentBlueBright) {
    Canvas(modifier) {
        val r = size.minDimension / 2 - 1.dp.toPx()
        val c = androidx.compose.ui.geometry.Offset(size.width / 2, size.height / 2)
        val stroke = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx())
        drawCircle(color, radius = r, center = c, style = stroke)
        drawLine(
            color,
            androidx.compose.ui.geometry.Offset(c.x - r, c.y),
            androidx.compose.ui.geometry.Offset(c.x + r, c.y),
            strokeWidth = 1.5.dp.toPx()
        )
        drawOval(
            color = color,
            topLeft = androidx.compose.ui.geometry.Offset(c.x - r * 0.52f, c.y - r),
            size = androidx.compose.ui.geometry.Size(r * 1.04f, r * 2),
            style = stroke
        )
    }
}

/** Chevron drawn rather than typed, so it never inherits font quirks or RTL mirroring. */
@Composable
fun ChevronGlyph(modifier: Modifier = Modifier, color: Color = FandoghColors.TextTertiary) {
    Canvas(modifier) {
        val stroke = androidx.compose.ui.graphics.drawscope.Stroke(
            width = 2.dp.toPx(),
            cap = androidx.compose.ui.graphics.StrokeCap.Round,
            join = androidx.compose.ui.graphics.StrokeJoin.Round
        )
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(size.width * 0.36f, size.height * 0.22f)
            lineTo(size.width * 0.66f, size.height * 0.5f)
            lineTo(size.width * 0.36f, size.height * 0.78f)
        }
        drawPath(path, color, style = stroke)
    }
}

@Composable
private fun TileArrow(down: Boolean, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = androidx.compose.ui.graphics.drawscope.Stroke(
            width = 2.dp.toPx(),
            cap = androidx.compose.ui.graphics.StrokeCap.Round,
            join = androidx.compose.ui.graphics.StrokeJoin.Round
        )
        val tip = if (down) h * 0.82f else h * 0.18f
        val tail = if (down) h * 0.18f else h * 0.82f
        val wing = if (down) h * 0.54f else h * 0.46f
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.5f, tail)
            lineTo(w * 0.5f, tip)
            moveTo(w * 0.24f, wing)
            lineTo(w * 0.5f, tip)
            lineTo(w * 0.76f, wing)
        }
        drawPath(path, color, style = stroke)
    }
}

/** Concentric arcs suggesting a signal probe, used for the latency tile. */
@Composable
private fun PulseGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val c = androidx.compose.ui.geometry.Offset(size.width * 0.5f, size.height * 0.78f)
        drawCircle(color, radius = size.minDimension * 0.10f, center = c)
        listOf(0.40f, 0.68f).forEach { scale ->
            val r = size.minDimension * scale
            drawArc(
                color = color,
                startAngle = 200f,
                sweepAngle = 140f,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(c.x - r, c.y - r),
                size = androidx.compose.ui.geometry.Size(r * 2, r * 2),
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = 1.8.dp.toPx(),
                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                )
            )
        }
    }
}
