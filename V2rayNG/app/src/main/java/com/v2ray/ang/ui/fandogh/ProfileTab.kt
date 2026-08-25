package com.v2ray.ang.ui.fandogh

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.remember
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

    /** Only a capped plan can be drawn as a proportion of anything. */
    val hasQuota: Boolean get() = totalBytes > 0

    /** An uncapped plan still has consumption the panel counted and the user wants. */
    val hasCountedUsage: Boolean get() = usedBytes > 0
}

/**
 * Everything the panel tells us about the account beyond its numbers.
 *
 * Panels differ in which of these they send and several send none, so every field is
 * optional and its card simply does not appear when the field is empty.
 */
data class SubscriptionDetails(
    val title: String = "",
    val announcement: String = "",
    val supportUrl: String = ""
)

data class ProfileTabState(
    val subscriptionUrl: String = "",
    val savedSubscriptionUrl: String = "",
    val usage: SubscriptionUsage? = null,
    val details: SubscriptionDetails = SubscriptionDetails(),
    val localUsedBytes: Long = 0,
    val todayUsedBytes: Long = 0,
    val monthUsedBytes: Long = 0,
    val serverCount: Int = 0,
    val fastestServerName: String? = null,
    val fastestServerPing: Long = 0,
    val connected: Boolean = false,
    val busy: Boolean = false,
    val message: String? = null
)

@Composable
fun ProfileTab(
    state: ProfileTabState,
    onUrlChange: (String) -> Unit,
    onSaveSubscription: () -> Unit,
    onRefreshUsage: () -> Unit,
    onShareSubscription: () -> Unit,
    onOpenSupport: (String) -> Unit,
    onOpenInBrowser: (String) -> Unit,
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
            // The panel's own name for the plan is more use than a generic subtitle,
            // and it is the first thing that tells the user this is really their account.
            subtitle = state.details.title.ifBlank {
                stringResource(R.string.fandogh_profile_subtitle)
            }
        )

        Spacer(Modifier.height(22.dp))
        AccountHero(
            usage = state.usage,
            localUsedBytes = state.localUsedBytes,
            connected = state.connected,
            busy = state.busy,
            onRefresh = onRefreshUsage
        )

        Spacer(Modifier.height(16.dp))
        AccountDetails(
            usage = state.usage,
            subscriptionUrl = state.savedSubscriptionUrl,
            serverCount = state.serverCount
        )

        // The panel's announcement is the operator talking to their users; when they
        // have written one it belongs above the mechanics of the subscription.
        AnimatedVisibility(
            visible = state.details.announcement.isNotBlank(),
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column {
                Spacer(Modifier.height(16.dp))
                AnnouncementCard(
                    text = state.details.announcement,
                    supportUrl = state.details.supportUrl,
                    onOpenSupport = onOpenSupport
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        ActivityCard(
            todayBytes = state.todayUsedBytes,
            monthBytes = state.monthUsedBytes,
            connected = state.connected
        )

        Spacer(Modifier.height(16.dp))
        NetworkCard(
            serverCount = state.serverCount,
            fastestName = state.fastestServerName,
            fastestPing = state.fastestServerPing
        )

        Spacer(Modifier.height(16.dp))
        SubscriptionCard(
            url = state.subscriptionUrl,
            saved = state.savedSubscriptionUrl,
            busy = state.busy,
            onUrlChange = onUrlChange,
            onSave = onSaveSubscription
        )

        Spacer(Modifier.height(16.dp))
        QuickActions(
            hasSubscription = state.savedSubscriptionUrl.isNotBlank(),
            onShare = onShareSubscription,
            onRefresh = onRefreshUsage,
            onOpenInBrowser = { onOpenInBrowser(state.savedSubscriptionUrl) }
        )

        AnimatedVisibility(
            visible = state.message != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Text(
                text = state.message.orEmpty(),
                color = FandoghColors.TextSecondary,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 14.dp, start = 4.dp, end = 4.dp)
            )
        }

        Spacer(Modifier.height(32.dp))
    }
}

/**
 * The operator's notice from the panel.
 *
 * Rendered as quoted text rather than a plain paragraph so it reads as somebody else
 * speaking, not as app copy.
 */
