package com.mgnkick.wifisonar.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mgnkick.wifisonar.AboutIconButton
import com.mgnkick.wifisonar.data.model.*
import com.mgnkick.wifisonar.ui.components.HelpHint
import com.mgnkick.wifisonar.ui.components.Text
import com.mgnkick.wifisonar.ui.viewmodel.DiagnosticUiState

private enum class DiagnosticTool { PING, TRACE, ECHO, TCP }

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
    onStartEchoScan: (String, String, String) -> Unit,
    onStartPortScan: (String, String) -> Unit,
    onStartGuidedDiagnostics: () -> Unit,
    onStop: () -> Unit,
    onClear: () -> Unit,
    connectionDetails: WifiConnectionDetails,
    visibleNetworkCount: Int,
    isRefreshingNetworks: Boolean,
    onRefreshNetworks: () -> Unit,
    lastScanTimestamp: Long = 0L,
    scanCooldownSeconds: Int = 0,
    onOpenAbout: () -> Unit = {}
) {
    var selectedTool by remember(state.mode) {
        mutableStateOf(when (state.mode) {
            DiagnosticMode.TRACEROUTE -> DiagnosticTool.TRACE
            DiagnosticMode.ECHO -> DiagnosticTool.ECHO
            DiagnosticMode.PORT_SCAN -> DiagnosticTool.TCP
            else -> DiagnosticTool.PING
        })
    }
    var advancedExpanded by remember { mutableStateOf(false) }
    var guideExpanded by remember { mutableStateOf(false) }
    var showConsole by remember { mutableStateOf(false) }
    var selectedOutputTab by remember { mutableIntStateOf(0) }
    var wasInterrupted by remember { mutableStateOf(false) }
    var lastAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var customPacketSize by remember { mutableStateOf("") }
    var portTarget by remember(connectionDetails.gateway) { mutableStateOf(connectionDetails.gateway) }
    var portRange by remember { mutableStateOf("1-1024") }
    val running = state.status == DiagnosticStatus.RUNNING
    val context = LocalContext.current
    val wide = LocalConfiguration.current.screenWidthDp >= 840
    val fontScale = LocalConfiguration.current.fontScale.coerceAtLeast(1f)
    val consoleHeight = (420f * fontScale).coerceIn(320f, 560f).dp
    val interrupt: () -> Unit = { wasInterrupted = true; onStop() }
    val continueLastAction: () -> Unit = {
        lastAction?.let { action ->
            wasInterrupted = false
            action()
        }
    }
    val runPing: () -> Unit = { wasInterrupted = false; lastAction = onStartPing; onStartPing() }
    val runTrace: () -> Unit = { wasInterrupted = false; lastAction = onStartTraceroute; onStartTraceroute() }
    val runEcho: (String, String, String) -> Unit = { ip, mask, gateway ->
        wasInterrupted = false
        lastAction = { onStartEchoScan(ip, mask, gateway) }
        onStartEchoScan(ip, mask, gateway)
    }
    val runTcp: (String, String) -> Unit = { target, ports ->
        wasInterrupted = false
        lastAction = { onStartPortScan(target, ports) }
        onStartPortScan(target, ports)
    }
    val runGuided: () -> Unit = { wasInterrupted = false; lastAction = onStartGuidedDiagnostics; onStartGuidedDiagnostics() }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Диагностика", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
                actions = {
                    if (state.logs.isNotEmpty()) IconButton(onClick = onClear) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Очистить логи")
                    }
                    HelpHint("Выберите инструмент, задайте цель и откройте консоль для подробных результатов.")
                    AboutIconButton(onOpenAbout)
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (running) StickyWifiStopButton(onStop = interrupt)
                StickyWifiRefreshButton(isRefreshingNetworks, onRefreshNetworks, cooldownSeconds = scanCooldownSeconds)
            }
        }
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            if (wide) {
                Row(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(Modifier.weight(0.92f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        ConnectionStatusPanel(connectionDetails, visibleNetworkCount, isRefreshingNetworks, onRefreshNetworks, lastScanTimestamp, showRefreshButton = false)
                        DiagnosticControls(
                            state, selectedTool, { selectedTool = it }, onTargetChange, onCountChange, onPacketSizeChange, onTimeoutChange,
                            runPing, runTrace, runEcho, runTcp, runGuided,
                            interrupt,
                            running, connectionDetails, portTarget, { portTarget = it }, portRange, { portRange = it },
                            advancedExpanded, { advancedExpanded = !advancedExpanded }, customPacketSize, { customPacketSize = it },
                            guideExpanded, { guideExpanded = !guideExpanded },
                            onSpeedOpen = { url -> runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } },
                            onRouter = { openRouter(context, connectionDetails.gateway) }
                        )
                    }
                    DiagnosticConsole(
                        state, running, selectedOutputTab, { selectedOutputTab = it }, onClear, connectionDetails,
                        Modifier.weight(1.08f).fillMaxHeight().padding(bottom = 8.dp), consoleHeight, showActions = true,
                        onInterrupt = interrupt, onContinue = continueLastAction, canContinue = wasInterrupted && lastAction != null
                    )
                }
            } else {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp).padding(bottom = 102.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ConnectionStatusPanel(connectionDetails, visibleNetworkCount, isRefreshingNetworks, onRefreshNetworks, lastScanTimestamp, modifier = Modifier.padding(top = 4.dp), showRefreshButton = false)
                    DiagnosticControls(
                        state, selectedTool, { selectedTool = it }, onTargetChange, onCountChange, onPacketSizeChange, onTimeoutChange,
                        runPing, runTrace, runEcho, runTcp, runGuided,
                        interrupt,
                        running, connectionDetails, portTarget, { portTarget = it }, portRange, { portRange = it },
                        advancedExpanded, { advancedExpanded = !advancedExpanded }, customPacketSize, { customPacketSize = it },
                        guideExpanded, { guideExpanded = !guideExpanded },
                        onSpeedOpen = { url -> runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } },
                        onRouter = { openRouter(context, connectionDetails.gateway) }
                    )
                    Card(
                        onClick = { showConsole = true }, modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Default.Terminal, null, tint = MaterialTheme.colorScheme.primary)
                            Column(Modifier.weight(1f)) {
                                Text("Консоль диагностики", fontWeight = FontWeight.SemiBold)
                                Text("${state.logs.size} записей · ${state.hops.size} хопов", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (running) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            Icon(Icons.Default.KeyboardArrowUp, "Открыть консоль")
                        }
                    }
                }
            }
        }
    }

    if (showConsole && !wide) {
        ModalBottomSheet(
            onDismissRequest = { showConsole = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
            containerColor = MaterialTheme.colorScheme.surface,
            dragHandle = {
                Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(Modifier.size(36.dp, 4.dp), shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .55f)) {}
                    Text("Потяните вниз, чтобы закрыть", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 5.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        ) {
            DiagnosticConsole(state, running, selectedOutputTab, { selectedOutputTab = it }, onClear, connectionDetails, Modifier.fillMaxWidth().height(consoleHeight + 210.dp).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp), consoleHeight, showActions = true, onInterrupt = interrupt, onContinue = continueLastAction, canContinue = wasInterrupted && lastAction != null)
        }
    }
}

