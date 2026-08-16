package com.v2ray.ang.dto

import java.io.Serializable

/**
 * A traffic sample sent from the daemon process to the UI.
 *
 * The core runs in :RunSoLibV2RayDaemon and its stats API is only reachable there, so
 * byte counters cannot be read from the UI process at all. Values are deltas accumulated
 * since the previous sample, paired with the interval they cover so the receiver can
 * derive a rate without assuming the sampling period.
 */
data class TrafficMessage(
    val uplinkBytes: Long,
    val downlinkBytes: Long,
    val elapsedMillis: Long,
) : Serializable
