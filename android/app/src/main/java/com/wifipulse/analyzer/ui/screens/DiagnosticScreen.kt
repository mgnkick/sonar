package com.wifipulse.analyzer.ui.screens

import com.wifipulse.analyzer.ui.components.Text

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
import androidx.compose.ui.platform.LocalConfiguration
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.wifipulse.analyzer.data.model.*
import com.wifipulse.analyzer.AboutIconButton
import com.wifipulse.analyzer.ui.viewmodel.DiagnosticUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticScreen(
    state: DiagnosticUiState,
    onTargetChange: (String) -> Unit,
    onCountChange: (Int) -> Unit,
    onPacketSizeChange: (Int) -> Unit,
    onTimeoutChange: (Int) -> Unit,
    onStartPing: () -> Unit,
    onStartTraceroute: () -> Unit,
    onStartGuidedDiagnostics: () -> Unit,
    onStop: () -> Unit,
    onClear: () -> Unit,
    connectionDetails: WifiConnectionDetails,
    visibleNetworkCount: Int,
    isRefreshingNetworks: Boolean,
    onRefreshNetworks: () -> Unit,
    lastScanTimestamp: Long = 0L,
    onOpenAbout: () -> Unit = {}
) {
    var selectedOutputTab by remember { mutableIntStateOf(0) }
    var showTroubleshooting by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val fontScale = LocalConfiguration.current.fontScale.coerceAtLeast(1f)
    // Reserve more visible terminal space for users with enlarged accessibility fonts.
    val consoleHeight = (360f * fontScale).coerceIn(360f, 560f).dp

    // Auto-scroll console to bottom when new logs arrive
    LaunchedEffect(state.logs.size) {
        if (state.logs.isNotEmpty()) {
            listState.animateScrollToItem(state.logs.size - 1)
        }
    }

    var customPacketSize by remember { mutableStateOf("") }
    val packetSizePresets = listOf(32, 64, 128, 512, 1300)
    val isRunning = state.status == DiagnosticStatus.RUNNING

    Scaffold(
        containerColor = Color.Transparent,
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
                            text = "PING • ТСАП • диагностика",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onClear,
                        enabled = state.logs.isNotEmpty()
                    ) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Очистить логи")
                    }
                    AboutIconButton(onOpenAbout)
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = { StickyWifiRefreshButton(isRefreshingNetworks, onRefreshNetworks) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 10.dp)
                .padding(bottom = 18.dp)
        ) {
            ConnectionStatusPanel(
                details = connectionDetails,
                visibleNetworkCount = visibleNetworkCount,
                isRefreshing = isRefreshingNetworks,
                onRefreshNetworks = onRefreshNetworks,
                lastScanTimestamp = lastScanTimestamp,
                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
                showRefreshButton = false
            )
            SpeedTestLinks(
                onOpen = { url -> runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } },
                onRouter = {
                    val gateway = connectionDetails.gateway.takeIf { it.isNotBlank() && it != "0.0.0.0" }
                    if (gateway != null) context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("http://$gateway")))
                },
                gateway = connectionDetails.gateway
            )

            Button(
                onClick = onStartGuidedDiagnostics,
                enabled = !isRunning,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.FactCheck, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Проверить шлюз, DNS и интернет")
            }
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

            Spacer(modifier = Modifier.height(6.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f))
            ) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.DataUsage, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Размер ICMP payload", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text("32 байта по умолчанию • диапазон 8–1400 байт", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Surface(shape = RoundedCornerShape(9.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                            Text("${state.packetSizeBytes} B", modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp), color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
                        }
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                        items(packetSizePresets) { bytes ->
                            FilterChip(
                                selected = state.packetSizeBytes == bytes,
                                onClick = { if (!isRunning) { onPacketSizeChange(bytes); customPacketSize = "" } },
                                enabled = !isRunning,
                                label = { Text("$bytes B") }
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = customPacketSize,
                            onValueChange = { value ->
                                customPacketSize = value.filter(Char::isDigit).take(4)
                                customPacketSize.toIntOrNull()?.takeIf { it in 8..1400 }?.let(onPacketSizeChange)
                            },
                            modifier = Modifier.weight(1f),
                            enabled = !isRunning,
                            singleLine = true,
                            label = { Text("Свой размер, байт") },
                            placeholder = { Text("8–1400") },
                            leadingIcon = { Icon(Icons.Default.Tune, contentDescription = null) },
                            supportingText = { Text("Максимум 1400, чтобы снизить риск IP-фрагментации") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(12.dp)
                        )
                        TextButton(
                            onClick = { customPacketSize.toIntOrNull()?.takeIf { it in 8..1400 }?.let(onPacketSizeChange) },
                            enabled = !isRunning && (customPacketSize.toIntOrNull()?.let { it in 8..1400 } == true)
                        ) { Text("Применить") }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

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
                    Text("Трассировка\nс анализом потерь (ТСАП)", fontSize = 11.sp, lineHeight = 13.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
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

            // Terminal status strip: visually separates live diagnostic output from form controls.
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF0B1422),
                shape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1B7182))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Terminal, contentDescription = null, tint = Color(0xFF54F4FF), modifier = Modifier.size(16.dp))
                        Text("FIELD TERMINAL", fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF54F4FF))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.size(7.dp).clip(RoundedCornerShape(50)).background(if (isRunning) Color(0xFFFFC857) else Color(0xFF64F7B0)))
                        Text(if (isRunning) "RUNNING" else "READY", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = if (isRunning) Color(0xFFFFC857) else Color(0xFF64F7B0))
                    }
                }
            }

            // Main Terminal / Results Display
            Box(
                modifier = Modifier
                    .height(consoleHeight)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp))
                    .background(Color(0xFF070B14))
                    .border(1.dp, Color(0xFF1B7182), RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp))
                    .padding(10.dp)
            ) {
                SelectionContainer {
                if (selectedOutputTab == 0) {
                    // Terminal Output
                    if (state.logs.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = "Готов к запуску. Выберите IP или домен и нажмите «Пинг» или «Трассировка с анализом потерь (ТСАП)».",
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
                                            color = if (entry.isHighlight) Color(0xFF54F4FF) else Color(0xFFD8E8F4)
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
                                            color = if (hop.isTargetReached) Color(0xFF54F4FF) else if (hop.isTimeout) Color(0xFFEF4444) else Color(0xFFE2E8F0)
                                        )
                                    }
                                    is DiagnosticLogEntry.Summary -> {
                                        val s = entry.summary
                                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                            HorizontalDivider(color = Color(0xFF29445E))
                                            Text(
                                                text = "--- Статистика: отправлено ${s.packetsTransmitted}, получено ${s.packetsReceived}, потеря ${s.packetLossPercent}% ---",
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF54F4FF)
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
                                text = if (isRunning) "ТСАП собирает выборку. Остановите трассировку, чтобы зафиксировать результат." else "Нет данных трассировки. Нажмите «ТСАП» для запуска непрерывного анализа потерь.",
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
                                val lossColor = when {
                                    hop.probesSent == 0 -> Color(0xFF68758D)
                                    hop.lossPercent >= 25f -> Color(0xFFFF5577)
                                    hop.lossPercent > 0f -> Color(0xFFFFC857)
                                    else -> Color(0xFF59F2BD)
                                }
                                val avgRtt = hop.averageRttMs ?: hop.rttMs
                                val rttColor = when {
                                    avgRtt == null -> Color(0xFFFF5577)
                                    avgRtt < 35f -> Color(0xFF59F2BD)
                                    avgRtt < 80f -> Color(0xFFFFC857)
                                    else -> Color(0xFFFF7799)
                                }
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFF101827),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, if (hop.isTargetReached) Color(0xFF55F4FF) else lossColor.copy(alpha = 0.7f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = if (hop.isTargetReached) Color(0xFF55F4FF) else if (hop.probesSent > 0) lossColor.copy(alpha = 0.18f) else Color(0xFF303B55),
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text(
                                                        text = hop.hopNumber.toString(),
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (hop.isTargetReached) Color.Black else lossColor
                                                    )
                                                }
                                            }
                                            Spacer(Modifier.width(10.dp))
                                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                Text(hop.ip ?: "Нет ответа от узла", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = if (hop.probesReceived == 0 && hop.probesSent > 0) Color(0xFFFF7799) else Color(0xFFEAF6FF), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                                Text(hop.hostName ?: if (hop.isTargetReached) "Конечный узел" else "Маршрутный хоп", fontSize = 10.sp, color = Color(0xFF94A3B8), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                            }
                                            if (hop.isTargetReached) {
                                                Surface(color = Color(0xFF143D49), shape = RoundedCornerShape(20.dp)) {
                                                    Text("ЦЕЛЬ", modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp), fontSize = 9.sp, fontWeight = FontWeight.Black, color = Color(0xFF55F4FF), letterSpacing = 0.8.sp)
                                                }
                                            }
                                        }
                                    if (hop.probesSent > 0) {
                                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                TsapMetric("ПОТЕРИ", "${"%.0f".format(hop.lossPercent)}%", lossColor, Modifier.weight(0.8f))
                                                TsapMetric("ОТВЕТЫ", "${hop.probesReceived} / ${hop.probesSent}", Color(0xFF55F4FF), Modifier.weight(1f))
                                                TsapMetric("СРЕДНИЙ RTT", avgRtt?.let { "${"%.1f".format(it)} ms" } ?: "—", rttColor, Modifier.weight(1.2f))
                                            }
                                            Text(
                                                text = "МИН ${hop.minRttMs?.let { "%.1f".format(it) } ?: "—"} ms     МАКС ${hop.maxRttMs?.let { "%.1f".format(it) } ?: "—"} ms",
                                                modifier = Modifier.padding(start = 2.dp),
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 9.sp,
                                                color = Color(0xFF9EACC2)
                                            )
                                        } else {
                                            Text("ОЖИДАНИЕ ПЕРВОЙ ПРОБЫ", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = Color(0xFF9EACC2), letterSpacing = 0.7.sp)
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
private fun TsapMetric(label: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = accent.copy(alpha = 0.09f),
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.28f))
    ) {
        Column(Modifier.padding(horizontal = 7.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(label, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color(0xFFA4B2C7), letterSpacing = 0.6.sp, maxLines = 1)
            Text(value, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = accent, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
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

