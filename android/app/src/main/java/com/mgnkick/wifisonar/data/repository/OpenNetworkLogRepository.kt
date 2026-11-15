package com.mgnkick.wifisonar.data.repository

import android.content.Context
import android.location.Location
import com.mgnkick.wifisonar.data.model.SecurityType
import com.mgnkick.wifisonar.data.model.WifiNetwork
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** Offline log of open access points observed by explicit foreground Wi-Fi scans. */
data class OpenNetworkLogEntry(
    val bssid: String,
    val ssid: String,
    val firstSeenAt: Long,
    val lastSeenAt: Long,
    val rssiDbm: Int,
    val frequencyMhz: Int,
    val channel: Int,
    val band: String,
    val firstLatitude: Double? = null,
    val firstLongitude: Double? = null,
    val firstLocationAccuracyMeters: Float? = null,
    val firstLocationCapturedAt: Long? = null,
    val lastLatitude: Double? = null,
    val lastLongitude: Double? = null,
    val lastLocationAccuracyMeters: Float? = null,
    val lastLocationCapturedAt: Long? = null
)

class OpenNetworkLogRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _entries = MutableStateFlow(loadEntries())
    val entries: StateFlow<List<OpenNetworkLogEntry>> = _entries.asStateFlow()
    private val lastPersistedAt = _entries.value.associate { it.bssid.lowercase() to it.lastSeenAt }.toMutableMap()

    @Synchronized
    fun observeScan(networks: List<WifiNetwork>, location: Location? = null) {
        val openNetworks = networks.filter { it.securityType == SecurityType.OPEN }
        if (openNetworks.isEmpty()) return
        val now = System.currentTimeMillis()
        val byBssid = _entries.value.associateBy { it.bssid.lowercase() }.toMutableMap()
        var changed = false
        for (network in openNetworks) {
            if (network.bssid.isBlank()) continue
            val key = network.bssid.lowercase()
            val previous = byBssid[key]
            val fresh = OpenNetworkLogEntry(
                bssid = network.bssid,
                ssid = network.ssid.ifBlank { "[Скрытая сеть]" },
                firstSeenAt = previous?.firstSeenAt ?: now,
                lastSeenAt = now,
                rssiDbm = network.rssi,
                frequencyMhz = network.frequency,
                channel = network.channel,
                band = network.band.displayName,
                firstLatitude = if (previous == null) location?.latitude else previous.firstLatitude,
                firstLongitude = if (previous == null) location?.longitude else previous.firstLongitude,
                firstLocationAccuracyMeters = if (previous == null) location?.accuracy?.takeIf { it > 0f } else previous.firstLocationAccuracyMeters,
                firstLocationCapturedAt = if (previous == null) location?.time else previous.firstLocationCapturedAt,
                lastLatitude = location?.latitude ?: previous?.lastLatitude,
                lastLongitude = location?.longitude ?: previous?.lastLongitude,
                lastLocationAccuracyMeters = location?.accuracy?.takeIf { it > 0f } ?: previous?.lastLocationAccuracyMeters,
                lastLocationCapturedAt = location?.time ?: previous?.lastLocationCapturedAt
            )
            byBssid[key] = fresh
            if (previous == null || now - (lastPersistedAt[key] ?: previous.firstSeenAt) >= PERSIST_INTERVAL_MS ||
                previous.channel != fresh.channel || previous.frequencyMhz != fresh.frequencyMhz ||
                previous.band != fresh.band || previous.ssid != fresh.ssid ||
                (location != null && now - (lastPersistedAt[key] ?: 0L) >= PERSIST_INTERVAL_MS &&
                    (previous.lastLatitude != fresh.lastLatitude || previous.lastLongitude != fresh.lastLongitude ||
                        previous.lastLocationCapturedAt != fresh.lastLocationCapturedAt))
            ) changed = true
        }
        _entries.value = byBssid.values.sortedByDescending { it.lastSeenAt }.take(MAX_ENTRIES)
        if (changed) persist()
    }

    @Synchronized
    fun clear() {
        _entries.value = emptyList()
        prefs.edit().remove(KEY_ENTRIES).apply()
    }

    private fun persist() {
        val array = JSONArray()
        _entries.value.forEach { entry ->
            array.put(JSONObject().apply {
                put("bssid", entry.bssid)
                put("ssid", entry.ssid)
                put("firstSeenAt", entry.firstSeenAt)
                put("lastSeenAt", entry.lastSeenAt)
                put("rssiDbm", entry.rssiDbm)
                put("frequencyMhz", entry.frequencyMhz)
                put("channel", entry.channel)
                put("band", entry.band)
                putNullable("firstLatitude", entry.firstLatitude)
                putNullable("firstLongitude", entry.firstLongitude)
                putNullable("firstLocationAccuracyMeters", entry.firstLocationAccuracyMeters)
                putNullable("firstLocationCapturedAt", entry.firstLocationCapturedAt)
                putNullable("lastLatitude", entry.lastLatitude)
                putNullable("lastLongitude", entry.lastLongitude)
                putNullable("lastLocationAccuracyMeters", entry.lastLocationAccuracyMeters)
                putNullable("lastLocationCapturedAt", entry.lastLocationCapturedAt)
            })
        }
        prefs.edit().putString(KEY_ENTRIES, array.toString()).apply()
        _entries.value.forEach { entry -> lastPersistedAt[entry.bssid.lowercase()] = entry.lastSeenAt }
    }

    private fun loadEntries(): List<OpenNetworkLogEntry> = runCatching {
        val array = JSONArray(prefs.getString(KEY_ENTRIES, "[]"))
        buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(OpenNetworkLogEntry(
                    bssid = item.optString("bssid"),
                    ssid = item.optString("ssid", "[Скрытая сеть]"),
                    firstSeenAt = item.optLong("firstSeenAt"),
                    lastSeenAt = item.optLong("lastSeenAt"),
                    rssiDbm = item.optInt("rssiDbm"),
                    frequencyMhz = item.optInt("frequencyMhz"),
                    channel = item.optInt("channel"),
                    band = item.optString("band"),
                    firstLatitude = item.optNullableDouble("firstLatitude"),
                    firstLongitude = item.optNullableDouble("firstLongitude"),
                    firstLocationAccuracyMeters = item.optNullableFloat("firstLocationAccuracyMeters"),
                    firstLocationCapturedAt = item.optNullableLong("firstLocationCapturedAt"),
                    lastLatitude = item.optNullableDouble("lastLatitude"),
                    lastLongitude = item.optNullableDouble("lastLongitude"),
                    lastLocationAccuracyMeters = item.optNullableFloat("lastLocationAccuracyMeters"),
                    lastLocationCapturedAt = item.optNullableLong("lastLocationCapturedAt")
                ))
            }
        }.sortedByDescending { it.lastSeenAt }.take(MAX_ENTRIES)
    }.getOrDefault(emptyList())

    private fun JSONObject.putNullable(key: String, value: Any?) {
        put(key, value ?: JSONObject.NULL)
    }

    private fun JSONObject.optNullableDouble(key: String): Double? =
        if (isNull(key) || !has(key)) null else optDouble(key)

    private fun JSONObject.optNullableFloat(key: String): Float? =
        if (isNull(key) || !has(key)) null else optDouble(key).toFloat()

    private fun JSONObject.optNullableLong(key: String): Long? =
        if (isNull(key) || !has(key)) null else optLong(key)

    companion object {
        private const val PREFS_NAME = "wifi_pulse_open_network_log"
        private const val KEY_ENTRIES = "entries"
        private const val MAX_ENTRIES = 2000
        private const val PERSIST_INTERVAL_MS = 60_000L
    }
}
