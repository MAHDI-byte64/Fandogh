package com.v2ray.ang.ui.fandogh

import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.net.toUri
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
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
import com.v2ray.ang.enums.PermissionType
import com.v2ray.ang.dto.entities.SubscriptionCache
import com.v2ray.ang.dto.entities.SubscriptionItem
import com.v2ray.ang.extension.toastError
import com.v2ray.ang.extension.toastSuccess
import com.v2ray.ang.handler.AngConfigManager
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val KEY_ONBOARDED = "fandogh_onboarded"

/** How long a latency probe may run before the tile stops claiming to be measuring. */
private const val PING_TIMEOUT_MILLIS = 6_000L

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

    /** Set while onboarding drives the consent dialog, so a grant advances the flow. */
    private var onVpnConsentResult: ((Boolean) -> Unit)? = null

    private val requestVpnPermission =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val granted = result.resultCode == RESULT_OK
            val pending = onVpnConsentResult
            onVpnConsentResult = null
            if (pending != null) pending(granted) else if (granted) startCore()
        }

    private var onNotificationResult: (() -> Unit)? = null

    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            // Either answer moves the flow along; the prompt is a courtesy, not a gate.
            onNotificationResult?.invoke()
            onNotificationResult = null
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
            val details by SubscriptionUsageRepository.details.collectAsStateWithLifecycle()
            val speedPhase by SpeedTestRunner.phase.collectAsStateWithLifecycle()
            val context = LocalContext.current
            val scope = rememberCoroutineScope()

            DisposableEffect(Unit) {
                TrafficTracker.start(this@FandoghActivity)
                onDispose { TrafficTracker.stop(this@FandoghActivity) }
            }

            val pingTest = remember { PingTestController(context) }
            DisposableEffect(pingTest) {
                pingTest.register()
                onDispose { pingTest.unregister() }
            }

            // Session clock and a latency probe, both scoped to the current connection.
            var sessionSeconds by remember { mutableStateOf(0L) }
            var latencyMillis by remember { mutableStateOf<Long?>(null) }
            var pinging by remember { mutableStateOf(false) }

            // A repeat probe can return the identical figure, which changes no state and
            // would leave the tile spinning forever. The timeout is what actually ends
            // the measuring state; the result below just ends it sooner.
            fun probeLatency() {
                if (!mainViewModel.uiState.value.isRunning || pinging) return
                pinging = true
                mainViewModel.testCurrentServerRealPing()
                scope.launch {
                    kotlinx.coroutines.delay(PING_TIMEOUT_MILLIS)
                    pinging = false
                }
            }

            LaunchedEffect(uiState.isRunning) {
                if (!uiState.isRunning) {
                    sessionSeconds = 0
                    latencyMillis = null
                    TrafficTracker.clearRate()
                    return@LaunchedEffect
                }
                // Give the tunnel a moment to settle before the first probe.
                kotlinx.coroutines.delay(1500)
                probeLatency()
                while (true) {
                    kotlinx.coroutines.delay(1000)
                    sessionSeconds += 1
                    // Re-probe every couple of minutes so the figure stays meaningful.
                    if (sessionSeconds % 120 == 0L) probeLatency()
                }
            }

            // Latency arrives asynchronously as a status update from the daemon.
            LaunchedEffect(uiState.status) {
                (uiState.status as? MainStatus.ConnectionTest)?.let {
                    latencyMillis = it.result.delayMillis
                    pinging = false
                }
            }

            var onboardingStep by rememberSaveable {
                mutableStateOf(if (isOnboarded()) null else OnboardingStep.Subscription)
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

            // Re-fetch when the tunnel comes up as well as on open: panels are often only
            // reachable through the VPN, so a fetch made while disconnected can fail even
            // though nothing is wrong with the subscription.
            LaunchedEffect(savedSubscription?.first, uiState.isRunning) {
                if (savedSubscription != null) SubscriptionUsageRepository.refresh()
            }

            val profile = remember(uiState.selectedGuid) {
                uiState.selectedGuid?.let { MmkvManager.decodeServerConfig(it) }
            }
            // Straight from storage rather than the view model's per-group flow: that
            // flow is filled by an async load behind a cache, so right after an import it
            // is still empty and the picker showed "no servers" for servers that existed.
            val servers = remember(
                uiState.selectedGroupId,
                uiState.selectedGuid,
                uiState.groups,
                uiState.isTesting,
                uiState.status,
                pingTest.resultTick,
                showPicker
            ) {
                pickableServers(uiState.selectedGroupId)
            }

            // Lowest measured latency wins; untested servers report 0 and would
            // otherwise sort to the front as if they were instant.
            val fastest = remember(servers) {
                servers.filter { it.delayMillis > 0 }.minByOrNull { it.delayMillis }
            }

            val homeState = HomeState(
                connected = uiState.isRunning,
                connecting = uiState.status is MainStatus.Testing && !uiState.isRunning,
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
                serverDetail = profile?.server,
                sessionSeconds = sessionSeconds,
                downSpeed = totals.downSpeed,
                upSpeed = totals.upSpeed,
                latencyMillis = latencyMillis,
                pinging = pinging
            )

            Box(
                Modifier
                    .fillMaxSize()
                    .fandoghBackground()
                    .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
            ) {
                val pendingCrash = crashReport
                val step = onboardingStep
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
                } else if (step != null) {
                    OnboardingScreen(
                        step = step,
                        subscriptionUrl = subscriptionUrl,
                        busy = busy,
                        message = message,
                        onUrlChange = { subscriptionUrl = it },
                        onSubmitSubscription = {
                            saveSubscription(
                                url = subscriptionUrl,
                                existingGuid = savedSubscription?.first,
                                setBusy = { busy = it },
                                setMessage = { message = it },
                                scopeLaunch = { block -> scope.launch { block() } },
                                onImported = { onboardingStep = OnboardingStep.VpnPermission }
                            )
                        },
                        onSkipSubscription = {
                            message = null
                            onboardingStep = OnboardingStep.VpnPermission
                        },
                        onGrantVpn = {
                            val intent = VpnService.prepare(this@FandoghActivity)
                            if (intent == null) {
                                onboardingStep = OnboardingStep.Notifications
                            } else {
                                onVpnConsentResult = { onboardingStep = OnboardingStep.Notifications }
                                requestVpnPermission.launch(intent)
                            }
                        },
                        onGrantNotifications = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                onNotificationResult = {
                                    markOnboarded()
                                    onboardingStep = null
                                }
                                requestNotifications.launch(PermissionType.POST_NOTIFICATIONS.getPermission())
                            } else {
                                markOnboarded()
                                onboardingStep = null
                            }
                        },
                        onSkip = {
                            when (step) {
                                OnboardingStep.VpnPermission ->
                                    onboardingStep = OnboardingStep.Notifications

                                else -> {
                                    markOnboarded()
                                    onboardingStep = null
                                }
                            }
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
                        onToggleStartOnBoot = {
                            MmkvManager.encodeStartOnBoot(it)
                            settingsState = settingsState.copy(startOnBoot = it)
                        },
                        onOpenKillSwitch = { openVpnSystemSettings() },
                        onToggleFilter = { filter, enabled ->
                            if (ContentFilters.set(filter, enabled)) {
                                settingsState = settingsState.copy(filters = ContentFilters.read())
                                if (mainViewModel.uiState.value.isRunning) {
                                    // Routing is baked into the config the core is
                                    // already running; it only changes on restart.
                                    mainViewModel.onAction(MainAction.RestartService)
                                } else {
                                    toastSuccess(R.string.toast_success)
                                }
                            } else {
                                toastError(R.string.fandogh_filter_needs_iran_rules)
                            }
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
                            Crossfade(
                                targetState = tab,
                                animationSpec = tween(260),
                                label = "tab"
                            ) { current -> when (current) {
                                0 -> HomeTab(
                                    state = homeState,
                                    onToggle = { toggleService(onNoServer = { tab = 2 }) },
                                    onOpenSettings = {
                                        settingsState = readVpnSettings()
                                        showSettings = true
                                    },
                                    onPickServer = { showPicker = true },
                                    onPing = { probeLatency() }
                                )

                                1 -> StatsTab(
                                    totals = totals,
                                    quotaBytes = usage?.totalBytes,
                                    speedPhase = speedPhase,
                                    onRunSpeedTest = {
                                        scope.launch {
                                            SpeedTestRunner.run(mainViewModel.uiState.value.isRunning)
                                        }
                                    },
                                    connected = uiState.isRunning
                                )

                                else -> ProfileTab(
                                    state = ProfileTabState(
                                        subscriptionUrl = subscriptionUrl,
                                        savedSubscriptionUrl = savedSubscription?.second?.url.orEmpty(),
                                        usage = usage,
                                        details = details,
                                        localUsedBytes = totals.monthTotal,
                                        todayUsedBytes = totals.todayTotal,
                                        monthUsedBytes = totals.monthTotal,
                                        serverCount = servers.size,
                                        fastestServerName = fastest?.name,
                                        fastestServerPing = fastest?.delayMillis ?: 0L,
                                        connected = uiState.isRunning,
                                        busy = busy,
                                        message = message
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
                                    onShareSubscription = {
                                        shareText(savedSubscription?.second?.url.orEmpty())
                                    },
                                    onOpenSupport = { openLink(it) },
                                    onOpenSettings = {
                                        settingsState = readVpnSettings()
                                        showSettings = true
                                    }
                                )
                            } }
                        }

                        FandoghBottomBar(selected = tab, onSelect = { tab = it })
                    }
                }

                if (showPicker) {
                    ServerPickerSheet(
                        servers = servers,
                        selectedGuid = uiState.selectedGuid,
                        testing = pingTest.testing,
                        progressText = pingTest.progress,
                        onSelect = { guid ->
                            mainViewModel.onAction(MainAction.SelectServer(guid))
                            showPicker = false
                            // Re-point a live tunnel at the newly chosen server.
                            if (mainViewModel.uiState.value.isRunning) {
                                mainViewModel.onAction(MainAction.RestartService)
                            }
                        },
                        onTestAll = { visible ->
                            // Test exactly the servers on screen. Going through the view
                            // model instead meant testing whatever its async group list
                            // happened to hold, which was often nothing at all.
                            val started = pingTest.start(
                                visible.map { it.guid },
                                uiState.selectedGroupId,
                                scope
                            )
                            if (!started) {
                                context.toastError(R.string.fandogh_test_unavailable)
                            }
                        },
                        onAutoSelect = { visible ->
                            val started = pingTest.start(
                                guids = visible.map { it.guid },
                                groupId = uiState.selectedGroupId,
                                scope = scope,
                                autoSelect = true
                            ) {
                                // Read the delays back from storage after the batch, not
                                // from the list captured when the button was pressed —
                                // that snapshot predates every result.
                                val visibleGuids = visible.mapTo(HashSet()) { it.guid }
                                val best = pickableServers(uiState.selectedGroupId)
                                    .filter { it.guid in visibleGuids && it.delayMillis > 0 }
                                    .minByOrNull { it.delayMillis }
                                if (best == null) {
                                    context.toastError(R.string.fandogh_no_server_responded)
                                } else {
                                    mainViewModel.onAction(MainAction.SelectServer(best.guid))
                                    if (mainViewModel.uiState.value.isRunning) {
                                        mainViewModel.onAction(MainAction.RestartService)
                                    }
                                    showPicker = false
                                    context.toastSuccess(
                                        getString(
                                            R.string.fandogh_auto_selected,
                                            CountryFlags.stripFlag(best.name),
                                            best.delayMillis
                                        )
                                    )
                                }
                            }
                            if (!started) context.toastError(R.string.fandogh_test_unavailable)
                        },
                        onAddSubscription = {
                            showPicker = false
                            tab = 2
                        },
                        onDismiss = { showPicker = false }
                    )
                }
            }
        }
    }

    /**
     * Servers for the picker.
     *
     * Falls back to every stored server when the selected group has none, so a stale or
     * empty group selection cannot hide profiles the user has actually imported.
     */
    private fun pickableServers(groupId: String): List<PickableServer> {
        val guids = MmkvManager.decodeServerList(groupId)
            .takeIf { groupId.isNotEmpty() && it.isNotEmpty() }
            ?: MmkvManager.decodeAllServerList()

        return guids.mapNotNull { guid ->
            val profile = MmkvManager.decodeServerConfig(guid) ?: return@mapNotNull null
            PickableServer(
                guid = guid,
                name = profile.remarks.ifBlank { profile.server.orEmpty() },
                protocol = profile.configType.name,
                address = profile.server.orEmpty(),
                delayMillis = MmkvManager.decodeServerAffiliationInfo(guid)?.testDelayMillis ?: 0L
            )
        }
    }

    /** The first enabled subscription, as (guid, item), or null when none is stored. */
    private fun firstSubscription(): Pair<String, SubscriptionItem>? =
        MmkvManager.decodeSubscriptions()
            .firstOrNull { it.subscription.url.isNotBlank() }
            ?.let { it.guid to it.subscription }

    /** Hands the subscription link to whatever the user shares things with. */
    private fun shareText(text: String) {
        if (text.isBlank()) return
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        runCatching {
            startActivity(Intent.createChooser(intent, getString(R.string.fandogh_share_subscription)))
        }.onFailure { toastError(R.string.toast_failure) }
    }

    /**
     * Opens a link the panel supplied. The URL comes from the operator's own server, but
     * it is still third-party text, so anything that is not http(s) is refused rather
     * than handed to the system to resolve into some other app.
     */
    private fun openLink(url: String) {
        val safe = url.trim()
        if (!safe.startsWith("http://") && !safe.startsWith("https://")) return
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, safe.toUri()))
        }.onFailure { toastError(R.string.toast_failure) }
    }

    /**
     * Opens Android's own VPN settings, where the kill switch lives.
     *
     * Only the system can enforce "block connections without VPN", so the app's job is
     * to take the user to the switch rather than pretend to own it. Some vendors ship
     * no such screen, hence the fallback and then the message.
     */
    private fun openVpnSystemSettings() {
        val candidates = listOf(
            Intent("android.net.vpn.SETTINGS"),
            Intent(android.provider.Settings.ACTION_VPN_SETTINGS)
        )
        for (intent in candidates) {
            if (runCatching { startActivity(intent); true }.getOrDefault(false)) return
        }
        toastError(R.string.fandogh_kill_switch_unavailable)
    }

    private fun readVpnSettings() = VpnSettingsState(
        dnsServers = MmkvManager.decodeSettingsString(AppConfig.PREF_VPN_DNS)
            .orEmpty()
            .split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() },
        attachHttpProxy = MmkvManager.decodeSettingsBool(AppConfig.PREF_PER_APP_PROXY),
        shareOverWifi = MmkvManager.decodeSettingsBool(AppConfig.PREF_PROXY_SHARING),
        showSpeedNotification = MmkvManager.decodeSettingsBool(AppConfig.PREF_SPEED_ENABLED),
        startOnBoot = MmkvManager.decodeStartOnBoot(),
        filters = ContentFilters.read(),
        perAppEnabled = MmkvManager.decodeSettingsBool(AppConfig.PREF_PER_APP_PROXY),
        perAppCount = MmkvManager.decodeSettingsStringSet(AppConfig.PREF_PER_APP_PROXY_SET)?.size ?: 0,
        appVersion = BuildConfig.VERSION_NAME
    )

    /**
     * Stores the subscription link and imports its servers.
     *
     * Two earlier attempts failed here for different reasons, both silent:
     * MainAction.UpdateSubscriptions resolves against the selected group, which does not
     * exist yet for a link being added; and SubscriptionUpdater.syncOne only schedules
     * *periodic* background work — and returns immediately without scheduling anything
     * when autoUpdate is off, which it is by default. Neither ever performed a fetch.
     *
     * AngConfigManager.updateConfigViaSub is the call that actually downloads and parses,
     * the same one the classic "update subscription" menu item uses. It blocks, so it
     * runs on IO, and it reports how many profiles it imported.
     */
    private fun saveSubscription(
        url: String,
        existingGuid: String?,
        setBusy: (Boolean) -> Unit,
        setMessage: (String?) -> Unit,
        scopeLaunch: (suspend () -> Unit) -> Unit,
        onImported: (() -> Unit)? = null
    ) {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) {
            setMessage(getString(R.string.fandogh_enter_link_first))
            return
        }
        if (!Utils.isValidUrl(trimmed)) {
            setMessage(getString(R.string.fandogh_invalid_link))
            return
        }

        val guid = existingGuid?.takeIf { it.isNotBlank() } ?: Utils.getUuid()
        val existing = MmkvManager.decodeSubscription(guid)
        val changed = existing?.url != trimmed
        val item = (existing ?: SubscriptionItem()).apply {
            if (remarks.isBlank()) remarks = getString(R.string.app_name)
            this.url = trimmed
            enabled = true
            // updateConfigViaSub rejects a link outright unless one of these holds; panel
            // links are pasted by the user and may be plain http on a custom port.
            allowInsecureUrl = true
            // Keep the servers fresh without the user coming back here.
            autoUpdate = true
        }
        MmkvManager.encodeSubscription(guid, item)
        if (changed) SubscriptionUsageRepository.clear()

        setBusy(true)
        setMessage(null)

        scopeLaunch {
            val result = withContext(Dispatchers.IO) {
                AngConfigManager.updateConfigViaSub(SubscriptionCache(guid, item))
            }
            SubscriptionUsageRepository.refresh()
            mainViewModel.setupGroupTab(forceRefresh = true)
            // Point at the new group, and pick its first server when nothing is active
            // yet, so pasting a link and pressing Connect works without a detour through
            // the server list.
            mainViewModel.onAction(MainAction.SelectGroup(guid))
            if (mainViewModel.uiState.value.selectedGuid.isNullOrEmpty()) {
                MmkvManager.decodeServerList(guid).firstOrNull()?.let {
                    mainViewModel.onAction(MainAction.SelectServer(it))
                }
            }

            setBusy(false)
            setMessage(
                when {
                    result.configCount > 0 ->
                        getString(R.string.fandogh_subscription_saved_count, result.configCount)

                    result.skipCount > 0 -> getString(R.string.fandogh_invalid_link)
                    else -> getString(R.string.fandogh_subscription_empty)
                }
            )
            // Only move on when servers actually arrived; a failed link should stay put
            // so the message is read rather than flashed past.
            if (result.configCount > 0) onImported?.invoke()
        }
    }

    /** Mirrors MainActivity's FAB behaviour, including the VPN consent dialog. */
    private fun toggleService(onNoServer: () -> Unit) {
        if (mainViewModel.uiState.value.isRunning) {
            LauncherManager.stopService(this)
            return
        }
        if (mainViewModel.uiState.value.selectedGuid.isNullOrEmpty()) {
            onNoServer()
            return
        }
        if (SettingsManager.isVpnMode()) {
            val intent = VpnService.prepare(this)
            if (intent == null) startCore() else requestVpnPermission.launch(intent)
        } else {
            startCore()
        }
    }

    /**
     * Starts the tunnel, or reports that there is nothing to start.
     *
     * Previously this bounced to the classic v2rayNG list, which is jarring for someone
     * who has only ever seen this UI — pressing Connect on a fresh install dropped them
     * into a different app. The caller decides where to send them instead.
     */
    private fun startCore(onNoServer: (() -> Unit)? = null) {
        if (mainViewModel.uiState.value.selectedGuid.isNullOrEmpty()) {
            onNoServer?.invoke() ?: toastSuccess(R.string.fandogh_no_server)
            return
        }
        LauncherManager.startService(this)
    }

    private fun isOnboarded(): Boolean =
        MmkvManager.decodeSettingsBool(KEY_ONBOARDED, false)

    private fun markOnboarded() {
        MmkvManager.encodeSettings(KEY_ONBOARDED, true)
    }
}

