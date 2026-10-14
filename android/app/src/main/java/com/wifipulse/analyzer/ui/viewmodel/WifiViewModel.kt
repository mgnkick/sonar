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

    init {
        checkWifiState()
        refreshConnectionDetails()
        refreshAudit()
        observeScans()
    }

    fun onPermissionsGranted() {
        _uiState.update { it.copy(hasPermissions = true) }
        triggerScan()
    }

    fun checkWifiState() {
        _uiState.update { it.copy(isWifiEnabled = repository.isWifiEnabled) }
    }

    fun refreshConnectionDetails() {
        val details = repository.getCurrentConnectionDetails()
        _uiState.update { it.copy(connectionDetails = details) }
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
                    val selectedBssid = current.selectedNetworkBssid ?: scanResults.firstOrNull()?.bssid
                    val selectedNet = scanResults.firstOrNull { it.bssid == selectedBssid }

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
        if (_uiState.value.scanThrottleCooldownSeconds > 0) return

        _uiState.update { it.copy(isScanning = true) }
        val initiated = repository.startScan()
        refreshConnectionDetails()
        refreshAudit()

        startCooldownTimer(15)

        if (!initiated) {
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
            listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13)
        } else {
            listOf(36, 40, 44, 48, 52, 56, 60, 64, 100, 104, 108, 112, 116, 120, 124, 128, 132, 136, 140, 149, 153, 157, 161, 165)
        }

        val bandNetworks = networks.filter { it.band == band }

        return standardChannels.map { ch ->
            val overlapping = bandNetworks.filter { net ->
                if (band == WifiBand.BAND_2_4_GHZ) {
                    kotlin.math.abs(net.channel - ch) < 3
                } else {
                    net.channel == ch
                }
            }

            val interferenceScore = overlapping.sumOf {
                when {
                    it.rssi > -60 -> 4
                    it.rssi > -75 -> 2
                    else -> 1
                }
            }

            val finalScore = (10 - interferenceScore).coerceIn(1, 10)
            val isRecommended = if (band == WifiBand.BAND_2_4_GHZ) {
                (ch == 1 || ch == 6 || ch == 11) && finalScore >= 7
            } else {
                finalScore >= 8
            }

            ChannelRating(
                channel = ch,
                frequency = WifiRepository.channelToFrequency(ch, band),
                band = band,
                networkCount = overlapping.size,
                score = finalScore,
                isRecommended = isRecommended,
                overlappingNetworks = overlapping.map { it.ssid }
            )
        }
    }
}
