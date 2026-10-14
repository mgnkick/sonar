package com.wifipulse.analyzer.data.model

enum class DiagnosticMode {
    PING,
    TRACEROUTE
}

enum class DiagnosticStatus {
    IDLE,
    RUNNING,
    COMPLETED,
    CANCELLED,
    ERROR
}

data class PingPacket(
    val sequence: Int,
    val bytes: Int,
    val ip: String,
    val ttl: Int,
    val rttMs: Float,
    val isTimeout: Boolean = false,
    val rawText: String = ""
)

data class PingSummary(
    val packetsTransmitted: Int = 0,
    val packetsReceived: Int = 0,
    val packetLossPercent: Float = 0f,
    val minRttMs: Float = 0f,
    val avgRttMs: Float = 0f,
    val maxRttMs: Float = 0f,
    val mdevRttMs: Float = 0f
)

data class TracerouteHop(
    val hopNumber: Int,
    val ip: String? = null,
    val hostName: String? = null,
    val rttMs: Float? = null,
    val isTimeout: Boolean = false,
    val isTargetReached: Boolean = false,
    val rawOutput: String = ""
)

sealed class DiagnosticLogEntry {
    data class Output(val text: String, val isHighlight: Boolean = false) : DiagnosticLogEntry()
    data class Error(val message: String) : DiagnosticLogEntry()
    data class HopEntry(val hop: TracerouteHop) : DiagnosticLogEntry()
    data class PingEntry(val packet: PingPacket) : DiagnosticLogEntry()
    data class Summary(val summary: PingSummary) : DiagnosticLogEntry()
}
