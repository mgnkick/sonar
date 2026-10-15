package com.wifipulse.analyzer.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.wifipulse.analyzer.data.repository.ConnectionHistoryEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.History, contentDescription = null) },
        title = { Text("История подключений", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "История, которую WiFi Pulse наблюдал во время работы. Android не предоставляет сторонним приложениям полный системный журнал Wi-Fi.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (entries.isEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text("Пока нет записей. Оставьте приложение запущенным при подключении или смене точки доступа.")
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().height(420.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(entries, key = { it.id }) { entry ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                            ) {
                                Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(entry.ssid, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                            Text(entry.event, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                        }
                                        IconButton(onClick = { onDelete(entry.id); revealedPasswords.remove(entry.id) }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Удалить запись")
                                        }
                                    }
                                    Text("BSSID: ${entry.bssid}", style = MaterialTheme.typography.bodySmall)
                                    Text("Обнаружено: ${formatDate(entry.firstSeenAt)}", style = MaterialTheme.typography.bodySmall)
                                    if (entry.lastSeenAt > entry.firstSeenAt) {
                                        Text("Последний раз замечено: ${formatDate(entry.lastSeenAt)}", style = MaterialTheme.typography.bodySmall)
                                    }
                                    val radio = listOfNotNull(
                                        entry.band,
                                        entry.channel?.let { "канал $it" },
                                        entry.frequencyMhz?.let { "$it МГц" },
                                        entry.rssiDbm?.let { "$it dBm" }
                                    ).joinToString(" · ")
                                    if (radio.isNotBlank()) Text(radio, style = MaterialTheme.typography.bodySmall)
                                    val details = listOfNotNull(
                                        entry.security,
                                        entry.channelWidthMhz?.let { "$it МГц" },
                                        entry.standard,
                                        entry.linkSpeedMbps?.let { "линк $it Мбит/с" }
                                    ).joinToString(" · ")
                                    if (details.isNotBlank()) Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    val networkDetails = listOfNotNull(
                                        entry.ipAddress?.let { "IP $it" },
                                        entry.gateway?.let { "шлюз $it" },
                                        entry.dns1?.let { "DNS $it" },
                                        entry.dns2?.let { "DNS2 $it" }
                                    ).joinToString(" · ")
                                    if (networkDetails.isNotBlank()) Text(networkDetails, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(onClick = {
                                            passwordDraft = ""
                                            passwordEntryId = entry.id
                                        }) {
                                            Icon(Icons.Default.Key, contentDescription = null)
                                            Text(if (entry.wifiPasswordCiphertext == null) "Задать пароль" else "Изменить пароль")
                                        }
                                        if (entry.wifiPasswordCiphertext != null) {
                                            IconButton(onClick = {
                                                if (revealedPasswords.containsKey(entry.id)) {
                                                    revealedPasswords.remove(entry.id)
                                                } else {
                                                    onGetPassword(entry.id)?.let { revealedPasswords[entry.id] = it }
                                                }
                                            }) {
                                                Icon(
                                                    if (revealedPasswords.containsKey(entry.id)) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                    contentDescription = if (revealedPasswords.containsKey(entry.id)) "Скрыть пароль" else "Показать пароль"
                                                )
                                            }
                                        }
                                    }
                                    revealedPasswords[entry.id]?.let { password ->
                                        Text("Пароль Wi-Fi: $password", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                    }
                                    Text(
                                        "Пароль вводится монтажником вручную и хранится в зашифрованном виде на этом устройстве.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
                Divider()
                Text(
                    "Пароли из настроек Android автоматически не извлекаются. Для сохранения нажмите «Задать пароль» и введите пароль, предоставленный абонентом или известный монтажнику.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Введите пароль, который монтажник получил от абонента или установил на роутере. Приложение не считывает пароли из Android.")
                    OutlinedTextField(
                        value = passwordDraft,
                        onValueChange = { passwordDraft = it.take(256) },
                        label = { Text("Пароль сети") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Хранение защищено AES-GCM с ключом Android Keystore. Удаление записи удаляет и сохранённый пароль.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    passwordEntryId?.let { onSavePassword(it, passwordDraft) }
                    passwordEntryId = null
                    passwordDraft = ""
                    revealedPasswords.clear()
                }) { Text("Сохранить") }
            },
            dismissButton = {
                TextButton(onClick = {
                    passwordEntryId?.let { onSavePassword(it, "") }
                    passwordEntryId = null
                    passwordDraft = ""
                    revealedPasswords.clear()
                }) { Text("Удалить пароль") }
            }
        )
    }
}

private fun formatDate(timestamp: Long): String =
    SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
