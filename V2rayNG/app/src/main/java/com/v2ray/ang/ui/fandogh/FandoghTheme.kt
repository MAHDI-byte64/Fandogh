package com.v2ray.ang.ui.fandogh

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Fandogh's visual language: a deep navy field with translucent "glass" cards and a
 * green-to-blue accent. The palette is fixed rather than theme-reactive — the design
 * is a single dark treatment, so a light scheme would only dilute it.
 */
object FandoghColors {
    val BackgroundTop = Color(0xFF16294A)
    val BackgroundMid = Color(0xFF102039)
    val BackgroundBottom = Color(0xFF050C17)

    val Surface = Color(0x0DFFFFFF)          // 5% white — the card fill
    val SurfaceStrong = Color(0x1AFFFFFF)    // 10% white — pressed / selected
    val Border = Color(0x1FFFFFFF)           // 12% white — hairline card edge

    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFF9BABC4)
    val TextTertiary = Color(0xFF6B7C96)

    val AccentBlue = Color(0xFF3BA9F5)
    val AccentBlueBright = Color(0xFF56C7FF)
    val AccentGreen = Color(0xFF35D6A0)
    val AccentGreenBright = Color(0xFF48E8B0)

    val Danger = Color(0xFFFF6B6B)
    val Warning = Color(0xFFFFB020)

    /** Connected / disconnected status dots. */
    val StatusOn = AccentGreen
    val StatusOff = Color(0xFF7C8BA3)

    val CtaGradient = Brush.horizontalGradient(listOf(AccentGreen, AccentBlueBright))
    val UploadAccent = AccentGreen
    val DownloadAccent = AccentBlueBright
}

/** The full-bleed page background used by every Fandogh screen. */
fun Modifier.fandoghBackground(): Modifier = background(
    Brush.verticalGradient(
        0.0f to FandoghColors.BackgroundTop,
        0.45f to FandoghColors.BackgroundMid,
        1.0f to FandoghColors.BackgroundBottom
    )
)

private val FandoghColorScheme = darkColorScheme(
    primary = FandoghColors.AccentBlue,
    onPrimary = Color.White,
    secondary = FandoghColors.AccentGreen,
    onSecondary = Color(0xFF04121F),
    background = FandoghColors.BackgroundMid,
    onBackground = FandoghColors.TextPrimary,
    surface = FandoghColors.BackgroundMid,
    onSurface = FandoghColors.TextPrimary,
    surfaceVariant = FandoghColors.SurfaceStrong,
    onSurfaceVariant = FandoghColors.TextSecondary,
    outline = FandoghColors.Border,
    error = FandoghColors.Danger
)

@Composable
fun FandoghTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = FandoghColorScheme,
        typography = MaterialTheme.typography,
        content = content
    )
}

/**
 * Spacing scale. Every gap in the app is one of these, so vertical rhythm stays even
 * across screens instead of drifting with ad-hoc dp values.
 */
object FandoghSpace {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 28.dp
}

/** Corner radii, paired to component size: bigger surfaces get rounder corners. */
object FandoghRadius {
    val tile = 12.dp
    val card = 20.dp
    val sheet = 28.dp
    val pill = 50.dp
}

/**
 * Layout metrics resolved from the space the screen actually offers.
 *
 * The home screen has a fixed set of blocks and one flexible centrepiece; on a short or
 * split-screen window the centrepiece has to give way rather than push the server card
 * off the bottom. [compact] also lets dense screens drop to smaller type.
 */
@Immutable
data class FandoghMetrics(
    val compact: Boolean,
    val connectDiameter: Dp,
    val titleSize: TextUnit,
    val cardTitleSize: TextUnit,
    val bodySize: TextUnit
)

@Composable
fun rememberFandoghMetrics(maxHeight: Dp, maxWidth: Dp): FandoghMetrics {
    val compact = maxHeight < 680.dp
    // Reserve room for header, status card, server card and the tab bar, then let the
    // dial take what is left — bounded so it stays a dial and not a coaster or a wall.
    val available = maxHeight - 420.dp
    val diameter = available.coerceIn(180.dp, 320.dp).coerceAtMost(maxWidth - 48.dp)
    return remember(compact, diameter) {
        FandoghMetrics(
            compact = compact,
            connectDiameter = diameter,
            titleSize = if (compact) 26.sp else 30.sp,
            cardTitleSize = if (compact) 18.sp else 20.sp,
            bodySize = if (compact) 13.sp else 14.sp
        )
    }
}
