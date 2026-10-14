package com.wifipulse.analyzer.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wifipulse.analyzer.data.model.*
import com.wifipulse.analyzer.data.repository.NetworkDiagnosticRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DiagnosticUiState(
    val target: String = "8.8.8.8",
    val mode: DiagnosticMode = DiagnosticMode.PING,
    val status: DiagnosticStatus = DiagnosticStatus.IDLE,
    val packetCount: Int = 4, // 0 = continuous
    val timeoutSeconds: Int = 2,
    val logs: List<DiagnosticLogEntry> = emptyList(),
    val pingPackets: List<PingPacket> = emptyList(),
    val pingSummary: PingSummary? = null,
    val hops: List<TracerouteHop> = emptyList(),
    val errorMessage: String? = null
)

class DiagnosticViewModel(
    private val repository: NetworkDiagnosticRepository = NetworkDiagnosticRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(DiagnosticUiState())
    val uiState: StateFlow<DiagnosticUiState> = _uiState.asStateFlow()

    private var activeJob: Job? = null

    fun setTarget(target: String) {
        _uiState.update { it.copy(target = target, errorMessage = null) }
    }

    fun setPacketCount(count: Int) {
        _uiState.update { it.copy(packetCount = count) }
    }

    fun setTimeoutSeconds(timeout: Int) {
        _uiState.update { it.copy(timeoutSeconds = timeout) }
    }

    fun setMode(mode: DiagnosticMode) {
        _uiState.update { it.copy(mode = mode) }
    }

    fun startPing() {
        stopDiagnostic()

        val target = _uiState.value.target.trim()
        if (target.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Введите целевой адрес для пинга") }
            return
        }

        _uiState.update {
            it.copy(
                mode = DiagnosticMode.PING,
                status = DiagnosticStatus.RUNNING,
                logs = emptyList(),
                pingPackets = emptyList(),
                pingSummary = null,
                errorMessage = null
            )
        }

        activeJob = viewModelScope.launch {
            try {
                repository.executePing(
                    rawTarget = target,
                    count = _uiState.value.packetCount,
                    timeoutSeconds = _uiState.value.timeoutSeconds
                ).collect { entry ->
                    _uiState.update { current ->
                        val updatedLogs = (current.logs + entry).takeLast(200)
                        when (entry) {
                            is DiagnosticLogEntry.PingEntry -> {
                                current.copy(
                                    logs = updatedLogs,
                                    pingPackets = current.pingPackets + entry.packet
                                )
                            }
                            is DiagnosticLogEntry.Summary -> {
                                current.copy(
                                    logs = updatedLogs,
                                    pingSummary = entry.summary,
                                    status = DiagnosticStatus.COMPLETED
                                )
                            }
                            is DiagnosticLogEntry.Error -> {
                                current.copy(
                                    logs = updatedLogs,
                                    errorMessage = entry.message,
                                    status = DiagnosticStatus.ERROR
                                )
                            }
                            else -> current.copy(logs = updatedLogs)
                        }
                    }
                }

                _uiState.update { current ->
                    if (current.status == DiagnosticStatus.RUNNING) {
                        current.copy(status = DiagnosticStatus.COMPLETED)
                    } else current
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        status = DiagnosticStatus.CANCELLED,
                        logs = it.logs + DiagnosticLogEntry.Output("Операция остановлена.")
                    )
                }
            }
        }
    }

    fun startTraceroute() {
        stopDiagnostic()

        val target = _uiState.value.target.trim()
        if (target.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Введите целевой адрес для трассировки") }
            return
        }

        _uiState.update {
            it.copy(
                mode = DiagnosticMode.TRACEROUTE,
                status = DiagnosticStatus.RUNNING,
                logs = emptyList(),
                hops = emptyList(),
                errorMessage = null
            )
        }

        activeJob = viewModelScope.launch {
            try {
                repository.executeTraceroute(
                    rawTarget = target,
                    maxHops = 30,
                    timeoutSeconds = _uiState.value.timeoutSeconds
                ).collect { entry ->
                    _uiState.update { current ->
                        val updatedLogs = (current.logs + entry).takeLast(200)
                        when (entry) {
                            is DiagnosticLogEntry.HopEntry -> {
                                current.copy(
                                    logs = updatedLogs,
                                    hops = current.hops + entry.hop
                                )
                            }
                            is DiagnosticLogEntry.Error -> {
                                current.copy(
                                    logs = updatedLogs,
                                    errorMessage = entry.message,
                                    status = DiagnosticStatus.ERROR
                                )
                            }
                            else -> current.copy(logs = updatedLogs)
                        }
                    }
                }

                _uiState.update { current ->
                    if (current.status == DiagnosticStatus.RUNNING) {
                        current.copy(status = DiagnosticStatus.COMPLETED)
                    } else current
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        status = DiagnosticStatus.CANCELLED,
                        logs = it.logs + DiagnosticLogEntry.Output("Трассировка прервана.")
                    )
                }
            }
        }
    }

    fun stopDiagnostic() {
        activeJob?.cancel()
        activeJob = null
        if (_uiState.value.status == DiagnosticStatus.RUNNING) {
            _uiState.update { it.copy(status = DiagnosticStatus.CANCELLED) }
        }
    }

    fun clearLogs() {
        _uiState.update {
            it.copy(
                logs = emptyList(),
                pingPackets = emptyList(),
                pingSummary = null,
                hops = emptyList(),
                status = DiagnosticStatus.IDLE
            )
        }
    }
}
