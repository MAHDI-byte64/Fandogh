package com.v2ray.ang.ui.fandogh

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
    val shape = RoundedCornerShape(22.dp)
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
fun ScreenHeader(title: String, subtitle: String?, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            text = title,
            color = FandoghColors.TextPrimary,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                color = FandoghColors.TextSecondary,
                fontSize = 15.sp,
                modifier = Modifier.padding(top = 4.dp)
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
    brush: Brush = FandoghColors.CtaGradient
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(58.dp)
            .clip(RoundedCornerShape(50))
            .background(brush)
            .clickable(onClick = onClick),
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
 * The home screen's centrepiece: concentric rings around a circular button.
 * [progress] drives the sweeping arc shown while a connection is being established.
 */
@Composable
fun ConnectButton(
    connected: Boolean,
    connecting: Boolean,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "connect")
    val sweep by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing)),
        label = "sweep"
    )
    // Rings breathe slowly while connected so the screen feels alive without distracting.
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulse"
    )

    val accent = if (connected) FandoghColors.AccentGreen else FandoghColors.AccentBlue

    Box(
        modifier = modifier.size(310.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(310.dp)) {
            val c = Offset(size.width / 2f, size.height / 2f)
            val base = size.minDimension / 2f

            // Three halo rings, fading outward.
            listOf(1.00f to 0.05f, 0.84f to 0.08f, 0.68f to 0.12f).forEach { (scale, alpha) ->
                val a = if (connected) alpha * (0.7f + 0.5f * pulse) else alpha
                drawCircle(
                    color = Color.White.copy(alpha = a),
                    radius = base * scale,
                    center = c,
                    style = Stroke(width = 1.dp.toPx())
                )
            }

            // Glow behind the button face.
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(accent.copy(alpha = if (connected) 0.30f else 0.16f), Color.Transparent),
                    center = c,
                    radius = base * 0.72f
                ),
                radius = base * 0.72f,
                center = c
            )

            // Button face.
            val faceRadius = base * 0.545f
            drawCircle(color = Color(0xFF12233E).copy(alpha = 0.85f), radius = faceRadius, center = c)
            drawCircle(
                color = Color.White.copy(alpha = 0.30f),
                radius = faceRadius,
                center = c,
                style = Stroke(width = 1.5.dp.toPx())
            )

            // Rotating arc while connecting.
            if (connecting) {
                drawArc(
                    color = accent,
                    startAngle = sweep,
                    sweepAngle = 90f,
                    useCenter = false,
                    topLeft = Offset(c.x - faceRadius, c.y - faceRadius),
                    size = Size(faceRadius * 2, faceRadius * 2),
                    style = Stroke(width = 3.dp.toPx())
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.clip(CircleShape).clickable(onClick = onClick).padding(30.dp)
        ) {
            ShieldGlyph(
                color = if (connected) FandoghColors.AccentGreen else Color.White,
                filled = connected,
                modifier = Modifier.size(52.dp)
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = label.uppercase(),
                color = if (connected) FandoghColors.AccentGreen else Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 3.sp,
                textAlign = TextAlign.Center
            )
        }
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
        Canvas(Modifier.size(170.dp)) {
            val stroke = 18.dp.toPx()
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
