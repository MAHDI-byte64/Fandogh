package com.v2ray.ang.ui.fandogh

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.v2ray.ang.R
import androidx.compose.foundation.BorderStroke

/** Everything the home screen needs, resolved by the activity from the real services. */
data class HomeState(
    val connected: Boolean = false,
    val connecting: Boolean = false,
    val statusText: String = "",
    val detailText: String? = null,
    val protocol: String? = null,
    val serverName: String? = null,
    val serverDetail: String? = null
)

@Composable
fun HomeTab(
    state: HomeState,
    onToggle: () -> Unit,
    onOpenSettings: () -> Unit,
    onPickServer: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(12.dp))
        HomeHeader(onOpenSettings = onOpenSettings)

        Spacer(Modifier.height(20.dp))
        StatusCard(state)

        // The connect control owns the vertical middle of the screen regardless of how
        // much text the status card needed.
        Box(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentAlignment = Alignment.Center
        ) {
            ConnectButton(
                connected = state.connected,
                connecting = state.connecting,
                label = if (state.connected) {
                    stringResource(R.string.fandogh_disconnect)
                } else {
                    stringResource(R.string.fandogh_connect)
                },
                onClick = onToggle
            )
        }

        ServerCard(state, onPickServer)
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun HomeHeader(onOpenSettings: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FandoghLogo(Modifier.size(44.dp))
        Spacer(Modifier.size(12.dp))
        Text(
            text = stringResource(R.string.app_name),
            color = FandoghColors.TextPrimary,
            fontSize = 27.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(FandoghColors.SurfaceStrong)
                .clickable(onClick = onOpenSettings),
            contentAlignment = Alignment.Center
        ) {
            GearGlyph(Modifier.size(23.dp))
        }
    }
}

@Composable
private fun StatusCard(state: HomeState) {
    GlassCard(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Canvas(Modifier.size(10.dp)) {
                        drawCircle(
                            if (state.connected) FandoghColors.StatusOn else FandoghColors.StatusOff
                        )
                    }
                    Spacer(Modifier.size(10.dp))
                    Text(
                        text = state.statusText,
                        color = FandoghColors.TextPrimary,
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (state.detailText != null) {
                    Text(
                        text = state.detailText,
                        color = FandoghColors.TextSecondary,
                        fontSize = 15.sp,
                        modifier = Modifier.padding(top = 4.dp, start = 20.dp)
                    )
                }
            }
            if (state.protocol != null) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = stringResource(R.string.fandogh_protocol).uppercase(),
                        color = FandoghColors.TextSecondary,
                        fontSize = 11.sp,
                        letterSpacing = 1.2.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = state.protocol,
                        color = FandoghColors.TextPrimary,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ServerCard(state: HomeState, onPickServer: () -> Unit) {
    GlassCard(onClick = onPickServer, contentPadding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(FandoghColors.SurfaceStrong)
                    .border(
                        BorderStroke(1.dp, FandoghColors.Border),
                        RoundedCornerShape(16.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                GlobeGlyph(Modifier.size(28.dp))
            }
            Spacer(Modifier.size(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = state.serverName ?: stringResource(R.string.fandogh_no_server),
                    color = FandoghColors.TextPrimary,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Text(
                    text = state.serverDetail ?: stringResource(R.string.fandogh_no_server_hint),
                    color = FandoghColors.TextSecondary,
                    fontSize = 14.sp,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Text("›", color = FandoghColors.TextTertiary, fontSize = 28.sp)
        }
    }
}

/** Settings cog, drawn so no extended-icon dependency is needed. */
@Composable
fun GearGlyph(modifier: Modifier = Modifier, color: Color = FandoghColors.TextPrimary) {
    Canvas(modifier) {
        val c = androidx.compose.ui.geometry.Offset(size.width / 2, size.height / 2)
        val outer = size.minDimension * 0.46f
        val inner = size.minDimension * 0.30f
        // Eight teeth around the rim.
        repeat(8) { i ->
            val angle = (Math.PI * 2 / 8 * i).toFloat()
            val tx = c.x + kotlin.math.cos(angle) * outer
            val ty = c.y + kotlin.math.sin(angle) * outer
            drawCircle(color, radius = size.minDimension * 0.085f, center = androidx.compose.ui.geometry.Offset(tx, ty))
        }
        drawCircle(color, radius = inner, center = c)
        drawCircle(Color(0xFF1B2E4D), radius = inner * 0.45f, center = c)
    }
}

/** Simple globe used as the server tile glyph. */
@Composable
fun GlobeGlyph(modifier: Modifier = Modifier, color: Color = FandoghColors.AccentBlueBright) {
    Canvas(modifier) {
        val r = size.minDimension / 2 - 1.dp.toPx()
        val c = androidx.compose.ui.geometry.Offset(size.width / 2, size.height / 2)
        val stroke = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.6.dp.toPx())
        drawCircle(color, radius = r, center = c, style = stroke)
        drawLine(color, androidx.compose.ui.geometry.Offset(c.x - r, c.y), androidx.compose.ui.geometry.Offset(c.x + r, c.y), strokeWidth = 1.6.dp.toPx())
        drawOval(
            color = color,
            topLeft = androidx.compose.ui.geometry.Offset(c.x - r * 0.5f, c.y - r),
            size = androidx.compose.ui.geometry.Size(r, r * 2),
            style = stroke
        )
    }
}
