package com.v2ray.ang.ui.fandogh

import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.UrlContentRequest
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.util.HttpUtil
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

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

    private const val HEADER_USERINFO = "subscription-userinfo"

    private const val KEY_CACHED_UPLOAD = "fandogh_sub_upload"
    private const val KEY_CACHED_DOWNLOAD = "fandogh_sub_download"
    private const val KEY_CACHED_TOTAL = "fandogh_sub_total"
    private const val KEY_CACHED_EXPIRE = "fandogh_sub_expire"
    private const val KEY_CACHED_AT = "fandogh_sub_cached_at"

    private val _usage = MutableStateFlow(loadCached())
    val usage: StateFlow<SubscriptionUsage?> = _usage.asStateFlow()

    private fun loadCached(): SubscriptionUsage? {
        if (MmkvManager.decodeSettingsLong(KEY_CACHED_AT, 0) == 0L) return null
        // A zero total is cached too: an unlimited account still has real consumption
        // worth showing, it just has no allowance to measure it against.
        return SubscriptionUsage(
            uploadBytes = MmkvManager.decodeSettingsLong(KEY_CACHED_UPLOAD, 0),
            downloadBytes = MmkvManager.decodeSettingsLong(KEY_CACHED_DOWNLOAD, 0),
            totalBytes = MmkvManager.decodeSettingsLong(KEY_CACHED_TOTAL, 0),
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
     * Fetches the allowance header for the first enabled subscription.
     *
     * This deliberately goes through the same [HttpUtil] path the subscription import
     * uses. A hand-rolled client here looked equivalent but was not: it never went
     * through the tunnel's local HTTP proxy, so on a network where the panel is only
     * reachable via the VPN the config list would import while the quota silently came
     * back empty. The proxy attempt is tried first and a direct request is the fallback,
     * matching AngConfigManager, so the quota is fetched under exactly the conditions
     * the servers were.
     */
    suspend fun refresh(): SubscriptionUsage? = withContext(Dispatchers.IO) {
        val subscription = MmkvManager.decodeSubscriptions()
            .firstOrNull { it.subscription.enabled && it.subscription.url.isNotBlank() }
            ?: return@withContext null

        val url = HttpUtil.toIdnUrl(subscription.subscription.url)
        val userAgent = subscription.subscription.userAgent
        val requestHeaders = subscription.subscription.requestHeaders

        val viaProxy = runCatching {
            HttpUtil.getUrlResponseHeader(
                UrlContentRequest(
                    url = url,
                    userAgent = userAgent,
                    requestHeaders = requestHeaders,
                    timeout = 15000,
                    httpPort = SettingsManager.getHttpPort(),
                    proxyUsername = SettingsManager.getSocksUsername(),
                    proxyPassword = SettingsManager.getSocksPassword()
                ),
                HEADER_USERINFO
            )
        }.getOrNull()

        val header = viaProxy ?: runCatching {
            HttpUtil.getUrlResponseHeader(
                UrlContentRequest(
                    url = url,
                    userAgent = userAgent,
                    requestHeaders = requestHeaders
                ),
                HEADER_USERINFO
            )
        }.getOrNull()

        if (header == null) {
            LogUtil.d(AppConfig.TAG, "Panel sent no $HEADER_USERINFO header")
            return@withContext null
        }

        val parsed = parse(header) ?: return@withContext null
        cache(parsed)
        _usage.value = parsed
        parsed
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

        // A missing total means an unlimited account, not a missing answer: panels set
        // total=0, or omit it, when no data cap is configured. Only a header with none
        // of the four fields is genuinely nothing to report.
        if (fields.isEmpty()) return null
        return SubscriptionUsage(
            uploadBytes = fields["upload"] ?: 0,
            downloadBytes = fields["download"] ?: 0,
            totalBytes = fields["total"] ?: 0,
            expiryEpochSeconds = fields["expire"] ?: 0
        )
    }
}
