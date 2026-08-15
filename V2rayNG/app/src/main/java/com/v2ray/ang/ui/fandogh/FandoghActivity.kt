package com.v2ray.ang.ui.fandogh

import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.R
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.core.LauncherManager
import com.v2ray.ang.ui.AboutActivity
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.ui.main.MainActivity
import com.v2ray.ang.ui.main.MainStatus
import com.v2ray.ang.ui.main.MainViewModel
import com.v2ray.ang.ui.settings.SettingsActivity
import com.v2ray.ang.ui.subscription.SubSettingActivity

/**
 * Fandogh's own front end.
 *
 * It drives the existing [MainViewModel] rather than duplicating connection logic, so
 * subscriptions, routing and the core service behave exactly as they do in the classic
 * UI — this activity only replaces what the user sees. The classic server list stays
 * reachable from the Profile tab for the management actions this screen intentionally
 * does not surface.
 */
class FandoghActivity : BaseComponentActivity() {

    private val mainViewModel: MainViewModel by viewModels()

    private val requestVpnPermission =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == RESULT_OK) startCore()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mainViewModel.initialize()
    }

    override fun onResume() {
        super.onResume()
        mainViewModel.refreshUiSettings()
        mainViewModel.setupGroupTab()
    }

    @Composable
    override fun ScreenContent() {
        FandoghTheme {
            val uiState by mainViewModel.uiState.collectAsStateWithLifecycle()
            val totals by TrafficTracker.totals.collectAsStateWithLifecycle()
            val context = LocalContext.current

            // Poll the core for traffic only while this screen is on display.
            DisposableEffect(uiState.isRunning) {
                if (uiState.isRunning) TrafficTracker.start() else TrafficTracker.stop()
                onDispose { TrafficTracker.stop() }
            }

            var tab by rememberSaveable { mutableIntStateOf(0) }

            val profile = remember(uiState.selectedGuid) {
                uiState.selectedGuid?.let { MmkvManager.decodeServerConfig(it) }
            }

            val homeState = HomeState(
                connected = uiState.isRunning,
                connecting = uiState.status is MainStatus.Testing,
                statusText = stringResource(
                    if (uiState.isRunning) R.string.fandogh_connected else R.string.fandogh_disconnected
                ),
                detailText = when {
                    uiState.isRunning -> profile?.remarks
                    else -> stringResource(R.string.fandogh_tap_to_connect)
                },
                protocol = profile?.configType?.name
                    ?.lowercase()
                    ?.replaceFirstChar { it.uppercase() },
                serverName = profile?.remarks,
                serverDetail = profile?.server
            )

            Box(
                Modifier
                    .fillMaxSize()
                    .fandoghBackground()
                    .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
            ) {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f)) {
                        when (tab) {
                            0 -> HomeTab(
                                state = homeState,
                                onToggle = ::toggleService,
                                onOpenSettings = {
                                    startActivity(Intent(context, SettingsActivity::class.java))
                                },
                                onPickServer = {
                                    startActivity(Intent(context, MainActivity::class.java))
                                }
                            )

                            1 -> StatsTab(
                                totals = totals,
                                quotaBytes = null
                            )

                            else -> ProfileTab(
                                displayName = stringResource(R.string.app_name),
                                usage = null,
                                localUsedBytes = totals.monthTotal,
                                onOpenSubscriptions = {
                                    startActivity(Intent(context, SubSettingActivity::class.java))
                                },
                                onOpenServerList = {
                                    startActivity(Intent(context, MainActivity::class.java))
                                },
                                onOpenAbout = {
                                    startActivity(Intent(context, AboutActivity::class.java))
                                }
                            )
                        }
                    }

                    FandoghBottomBar(selected = tab, onSelect = { tab = it })
                }
            }
        }
    }

    /** Mirrors MainActivity's FAB behaviour, including the VPN consent dialog. */
    private fun toggleService() {
        if (mainViewModel.uiState.value.isRunning) {
            LauncherManager.stopService(this)
        } else if (SettingsManager.isVpnMode()) {
            val intent = VpnService.prepare(this)
            if (intent == null) startCore() else requestVpnPermission.launch(intent)
        } else {
            startCore()
        }
    }

    private fun startCore() {
        if (mainViewModel.uiState.value.selectedGuid.isNullOrEmpty()) {
            // Nothing selected yet — send the user to the list rather than failing silently.
            startActivity(Intent(this, MainActivity::class.java))
            return
        }
        LauncherManager.startService(this)
    }
}

