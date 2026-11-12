package com.wifipulse.analyzer

import com.wifipulse.analyzer.ui.components.Text

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.CompositionLocalProvider
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
import com.wifipulse.analyzer.data.repository.OpenNetworkLogRepository
import com.wifipulse.analyzer.ui.screens.ConnectionHistoryScreen
import com.wifipulse.analyzer.ui.screens.SmartAuditScreen
import com.wifipulse.analyzer.ui.screens.ConnectionInfoScreen
import com.wifipulse.analyzer.ui.screens.DashboardScreen
import com.wifipulse.analyzer.ui.screens.PermissionsScreen
import com.wifipulse.analyzer.ui.screens.SignalAnalysisScreen
import com.wifipulse.analyzer.ui.screens.ExperimentalHeatmapTab
import com.wifipulse.analyzer.ui.screens.AboutProgramDialog
import com.wifipulse.analyzer.ui.theme.WiFiPulseTheme
import com.wifipulse.analyzer.ui.viewmodel.WifiViewModel
import com.wifipulse.analyzer.ui.components.AppLanguage
import com.wifipulse.analyzer.ui.components.LocalAppLanguage
import com.wifipulse.analyzer.ui.components.appUiText
import com.wifipulse.analyzer.ui.components.LocalHelpMode
import com.wifipulse.analyzer.ui.components.LocalHelpToggle

