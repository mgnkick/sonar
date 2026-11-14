package com.mgnkick.wifisonar.ui.screens

import com.mgnkick.wifisonar.ui.components.Text
import com.mgnkick.wifisonar.ui.components.HelpHint


import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import com.mgnkick.wifisonar.ui.components.LocalAppLanguage
import com.mgnkick.wifisonar.ui.components.appUiText
import com.mgnkick.wifisonar.data.model.*
import com.mgnkick.wifisonar.AboutIconButton
import com.mgnkick.wifisonar.ui.viewmodel.SortOption
import com.mgnkick.wifisonar.ui.viewmodel.WifiUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    state: WifiUiState,
    onScanClick: () -> Unit,
    onBandSelected: (WifiBand?) -> Unit,
    onSearchChange: (String) -> Unit,
    onSortChange: (SortOption) -> Unit,
    onToggleGroup: (String) -> Unit,
    onNetworkSelected: (String) -> Unit,
    onNavigateToAnalysis: () -> Unit,
    onRefreshNetworks: () -> Unit,
    onOpenAbout: () -> Unit = {}
) {
    var showSortMenu by remember { mutableStateOf(false) }
    var showSurveyDialog by remember { mutableStateOf(false) }
    var networkSelectionMode by remember { mutableStateOf(false) }
    val selectedSsids = remember { mutableStateListOf<String>() }
    val context = LocalContext.current
    val language = LocalAppLanguage.current
    val exportingSelection = networkSelectionMode && selectedSsids.isNotEmpty()
    val reportGroups = if (networkSelectionMode && selectedSsids.isNotEmpty()) state.filteredGroups.filter { it.ssid in selectedSsids } else state.filteredGroups
    val networkReport = remember(state.connectionDetails, reportGroups, exportingSelection) {
        buildString {
            appendLine("WiFi Sonar — ${appUiText(if (exportingSelection) "Выбранные сети" else "Комплексный отчёт Wi-Fi", language)}")
            appendLine("${appUiText("Создано: ", language)}${java.text.SimpleDateFormat("dd.MM.yyyy HH:mm:ss", java.util.Locale("ru", "RU")).format(java.util.Date())}")
            appendLine()
            if (!exportingSelection) {
                val connection = state.connectionDetails
                appendLine(appUiText("ТЕКУЩЕЕ ПОДКЛЮЧЕНИЕ", language))
                appendLine("${appUiText("Статус: ", language)}${appUiText(if (connection.isConnected) "Подключено" else "Нет подключения", language)}")
                appendLine("SSID: ${connection.ssid}")
                appendLine("BSSID: ${connection.bssid}")
                appendLine("${appUiText("Сигнал: ", language)}${connection.rssi} ${appUiText("дБм", language)}")
                appendLine("${appUiText("Частота: ", language)}${connection.frequency} ${appUiText("МГц", language)} · ${appUiText("Скорость: ", language)}${connection.linkSpeedMbps} ${appUiText("Мбит/с", language)} (TX ${connection.txLinkSpeedMbps} / RX ${connection.rxLinkSpeedMbps})")
                appendLine("IP: ${connection.ipAddress} · ${appUiText("Шлюз", language)}: ${connection.gateway} · ${appUiText("Маска", language)}: ${connection.subnetMask}")
                appendLine("DNS: ${connection.dns1}, ${connection.dns2} · ${appUiText("Стандарт", language)}: ${appUiText(connection.standard, language)}")
                appendLine()
            }
            appendLine("${appUiText("ВИДИМЫЕ СЕТИ", language)} (${reportGroups.size})")
            reportGroups.forEach { group ->
                appendLine("\n${group.ssid} · ${group.primaryVendor} · ${appUiText(group.topologyType.displayName, language)}")
                appendLine("${appUiText("Защита: ", language)}${appUiText(group.bestSecurity.displayName, language)} · ${appUiText("Точек доступа: ", language)}${group.totalNodesCount}")
                group.nodes.forEach { node ->
                    appendLine("  ${node.bssid} · ${appUiText(node.band.displayName, language)} · ${appUiText("Канал", language)} ${node.channel} · ${node.frequency} ${appUiText("МГц", language)} · ${node.rssi} ${appUiText("дБм", language)} · ${node.channelWidth} ${appUiText("МГц", language)}")
                }
            }
        }.trimEnd()
    }
    val scrollState = rememberScrollState()
    val isAtContentEnd = scrollState.maxValue > 0 && scrollState.value >= scrollState.maxValue
    val totalNodesCount = state.networkGroups.sumOf { it.totalNodesCount }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "WiFi Pulse Analyzer",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Сетей: ${state.filteredGroups.size} • Физических BSSID: $totalNodesCount",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        networkSelectionMode = !networkSelectionMode
                        if (!networkSelectionMode) selectedSsids.clear()
                    }) {
                        Icon(if (networkSelectionMode) Icons.Default.Close else Icons.Default.Checklist, contentDescription = appUiText("Выбрать сети", language))
                    }
                    HelpHint("Включить режим выбора плиток сетей, чтобы скопировать или сохранить только отмеченные сети.")
                    if (networkSelectionMode && selectedSsids.isNotEmpty()) {
                        IconButton(onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Wi-Fi сети", networkReport))
                            Toast.makeText(context, appUiText("Выбранные сети скопированы", language), Toast.LENGTH_SHORT).show()
                        }) { Icon(Icons.Default.ContentCopy, contentDescription = appUiText("Скопировать сети", language)) }
                    } else {
                        IconButton(onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Wi-Fi отчёт", networkReport))
                            Toast.makeText(context, appUiText("Отчёт скопирован", language), Toast.LENGTH_SHORT).show()
                        }) { Icon(Icons.Default.ContentCopy, contentDescription = appUiText("Скопировать отчёт", language)) }
                    }
                    IconButton(onClick = {
                        val saved = exportTextFile(context, if (networkSelectionMode && selectedSsids.isNotEmpty()) "WiFiPulse-selected-networks" else "WiFiPulse-wifi-report", networkReport)
                        Toast.makeText(context, appUiText(if (saved != null) "Файл сохранён в Загрузки/WiFiPulse" else "Не удалось сохранить файл", language), Toast.LENGTH_LONG).show()
                    }) { Icon(Icons.Default.SaveAlt, contentDescription = appUiText("Сохранить отчёт", language)) }
                    HelpHint("Сохранить TXT-отчёт по текущему подключению и видимым сетям либо выбранным сетям.")
                    AboutIconButton(onOpenAbout)
                    IconButton(onClick = { showSurveyDialog = true }) {
                        Icon(Icons.Default.Place, contentDescription = "Обследование квартиры")
                    }
                    HelpHint("Открыть журнал замеров по комнатам и сравнить результаты обследования.")
                    IconButton(onClick = { showSortMenu = true }) {
                        Icon(Icons.Default.Sort, contentDescription = "Сортировка")
                    }
                    HelpHint("Изменить порядок отображения найденных сетей.")
                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false }
                    ) {
                        SortOption.values().forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.title) },
                                onClick = {
                                    onSortChange(option)
                                    showSortMenu = false
                                },
                                leadingIcon = {
                                    if (state.sortBy == option) {
                                        Icon(Icons.Default.Check, contentDescription = null)
                                    }
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButtonPosition = if (isAtContentEnd) FabPosition.Start else FabPosition.End,
        floatingActionButton = {
            StickyWifiRefreshButton(
                isRefreshing = state.isScanning,
                onRefresh = onScanClick,
                modifier = Modifier.offset(y = (-24).dp)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(horizontal = 12.dp)
        ) {
            ConnectionStatusPanel(
                details = state.connectionDetails,
                visibleNetworkCount = state.networks.size,
                isRefreshing = state.isScanning,
                onRefreshNetworks = onRefreshNetworks,
                lastScanTimestamp = state.lastScanTimestamp,
                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
                showRefreshButton = false
            )

            // Search Bar
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = onSearchChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                placeholder = { Text("Поиск по SSID, BSSID или вендору (Keenetic, Deco)...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.searchQuery.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { onSearchChange("") }) {
                                Icon(Icons.Default.Close, contentDescription = "Очистить")
                            }
                            HelpHint("Очистить текст поиска.")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            // Band Filter Chips
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FilterChip(selected = state.selectedBand == null, onClick = { onBandSelected(null) }, label = { Text("Все диапазоны") })
                        HelpHint("Показывать найденные сети всех диапазонов.")
                    }
                }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FilterChip(selected = state.selectedBand == WifiBand.BAND_2_4_GHZ, onClick = { onBandSelected(WifiBand.BAND_2_4_GHZ) }, label = { Text("2.4 ГГц") })
                        HelpHint("Показывать только сети диапазона 2,4 ГГц.")
                    }
                }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FilterChip(selected = state.selectedBand == WifiBand.BAND_5_GHZ, onClick = { onBandSelected(WifiBand.BAND_5_GHZ) }, label = { Text("5 ГГц") })
                        HelpHint("Показывать только сети диапазона 5 ГГц.")
                    }
                }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FilterChip(selected = state.selectedBand == WifiBand.BAND_6_GHZ, onClick = { onBandSelected(WifiBand.BAND_6_GHZ) }, label = { Text("6 ГГц (Wi-Fi 6E)") })
                        HelpHint("Показывать только сети диапазона 6 ГГц (Wi-Fi 6E).")
                    }
                }
            }

            // Networks List (Hierarchical Expandable Groups)
            if (state.filteredGroups.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 220.dp)
                        .padding(bottom = 80.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.WifiOff,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (state.isScanning) "Идет сканирование эфира..." else "Точки доступа не найдены",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                        Text(
                            text = "Нажмите «Сканировать» для обнаружения сетей и Mesh-систем",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    state.filteredGroups.forEach { group ->
                        ExpandableWifiGroupCard(
                            group = group,
                            connectedSsid = state.connectionDetails.ssid.takeIf { state.connectionDetails.isConnected },
                            selectedBssid = state.selectedNetworkBssid,
                            onToggle = {
                                if (networkSelectionMode) {
                                    if (group.ssid in selectedSsids) selectedSsids.remove(group.ssid) else selectedSsids.add(group.ssid)
                                } else onToggleGroup(group.ssid)
                            },
                            isSelectedForExport = group.ssid in selectedSsids,
                            selectionMode = networkSelectionMode,
                            onSelectNode = { bssid ->
                                onNetworkSelected(bssid)
                                onNavigateToAnalysis()
                            }
                        )
                    }
                    Spacer(modifier = Modifier.height(88.dp))
                }
            }
        }
        if (showSurveyDialog) {
            SurveyPointDialog(
                details = state.connectionDetails,
                onDismiss = { showSurveyDialog = false }
            )
        }
    }
}

