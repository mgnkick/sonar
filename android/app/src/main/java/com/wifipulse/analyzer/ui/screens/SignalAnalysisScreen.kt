package com.wifipulse.analyzer.ui.screens

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
    onSelectNetwork: (String) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("График сигнала", "Спектр каналов", "Рейтинг каналов")

    val selectedNetwork = state.networks.firstOrNull { it.bssid == state.selectedNetworkBssid }
        ?: state.networks.firstOrNull()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Анализ сигнала и спектра", fontWeight = FontWeight.Bold) },
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
        ) {
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
fun SignalGraphTab(state: WifiUiState, selectedNetwork: WifiNetwork?) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
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
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
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

    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        // Draw horizontal grid lines: -30 dBm, -50 dBm, -70 dBm, -90 dBm
        val dbmLevels = listOf(-30, -50, -70, -90)
        dbmLevels.forEach { dbm ->
            val y = ((-dbm - 30) / 70f) * height
            drawLine(
                color = Color.Gray.copy(alpha = 0.2f),
                start = Offset(0f, y),
                end = Offset(width, y),
                strokeWidth = 1.dp.toPx()
            )
        }

        if (history.size < 2) {
            // Draw placeholder wave if samples are accumulating
            return@Canvas
        }

        val stepX = width / (history.size - 1).coerceAtLeast(1)
        val path = Path()

        history.forEachIndexed { i, pt ->
            // Map -100 dBm to height, -30 dBm to 0
            val normalized = ((-pt.rssi - 30) / 70f).coerceIn(0f, 1f)
            val x = i * stepX
            val y = normalized * height

            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = 3.dp.toPx())
        )
    }
}

@Composable
fun ChannelSpectrumTab(networks: List<WifiNetwork>) {
    var selectedBand by remember { mutableStateOf(WifiBand.BAND_2_4_GHZ) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
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
                SpectrumCanvas(networks = networks.filter { it.band == selectedBand }, band = selectedBand)
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
fun SpectrumCanvas(networks: List<WifiNetwork>, band: WifiBand) {
    val primaryColor = MaterialTheme.colorScheme.primary

    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

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

        // Draw parabolic curves for each network
        networks.take(12).forEach { net ->
            val centerChannel = net.channel.coerceIn(minChannel, maxChannel)
            val centerX = ((centerChannel - minChannel).toFloat() / channelSpan) * width
            val curveWidth = (width / channelSpan) * (if (band == WifiBand.BAND_2_4_GHZ) 4.5f else 3.5f)

            // Normalized peak height from RSSI (-100 to -30 dBm)
            val peakY = height - 25.dp.toPx() - (((net.rssi + 100) / 70f).coerceIn(0.1f, 1f) * (height - 50.dp.toPx()))

            val path = Path()
            path.moveTo(centerX - curveWidth / 2, height - 20.dp.toPx())
            path.quadraticTo(centerX, peakY, centerX + curveWidth / 2, height - 20.dp.toPx())

            val curveColor = Color(net.signalQuality.hexColor)
            drawPath(path = path, color = curveColor.copy(alpha = 0.35f))
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
            .fillMaxSize()
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

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(ratings) { rating ->
                ChannelRatingCard(rating = rating)
            }
        }
    }
}

@Composable
fun ChannelRatingCard(rating: ChannelRating) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
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
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "${rating.frequency} МГц",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (rating.networkCount == 0) "Свободен (0 помех)" else "Сетей на канале/рядом: ${rating.networkCount}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }

            // Score stars or number
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${rating.score}/10",
                    fontWeight = FontWeight.Bold,
                    color = if (rating.score >= 8) Color(0xFF22C55E) else if (rating.score >= 5) Color(0xFFF59E0B) else Color(0xFFEF4444)
                )
                if (rating.isRecommended) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(Icons.Default.Verified, contentDescription = "Рекомендован", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
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
