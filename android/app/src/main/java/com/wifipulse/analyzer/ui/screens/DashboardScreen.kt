package com.wifipulse.analyzer.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import com.wifipulse.analyzer.data.model.*
import com.wifipulse.analyzer.ui.viewmodel.SortOption
import com.wifipulse.analyzer.ui.viewmodel.WifiUiState

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
    onNavigateToAnalysis: () -> Unit
) {
    var showSortMenu by remember { mutableStateOf(false) }

    val totalNodesCount = state.networkGroups.sumOf { it.totalNodesCount }

    Scaffold(
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
                    IconButton(onClick = { showSortMenu = true }) {
                        Icon(Icons.Default.Sort, contentDescription = "Сортировка")
                    }
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
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onScanClick,
                icon = {
                    if (state.isScanning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = "Сканировать")
                    }
                },
                text = {
                    if (state.scanThrottleCooldownSeconds > 0) {
                        Text("Ждать ${state.scanThrottleCooldownSeconds} с")
                    } else {
                        Text(if (state.isScanning) "Сканирование..." else "Сканировать")
                    }
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
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
                        IconButton(onClick = { onSearchChange("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Очистить")
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
                    FilterChip(
                        selected = state.selectedBand == null,
                        onClick = { onBandSelected(null) },
                        label = { Text("Все диапазоны") }
                    )
                }
                item {
                    FilterChip(
                        selected = state.selectedBand == WifiBand.BAND_2_4_GHZ,
                        onClick = { onBandSelected(WifiBand.BAND_2_4_GHZ) },
                        label = { Text("2.4 ГГц") }
                    )
                }
                item {
                    FilterChip(
                        selected = state.selectedBand == WifiBand.BAND_5_GHZ,
                        onClick = { onBandSelected(WifiBand.BAND_5_GHZ) },
                        label = { Text("5 ГГц") }
                    )
                }
                item {
                    FilterChip(
                        selected = state.selectedBand == WifiBand.BAND_6_GHZ,
                        onClick = { onBandSelected(WifiBand.BAND_6_GHZ) },
                        label = { Text("6 ГГц (Wi-Fi 6E)") }
                    )
                }
            }

            // Networks List (Hierarchical Expandable Groups)
            if (state.filteredGroups.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
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
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 88.dp)
                ) {
                    items(state.filteredGroups, key = { it.ssid }) { group ->
                        ExpandableWifiGroupCard(
                            group = group,
                            selectedBssid = state.selectedNetworkBssid,
                            onToggle = { onToggleGroup(group.ssid) },
                            onSelectNode = { bssid ->
                                onNetworkSelected(bssid)
                                onNavigateToAnalysis()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ExpandableWifiGroupCard(
    group: WifiNetworkGroup,
    selectedBssid: String?,
    onToggle: () -> Unit,
    onSelectNode: (String) -> Unit
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
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Parent Header (SSID Level)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = group.ssid,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        // Vendor & Node count
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = group.primaryVendor,
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
                                text = "${group.totalNodesCount} BSSID (${group.nodes.map { it.band.displayName }.distinct().joinToString(", ")})",
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
                                if (group.isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (group.isExpanded) "Свернуть" else "Развернуть",
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

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
            androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
        }
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
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = node.vendor,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Channel & Frequency & Width
            Row(
                modifier = Modifier.fillMaxWidth(),
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
