package com.wifipulse.analyzer

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wifipulse.analyzer.data.repository.WifiRepository
import com.wifipulse.analyzer.data.repository.ConnectionHistoryRepository
import com.wifipulse.analyzer.ui.screens.ConnectionHistoryDialog
import com.wifipulse.analyzer.ui.screens.SmartAuditScreen
import com.wifipulse.analyzer.ui.screens.ConnectionInfoScreen
import com.wifipulse.analyzer.ui.screens.DashboardScreen
import com.wifipulse.analyzer.ui.screens.PermissionsScreen
import com.wifipulse.analyzer.ui.screens.SignalAnalysisScreen
import com.wifipulse.analyzer.ui.screens.ExperimentalHeatmapTab
import com.wifipulse.analyzer.ui.screens.AboutProgramDialog
import com.wifipulse.analyzer.ui.theme.WiFiPulseTheme
import com.wifipulse.analyzer.ui.viewmodel.WifiViewModel

@Composable
fun AboutIconButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(Icons.Default.QuestionMark, contentDescription = "О программе")
    }
}

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Dashboard : Screen("dashboard", "Сканер (Mesh)", Icons.Default.Wifi)
    object SmartAudit : Screen("audit", "Смарт-Аудит", Icons.Default.Shield)
    object Diagnostics : Screen("diagnostics", "Диагностика", Icons.Default.Speed)
    object SignalAnalysis : Screen("signal", "Спектр/Инфо", Icons.Default.Assessment)
    object CoverageMap : Screen("coverage-map", "Карта", Icons.Default.Map)
}

class MainActivity : ComponentActivity() {

    private lateinit var repository: WifiRepository
    private lateinit var connectionHistoryRepository: ConnectionHistoryRepository
    private var hasPermissionsState by mutableStateOf(false)
    private var isBackgroundMonitoringState by mutableStateOf(false)

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (isBackgroundMonitorStartPending()) {
            if (granted) {
                completePendingBackgroundMonitorStart()
            } else {
                setBackgroundMonitorStartPending(false)
                Toast.makeText(
                    this,
                    "Для отображения мониторинга в системной шторке разрешите уведомления WiFi Pulse.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        val nearbyGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions[Manifest.permission.NEARBY_WIFI_DEVICES] ?: true
        } else true

        hasPermissionsState = (fineLocationGranted || coarseLocationGranted) && nearbyGranted
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = WifiRepository(this)
        connectionHistoryRepository = ConnectionHistoryRepository(this)
        checkPermissions()
        isBackgroundMonitoringState = getSharedPreferences(
            WifiMonitorService.PREFERENCES, MODE_PRIVATE
        ).getBoolean(WifiMonitorService.KEY_RUNNING, false)

        setContent {
            var darkTheme by remember { mutableStateOf(getSharedPreferences("wifi_pulse_settings", MODE_PRIVATE).getBoolean("dark_theme", true)) }
            WiFiPulseTheme(darkTheme = darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val viewModel: WifiViewModel = viewModel(
                        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                            @Suppress("UNCHECKED_CAST")
                            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                                return WifiViewModel(repository) as T
                            }
                        }
                    )

                    val uiState by viewModel.uiState.collectAsState()

                    LaunchedEffect(hasPermissionsState) {
                        if (hasPermissionsState) {
                            viewModel.onPermissionsGranted()
                        }
                    }

                    if (!hasPermissionsState) {
                        PermissionsScreen(
                            onRequestPermissions = { requestPermissions() }
                        )
                    } else {
                        MainAppContent(
                            viewModel = viewModel,
                            uiState = uiState,
                            connectionHistoryRepository = connectionHistoryRepository,
                            darkTheme = darkTheme,
                            onToggleTheme = {
                                darkTheme = !darkTheme
                                getSharedPreferences("wifi_pulse_settings", MODE_PRIVATE).edit().putBoolean("dark_theme", darkTheme).apply()
                            },
                            isBackgroundMonitoring = isBackgroundMonitoringState,
                            onToggleBackgroundMonitoring = { toggleBackgroundMonitoring() }
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        checkPermissions()
        isBackgroundMonitoringState = getSharedPreferences(
            WifiMonitorService.PREFERENCES, MODE_PRIVATE
        ).getBoolean(WifiMonitorService.KEY_RUNNING, false)
        // A permission dialog can recreate the Activity. Resume the user's pending
        // start request as soon as Android reports the permission has been granted.
        completePendingBackgroundMonitorStart()
    }

    private fun toggleBackgroundMonitoring() {
        if (isBackgroundMonitoringState) {
            WifiMonitorService.stop(this)
            isBackgroundMonitoringState = false
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            setBackgroundMonitorStartPending(true)
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        startBackgroundMonitoring()
    }

    private fun completePendingBackgroundMonitorStart() {
        if (!isBackgroundMonitorStartPending()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        // Clear the marker before starting so an activity-result callback and
        // onResume cannot start the same foreground service twice.
        setBackgroundMonitorStartPending(false)
        startBackgroundMonitoring()
    }

    private fun isBackgroundMonitorStartPending(): Boolean = getSharedPreferences(
        WifiMonitorService.PREFERENCES, MODE_PRIVATE
    ).getBoolean(KEY_PENDING_BACKGROUND_MONITOR_START, false)

    private fun setBackgroundMonitorStartPending(pending: Boolean) {
        getSharedPreferences(WifiMonitorService.PREFERENCES, MODE_PRIVATE)
            .edit().putBoolean(KEY_PENDING_BACKGROUND_MONITOR_START, pending).apply()
    }

    private fun startBackgroundMonitoring() {
        try {
            WifiMonitorService.start(this)
            isBackgroundMonitoringState = true
        } catch (e: Exception) {
            isBackgroundMonitoringState = false
            Toast.makeText(
                this,
                "Не удалось запустить фоновый мониторинг: ${e.message ?: "ошибка запуска"}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun checkPermissions() {
        val fineLocation = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarseLocation = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val nearby = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED
        } else true

        hasPermissionsState = (fineLocation || coarseLocation) && nearby
    }

    private fun requestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }
        permissionLauncher.launch(permissions.toTypedArray())
    }

    private companion object {
        const val KEY_PENDING_BACKGROUND_MONITOR_START = "background_monitor_start_pending"
    }
}

@Composable
fun MainAppContent(
    viewModel: WifiViewModel,
    uiState: com.wifipulse.analyzer.ui.viewmodel.WifiUiState,
    connectionHistoryRepository: ConnectionHistoryRepository,
    darkTheme: Boolean,
    onToggleTheme: () -> Unit,
    isBackgroundMonitoring: Boolean,
    onToggleBackgroundMonitoring: () -> Unit
) {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Dashboard) }
    var showConnectionHistory by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    val connectionHistory by connectionHistoryRepository.entries.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    // Keep automatic scans active only while the app is visible. The periodic scan runs
    // independently of Compose rendering and never changes focus or the manual scan state.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> viewModel.startAutoRefresh()
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> viewModel.stopAutoRefresh()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            viewModel.startAutoRefresh()
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.stopAutoRefresh()
        }
    }

