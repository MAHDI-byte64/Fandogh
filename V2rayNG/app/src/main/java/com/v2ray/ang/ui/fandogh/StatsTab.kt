package com.v2ray.ang.ui.fandogh

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.v2ray.ang.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun StatsTab(
    totals: TrafficTracker.Totals,
    quotaBytes: Long?,
    connected: Boolean,
    speedPhase: SpeedTestRunner.Phase,
    onRunSpeedTest: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(16.dp))
        ScreenHeader(
            title = stringResource(R.string.fandogh_stats),
            subtitle = stringResource(R.string.fandogh_stats_subtitle)
        )

        Spacer(Modifier.height(20.dp))
        UsageCard(totals = totals, quotaBytes = quotaBytes)

        Spacer(Modifier.height(26.dp))
        Text(
            text = stringResource(R.string.fandogh_today),
            color = FandoghColors.TextPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            TodayCard(
                modifier = Modifier.weight(1f),
                label = stringResource(R.string.fandogh_upload),
                bytes = totals.todayUp,
                accent = FandoghColors.UploadAccent,
                up = true
            )
            TodayCard(
                modifier = Modifier.weight(1f),
                label = stringResource(R.string.fandogh_download),
                bytes = totals.todayDown,
                accent = FandoghColors.DownloadAccent,
                up = false
            )
        }

        Spacer(Modifier.height(26.dp))
        LiveSpeedCard(totals, connected)

        Spacer(Modifier.height(26.dp))
        SpeedTestCard(phase = speedPhase, connected = connected, onRun = onRunSpeedTest)
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun UsageCard(totals: TrafficTracker.Totals, quotaBytes: Long?) {
    val used = totals.monthTotal
    val fraction = if (quotaBytes != null && quotaBytes > 0) used.toFloat() / quotaBytes else 0f
    val monthLabel = remember { SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date()) }

    GlassCard(contentPadding = PaddingValues(20.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Text(
                stringResource(R.string.fandogh_data_usage),
                color = FandoghColors.TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Text(monthLabel, color = FandoghColors.TextSecondary, fontSize = 15.sp)
        }

        Spacer(Modifier.height(18.dp))
        GaugeBreakdown(
            fraction = fraction,
            gaugeCenter = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (quotaBytes != null && quotaBytes > 0) {
                        Text(
                            text = "${(fraction * 100).toInt()}%",
                            color = FandoghColors.TextPrimary,
                            fontSize = 34.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            stringResource(R.string.fandogh_used).uppercase(),
                            color = FandoghColors.TextSecondary,
                            fontSize = 11.sp,
                            letterSpacing = 1.1.sp
                        )
                    } else {
                        Text(
                            text = formatBytes(used).first,
                            color = FandoghColors.TextPrimary,
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            formatBytes(used).second,
                            color = FandoghColors.TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        ) {
            MetricRow(
                label = stringResource(R.string.fandogh_upload),
                value = formatBytesLabel(totals.monthUp),
                accent = FandoghColors.UploadAccent,
                markerFilled = false
            )
            MetricRow(
                label = stringResource(R.string.fandogh_download),
                value = formatBytesLabel(totals.monthDown),
                accent = FandoghColors.DownloadAccent
            )
            MetricRow(
                label = if (quotaBytes != null && quotaBytes > 0) {
                    stringResource(R.string.fandogh_total)
                } else {
                    stringResource(R.string.fandogh_used)
                },
                value = formatBytesLabel(quotaBytes?.takeIf { it > 0 } ?: used),
                accent = FandoghColors.TextTertiary
            )
        }
    }
}

@Composable
private fun TodayCard(
    label: String,
    bytes: Long,
    accent: Color,
    up: Boolean,
    modifier: Modifier = Modifier
) {
    GlassCard(modifier = modifier, contentPadding = PaddingValues(18.dp)) {
        Box(
            Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(accent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            ArrowGlyph(up = up, color = accent, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(label, color = FandoghColors.TextSecondary, fontSize = 15.sp)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = formatBytes(bytes).first,
                color = FandoghColors.TextPrimary,
                fontSize = 27.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.size(5.dp))
            Text(
                text = formatBytes(bytes).second,
                color = accent,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
    }
}

/**
 * Live throughput, with the reason shown when there is nothing to display.
 *
 * Samples come from the daemon process over a broadcast, so "no numbers" has three quite
 * different causes — not connected, connected but the first sample has not landed, or
 * genuinely idle. Collapsing all three into a row of zeroes made a working screen look
 * broken, so each says what it is.
 */
@Composable
private fun LiveSpeedCard(totals: TrafficTracker.Totals, connected: Boolean) {
    GlassCard(contentPadding = PaddingValues(FandoghSpace.xl)) {
        Text(
            stringResource(R.string.fandogh_live_speed),
            color = FandoghColors.TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(FandoghSpace.md))

        when {
            !connected -> StatusLine(stringResource(R.string.fandogh_live_disconnected))

            !totals.hasLiveData -> StatusLine(stringResource(R.string.fandogh_live_waiting))

            else -> Row(horizontalArrangement = Arrangement.spacedBy(FandoghSpace.xxl)) {
                SpeedReadout(
                    label = stringResource(R.string.fandogh_download),
                    speed = totals.downSpeed,
                    accent = FandoghColors.DownloadAccent
                )
                SpeedReadout(
                    label = stringResource(R.string.fandogh_upload),
                    speed = totals.upSpeed,
                    accent = FandoghColors.UploadAccent
                )
            }
        }
    }
}

@Composable
private fun StatusLine(text: String) {
    Text(
        text = text,
        color = FandoghColors.TextSecondary,
        fontSize = 14.sp
    )
}

@Composable
private fun SpeedReadout(label: String, speed: Long, accent: Color) {
    Column {
        Text(label, color = FandoghColors.TextSecondary, fontSize = 14.sp)
        Text(
            text = "${formatBytesLabel(speed)}/s",
            color = accent,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun ArrowGlyph(up: Boolean, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = androidx.compose.ui.graphics.drawscope.Stroke(
            width = 2.2.dp.toPx(),
            cap = androidx.compose.ui.graphics.StrokeCap.Round,
            join = androidx.compose.ui.graphics.StrokeJoin.Round
        )
        val path = androidx.compose.ui.graphics.Path().apply {
            if (up) {
                moveTo(w * 0.16f, h * 0.74f)
                lineTo(w * 0.44f, h * 0.42f)
                lineTo(w * 0.62f, h * 0.58f)
                lineTo(w * 0.86f, h * 0.26f)
            } else {
                moveTo(w * 0.16f, h * 0.30f)
                lineTo(w * 0.44f, h * 0.60f)
                lineTo(w * 0.62f, h * 0.44f)
                lineTo(w * 0.86f, h * 0.74f)
            }
        }
        drawPath(path, color, style = stroke)
        // Arrow head at the end of the trend line.
        val head = androidx.compose.ui.graphics.Path().apply {
            if (up) {
                moveTo(w * 0.64f, h * 0.26f); lineTo(w * 0.86f, h * 0.26f); lineTo(w * 0.86f, h * 0.48f)
            } else {
                moveTo(w * 0.64f, h * 0.74f); lineTo(w * 0.86f, h * 0.74f); lineTo(w * 0.86f, h * 0.52f)
            }
        }
        drawPath(head, color, style = stroke)
    }
}

/**
 * On-demand throughput measurement.
 *
 * The live readout above only shows traffic the user happens to be generating, so it
 * reads zero on an idle connection. This deliberately generates traffic, which is the
 * only way to answer "how fast is this server actually".
 */
@Composable
private fun SpeedTestCard(
    phase: SpeedTestRunner.Phase,
    connected: Boolean,
    onRun: () -> Unit
) {
    GlassCard(contentPadding = PaddingValues(20.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.fandogh_speed_test),
                    color = FandoghColors.TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = when (phase) {
                        is SpeedTestRunner.Phase.Failed ->
                            stringResource(
                                if (phase.reason == SpeedTestRunner.Reason.NotConnected) {
                                    R.string.fandogh_speed_test_offline
                                } else {
                                    R.string.fandogh_speed_test_failed
                                }
                            )

                        else -> stringResource(R.string.fandogh_speed_test_hint)
                    },
                    color = FandoghColors.TextSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }

        Spacer(Modifier.height(18.dp))

        when (phase) {
            is SpeedTestRunner.Phase.Download, is SpeedTestRunner.Phase.Upload,
            SpeedTestRunner.Phase.Connecting -> RunningReadout(phase)

            is SpeedTestRunner.Phase.Done -> Row(Modifier.fillMaxWidth()) {
                ResultColumn(
                    modifier = Modifier.weight(1f),
                    label = stringResource(R.string.fandogh_download),
                    mbps = phase.downloadMbps,
                    accent = FandoghColors.DownloadAccent
                )
                ResultColumn(
                    modifier = Modifier.weight(1f),
                    label = stringResource(R.string.fandogh_upload),
                    mbps = phase.uploadMbps,
                    accent = FandoghColors.UploadAccent
                )
            }

            else -> Unit
        }

        if (phase !is SpeedTestRunner.Phase.Connecting &&
            phase !is SpeedTestRunner.Phase.Download &&
            phase !is SpeedTestRunner.Phase.Upload
        ) {
            Spacer(Modifier.height(if (phase is SpeedTestRunner.Phase.Done) 18.dp else 0.dp))
            GradientButton(
                text = stringResource(
                    if (phase is SpeedTestRunner.Phase.Done) {
                        R.string.fandogh_speed_test_again
                    } else {
                        R.string.fandogh_speed_test_start
                    }
                ),
                enabled = connected,
                onClick = onRun
            )
        }
    }
}

@Composable
private fun RunningReadout(phase: SpeedTestRunner.Phase) {
    val label = when (phase) {
        is SpeedTestRunner.Phase.Download -> stringResource(R.string.fandogh_download)
        is SpeedTestRunner.Phase.Upload -> stringResource(R.string.fandogh_upload)
        else -> stringResource(R.string.fandogh_speed_test_connecting)
    }
    val mbps = when (phase) {
        is SpeedTestRunner.Phase.Download -> phase.mbps
        is SpeedTestRunner.Phase.Upload -> phase.mbps
        else -> 0.0
    }
    val progress = when (phase) {
        is SpeedTestRunner.Phase.Download -> phase.progress
        is SpeedTestRunner.Phase.Upload -> phase.progress
        else -> 0f
    }
    val accent = if (phase is SpeedTestRunner.Phase.Upload) {
        FandoghColors.UploadAccent
    } else {
        FandoghColors.DownloadAccent
    }
    val animatedProgress by animateFloatAsState(progress, tween(300), label = "speedProgress")

    Column(Modifier.fillMaxWidth()) {
        Text(
            text = label.uppercase(),
            color = FandoghColors.TextSecondary,
            fontSize = 10.sp,
            letterSpacing = 1.1.sp
        )
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 4.dp)) {
            Text(
                text = formatMbps(mbps),
                color = accent,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "Mbps",
                color = FandoghColors.TextSecondary,
                fontSize = 13.sp,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.10f))
        ) {
            Box(
                Modifier
                    .fillMaxWidth(animatedProgress)
                    .height(6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(accent)
            )
        }
    }
}

@Composable
private fun ResultColumn(label: String, mbps: Double, accent: Color, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            text = label.uppercase(),
            color = FandoghColors.TextSecondary,
            fontSize = 10.sp,
            letterSpacing = 1.1.sp
        )
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 4.dp)) {
            Text(
                text = formatMbps(mbps),
                color = accent,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = "Mbps",
                color = FandoghColors.TextSecondary,
                fontSize = 11.sp,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
    }
}

/** Sub-10 figures need a decimal to be meaningful; above that it is noise. */
private fun formatMbps(mbps: Double): String =
    if (mbps < 10) String.format("%.1f", mbps) else mbps.toInt().toString()