@Composable
fun ExpandableWifiGroupCard(
    group: WifiNetworkGroup,
    connectedSsid: String?,
    selectedBssid: String?,
    onToggle: () -> Unit,
    onSelectNode: (String) -> Unit,
    isSelectedForExport: Boolean = false,
    selectionMode: Boolean = false
) {
    val bestNode = group.bestNode
    val qualityColor = bestNode?.let { Color(it.signalQuality.hexColor) } ?: MaterialTheme.colorScheme.primary
    val topologyColor = Color(group.topologyType.badgeColor)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = androidx.compose.foundation.BorderStroke(
            if (group.isExpanded) 1.5.dp else 1.dp,
            (if (isSelectedForExport) MaterialTheme.colorScheme.tertiary else qualityColor).copy(alpha = if (group.isExpanded || isSelectedForExport) 0.85f else 0.30f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Thin signal-colored neon rail ties each network card to its measured RSSI.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(if (group.isExpanded) 3.dp else 2.dp)
                    .background(
                        androidx.compose.ui.graphics.Brush.horizontalGradient(
                            listOf(qualityColor.copy(alpha = 0.95f), MaterialTheme.colorScheme.tertiary.copy(alpha = 0.55f), Color.Transparent)
                        )
                    )
            )
            // Parent Header (SSID Level)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(if (group.isExpanded) 12.dp else 6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${group.ssid}  •  CH ${group.nodes.map { it.channel }.distinct().sorted().joinToString("/")}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (group.bestSecurity == SecurityType.OPEN) {
                                Spacer(modifier = Modifier.width(6.dp))
                                BadgeChip(label = "БЕЗ ПАРОЛЯ", color = Color(0xFFFF8A4C))
                            }
                            if (connectedSsid != null && group.ssid == connectedSsid) {
                                Spacer(modifier = Modifier.width(6.dp))
                                BadgeChip(label = "ПОДКЛЮЧЕНО", color = Color(0xFF22C55E))
                            }
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        // Vendor & Node count
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = group.primaryVendor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "•",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            )
                            Text(
                                text = "${group.totalNodesCount} BSSID • ${group.nodes.map { it.band.displayName }.distinct().joinToString(", ")}",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    }

                    // Best RSSI Badge & Expand Chevron
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = qualityColor.copy(alpha = 0.15f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, qualityColor.copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = "${group.bestRssi} дБм",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = qualityColor
                            )
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        IconButton(
                            onClick = onToggle,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                if (selectionMode) {
                                    if (isSelectedForExport) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked
                                } else if (group.isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (selectionMode) "Выбрать сеть" else if (group.isExpanded) "Свернуть" else "Развернуть",
                                tint = if (isSelectedForExport) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                        HelpHint(if (selectionMode) "Отметить эту сеть для копирования или сохранения." else if (group.isExpanded) "Свернуть список точек доступа этой сети." else "Показать отдельные точки доступа и BSSID этой сети.")
                    }
                }

                Spacer(modifier = Modifier.height(if (group.isExpanded) 8.dp else 3.dp))

                // Topology Badge & Roaming Tags
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Topology Badge
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = topologyColor.copy(alpha = 0.15f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, topologyColor.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                when (group.topologyType) {
                                    NetworkTopologyType.MESH_NETWORK -> Icons.Default.Hub
                                    NetworkTopologyType.REPEATER_DETECTED -> Icons.Default.AltRoute
                                    NetworkTopologyType.ENTERPRISE_ROAMING -> Icons.Default.Business
                                    NetworkTopologyType.STANDALONE -> Icons.Default.Wifi
                                },
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = topologyColor
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = group.topologyType.displayName,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = topologyColor,
                                fontSize = 10.sp
                            )
                        }
                    }

                    // Roaming 802.11k/r/v badge
                    val roaming = group.roamingSummary
                    if (roaming.isSupported) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = buildList {
                                    if (roaming.has80211k) add("802.11k")
                                    if (roaming.has80211r) add("802.11r")
                                    if (roaming.has80211v) add("802.11v")
                                }.joinToString("/"),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            )
                        }
                    }

                    // Security type
                    BadgeChip(label = group.bestSecurity.displayName, color = Color.Gray)
                }
            }

            // Expandable Children: Sub-nodes (BSSIDs)
            AnimatedVisibility(
                visible = group.isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Физические узлы сети (${group.nodes.size} BSSID):",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )

                    group.nodes.forEach { node ->
                        BssIdNodeCard(
                            node = node,
                            isSelected = node.bssid == selectedBssid,
                            onSelect = { onSelectNode(node.bssid) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BssIdNodeCard(
    node: BssIdNode,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    val qualityColor = Color(node.signalQuality.hexColor)
    val roleColor = Color(node.nodeRole.badgeColor)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = if (isSelected) {
            androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
        } else {
            androidx.compose.foundation.BorderStroke(1.dp, qualityColor.copy(alpha = 0.28f))
        },
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 2.dp else 0.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            // Node Role badge & BSSID MAC
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = roleColor.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = node.nodeRole.displayName,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = roleColor,
                            fontSize = 10.sp
                        )
                    }
                }

                Text(
                    text = "${node.rssi} дБм",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = qualityColor
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // MAC and Hardware Vendor
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = node.bssid,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = node.vendor,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Channel & Frequency & Width
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BadgeChip(label = node.band.displayName, color = MaterialTheme.colorScheme.primary)
                BadgeChip(label = "Канал ${node.channel} (${node.frequency} МГц)", color = MaterialTheme.colorScheme.secondary)
                BadgeChip(label = "${node.channelWidth} МГц", color = MaterialTheme.colorScheme.tertiary)

                if (node.roamingSupport.isSupported) {
                    val flags = buildList {
                        if (node.roamingSupport.has80211k) add("k")
                        if (node.roamingSupport.has80211r) add("r")
                        if (node.roamingSupport.has80211v) add("v")
                    }.joinToString("")
                    BadgeChip(label = "802.11$flags", color = Color(0xFF10B981))
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Progress Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LinearProgressIndicator(
                    progress = node.signalPercent / 100f,
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = qualityColor,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${node.signalPercent}% (~${node.estimatedDistanceMeters} м)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
fun BadgeChip(label: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.12f)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp
        )
    }
}

