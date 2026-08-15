package com.v2ray.ang.ui.fandogh

import com.v2ray.ang.AppConfig
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Reads the real allowance from the panel.
 *
 * Sub-panels in the 3x-ui / x-ui family answer a subscription request with a
 * `subscription-userinfo` header, the de-facto standard shared with Clash and
 * Shadowrocket:
 *
 *     subscription-userinfo: upload=1234; download=5678; total=107374182400; expire=1767225600
 *
 * All four fields are optional and panels differ in which they send, so each is parsed
 * independently and a missing one stays null rather than defaulting to a number that
 * would render as a confident-looking lie.
 */
object SubscriptionUsageRepository {

    private const val KEY_CACHED_UPLOAD = "fandogh_sub_upload"
    private const val KEY_CACHED_DOWNLOAD = "fandogh_sub_download"
    private const val KEY_CACHED_TOTAL = "fandogh_sub_total"
    private const val KEY_CACHED_EXPIRE = "fandogh_sub_expire"
    private const val KEY_CACHED_AT = "fandogh_sub_cached_at"

    private val _usage = MutableStateFlow(loadCached())
    val usage: StateFlow<SubscriptionUsage?> = _usage.asStateFlow()

    private fun loadCached(): SubscriptionUsage? {
        if (MmkvManager.decodeSettingsLong(KEY_CACHED_AT, 0) == 0L) return null
        val total = MmkvManager.decodeSettingsLong(KEY_CACHED_TOTAL, 0)
        if (total <= 0) return null
        return SubscriptionUsage(
            uploadBytes = MmkvManager.decodeSettingsLong(KEY_CACHED_UPLOAD, 0),
            downloadBytes = MmkvManager.decodeSettingsLong(KEY_CACHED_DOWNLOAD, 0),
            totalBytes = total,
            expiryEpochSeconds = MmkvManager.decodeSettingsLong(KEY_CACHED_EXPIRE, 0)
        )
    }

    private fun cache(usage: SubscriptionUsage) {
        MmkvManager.encodeSettings(KEY_CACHED_UPLOAD, usage.uploadBytes)
        MmkvManager.encodeSettings(KEY_CACHED_DOWNLOAD, usage.downloadBytes)
        MmkvManager.encodeSettings(KEY_CACHED_TOTAL, usage.totalBytes)
        MmkvManager.encodeSettings(KEY_CACHED_EXPIRE, usage.expiryEpochSeconds)
        MmkvManager.encodeSettings(KEY_CACHED_AT, System.currentTimeMillis())
    }

    /** Forgets any cached allowance — used when the subscription itself is replaced. */
    fun clear() {
        MmkvManager.encodeSettings(KEY_CACHED_AT, 0L)
        MmkvManager.encodeSettings(KEY_CACHED_TOTAL, 0L)
        _usage.value = null
    }

    /**
     * Fetches the header for the first enabled subscription.
     *
     * Only headers are needed, but a plain GET is used rather than HEAD: several panels
     * answer HEAD with 405 while returning the header perfectly well on GET. The body is
     * closed without reading, so the payload is never buffered.
     */
    suspend fun refresh(): SubscriptionUsage? = withContext(Dispatchers.IO) {
        val subscription = MmkvManager.decodeSubscriptions()
            .firstOrNull { it.subscription.enabled && it.subscription.url.isNotBlank() }
            ?: return@withContext null

        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()

        val request = Request.Builder()
            .url(subscription.subscription.url)
            .get()
            .header("User-Agent", subscription.subscription.userAgent?.takeIf { it.isNotBlank() } ?: "Fandogh")
            .header("Connection", "close")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val header = response.header("subscription-userinfo")
                    ?: return@withContext null.also {
                        LogUtil.d(AppConfig.TAG, "Panel sent no subscription-userinfo header")
                    }
                val parsed = parse(header) ?: return@withContext null
                cache(parsed)
                _usage.value = parsed
                parsed
            }
        } catch (e: Exception) {
            LogUtil.w(AppConfig.TAG, "subscription-userinfo fetch failed: ${e.message}")
            null
        }
    }

    /** Parses `upload=..; download=..; total=..; expire=..` in any order. */
    internal fun parse(header: String): SubscriptionUsage? {
        val fields = header.split(';')
            .mapNotNull { part ->
                val pair = part.split('=', limit = 2)
                if (pair.size != 2) return@mapNotNull null
                val key = pair[0].trim().lowercase()
                val value = pair[1].trim().toLongOrNull() ?: return@mapNotNull null
                key to value
            }
            .toMap()

        // Without a total there is no allowance to draw a gauge against.
        val total = fields["total"] ?: return null
        return SubscriptionUsage(
            uploadBytes = fields["upload"] ?: 0,
            downloadBytes = fields["download"] ?: 0,
            totalBytes = total,
            expiryEpochSeconds = fields["expire"] ?: 0
        )
    }
}
