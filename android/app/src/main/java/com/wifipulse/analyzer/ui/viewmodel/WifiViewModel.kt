package com.wifipulse.analyzer.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wifipulse.analyzer.data.model.*
import com.wifipulse.analyzer.data.repository.WifiRepository
import com.wifipulse.analyzer.data.repository.WifiTopologyAnalyzer
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WifiUiState(
    val networks: List<WifiNetwork> = emptyList(),
    val filteredNetworks: List<WifiNetwork> = emptyList(),
    val networkGroups: List<WifiNetworkGroup> = emptyList(),
    val filteredGroups: List<WifiNetworkGroup> = emptyList(),
    val expandedSsids: Set<String> = emptySet(),
    val connectionDetails: WifiConnectionDetails = WifiConnectionDetails(),
    val isScanning: Boolean = false,
    val selectedBand: WifiBand? = null, // null means ALL
    val searchQuery: String = "",
    val sortBy: SortOption = SortOption.SIGNAL,
    val selectedNetworkBssid: String? = null,
    val signalHistory: List<SignalHistoryPoint> = emptyList(),
    val channelRatings24: List<ChannelRating> = emptyList(),
    val channelRatings5: List<ChannelRating> = emptyList(),
    val isWifiEnabled: Boolean = true,
    val hasPermissions: Boolean = false,
    val scanThrottleCooldownSeconds: Int = 0,
    val lastScanTimestamp: Long = 0,
    val auditReport: AuditReport? = null
)

enum class SortOption(val title: String) {
    SIGNAL("По уровню сигнала"),
    SSID("По имени (A-Z)"),
    CHANNEL("По номеру канала"),
    NODES_COUNT("По числу узлов (Mesh/Репитеры)")
}