@Composable
fun AboutIconButton(onClick: () -> Unit) {
    androidx.compose.foundation.layout.Row {
        IconButton(onClick = LocalHelpToggle.current, modifier = Modifier.size(34.dp)) {
            Icon(
                Icons.Default.QuestionMark,
                contentDescription = appUiText("Режим подсказок", LocalAppLanguage.current),
                tint = if (LocalHelpMode.current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onClick, modifier = Modifier.size(34.dp)) {
            Icon(Icons.Outlined.Info, contentDescription = appUiText("О программе", LocalAppLanguage.current))
        }
    }
}

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Dashboard : Screen("dashboard", "Сканер (Mesh)", Icons.Default.Wifi)
    object SmartAudit : Screen("audit", "Смарт-Аудит", Icons.Default.Shield)
    object Diagnostics : Screen("diagnostics", "Диагностика", Icons.Default.Speed)
    object SignalAnalysis : Screen("signal", "Спектр/Инфо", Icons.Default.Assessment)
    object CoverageMap : Screen("coverage-map", "Карта", Icons.Default.Map)
    object History : Screen("history", "История", Icons.Default.History)
}

class MainActivity : ComponentActivity() {

    private lateinit var repository: WifiRepository
    private lateinit var connectionHistoryRepository: ConnectionHistoryRepository
    private lateinit var openNetworkLogRepository: OpenNetworkLogRepository
    private var hasPermissionsState by mutableStateOf(false)
    private var isBackgroundMonitoringState by mutableStateOf(false)
    private var initialPermissionFlowActive = false

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (initialPermissionFlowActive) initialPermissionFlowActive = false
        if (isBackgroundMonitorStartPending()) {
            if (granted) {
                completePendingBackgroundMonitorStart()
            } else {
                setBackgroundMonitorStartPending(false)
                Toast.makeText(
                    this,
                    localized("Для отображения мониторинга в системной шторке разрешите уведомления WiFi Pulse."),
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

        hasPermissionsState = fineLocationGranted && nearbyGranted
        if (initialPermissionFlowActive) requestInitialNotificationPermission()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = WifiRepository(this)
        connectionHistoryRepository = ConnectionHistoryRepository(this)
        openNetworkLogRepository = OpenNetworkLogRepository(this)
        checkPermissions()
        isBackgroundMonitoringState = getSharedPreferences(
            WifiMonitorService.PREFERENCES, MODE_PRIVATE
        ).getBoolean(WifiMonitorService.KEY_RUNNING, false)

        setContent {
            var darkTheme by remember { mutableStateOf(getSharedPreferences("wifi_pulse_settings", MODE_PRIVATE).getBoolean("dark_theme", true)) }
            var appLanguage by remember {
                mutableStateOf(
                    if (getSharedPreferences("wifi_pulse_settings", MODE_PRIVATE).getString("app_language", "ru") == "en") AppLanguage.EN else AppLanguage.RU
                )
            }
            WiFiPulseTheme(darkTheme = darkTheme) {
                CompositionLocalProvider(LocalAppLanguage provides appLanguage) {
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

                    LaunchedEffect(Unit) { requestInitialPermissionsIfNeeded() }

                    LaunchedEffect(hasPermissionsState) {
                        if (hasPermissionsState) {
                            viewModel.onPermissionsGranted()
                        }
                    }

                    if (!hasPermissionsState) {
                        PermissionsScreen(
                            appLanguage = appLanguage,
                            onToggleLanguage = {
                                appLanguage = if (appLanguage == AppLanguage.RU) AppLanguage.EN else AppLanguage.RU
                                getSharedPreferences("wifi_pulse_settings", MODE_PRIVATE).edit()
                                    .putString("app_language", if (appLanguage == AppLanguage.EN) "en" else "ru").apply()
                            },
                            onRequestPermissions = { requestPermissions() }
                        )
                    } else {
                        MainAppContent(
                            viewModel = viewModel,
                            uiState = uiState,
                            connectionHistoryRepository = connectionHistoryRepository,
                            openNetworkLogRepository = openNetworkLogRepository,
                            darkTheme = darkTheme,
                            appLanguage = appLanguage,
                            onToggleLanguage = {
                                appLanguage = if (appLanguage == AppLanguage.RU) AppLanguage.EN else AppLanguage.RU
                                getSharedPreferences("wifi_pulse_settings", MODE_PRIVATE).edit()
                                    .putString("app_language", if (appLanguage == AppLanguage.EN) "en" else "ru").apply()
                            },
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
                localized("Не удалось запустить фоновый мониторинг: ${e.message ?: "ошибка запуска"}"),
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

        hasPermissionsState = fineLocation && nearby
    }

    private fun localized(value: String): String {
        val lang = if (getSharedPreferences("wifi_pulse_settings", MODE_PRIVATE)
                .getString("app_language", "ru") == "en") AppLanguage.EN else AppLanguage.RU
        return appUiText(value, lang)
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

    private fun requestInitialPermissionsIfNeeded() {
        val settings = getSharedPreferences("wifi_pulse_settings", MODE_PRIVATE)
        if (settings.getBoolean(KEY_INITIAL_PERMISSION_REQUESTED, false)) return
        settings.edit().putBoolean(KEY_INITIAL_PERMISSION_REQUESTED, true).apply()
        initialPermissionFlowActive = true
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val nearby = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED
        if (!fine || !nearby) {
            requestPermissions()
        } else {
            requestInitialNotificationPermission()
        }
    }

    private fun requestInitialNotificationPermission() {
        if (!initialPermissionFlowActive) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            initialPermissionFlowActive = false
        }
    }

    private companion object {
        const val KEY_PENDING_BACKGROUND_MONITOR_START = "background_monitor_start_pending"
        const val KEY_INITIAL_PERMISSION_REQUESTED = "initial_runtime_permission_requested"
    }
}

@Composable
fun MainAppContent(
    viewModel: WifiViewModel,
    uiState: com.wifipulse.analyzer.ui.viewmodel.WifiUiState,
    connectionHistoryRepository: ConnectionHistoryRepository,
    openNetworkLogRepository: OpenNetworkLogRepository,
    darkTheme: Boolean,
    appLanguage: AppLanguage,
    onToggleLanguage: () -> Unit,
    onToggleTheme: () -> Unit,
    isBackgroundMonitoring: Boolean,
    onToggleBackgroundMonitoring: () -> Unit
) {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Dashboard) }
    var showAbout by remember { mutableStateOf(false) }
    var showQuickSettings by remember { mutableStateOf(false) }
    val connectionHistory by connectionHistoryRepository.entries.collectAsState()
    val openNetworkHistory by openNetworkLogRepository.entries.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    var helpMode by remember { mutableStateOf(false) }

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

    val locationForHistory = remember(uiState.connectionDetails, uiState.networks) {
        recentLocationForHistory(context)
    }
    LaunchedEffect(uiState.connectionDetails, uiState.networks) {
        connectionHistoryRepository.observeConnection(uiState.connectionDetails, uiState.networks, locationForHistory)
        openNetworkLogRepository.observeScan(uiState.networks)
    }

    val diagnosticViewModel: com.wifipulse.analyzer.ui.viewmodel.DiagnosticViewModel = viewModel()
    val diagnosticState by diagnosticViewModel.uiState.collectAsState()

    val navItems = listOf(
        Screen.Dashboard,
        Screen.SmartAudit,
        Screen.Diagnostics,
        Screen.SignalAnalysis,
        Screen.CoverageMap,
        Screen.History
    )

    if (showAbout) {
        AboutProgramDialog(onDismiss = { showAbout = false })
    }

    CompositionLocalProvider(
        LocalHelpMode provides helpMode,
        LocalHelpToggle provides { helpMode = !helpMode }
    ) {
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
                Box {
                    IconButton(onClick = { showQuickSettings = true }, modifier = Modifier.size(42.dp)) {
                        Icon(Icons.Default.MoreVert, contentDescription = appUiText("Язык и тема", appLanguage), tint = MaterialTheme.colorScheme.primary)
                    }
                    DropdownMenu(expanded = showQuickSettings, onDismissRequest = { showQuickSettings = false }) {
                        DropdownMenuItem(
                            text = { Text("Язык: ${if (appLanguage == AppLanguage.RU) "Русский" else "English"}") },
                            leadingIcon = { Icon(Icons.Default.Language, contentDescription = null) },
                            onClick = { showQuickSettings = false; onToggleLanguage() }
                        )
                        DropdownMenuItem(
                            text = { Text(if (darkTheme) "Светлая тема" else "Тёмная тема") },
                            leadingIcon = { Icon(if (darkTheme) Icons.Default.LightMode else Icons.Default.DarkMode, contentDescription = null) },
                            onClick = { showQuickSettings = false; onToggleTheme() }
                        )
                    }
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
                    onSelectSignalTrackingBssid = { viewModel.selectSignalTrackingBssid(it) },
                    onOpenAbout = { showAbout = true }
                )
                is Screen.History -> ConnectionHistoryScreen(
                    entries = connectionHistory,
                    openNetworks = openNetworkHistory,
                    onDeleteConnection = { connectionHistoryRepository.deleteEntry(it) },
                    onClearConnections = { connectionHistoryRepository.clearHistory() },
                    onClearOpenNetworks = { openNetworkLogRepository.clear() },
                    onGetPassword = { connectionHistoryRepository.getWifiPassword(it) },
                    onSavePassword = { id, password ->
                        runCatching { connectionHistoryRepository.saveWifiPassword(id, password) }
                            .onFailure { Toast.makeText(context, "Не удалось сохранить пароль: ${it.message ?: "ошибка"}", Toast.LENGTH_LONG).show() }
                    },
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
}

/** Returns only a recent, already-known fix; the app does not start location updates or track in the background. */
private fun recentLocationForHistory(context: android.content.Context): android.location.Location? {
    val fineGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    val coarseGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    if (!fineGranted && !coarseGranted) return null
    val manager = context.getSystemService(android.content.Context.LOCATION_SERVICE) as? android.location.LocationManager ?: return null
    val now = System.currentTimeMillis()
    return listOf(
        android.location.LocationManager.GPS_PROVIDER,
        android.location.LocationManager.NETWORK_PROVIDER,
        android.location.LocationManager.PASSIVE_PROVIDER
    ).mapNotNull { provider ->
        runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
    }.filter { location ->
        val age = now - location.time
        age in 0..15.minutesInMillis
    }.maxByOrNull { it.time }
}

private val Int.minutesInMillis: Long get() = this * 60_000L
