package com.wifipulse.analyzer.data.repository

import android.content.Context
import com.wifipulse.analyzer.data.model.WifiConnectionDetails
import com.wifipulse.analyzer.data.model.WifiNetwork
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import java.util.UUID

/**
 * Offline-only history of Wi-Fi connections observed while WiFi Pulse is running.
 * Android does not expose a complete system-wide connection history or saved Wi-Fi passwords
 * to ordinary third-party applications, so neither is fabricated or collected here.
 */
data class ConnectionHistoryEntry(
    val id: String,
    val ssid: String,
    val bssid: String,
    val firstSeenAt: Long,
    val lastSeenAt: Long,
    val event: String,
    val rssiDbm: Int?,
    val frequencyMhz: Int?,
    val channel: Int?,
    val band: String?,
    val channelWidthMhz: Int?,
    val security: String?,
    val standard: String?,
    val linkSpeedMbps: Int?,
    val ipAddress: String?,
    val gateway: String?,
    val dns1: String?,
    val dns2: String?,
    /** AES-GCM ciphertext only; the plaintext password is never persisted. */
    val wifiPasswordCiphertext: String? = null
)

class ConnectionHistoryRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _entries = MutableStateFlow(loadEntries())
    val entries: StateFlow<List<ConnectionHistoryEntry>> = _entries.asStateFlow()

    private var lastObservedKey: String? = prefs.getString(KEY_LAST_OBSERVED, null)

    @Synchronized
    fun observeConnection(details: WifiConnectionDetails, networks: List<WifiNetwork>) {
        val currentBssid = details.bssid.takeIf {
            details.isConnected && it.isNotBlank() && it != "BSSID недоступен" &&
                it != "02:00:00:00:00:00"
        }
        val key = if (currentBssid != null) currentBssid.lowercase() else if (!details.isConnected) "disconnected" else null
        if (key == null) return

        val now = System.currentTimeMillis()
        if (key == lastObservedKey) {
            // Refresh last-seen time at most once per minute; avoid writing preferences every poll.
            if (currentBssid != null) {
                val latest = _entries.value.firstOrNull { it.bssid.equals(currentBssid, ignoreCase = true) }
                if (latest != null && now - latest.lastSeenAt >= 60_000L) {
                    _entries.value = _entries.value.map {
                        if (it.id == latest.id) it.copy(lastSeenAt = now) else it
                    }
                    persistEntries()
                }
            }
            return
        }
        val connectedNetwork = currentBssid?.let { bssid ->
            networks.firstOrNull { it.bssid.equals(bssid, ignoreCase = true) }
        }
        val event = when {
            currentBssid == null -> "Соединение Wi-Fi завершено"
            lastObservedKey == null -> "Подключение обнаружено приложением"
            lastObservedKey == "disconnected" -> "Подключение обнаружено приложением"
            else -> "Смена точки доступа"
        }

        if (currentBssid != null) {
            val entry = ConnectionHistoryEntry(
                id = UUID.randomUUID().toString(),
                ssid = details.ssid.ifBlank { "SSID недоступен" },
                bssid = currentBssid,
                firstSeenAt = now,
                lastSeenAt = now,
                event = event,
                rssiDbm = details.rssi.takeIf { it in -100..0 },
                frequencyMhz = details.frequency.takeIf { it > 0 } ?: connectedNetwork?.frequency,
                channel = connectedNetwork?.channel,
                band = connectedNetwork?.band?.displayName,
                channelWidthMhz = connectedNetwork?.channelWidth,
                security = connectedNetwork?.securityType?.displayName,
                standard = details.standard.takeIf { it.isNotBlank() },
                linkSpeedMbps = details.linkSpeedMbps.takeIf { it > 0 },
                ipAddress = details.ipAddress.takeIf { it != "0.0.0.0" },
                gateway = details.gateway.takeIf { it != "0.0.0.0" },
                dns1 = details.dns1.takeIf { it != "0.0.0.0" },
                dns2 = details.dns2.takeIf { it != "0.0.0.0" },
            )
            _entries.value = (listOf(entry) + _entries.value).take(MAX_ENTRIES)
            persistEntries()
        }
        lastObservedKey = key
        prefs.edit().putString(KEY_LAST_OBSERVED, key).apply()
    }

    /** Save a password explicitly entered by the technician. Never reads Android-saved credentials. */
    @Synchronized
    fun saveWifiPassword(id: String, password: String) {
        require(password.length <= 256) { "Пароль слишком длинный" }
        val encrypted = password.takeIf { it.isNotEmpty() }?.let(::encryptPassword)
        _entries.value = _entries.value.map { if (it.id == id) it.copy(wifiPasswordCiphertext = encrypted) else it }
        persistEntries()
    }

    /** Decrypt only on explicit user request; returns null if no password is stored or decryption fails. */
    @Synchronized
    fun getWifiPassword(id: String): String? {
        val encrypted = _entries.value.firstOrNull { it.id == id }?.wifiPasswordCiphertext ?: return null
        return runCatching { decryptPassword(encrypted) }.getOrNull()
    }

    private fun encryptPassword(password: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val ciphertext = cipher.doFinal(password.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv + ciphertext, Base64.NO_WRAP)
    }

    private fun decryptPassword(encoded: String): String {
        val payload = Base64.decode(encoded, Base64.NO_WRAP)
        require(payload.size > 12) { "Повреждённые зашифрованные данные" }
        val iv = payload.copyOfRange(0, 12)
        val ciphertext = payload.copyOfRange(12, payload.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(PASSWORD_KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                PASSWORD_KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    @Synchronized
    fun deleteEntry(id: String) {
        _entries.value = _entries.value.filterNot { it.id == id }
        persistEntries()
    }

    @Synchronized
    fun clearHistory() {
        _entries.value = emptyList()
        lastObservedKey = null
        prefs.edit().remove(KEY_LAST_OBSERVED).apply()
        persistEntries()
    }

    private fun persistEntries() {
        val array = JSONArray()
        _entries.value.forEach { entry ->
            array.put(JSONObject().apply {
                put("id", entry.id)
                put("ssid", entry.ssid)
                put("bssid", entry.bssid)
                put("firstSeenAt", entry.firstSeenAt)
                put("lastSeenAt", entry.lastSeenAt)
                put("event", entry.event)
                putNullable("rssiDbm", entry.rssiDbm)
                putNullable("frequencyMhz", entry.frequencyMhz)
                putNullable("channel", entry.channel)
                putNullable("band", entry.band)
                putNullable("channelWidthMhz", entry.channelWidthMhz)
                putNullable("security", entry.security)
                putNullable("standard", entry.standard)
                putNullable("linkSpeedMbps", entry.linkSpeedMbps)
                putNullable("ipAddress", entry.ipAddress)
                putNullable("gateway", entry.gateway)
                putNullable("dns1", entry.dns1)
                putNullable("dns2", entry.dns2)
                putNullable("wifiPasswordCiphertext", entry.wifiPasswordCiphertext)
            })
        }
        prefs.edit().putString(KEY_ENTRIES, array.toString()).apply()
    }

    private fun JSONObject.putNullable(key: String, value: Any?) {
        put(key, value ?: JSONObject.NULL)
    }

    private fun loadEntries(): List<ConnectionHistoryEntry> {
        val raw = prefs.getString(KEY_ENTRIES, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    add(ConnectionHistoryEntry(
                        id = item.optString("id"),
                        ssid = item.optString("ssid", "SSID недоступен"),
                        bssid = item.optString("bssid"),
                        firstSeenAt = item.optLong("firstSeenAt"),
                        lastSeenAt = item.optLong("lastSeenAt"),
                        event = item.optString("event"),
                        rssiDbm = item.optNullableInt("rssiDbm"),
                        frequencyMhz = item.optNullableInt("frequencyMhz"),
                        channel = item.optNullableInt("channel"),
                        band = item.optNullableString("band"),
                        channelWidthMhz = item.optNullableInt("channelWidthMhz"),
                        security = item.optNullableString("security"),
                        standard = item.optNullableString("standard"),
                        linkSpeedMbps = item.optNullableInt("linkSpeedMbps"),
                        ipAddress = item.optNullableString("ipAddress"),
                        gateway = item.optNullableString("gateway"),
                        dns1 = item.optNullableString("dns1"),
                        dns2 = item.optNullableString("dns2"),
                        wifiPasswordCiphertext = item.optNullableString("wifiPasswordCiphertext")
                    ))
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun JSONObject.optNullableInt(key: String): Int? =
        if (isNull(key) || !has(key)) null else optInt(key)

    private fun JSONObject.optNullableString(key: String): String? =
        if (isNull(key) || !has(key)) null else optString(key).takeIf { it.isNotBlank() }

    companion object {
        private const val PREFS_NAME = "wifi_pulse_connection_history"
        private const val KEY_ENTRIES = "entries"
        private const val KEY_LAST_OBSERVED = "last_observed_connection"
        private const val MAX_ENTRIES = 500
        private const val PASSWORD_KEY_ALIAS = "wifi_pulse_history_password_key"
    }
}
