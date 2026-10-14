package com.wifipulse.analyzer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wifipulse.analyzer.data.model.*
import com.wifipulse.analyzer.ui.viewmodel.DiagnosticUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticScreen(
    state: DiagnosticUiState,
    onTargetChange: (String) -> Unit,
    onCountChange: (Int) -> Unit,
    onTimeoutChange: (Int) -> Unit,
    onStartPing: () -> Unit,
    onStartTraceroute: () -> Unit,
    onStop: () -> Unit,
    onClear: () -> Unit,
    connectionDetails: WifiConnectionDetails
) {
    var selectedOutputTab by remember { mutableIntStateOf(0) }
    var showTroubleshooting by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    // Auto-scroll console to bottom when new logs arrive
    LaunchedEffect(state.logs.size) {
        if (state.logs.isNotEmpty()) {
            listState.animateScrollToItem(state.logs.size - 1)
        }
    }

    val isRunning = state.status == DiagnosticStatus.RUNNING

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Диагностика сети",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Ping • Traceroute • диагностика",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onClear,
                        enabled = state.logs.isNotEmpty() && !isRunning
                    ) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Очистить логи")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 10.dp)
                .padding(bottom = 18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(modifier = Modifier.weight(1f)) {
                    ConnectionStatusBanner(details = connectionDetails, modifier = Modifier.padding(top = 4.dp, bottom = 4.dp))
                }
                MiniSonarControl(rssi = if (connectionDetails.isConnected) connectionDetails.rssi else -65)
            }
            SpeedTestLinks(
                onOpen = { url -> runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } },
                onRouter = {
                    val gateway = connectionDetails.gateway.takeIf { it.isNotBlank() && it != "0.0.0.0" }
                    if (gateway != null) context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("http://$gateway")))
                },
                gateway = connectionDetails.gateway
            )
            // Expandable field troubleshooting guide
            Card(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            ) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp)) {
                    TextButton(
                        onClick = { showTroubleshooting = !showTroubleshooting },
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (showTroubleshooting) "Скрыть памятку монтажника" else "Памятка монтажника: типовые неисправности")
                        Spacer(Modifier.weight(1f))
                        Icon(if (showTroubleshooting) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
                    }
                    if (showTroubleshooting) {
                        val scenarios = listOf(
                            "Wi-Fi подключён, интернета нет" to "Проверьте шлюз, затем 1.1.1.1 и DNS-имя yandex.ru. Если шлюз отвечает, а внешние узлы нет — вероятна проблема WAN/провайдера.",
                            "Скорость низкая только в дальней комнате" to "Сравните RSSI и задержку рядом с роутером и в проблемной комнате. Ниже −70 dBm проверьте размещение, препятствия и диапазон 2,4/5 ГГц.",
                            "Высокий ping или потери" to "Сначала проверьте шлюз. Потери уже до шлюза указывают на локальный сегмент; только за шлюзом — на внешний маршрут или провайдера.",
                            "IPTV зависает / видеонаблюдение отваливается" to "Проверьте стабильность RSSI, потери и задержку в течение минуты; для стационарных устройств предпочтительно проводное подключение, если оно возможно."
                        )
                        scenarios.forEach { (title, advice) ->
                            Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                                Text(title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                                Text(advice, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            // Target Input Field
            OutlinedTextField(
                value = state.target,
                onValueChange = onTargetChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                label = { Text("Целевой узел (IP или Домен)") },
                placeholder = { Text("8.8.8.8, 1.1.1.1, google.com") },
                leadingIcon = { Icon(Icons.Default.Language, contentDescription = null) },
                trailingIcon = {
                    if (state.target.isNotEmpty()) {
                        IconButton(onClick = { onTargetChange("") }, enabled = !isRunning) {
                            Icon(Icons.Default.Close, contentDescription = "Очистить")
                        }
                    }
                },
                singleLine = true,
                enabled = !isRunning,
                shape = RoundedCornerShape(14.dp)
            )

            // Preset Quick-Pick Chips
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val presets = (listOfNotNull(connectionDetails.gateway.takeIf { it.isNotBlank() && it != "0.0.0.0" }?.let { "Шлюз: $it" }) + listOf("8.8.8.8", "1.1.1.1", "yandex.ru"))
                items(presets) { preset ->
                    SuggestionChip(
                        onClick = { if (!isRunning) onTargetChange(preset.substringAfter(": ", preset)) },
                        label = { Text(preset, fontSize = 10.sp, fontFamily = FontFamily.Monospace) }
                    )
                }
            }

            // Controls & Options Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Packet Count Selector
                FilterChip(
                    selected = state.packetCount == 4,
                    onClick = { if (!isRunning) onCountChange(4) },
                    label = { Text("4 пак.") },
                    enabled = !isRunning
                )
                FilterChip(
                    selected = state.packetCount == 10,
                    onClick = { if (!isRunning) onCountChange(10) },
                    label = { Text("10 пак.") },
                    enabled = !isRunning
                )
                FilterChip(
                    selected = state.packetCount == 0,
                    onClick = { if (!isRunning) onCountChange(0) },
                    label = { Text("Бесконечно") },
                    enabled = !isRunning
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onStartPing,
                    enabled = !isRunning && state.target.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Пинг")
                }

                Button(
                    onClick = onStartTraceroute,
                    enabled = !isRunning && state.target.isNotEmpty(),
                    modifier = Modifier.weight(1.2f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.AltRoute, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Трассировка")
                }

                if (isRunning) {
                    Button(
                        onClick = onStop,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.weight(0.9f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Стоп")
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Output Mode Tabs
            TabRow(
                selectedTabIndex = selectedOutputTab,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Tab(
                    selected = selectedOutputTab == 0,
                    onClick = { selectedOutputTab = 0 },
                    text = { Text("Консоль вывода (${state.logs.size})", fontSize = 12.sp) }
                )
                Tab(
                    selected = selectedOutputTab == 1,
                    onClick = { selectedOutputTab = 1 },
                    text = { Text("Узлы / Хопы (${state.hops.size})", fontSize = 12.sp) }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Main Terminal / Results Display
            Box(
                modifier = Modifier
                    .height(260.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF0F172A))
                    .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(14.dp))
                    .padding(10.dp)
            ) {
                SelectionContainer {
                if (selectedOutputTab == 0) {
                    // Terminal Output
                    if (state.logs.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = "Готов к запуску. Выберите IP или домен и нажмите «Пинг» или «Трассировка».",
                                color = Color.Gray,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(state.logs) { entry ->
                                when (entry) {
                                    is DiagnosticLogEntry.Output -> {
                                        Text(
                                            text = entry.text,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            color = if (entry.isHighlight) Color(0xFF00E5FF) else Color(0xFFCBD5E1)
                                        )
                                    }
                                    is DiagnosticLogEntry.PingEntry -> {
                                        Text(
                                            text = "${entry.packet.bytes} bytes from ${entry.packet.ip}: icmp_seq=${entry.packet.sequence} ttl=${entry.packet.ttl} time=${entry.packet.rttMs} ms",
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            color = Color(0xFF22C55E)
                                        )
                                    }
                                    is DiagnosticLogEntry.HopEntry -> {
                                        val hop = entry.hop
                                        val text = if (hop.isTimeout) {
                                            "Hop ${hop.hopNumber}: * * * (Превышен таймаут)"
                                        } else {
                                            "Hop ${hop.hopNumber}: ${hop.ip} ${hop.hostName?.let { "($it)" } ?: ""} - ${hop.rttMs} ms ${if (hop.isTargetReached) "[Цель достигнута]" else ""}"
                                        }
                                        Text(
                                            text = text,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            color = if (hop.isTargetReached) Color(0xFF00E5FF) else if (hop.isTimeout) Color(0xFFEF4444) else Color(0xFFE2E8F0)
                                        )
                                    }
                                    is DiagnosticLogEntry.Summary -> {
                                        val s = entry.summary
                                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                            HorizontalDivider(color = Color(0xFF334155))
                                            Text(
                                                text = "--- Статистика: отправлено ${s.packetsTransmitted}, получено ${s.packetsReceived}, потеря ${s.packetLossPercent}% ---",
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF00E5FF)
                                            )
                                            Text(
                                                text = "rtt min/avg/max = ${s.minRttMs} / ${s.avgRttMs} / ${s.maxRttMs} ms",
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 11.sp,
                                                color = Color(0xFF10B981)
                                            )
                                        }
                                    }
                                    is DiagnosticLogEntry.Error -> {
                                        Text(
                                            text = "✕ ${entry.message}",
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            color = Color(0xFFEF4444),
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Structured Traceroute Hops View
                    if (state.hops.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = "Нет данных трассировки. Нажмите «Трассировка» для запуска пошагового зондирования хопов.",
                                color = Color.Gray,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(state.hops) { hop ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (hop.isTargetReached) Color(0xFF1E3A5F) else Color(0xFF1E293B),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = if (hop.isTargetReached) Color(0xFF00E5FF) else Color(0xFF334155),
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text(
                                                        text = hop.hopNumber.toString(),
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (hop.isTargetReached) Color.Black else Color.White
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column {
                                                Text(
                                                    text = hop.ip ?: "* * *",
                                                    fontFamily = FontFamily.Monospace,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp,
                                                    color = if (hop.isTimeout) Color(0xFFEF4444) else Color.White
                                                )
                                                if (hop.hostName != null) {
                                                    Text(
                                                        text = hop.hostName,
                                                        fontSize = 10.sp,
                                                        color = Color(0xFF94A3B8)
                                                    )
                                                }
                                            }
                                        }

                                        if (hop.rttMs != null) {
                                            Text(
                                                text = "${hop.rttMs} ms",
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (hop.rttMs < 35) Color(0xFF22C55E) else if (hop.rttMs < 80) Color(0xFFF59E0B) else Color(0xFFEF4444)
                                            )
                                        } else {
                                            Text(
                                                text = "Таймаут",
                                                fontSize = 10.sp,
                                                color = Color(0xFFEF4444)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            // Copy Log Button Bar
            if (state.logs.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 2.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = {
                            val allText = state.logs.joinToString("\n") {
                                when (it) {
                                    is DiagnosticLogEntry.Output -> it.text
                                    is DiagnosticLogEntry.PingEntry -> "${it.packet.bytes} bytes from ${it.packet.ip}: seq=${it.packet.sequence} time=${it.packet.rttMs} ms"
                                    is DiagnosticLogEntry.HopEntry -> "${it.hop.hopNumber}  ${it.hop.ip ?: "*"}  ${it.hop.rttMs ?: "*"} ms"
                                    is DiagnosticLogEntry.Summary -> "Loss: ${it.summary.packetLossPercent}%, Avg: ${it.summary.avgRttMs} ms"
                                    is DiagnosticLogEntry.Error -> it.message
                                }
                            }
                            clipboardManager.setText(AnnotatedString(allText))
                        }
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Скопировать консоль", fontSize = 11.sp)
                    }
                    TextButton(
                        onClick = {
                            val pdfBody = buildString {
                                appendLine("Дата: ${java.text.SimpleDateFormat("dd.MM.yyyy HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}")
                                appendLine("SSID: ${connectionDetails.ssid.ifBlank { "не подключён" }}")
                                appendLine("BSSID: ${connectionDetails.bssid}")
                                appendLine("RSSI: ${connectionDetails.rssi} dBm • Частота: ${connectionDetails.frequency} MHz • Линк: ${connectionDetails.linkSpeedMbps} Mbps")
                                appendLine("IP: ${connectionDetails.ipAddress} • Шлюз: ${connectionDetails.gateway}")
                                appendLine("DNS: ${connectionDetails.dns1}, ${connectionDetails.dns2}")
                                appendLine("VPN: ${connectionDetails.isVpnActive} • Мобильная сеть: ${connectionDetails.isMobileDataActive}")
                                appendLine("Цель: ${state.target} • Статус: ${state.status}")
                                state.logs.forEach { entry ->
                                    appendLine(when (entry) {
                                        is DiagnosticLogEntry.Output -> entry.text
                                        is DiagnosticLogEntry.PingEntry -> "Ping ${entry.packet.ip}: ${entry.packet.rttMs} ms"
                                        is DiagnosticLogEntry.HopEntry -> "Hop ${entry.hop.hopNumber}: ${entry.hop.ip ?: "*"} ${entry.hop.rttMs ?: "*"} ms"
                                        is DiagnosticLogEntry.Summary -> "Потери ${entry.summary.packetLossPercent}%, средний RTT ${entry.summary.avgRttMs} ms"
                                        is DiagnosticLogEntry.Error -> "ОШИБКА: ${entry.message}"
                                    })
                                }
                            }
                            val savedPdf = exportReportPdf(context, "WiFiPulse-diagnostics-${System.currentTimeMillis()}", "Диагностика сети", pdfBody)
                            Toast.makeText(context, savedPdf?.let { "PDF сохранён в Загрузки/WiFiPulse" } ?: "Не удалось сохранить PDF", Toast.LENGTH_LONG).show()
                        }
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("PDF", fontSize = 11.sp)
                    }
                    TextButton(
                        onClick = {
                            val report = buildString {
                                appendLine("WiFi Pulse Analyzer — отчёт")
                                appendLine("Дата: ${java.text.SimpleDateFormat("dd.MM.yyyy HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}")
                                appendLine("SSID: ${connectionDetails.ssid.ifBlank { "не подключён" }}")
                                appendLine("BSSID: ${connectionDetails.bssid}")
                                appendLine("RSSI: ${connectionDetails.rssi} dBm • Частота: ${connectionDetails.frequency} MHz • Линк: ${connectionDetails.linkSpeedMbps} Mbps")
                                appendLine("IP: ${connectionDetails.ipAddress} • Шлюз: ${connectionDetails.gateway}")
                                appendLine("DNS: ${connectionDetails.dns1}, ${connectionDetails.dns2}")
                                appendLine("VPN: ${connectionDetails.isVpnActive} • Мобильная сеть: ${connectionDetails.isMobileDataActive}")
                                appendLine("Цель: ${state.target} • Статус: ${state.status}")
                                state.logs.forEach { entry ->
                                    appendLine(when (entry) {
                                        is DiagnosticLogEntry.Output -> entry.text
                                        is DiagnosticLogEntry.PingEntry -> "Ping ${entry.packet.ip}: ${entry.packet.rttMs} ms"
                                        is DiagnosticLogEntry.HopEntry -> "Hop ${entry.hop.hopNumber}: ${entry.hop.ip ?: "*"} ${entry.hop.rttMs ?: "*"} ms"
                                        is DiagnosticLogEntry.Summary -> "Потери ${entry.summary.packetLossPercent}%, средний RTT ${entry.summary.avgRttMs} ms"
                                        is DiagnosticLogEntry.Error -> "ОШИБКА: ${entry.message}"
                                    })
                                }
                            }
                            clipboardManager.setText(AnnotatedString(report))
                            context.getSharedPreferences("wifi_pulse_diagnostics", android.content.Context.MODE_PRIVATE)
                                .edit()
                                .putString("last_report", report)
                                .putLong("last_report_saved_at", System.currentTimeMillis())
                                .apply()
                            Toast.makeText(context, "Отчёт сохранён и скопирован", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Сохранить", fontSize = 11.sp)
                    }
                }
            }
        }
    }
}


@Composable
private fun SpeedTestLinks(
    onOpen: (String) -> Unit,
    onRouter: () -> Unit,
    gateway: String
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text("Проверка скорости в браузере", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            item { AssistChip(onClick = { onOpen("https://2ip.ru/speed/") }, label = { Text("2IP", fontSize = 11.sp) }, leadingIcon = { Icon(Icons.Default.Speed, null, modifier = Modifier.size(14.dp)) }) }
            item { AssistChip(onClick = { onOpen("https://yandex.ru/internet/") }, label = { Text("Интернетометр", fontSize = 11.sp) }, leadingIcon = { Icon(Icons.Default.NetworkCheck, null, modifier = Modifier.size(14.dp)) }) }
            item { AssistChip(onClick = { onOpen("https://speedtest.su/") }, label = { Text("Speedtest.su", fontSize = 11.sp) }, leadingIcon = { Icon(Icons.Default.Bolt, null, modifier = Modifier.size(14.dp)) }) }
        }
        OutlinedButton(
            onClick = onRouter,
            enabled = gateway.isNotBlank() && gateway != "0.0.0.0",
            modifier = Modifier.align(Alignment.Start),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Icon(Icons.Default.Router, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(if (gateway.isNotBlank() && gateway != "0.0.0.0") "Настройки роутера • $gateway" else "Шлюз недоступен", fontSize = 11.sp)
        }
    }
}
