package com.v2ray.ang.ui.fandogh

import com.v2ray.ang.AppConfig
import com.v2ray.ang.core.CoreServiceManager
import com.v2ray.ang.handler.MmkvManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * Accumulates proxied traffic for the Stats screen.
 *
 * The core reports *deltas* since the previous query, so totals only stay correct if a
 * single owner polls it. This object is that owner while the Fandogh UI is in the
 * foreground; the notification's own sampler runs on the same delta source, which is why
 * the poll interval here is deliberately coarse and why totals are persisted as running
 * sums rather than recomputed.
 *
 * Daily and monthly buckets roll over on read, so a device left running past midnight
 * reports the new day rather than silently extending the old one.
 */
object TrafficTracker {

    private const val POLL_INTERVAL_MS = 2_000L

    private const val KEY_MONTH_TAG = "fandogh_traffic_month_tag"
    private const val KEY_MONTH_UP = "fandogh_traffic_month_up"
    private const val KEY_MONTH_DOWN = "fandogh_traffic_month_down"
    private const val KEY_DAY_TAG = "fandogh_traffic_day_tag"
    private const val KEY_DAY_UP = "fandogh_traffic_day_up"
    private const val KEY_DAY_DOWN = "fandogh_traffic_day_down"

    data class Totals(
        val monthUp: Long = 0,
        val monthDown: Long = 0,
        val todayUp: Long = 0,
        val todayDown: Long = 0,
        val upSpeed: Long = 0,
        val downSpeed: Long = 0
    ) {
        val monthTotal: Long get() = monthUp + monthDown
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    private val _totals = MutableStateFlow(load())
    val totals: StateFlow<Totals> = _totals.asStateFlow()

    private fun monthTag(): String {
        val c = Calendar.getInstance()
        return "${c.get(Calendar.YEAR)}-${c.get(Calendar.MONTH) + 1}"
    }

    private fun dayTag(): String {
        val c = Calendar.getInstance()
        return "${c.get(Calendar.YEAR)}-${c.get(Calendar.MONTH) + 1}-${c.get(Calendar.DAY_OF_MONTH)}"
    }

    private fun load(): Totals {
        val monthMatches = MmkvManager.decodeSettingsString(KEY_MONTH_TAG) == monthTag()
        val dayMatches = MmkvManager.decodeSettingsString(KEY_DAY_TAG) == dayTag()
        return Totals(
            monthUp = if (monthMatches) MmkvManager.decodeSettingsLong(KEY_MONTH_UP, 0) else 0,
            monthDown = if (monthMatches) MmkvManager.decodeSettingsLong(KEY_MONTH_DOWN, 0) else 0,
            todayUp = if (dayMatches) MmkvManager.decodeSettingsLong(KEY_DAY_UP, 0) else 0,
            todayDown = if (dayMatches) MmkvManager.decodeSettingsLong(KEY_DAY_DOWN, 0) else 0
        )
    }

    private fun persist(t: Totals) {
        MmkvManager.encodeSettings(KEY_MONTH_TAG, monthTag())
        MmkvManager.encodeSettings(KEY_MONTH_UP, t.monthUp)
        MmkvManager.encodeSettings(KEY_MONTH_DOWN, t.monthDown)
        MmkvManager.encodeSettings(KEY_DAY_TAG, dayTag())
        MmkvManager.encodeSettings(KEY_DAY_UP, t.todayUp)
        MmkvManager.encodeSettings(KEY_DAY_DOWN, t.todayDown)
    }

    /** Begins polling. Safe to call repeatedly — only one poll loop ever runs. */
    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            var lastSampleAt = System.currentTimeMillis()
            while (isActive) {
                delay(POLL_INTERVAL_MS)
                val now = System.currentTimeMillis()
                val elapsedSec = ((now - lastSampleAt).coerceAtLeast(1)) / 1000.0
                lastSampleAt = now

                var up = 0L
                var down = 0L
                CoreServiceManager.queryAllOutboundTrafficStats().forEach { stat ->
                    if (stat.tag == AppConfig.TAG_DIRECT || stat.tag == AppConfig.TAG_BLOCKED) return@forEach
                    when (stat.direction) {
                        AppConfig.UPLINK -> up += stat.value
                        AppConfig.DOWNLINK -> down += stat.value
                    }
                }

                // Roll the buckets before adding, so a rollover mid-session lands correctly.
                val rolled = rollIfNeeded(_totals.value)
                val next = rolled.copy(
                    monthUp = rolled.monthUp + up,
                    monthDown = rolled.monthDown + down,
                    todayUp = rolled.todayUp + up,
                    todayDown = rolled.todayDown + down,
                    upSpeed = (up / elapsedSec).toLong(),
                    downSpeed = (down / elapsedSec).toLong()
                )
                _totals.value = next
                if (up > 0 || down > 0) persist(next)
            }
        }
    }

    private fun rollIfNeeded(current: Totals): Totals {
        var result = current
        if (MmkvManager.decodeSettingsString(KEY_MONTH_TAG) != monthTag()) {
            result = result.copy(monthUp = 0, monthDown = 0)
        }
        if (MmkvManager.decodeSettingsString(KEY_DAY_TAG) != dayTag()) {
            result = result.copy(todayUp = 0, todayDown = 0)
        }
        return result
    }

    fun stop() {
        job?.cancel()
        job = null
        _totals.value = _totals.value.copy(upSpeed = 0, downSpeed = 0)
    }
}

/** Formats a byte count the way the Stats screen shows it: "219.4 MB", "5.0 GB". */
fun formatBytes(bytes: Long): Pair<String, String> {
    val kb = 1024.0
    val mb = kb * 1024
    val gb = mb * 1024
    val tb = gb * 1024
    return when {
        bytes >= tb -> String.format("%.2f", bytes / tb) to "TB"
        bytes >= gb -> String.format("%.2f", bytes / gb) to "GB"
        bytes >= mb -> String.format("%.1f", bytes / mb) to "MB"
        bytes >= kb -> String.format("%.0f", bytes / kb) to "KB"
        else -> bytes.toString() to "B"
    }
}

fun formatBytesLabel(bytes: Long): String {
    val (value, unit) = formatBytes(bytes)
    return "$value $unit"
}