    LaunchedEffect(currentScreen) {
        viewModel.refreshConnectionDetails()
        viewModel.refreshAudit()
    }

    LaunchedEffect(uiState.connectionDetails, uiState.networks) {
        connectionHistoryRepository.observeConnection(uiState.connectionDetails, uiState.networks)
    }

    val diagnosticViewModel: com.wifipulse.analyzer.ui.viewmodel.DiagnosticViewModel = viewModel()
    val diagnosticState by diagnosticViewModel.uiState.collectAsState()

    val navItems = listOf(
        Screen.Dashboard,
        Screen.SmartAudit,
        Screen.Diagnostics,
        Screen.SignalAnalysis,
        Screen.CoverageMap
    )

    if (showConnectionHistory) {
        ConnectionHistoryDialog(
            entries = connectionHistory,
            onDelete = { connectionHistoryRepository.deleteEntry(it) },
            onClear = { connectionHistoryRepository.clearHistory() },
            onGetPassword = { connectionHistoryRepository.getWifiPassword(it) },
            onSavePassword = { id, password ->
                runCatching { connectionHistoryRepository.saveWifiPassword(id, password) }
                    .onFailure { Toast.makeText(context, "Не удалось сохранить пароль: ${it.message ?: "ошибка"}", Toast.LENGTH_LONG).show() }
            },
            onDismiss = { showConnectionHistory = false }
        )
    }

    if (showAbout) {
        AboutProgramDialog(onDismiss = { showAbout = false })
    }

