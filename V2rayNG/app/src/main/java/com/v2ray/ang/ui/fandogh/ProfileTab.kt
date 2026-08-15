package com.v2ray.ang.ui.fandogh

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.v2ray.ang.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Quota reported by the panel's `subscription-userinfo` header. Null when the panel
 * sends no such header, in which case the UI falls back to locally measured traffic
 * rather than inventing an allowance.
 */
data class SubscriptionUsage(
    val uploadBytes: Long,
    val downloadBytes: Long,
    val totalBytes: Long,
    val expiryEpochSeconds: Long
) {
    val usedBytes: Long get() = uploadBytes + downloadBytes
    val remainingBytes: Long get() = (totalBytes - usedBytes).coerceAtLeast(0)
    val usedFraction: Float
        get() = if (totalBytes > 0) (usedBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
}

data class ProfileTabState(
    val subscriptionUrl: String = "",
    val savedSubscriptionUrl: String = "",
    val usage: SubscriptionUsage? = null,
    val localUsedBytes: Long = 0,
    val busy: Boolean = false,
    val message: String? = null,
    val currentServerName: String? = null,
    val serverCount: Int = 0
)

@Composable
fun ProfileTab(
    state: ProfileTabState,
    onUrlChange: (String) -> Unit,
    onSaveSubscription: () -> Unit,
    onRefreshUsage: () -> Unit,
    onChangeProfile: () -> Unit,
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
            title = stringResource(R.string.fandogh_profile),
            subtitle = stringResource(R.string.fandogh_profile_subtitle)
        )

        Spacer(Modifier.height(22.dp))
        QuotaCard(
            usage = state.usage,
            localUsedBytes = state.localUsedBytes,
            busy = state.busy,
            onRefresh = onRefreshUsage
        )

        Spacer(Modifier.height(16.dp))
        CurrentProfileCard(
            serverName = state.currentServerName,
            serverCount = state.serverCount,
            onChangeProfile = onChangeProfile
        )

        Spacer(Modifier.height(16.dp))
        SubscriptionCard(
            url = state.subscriptionUrl,
            saved = state.savedSubscriptionUrl,
            busy = state.busy,
            onUrlChange = onUrlChange,
            onSave = onSaveSubscription
        )

        if (state.message != null) {
            Spacer(Modifier.height(14.dp))
            Text(
                text = state.message,
                color = FandoghColors.TextSecondary,
                fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun QuotaCard(
    usage: SubscriptionUsage?,
    localUsedBytes: Long,
    busy: Boolean,
    onRefresh: () -> Unit
) {
    GlassCard(contentPadding = PaddingValues(22.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = if (usage != null) {
                        stringResource(R.string.fandogh_plan_active)
                    } else {
                        stringResource(R.string.fandogh_plan_unknown)
                    },
                    color = FandoghColors.TextPrimary,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = usage?.let { expiryLabel(it.expiryEpochSeconds) }
                        ?: stringResource(R.string.fandogh_plan_unknown_hint),
                    color = FandoghColors.TextSecondary,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
            if (busy) {
                CircularProgressIndicator(
                    color = FandoghColors.AccentBlueBright,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(24.dp)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(FandoghColors.AccentGreen.copy(alpha = 0.15f))
                        .border(
                            BorderStroke(1.dp, FandoghColors.AccentGreen.copy(alpha = 0.4f)),
                            RoundedCornerShape(13.dp)
                        )
                        .clickable(onClick = onRefresh),
                    contentAlignment = Alignment.Center
                ) {
                    Text("↻", color = FandoghColors.AccentGreen, fontSize = 20.sp)
                }
            }
        }

        Spacer(Modifier.height(22.dp))

        if (usage != null && usage.totalBytes > 0) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DonutGauge(fraction = usage.usedFraction, modifier = Modifier.size(170.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${(usage.usedFraction * 100).toInt()}%",
                            color = FandoghColors.TextPrimary,
                            fontSize = 38.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            stringResource(R.string.fandogh_used).uppercase(),
                            color = FandoghColors.TextSecondary,
                            fontSize = 12.sp,
                            letterSpacing = 1.2.sp
                        )
                    }
                }
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    MetricRow(
                        label = stringResource(R.string.fandogh_upload),
                        value = formatBytesLabel(usage.uploadBytes),
                        accent = FandoghColors.UploadAccent,
                        markerFilled = false
                    )
                    MetricRow(
                        label = stringResource(R.string.fandogh_download),
                        value = formatBytesLabel(usage.downloadBytes),
                        accent = FandoghColors.DownloadAccent
                    )
                    MetricRow(
                        label = stringResource(R.string.fandogh_total),
                        value = formatBytesLabel(usage.totalBytes),
                        accent = FandoghColors.TextTertiary
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    stringResource(R.string.fandogh_your_data),
                    color = FandoghColors.TextSecondary,
                    fontSize = 15.sp
                )
                Text(
                    text = stringResource(
                        R.string.fandogh_data_left,
                        formatBytesLabel(usage.remainingBytes),
                        formatBytesLabel(usage.totalBytes)
                    ),
                    color = FandoghColors.TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(10.dp))
            QuotaBar(fraction = 1f - usage.usedFraction)
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    stringResource(R.string.fandogh_measured_usage),
                    color = FandoghColors.TextSecondary,
                    fontSize = 15.sp
                )
                Text(
                    text = formatBytesLabel(localUsedBytes),
                    color = FandoghColors.TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun CurrentProfileCard(
    serverName: String?,
    serverCount: Int,
    onChangeProfile: () -> Unit
) {
    GlassCard(contentPadding = PaddingValues(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LeadingTile(FandoghColors.AccentBlue) {
                GlobeGlyph(Modifier.size(24.dp))
            }
            Spacer(Modifier.size(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.fandogh_current_profile),
                    color = FandoghColors.TextSecondary,
                    fontSize = 13.sp
                )
                Text(
                    text = serverName ?: stringResource(R.string.fandogh_no_server),
                    color = FandoghColors.TextPrimary,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        GradientButton(
            text = stringResource(R.string.fandogh_change_profile),
            onClick = onChangeProfile
        )
        if (serverCount > 0) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.fandogh_server_count, serverCount),
                color = FandoghColors.TextTertiary,
                fontSize = 13.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
private fun SubscriptionCard(
    url: String,
    saved: String,
    busy: Boolean,
    onUrlChange: (String) -> Unit,
    onSave: () -> Unit
) {
    GlassCard(contentPadding = PaddingValues(20.dp)) {
        Text(
            stringResource(R.string.fandogh_sub_link),
            color = FandoghColors.TextPrimary,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            stringResource(R.string.fandogh_sub_link_hint),
            color = FandoghColors.TextSecondary,
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 3.dp)
        )

        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = url,
            onValueChange = onUrlChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = {
                Text(
                    "https://panel.example.com/sub/xxxx",
                    color = FandoghColors.TextTertiary,
                    fontSize = 14.sp
                )
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            shape = RoundedCornerShape(16.dp),
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

        Spacer(Modifier.height(16.dp))
        GradientButton(
            text = if (busy) {
                stringResource(R.string.fandogh_updating)
            } else if (saved.isBlank()) {
                stringResource(R.string.fandogh_save_and_update)
            } else {
                stringResource(R.string.fandogh_update_subscription)
            },
            onClick = { if (!busy) onSave() },
            brush = if (busy) {
                Brush.horizontalGradient(
                    listOf(
                        FandoghColors.AccentGreen.copy(alpha = 0.4f),
                        FandoghColors.AccentBlueBright.copy(alpha = 0.4f)
                    )
                )
            } else {
                FandoghColors.CtaGradient
            }
        )
    }
}

@Composable
private fun QuotaBar(fraction: Float) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(9.dp)
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.10f))
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(9.dp)
                .clip(RoundedCornerShape(50))
                .background(
                    Brush.horizontalGradient(
                        listOf(FandoghColors.AccentBlue, FandoghColors.AccentGreen)
                    )
                )
        )
    }
}

/** Renders the panel's expiry timestamp, or a neutral line when it sends none. */
@Composable
private fun expiryLabel(epochSeconds: Long): String {
    if (epochSeconds <= 0) return stringResource(R.string.fandogh_plan_active_hint)
    val date = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(Date(epochSeconds * 1000))
    return stringResource(R.string.fandogh_expires_on, date)
}
