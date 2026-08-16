package com.v2ray.ang.ui.fandogh

import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.v2ray.ang.AngApplication
import com.v2ray.ang.AppConfig
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.R
import com.v2ray.ang.core.LauncherManager
import com.v2ray.ang.dto.entities.SubscriptionItem
import com.v2ray.ang.extension.toastSuccess
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.ui.AboutActivity
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.ui.main.MainAction
import com.v2ray.ang.ui.main.MainActivity
import com.v2ray.ang.ui.main.MainRepository
import com.v2ray.ang.ui.main.MainStatus
import com.v2ray.ang.ui.main.MainViewModel
import com.v2ray.ang.ui.perappproxy.PerAppProxyActivity
import com.v2ray.ang.ui.settings.SettingsActivity
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.launch

/**
 * Fandogh's own front end.
 *
 * It drives the existing [MainViewModel] rather than duplicating connection logic, so
 * subscriptions, routing and the core service behave exactly as they do in the classic
 * UI — this activity only replaces what the user sees.
 */
class FandoghActivity : BaseComponentActivity() {

    // MainViewModel takes a MainDataSource alongside the Application, so it cannot be
    // built by the default factory — asking for it without one throws while the activity
    // is still starting, which is a crash before anything reaches the screen.
    private val mainViewModel: MainViewModel by viewModels {
        MainViewModel.Factory(application, MainRepository(application as AngApplication))
    }

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
            val usage by SubscriptionUsageRepository.usage.collectAsStateWithLifecycle()
            val context = LocalContext.current
            val scope = rememberCoroutineScope()

            DisposableEffect(uiState.isRunning) {
                if (uiState.isRunning) TrafficTracker.start() else TrafficTracker.stop()
                onDispose { TrafficTracker.stop() }
            }

            // A crash report from the previous run takes over the screen until dismissed.
            var crashReport by remember { mutableStateOf(CrashReporter.lastReport(this@FandoghActivity)) }

            var tab by rememberSaveable { mutableIntStateOf(0) }
            var showSettings by rememberSaveable { mutableStateOf(false) }
            var showPicker by rememberSaveable { mutableStateOf(false) }
            var busy by remember { mutableStateOf(false) }
            var message by remember { mutableStateOf<String?>(null) }

            val savedSubscription = remember(uiState.groups) { firstSubscription() }
            var subscriptionUrl by remember(savedSubscription?.second?.url) {
                mutableStateOf(savedSubscription?.second?.url.orEmpty())
            }

            var settingsState by remember { mutableStateOf(readVpnSettings()) }

            // One quota fetch per foreground visit; the refresh button covers the rest.
            LaunchedEffect(savedSubscription?.first) {
                if (savedSubscription != null) SubscriptionUsageRepository.refresh()
            }

            val profile = remember(uiState.selectedGuid) {
                uiState.selectedGuid?.let { MmkvManager.decodeServerConfig(it) }
            }
            val servers by mainViewModel
                .serversForGroup(uiState.selectedGroupId)
                .collectAsStateWithLifecycle()