@Composable
private fun AnnouncementCard(
    text: String,
    supportUrl: String,
    onOpenSupport: (String) -> Unit
) {
    GlassCard(contentPadding = PaddingValues(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(FandoghColors.Warning.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Text("!", color = FandoghColors.Warning, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(12.dp))
            Text(
                stringResource(R.string.fandogh_announcement),
                color = FandoghColors.TextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(
            text = text,
            color = FandoghColors.TextSecondary,
            fontSize = 14.sp,
            lineHeight = 21.sp
        )
        if (supportUrl.isNotBlank()) {
            Spacer(Modifier.height(14.dp))
            Text(
                text = stringResource(R.string.fandogh_open_support),
                color = FandoghColors.AccentBlueBright,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable { onOpenSupport(supportUrl) }
            )
        }
    }
}

/** Traffic this device has actually moved, split into the two spans people ask about. */
@Composable
private fun ActivityCard(todayBytes: Long, monthBytes: Long, connected: Boolean) {
    GlassCard(contentPadding = PaddingValues(20.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.fandogh_activity),
                color = FandoghColors.TextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
            StatusDot(connected)
        }
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth()) {
            StatColumn(
                modifier = Modifier.weight(1f),
                label = stringResource(R.string.fandogh_today),
                value = formatBytesLabel(todayBytes),
                accent = FandoghColors.AccentGreen
            )
            Box(
                Modifier
                    .width(1.dp)
                    .height(46.dp)
                    .background(FandoghColors.Border)
            )
            StatColumn(
                modifier = Modifier.weight(1f),
                label = stringResource(R.string.fandogh_this_month),
                value = formatBytesLabel(monthBytes),
                accent = FandoghColors.AccentBlueBright,
                alignEnd = true
            )
        }
    }
}

/** What the subscription actually delivered: how many servers, and the best of them. */
@Composable
private fun NetworkCard(serverCount: Int, fastestName: String?, fastestPing: Long) {
    GlassCard(contentPadding = PaddingValues(20.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    stringResource(R.string.fandogh_your_network),
                    color = FandoghColors.TextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = pluralServers(serverCount),
                    color = FandoghColors.TextSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Text(
                text = serverCount.toString(),
                color = FandoghColors.AccentBlueBright,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold
            )
        }

        if (fastestName != null) {
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                ServerFlagTile(serverName = fastestName, size = 38.dp, tint = FandoghColors.AccentGreen)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.fandogh_fastest_server),
                        color = FandoghColors.TextSecondary,
                        fontSize = 11.sp,
                        letterSpacing = 1.1.sp
                    )
                    Text(
                        text = CountryFlags.stripFlag(fastestName),
                        color = FandoghColors.TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 1.dp)
                    )
                }
                if (fastestPing > 0) {
                    Text(
                        text = "$fastestPing ms",
                        color = FandoghColors.AccentGreen,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun StatColumn(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier,
    alignEnd: Boolean = false
) {
    Column(
        modifier = modifier,
        horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start
    ) {
        Text(
            text = label.uppercase(),
            color = FandoghColors.TextSecondary,
            fontSize = 10.sp,
            letterSpacing = 1.1.sp
        )
        Text(
            text = value,
            color = accent,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 5.dp)
        )
    }
}

@Composable
private fun StatusDot(connected: Boolean) {
    val color = if (connected) FandoghColors.AccentGreen else FandoghColors.TextTertiary
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(50))
                .background(color)
        )
        Spacer(Modifier.width(7.dp))
        Text(
            text = stringResource(
                if (connected) R.string.fandogh_connected else R.string.fandogh_disconnected
            ),
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/** The three things people reach for on this screen, one tap from the bottom of it. */
@Composable
private fun QuickActions(
    hasSubscription: Boolean,
    onShare: () -> Unit,
    onRefresh: () -> Unit,
    onOpenInBrowser: () -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ActionTile(
            modifier = Modifier.weight(1f),
            label = stringResource(R.string.fandogh_refresh),
            accent = FandoghColors.AccentBlueBright,
            onClick = onRefresh
        )
        // Both act on the stored link, so neither is offered before there is one.
        if (hasSubscription) {
            ActionTile(
                modifier = Modifier.weight(1f),
                label = stringResource(R.string.fandogh_share),
                accent = FandoghColors.AccentGreen,
                onClick = onShare
            )
            ActionTile(
                modifier = Modifier.weight(1f),
                label = stringResource(R.string.fandogh_open_in_browser),
                accent = FandoghColors.Warning,
                onClick = onOpenInBrowser
            )
        }
    }
}

