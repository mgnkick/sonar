package com.wifipulse.analyzer.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wifipulse.analyzer.data.repository.ConnectionHistoryEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val HistoryCyan = Color(0xFF55F4FF)
private val HistoryMagenta = Color(0xFFFF5AD9)
private val HistoryGreen = Color(0xFF69F5B5)

@Composable
fun ConnectionHistoryDialog(
    entries: List<ConnectionHistoryEntry>,
    onDelete: (String) -> Unit,
    onClear: () -> Unit,
    onGetPassword: (String) -> String?,
    onSavePassword: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var passwordEntryId by remember { mutableStateOf<String?>(null) }
    var passwordDraft by remember { mutableStateOf("") }
    val revealedPasswords = remember { mutableStateMapOf<String, String>() }
    val chronologicalEntries = remember(entries) { entries.sortedBy { it.firstSeenAt } }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.History, contentDescription = null, tint = HistoryCyan) },
        title = { Text("История подключений", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Surface(
                    color = Color(0xFF11182A),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, HistoryCyan.copy(alpha = 0.35f))
                ) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("ХРОНОЛОГИЯ · СТАРЫЕ СОБЫТИЯ СВЕРХУ", color = HistoryCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.7.sp)
                        Text(
                            "Время показывает, когда приложение обнаружило подключение или смену точки. Android не отдаёт сторонним приложениям полный журнал, пока приложение было закрыто.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 19.sp
                        )
                    }
                }
                if (chronologicalEntries.isEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text("Пока нет событий. Оставьте приложение работающим при подключении или переходе между точками доступа.")
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp, max = 440.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(chronologicalEntries, key = { it.id }) { entry ->
                            val isRoam = entry.event.contains("точки доступа", true) ||
                                entry.event.contains("сателлит", true) ||
                                entry.event.contains("роуминг", true)
                            val accent = if (isRoam) HistoryMagenta else HistoryCyan
                            HistoryEntryCard(
                                entry = entry,
                                accent = accent,
                                onDelete = { onDelete(entry.id); revealedPasswords.remove(entry.id) },
                                passwordRevealed = revealedPasswords[entry.id],
                                onTogglePassword = {
                                    if (revealedPasswords.containsKey(entry.id)) revealedPasswords.remove(entry.id)
                                    else onGetPassword(entry.id)?.let { revealedPasswords[entry.id] = it }
                                },
                                onSetPassword = { passwordDraft = ""; passwordEntryId = entry.id }
                            )
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                Text(
                    "Пароли из Android не считываются. Сохранение пароля здесь — ручной ввод монтажником; данные шифруются локально.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 17.sp
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } },
        dismissButton = {
            if (entries.isNotEmpty()) OutlinedButton(onClick = { onClear(); revealedPasswords.clear() }) { Text("Очистить всё") }
        },
        containerColor = MaterialTheme.colorScheme.surface
    )

    if (passwordEntryId != null) {
        AlertDialog(
            onDismissRequest = { passwordEntryId = null; passwordDraft = "" },
            title = { Text("Пароль Wi-Fi") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Введите пароль, который известен монтажнику. Приложение не читает сохранённые пароли Android.")
                    OutlinedTextField(
                        value = passwordDraft,
                        onValueChange = { passwordDraft = it.take(256) },
                        label = { Text("Пароль сети") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("AES-GCM · Android Keystore · хранение только на этом устройстве", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    passwordEntryId?.let { onSavePassword(it, passwordDraft) }
                    passwordEntryId = null; passwordDraft = ""; revealedPasswords.clear()
                }) { Text("Сохранить") }
            },
            dismissButton = {
                TextButton(onClick = {
                    passwordEntryId?.let { onSavePassword(it, "") }
                    passwordEntryId = null; passwordDraft = ""; revealedPasswords.clear()
                }) { Text("Удалить пароль") }
            }
        )
    }
}