@Composable
private fun FandoghBottomBar(selected: Int, onSelect: (Int) -> Unit) {
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF060D1A).copy(alpha = 0.92f))
            .padding(top = 10.dp, bottom = bottomInset + 10.dp, start = 18.dp, end = 18.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        BottomBarItem(
            label = stringResource(R.string.fandogh_home),
            selected = selected == 0,
            onClick = { onSelect(0) }
        ) { tint -> HomeGlyph(Modifier.size(26.dp), tint) }

        BottomBarItem(
            label = stringResource(R.string.fandogh_stats),
            selected = selected == 1,
            onClick = { onSelect(1) }
        ) { tint -> BarsGlyph(Modifier.size(26.dp), tint) }

        BottomBarItem(
            label = stringResource(R.string.fandogh_profile),
            selected = selected == 2,
            onClick = { onSelect(2) }
        ) { tint -> PersonGlyph(Modifier.size(26.dp), tint) }
    }
}

@Composable
private fun BottomBarItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable (Color) -> Unit
) {
    val tint = if (selected) FandoghColors.AccentBlueBright else FandoghColors.TextTertiary
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .background(
                if (selected) FandoghColors.AccentBlue.copy(alpha = 0.14f) else Color.Transparent
            )
            .padding(horizontal = 22.dp, vertical = 8.dp)
    ) {
        icon(tint)
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            color = tint,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun HomeGlyph(modifier: Modifier, color: Color) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = androidx.compose.ui.graphics.drawscope.Stroke(
            width = 2.dp.toPx(),
            join = androidx.compose.ui.graphics.StrokeJoin.Round
        )
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.5f, h * 0.10f)
            lineTo(w * 0.90f, h * 0.42f)
            lineTo(w * 0.90f, h * 0.88f)
            lineTo(w * 0.10f, h * 0.88f)
            lineTo(w * 0.10f, h * 0.42f)
            close()
        }
        drawPath(path, color, style = stroke)
        drawCircle(color, radius = w * 0.07f, center = androidx.compose.ui.geometry.Offset(w * 0.5f, h * 0.62f))
    }
}

@Composable
private fun BarsGlyph(modifier: Modifier, color: Color) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val bar = w * 0.16f
        val radius = androidx.compose.ui.geometry.CornerRadius(bar / 2, bar / 2)
        listOf(0.30f to 0.55f, 0.52f to 0.30f, 0.74f to 0.68f).forEach { (x, top) ->
            drawRoundRect(
                color = color,
                topLeft = androidx.compose.ui.geometry.Offset(w * x - bar / 2, h * top),
                size = androidx.compose.ui.geometry.Size(bar, h * (0.86f - top)),
                cornerRadius = radius
            )
        }
    }
}

@Composable
private fun PersonGlyph(modifier: Modifier, color: Color) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
        drawCircle(
            color = color,
            radius = w * 0.20f,
            center = androidx.compose.ui.geometry.Offset(w * 0.5f, h * 0.30f),
            style = stroke
        )
        drawArc(
            color = color,
            startAngle = 200f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = androidx.compose.ui.geometry.Offset(w * 0.18f, h * 0.55f),
            size = androidx.compose.ui.geometry.Size(w * 0.64f, h * 0.60f),
            style = stroke
        )
    }
}