            val homeState = HomeState(
                connected = uiState.isRunning,
                connecting = uiState.status is MainStatus.Testing,
                statusText = stringResource(
                    if (uiState.isRunning) R.string.fandogh_connected else R.string.fandogh_disconnected
                ),
                detailText = if (uiState.isRunning) {
                    profile?.remarks
                } else {
                    stringResource(R.string.fandogh_tap_to_connect)
                },
                protocol = profile?.configType?.name?.lowercase()
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
                val pendingCrash = crashReport
                if (pendingCrash != null) {
                    CrashReportScreen(
                        report = pendingCrash,
                        onCopy = {
                            Utils.setClipboard(context, pendingCrash)
                            toastSuccess(R.string.toast_success)
                        },
                        onDismiss = {
                            CrashReporter.clear(this@FandoghActivity)
                            crashReport = null
                        }
                    )
                } else if (showSettings) {
                    VpnSettingsTab(
                        state = settingsState,
                        onDnsDraftChange = { settingsState = settingsState.copy(dnsDraft = it) },
                        onAddDns = {
                            val entry = settingsState.dnsDraft.trim()
                            if (entry.isNotEmpty() && entry !in settingsState.dnsServers) {
                                val next = settingsState.dnsServers + entry
                                MmkvManager.encodeSettings(AppConfig.PREF_VPN_DNS, next.joinToString(","))
                                settingsState = settingsState.copy(dnsServers = next, dnsDraft = "")
                            }
                        },
                        onRemoveDns = { entry ->
                            val next = settingsState.dnsServers - entry
                            MmkvManager.encodeSettings(AppConfig.PREF_VPN_DNS, next.joinToString(","))
                            settingsState = settingsState.copy(dnsServers = next)
                        },
                        onToggleHttpProxy = {
                            MmkvManager.encodeSettings(AppConfig.PREF_PER_APP_PROXY, it)
                            settingsState = settingsState.copy(attachHttpProxy = it)
                        },
                        onToggleShareOverWifi = {
                            MmkvManager.encodeSettings(AppConfig.PREF_PROXY_SHARING, it)
                            settingsState = settingsState.copy(shareOverWifi = it)
                        },
                        onToggleSpeedNotification = {
                            MmkvManager.encodeSettings(AppConfig.PREF_SPEED_ENABLED, it)
                            settingsState = settingsState.copy(showSpeedNotification = it)
                        },
                        onOpenPerApp = {
                            startActivity(Intent(context, PerAppProxyActivity::class.java))
                        },
                        onOpenAdvanced = {
                            startActivity(Intent(context, SettingsActivity::class.java))
                        },
                        onBack = {
                            showSettings = false
                            settingsState = readVpnSettings()
                        }
                    )
                } else {
                    Column(Modifier.fillMaxSize()) {
                        Box(Modifier.weight(1f)) {
                            when (tab) {
                                0 -> HomeTab(
                                    state = homeState,
                                    onToggle = ::toggleService,
                                    onOpenSettings = {
                                        settingsState = readVpnSettings()
                                        showSettings = true
                                    },
                                    onPickServer = { showPicker = true }
                                )

                                1 -> StatsTab(
                                    totals = totals,
                                    quotaBytes = usage?.totalBytes
                                )

                                else -> ProfileTab(
                                    state = ProfileTabState(
                                        subscriptionUrl = subscriptionUrl,
                                        savedSubscriptionUrl = savedSubscription?.second?.url.orEmpty(),
                                        usage = usage,
                                        localUsedBytes = totals.monthTotal,
                                        busy = busy,
                                        message = message,
                                        currentServerName = profile?.remarks,
                                        serverCount = servers.size
                                    ),
                                    onUrlChange = { subscriptionUrl = it },
                                    onSaveSubscription = {
                                        saveSubscription(
                                            url = subscriptionUrl,
                                            existingGuid = savedSubscription?.first,
                                            setBusy = { busy = it },
                                            setMessage = { message = it },
                                            scopeLaunch = { block -> scope.launch { block() } }
                                        )
                                    },
                                    onRefreshUsage = {
                                        busy = true
                                        scope.launch {
                                            val result = SubscriptionUsageRepository.refresh()
                                            busy = false
                                            message = if (result == null) {
                                                getString(R.string.fandogh_quota_unavailable)
                                            } else {
                                                null
                                            }
                                        }
                                    },
                                    onChangeProfile = { showPicker = true }
                                )
                            }
                        }

                        FandoghBottomBar(selected = tab, onSelect = { tab = it })
                    }
                }

                if (showPicker) {
                    ServerPickerSheet(
                        servers = servers.map {
                            PickableServer(
                                guid = it.guid,
                                name = it.profile.remarks.ifBlank {
                                    it.profile.server.orEmpty()
                                },
                                protocol = it.profile.configType.name,
                                address = it.profile.server.orEmpty(),
                                delayMillis = it.testDelayMillis
                            )
                        },
                        selectedGuid = uiState.selectedGuid,
                        onSelect = { guid ->
                            mainViewModel.onAction(MainAction.SelectServer(guid))
                            showPicker = false
                            // Re-point a live tunnel at the newly chosen server.
                            if (mainViewModel.uiState.value.isRunning) {
                                mainViewModel.onAction(MainAction.RestartService)
                            }
                        },
                        onTestAll = { mainViewModel.onAction(MainAction.TestAllServers) },
                        onDismiss = { showPicker = false }
                    )
                }
            }
        }
    }

    /** The first enabled subscription, as (guid, item), or null when none is stored. */
    private fun firstSubscription(): Pair<String, SubscriptionItem>? =
        MmkvManager.decodeSubscriptions()
            .firstOrNull { it.subscription.url.isNotBlank() }
            ?.let { it.guid to it.subscription }

    private fun readVpnSettings() = VpnSettingsState(
        dnsServers = MmkvManager.decodeSettingsString(AppConfig.PREF_VPN_DNS)
            .orEmpty()
            .split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() },
        attachHttpProxy = MmkvManager.decodeSettingsBool(AppConfig.PREF_PER_APP_PROXY),
        shareOverWifi = MmkvManager.decodeSettingsBool(AppConfig.PREF_PROXY_SHARING),
        showSpeedNotification = MmkvManager.decodeSettingsBool(AppConfig.PREF_SPEED_ENABLED),
        perAppEnabled = MmkvManager.decodeSettingsBool(AppConfig.PREF_PER_APP_PROXY),
        perAppCount = MmkvManager.decodeSettingsStringSet(AppConfig.PREF_PER_APP_PROXY_SET)?.size ?: 0,
        appVersion = BuildConfig.VERSION_NAME
    )

    /**
     * Stores the subscription link and pulls its servers.
     *
     * Replacing the URL invalidates any cached allowance, so the quota card cannot show
     * the previous panel's numbers against the new one.
     */
    private fun saveSubscription(
        url: String,
        existingGuid: String?,
        setBusy: (Boolean) -> Unit,
        setMessage: (String?) -> Unit,
        scopeLaunch: (suspend () -> Unit) -> Unit
    ) {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) {
            setMessage(getString(R.string.fandogh_enter_link_first))
            return
        }
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            setMessage(getString(R.string.fandogh_invalid_link))
            return
        }

        val existing = existingGuid?.let { MmkvManager.decodeSubscription(it) }
        val changed = existing?.url != trimmed
        val item = (existing ?: SubscriptionItem()).apply {
            remarks = remarks.ifBlank { getString(R.string.app_name) }
            this.url = trimmed
            enabled = true
        }
        MmkvManager.encodeSubscription(existingGuid.orEmpty(), item)
        if (changed) SubscriptionUsageRepository.clear()

        setBusy(true)
        setMessage(null)
        mainViewModel.onAction(MainAction.UpdateSubscriptions)
        scopeLaunch {
            // Give the update a moment to land before reading the panel's quota header.
            kotlinx.coroutines.delay(2500)
            SubscriptionUsageRepository.refresh()
            mainViewModel.setupGroupTab(forceRefresh = true)
            setBusy(false)
            setMessage(getString(R.string.fandogh_subscription_saved))
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
        horizontalArrangement = Arrangement.SpaceEvenly,
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
        drawCircle(
            color,
            radius = w * 0.07f,
            center = androidx.compose.ui.geometry.Offset(w * 0.5f, h * 0.62f)
        )
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
