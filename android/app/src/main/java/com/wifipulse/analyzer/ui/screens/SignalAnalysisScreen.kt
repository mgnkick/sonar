package com.wifipulse.analyzer.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.graphics.Paint as AndroidPaint
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wifipulse.analyzer.data.model.ChannelRating
import com.wifipulse.analyzer.data.model.WifiBand
import com.wifipulse.analyzer.data.model.WifiNetwork
import com.wifipulse.analyzer.data.model.WifiConnectionDetails
import com.wifipulse.analyzer.ui.viewmodel.WifiUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignalAnalysisScreen(
    state: WifiUiState,
    onSelectNetwork: (String) -> Unit,
    visibleNetworkCount: Int,
    isRefreshingNetworks: Boolean,
    onRefreshNetworks: () -> Unit,
    lastScanTimestamp: Long = 0L,
    isBackgroundMonitoring: Boolean = false,
    onToggleBackgroundMonitoring: () -> Unit = {},
    onOpenConnectionHistory: () -> Unit = {},
    onSelectSignalTrackingBssid: (String?) -> Unit = {}
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("График сигнала", "Спектр каналов", "Рейтинг каналов")

    val connectedBssid = state.connectionDetails.bssid.takeIf {
        state.connectionDetails.isConnected && it.isNotBlank() && it != "BSSID недоступен"
    }
    val trackedBssid = state.trackedSignalBssid ?: connectedBssid
    val trackedNetwork = state.networks.firstOrNull { it.bssid.equals(trackedBssid, ignoreCase = true) }
    val selectedNetwork = trackedNetwork
    val connectedSsid = state.connectionDetails.ssid.takeIf {
        it.isNotBlank() && !it.startsWith("Подключено к Wi-Fi")
    }
    val meshCandidates = state.networks
        .filter { connectedSsid == null || it.ssid == connectedSsid }
        .distinctBy { it.bssid.lowercase() }
        .sortedByDescending { it.rssi }
    var trackingMenuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Анализ сигнала и спектра", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onOpenConnectionHistory) {
                        Icon(Icons.Default.History, contentDescription = "История подключений")
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
                .padding(bottom = 16.dp)
        ) {
            ConnectionStatusPanel(
                details = state.connectionDetails,
                visibleNetworkCount = visibleNetworkCount,
                isRefreshing = isRefreshingNetworks,
                onRefreshNetworks = onRefreshNetworks,
                lastScanTimestamp = lastScanTimestamp,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
            BackgroundMonitoringCard(
                isActive = isBackgroundMonitoring,
                onToggle = onToggleBackgroundMonitoring
            )
            // Signal target selector: auto-follow the connected BSSID by default,
            // or pin another AP of the same SSID for a placement survey.
            if (trackedBssid != null || meshCandidates.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth().clickable { trackingMenuExpanded = true },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Отслеживаемый сателлит  ▾",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = trackedNetwork?.ssid ?: state.connectionDetails.ssid.ifBlank { "Точка доступа не обнаружена" },
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = buildString {
                                        append(trackedBssid ?: "BSSID недоступен")
                                        trackedNetwork?.let {
                                            append(" • CH ${it.channel} • ${it.band.displayName}")
                                        } ?: run {
                                            if (state.connectionDetails.isConnected &&
                                                trackedBssid.equals(state.connectionDetails.bssid, ignoreCase = true)
                                            ) append(" • ${state.connectionDetails.frequency} МГц")
                                        }
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                                Text(
                                    text = if (state.trackedSignalBssid == null)
                                        "АВТО: следит за текущим подключением"
                                    else "РУЧНОЙ ВЫБОР: закреплённый BSSID",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            val trackedRssi = if (trackedBssid.equals(state.connectionDetails.bssid, ignoreCase = true) &&
                                state.connectionDetails.isConnected
                            ) state.connectionDetails.rssi else trackedNetwork?.rssi
                            if (trackedRssi != null && trackedRssi in -100..0) {
                                val qualityColor = trackedNetwork?.let { Color(it.signalQuality.hexColor) }
                                    ?: MaterialTheme.colorScheme.primary
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = qualityColor.copy(alpha = 0.2f)
                                ) {
                                    Text(
                                        text = "$trackedRssi дБм",
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                        color = qualityColor,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Icon(Icons.Default.ExpandMore, contentDescription = "Выбрать сателлит")
                        }
                    }
                    DropdownMenu(
                        expanded = trackingMenuExpanded,
                        onDismissRequest = { trackingMenuExpanded = false },
                        modifier = Modifier.heightIn(max = 360.dp)
                    ) {
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text("Автоматически: подключённый сателлит", fontWeight = FontWeight.Bold)
                                    Text("График переключается вместе с роумингом", style = MaterialTheme.typography.labelSmall)
                                }
                            },
                            leadingIcon = { Icon(Icons.Default.Wifi, contentDescription = null) },
                            onClick = {
                                trackingMenuExpanded = false
                                onSelectSignalTrackingBssid(null)
                            }
                        )
                        Divider()
                        meshCandidates.forEach { network ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(network.ssid, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            "${network.bssid} • CH ${network.channel} • ${network.rssi} дБм",
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                },
                                leadingIcon = {
                                    if (network.bssid.equals(trackedBssid, ignoreCase = true)) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = "Отслеживается")
                                    } else {
                                        Icon(Icons.Default.Wifi, contentDescription = null)
                                    }
                                },
                                onClick = {
                                    trackingMenuExpanded = false
                                    onSelectSignalTrackingBssid(network.bssid)
                                }
                            )
                        }
                    }
                }
                if (!state.signalTrackingNotice.isNullOrBlank()) {
                    Text(
                        text = state.signalTrackingNotice,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Tab Selector
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title, fontSize = 13.sp) }
                    )
                }
            }

            when (selectedTab) {
                0 -> SignalGraphTab(state = state, selectedNetwork = selectedNetwork)
                1 -> ChannelSpectrumTab(networks = state.networks, connectionDetails = state.connectionDetails)
                2 -> ChannelRatingTab(ratings24 = state.channelRatings24, ratings5 = state.channelRatings5)
            }
        }
    }
}

