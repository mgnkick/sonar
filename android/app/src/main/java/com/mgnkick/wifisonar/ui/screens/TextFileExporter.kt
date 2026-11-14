package com.mgnkick.wifisonar.ui.screens

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/** Saves a UTF-8 text export to Downloads/WiFiPulse. */
fun exportTextFile(context: Context, fileName: String, body: String): String? = runCatching {
    val safeName = fileName.replace(Regex("[^A-Za-z0-9._-]"), "_").take(70).ifBlank { "WiFiPulse-export" } + ".txt"
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, safeName)
            put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/WiFiPulse")
        }
        val uri: Uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("Не удалось создать файл в Downloads")
        context.contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use { it.write(body) }
            ?: error("Не удалось открыть файл")
        uri.toString()
    } else {
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "WiFiPulse").apply { mkdirs() }
        val file = File(dir, safeName)
        file.writeText(body, Charsets.UTF_8)
        file.absolutePath
    }
}.getOrNull()
