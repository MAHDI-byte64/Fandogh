package com.v2ray.ang.ui.fandogh

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

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
