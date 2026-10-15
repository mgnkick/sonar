package com.wifipulse.analyzer.ui.screens

import android.content.Context
import android.graphics.Paint as AndroidPaint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wifipulse.analyzer.data.model.WifiConnectionDetails
import org.json.JSONArray
import org.json.JSONObject

private data class CoverageSample(val x: Float, val y: Float, val rssi: Int, val bssid: String, val time: Long)
private val HeatCyan = Color(0xFF55F4FF)
private const val HEAT_PREFS = "wifi_pulse_experimental_coverage"
private const val HEAT_KEY = "samples"

@Composable
fun ExperimentalHeatmapTab(connectionDetails: WifiConnectionDetails) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val preferences = remember(context) { context.getSharedPreferences(HEAT_PREFS, Context.MODE_PRIVATE) }
    var samples by remember { mutableStateOf(readSamples(preferences.getString(HEAT_KEY, null))) }
    val currentRssi = connectionDetails.rssi.takeIf { connectionDetails.isConnected && it in -100..0 }
    val currentBssid = connectionDetails.bssid.takeIf { it.isNotBlank() && it != "BSSID недоступен" } ?: "BSSID недоступен"

    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Surface(color = Color(0xFF11162A), shape = RoundedCornerShape(16.dp), border = androidx.compose.foundation.BorderStroke(1.dp, HeatCyan.copy(alpha = 0.55f))) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("ЭКСПЕРИМЕНТАЛЬНОЕ · КАРТА ПОКРЫТИЯ", color = HeatCyan, style = MaterialTheme.typography.labelLarge, letterSpacing = 0.8.sp)
                Text("Переместитесь в нужную зону и коснитесь её на схеме. В точку запишутся текущие RSSI и BSSID.", style = MaterialTheme.typography.bodyMedium)
                Text("Сейчас: " + (currentRssi?.let { it.toString() + " дБм" } ?: "нет достоверного RSSI") + " · " + currentBssid, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF090D19)), border = androidx.compose.foundation.BorderStroke(1.dp, HeatCyan.copy(alpha = 0.65f))) {
            Box(
                Modifier.fillMaxWidth().height(300.dp).clip(RoundedCornerShape(18.dp))
                    .pointerInput(currentRssi, currentBssid, samples) {
                        detectTapGestures { tap ->
                            val margin = 20.dp.toPx()
                            val planWidth = (size.width - margin * 2).coerceAtLeast(1f)
                            val planHeight = (size.height - margin * 2).coerceAtLeast(1f)
                            if (tap.x >= margin && tap.x <= size.width - margin &&
                                tap.y >= margin && tap.y <= size.height - margin && currentRssi != null
                            ) {
                                samples = (samples + CoverageSample(
                                    x = ((tap.x - margin) / planWidth).coerceIn(0f, 1f),
                                    y = ((tap.y - margin) / planHeight).coerceIn(0f, 1f),
                                    rssi = currentRssi, bssid = currentBssid, time = System.currentTimeMillis()
                                )).takeLast(200)
                                preferences.edit().putString(HEAT_KEY, writeSamples(samples)).apply()
                            }
                        }
                    }
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val margin = 20.dp.toPx()
                    val left = margin
                    val top = margin
                    val width = (size.width - margin * 2).coerceAtLeast(1f)
                    val height = (size.height - margin * 2).coerceAtLeast(1f)
                    drawRect(Color(0xFF10182A), Offset(left, top), Size(width, height))
                    if (samples.isNotEmpty()) {
                        val columns = 32
                        val rows = 20
                        val cellW = width / columns
                        val cellH = height / rows
                        for (row in 0 until rows) for (col in 0 until columns) {
                            val px = (col + 0.5f) / columns
                            val py = (row + 0.5f) / rows
                            var weighted = 0f
                            var totalWeight = 0f
                            samples.forEach { sample ->
                                val dx = px - sample.x
                                val dy = py - sample.y
                                val weight = 1f / (dx * dx + dy * dy + 0.012f)
                                weighted += sample.rssi * weight
                                totalWeight += weight
                            }
                            val value = weighted / totalWeight
                            val heat = when {
                                value >= -50f -> lerp(Color(0xFFFF4FD8), Color(0xFF55F4FF), ((value + 50f) / 15f).coerceIn(0f, 1f))
                                value >= -65f -> lerp(Color(0xFFFFC857), Color(0xFF55F4FF), ((value + 65f) / 15f).coerceIn(0f, 1f))
                                else -> lerp(Color(0xFFFF5364), Color(0xFFFFC857), ((value + 85f) / 20f).coerceIn(0f, 1f))
                            }
                            drawRect(heat.copy(alpha = 0.52f), Offset(left + col * cellW, top + row * cellH), Size(cellW + 1f, cellH + 1f))
                        }
                    }
                    val wall = HeatCyan.copy(alpha = 0.76f)
                    drawRect(wall, Offset(left, top), Size(width, height), style = Stroke(2.dp.toPx()))
                    drawLine(wall.copy(alpha = 0.5f), Offset(left + width * 0.34f, top), Offset(left + width * 0.34f, top + height * 0.42f), 1.5.dp.toPx())
                    drawLine(wall.copy(alpha = 0.5f), Offset(left + width * 0.34f, top + height * 0.58f), Offset(left + width * 0.34f, top + height), 1.5.dp.toPx())
                    drawLine(wall.copy(alpha = 0.5f), Offset(left + width * 0.68f, top), Offset(left + width * 0.68f, top + height * 0.35f), 1.5.dp.toPx())
                    drawLine(wall.copy(alpha = 0.5f), Offset(left + width * 0.68f, top + height * 0.52f), Offset(left + width * 0.68f, top + height), 1.5.dp.toPx())
                    drawLine(wall.copy(alpha = 0.5f), Offset(left, top + height * 0.55f), Offset(left + width * 0.34f, top + height * 0.55f), 1.5.dp.toPx())
                    drawLine(wall.copy(alpha = 0.5f), Offset(left + width * 0.68f, top + height * 0.55f), Offset(left + width, top + height * 0.55f), 1.5.dp.toPx())

                    val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
                        color = android.graphics.Color.argb(225, 85, 244, 255)
                        textSize = 10.dp.toPx()
                        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
                    }
                    listOf(Triple("ЗОНА A", 0.17f, 0.25f), Triple("ЗОНА B", 0.51f, 0.25f), Triple("ЗОНА C", 0.84f, 0.25f), Triple("ЗОНА D", 0.17f, 0.78f), Triple("ЗОНА E", 0.51f, 0.78f), Triple("ЗОНА F", 0.84f, 0.78f)).forEach { zone ->
                        drawContext.canvas.nativeCanvas.drawText(zone.first, left + width * zone.second, top + height * zone.third, paint)
                    }
                    samples.forEachIndexed { index, sample ->
                        val center = Offset(left + width * sample.x, top + height * sample.y)
                        drawCircle(Color.Black.copy(alpha = 0.55f), radius = 12.dp.toPx(), center = center)
                        drawCircle(HeatCyan, radius = 8.dp.toPx(), center = center)
                        drawCircle(Color(0xFF071018), radius = 4.dp.toPx(), center = center)
                        val pointPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
                            color = android.graphics.Color.WHITE
                            textSize = 9.dp.toPx()
                            typeface = android.graphics.Typeface.DEFAULT_BOLD
                        }
                        drawContext.canvas.nativeCanvas.drawText((index + 1).toString(), center.x + 10.dp.toPx(), center.y - 8.dp.toPx(), pointPaint)
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            HeatLegend(Color(0xFF55F4FF), "Сильный ≥ −50")
            HeatLegend(Color(0xFFFFC857), "Средний −65")
            HeatLegend(Color(0xFFFF5364), "Слабый ≤ −85")
        }
        Text("СХЕМА УСЛОВНАЯ · точки хранятся локально. Цвет между точками — интерполяция измеренного RSSI, не прогноз радиопокрытия.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Измерений: " + samples.size, style = MaterialTheme.typography.titleSmall, color = HeatCyan)
            TextButton(onClick = {
                samples = emptyList()
                preferences.edit().remove(HEAT_KEY).apply()
            }) {
                Icon(Icons.Default.DeleteSweep, contentDescription = null)
                Spacer(Modifier.width(5.dp))
                Text("Очистить точки")
            }
        }
        Text(
            if (currentRssi == null) "Подключите Wi-Fi, чтобы записывать реальные точки уровня сигнала."
            else "Переместитесь в нужную зону и коснитесь её на плане для записи измерения.",
            color = if (currentRssi == null) MaterialTheme.colorScheme.error else HeatCyan,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun HeatLegend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Surface(Modifier.size(10.dp), color = color, shape = RoundedCornerShape(3.dp)) {}
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp)
    }
}

private fun readSamples(serialized: String?): List<CoverageSample> {
    if (serialized.isNullOrBlank()) return emptyList()
    return runCatching {
        val array = JSONArray(serialized)
        buildList {
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                add(CoverageSample(
                    x = item.getDouble("x").toFloat().coerceIn(0f, 1f),
                    y = item.getDouble("y").toFloat().coerceIn(0f, 1f),
                    rssi = item.getInt("rssi").coerceIn(-100, 0),
                    bssid = item.optString("bssid", "BSSID недоступен"),
                    time = item.optLong("time")
                ))
            }
        }.takeLast(200)
    }.getOrDefault(emptyList())
}

private fun writeSamples(samples: List<CoverageSample>): String =
    JSONArray().apply {
        samples.forEach { sample ->
            put(JSONObject().apply {
                put("x", sample.x)
                put("y", sample.y)
                put("rssi", sample.rssi)
                put("bssid", sample.bssid)
                put("time", sample.time)
            })
        }
    }.toString()
