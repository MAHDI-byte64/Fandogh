package com.v2ray.ang.ui.fandogh

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp

/**
 * The translucent bordered panel used for every grouped block in the app.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: androidx.compose.foundation.layout.PaddingValues =
        androidx.compose.foundation.layout.PaddingValues(20.dp),
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(FandoghRadius.card)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(FandoghColors.Surface)
            .border(BorderStroke(1.dp, FandoghColors.Border), shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(contentPadding),
        content = content
    )
}

/** Small uppercase heading that introduces a group of cards. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        color = FandoghColors.AccentBlue,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.4.sp,
        modifier = modifier
    )
}

/** Screen title plus subtitle, matching the Stats / Profile / Settings headers. */
@Composable
fun ScreenHeader(
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    titleSize: androidx.compose.ui.unit.TextUnit = 29.sp
) {
    Column(modifier) {
        Text(
            text = title,
            color = FandoghColors.TextPrimary,
            fontSize = titleSize,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                color = FandoghColors.TextSecondary,
                fontSize = 14.sp,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = FandoghSpace.xs)
            )
        }
    }
}

/** Rounded selectable chip used for categories, priorities and Highest/Lowest toggles. */
@Composable
fun FandoghChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selectedColor: Color = FandoghColors.AccentBlue
) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier = modifier
            .clip(shape)
            .background(if (selected) selectedColor.copy(alpha = 0.18f) else FandoghColors.Surface)
            .border(
                BorderStroke(1.dp, if (selected) selectedColor else FandoghColors.Border),
                shape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 11.dp)
    ) {
        Text(
            text = label,
            color = if (selected) selectedColor else FandoghColors.TextSecondary,
            fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

/** Full-width gradient action button (Send, Upgrade plan, Allow access). */
@Composable
fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    brush: Brush = FandoghColors.CtaGradient
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(FandoghRadius.pill))
            // Dimmed rather than hidden: a button that vanishes leaves the user
            // wondering where the feature went.
            .alpha(if (enabled) 1f else 0.45f)
            .background(brush)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/**
 * The home screen's centrepiece.
 *
 * The label is measured against the face rather than set at a fixed size: "DISCONNECT" is
 * half again as wide as "CONNECT" and used to spill past the circle it sits in. Ripples
 * expand outward while connected, so the dial reads as live at a glance without becoming
 * a distraction, and the whole control dips slightly on press for tactile feedback.
 */
@Composable
fun ConnectButton(
    connected: Boolean,
    connecting: Boolean,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    diameter: androidx.compose.ui.unit.Dp = 300.dp
) {
    val transition = rememberInfiniteTransition(label = "connect")

    val sweep by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing)),
        label = "sweep"
    )
    // Two ripples a half-cycle apart, so one is always mid-flight.
    val rippleA by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing)),
        label = "rippleA"
    )
    val rippleB by transition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing)),
        label = "rippleB"
    )
    val breathe by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Reverse),
        label = "breathe"
    )

    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "press"
    )

    val accent by animateColorAsState(
        targetValue = if (connected) FandoghColors.AccentGreen else FandoghColors.AccentBlue,
        animationSpec = tween(500),
        label = "dialAccent"
    )
    val labelColor by animateColorAsState(
        targetValue = if (connected) FandoghColors.AccentGreenBright else Color.White,
        animationSpec = tween(500),
        label = "dialLabel"
    )

    Box(
        modifier = modifier
            .size(diameter)
            .graphicsLayer { scaleX = scale; scaleY = scale },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(diameter)) {
            val c = Offset(size.width / 2f, size.height / 2f)
            val base = size.minDimension / 2f
            val faceRadius = base * 0.60f

            // Static guide rings, barely there when idle.
            listOf(1.00f, 0.84f).forEach { scaleF ->
                drawCircle(
                    color = Color.White.copy(alpha = if (connected) 0.07f else 0.05f),
                    radius = base * scaleF,
                    center = c,
                    style = Stroke(width = 1.dp.toPx())
                )
            }

            // Ripples travelling from the face out to the rim while connected.
            if (connected) {
                listOf(rippleA, rippleB).forEach { raw ->
                    val t = raw % 1f
                    val radius = faceRadius + (base - faceRadius) * t
                    drawCircle(
                        color = accent.copy(alpha = 0.34f * (1f - t)),
                        radius = radius,
                        center = c,
                        style = Stroke(width = 1.6.dp.toPx())
                    )
                }
            }

            // Glow behind the face, breathing while connected.
            val glowAlpha = when {
                connected -> 0.22f + 0.14f * breathe
                connecting -> 0.18f
                else -> 0.12f
            }
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(accent.copy(alpha = glowAlpha), Color.Transparent),
                    center = c,
                    radius = faceRadius * 1.5f
                ),
                radius = faceRadius * 1.5f,
                center = c
            )

            // Face.
            drawCircle(color = Color(0xFF12233E).copy(alpha = 0.9f), radius = faceRadius, center = c)
            drawCircle(
                color = if (connected) accent.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.28f),
                radius = faceRadius,
                center = c,
                style = Stroke(width = if (connected) 2.dp.toPx() else 1.5.dp.toPx())
            )

            if (connecting) {
                drawArc(
                    color = accent,
                    startAngle = sweep,
                    sweepAngle = 90f,
                    useCenter = false,
                    topLeft = Offset(c.x - faceRadius, c.y - faceRadius),
                    size = Size(faceRadius * 2, faceRadius * 2),
                    style = Stroke(width = 3.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
                )
            }
        }

        // The label has to sit inside a circle, so it is sized from the chord available
        // at its baseline rather than from a constant.
        val faceDiameter = diameter * 0.60f
        val labelSize = ((faceDiameter.value * 1.55f) / label.length.coerceAtLeast(7))
            .coerceIn(11f, 17f)

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .clip(CircleShape)
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = onClick
                )
                .padding(diameter * 0.12f)
        ) {
            ShieldGlyph(
                color = labelColor,
                filled = connected,
                modifier = Modifier.size(diameter * 0.16f)
            )
            Spacer(Modifier.height(diameter * 0.04f))
            Text(
                text = label.uppercase(),
                color = labelColor,
                fontSize = labelSize.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (labelSize * 0.14f).sp,
                maxLines = 1,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * The hazelnut mark, drawn with the same geometry as the launcher icon.
 *
 * Deliberately not loaded via painterResource(R.mipmap.ic_launcher): from API 26 that
 * resource resolves to an AdaptiveIconDrawable, which Compose's painterResource cannot
 * decode — it throws, taking the whole screen down before first frame.
 */
@Composable
fun FandoghLogo(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        // Work in the icon's 108-unit design space, then scale to the drawn size.
        val s = size.minDimension / 108f
        fun p(x: Float, y: Float) = Offset(x * s, y * s)

        val corner = androidx.compose.ui.geometry.CornerRadius(24f * s, 24f * s)
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFF1B3358), Color(0xFF122744), Color(0xFF0A1526)),
                start = Offset(0f, 0f),
                end = Offset(size.width, size.height)
            ),
            size = size,
            cornerRadius = corner
        )

        val nut = Path().apply {
            moveTo(54 * s, 30 * s)
            cubicTo(69 * s, 30 * s, 80 * s, 43 * s, 80 * s, 59 * s)
            cubicTo(80 * s, 75 * s, 68 * s, 87 * s, 54 * s, 87 * s)
            cubicTo(40 * s, 87 * s, 28 * s, 75 * s, 28 * s, 59 * s)
            cubicTo(28 * s, 43 * s, 39 * s, 30 * s, 54 * s, 30 * s)
            close()
        }
        drawPath(
            nut,
            Brush.linearGradient(
                colors = listOf(Color(0xFFF0B978), Color(0xFFD98F45), Color(0xFFA25F27)),
                start = p(28f, 32f),
                end = p(80f, 87f)
            )
        )

        val husk = Path().apply {
            moveTo(28 * s, 44 * s)
            cubicTo(28 * s, 33 * s, 39 * s, 24 * s, 54 * s, 24 * s)
            cubicTo(69 * s, 24 * s, 80 * s, 33 * s, 80 * s, 44 * s)
            var x = 80f
            repeat(4) {
                quadraticTo((x - 6.5f) * s, 51.5f * s, (x - 13f) * s, 44 * s)
                x -= 13f
            }
            close()
        }
        drawPath(
            husk,
            Brush.linearGradient(
                colors = listOf(Color(0xFF3FD9A0), Color(0xFF2BB6B4), Color(0xFF2E8FE0)),
                start = p(28f, 24f),
                end = p(80f, 51f)
            )
        )

        val stem = Path().apply {
            moveTo(52 * s, 27 * s)
            cubicTo(51 * s, 21 * s, 52 * s, 17 * s, 55 * s, 14 * s)
            cubicTo(58 * s, 17 * s, 57 * s, 23 * s, 56 * s, 27 * s)
            close()
        }
        drawPath(stem, Color(0xFF2E8FE0))
    }
}

