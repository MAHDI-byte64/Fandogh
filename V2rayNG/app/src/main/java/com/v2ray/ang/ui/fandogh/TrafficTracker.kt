package com.v2ray.ang.ui.fandogh

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.TrafficMessage
import com.v2ray.ang.extension.serializable
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Calendar

/**
 * Traffic totals for the Stats screen.
 *
 * The Xray core lives in the :RunSoLibV2RayDaemon process and its stats API is reachable
 * only from there, so this cannot poll it — an earlier version tried and simply read
 * zeroes. Instead the daemon's existing sampler broadcasts each delta and this collects
 * them, which also means one owner of the delta stream rather than two competing pollers.
 *
 * Daily and monthly buckets roll over on read, so a device left running past midnight
 * reports the new day rather than silently extending the old one.
 */
object TrafficTracker {

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
        val downSpeed: Long = 0,
        /** Uptime millis of the last sample received, or 0 when none has arrived yet. */
        val lastSampleAt: Long = 0
    ) {
        val monthTotal: Long get() = monthUp + monthDown
        val hasLiveData: Boolean get() = lastSampleAt > 0
    }

    private val _totals = MutableStateFlow(load())
    val totals: StateFlow<Totals> = _totals.asStateFlow()

    private var registered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.getIntExtra("key", -1) != AppConfig.MSG_STATE_TRAFFIC) return
            val sample = intent.serializable<TrafficMessage>("content") ?: return
            record(sample)
        }
    }

    /** Starts listening. Safe to call repeatedly — only one registration is kept. */
    fun start(context: Context) {
        if (registered) return
        runCatching {
            ContextCompat.registerReceiver(
                context.applicationContext,
                receiver,
                IntentFilter(AppConfig.BROADCAST_ACTION_ACTIVITY),
                Utils.receiverFlags()
            )
            registered = true
        }
    }

    fun stop(context: Context) {
        if (!registered) return
        runCatching { context.applicationContext.unregisterReceiver(receiver) }
        registered = false
        _totals.value = _totals.value.copy(upSpeed = 0, downSpeed = 0, lastSampleAt = 0)
    }

    /** Zeroes the live rate without touching accumulated totals. */
    fun clearRate() {
        _totals.value = _totals.value.copy(upSpeed = 0, downSpeed = 0, lastSampleAt = 0)
    }

    private fun record(sample: TrafficMessage) {
        val seconds = (sample.elapsedMillis.coerceAtLeast(1)) / 1000.0
        val rolled = rollIfNeeded(_totals.value)
        val next = rolled.copy(
            monthUp = rolled.monthUp + sample.uplinkBytes,
            monthDown = rolled.monthDown + sample.downlinkBytes,
            todayUp = rolled.todayUp + sample.uplinkBytes,
            todayDown = rolled.todayDown + sample.downlinkBytes,
            upSpeed = (sample.uplinkBytes / seconds).toLong(),
            downSpeed = (sample.downlinkBytes / seconds).toLong(),
            lastSampleAt = android.os.SystemClock.elapsedRealtime()
        )
        _totals.value = next
        if (sample.uplinkBytes > 0 || sample.downlinkBytes > 0) persist(next)
    }

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
