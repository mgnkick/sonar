package com.mgnkick.wifisonar.ui.screens

import com.mgnkick.wifisonar.AboutIconButton
import com.mgnkick.wifisonar.data.repository.ConnectionHistoryEntry
import com.mgnkick.wifisonar.data.repository.OpenNetworkLogEntry
import com.mgnkick.wifisonar.ui.components.HelpHint
import com.mgnkick.wifisonar.ui.components.LocalAppLanguage
import com.mgnkick.wifisonar.ui.components.AppLanguage
import com.mgnkick.wifisonar.ui.components.Text
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionHistoryScreen(
    entries: List<ConnectionHistoryEntry>,
    openNetworks: List<OpenNetworkLogEntry>,
    onDeleteConnection: (String) -> Unit,
    onClearConnections: () -> Unit,
    onClearOpenNetworks: () -> Unit,
    onGetPassword: (String) -> String?,
    onSavePassword: (String, String) -> Unit,
    onOpenAbout: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var passwordEntryId by remember { mutableStateOf<String?>(null) }
    var passwordDraft by remember { mutableStateOf("") }
    val revealedPasswords = remember { mutableStateMapOf<String, String>() }
    val chronologicalEntries = remember(entries) { entries.sortedBy { it.firstSeenAt } }
    val language = LocalAppLanguage.current
    val dateLocale = if (language == AppLanguage.EN) Locale.US else Locale("ru")

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("История сетей", fontWeight = FontWeight.Bold) },
            navigationIcon = { Icon(Icons.Default.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 14.dp)) },
            actions = {
                IconButton(onClick = { if (selectedTab == 0) { onClearConnections(); revealedPasswords.clear() } else onClearOpenNetworks() }) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = "Очистить журнал", tint = MaterialTheme.colorScheme.error)
                }
                HelpHint("История подключений и открытых Wi-Fi-сетей, сохранённая локально на этом устройстве.")
                AboutIconButton(onOpenAbout)
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
        )
        TabRow(selectedTabIndex = selectedTab) {
            listOf("Подключения" to entries.size, "Сети без пароля" to openNetworks.size).forEachIndexed { index, (label, count) ->
                Tab(selected = selectedTab == index, onClick = { selectedTab = index }, text = { Text("$label ($count)") })
            }
        }
        if (selectedTab == 0) {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    HistoryNotice(
                        title = "ХРОНОЛОГИЯ · СТАРЫЕ СОБЫТИЯ СВЕРХУ",
                        body = "Приложение фиксирует подключения и смену BSSID, замеченные во время его работы. Координаты берутся из последнего известного местоположения Android и могут отсутствовать, если геолокация выключена или данные устарели."
                    )
                }
                if (chronologicalEntries.isEmpty()) {
                    item { EmptyHistory("Пока нет подключений. История начнёт заполняться, когда приложение обнаружит подключение или смену точки доступа.") }
                } else {
                    items(chronologicalEntries, key = { it.id }) { entry ->
                        val isRoam = entry.event.contains("точки доступа", true) || entry.event.contains("сателлит", true) || entry.event.contains("роуминг", true) || entry.event.contains("bssid", true)
                        HistoryEntryCard(
                            entry = entry,
                            accent = if (isRoam) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                            onDelete = { onDeleteConnection(entry.id); revealedPasswords.remove(entry.id) },
                            passwordRevealed = revealedPasswords[entry.id],
                            onTogglePassword = {
                                if (revealedPasswords.containsKey(entry.id)) revealedPasswords.remove(entry.id)
                                else onGetPassword(entry.id)?.let { revealedPasswords[entry.id] = it }
                            },
                            onSetPassword = { passwordDraft = ""; passwordEntryId = entry.id },
                            dateLocale = dateLocale
                        )
                    }
                }
                item {
                    HistoryNotice(
                        title = "ПРИВАТНОСТЬ И ПАРОЛИ",
                        body = "Сетевые имена, BSSID, координаты и журнал открытых сетей остаются на устройстве. Пароль сети приложение не извлекает из Android: его можно внести вручную, он шифруется AES-GCM ключом Android Keystore и показывается только по нажатию."
                    )
                }
            }
        } else {
            val sortedOpenNetworks = remember(openNetworks) { openNetworks.sortedByDescending { it.lastSeenAt } }
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    HistoryNotice(
                        title = "ОТКРЫТЫЕ СЕТИ · ЛОКАЛЬНЫЙ ЖУРНАЛ",
                        body = "Список пополняется по результатам сканирования Wi-Fi, пока приложение открыто. Точки доступа объединяются по BSSID; сохраняются первое и последнее обнаружение, сигнал, диапазон и канал."
                    )
                }
                if (sortedOpenNetworks.isEmpty()) {
                    item { EmptyHistory("Открытые точки доступа пока не обнаружены. Выполните сканирование на вкладке «Сканер».") }
                } else {
                    items(sortedOpenNetworks, key = { it.bssid }) { network ->
                        OpenNetworkHistoryCard(network, dateLocale)
                    }
                }
            }
        }
    }

    if (passwordEntryId != null) {
        AlertDialog(
            onDismissRequest = { passwordEntryId = null; passwordDraft = "" },
            title = { Text("Пароль Wi-Fi") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Введите пароль, известный монтажнику. Сохранённые данные Android приложение не читает.")
                    OutlinedTextField(
                        value = passwordDraft,
                        onValueChange = { passwordDraft = it.take(256) },
                        label = { Text("Пароль сети") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("AES-GCM · Android Keystore · только это устройство", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = { TextButton(onClick = { passwordEntryId?.let { onSavePassword(it, passwordDraft) }; passwordEntryId = null; passwordDraft = ""; revealedPasswords.clear() }) { Text("Сохранить") } },
            dismissButton = { TextButton(onClick = { passwordEntryId?.let { onSavePassword(it, "") }; passwordEntryId = null; passwordDraft = ""; revealedPasswords.clear() }) { Text("Удалить пароль") } }
        )
    }
}

@Composable
private fun HistoryNotice(title: String, body: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 18.sp)
        }
    }
}

@Composable
private fun EmptyHistory(message: String) {
    Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), shape = RoundedCornerShape(14.dp)) {
        Text(message, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 21.sp)
    }
}

@Composable
private fun OpenNetworkHistoryCard(entry: OpenNetworkLogEntry, dateLocale: Locale) {
    val alert = Color(0xFFFF8A4C)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.5.dp, alert.copy(alpha = 0.75f))
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.WifiOff, contentDescription = null, tint = alert)
                Text(entry.ssid, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
                Surface(color = alert.copy(alpha = 0.16f), shape = RoundedCornerShape(8.dp)) {
                    Text("БЕЗ ПАРОЛЯ", Modifier.padding(horizontal = 8.dp, vertical = 5.dp), color = alert, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
            }
            Text("BSSID  ${entry.bssid}", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${entry.band} · CH ${entry.channel} · ${entry.frequencyMhz} МГц · ${entry.rssiDbm} дБм", color = alert, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            HorizontalDivider(color = alert.copy(alpha = 0.2f))
            Text("Первый раз: ${dateTime(entry.firstSeenAt, dateLocale)}", style = MaterialTheme.typography.bodySmall)
            Text("Последний раз: ${dateTime(entry.lastSeenAt, dateLocale)}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun dateTime(timestamp: Long, locale: Locale): String = SimpleDateFormat("dd MMM yyyy · HH:mm:ss", locale).format(Date(timestamp))