@Composable
private fun DiagnosticControls(
    state: DiagnosticUiState,
    selectedTool: DiagnosticTool,
    onToolChange: (DiagnosticTool) -> Unit,
    onTargetChange: (String) -> Unit,
    onCountChange: (Int) -> Unit,
    onPacketSizeChange: (Int) -> Unit,
    onTimeoutChange: (Int) -> Unit,
    onStartPing: () -> Unit,
    onStartTraceroute: () -> Unit,
    onStartEchoScan: (String, String, String) -> Unit,
    onStartPortScan: (String, String) -> Unit,
    onStartGuidedDiagnostics: () -> Unit,
    onStop: () -> Unit,
    running: Boolean,
    connection: WifiConnectionDetails,
    portTarget: String,
    onPortTarget: (String) -> Unit,
    portRange: String,
    onPortRange: (String) -> Unit,
    advancedExpanded: Boolean,
    toggleAdvanced: () -> Unit,
    customPacketSize: String,
    onCustomPacketSize: (String) -> Unit,
    guideExpanded: Boolean,
    toggleGuide: () -> Unit,
    onSpeedOpen: (String) -> Unit,
    onRouter: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val presets = listOfNotNull(connection.gateway.takeIf { it.isNotBlank() && it != "0.0.0.0" }?.let { "Шлюз · $it" }) + listOf("8.8.8.8", "1.1.1.1", "yandex.ru")

    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = colors.surface)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("Параметры сети", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Text("IP ${connection.ipAddress.ifBlank { "—" }} · шлюз ${connection.gateway.ifBlank { "—" }} · маска ${connection.subnetMask.ifBlank { "—" }}", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("DNS ${connection.dns1.ifBlank { "—" }}${if (connection.dns2.isNotBlank()) ", ${connection.dns2}" else ""}", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            HelpHint("IP-адрес телефона, шлюз, маска и DNS текущего подключения.")
        }
    }

    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = colors.surface)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Инструмент", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ToolChip("PING", Icons.Default.Speed, selectedTool == DiagnosticTool.PING, !running, Modifier.weight(1f)) { onToolChange(DiagnosticTool.PING) }
                ToolChip("ТСАП", Icons.Default.AltRoute, selectedTool == DiagnosticTool.TRACE, !running, Modifier.weight(1f)) { onToolChange(DiagnosticTool.TRACE) }
                ToolChip("ЭХО", Icons.Default.Radar, selectedTool == DiagnosticTool.ECHO, !running, Modifier.weight(1f)) { onToolChange(DiagnosticTool.ECHO) }
                ToolChip("TCP", Icons.Default.Search, selectedTool == DiagnosticTool.TCP, !running, Modifier.weight(1f)) { onToolChange(DiagnosticTool.TCP) }
            }
            when (selectedTool) {
                DiagnosticTool.PING, DiagnosticTool.TRACE -> {
                    OutlinedTextField(
                        value = state.target, onValueChange = onTargetChange, enabled = !running,
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        label = { Text("Целевой узел (IP или домен)") }, placeholder = { Text("8.8.8.8 или example.com") },
                        leadingIcon = { Icon(Icons.Default.Language, null) },
                        trailingIcon = { if (state.target.isNotEmpty()) IconButton(onClick = { onTargetChange("") }, enabled = !running) { Icon(Icons.Default.Close, "Очистить") } },
                        shape = RoundedCornerShape(14.dp)
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(presets) { preset -> SuggestionChip(onClick = { if (!running) onTargetChange(preset.substringAfter(" · ", preset)) }, label = { Text(preset, maxLines = 1) }, enabled = !running) }
                    }
                }
                DiagnosticTool.ECHO -> {
                    Text("Поиск устройств в текущей подсети через ICMP Echo, TCP и таблицу соседей Android.", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    HelpHint("Устройства, блокирующие ответы или скрытые Android, могут не обнаружиться.")
                }
                DiagnosticTool.TCP -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(portTarget, onPortTarget, Modifier.weight(1.2f), enabled = !running, singleLine = true, label = { Text("IP устройства") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii), shape = RoundedCornerShape(12.dp))
                        OutlinedTextField(portRange, onPortRange, Modifier.weight(.8f), enabled = !running, singleLine = true, label = { Text("Порты") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii), shape = RoundedCornerShape(12.dp))
                    }
                    Text("Диапазон до 4096 портов; проверяются адреса текущей локальной подсети.", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
            }

            Button(
                onClick = {
                    when (selectedTool) {
                        DiagnosticTool.PING -> onStartPing()
                        DiagnosticTool.TRACE -> onStartTraceroute()
                        DiagnosticTool.ECHO -> onStartEchoScan(connection.ipAddress, connection.subnetMask, connection.gateway)
                        DiagnosticTool.TCP -> onStartPortScan(portTarget.trim(), portRange.trim())
                    }
                },
                enabled = !running && when (selectedTool) {
                    DiagnosticTool.PING, DiagnosticTool.TRACE -> state.target.isNotBlank()
                    DiagnosticTool.ECHO -> connection.ipAddress.isNotBlank() && connection.subnetMask.isNotBlank()
                    DiagnosticTool.TCP -> portTarget.isNotBlank() && portRange.isNotBlank()
                }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)
            ) {
                Icon(if (running) Icons.Default.HourglassTop else Icons.Default.PlayArrow, null)
                Spacer(Modifier.width(8.dp))
                Text(if (running) "Выполняется…" else when (selectedTool) {
                    DiagnosticTool.PING -> "Запустить Ping"
                    DiagnosticTool.TRACE -> "Запустить ТСАП"
                    DiagnosticTool.ECHO -> "Найти устройства"
                    DiagnosticTool.TCP -> "Сканировать TCP-порты"
                })
            }
            if (running) TextButton(onClick = onStop, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Stop, null); Spacer(Modifier.width(6.dp)); Text("Остановить диагностику") }
            if (state.status == DiagnosticStatus.ERROR && state.errorMessage != null) Text(state.errorMessage, color = colors.error, style = MaterialTheme.typography.bodySmall)
        }
    }

    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = colors.surfaceVariant.copy(alpha = .45f))) {
        Column(Modifier.fillMaxWidth()) {
            TextButton(onClick = toggleAdvanced, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)) {
                Icon(Icons.Default.Tune, null); Spacer(Modifier.width(8.dp)); Text("Дополнительные параметры", Modifier.weight(1f)); Icon(if (advancedExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
            }
            if (advancedExpanded) Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Количество пакетов", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(4 to "4", 10 to "10", 0 to "∞").forEach { (count, label) -> FilterChip(state.packetCount == count, { onCountChange(count) }, enabled = !running, label = { Text(label) }) }
                }
                Text("Размер ICMP payload · 8–1400 байт", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(32, 64, 128, 512, 1300).forEach { size -> FilterChip(state.packetSizeBytes == size, { onPacketSizeChange(size); onCustomPacketSize("") }, enabled = !running, label = { Text("$size B") }) }
                }
                OutlinedTextField(customPacketSize, { value ->
                    onCustomPacketSize(value.filter(Char::isDigit).take(4))
                    value.filter(Char::isDigit).take(4).toIntOrNull()?.takeIf { it in 8..1400 }?.let(onPacketSizeChange)
                }, Modifier.fillMaxWidth(), enabled = !running, singleLine = true, label = { Text("Свой размер, байт") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), shape = RoundedCornerShape(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Таймаут ответа", style = MaterialTheme.typography.labelLarge)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(listOf(1, 2, 3, 5)) { timeout -> FilterChip(state.timeoutSeconds == timeout, { onTimeoutChange(timeout) }, enabled = !running, label = { Text("${timeout}с") }) }
                    }
                }
            }
        }
    }

    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = colors.surfaceVariant.copy(alpha = .32f))) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
            TextButton(onClick = toggleGuide, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 6.dp)) {
                Icon(Icons.Default.Build, null); Spacer(Modifier.width(8.dp)); Text("Памятка монтажника", Modifier.weight(1f)); Icon(if (guideExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
            }
            if (guideExpanded) listOf(
                "Wi-Fi подключён, интернета нет" to "Проверьте шлюз, затем 1.1.1.1 и DNS-имя yandex.ru. Если шлюз отвечает, а внешние узлы нет — вероятна проблема WAN/провайдера.",
                "Скорость низкая только в дальней комнате" to "Сравните RSSI и задержку рядом с роутером и в проблемной комнате. Ниже −70 dBm проверьте размещение, препятствия и диапазон 2,4/5 ГГц.",
                "Высокий ping или потери" to "Сначала проверьте шлюз. Потери уже до шлюза указывают на локальный сегмент; только за шлюзом — на внешний маршрут или провайдера.",
                "IPTV зависает / видеонаблюдение отваливается" to "Проверьте стабильность RSSI, потери и задержку в течение минуты; для стационарных устройств предпочтительно проводное подключение, если оно возможно."
            ).forEach { (title, body) -> Column(Modifier.padding(bottom = 8.dp)) { Text(title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall); Text(body, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant) } }
        }
    }

    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = colors.surfaceVariant.copy(alpha = .32f))) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Text("Проверка скорости", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge); HelpHint("Ссылки открываются в браузере.") }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                item { AssistChip(onClick = { onSpeedOpen("https://2ip.ru/speed/") }, label = { Text("2IP") }, leadingIcon = { Icon(Icons.Default.Speed, null, Modifier.size(16.dp)) }) }
                item { AssistChip(onClick = { onSpeedOpen("https://yandex.ru/internet/") }, label = { Text("Интернетометр") }, leadingIcon = { Icon(Icons.Default.NetworkCheck, null, Modifier.size(16.dp)) }) }
                item { AssistChip(onClick = { onSpeedOpen("https://speedtest.su/") }, label = { Text("Speedtest.su") }, leadingIcon = { Icon(Icons.Default.Bolt, null, Modifier.size(16.dp)) }) }
            }
            OutlinedButton(onClick = onRouter, enabled = connection.gateway.isNotBlank() && connection.gateway != "0.0.0.0", contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) {
                Icon(Icons.Default.Router, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Настройки роутера · ${connection.gateway}", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Button(onClick = onStartGuidedDiagnostics, enabled = !running, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Icon(Icons.Default.FactCheck, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Проверить шлюз, DNS и интернет")
            }
            HelpHint("Последовательно проверить связь с маршрутизатором, DNS-серверами и интернетом.")
        }
    }
}