    Scaffold(
        bottomBar = {
            NavigationBar(
                modifier = Modifier.border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)
                ),
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                tonalElevation = 8.dp
            ) {
                navItems.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.title) },
                        label = { Text(screen.title, fontSize = 11.sp) },
                        selected = currentScreen == screen,
                        onClick = { currentScreen = screen },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = if (darkTheme) 0.16f else 0.10f),
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
                IconButton(
                    onClick = onToggleTheme,
                    modifier = Modifier
                        .padding(end = 4.dp)
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                            shape = RoundedCornerShape(12.dp)
                        )
                ) {
                    Icon(
                        imageVector = if (darkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                        contentDescription = if (darkTheme) "Включить светлую тему" else "Включить тёмную тему",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = if (darkTheme) listOf(
                            Color(0xFF070B14), Color(0xFF0A1421), Color(0xFF10101F)
                        ) else listOf(
                            Color(0xFFF2F7FB), Color(0xFFEAF7FA), Color(0xFFF4F0FC)
                        )
                    )
                )
                .padding(padding)
        ) {
            when (currentScreen) {
                is Screen.Dashboard -> DashboardScreen(
                    state = uiState,
                    onScanClick = { viewModel.triggerScan() },
                    onBandSelected = { viewModel.selectBand(it) },
                    onSearchChange = { viewModel.setSearchQuery(it) },
                    onSortChange = { viewModel.setSortOption(it) },
                    onToggleGroup = { viewModel.toggleGroupExpansion(it) },
                    onNetworkSelected = { viewModel.selectNetworkForAnalysis(it) },
                    onNavigateToAnalysis = { currentScreen = Screen.SignalAnalysis },
                    onRefreshNetworks = { viewModel.triggerScan() },
                    onOpenAbout = { showAbout = true }
                )
                is Screen.SmartAudit -> SmartAuditScreen(
                    report = uiState.auditReport,
                    isScanning = uiState.isScanning,
                    onRefreshAudit = { viewModel.triggerScan() },
                    connectionDetails = uiState.connectionDetails,
                    visibleNetworkCount = uiState.networks.size,
                    isRefreshingNetworks = uiState.isScanning,
                    onRefreshNetworks = { viewModel.triggerScan() },
                    lastScanTimestamp = uiState.lastScanTimestamp,
                    onOpenAbout = { showAbout = true }
                )
                is Screen.Diagnostics -> com.wifipulse.analyzer.ui.screens.DiagnosticScreen(
                    state = diagnosticState,
                    onTargetChange = { diagnosticViewModel.setTarget(it) },
                    onCountChange = { diagnosticViewModel.setPacketCount(it) },
                    onTimeoutChange = { diagnosticViewModel.setTimeoutSeconds(it) },
                    onPacketSizeChange = { diagnosticViewModel.setPacketSizeBytes(it) },
                    onStartPing = { diagnosticViewModel.startPing() },
                    onStartTraceroute = { diagnosticViewModel.startTraceroute() },
                    onStartGuidedDiagnostics = { diagnosticViewModel.startGuidedDiagnostics(uiState.connectionDetails.gateway, uiState.connectionDetails.dns1, uiState.connectionDetails.dns2) },
                    onStop = { diagnosticViewModel.stopDiagnostic() },
                    onClear = { diagnosticViewModel.clearLogs() },
                    connectionDetails = uiState.connectionDetails,
                    visibleNetworkCount = uiState.networks.size,
                    isRefreshingNetworks = uiState.isScanning,
                    onRefreshNetworks = { viewModel.triggerScan() },
                    lastScanTimestamp = uiState.lastScanTimestamp,
                    onOpenAbout = { showAbout = true }
                )
                is Screen.SignalAnalysis -> SignalAnalysisScreen(
                    state = uiState,
                    onSelectNetwork = { viewModel.selectNetworkForAnalysis(it) },
                    visibleNetworkCount = uiState.networks.size,
                    isRefreshingNetworks = uiState.isScanning,
                    onRefreshNetworks = { viewModel.triggerScan() },
                    lastScanTimestamp = uiState.lastScanTimestamp,
                    isBackgroundMonitoring = isBackgroundMonitoring,
                    onToggleBackgroundMonitoring = onToggleBackgroundMonitoring,
                    onOpenConnectionHistory = { showConnectionHistory = true },
                    onSelectSignalTrackingBssid = { viewModel.selectSignalTrackingBssid(it) },
                    onOpenAbout = { showAbout = true }
                )
                is Screen.CoverageMap -> ExperimentalHeatmapTab(
                    connectionDetails = uiState.connectionDetails,
                    onOpenAbout = { showAbout = true },
                    isRefreshingNetworks = uiState.isScanning,
                    onRefreshNetworks = { viewModel.triggerScan() }
                )
            }
        }
    }
}
