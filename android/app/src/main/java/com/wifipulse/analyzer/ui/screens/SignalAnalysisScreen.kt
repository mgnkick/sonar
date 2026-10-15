package com.wifipulse.analyzer.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.graphics.Paint as AndroidPaint
import androidx.compose.foundation.clickable
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
    onOpenConnectionHistory: () -> Unit = {}
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("График сигнала", "Спектр каналов", "Рейтинг каналов")

    val selectedNetwork = state.networks.firstOrNull { it.bssid == state.selectedNetworkBssid }
        ?: state.networks.firstOrNull()

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
            // Selected Network Quick Selector Bar
            if (selectedNetwork != null) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Отслеживаемая сеть: ${selectedNetwork.ssid}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${selectedNetwork.bssid} • CH ${selectedNetwork.channel} • ${selectedNetwork.band.displayName}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(selectedNetwork.signalQuality.hexColor).copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = "${selectedNetwork.rssi} дБм",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                color = Color(selectedNetwork.signalQuality.hexColor),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
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
                1 -> ChannelSpectrumTab(networks = state.networks)
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
fun ChannelSpectrumTab(networks: List<WifiNetwork>) {
    var selectedBand by remember { mutableStateOf(WifiBand.BAND_2_4_GHZ) }
    var showFullscreen by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

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
                    Column(Modifier.fillMaxSize().padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Спектр Wi-Fi", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            IconButton(onClick = { showFullscreen = false }) { Icon(Icons.Default.FullscreenExit, contentDescription = "Свернуть спектр") }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = selectedBand == WifiBand.BAND_2_4_GHZ, onClick = { selectedBand = WifiBand.BAND_2_4_GHZ }, label = { Text("2,4 ГГц") })
                            FilterChip(selected = selectedBand == WifiBand.BAND_5_GHZ, onClick = { selectedBand = WifiBand.BAND_5_GHZ }, label = { Text("5 ГГц") })
                        }
                        Spacer(Modifier.height(6.dp))
                        SpectrumCanvas(networks = networks.filter { it.band == selectedBand }, band = selectedBand, modifier = Modifier.fillMaxSize())
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

        val visibleNetworks = networks.filter { it.band == selectedBand }.sortedByDescending { it.rssi }
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
fun SpectrumCanvas(networks: List<WifiNetwork>, band: WifiBand, modifier: Modifier = Modifier.fillMaxSize()) {
    val labelColor = MaterialTheme.colorScheme.onSurface
    val gridAccent = MaterialTheme.colorScheme.tertiary

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        // Canvas can receive a transient zero/tiny size during first layout or rotation.
        // Avoid coerceIn(min > max) and invalid draw coordinates in that frame.
        if (width < 100.dp.toPx() || height < 40.dp.toPx()) return@Canvas
        val nativeCanvas = drawContext.canvas.nativeCanvas
        val labelPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb((labelColor.red * 255).toInt(), (labelColor.green * 255).toInt(), (labelColor.blue * 255).toInt())
            textSize = 11.dp.toPx()
            textAlign = AndroidPaint.Align.CENTER
        }
        val channelPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.GRAY
            textSize = 10.dp.toPx()
            textAlign = AndroidPaint.Align.CENTER
        }

        // Axis
        drawLine(
            color = Color.Gray.copy(alpha = 0.3f),
            start = Offset(0f, height - 20.dp.toPx()),
            end = Offset(width, height - 20.dp.toPx()),
            strokeWidth = 2.dp.toPx()
        )

        val minChannel = if (band == WifiBand.BAND_2_4_GHZ) 1 else 36
        val maxChannel = if (band == WifiBand.BAND_2_4_GHZ) 14 else 165
        val channelSpan = (maxChannel - minChannel).coerceAtLeast(1)

        // Channel ticks along the horizontal axis
        val tickStep = if (band == WifiBand.BAND_2_4_GHZ) 3 else 20
        (minChannel..maxChannel step tickStep).forEach { ch ->
            val x = ((ch - minChannel).toFloat() / channelSpan) * width
            drawLine(color = gridAccent.copy(alpha = 0.10f), start = Offset(x, 8.dp.toPx()), end = Offset(x, height - 20.dp.toPx()), strokeWidth = 1.dp.toPx())
            drawLine(color = gridAccent.copy(alpha = 0.60f), start = Offset(x, height - 30.dp.toPx()), end = Offset(x, height - 20.dp.toPx()), strokeWidth = 1.dp.toPx())
            nativeCanvas.drawText(ch.toString(), x.coerceIn(12.dp.toPx(), width - 12.dp.toPx()), height - 5.dp.toPx(), channelPaint)
        }

        networks.sortedByDescending { it.rssi }.take(16).forEach { net ->
            val center = net.channel.coerceIn(minChannel, maxChannel)
            val x = ((center - minChannel).toFloat() / channelSpan) * width
            val peakY = height - 38.dp.toPx() - (((net.rssi + 100) / 70f).coerceIn(0.1f, 1f) * (height - 65.dp.toPx()))
            val ssid = net.ssid.ifBlank { "Скрытая" }
            val limit = if (width > 700.dp.toPx()) 18 else 9
            val shortName = if (ssid.length > limit) ssid.take(limit - 1) + "…" else ssid
            nativeCanvas.drawText("$shortName · CH${net.channel}", x.coerceIn(48.dp.toPx(), width - 48.dp.toPx()), (peakY - 7.dp.toPx()).coerceAtLeast(14.dp.toPx()), labelPaint)
        }

        // Draw parabolic curves for each network
        networks.take(12).forEach { net ->
            val centerChannel = net.channel.coerceIn(minChannel, maxChannel)
            val centerX = ((centerChannel - minChannel).toFloat() / channelSpan) * width
            val curveWidth = (width / channelSpan) * (if (band == WifiBand.BAND_2_4_GHZ) 4.5f else 3.5f)

            // Normalized peak height from RSSI (-100 to -30 dBm)
            val peakY = height - 25.dp.toPx() - (((net.rssi + 100) / 70f).coerceIn(0.1f, 1f) * (height - 50.dp.toPx()))

            val path = Path()
            path.moveTo(centerX - curveWidth / 2, height - 20.dp.toPx())
            path.cubicTo(
                centerX - curveWidth / 4, peakY,
                centerX + curveWidth / 4, peakY,
                centerX + curveWidth / 2, height - 20.dp.toPx()
            )

            val curveColor = Color(net.signalQuality.hexColor)
            drawPath(path = path, color = curveColor.copy(alpha = 0.08f), style = Stroke(width = 12.dp.toPx()))
            drawPath(path = path, color = curveColor.copy(alpha = 0.24f), style = Stroke(width = 6.dp.toPx()))
            drawPath(path = path, color = curveColor.copy(alpha = 0.16f))
            drawPath(path = path, color = curveColor, style = Stroke(width = 2.dp.toPx()))
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