class WifiViewModel(
    private val repository: WifiRepository
) : ViewModel() {

    private val auditEngine = com.wifipulse.analyzer.data.repository.SmartAuditEngine()
    private val _uiState = MutableStateFlow(WifiUiState())
    val uiState: StateFlow<WifiUiState> = _uiState.asStateFlow()

    private var cooldownJob: Job? = null
    private var autoRefreshJob: Job? = null
    private var scanTimeoutJob: Job? = null

    /**
     * Refresh live connection/VPN/mobile state every 2 seconds while visible.
     * The network list itself remains event-driven by Android scan-result broadcasts;
     * this loop never starts extra radio scans.
     */
    fun startAutoRefresh() {
        refreshConnectionDetails()
        refreshAudit()
        if (autoRefreshJob?.isActive == true) return
        autoRefreshJob = viewModelScope.launch {
            while (true) {
                delay(2_000)
                refreshConnectionDetails()
            }
        }
    }

    fun stopAutoRefresh() {
        autoRefreshJob?.cancel()
        autoRefreshJob = null
    }

    override fun onCleared() {
        cooldownJob?.cancel()
        autoRefreshJob?.cancel()
        scanTimeoutJob?.cancel()
        super.onCleared()
    }

    init {
        checkWifiState()
        refreshConnectionDetails()
        refreshAudit()
        observeScans()
    }

    fun onPermissionsGranted() {
        _uiState.update { it.copy(hasPermissions = true) }
        triggerScan()
        startAutoRefresh()
    }

    fun checkWifiState() {
        _uiState.update { it.copy(isWifiEnabled = repository.isWifiEnabled) }
    }

    fun refreshConnectionDetails() {
        val details = repository.getCurrentConnectionDetails()
        _uiState.update { current ->
            val isSelectedConnection = details.isConnected &&
                current.selectedNetworkBssid.equals(details.bssid, ignoreCase = true)
            val now = System.currentTimeMillis()
            val lastPoint = current.signalHistory.lastOrNull()
            val history = if (isSelectedConnection && details.rssi in -100..0 &&
                (lastPoint == null || now - lastPoint.timestamp >= 1_000)
            ) {
                (current.signalHistory + SignalHistoryPoint(now, details.rssi)).takeLast(120)
            } else current.signalHistory
            current.copy(
                connectionDetails = details,
                signalHistory = history,
                auditReport = auditEngine.generateReport(current.networkGroups, details)
            )
        }
    }

    fun toggleGroupExpansion(ssid: String) {
        _uiState.update { current ->
            val updatedExpanded = if (current.expandedSsids.contains(ssid)) {
                current.expandedSsids - ssid
            } else {
                current.expandedSsids + ssid
            }

            val updatedGroups = current.networkGroups.map { grp ->
                grp.copy(isExpanded = updatedExpanded.contains(grp.ssid))
            }
            val updatedFiltered = applyGroupFiltersAndSort(
                groups = updatedGroups,
                band = current.selectedBand,
                query = current.searchQuery,
                sort = current.sortBy
            )

            current.copy(
                expandedSsids = updatedExpanded,
                networkGroups = updatedGroups,
                filteredGroups = updatedFiltered
            )
        }
    }

    private fun observeScans() {
        viewModelScope.launch {
            repository.observeScanResults().collect { scanResults ->
                scanTimeoutJob?.cancel()
                _uiState.update { current ->
                    // Aggregate into groups
                    val groups = WifiTopologyAnalyzer.aggregateIntoGroups(
                        networks = scanResults,
                        previousExpandedSsids = current.expandedSsids
                    )

                    val updatedFiltered = applyFiltersAndSort(
                        networks = scanResults,
                        band = current.selectedBand,
                        query = current.searchQuery,
                        sort = current.sortBy
                    )

                    val updatedFilteredGroups = applyGroupFiltersAndSort(
                        groups = groups,
                        band = current.selectedBand,
                        query = current.searchQuery,
                        sort = current.sortBy
                    )

                    // Auto-select first or maintain selection
                    val connectedBssid = current.connectionDetails.bssid
                        .takeIf { current.connectionDetails.isConnected && it.isNotBlank() }
                    val selectedBssid = current.selectedNetworkBssid
                        ?: connectedBssid?.takeIf { bssid -> scanResults.any { it.bssid.equals(bssid, ignoreCase = true) } }
                        ?: scanResults.firstOrNull()?.bssid
                    val selectedNet = scanResults.firstOrNull { it.bssid.equals(selectedBssid, ignoreCase = true) }

                    val updatedHistory = if (selectedNet != null) {
                        val newPoint = SignalHistoryPoint(System.currentTimeMillis(), selectedNet.rssi)
                        (current.signalHistory + newPoint).takeLast(30)
                    } else {
                        current.signalHistory
                    }

                    val ratings24 = calculateChannelRatings(scanResults, WifiBand.BAND_2_4_GHZ)
                    val ratings5 = calculateChannelRatings(scanResults, WifiBand.BAND_5_GHZ)
                    val report = auditEngine.generateReport(groups, current.connectionDetails)

                    current.copy(
                        networks = scanResults,
                        filteredNetworks = updatedFiltered,
                        networkGroups = groups,
                        filteredGroups = updatedFilteredGroups,
                        selectedNetworkBssid = selectedBssid,
                        signalHistory = updatedHistory,
                        channelRatings24 = ratings24,
                        channelRatings5 = ratings5,
                        auditReport = report,
                        isScanning = false,
                        lastScanTimestamp = System.currentTimeMillis()
                    )
                }
            }
        }
    }

    fun refreshAudit() {
        val groups = _uiState.value.networkGroups
        val details = repository.getCurrentConnectionDetails()
        val report = auditEngine.generateReport(groups, details)
        _uiState.update { it.copy(connectionDetails = details, auditReport = report) }
    }

    fun triggerScan() {
        // Refresh connection warnings even if Android throttles the actual radio scan.
        refreshConnectionDetails()
        refreshAudit()
        if (_uiState.value.scanThrottleCooldownSeconds > 0) return

        _uiState.update { it.copy(isScanning = true) }
        val initiated = repository.startScan()
        refreshConnectionDetails()
        refreshAudit()

        startCooldownTimer(15)

        scanTimeoutJob?.cancel()
        if (initiated) {
            // Android may report a throttled/unchanged scan without a fresh-results event.
            // Never leave the refresh button spinning forever in that case.
            scanTimeoutJob = viewModelScope.launch {
                delay(8_000)
                _uiState.update { it.copy(isScanning = false) }
            }
        } else {
            _uiState.update { it.copy(isScanning = false) }
        }
    }

    private fun startCooldownTimer(seconds: Int) {
        cooldownJob?.cancel()
        cooldownJob = viewModelScope.launch {
            for (i in seconds downTo 1) {
                _uiState.update { it.copy(scanThrottleCooldownSeconds = i) }
                delay(1000)
            }
            _uiState.update { it.copy(scanThrottleCooldownSeconds = 0) }
        }
    }

    fun selectBand(band: WifiBand?) {
        _uiState.update { current ->
            current.copy(
                selectedBand = band,
                filteredNetworks = applyFiltersAndSort(
                    networks = current.networks,
                    band = band,
                    query = current.searchQuery,
                    sort = current.sortBy
                ),
                filteredGroups = applyGroupFiltersAndSort(
                    groups = current.networkGroups,
                    band = band,
                    query = current.searchQuery,
                    sort = current.sortBy
                )
            )
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { current ->
            current.copy(
                searchQuery = query,
                filteredNetworks = applyFiltersAndSort(
                    networks = current.networks,
                    band = current.selectedBand,
                    query = query,
                    sort = current.sortBy
                ),
                filteredGroups = applyGroupFiltersAndSort(
                    groups = current.networkGroups,
                    band = current.selectedBand,
                    query = query,
                    sort = current.sortBy
                )
            )
        }
    }

    fun setSortOption(sort: SortOption) {
        _uiState.update { current ->
            current.copy(
                sortBy = sort,
                filteredNetworks = applyFiltersAndSort(
                    networks = current.networks,
                    band = current.selectedBand,
                    query = current.searchQuery,
                    sort = sort
                ),
                filteredGroups = applyGroupFiltersAndSort(
                    groups = current.networkGroups,
                    band = current.selectedBand,
                    query = current.searchQuery,
                    sort = sort
                )
            )
        }
    }

    fun selectNetworkForAnalysis(bssid: String) {
        _uiState.update { current ->
            val net = current.networks.firstOrNull { it.bssid == bssid }
            val initialHistory = if (net != null) {
                listOf(SignalHistoryPoint(System.currentTimeMillis(), net.rssi))
            } else emptyList()
            current.copy(
                selectedNetworkBssid = bssid,
                signalHistory = initialHistory
            )
        }
    }

    private fun applyFiltersAndSort(
        networks: List<WifiNetwork>,
        band: WifiBand?,
        query: String,
        sort: SortOption
    ): List<WifiNetwork> {
        return networks
            .filter { band == null || it.band == band }
            .filter { query.isEmpty() || it.ssid.contains(query, ignoreCase = true) || it.bssid.contains(query, ignoreCase = true) }
            .let { list ->
                when (sort) {
                    SortOption.SIGNAL -> list.sortedByDescending { it.rssi }
                    SortOption.SSID -> list.sortedBy { it.ssid.lowercase() }
                    SortOption.CHANNEL -> list.sortedBy { it.channel }
                    SortOption.NODES_COUNT -> list.sortedByDescending { it.rssi }
                }
            }
    }

    private fun applyGroupFiltersAndSort(
        groups: List<WifiNetworkGroup>,
        band: WifiBand?,
        query: String,
        sort: SortOption
    ): List<WifiNetworkGroup> {
        return groups
            .filter { grp -> band == null || grp.availableBands.contains(band) }
            .filter { grp ->
                query.isEmpty() ||
                grp.ssid.contains(query, ignoreCase = true) ||
                grp.nodes.any { it.bssid.contains(query, ignoreCase = true) || it.vendor.contains(query, ignoreCase = true) }
            }
            .let { list ->
                when (sort) {
                    SortOption.SIGNAL -> list.sortedByDescending { it.bestRssi }
                    SortOption.SSID -> list.sortedBy { it.ssid.lowercase() }
                    SortOption.CHANNEL -> list.sortedBy { it.bestNode?.channel ?: 0 }
                    SortOption.NODES_COUNT -> list.sortedByDescending { it.totalNodesCount }
                }
            }
    }

    private fun calculateChannelRatings(networks: List<WifiNetwork>, band: WifiBand): List<ChannelRating> {
        val standardChannels = if (band == WifiBand.BAND_2_4_GHZ) {
            (1..13).toList()
        } else {
            listOf(36, 40, 44, 48, 52, 56, 60, 64, 100, 104, 108, 112, 116, 120, 124, 128, 132, 136, 140, 144, 149, 153, 157, 161, 165)
        }
        val candidateWidth = if (band == WifiBand.BAND_2_4_GHZ) 20 else 40
        val bandNetworks = networks.filter { it.band == band }

        return standardChannels.map { channel ->
            val centerFrequency = WifiRepository.channelToFrequency(channel, band)
            val candidateLow = centerFrequency - candidateWidth / 2.0
            val candidateHigh = centerFrequency + candidateWidth / 2.0

            // Estimate occupied-spectrum overlap, weighted by received power.
            // Android exposes scan results, not real airtime/channel utilization.
            val interfering = bandNetworks.mapNotNull { net ->
                val networkWidth = net.channelWidth.coerceAtLeast(20)
                val networkCenter = net.frequency.toDouble()
                val networkLow = networkCenter - networkWidth / 2.0
                val networkHigh = networkCenter + networkWidth / 2.0
                val overlapMhz = (minOf(candidateHigh, networkHigh) - maxOf(candidateLow, networkLow)).coerceAtLeast(0.0)
                if (overlapMhz <= 0.0) null else {
                    val overlapRatio = (overlapMhz / candidateWidth).coerceIn(0.0, 1.0)
                    val signalWeight = when {
                        net.rssi >= -55 -> 4.0
                        net.rssi >= -67 -> 2.5
                        net.rssi >= -75 -> 1.5
                        else -> 0.5
                    }
                    Triple(net, overlapRatio, signalWeight * overlapRatio)
                }
            }

            val penalty = interfering.sumOf { it.third }
            val score = (10.0 - penalty).coerceIn(0.0, 10.0).toInt()
            val isPreferredPrimary = band != WifiBand.BAND_2_4_GHZ || channel in listOf(1, 6, 11)
            val recommended = isPreferredPrimary && score >= if (band == WifiBand.BAND_2_4_GHZ) 6 else 7
            val names = interfering.sortedByDescending { it.third }.map { it.first.ssid }.distinct()
            val explanation = buildString {
                append("Ширина оценки: ${candidateWidth} МГц; ")
                append("${interfering.size} сетей перекрывают часть канала")
                if (names.isNotEmpty()) append(" (сильнее всего: ${names.take(3).joinToString(", ")})")
                append(". Оценка учитывает ширину соседних сетей и их RSSI, но не измеряет фактическую загрузку эфира.")
                if (band == WifiBand.BAND_2_4_GHZ && channel !in listOf(1, 6, 11)) append(" Для 2,4 ГГц обычно предпочтительнее каналы 1, 6 или 11 при соблюдении региональных правил.")
            }

            ChannelRating(
                channel = channel,
                frequency = centerFrequency,
                band = band,
                networkCount = interfering.size,
                score = score,
                isRecommended = recommended,
                overlappingNetworks = names,
                recommendedWidthMhz = candidateWidth,
                explanation = explanation
            )
        }.sortedWith(compareByDescending<ChannelRating> { it.score }.thenBy { it.channel })
    }
}
