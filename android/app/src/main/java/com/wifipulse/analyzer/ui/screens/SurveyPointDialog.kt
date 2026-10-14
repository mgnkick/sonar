package com.wifipulse.analyzer.ui.screens

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import androidx.compose.foundation.background
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
    val timestamp: Long
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
                Button(
                    onClick = {
                        if (details.isConnected) {
                            val next = SurveyPoint(
                                room = roomName.trim().ifBlank { "Точка ${points.size + 1}" },
                                ssid = details.ssid,
                                rssi = details.rssi,
                                frequency = details.frequency,
                                timestamp = System.currentTimeMillis()
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
                val comparablePoints = points.filter { it.ssid == details.ssid && it.band == currentBand }
                if (comparablePoints.size >= 2) {
                    val best = comparablePoints.maxByOrNull { it.rssi }!!
                    val weakest = comparablePoints.minByOrNull { it.rssi }!!
                    Text(
                        "Для сети ${details.ssid} в диапазоне $currentBand: лучшее место — ${best.room} (${best.rssi} dBm), слабейшее — ${weakest.room} (${weakest.rssi} dBm).",
                        style = MaterialTheme.typography.bodySmall
                    )
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
            timestamp = item.optLong("timestamp", 0L)
        )
    }
}.getOrDefault(emptyList())