@Composable
private fun ToolChip(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    FilterChip(selected, onClick, modifier = modifier, enabled = enabled, label = { Text(label, maxLines = 1) }, leadingIcon = { Icon(icon, null, Modifier.size(16.dp)) })
}

@Composable
private fun DiagnosticConsole(
    state: DiagnosticUiState,
    running: Boolean,
    selectedTab: Int,
    onTabChange: (Int) -> Unit,
    onClear: () -> Unit,
    connection: WifiConnectionDetails,
    modifier: Modifier = Modifier,
    consoleHeight: androidx.compose.ui.unit.Dp,
    showActions: Boolean,
    onInterrupt: () -> Unit,
    onContinue: () -> Unit,
    canContinue: Boolean
) {
    val listState = rememberLazyListState()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    LaunchedEffect(state.logs.size, selectedTab) {
        if (selectedTab == 0 && state.logs.isNotEmpty()) listState.animateScrollToItem(state.logs.lastIndex)
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Консоль диагностики", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(if (running) "Выполняется · ${state.target}" else "${state.logs.size} записей · ${state.hops.size} хопов", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (running) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            if (state.logs.isNotEmpty()) IconButton(onClick = onClear) { Icon(Icons.Default.DeleteOutline, "Очистить логи") }
            HelpHint("Вывод операций и структурированные данные трассировки. Новые строки прокручиваются автоматически.")
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onInterrupt, enabled = running, modifier = Modifier.weight(1f), colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                Icon(Icons.Default.Stop, null)
                Spacer(Modifier.width(6.dp))
                Text("Прервать")
            }
            Button(onClick = onContinue, enabled = !running && canContinue, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.PlayArrow, null)
                Spacer(Modifier.width(6.dp))
                Text("Продолжить")
            }
        }
        if (canContinue) Text(
            "После прерывания проверка запускается заново.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        TabRow(selectedTabIndex = selectedTab) {
            Tab(selectedTab == 0, { onTabChange(0) }, text = { Text("Вывод (${state.logs.size})") })
            Tab(selectedTab == 1, { onTabChange(1) }, text = { Text("Узлы / Хопы (${state.hops.size})") })
        }
        Surface(
            Modifier.fillMaxWidth().height(consoleHeight),
            shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .38f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            SelectionContainer {
                if (selectedTab == 0) {
                    if (state.logs.isEmpty()) Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                        Text("Готово к запуску. Выберите режим и откройте консоль после начала проверки.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    } else LazyColumn(Modifier.fillMaxSize().padding(10.dp), state = listState, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        items(state.logs) { entry -> DiagnosticLogRow(entry) }
                    }
                } else {
                    if (state.hops.isEmpty()) Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                        Text(if (running) "ТСАП собирает данные маршрута…" else "Запустите ТСАП, чтобы увидеть узлы маршрута.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    } else LazyColumn(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(state.hops) { hop -> HopResultCard(hop) }
                    }
                }
            }
        }
        if (showActions && state.logs.isNotEmpty()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { clipboard.setText(AnnotatedString(state.logs.joinToString("\n") { diagnosticEntryText(it) })) }) { Icon(Icons.Default.ContentCopy, null); Spacer(Modifier.width(4.dp)); Text("Копировать") }
                TextButton(onClick = {
                    val body = diagnosticReport(state, connection)
                    val pdf = exportReportPdf(context, wifiSonarExportFileName("pdf"), "Диагностика сети", body)
                    Toast.makeText(context, if (pdf != null) "PDF сохранён в Загрузки/WiFi Sonar" else "Не удалось сохранить PDF", Toast.LENGTH_LONG).show()
                }) { Icon(Icons.Default.PictureAsPdf, null); Spacer(Modifier.width(4.dp)); Text("PDF") }
                TextButton(onClick = {
                    val report = diagnosticReport(state, connection)
                    clipboard.setText(AnnotatedString(report))
                    context.getSharedPreferences("wifi_pulse_diagnostics", android.content.Context.MODE_PRIVATE).edit().putString("last_report", report).putLong("last_report_saved_at", System.currentTimeMillis()).apply()
                    Toast.makeText(context, "Отчёт сохранён и скопирован", Toast.LENGTH_SHORT).show()
                }) { Icon(Icons.Default.Save, null); Spacer(Modifier.width(4.dp)); Text("Сохранить") }
            }
        }
    }
}