/** Shield outline drawn directly so the icon needs no extended-icons dependency. */
@Composable
fun ShieldGlyph(color: Color, filled: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.5f, h * 0.06f)
            cubicTo(w * 0.5f, h * 0.06f, w * 0.86f, h * 0.20f, w * 0.90f, h * 0.22f)
            lineTo(w * 0.90f, h * 0.52f)
            cubicTo(w * 0.90f, h * 0.76f, w * 0.70f, h * 0.90f, w * 0.5f, h * 0.96f)
            cubicTo(w * 0.30f, h * 0.90f, w * 0.10f, h * 0.76f, w * 0.10f, h * 0.52f)
            lineTo(w * 0.10f, h * 0.22f)
            cubicTo(w * 0.14f, h * 0.20f, w * 0.5f, h * 0.06f, w * 0.5f, h * 0.06f)
            close()
        }
        if (filled) {
            drawPath(path, color.copy(alpha = 0.22f))
        }
        drawPath(path, color, style = Stroke(width = size.minDimension * 0.075f))
    }
}

/**
 * Ring gauge for the data-usage card. [fraction] is clamped to 0..1; the track stays
 * visible underneath so an empty allowance still reads as a ring rather than nothing.
 */
@Composable
fun DonutGauge(
    fraction: Float,
    modifier: Modifier = Modifier,
    trackColor: Color = Color.White.copy(alpha = 0.08f),
    progressBrush: Brush = Brush.sweepGradient(
        listOf(FandoghColors.AccentGreen, FandoghColors.AccentBlueBright, FandoghColors.AccentGreen)
    ),
    centerContent: @Composable () -> Unit
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        // Fills the box rather than a fixed 170dp, which used to overflow whenever the
        // caller asked for a smaller gauge.
        Canvas(Modifier.fillMaxSize()) {
            val stroke = (size.minDimension * 0.11f).coerceIn(10.dp.toPx(), 20.dp.toPx())
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            )
            val safe = fraction.coerceIn(0f, 1f)
            if (safe > 0f) {
                drawArc(
                    brush = progressBrush,
                    startAngle = -90f,
                    sweepAngle = 360f * safe,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                )
            }
        }
        centerContent()
    }
}

