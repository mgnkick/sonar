package com.wifipulse.analyzer.ui.screens

import android.content.Context
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wifipulse.analyzer.data.model.WifiConnectionDetails
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class SurveyPoint(
    val room: String,
    val ssid: String,
    val rssi: Int,
    val frequency: Int,
    val timestamp: Long,
    val cell: Int = 4
) {
    val band: String get() = when {
        frequency in 2400..2500 -> "2,4 ГГц"
        frequency in 4900..5900 -> "5 ГГц"
        frequency in 5925..7125 -> "6 ГГц"
        else -> "диапазон неизвестен"
    }
}

@Composable
fun SurveyPointDialog(
    details: WifiConnectionDetails,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("wifi_pulse_survey", Context.MODE_PRIVATE) }
    var roomName by remember { mutableStateOf("") }
    var selectedCell by remember { mutableIntStateOf(4) }
    var points by remember { mutableStateOf(loadSurveyPoints(prefs.getString("points", "[]") ?: "[]")) }

    val currentBand = when {
        details.frequency in 2400..2500 -> "2,4 ГГц"
        details.frequency in 4900..5900 -> "5 ГГц"
        details.frequency in 5925..7125 -> "6 ГГц"
        else -> "диапазон неизвестен"
    }

    fun persist(updated: List<SurveyPoint>) {
        points = updated
        prefs.edit().putString("points", JSONArray().apply {
            updated.forEach { point ->
                put(JSONObject().apply {
                    put("room", point.room)
                    put("ssid", point.ssid)
                    put("rssi", point.rssi)
                    put("frequency", point.frequency)
                    put("timestamp", point.timestamp)
                    put("cell", point.cell)
                })
            }
        }.toString()).apply()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Обследование квартиры", fontWeight = FontWeight.Bold)
                Text(
                    "Фиксируйте сигнал в каждой комнате",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = roomName,
                    onValueChange = { roomName = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Комната / место") },
                    placeholder = { Text("Например, спальня") }
                )
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            if (details.isConnected) details.ssid else "Wi-Fi не подключён",
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            if (details.isConnected) "${details.rssi} dBm • ${details.frequency} MHz • ${details.linkSpeedMbps} Мбит/с"
                            else "Подключитесь к сети клиента для фиксации RSSI",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text("Схема покрытия — выберите примерное расположение точки", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(3) { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            repeat(3) { col ->
                                val cell = row * 3 + col
                                val cellPoint = points.filter { it.cell == cell }.maxByOrNull { it.timestamp }
                                val heat = when {
                                    cellPoint == null -> MaterialTheme.colorScheme.surfaceVariant
                                    cellPoint.rssi >= -55 -> Color(0xFF166534)
                                    cellPoint.rssi >= -67 -> Color(0xFF65A30D)
                                    cellPoint.rssi >= -75 -> Color(0xFFCA8A04)
                                    else -> Color(0xFFB91C1C)
                                }
                                Surface(
                                    modifier = Modifier.weight(1f).height(58.dp).clickable { selectedCell = cell },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (selectedCell == cell) MaterialTheme.colorScheme.primaryContainer else heat,
                                    border = if (selectedCell == cell) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxSize().padding(4.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            cellPoint?.room ?: "Зона ${cell + 1}",
                                            style = MaterialTheme.typography.labelSmall,
                                            maxLines = 1
                                        )
                                        if (cellPoint != null) Text("${cellPoint.rssi} dBm", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }
                Button(
                    onClick = {
                        if (details.isConnected) {
                            val next = SurveyPoint(
                                room = roomName.trim().ifBlank { "Зона ${selectedCell + 1}" },
                                ssid = details.ssid,
                                rssi = details.rssi,
                                frequency = details.frequency,
                                timestamp = System.currentTimeMillis(),
                                cell = selectedCell
                            )
                            persist(points + next)
                            roomName = ""
                        }
                    },
                    enabled = details.isConnected,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Зафиксировать точку")
                }
                OutlinedButton(
                    onClick = {
                        val report = buildString {
                            appendLine("Дата: ${SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault()).format(Date())}")
                            appendLine("Подключённая сеть: ${details.ssid}")
                            appendLine("Текущая частота: ${details.frequency} МГц")
                            appendLine("Замеров: ${points.size}")
                            appendLine()
                            points.forEachIndexed { i, point ->
                                appendLine("${i + 1}. ${point.room} | ${point.ssid} | ${point.band} | ${point.rssi} dBm | ${point.frequency} MHz")
                            }
                            val comparable = points.filter { it.ssid == details.ssid && it.band == currentBand }
                            if (comparable.size >= 2) {
                                appendLine()
                                appendLine("Лучшее место: ${comparable.maxByOrNull { it.rssi }?.room}")
                                appendLine("Слабейшее место: ${comparable.minByOrNull { it.rssi }?.room}")
                            }
                        }
                        val saved = exportReportPdf(context, "WiFiPulse-survey-${System.currentTimeMillis()}", "Обследование Wi-Fi", report)
                        Toast.makeText(context, saved?.let { "PDF сохранён: ${if (it.startsWith("content:")) "Загрузки/WiFiPulse" else it}" } ?: "Не удалось сохранить PDF", Toast.LENGTH_LONG).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Экспорт отчёта PDF")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Замеры: ${points.size}", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton(
                        onClick = { persist(emptyList()) },
                        enabled = points.isNotEmpty()
                    ) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text("Очистить")
                    }
                }
                points.forEachIndexed { index, point ->
                    val qualityColor = when {
                        point.rssi >= -60 -> Color(0xFF16A34A)
                        point.rssi >= -70 -> Color(0xFFCA8A04)
                        else -> Color(0xFFDC2626)
                    }
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.LocationOn, contentDescription = null, tint = qualityColor)
                            Column(Modifier.weight(1f)) {
                                Text(point.room, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "${point.ssid} • ${point.band} • ${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(point.timestamp))}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text("${point.rssi} dBm", color = qualityColor, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            IconButton(onClick = { persist(points.filterIndexed { i, _ -> i != index }) }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "Удалить замер", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
                if (points.isNotEmpty()) {
                    Text("Карта покрытия по зафиксированным точкам", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    Text("Цвет показывает уровень RSSI; это схема замеров, а не план помещения.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    points.forEach { point ->
                        val heatColor = when {
                            point.rssi >= -55 -> Color(0xFF16A34A)
                            point.rssi >= -67 -> Color(0xFF65A30D)
                            point.rssi >= -75 -> Color(0xFFEAB308)
                            else -> Color(0xFFDC2626)
                        }
                        Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(point.room, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, maxLines = 1)
                                Text("${point.rssi} dBm", color = heatColor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                            }
                            LinearProgressIndicator(
                                progress = ((point.rssi + 100).coerceIn(0, 70) / 70f),
                                modifier = Modifier.fillMaxWidth().height(7.dp),
                                color = heatColor,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        }
                    }
                }
                val comparablePoints = points.filter { it.ssid == details.ssid && it.band == currentBand }
                if (comparablePoints.size >= 2) {
                    val best = comparablePoints.maxByOrNull { it.rssi }!!
                    val weakest = comparablePoints.minByOrNull { it.rssi }!!
                    Text(
                        "Для сети ${details.ssid} в диапазоне $currentBand: лучшее место — ${best.room} (${best.rssi} dBm), слабейшее — ${weakest.room} (${weakest.rssi} dBm).",
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (weakest.rssi <= -75) {
                        Text(
                            "Рекомендация: проверьте более центральное место установки роутера. Если слабая зона остаётся после перемещения, рассмотрите дополнительную точку доступа с проводным backhaul; Mesh/репитер не следует ставить в зоне уже слабого сигнала.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else if (best.rssi - weakest.rssi >= 15) {
                        Text(
                            "Разница между лучшей и слабейшей точками составляет ${best.rssi - weakest.rssi} dB. Перемещение роутера может заметно улучшить покрытие слабой комнаты.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                } else if (points.isNotEmpty()) {
                    Text(
                        "Для корректного сравнения зафиксируйте минимум две точки с одной и той же сетью и диапазоном ($currentBand).",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Готово") } }
    )
}

private fun loadSurveyPoints(raw: String): List<SurveyPoint> = runCatching {
    val array = JSONArray(raw)
    (0 until array.length()).map { index ->
        val item = array.getJSONObject(index)
        SurveyPoint(
            room = item.optString("room", "Точка ${index + 1}"),
            ssid = item.optString("ssid", ""),
            rssi = item.optInt("rssi", -100),
            frequency = item.optInt("frequency", 0),
            timestamp = item.optLong("timestamp", 0L),
            cell = item.optInt("cell", index % 9).coerceIn(0, 8)
        )
    }
}.getOrDefault(emptyList())
