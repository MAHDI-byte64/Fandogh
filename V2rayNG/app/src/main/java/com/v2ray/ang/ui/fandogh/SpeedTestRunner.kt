package com.v2ray.ang.ui.fandogh

import com.v2ray.ang.AppConfig
import com.v2ray.ang.AppConfig.LOOPBACK
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

/**
 * Measures real throughput through the tunnel.
 *
 * The latency test already in the app measures how long a TCP handshake takes, which
 * says nothing about how fast the connection actually is — a server can answer a
 * handshake in 80 ms and then deliver 200 KB/s. This moves real bytes, through the
 * core's own local HTTP proxy, so the number reflects what the user will actually get.
 */
object SpeedTestRunner {

    // Cloudflare's speed endpoints: they exist for exactly this, are reachable from
    // everywhere the tunnel is, and cost nothing to hammer briefly.
    private const val DOWNLOAD_URL = "https://speed.cloudflare.com/__down?bytes=%d"
    private const val UPLOAD_URL = "https://speed.cloudflare.com/__up"

    private const val DOWNLOAD_BYTES = 25_000_000L
    private const val UPLOAD_BYTES = 8_000_000L

    /** Long enough to be past TCP slow start, short enough not to burn the user's data. */
    private const val MAX_PHASE_MILLIS = 12_000L

    sealed interface Phase {
        data object Idle : Phase
        data object Connecting : Phase
        data class Download(val mbps: Double, val progress: Float) : Phase
        data class Upload(val mbps: Double, val progress: Float) : Phase
        data class Done(val downloadMbps: Double, val uploadMbps: Double) : Phase
        data class Failed(val reason: Reason) : Phase
    }

    enum class Reason { NotConnected, Unreachable }

    private val _phase = MutableStateFlow<Phase>(Phase.Idle)
    val phase: StateFlow<Phase> = _phase.asStateFlow()

    fun reset() {
        _phase.value = Phase.Idle
    }

    val isRunning: Boolean
        get() = _phase.value.let {
            it is Phase.Connecting || it is Phase.Download || it is Phase.Upload
        }

    /**
     * Runs a download then an upload pass.
     *
     * @param connected whether the tunnel is up. Measuring while disconnected would
     * report the phone's own connection and quietly pass it off as the VPN's.
     */
    suspend fun run(connected: Boolean) = withContext(Dispatchers.IO) {
        if (!connected) {
            _phase.value = Phase.Failed(Reason.NotConnected)
            return@withContext
        }
        _phase.value = Phase.Connecting

        val port = SettingsManager.getHttpPort()
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .apply {
                if (port != 0) proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress(LOOPBACK, port)))
            }
            .build()

        val download = runCatching { measureDownload(client) }.getOrElse {
            LogUtil.w(AppConfig.TAG, "Speed test download failed: ${it.message}")
            null
        }
        if (download == null) {
            _phase.value = Phase.Failed(Reason.Unreachable)
            return@withContext
        }

        val upload = runCatching { measureUpload(client) }.getOrElse {
            LogUtil.w(AppConfig.TAG, "Speed test upload failed: ${it.message}")
            0.0
        }

        _phase.value = Phase.Done(download, upload)
    }

    private suspend fun measureDownload(client: OkHttpClient): Double {
        val request = Request.Builder()
            .url(DOWNLOAD_URL.format(DOWNLOAD_BYTES))
            .header("Connection", "close")
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body ?: return 0.0
            val source = body.source()
            val buffer = ByteArray(64 * 1024)
            var total = 0L
            val started = System.nanoTime()

            while (true) {
                currentCoroutineContext().ensureActive()
                val read = source.inputStream().read(buffer)
                if (read <= 0) break
                total += read
                val elapsed = (System.nanoTime() - started) / 1_000_000
                if (elapsed > 250) {
                    _phase.value = Phase.Download(
                        mbps = toMbps(total, elapsed),
                        progress = (total.toFloat() / DOWNLOAD_BYTES).coerceIn(0f, 1f)
                    )
                }
                if (elapsed > MAX_PHASE_MILLIS) break
            }

            val elapsed = (System.nanoTime() - started) / 1_000_000
            return toMbps(total, elapsed)
        }
    }

    private suspend fun measureUpload(client: OkHttpClient): Double {
        var sent = 0L
        val started = System.nanoTime()

        // Streamed rather than held in memory: an 8 MB byte array is avoidable, and
        // writing in chunks is what lets progress be reported at all.
        val body = object : RequestBody() {
            override fun contentType() = "application/octet-stream".toMediaType()
            override fun contentLength() = UPLOAD_BYTES

            override fun writeTo(sink: BufferedSink) {
                val chunk = ByteArray(64 * 1024)
                while (sent < UPLOAD_BYTES) {
                    val size = minOf(chunk.size.toLong(), UPLOAD_BYTES - sent).toInt()
                    sink.write(chunk, 0, size)
                    sent += size
                    val elapsed = (System.nanoTime() - started) / 1_000_000
                    if (elapsed > 250) {
                        _phase.value = Phase.Upload(
                            mbps = toMbps(sent, elapsed),
                            progress = (sent.toFloat() / UPLOAD_BYTES).coerceIn(0f, 1f)
                        )
                    }
                    if (elapsed > MAX_PHASE_MILLIS) break
                }
            }
        }

        val request = Request.Builder()
            .url(UPLOAD_URL)
            .post(body)
            .header("Connection", "close")
            .build()

        client.newCall(request).execute().use { }
        val elapsed = (System.nanoTime() - started) / 1_000_000
        return toMbps(sent, elapsed)
    }

    private fun toMbps(bytes: Long, millis: Long): Double {
        if (millis <= 0) return 0.0
        return (bytes * 8.0) / (millis / 1000.0) / 1_000_000.0
    }
}
