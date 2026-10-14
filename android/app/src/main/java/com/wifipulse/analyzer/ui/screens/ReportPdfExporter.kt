package com.wifipulse.analyzer.ui.screens

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.File
import java.io.FileOutputStream

/**
 * Writes a plain, readable, multi-page field report to Downloads/WiFiPulse on Android 10+.
 * On older Android versions it is stored in the app's Documents directory.
 * Returns the saved file location, or null if export failed.
 */
fun exportReportPdf(
    context: Context,
    fileName: String,
    title: String,
    body: String
): String? = runCatching {
    val document = PdfDocument()
    val pageWidth = 595
    val pageHeight = 842
    val margin = 42f
    val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.rgb(35, 45, 60)
        textSize = 10f
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
    }
    val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.rgb(0, 120, 150)
        textSize = 17f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val lineHeight = 15f
    val maxChars = 88
    val lines = buildList {
        body.lines().forEach { raw ->
            if (raw.isBlank()) add("") else raw.chunked(maxChars).forEach { add(it) }
        }
    }
    var pageNumber = 1
    var page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
    var y = 54f
    page.canvas.drawText(title.take(70), margin, y, titlePaint)
    y += 30f
    page.canvas.drawText("WiFi Pulse Analyzer • полевой отчёт", margin, y, bodyPaint)
    y += 25f

    lines.forEach { line ->
        if (y > pageHeight - margin) {
            document.finishPage(page)
            pageNumber += 1
            page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
            y = margin
        }
        page.canvas.drawText(line.take(100), margin, y, bodyPaint)
        y += lineHeight
    }
    document.finishPage(page)

    val safeName = fileName.replace(Regex("[^A-Za-z0-9._-]"), "_").take(70).ifBlank { "WiFiPulse-report" } + ".pdf"
    val location: String
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, safeName)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/WiFiPulse")
        }
        val uri: Uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("Не удалось создать файл в Downloads")
        context.contentResolver.openOutputStream(uri)?.use { document.writeTo(it) }
            ?: error("Не удалось открыть файл отчёта")
        location = uri.toString()
    } else {
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "WiFiPulse").apply { mkdirs() }
        val file = File(dir, safeName)
        FileOutputStream(file).use { document.writeTo(it) }
        location = file.absolutePath
    }
    document.close()
    location
}.getOrNull()
