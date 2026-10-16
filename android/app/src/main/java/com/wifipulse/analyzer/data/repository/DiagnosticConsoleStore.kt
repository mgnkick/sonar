package com.wifipulse.analyzer.data.repository

import android.content.Context
import com.wifipulse.analyzer.data.model.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Local append-only diagnostic console. Entries survive screen and process recreation until explicitly cleared. */
class DiagnosticConsoleStore(context: Context) {
    private val file = File(context.filesDir, "diagnostics/console.jsonl").apply { parentFile?.mkdirs() }

    @Synchronized fun readAll(): List<DiagnosticLogEntry> = runCatching {
        if (!file.exists()) return emptyList()
        file.readLines().mapNotNull { line -> runCatching { decode(JSONObject(line)) }.getOrNull() }
    }.getOrDefault(emptyList())

    @Synchronized fun append(entry: DiagnosticLogEntry) {
        file.appendText(encode(entry).toString() + "\n")
    }

    @Synchronized fun clear() { file.writeText("") }

    private fun encode(entry: DiagnosticLogEntry) = JSONObject().apply {
        when (entry) {
            is DiagnosticLogEntry.Output -> put("type", "output").put("text", entry.text).put("highlight", entry.isHighlight)
            is DiagnosticLogEntry.Error -> put("type", "error").put("message", entry.message)
            is DiagnosticLogEntry.PingEntry -> put("type", "ping").put("packet", JSONObject().apply {
                val p = entry.packet
                put("sequence", p.sequence).put("bytes", p.bytes).put("ip", p.ip).put("ttl", p.ttl)
                    .put("rtt", p.rttMs.toDouble()).put("timeout", p.isTimeout).put("raw", p.rawText)
            })
            is DiagnosticLogEntry.Summary -> put("type", "summary").put("summary", JSONObject().apply {
                val s = entry.summary
                put("sent", s.packetsTransmitted).put("received", s.packetsReceived).put("loss", s.packetLossPercent.toDouble())
                    .put("min", s.minRttMs.toDouble()).put("avg", s.avgRttMs.toDouble()).put("max", s.maxRttMs.toDouble()).put("mdev", s.mdevRttMs.toDouble())
            })
            is DiagnosticLogEntry.HopEntry -> put("type", "hop").put("hop", JSONObject().apply {
                val h = entry.hop
                put("number", h.hopNumber).put("ip", h.ip).put("host", h.hostName).put("rtt", h.rttMs?.toDouble())
                    .put("timeout", h.isTimeout).put("target", h.isTargetReached).put("raw", h.rawOutput)
                    .put("sent", h.probesSent).put("received", h.probesReceived).put("loss", h.lossPercent.toDouble())
                    .put("min", h.minRttMs?.toDouble()).put("avg", h.averageRttMs?.toDouble()).put("max", h.maxRttMs?.toDouble())
            })
        }
    }

    private fun decode(obj: JSONObject): DiagnosticLogEntry = when (obj.getString("type")) {
        "output" -> DiagnosticLogEntry.Output(obj.optString("text"), obj.optBoolean("highlight"))
        "error" -> DiagnosticLogEntry.Error(obj.optString("message"))
        "ping" -> obj.getJSONObject("packet").let { p -> DiagnosticLogEntry.PingEntry(PingPacket(
            p.optInt("sequence"), p.optInt("bytes"), p.optString("ip"), p.optInt("ttl"), p.optDouble("rtt").toFloat(), p.optBoolean("timeout"), p.optString("raw")
        )) }
        "summary" -> obj.getJSONObject("summary").let { s -> DiagnosticLogEntry.Summary(PingSummary(
            s.optInt("sent"), s.optInt("received"), s.optDouble("loss").toFloat(), s.optDouble("min").toFloat(), s.optDouble("avg").toFloat(), s.optDouble("max").toFloat(), s.optDouble("mdev").toFloat()
        )) }
        "hop" -> obj.getJSONObject("hop").let { h -> DiagnosticLogEntry.HopEntry(TracerouteHop(
            h.optInt("number"), h.optString("ip").takeUnless { it == "null" }, h.optString("host").takeUnless { it == "null" },
            h.optDouble("rtt").takeUnless { it.isNaN() }?.toFloat(), h.optBoolean("timeout"), h.optBoolean("target"), h.optString("raw"),
            h.optInt("sent"), h.optInt("received"), h.optDouble("loss").toFloat(),
            h.optDouble("min").takeUnless { it.isNaN() }?.toFloat(), h.optDouble("avg").takeUnless { it.isNaN() }?.toFloat(), h.optDouble("max").takeUnless { it.isNaN() }?.toFloat()
        )) }
        else -> error("Unknown console entry")
    }
}