@Composable
private fun DiagnosticLogRow(entry: DiagnosticLogEntry) {
    val accent = when (entry) {
        is DiagnosticLogEntry.Error -> MaterialTheme.colorScheme.error
        is DiagnosticLogEntry.PingEntry -> Color(0xFF16825D)
        is DiagnosticLogEntry.HopEntry -> if (entry.hop.isTargetReached) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        is DiagnosticLogEntry.Summary -> MaterialTheme.colorScheme.primary
        is DiagnosticLogEntry.Output -> if (entry.isHighlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    }
    Text(diagnosticEntryText(entry), color = accent, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
}

private fun diagnosticEntryText(entry: DiagnosticLogEntry): String = when (entry) {
    is DiagnosticLogEntry.Output -> entry.text
    is DiagnosticLogEntry.PingEntry -> "${entry.packet.bytes} bytes from ${entry.packet.ip}: icmp_seq=${entry.packet.sequence} ttl=${entry.packet.ttl} time=${entry.packet.rttMs} ms"
    is DiagnosticLogEntry.HopEntry -> "Hop ${entry.hop.hopNumber}: ${entry.hop.ip ?: "*"} ${entry.hop.rttMs ?: "*"} ms${if (entry.hop.isTargetReached) " · Цель достигнута" else ""}"
    is DiagnosticLogEntry.Summary -> "Statistics: sent ${entry.summary.packetsTransmitted}, received ${entry.summary.packetsReceived}, loss ${entry.summary.packetLossPercent}% · RTT ${entry.summary.minRttMs}/${entry.summary.avgRttMs}/${entry.summary.maxRttMs} ms"
    is DiagnosticLogEntry.Error -> "ERROR: ${entry.message}"
}

@Composable
private fun HopResultCard(hop: TracerouteHop) {
    val lossColor = when { hop.lossPercent >= 25f -> MaterialTheme.colorScheme.error; hop.lossPercent > 0f -> Color(0xFFB56B00); else -> Color(0xFF16825D) }
    Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.primaryContainer) { Text("${hop.hopNumber}", Modifier.padding(horizontal = 10.dp, vertical = 6.dp), fontWeight = FontWeight.Bold) }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(hop.ip ?: "Нет ответа от узла", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(hop.hostName ?: if (hop.isTargetReached) "Конечный узел" else "Маршрутный узел", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (hop.isTargetReached) AssistChip(onClick = {}, label = { Text("Цель") }, leadingIcon = { Icon(Icons.Default.Flag, null, Modifier.size(16.dp)) })
            }
            if (hop.probesSent > 0) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Metric("Потери", "${"%.0f".format(hop.lossPercent)}%", lossColor, Modifier.weight(1f))
                Metric("Ответы", "${hop.probesReceived}/${hop.probesSent}", MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                Metric("Средний RTT", hop.averageRttMs?.let { "${"%.1f".format(it)} ms" } ?: "—", MaterialTheme.colorScheme.secondary, Modifier.weight(1f))
            } else Text("Ожидание первой пробы", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("МИН ${hop.minRttMs?.let { "%.1f".format(it) } ?: "—"} ms · МАКС ${hop.maxRttMs?.let { "%.1f".format(it) } ?: "—"} ms", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Metric(label: String, value: String, color: Color, modifier: Modifier) {
    Surface(modifier, shape = RoundedCornerShape(8.dp), color = color.copy(alpha = .1f)) {
        Column(Modifier.padding(7.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontWeight = FontWeight.Bold, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun diagnosticReport(state: DiagnosticUiState, c: WifiConnectionDetails): String = buildString {
    appendLine("WiFi Sonar — отчёт")
    appendLine("SSID: ${c.ssid.ifBlank { "не подключён" }}")
    appendLine("BSSID: ${c.bssid}")
    appendLine("RSSI: ${c.rssi} dBm · Частота: ${c.frequency} MHz · Линк: ${c.linkSpeedMbps} Mbps")
    appendLine("IP: ${c.ipAddress} · Шлюз: ${c.gateway} · Маска: ${c.subnetMask}")
    appendLine("DNS: ${c.dns1}, ${c.dns2}")
    appendLine("Цель: ${state.target} · Статус: ${state.status}")
    state.logs.forEach { appendLine(diagnosticEntryText(it)) }
}

private fun openRouter(context: android.content.Context, gateway: String) {
    if (gateway.isNotBlank() && gateway != "0.0.0.0") runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("http://$gateway")))
    }
}