/** Labelled metric with a coloured underline, as used beside the usage donut. */
@Composable
fun MetricRow(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier,
    markerFilled: Boolean = true
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.size(10.dp)) {
                    if (markerFilled) {
                        drawCircle(accent)
                    } else {
                        drawCircle(accent, style = Stroke(width = 2.dp.toPx()))
                    }
                }
                Spacer(Modifier.size(8.dp))
                Text(label, color = FandoghColors.TextSecondary, fontSize = 15.sp)
            }
            Text(
                value,
                color = FandoghColors.TextPrimary,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(2.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(accent.copy(alpha = 0.65f))
        )
    }
}

/** Row that navigates somewhere, with a tinted leading square and a chevron. */
@Composable
fun NavRow(
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null
) {
    GlassCard(modifier = modifier, onClick = onClick, contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (leading != null) {
                leading()
                Spacer(Modifier.size(16.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, color = FandoghColors.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                if (subtitle != null) {
                    Text(
                        subtitle,
                        color = FandoghColors.TextSecondary,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
            Text("›", color = FandoghColors.TextTertiary, fontSize = 26.sp)
        }
    }
}

/** Tinted rounded square that holds a small glyph at the start of a [NavRow]. */
@Composable
fun LeadingTile(tint: Color, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(tint.copy(alpha = 0.16f))
            .border(BorderStroke(1.dp, tint.copy(alpha = 0.35f)), RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center,
        content = { content() }
    )
}


/**
 * Gauge plus its breakdown, laid out side by side when the card is wide enough and
 * stacked when it is not. A fixed 170dp ring beside three metric rows overflows on
 * narrow screens, which is what pushed values off the edge before.
 */
@Composable
fun GaugeBreakdown(
    fraction: Float,
    modifier: Modifier = Modifier,
    gaugeCenter: @Composable () -> Unit,
    metrics: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    androidx.compose.foundation.layout.BoxWithConstraints(modifier.fillMaxWidth()) {
        val stacked = maxWidth < 330.dp
        val gaugeSize = if (stacked) 150.dp else 160.dp

        if (stacked) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                DonutGauge(fraction = fraction, modifier = Modifier.size(gaugeSize)) { gaugeCenter() }
                Spacer(Modifier.height(FandoghSpace.lg))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(FandoghSpace.md),
                    content = metrics
                )
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DonutGauge(fraction = fraction, modifier = Modifier.size(gaugeSize)) { gaugeCenter() }
                Spacer(Modifier.size(FandoghSpace.lg))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(FandoghSpace.md),
                    content = metrics
                )
            }
        }
    }
}

/**
 * Square tile identifying a server by its country flag.
 *
 * Falls back to the globe when the name names no country, so an unrecognised server
 * still gets a tile of the same size and the rows stay aligned.
 */
@Composable
fun ServerFlagTile(
    serverName: String?,
    size: Dp,
    modifier: Modifier = Modifier,
    tint: Color = FandoghColors.AccentBlue
) {
    val flag = remember(serverName) { serverName?.let { CountryFlags.forName(it) } }
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(FandoghRadius.tile))
            .background(tint.copy(alpha = 0.14f))
            .border(
                BorderStroke(1.dp, tint.copy(alpha = 0.32f)),
                RoundedCornerShape(FandoghRadius.tile)
            ),
        contentAlignment = Alignment.Center
    ) {
        if (flag != null) {
            Text(
                text = flag,
                // Emoji render tall for their point size; this keeps the glyph inside
                // the tile instead of clipping against the rounded corners.
                fontSize = (size.value * 0.46f).sp,
                lineHeight = (size.value * 0.52f).sp
            )
        } else {
            GlobeGlyph(Modifier.size(size * 0.5f))
        }
    }
}
