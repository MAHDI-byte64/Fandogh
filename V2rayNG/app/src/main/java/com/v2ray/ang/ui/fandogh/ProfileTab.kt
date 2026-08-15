package com.v2ray.ang.ui.fandogh

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.v2ray.ang.R

/**
 * Account overview.
 *
 * Quota figures come from the subscription's `subscription-userinfo` header when the
 * panel supplies one (3x-ui does); [SubscriptionUsage] is null when it does not, and the
 * card then shows the locally measured total instead of inventing an allowance.
 */
data class SubscriptionUsage(
    val uploadBytes: Long,
    val downloadBytes: Long,
    val totalBytes: Long,
    val expiryEpochSeconds: Long
) {
    val usedBytes: Long get() = uploadBytes + downloadBytes
    val remainingBytes: Long get() = (totalBytes - usedBytes).coerceAtLeast(0)
}

@Composable
fun ProfileTab(
    displayName: String,
    usage: SubscriptionUsage?,
    localUsedBytes: Long,
    onOpenSubscriptions: () -> Unit,
    onOpenServerList: () -> Unit,
    onOpenAbout: () -> Unit,
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

        Spacer(Modifier.height(24.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(FandoghColors.AccentBlue, FandoghColors.AccentGreen)
                            )
                        )
                        .border(BorderStroke(2.dp, FandoghColors.AccentGreenBright), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = displayName.take(1).uppercase(),
                        color = androidx.compose.ui.graphics.Color.White,
                        fontSize = 52.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    text = displayName,
                    color = FandoghColors.TextPrimary,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(Modifier.height(24.dp))
        PlanCard(usage = usage, localUsedBytes = localUsedBytes)

        Spacer(Modifier.height(16.dp))
        NavRow(
            title = stringResource(R.string.fandogh_subscriptions),
            subtitle = stringResource(R.string.fandogh_subscriptions_hint),
            onClick = onOpenSubscriptions,
            leading = {
                LeadingTile(FandoghColors.AccentGreen) {
                    Text("↻", color = FandoghColors.AccentGreen, fontSize = 22.sp)
                }
            }
        )

        Spacer(Modifier.height(12.dp))
        NavRow(
            title = stringResource(R.string.fandogh_server_list),
            subtitle = stringResource(R.string.fandogh_server_list_hint),
            onClick = onOpenServerList,
            leading = {
                LeadingTile(FandoghColors.AccentBlue) {
                    GlobeGlyph(Modifier.size(24.dp))
                }
            }
        )

        Spacer(Modifier.height(12.dp))
        NavRow(
            title = stringResource(R.string.fandogh_about),
            subtitle = stringResource(R.string.fandogh_about_hint),
            onClick = onOpenAbout,
            leading = {
                LeadingTile(FandoghColors.TextSecondary) {
                    Text("i", color = FandoghColors.TextSecondary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
            }
        )

        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun PlanCard(usage: SubscriptionUsage?, localUsedBytes: Long) {
    GlassCard(contentPadding = PaddingValues(20.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = if (usage != null) {
                        stringResource(R.string.fandogh_plan_active)
                    } else {
                        stringResource(R.string.fandogh_plan_unknown)
                    },
                    color = FandoghColors.TextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (usage != null) {
                        stringResource(R.string.fandogh_plan_active_hint)
                    } else {
                        stringResource(R.string.fandogh_plan_unknown_hint)
                    },
                    color = FandoghColors.TextSecondary,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }

        Spacer(Modifier.height(18.dp))

        if (usage != null && usage.totalBytes > 0) {
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
            QuotaBar(
                fraction = (usage.remainingBytes.toFloat() / usage.totalBytes).coerceIn(0f, 1f)
            )
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
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun QuotaBar(fraction: Float) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(9.dp)
            .clip(RoundedCornerShape(50))
            .background(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.10f))
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction)
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
