package com.v2ray.ang.ui.fandogh

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.v2ray.ang.R

data class PickableServer(
    val guid: String,
    val name: String,
    val protocol: String,
    val address: String,
    val delayMillis: Long
)

/**
 * Server chooser shown by the Profile tab's "Change profile" button.
 *
 * Latency is only rendered when the server has actually been tested — an untested
 * server shows nothing rather than a zero that would read as "instant".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerPickerSheet(
    servers: List<PickableServer>,
    selectedGuid: String?,
    onSelect: (String) -> Unit,
    onTestAll: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = FandoghColors.BackgroundMid,
        contentColor = FandoghColors.TextPrimary,
        dragHandle = {
            Box(Modifier.fillMaxWidth().padding(top = 12.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .width(40.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.25f))
                )
            }
        }
    ) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        stringResource(R.string.fandogh_change_profile),
                        color = FandoghColors.TextPrimary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        stringResource(R.string.fandogh_server_count, servers.size),
                        color = FandoghColors.TextSecondary,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(FandoghColors.AccentGreen.copy(alpha = 0.15f))
                        .border(
                            BorderStroke(1.dp, FandoghColors.AccentGreen.copy(alpha = 0.4f)),
                            RoundedCornerShape(12.dp)
                        )
                        .clickable(onClick = onTestAll)
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Text(
                        stringResource(R.string.fandogh_test_all),
                        color = FandoghColors.AccentGreen,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            if (servers.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    GlobeGlyph(Modifier.size(46.dp))
                    Spacer(Modifier.height(14.dp))
                    Text(
                        stringResource(R.string.fandogh_no_servers_yet),
                        color = FandoghColors.TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        stringResource(R.string.fandogh_no_servers_yet_hint),
                        color = FandoghColors.TextSecondary,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 460.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(servers, key = { it.guid }) { server ->
                        ServerRow(
                            server = server,
                            selected = server.guid == selectedGuid,
                            onClick = { onSelect(server.guid) }
                        )
                    }
                }
            }

            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun ServerRow(server: PickableServer, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                if (selected) FandoghColors.AccentBlue.copy(alpha = 0.16f) else FandoghColors.Surface
            )
            .border(
                BorderStroke(1.dp, if (selected) FandoghColors.AccentBlue else FandoghColors.Border),
                shape
            )
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(FandoghColors.SurfaceStrong),
            contentAlignment = Alignment.Center
        ) {
            GlobeGlyph(Modifier.size(22.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                server.name,
                color = FandoghColors.TextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${server.protocol} · ${server.address}",
                color = FandoghColors.TextSecondary,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        if (server.delayMillis > 0) {
            Spacer(Modifier.width(10.dp))
            Text(
                text = "${server.delayMillis} ms",
                color = latencyColor(server.delayMillis),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
        if (selected) {
            Spacer(Modifier.width(10.dp))
            Text("✓", color = FandoghColors.AccentBlueBright, fontSize = 20.sp)
        }
    }
}

private fun latencyColor(delayMillis: Long): Color = when {
    delayMillis < 300 -> FandoghColors.AccentGreen
    delayMillis < 800 -> FandoghColors.Warning
    else -> FandoghColors.Danger
}