@Composable
private fun ActionTile(
    label: String,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(FandoghRadius.tile)
    Box(
        modifier = modifier
            .clip(shape)
            .background(accent.copy(alpha = 0.12f))
            .border(BorderStroke(1.dp, accent.copy(alpha = 0.3f)), shape)
            .clickable(onClick = onClick)
            .padding(vertical = 15.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = accent,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

@Composable
private fun pluralServers(count: Int): String = when (count) {
    0 -> stringResource(R.string.fandogh_no_servers_yet)
    1 -> stringResource(R.string.fandogh_one_server)
    else -> stringResource(R.string.fandogh_server_count, count)
}

/**
 * The account at a glance: a ring for a capped plan, the counted figure for an uncapped
 * one, and the state of the connection.
 *
 * A percentage of an unlimited plan is meaningless, so the ring is only drawn when there
 * is a real allowance to draw against; otherwise the used figure carries the card.
 */
@Composable
private fun AccountHero(
    usage: SubscriptionUsage?,
    localUsedBytes: Long,
    connected: Boolean,
    busy: Boolean,
    onRefresh: () -> Unit
) {
    GlassCard(contentPadding = PaddingValues(22.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(
                        if (usage != null) R.string.fandogh_plan_active
                        else R.string.fandogh_plan_unknown
                    ),
                    color = FandoghColors.TextPrimary,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                StatusBadge(connected)
            }
            RefreshButton(busy = busy, onRefresh = onRefresh)
        }

        Spacer(Modifier.height(22.dp))

        when {
            usage != null && usage.hasQuota -> QuotaRing(usage)
            usage != null && usage.hasCountedUsage -> UncappedUsage(usage)
            else -> LocalOnlyUsage(localUsedBytes)
        }
    }
}

@Composable
private fun QuotaRing(usage: SubscriptionUsage) {
    GaugeBreakdown(
        fraction = usage.usedFraction,
        gaugeCenter = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${(usage.usedFraction * 100).toInt()}%",
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
            }
        }
    ) {
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
}

@Composable
private fun UncappedUsage(usage: SubscriptionUsage) {
    UsageTotal(
        label = stringResource(R.string.fandogh_panel_usage),
        value = formatBytesLabel(usage.usedBytes)
    )
    Spacer(Modifier.height(18.dp))
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
        value = stringResource(R.string.fandogh_unlimited),
        accent = FandoghColors.TextTertiary
    )
}

@Composable
private fun LocalOnlyUsage(localUsedBytes: Long) {
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

/** Pill badge in the sub page's idiom: coloured by state, never by decoration. */
@Composable
private fun StatusBadge(connected: Boolean) {
    val accent = if (connected) FandoghColors.AccentGreen else FandoghColors.TextTertiary
    val shape = RoundedCornerShape(50)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(accent.copy(alpha = 0.12f))
            .border(BorderStroke(1.dp, accent.copy(alpha = 0.28f)), shape)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(7.dp)
                .clip(RoundedCornerShape(50))
                .background(accent)
        )
        Spacer(Modifier.width(7.dp))
        Text(
            text = stringResource(
                if (connected) R.string.fandogh_connected else R.string.fandogh_disconnected
            ),
            color = accent,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun RefreshButton(busy: Boolean, onRefresh: () -> Unit) {
    if (busy) {
        CircularProgressIndicator(
            color = FandoghColors.AccentBlueBright,
            strokeWidth = 2.dp,
            modifier = Modifier.size(24.dp)
        )
        return
    }
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

/**
 * The identifying facts about the account, as the panel's own page lists them.
 *
 * The subscription id is masked: this screen is the one people screenshot when asking
 * for help, and the id is the credential.
 */
@Composable
private fun AccountDetails(
    usage: SubscriptionUsage?,
    subscriptionUrl: String,
    serverCount: Int
) {
    val subId = remember(subscriptionUrl) { maskedSubscriptionId(subscriptionUrl) }

    GlassCard(contentPadding = PaddingValues(20.dp)) {
        Text(
            stringResource(R.string.fandogh_account_details),
            color = FandoghColors.TextPrimary,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(16.dp))

        DetailRow(
            accent = FandoghColors.AccentBlueBright,
            label = stringResource(R.string.fandogh_subscription_id),
            value = subId ?: stringResource(R.string.fandogh_not_set)
        )
        DetailRow(
            accent = FandoghColors.AccentGreen,
            label = stringResource(R.string.fandogh_servers_count),
            value = serverCount.toString()
        )
        DetailRow(
            accent = FandoghColors.Warning,
            label = stringResource(R.string.fandogh_expiry_date),
            value = usage?.expiryEpochSeconds
                ?.takeIf { it > 0 }
                ?.let { formatDate(it) }
                ?: stringResource(R.string.fandogh_unlimited),
            last = true
        )
    }
}

@Composable
private fun DetailRow(accent: Color, label: String, value: String, last: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(50))
                .background(accent)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            color = FandoghColors.TextSecondary,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            color = FandoghColors.TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
    if (!last) {
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(FandoghColors.Border)
        )
        Spacer(Modifier.height(8.dp))
    }
}

/**
 * The subscription's own identifier, shortened and masked.
 *
 * Anyone holding the full id holds the account, so only enough is shown to tell two
 * subscriptions apart.
 */
private fun maskedSubscriptionId(url: String): String? {
    val id = url.trim().trimEnd('/').substringAfterLast('/', "")
    if (id.isBlank()) return null
    return if (id.length <= 8) id else "${id.take(4)}…${id.takeLast(4)}"
}

private fun formatDate(epochSeconds: Long): String =
    SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(Date(epochSeconds * 1000))

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

/**
 * The headline figure for an uncapped plan, where a percentage would be meaningless.
 * The number carries the weight the gauge would otherwise have.
 */
@Composable
private fun UsageTotal(label: String, value: String) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = label.uppercase(),
            color = FandoghColors.TextSecondary,
            fontSize = 11.sp,
            letterSpacing = 1.1.sp
        )
        Text(
            text = value,
            color = FandoghColors.TextPrimary,
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 4.dp)
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
