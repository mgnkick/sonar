package com.wifipulse.analyzer.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
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
    val packetSizeBytes: Int = 32,
    val timeoutSeconds: Int = 2,
    val logs: List<DiagnosticLogEntry> = emptyList(),
    val pingPackets: List<PingPacket> = emptyList(),
    val pingSummary: PingSummary? = null,
    val hops: List<TracerouteHop> = emptyList(),
    val errorMessage: String? = null
)

class DiagnosticViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val repository = NetworkDiagnosticRepository()
    private val consoleStore = com.wifipulse.analyzer.data.repository.DiagnosticConsoleStore(application)
    private val restoredLogs = consoleStore.readAll()

    private val _uiState = MutableStateFlow(DiagnosticUiState(
        logs = restoredLogs,
        pingPackets = restoredLogs.filterIsInstance<DiagnosticLogEntry.PingEntry>().map { it.packet },
        pingSummary = restoredLogs.filterIsInstance<DiagnosticLogEntry.Summary>().lastOrNull()?.summary,
        hops = restoredLogs.filterIsInstance<DiagnosticLogEntry.HopEntry>().map { it.hop }.groupBy { it.hopNumber }.values.map { it.last() }.sortedBy { it.hopNumber }.takeLast(30)
    ))
    val uiState: StateFlow<DiagnosticUiState> = _uiState.asStateFlow()

    private var activeJob: Job? = null

    private fun append(entry: DiagnosticLogEntry) {
        consoleStore.append(entry)
        _uiState.update { current ->
            val hops = if (entry is DiagnosticLogEntry.HopEntry) (current.hops.filterNot { it.hopNumber == entry.hop.hopNumber } + entry.hop).sortedBy { it.hopNumber } else current.hops
            current.copy(logs = current.logs + entry, hops = hops)
        }
    }

    fun setTarget(target: String) {
        _uiState.update { it.copy(target = target, errorMessage = null) }
    }

    fun setPacketCount(count: Int) {
        _uiState.update { it.copy(packetCount = count) }
    }

    fun setPacketSizeBytes(size: Int) {
        _uiState.update { it.copy(packetSizeBytes = size.coerceIn(8, 1400)) }
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
                    timeoutSeconds = _uiState.value.timeoutSeconds,
                    packetSizeBytes = _uiState.value.packetSizeBytes
                ).collect { entry ->
                    consoleStore.append(entry)
                    _uiState.update { current ->
                        val updatedLogs = current.logs + entry
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
                val message = if (e is kotlinx.coroutines.CancellationException) "Операция остановлена." else "Ошибка выполнения: ${e.message ?: "неизвестная ошибка"}"
                _uiState.update {
                    it.copy(
                        status = if (e is kotlinx.coroutines.CancellationException) DiagnosticStatus.CANCELLED else DiagnosticStatus.ERROR,
                    )
                }
                append(DiagnosticLogEntry.Output(message))
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
                hops = emptyList(),
                errorMessage = null
            )
        }

        activeJob = viewModelScope.launch {
            try {
                repository.executeTsapTraceroute(
                    rawTarget = target,
                    maxHops = 30,
                    timeoutSeconds = _uiState.value.timeoutSeconds
                ).collect { entry ->
                    consoleStore.append(entry)
                    _uiState.update { current ->
                        val updatedLogs = current.logs + entry
                        when (entry) {
                            is DiagnosticLogEntry.HopEntry -> {
                                current.copy(
                                    logs = updatedLogs,
                                    hops = (current.hops.filterNot { it.hopNumber == entry.hop.hopNumber } + entry.hop).sortedBy { it.hopNumber }
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
                _uiState.update { it.copy(status = if (e is kotlinx.coroutines.CancellationException) DiagnosticStatus.CANCELLED else DiagnosticStatus.ERROR) }
                append(DiagnosticLogEntry.Output(if (e is kotlinx.coroutines.CancellationException) "Трассировка остановлена." else "Ошибка трассировки: ${e.message ?: "неизвестная ошибка"}"))
            }
        }
    }

    fun startGuidedDiagnostics(gateway: String, dns1: String, dns2: String) {
        stopDiagnostic()
        append(DiagnosticLogEntry.Output("Подготовка пошаговой диагностики…", isHighlight = true))
        _uiState.update {
            it.copy(
                mode = DiagnosticMode.PING,
                status = DiagnosticStatus.RUNNING,
                pingPackets = emptyList(),
                pingSummary = null,
                hops = emptyList(),
                errorMessage = null
            )
        }
        activeJob = viewModelScope.launch {
            try {
                repository.runGuidedDiagnostics(gateway, dns1, dns2) { line ->
                    append(DiagnosticLogEntry.Output(line, line.startsWith("===") || line.startsWith("[4/4]")))
                }
                _uiState.update { current ->
                    current.copy(status = DiagnosticStatus.COMPLETED)
                }
            } catch (e: Exception) {
                    _uiState.update { it.copy(errorMessage = e.message, status = DiagnosticStatus.ERROR) }
                    append(DiagnosticLogEntry.Error("Диагностика завершилась с ошибкой: ${e.message ?: "неизвестная ошибка"}"))
            }
        }
    }

    fun stopDiagnostic() {
        activeJob?.cancel()
        activeJob = null
        val wasRunning = _uiState.value.status == DiagnosticStatus.RUNNING
        if (wasRunning) {
            _uiState.update { it.copy(status = DiagnosticStatus.CANCELLED) }
            append(DiagnosticLogEntry.Output("Операция остановлена пользователем."))
        }
    }

    fun clearLogs() {
        consoleStore.clear()
        _uiState.update {
            it.copy(
                logs = emptyList(),
                pingPackets = emptyList(),
                pingSummary = null,
                hops = emptyList(),
                status = if (activeJob?.isActive == true) DiagnosticStatus.RUNNING else DiagnosticStatus.IDLE
            )
        }
    }
}