@Composable
private fun HistoryEntryCard(
    entry: ConnectionHistoryEntry,
    accent: Color,
    onDelete: () -> Unit,
    passwordRevealed: String?,
    onTogglePassword: () -> Unit,
    onSetPassword: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF101626)),
        border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.6f))
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("ВРЕМЯ СОБЫТИЯ", color = accent, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                    Text(formatDate(entry.firstSeenAt), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                    Text(formatTime(entry.firstSeenAt), color = accent, fontFamily = FontFamily.Monospace, fontSize = 23.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Удалить запись", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Surface(color = accent.copy(alpha = 0.14f), shape = RoundedCornerShape(8.dp)) {
                Text(eventTitle(entry.event), modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp), color = accent, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.45.sp)
            }
            HorizontalDivider(color = accent.copy(alpha = 0.2f))
            HistoryValue("СЕТЬ (SSID)", entry.ssid, accent)
            HistoryValue(if (entry.bssid == "BSSID недоступен") "ТОЧКА ДОСТУПА" else "BSSID САТЕЛЛИТА", entry.bssid, accent)
            val radio = listOfNotNull(entry.band, entry.channel?.let { "канал $it" }, entry.frequencyMhz?.let { "$it МГц" }, entry.rssiDbm?.let { "$it дБм" }).joinToString(" · ")
            if (radio.isNotBlank()) HistoryValue("РАДИОПАРАМЕТРЫ", radio, HistoryGreen)
            val tech = listOfNotNull(entry.security, entry.channelWidthMhz?.let { "ширина $it МГц" }, entry.standard, entry.linkSpeedMbps?.let { "линк $it Мбит/с" }).joinToString(" · ")
            if (tech.isNotBlank()) HistoryValue("ПОДКЛЮЧЕНИЕ", tech, MaterialTheme.colorScheme.onSurfaceVariant)
            val networkDetails = listOfNotNull(entry.ipAddress?.let { "IP $it" }, entry.gateway?.let { "шлюз $it" }, entry.dns1?.let { "DNS $it" }, entry.dns2?.let { "DNS2 $it" }).joinToString(" · ")
            if (networkDetails.isNotBlank()) HistoryValue("СЕТЕВЫЕ ДАННЫЕ", networkDetails, MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onSetPassword) {
                    Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(16.dp), tint = HistoryCyan)
                    Spacer(Modifier.width(6.dp))
                    Text(if (entry.wifiPasswordCiphertext == null) "Задать пароль" else "Изменить пароль")
                }
                if (entry.wifiPasswordCiphertext != null) {
                    IconButton(onClick = onTogglePassword) {
                        Icon(if (passwordRevealed != null) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = if (passwordRevealed != null) "Скрыть пароль" else "Показать пароль", tint = HistoryCyan)
                    }
                }
            }
            passwordRevealed?.let { Text("Пароль Wi-Fi: " + it, color = HistoryGreen, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
private fun HistoryValue(label: String, value: String, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = color.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, letterSpacing = 0.55.sp)
        Text(value, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium, lineHeight = 21.sp)
    }
}

private fun eventTitle(event: String): String = when {
    event.contains("сателлит", true) || event.contains("точки доступа", true) || event.contains("роуминг", true) -> "↔  ПЕРЕКЛЮЧЕНИЕ МЕЖДУ САТЕЛЛИТАМИ"
    event.contains("заверш", true) -> "■  ОТКЛЮЧЕНИЕ"
    event.contains("друг", true) -> "＋  ПОДКЛЮЧЕНИЕ К ДРУГОЙ СЕТИ"
    event.contains("подключ", true) -> "＋  ПОДКЛЮЧЕНИЕ К СЕТИ"
    else -> event.uppercase(Locale.getDefault())
}

private fun formatDate(timestamp: Long): String = SimpleDateFormat("EEEE, dd MMMM yyyy", Locale.getDefault()).format(Date(timestamp))
private fun formatTime(timestamp: Long): String = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
