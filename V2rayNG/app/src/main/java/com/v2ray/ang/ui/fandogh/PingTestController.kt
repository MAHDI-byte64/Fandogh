package com.v2ray.ang.ui.fandogh

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.TestServiceMessage
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.helper.MessageHelper
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Runs "test all" for the Fandogh UI without going through [com.v2ray.ang.ui.main.MainViewModel].
 *
 * The view model's own `testAllRealPing` reads a per-group list that is filled asynchronously
 * and returns silently when it is empty, so a press could do nothing with no way to tell why.
 * The picker already builds its list straight from storage, so the servers on screen are the
 * authoritative set: this sends exactly those GUIDs to [com.v2ray.ang.service.CoreTestService]
 * and listens for its results itself. Nothing here depends on view model state.
 */
@Stable
class PingTestController(context: Context) {

    private val appContext = context.applicationContext

    /** True from the moment a batch is dispatched until it finishes or the watchdog fires. */
    var testing by mutableStateOf(false)
        private set

    /** Live "remaining / total" text from the test service, empty when idle. */
    var progress by mutableStateOf("")
        private set

    /** Bumped whenever a delay lands in storage, so the list can be recomputed. */
    var resultTick by mutableIntStateOf(0)
        private set

    private var registered = false
    private var watchdog: Job? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.getIntExtra("key", -1)) {
                AppConfig.MSG_MEASURE_CONFIG_SUCCESS -> resultTick++

                AppConfig.MSG_MEASURE_CONFIG_NOTIFY -> {
                    progress = intent.getStringExtra("content").orEmpty()
                    resultTick++
                }

                AppConfig.MSG_MEASURE_CONFIG_FINISH -> finish()
            }
        }
    }

    fun register() {
        if (registered) return
        runCatching {
            ContextCompat.registerReceiver(
                appContext,
                receiver,
                IntentFilter(AppConfig.BROADCAST_ACTION_ACTIVITY),
                Utils.receiverFlags()
            )
            registered = true
        }
    }

    fun unregister() {
        if (!registered) return
        runCatching { appContext.unregisterReceiver(receiver) }
        registered = false
        watchdog?.cancel()
        watchdog = null
    }

    /**
     * Starts a TCP latency test over [guids].
     *
     * @return false when there is nothing to test, so the caller can say so instead of
     * leaving the user looking at a button that appears to be ignoring them.
     */
    fun start(guids: List<String>, groupId: String, scope: CoroutineScope): Boolean {
        if (guids.isEmpty() || testing) return false

        // Drop the previous run's numbers first: a stale delay next to a fresh one is
        // indistinguishable on screen, and a server that has since died would keep
        // showing the latency it had when it was alive.
        MmkvManager.clearAllTestDelayResults(guids)
        testing = true
        progress = ""
        resultTick++

        MessageHelper.sendMsg2TestService(
            appContext,
            TestServiceMessage(
                key = AppConfig.MSG_MEASURE_CONFIG_START,
                subscriptionId = groupId,
                serverGuids = guids,
                onlyTcp = true
            )
        )

        // The test service lives in the core process. If it dies before reporting, no
        // finish broadcast ever arrives and the button would stay disabled forever.
        watchdog?.cancel()
        watchdog = scope.launch {
            delay(TIMEOUT_BASE_MILLIS + guids.size * TIMEOUT_PER_SERVER_MILLIS)
            finish()
        }
        return true
    }

    private fun finish() {
        watchdog?.cancel()
        watchdog = null
        if (!testing) return
        testing = false
        progress = ""
        resultTick++
    }

    private companion object {
        const val TIMEOUT_BASE_MILLIS = 15_000L
        const val TIMEOUT_PER_SERVER_MILLIS = 1_500L
    }
}
