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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.v2ray.ang.R

data class VpnSettingsState(
    val dnsServers: List<String> = emptyList(),
    val dnsDraft: String = "",
    val attachHttpProxy: Boolean = false,
    val shareOverWifi: Boolean = false,
    val showSpeedNotification: Boolean = false,
    val perAppEnabled: Boolean = false,
    val perAppCount: Int = 0,
    val appVersion: String = ""
)

/**
 * Routing, DNS and proxy controls, presented in Fandogh's visual language.
 *
 * Each control writes straight to the same preference key the classic settings screen
 * uses, so the two stay in agreement and the core picks changes up on its next start.
 */
@Composable
fun VpnSettingsTab(
    state: VpnSettingsState,
    onDnsDraftChange: (String) -> Unit,
    onAddDns: () -> Unit,
    onRemoveDns: (String) -> Unit,
    onToggleHttpProxy: (Boolean) -> Unit,
    onToggleShareOverWifi: (Boolean) -> Unit,
    onToggleSpeedNotification: (Boolean) -> Unit,
    onOpenPerApp: () -> Unit,
    onOpenAdvanced: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Text("←", color = FandoghColors.TextPrimary, fontSize = 26.sp)
            }
            Spacer(Modifier.width(8.dp))
            ScreenHeader(
                title = stringResource(R.string.fandogh_vpn_settings),
                subtitle = stringResource(R.string.fandogh_vpn_settings_subtitle)
            )
        }

        Spacer(Modifier.height(26.dp))
        SectionLabel(stringResource(R.string.fandogh_section_connection))
        Spacer(Modifier.height(12.dp))
        DnsCard(
            servers = state.dnsServers,
            draft = state.dnsDraft,
            onDraftChange = onDnsDraftChange,
            onAdd = onAddDns,
            onRemove = onRemoveDns
        )

        Spacer(Modifier.height(26.dp))
        SectionLabel(stringResource(R.string.fandogh_section_proxy))
        Spacer(Modifier.height(12.dp))
        SwitchCard(
            title = stringResource(R.string.fandogh_attach_http_proxy),
            subtitle = stringResource(R.string.fandogh_attach_http_proxy_hint),
            checked = state.attachHttpProxy,
            onCheckedChange = onToggleHttpProxy
        )
        Spacer(Modifier.height(12.dp))
        SwitchCard(
            title = stringResource(R.string.fandogh_share_wifi),
            subtitle = stringResource(R.string.fandogh_share_wifi_hint),
            checked = state.shareOverWifi,
            onCheckedChange = onToggleShareOverWifi
        )

        Spacer(Modifier.height(26.dp))
        SectionLabel(stringResource(R.string.fandogh_section_app))
        Spacer(Modifier.height(12.dp))
        NavRow(
            title = stringResource(R.string.fandogh_per_app_routing),
            subtitle = if (state.perAppEnabled && state.perAppCount > 0) {
                stringResource(R.string.fandogh_per_app_active, state.perAppCount)
            } else {
                stringResource(R.string.fandogh_per_app_routing_hint)
            },
            onClick = onOpenPerApp,
            leading = {
                LeadingTile(FandoghColors.AccentGreen) {
                    Text("⇄", color = FandoghColors.AccentGreen, fontSize = 20.sp)
                }
            }
        )
        Spacer(Modifier.height(12.dp))
        SwitchCard(
            title = stringResource(R.string.fandogh_speed_notification),
            subtitle = stringResource(R.string.fandogh_speed_notification_hint),
            checked = state.showSpeedNotification,
            onCheckedChange = onToggleSpeedNotification
        )

        Spacer(Modifier.height(12.dp))
        NavRow(
            title = stringResource(R.string.fandogh_advanced_settings),
            subtitle = stringResource(R.string.fandogh_advanced_settings_hint),
            onClick = onOpenAdvanced,
            leading = {
                LeadingTile(FandoghColors.TextSecondary) {
                    GearGlyph(Modifier.size(22.dp), FandoghColors.TextSecondary)
                }
            }
        )

        Spacer(Modifier.height(20.dp))
        GlassCard(contentPadding = PaddingValues(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        stringResource(R.string.fandogh_app_version),
                        color = FandoghColors.TextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        stringResource(R.string.fandogh_app_version_hint),
                        color = FandoghColors.TextSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                Text(
                    state.appVersion,
                    color = FandoghColors.AccentBlueBright,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun DnsCard(
    servers: List<String>,
    draft: String,
    onDraftChange: (String) -> Unit,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit
) {
    GlassCard(contentPadding = PaddingValues(20.dp)) {
        Text(
            stringResource(R.string.fandogh_dns_servers),
            color = FandoghColors.TextPrimary,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            stringResource(R.string.fandogh_dns_servers_hint),
            color = FandoghColors.TextSecondary,
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 3.dp)
        )

        if (servers.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            // A simple wrapping row: DNS lists are short, so a two-per-line grid reads
            // better here than a horizontally scrolling strip.
            servers.chunked(2).forEach { pair ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    pair.forEach { server ->
                        DnsPill(
                            value = server,
                            onRemove = { onRemove(server) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = draft,
                onValueChange = onDraftChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = {
                    Text("1.1.1.1", color = FandoghColors.TextTertiary, fontSize = 14.sp)
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                shape = RoundedCornerShape(14.dp),
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
            Spacer(Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(FandoghColors.AccentBlue.copy(alpha = 0.18f))
                    .border(
                        BorderStroke(1.dp, FandoghColors.AccentBlue.copy(alpha = 0.45f)),
                        RoundedCornerShape(14.dp)
                    )
                    .clickable(onClick = onAdd),
                contentAlignment = Alignment.Center
            ) {
                Text("+", color = FandoghColors.AccentBlueBright, fontSize = 26.sp)
            }
        }
    }
}

@Composable
private fun DnsPill(value: String, onRemove: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(FandoghColors.SurfaceStrong)
            .border(BorderStroke(1.dp, FandoghColors.Border), shape)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            value,
            color = FandoghColors.TextPrimary,
            fontSize = 15.sp,
            maxLines = 1,
            modifier = Modifier.weight(1f, fill = false)
        )
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.12f))
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center
        ) {
            Text("×", color = FandoghColors.TextSecondary, fontSize = 15.sp)
        }
    }
}

@Composable
private fun SwitchCard(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    GlassCard(contentPadding = PaddingValues(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    color = FandoghColors.TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    subtitle,
                    color = FandoghColors.TextSecondary,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 3.dp, end = 12.dp)
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = FandoghColors.AccentBlue,
                    checkedBorderColor = FandoghColors.AccentBlue,
                    uncheckedThumbColor = FandoghColors.TextSecondary,
                    uncheckedTrackColor = FandoghColors.SurfaceStrong,
                    uncheckedBorderColor = FandoghColors.Border
                )
            )
        }
    }
}
