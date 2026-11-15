package com.mgnkick.wifisonar.data.backup

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Portable, local-only archive for user-created Wi-Fi history and survey data. */
data class AppDataBackup internal constructor(
    internal val document: JSONObject,
    internal val floorPlan: ByteArray?
) {
    val connectionCount: Int get() = document.optInt("connectionCount")
    val openNetworkCount: Int get() = document.optInt("openNetworkCount")
    val hasFloorPlan: Boolean get() = floorPlan != null
}

object AppDataBackupManager {
    private const val FORMAT = "wifi-sonar-backup"
    private const val VERSION = 1
    private const val MAX_JSON_BYTES = 12 * 1024 * 1024
    private const val MAX_PLAN_BYTES = 24 * 1024 * 1024
    private val preferenceFiles = setOf(
        "wifi_pulse_connection_history",
        "wifi_pulse_open_network_log",
        "wifi_pulse_settings",
        "wifi_pulse_experimental_coverage",
        "wifi_pulse_survey",
        "wifi_pulse_diagnostics"
    )

    fun write(context: Context, destination: Uri) {
        val doc = JSONObject().put("format", FORMAT).put("version", VERSION)
            .put("createdAt", System.currentTimeMillis())
        val prefsArray = JSONArray()
        var connectionCount = 0
        var openNetworkCount = 0
        var floorPlan: ByteArray? = null

        preferenceFiles.forEach { fileName ->
            val all = context.getSharedPreferences(fileName, Context.MODE_PRIVATE).all
            val values = JSONObject()
            all.forEach { (key, rawValue) ->
                if (fileName == "wifi_pulse_settings" && key == "background_monitor_running") return@forEach
                if (fileName == "wifi_pulse_connection_history" && key == "entries" && rawValue is String) {
                    val entries = runCatching { JSONArray(rawValue) }.getOrElse { JSONArray() }
                    connectionCount = entries.length()
                    for (index in 0 until entries.length()) entries.optJSONObject(index)?.remove("wifiPasswordCiphertext")
                    values.put(key, encoded("string", entries.toString()))
                    return@forEach
                }
                if (fileName == "wifi_pulse_open_network_log" && key == "entries" && rawValue is String) {
                    openNetworkCount = runCatching { JSONArray(rawValue).length() }.getOrDefault(0)
                }
                if (fileName == "wifi_pulse_experimental_coverage" && key == "plan_uri" && rawValue is String) {
                    floorPlan = readStreamBounded(context.contentResolver.openInputStream(Uri.parse(rawValue)), MAX_PLAN_BYTES)
                }
                encodeValue(rawValue)?.let { values.put(key, it) }
            }
            prefsArray.put(JSONObject().put("name", fileName).put("values", values))
        }
        doc.put("preferences", prefsArray).put("connectionCount", connectionCount)
            .put("openNetworkCount", openNetworkCount).put("hasFloorPlan", floorPlan != null)

        context.contentResolver.openOutputStream(destination, "wt")?.use { raw ->
            ZipOutputStream(raw).use { zip ->
                zip.putNextEntry(ZipEntry("backup.json"))
                val jsonBytes = doc.toString().toByteArray(Charsets.UTF_8)
                require(jsonBytes.size <= MAX_JSON_BYTES) { "Backup is too large" }
                zip.write(jsonBytes)
                zip.closeEntry()
                floorPlan?.let {
                    zip.putNextEntry(ZipEntry("floor-plan.img"))
                    zip.write(it)
                    zip.closeEntry()
                }
            }
        } ?: error("Could not open destination")
    }