@Composable
private fun BackgroundMonitoringCard(
    isActive: Boolean,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Notifications,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isActive) "Фоновый мониторинг включён" else "Фоновый мониторинг Wi-Fi",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Имя сети, RSSI и текущий трафик в системном уведомлении",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Button(
                onClick = onToggle,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = if (isActive) Icons.Default.StopCircle else Icons.Default.PlayCircle,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(if (isActive) "Остановить мониторинг" else "Запустить в фоне")
            }
            Text(
                text = "Скорость берётся со счётчиков Wi-Fi-интерфейса, если они доступны; иначе показывается общий трафик устройства. Это не отдельный тест LAN.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SignalGraphTab(state: WifiUiState, selectedNetwork: WifiNetwork?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(
            text = "Динамика уровня сигнала во времени (дБм)",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        val activeBssid = state.signalTrackingBssid
            ?: state.connectionDetails.bssid.takeIf { state.connectionDetails.isConnected }
        if (!activeBssid.isNullOrBlank()) {
            val isConnectedTarget = state.connectionDetails.isConnected &&
                activeBssid.equals(state.connectionDetails.bssid, ignoreCase = true)
            Text(
                text = if (isConnectedTarget) {
                    "Сейчас подключён этот BSSID • ${activeBssid}"
                } else {
                    "График выбранного сателлита • ${activeBssid} • телефон подключён к другому BSSID"
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (isConnectedTarget) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                fontWeight = FontWeight.SemiBold
            )
        }
        if (!state.signalTrackingNotice.isNullOrBlank()) {
            Text(
                text = state.signalTrackingNotice,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = "Показывает стабильность приема и затухание при перемещении",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Signal Timeline Canvas
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.42f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                SignalCanvas(history = state.signalHistory)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Signal Metrics Summary
        if (selectedNetwork != null) {
            Text(
                text = "Оценка стабильности соединения",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricTile(
                    title = "Текущий уровень",
                    value = "${selectedNetwork.rssi} дБм",
                    modifier = Modifier.weight(1f)
                )
                MetricTile(
                    title = "Дистанция",
                    value = "~${selectedNetwork.estimatedDistanceMeters} м",
                    modifier = Modifier.weight(1f)
                )
                MetricTile(
                    title = "Качество",
                    value = selectedNetwork.signalQuality.label,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun SignalCanvas(history: List<com.wifipulse.analyzer.data.model.SignalHistoryPoint>) {
    val lineColor = MaterialTheme.colorScheme.primary
    val axisColor = MaterialTheme.colorScheme.tertiary
    val labelColor = MaterialTheme.colorScheme.onSurface

    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        if (width < 24.dp.toPx() || height < 24.dp.toPx()) return@Canvas
        val nativeCanvas = drawContext.canvas.nativeCanvas
        val axisPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(
                (labelColor.red * 255).toInt(),
                (labelColor.green * 255).toInt(),
                (labelColor.blue * 255).toInt()
            )
            textSize = 10.dp.toPx()
            textAlign = AndroidPaint.Align.LEFT
        }

        // Cyber telemetry grid with readable RSSI labels.
        val dbmLevels = listOf(-30, -50, -70, -90)
        dbmLevels.forEach { dbm ->
            val y = ((-dbm - 30) / 70f) * (height - 8.dp.toPx()) + 4.dp.toPx()
            drawLine(
                color = axisColor.copy(alpha = 0.20f),
                start = Offset(34.dp.toPx(), y),
                end = Offset(width, y),
                strokeWidth = 1.dp.toPx()
            )
            nativeCanvas.drawText("$dbm", 1.dp.toPx(), (y + 3.dp.toPx()).coerceIn(10.dp.toPx(), height - 2.dp.toPx()), axisPaint)
        }

        if (history.size < 2) {
            // Draw placeholder wave if samples are accumulating
            return@Canvas
        }

        val plotLeft = 34.dp.toPx()
        val plotWidth = (width - plotLeft).coerceAtLeast(1f)
        val plotHeight = (height - 8.dp.toPx()).coerceAtLeast(1f)
        val stepX = plotWidth / (history.size - 1).coerceAtLeast(1)
        val path = Path()

        history.forEachIndexed { i, pt ->
            val normalized = ((-pt.rssi - 30) / 70f).coerceIn(0f, 1f)
            val x = plotLeft + i * stepX
            val y = 4.dp.toPx() + normalized * plotHeight
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        // A soft outer stroke creates a restrained neon glow without obscuring data.
        drawPath(path = path, color = lineColor.copy(alpha = 0.18f), style = Stroke(width = 9.dp.toPx()))
        drawPath(path = path, color = lineColor.copy(alpha = 0.55f), style = Stroke(width = 5.dp.toPx()))
        drawPath(path = path, color = lineColor, style = Stroke(width = 2.5.dp.toPx()))

        val last = history.last()
        val lastY = 4.dp.toPx() + ((-last.rssi - 30) / 70f).coerceIn(0f, 1f) * plotHeight
        drawCircle(color = lineColor.copy(alpha = 0.22f), radius = 8.dp.toPx(), center = Offset(width - 2.dp.toPx(), lastY))
        drawCircle(color = lineColor, radius = 3.dp.toPx(), center = Offset(width - 2.dp.toPx(), lastY))
    }
}

@Composable
fun ChannelSpectrumTab(
    networks: List<WifiNetwork>,
    connectionDetails: WifiConnectionDetails = WifiConnectionDetails()
) {
    var selectedBand by remember { mutableStateOf(WifiBand.BAND_2_4_GHZ) }
    var showFullscreen by remember { mutableStateOf(false) }
    var zoomScale by remember { mutableFloatStateOf(1f) }
    var panFraction by remember { mutableFloatStateOf(0f) }
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val connectedBand = when {
        connectionDetails.frequency in 2400..2500 -> WifiBand.BAND_2_4_GHZ
        connectionDetails.frequency in 4900..5900 -> WifiBand.BAND_5_GHZ
        connectionDetails.frequency in 5925..7125 -> WifiBand.BAND_6_GHZ
        else -> WifiBand.UNKNOWN
    }
    val inferredSsid = remember(networks, connectionDetails.frequency, connectionDetails.ssid) {
        val names = networks.filter { it.band == connectedBand && it.ssid.isNotBlank() && it.ssid != "[Скрытая сеть]" }
            .groupingBy { it.ssid }.eachCount().entries.sortedByDescending { it.value }
        val first = names.firstOrNull()
        if (first != null && first.value >= 2 && (names.size == 1 || first.value > (names.getOrNull(1)?.value ?: 0))) first.key else null
    }
    val ssidKnown = connectionDetails.ssid.isNotBlank() && !connectionDetails.ssid.startsWith("Подключено к Wi-Fi") && !connectionDetails.ssid.equals("<unknown ssid>", ignoreCase = true)
    val connectedBssidKnown = connectionDetails.bssid.isNotBlank() && connectionDetails.bssid != "BSSID недоступен" && connectionDetails.bssid != "02:00:00:00:00:00"
    val spectrumNetworks = remember(networks, connectionDetails, inferredSsid) {
        val result = networks.toMutableList()
        if (connectionDetails.isConnected && connectedBssidKnown && connectionDetails.frequency > 0 && result.none { it.bssid.equals(connectionDetails.bssid, ignoreCase = true) }) {
            val band = when {
                connectionDetails.frequency in 2400..2500 -> WifiBand.BAND_2_4_GHZ
                connectionDetails.frequency in 4900..5900 -> WifiBand.BAND_5_GHZ
                connectionDetails.frequency in 5925..7125 -> WifiBand.BAND_6_GHZ
                else -> WifiBand.UNKNOWN
            }
            val label = when {
                ssidKnown -> connectionDetails.ssid
                inferredSsid != null -> inferredSsid + " · имя предположительно"
                else -> "[Подключено · SSID скрыт]"
            }
            result += WifiNetwork(ssid = label, bssid = connectionDetails.bssid, rssi = connectionDetails.rssi.coerceIn(-100, 0), frequency = connectionDetails.frequency, channel = com.wifipulse.analyzer.data.repository.WifiRepository.frequencyToChannel(connectionDetails.frequency), band = band, channelWidth = 20, securityType = com.wifipulse.analyzer.data.model.SecurityType.OPEN, capabilities = "Параметры текущего подключения Android; SSID может быть предположительным")
        }
        result
    }

    // The parent SignalAnalysisScreen already owns vertical scrolling.
    // A second verticalScroll with fillMaxSize creates unbounded nested constraints
    // on some Compose versions and can crash when this tab is first opened.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = selectedBand == WifiBand.BAND_2_4_GHZ,
                onClick = { selectedBand = WifiBand.BAND_2_4_GHZ },
                label = { Text("Диапазон 2.4 ГГц") }
            )
            FilterChip(
                selected = selectedBand == WifiBand.BAND_5_GHZ,
                onClick = { selectedBand = WifiBand.BAND_5_GHZ },
                label = { Text("Диапазон 5 ГГц") }
            )
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            OutlinedButton(onClick = { showFullscreen = true }) {
                Icon(Icons.Default.Fullscreen, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("На весь экран")
            }
        }

        if (showFullscreen && activity != null) {
            val previousOrientation = remember { activity.requestedOrientation }
            DisposableEffect(activity) {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                onDispose { activity.requestedOrientation = previousOrientation }
            }
            Dialog(onDismissRequest = { showFullscreen = false }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.fillMaxSize().padding(WindowInsets.safeDrawing.asPaddingValues()).padding(horizontal = 8.dp, vertical = 4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Спектр Wi-Fi", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1)
                            Text((zoomScale * 100).toInt().toString() + "%", style = MaterialTheme.typography.labelMedium)
                            TextButton(onClick = { zoomScale = 1f; panFraction = 0f }) { Text("Сбросить") }
                            IconButton(onClick = { showFullscreen = false }) { Icon(Icons.Default.FullscreenExit, contentDescription = "Свернуть спектр") }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = selectedBand == WifiBand.BAND_2_4_GHZ, onClick = { selectedBand = WifiBand.BAND_2_4_GHZ; zoomScale = 1f; panFraction = 0f }, label = { Text("2,4 ГГц") })
                            FilterChip(selected = selectedBand == WifiBand.BAND_5_GHZ, onClick = { selectedBand = WifiBand.BAND_5_GHZ; zoomScale = 1f; panFraction = 0f }, label = { Text("5 ГГц") })
                        }
                        Text("Щипок — масштаб · перетаскивание — перемещение", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(2.dp))
                        SpectrumCanvas(networks = spectrumNetworks.filter { it.band == selectedBand }, band = selectedBand, zoomScale = zoomScale, panFraction = panFraction, onTransform = { scaleChange, panChange, canvasWidth ->
                            val newScale = (zoomScale * scaleChange).coerceIn(1f, 8f)
                            zoomScale = newScale
                            if (canvasWidth > 0f) panFraction = (panFraction - panChange / canvasWidth / newScale).coerceIn(-0.5f + 0.5f / newScale, 0.5f - 0.5f / newScale)
                        }, modifier = Modifier.fillMaxWidth().weight(1f))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Спектральный анализ интерференции",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Параболические кривые отражают перекрытие каналов и ширину полосы",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Parabolic Spectrum Canvas
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Box(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                SpectrumCanvas(networks = networks.filter { it.band == selectedBand }, band = selectedBand, modifier = Modifier.fillMaxSize())
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        val visibleNetworks = spectrumNetworks.filter { it.band == selectedBand }.sortedByDescending { it.rssi }
        Text("Сети и каналы (${visibleNetworks.size})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(6.dp))
        if (visibleNetworks.isEmpty()) {
            Text("Для выбранного диапазона сети не обнаружены. Нажмите «Сканировать» на вкладке сканера.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            visibleNetworks.forEach { network ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(network.ssid.ifBlank { "[Скрытая сеть]" }, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text("${network.bssid} • ${network.frequency} МГц", maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Surface(shape = RoundedCornerShape(8.dp), color = Color(network.signalQuality.hexColor).copy(alpha = 0.12f)) {
                        Text("CH ${network.channel} • ${network.rssi} дБм", modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp), color = Color(network.signalQuality.hexColor), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Explanation card
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "💡 Совет по радиоэфиру:",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = if (selectedBand == WifiBand.BAND_2_4_GHZ) {
                        "В диапазоне 2.4 ГГц непересекающимися являются каналы 1, 6 и 11. Использование промежуточных каналов (например, 2, 3, 4, 8) приводит к перекрестным помехам сразу с несколькими соседними сетями."
                    } else {
                        "В диапазоне 5 ГГц больше свободных каналов с полосой 40/80/160 МГц. Рекомендуется использовать каналы 36–48 или 149–161 для максимальной скорости без коллизий."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
fun SpectrumCanvas(
    networks: List<WifiNetwork>,
    band: WifiBand,
    modifier: Modifier = Modifier.fillMaxSize(),
    zoomScale: Float = 1f,
    panFraction: Float = 0f,
    onTransform: ((scaleChange: Float, panChangePx: Float, canvasWidthPx: Float) -> Unit)? = null
) {
    val labelColor = MaterialTheme.colorScheme.onSurface
    val gridAccent = MaterialTheme.colorScheme.tertiary
    val gestureModifier = if (onTransform != null) modifier.pointerInput(onTransform) {
        detectTransformGestures { _, pan, zoom, _ -> onTransform(zoom, pan.x, size.width.toFloat()) }
    } else modifier

    Canvas(modifier = gestureModifier) {
        val width = size.width
        val height = size.height
        if (width < 100.dp.toPx() || height < 40.dp.toPx()) return@Canvas
        val nativeCanvas = drawContext.canvas.nativeCanvas
        val labelPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb((labelColor.red * 255).toInt(), (labelColor.green * 255).toInt(), (labelColor.blue * 255).toInt())
            textSize = 10.dp.toPx()
            textAlign = AndroidPaint.Align.CENTER
        }
        val channelPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.GRAY
            textSize = 10.dp.toPx()
            textAlign = AndroidPaint.Align.CENTER
        }
        val minChannel = if (band == WifiBand.BAND_2_4_GHZ) 1 else 36
        val maxChannel = if (band == WifiBand.BAND_2_4_GHZ) 14 else 165
        val channelSpan = (maxChannel - minChannel).coerceAtLeast(1).toFloat()
        val scale = zoomScale.coerceIn(1f, 8f)
        val visibleSpan = channelSpan / scale
        val center = (0.5f + panFraction).coerceIn(0.5f / scale, 1f - 0.5f / scale)
        val viewMin = minChannel + center * channelSpan - visibleSpan / 2f
        val viewMax = viewMin + visibleSpan
        fun channelX(channel: Float): Float = ((channel - viewMin) / visibleSpan * width).coerceIn(-width, width * 2f)

        val axisY = height - 22.dp.toPx()
        drawLine(Color.Gray.copy(alpha = 0.35f), Offset(0f, axisY), Offset(width, axisY), 2.dp.toPx())
        val desiredTickCount = (width / (if (scale > 3f) 38.dp.toPx() else 64.dp.toPx())).toInt().coerceAtLeast(2)
        val rawStep = visibleSpan / desiredTickCount
        val niceSteps = if (band == WifiBand.BAND_2_4_GHZ) listOf(1f, 2f, 3f, 4f, 5f) else listOf(1f, 2f, 4f, 5f, 10f, 20f)
        val tickStep = niceSteps.firstOrNull { it >= rawStep } ?: niceSteps.last()
        var tick = kotlin.math.ceil(viewMin / tickStep) * tickStep
        while (tick <= viewMax + 0.001f) {
            val x = channelX(tick)
            if (x in 0f..width) {
                drawLine(gridAccent.copy(alpha = 0.12f), Offset(x, 8.dp.toPx()), Offset(x, axisY), 1.dp.toPx())
                drawLine(gridAccent.copy(alpha = 0.6f), Offset(x, axisY - 8.dp.toPx()), Offset(x, axisY), 1.dp.toPx())
                nativeCanvas.drawText(tick.toInt().toString(), x.coerceIn(12.dp.toPx(), width - 12.dp.toPx()), height - 5.dp.toPx(), channelPaint)
            }
            tick += tickStep
        }

        val visible = networks.filter { it.channel.toFloat() in viewMin..viewMax }.sortedByDescending { it.rssi }.take(12)
        val labelLanes = FloatArray(4) { -1000f }
        visible.sortedBy { it.channel }.forEach { net ->
            val x = channelX(net.channel.toFloat())
            val peakY = height - 42.dp.toPx() - (((net.rssi + 100) / 70f).coerceIn(0.1f, 1f) * (height - 72.dp.toPx()))
            val ssid = net.ssid.ifBlank { "Скрытая" }
            val limit = if (width > 700.dp.toPx()) 16 else 10
            val shortName = if (ssid.length > limit) ssid.take(limit - 1) + "…" else ssid
            val label = "$" + "{shortName} · CH${'$'}{net.channel}"
            var lane = labelLanes.indexOfFirst { kotlin.math.abs(it - x) > 72.dp.toPx() }
            if (lane < 0) lane = visible.indexOf(net) % labelLanes.size
            labelLanes[lane] = x
            val labelY = (peakY - 8.dp.toPx() - lane * 14.dp.toPx()).coerceIn(14.dp.toPx(), axisY - 12.dp.toPx())
            if (x in 0f..width) nativeCanvas.drawText(label, x.coerceIn(45.dp.toPx(), width - 45.dp.toPx()), labelY, labelPaint)
        }

        visible.forEach { net ->
            val centerX = channelX(net.channel.toFloat())
            val curveWidth = (width / visibleSpan) * (if (band == WifiBand.BAND_2_4_GHZ) 4.5f else 3.5f) * (net.channelWidth / 20f).coerceIn(1f, 8f)
            val peakY = height - 25.dp.toPx() - (((net.rssi + 100) / 70f).coerceIn(0.1f, 1f) * (height - 50.dp.toPx()))
            val path = Path()
            path.moveTo(centerX - curveWidth / 2f, axisY)
            path.cubicTo(centerX - curveWidth / 4f, peakY, centerX + curveWidth / 4f, peakY, centerX + curveWidth / 2f, axisY)
            val curveColor = Color(net.signalQuality.hexColor)
            drawPath(path, curveColor.copy(alpha = 0.10f), style = Stroke(width = 10.dp.toPx()))
            drawPath(path, curveColor.copy(alpha = 0.16f))
            drawPath(path, curveColor, style = Stroke(width = 2.dp.toPx()))
        }
    }
}
@Composable
fun ChannelRatingTab(ratings24: List<ChannelRating>, ratings5: List<ChannelRating>) {
    var selectedBandTab by remember { mutableStateOf(WifiBand.BAND_2_4_GHZ) }
    val ratings = if (selectedBandTab == WifiBand.BAND_2_4_GHZ) ratings24 else ratings5

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = selectedBandTab == WifiBand.BAND_2_4_GHZ,
                onClick = { selectedBandTab = WifiBand.BAND_2_4_GHZ },
                label = { Text("Рейтинг 2.4 ГГц") }
            )
            FilterChip(
                selected = selectedBandTab == WifiBand.BAND_5_GHZ,
                onClick = { selectedBandTab = WifiBand.BAND_5_GHZ },
                label = { Text("Рейтинг 5 ГГц") }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        val bestChannels = ratings.filter { it.isRecommended }.map { it.channel }
        Surface(
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Star, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (bestChannels.isNotEmpty()) {
                        "Лучшие каналы для настройки роутера: ${bestChannels.joinToString(", ")}"
                    } else {
                        "Рекомендуются стандартные непересекающиеся каналы"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ratings.forEach { rating ->
                ChannelRatingCard(rating = rating)
            }
        }
    }
}

@Composable
fun ChannelRatingCard(rating: ChannelRating) {
    val expanded = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            (if (rating.isRecommended) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary).copy(alpha = 0.42f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded.value = !expanded.value }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (rating.isRecommended) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = "CH ${rating.channel}",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            fontWeight = FontWeight.Bold,
                            color = if (rating.isRecommended) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(text = "${rating.frequency} МГц", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        if (rating.band == WifiBand.BAND_5_GHZ && rating.channel in 52..144) {
                            Surface(
                                color = Color(0xFF7F1D1D),
                                shape = RoundedCornerShape(5.dp),
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                Text(
                                    text = "-=!!!=-  DFS / РАДАРНЫЙ ДИАПАЗОН",
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                    color = Color.White,
                                    fontWeight = FontWeight.Black,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                        Text(
                            text = when {
                                !rating.hasScanData -> "Недостаточно данных"
                                rating.networkCount == 0 -> "Соседние сети не обнаружены"
                                rating.networkCount == 1 -> "1 перекрывающаяся сеть"
                                else -> "Перекрывающихся сетей: ${rating.networkCount}"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = if (rating.hasScanData) "${rating.score}/10" else "—/10",
                        fontWeight = FontWeight.Bold,
                        color = if (!rating.hasScanData) MaterialTheme.colorScheme.onSurfaceVariant
                            else if (rating.score >= 8) Color(0xFF22C55E)
                            else if (rating.score >= 5) Color(0xFFF59E0B) else Color(0xFFEF4444)
                    )
                    Text(
                        text = if (rating.isRecommended) "Кандидат" else "Подробнее",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            if (expanded.value) {
                androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("Ширина оценки: ${rating.recommendedWidthMhz} МГц", style = MaterialTheme.typography.bodySmall)
                    Text(
                        text = if (rating.confidence == "NO_DATA") "Достоверность: данных нет"
                            else "Достоверность: ограниченная (один снимок сканирования)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    rating.strongestNeighborRssiDbm?.let {
                        Text("Самая сильная соседняя сеть: $it dBm", style = MaterialTheme.typography.bodySmall)
                    }
                    if (rating.band == WifiBand.BAND_5_GHZ && rating.channel in 52..144) {
                        Text(
                            text = "-=!!!=- DFS: этот канал может требовать обнаружения радаров. При обнаружении радара точка доступа должна освободить канал, из-за чего возможна автоматическая смена канала или пауза связи. Это регуляторное ограничение, а не измеренная помеха; доступность DFS зависит от страны и оборудования.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFEF4444),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Text(
                        text = rating.explanation + " Оценка не измеряет занятость радиоэфира.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (rating.overlappingNetworks.isNotEmpty()) {
                        Text(
                            text = "Сети: ${rating.overlappingNetworks.take(5).joinToString(", ")}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MetricTile(title: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    ) {
        Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        }
    }
}


private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