@Composable
private fun FandoghBottomBar(selected: Int, onSelect: (Int) -> Unit) {
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color(0xFF060D1A).copy(alpha = 0.94f))
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(FandoghColors.Border)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = bottomInset + 8.dp, start = 10.dp, end = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottomBarItem(
                modifier = Modifier.weight(1f),
                label = stringResource(R.string.fandogh_home),
                selected = selected == 0,
                onClick = { onSelect(0) }
            ) { tint -> HomeGlyph(Modifier.size(23.dp), tint) }

            BottomBarItem(
                modifier = Modifier.weight(1f),
                label = stringResource(R.string.fandogh_stats),
                selected = selected == 1,
                onClick = { onSelect(1) }
            ) { tint -> BarsGlyph(Modifier.size(23.dp), tint) }

            BottomBarItem(
                modifier = Modifier.weight(1f),
                label = stringResource(R.string.fandogh_profile),
                selected = selected == 2,
                onClick = { onSelect(2) }
            ) { tint -> PersonGlyph(Modifier.size(23.dp), tint) }
        }
    }
}

@Composable
private fun BottomBarItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable (Color) -> Unit
) {
    val tint = if (selected) FandoghColors.AccentBlueBright else FandoghColors.TextTertiary
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(FandoghRadius.tile))
            .clickable(onClick = onClick)
            .background(
                if (selected) FandoghColors.AccentBlue.copy(alpha = 0.14f) else Color.Transparent
            )
            .padding(vertical = 7.dp)
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