    fun read(context: Context, source: Uri): AppDataBackup {
        var jsonBytes: ByteArray? = null
        var planBytes: ByteArray? = null
        context.contentResolver.openInputStream(source)?.use { input ->
            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    when (entry.name) {
                        "backup.json" -> jsonBytes = readBounded(zip, MAX_JSON_BYTES)
                        "floor-plan.img" -> planBytes = readBounded(zip, MAX_PLAN_BYTES)
                    }
                    zip.closeEntry()
                }
            }
        } ?: error("Could not open backup")
        val doc = JSONObject((jsonBytes ?: error("Backup data is missing")).toString(Charsets.UTF_8))
        require(doc.optString("format") == FORMAT && doc.optInt("version") == VERSION) { "Unsupported backup format" }
        validatePreferences(doc.optJSONArray("preferences") ?: error("Backup preferences are missing"))
        if (doc.optBoolean("hasFloorPlan") && planBytes == null) error("Backup floor plan is incomplete")
        return AppDataBackup(doc, planBytes)
    }

    /** Replaces app-owned preference files only after the caller has confirmed the restore. */
    fun restore(context: Context, backup: AppDataBackup) {
        val prefs = backup.document.getJSONArray("preferences")
        val planUri = backup.floorPlan?.let { bytes ->
            val dir = java.io.File(context.filesDir, "experimental").apply { mkdirs() }
            val file = java.io.File(dir, "restored-floor-plan.img")
            file.outputStream().use { it.write(bytes) }
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file).toString()
        }
        val restored = mutableMapOf<String, MutableMap<String, Any?>>()
        for (index in 0 until prefs.length()) {
            val item = prefs.getJSONObject(index)
            val name = item.getString("name")
            val values = item.getJSONObject("values")
            val decoded = mutableMapOf<String, Any?>()
            values.keys().forEach { key -> decoded[key] = decodeValue(values.getJSONObject(key)) }
            if (name == "wifi_pulse_connection_history") {
                (decoded["entries"] as? String)?.let { raw ->
                    val entries = JSONArray(raw)
                    for (entryIndex in 0 until entries.length()) entries.optJSONObject(entryIndex)?.remove("wifiPasswordCiphertext")
                    decoded["entries"] = entries.toString()
                }
            }
            if (name == "wifi_pulse_settings") decoded["background_monitor_running"] = false
            if (name == "wifi_pulse_experimental_coverage") {
                if (planUri != null) decoded["plan_uri"] = planUri else decoded.remove("plan_uri")
            }
            restored[name] = decoded
        }
        preferenceFiles.forEach { name ->
            val editor = context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear()
            restored[name].orEmpty().forEach { (key, value) ->
                when (value) {
                    is String -> editor.putString(key, value)
                    is Boolean -> editor.putBoolean(key, value)
                    is Int -> editor.putInt(key, value)
                    is Long -> editor.putLong(key, value)
                    is Float -> editor.putFloat(key, value)
                    is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
                }
            }
            check(editor.commit()) { "Could not restore $name" }
        }
    }

    private fun validatePreferences(prefs: JSONArray) {
        val seen = mutableSetOf<String>()
        for (index in 0 until prefs.length()) {
            val item = prefs.getJSONObject(index)
            val name = item.getString("name")
            require(name in preferenceFiles && seen.add(name)) { "Unknown or duplicate app data section" }
            val values = item.getJSONObject("values")
            values.keys().forEach { key -> decodeValue(values.getJSONObject(key)) }
        }
        require(seen.contains("wifi_pulse_connection_history") && seen.contains("wifi_pulse_open_network_log")) {
            "Backup does not contain network history"
        }
    }

    private fun encodeValue(value: Any?): JSONObject? = when (value) {
        is String -> encoded("string", value)
        is Boolean -> encoded("boolean", value)
        is Int -> encoded("int", value)
        is Long -> encoded("long", value)
        is Float -> encoded("float", value.toDouble())
        is Set<*> -> encoded("stringSet", JSONArray(value.filterIsInstance<String>()))
        else -> null
    }

    private fun encoded(type: String, value: Any) = JSONObject().put("type", type).put("value", value)

    private fun decodeValue(value: JSONObject): Any = when (value.getString("type")) {
        "string" -> value.getString("value")
        "boolean" -> value.getBoolean("value")
        "int" -> value.getInt("value")
        "long" -> value.getLong("value")
        "float" -> value.getDouble("value").toFloat()
        "stringSet" -> value.getJSONArray("value").let { array -> (0 until array.length()).map { array.getString(it) }.toSet() }
        else -> error("Unsupported preference value")
    }

    private fun readStreamBounded(stream: InputStream?, maxBytes: Int): ByteArray {
        requireNotNull(stream) { "Could not read floor plan" }
        stream.use { input -> return readBounded(input, maxBytes) }
    }

    private fun readBounded(input: InputStream, maxBytes: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            require(output.size() + count <= maxBytes) { "Backup entry is too large" }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }
}
