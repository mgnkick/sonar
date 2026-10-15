package com.wifipulse.analyzer

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wifipulse.analyzer.data.repository.WifiRepository
import com.wifipulse.analyzer.ui.screens.SmartAuditScreen
import com.wifipulse.analyzer.ui.screens.ConnectionInfoScreen
import com.wifipulse.analyzer.ui.screens.DashboardScreen
import com.wifipulse.analyzer.ui.screens.PermissionsScreen
import com.wifipulse.analyzer.ui.screens.SignalAnalysisScreen
import com.wifipulse.analyzer.ui.theme.WiFiPulseTheme
import com.wifipulse.analyzer.ui.viewmodel.WifiViewModel

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Dashboard : Screen("dashboard", "Сканер (Mesh)", Icons.Default.Wifi)
    object SmartAudit : Screen("audit", "Смарт-Аудит", Icons.Default.Shield)
    object Diagnostics : Screen("diagnostics", "Диагностика", Icons.Default.Speed)
    object SignalAnalysis : Screen("signal", "Спектр/Инфо", Icons.Default.Assessment)
}

class MainActivity : ComponentActivity() {

    private lateinit var repository: WifiRepository
    private var hasPermissionsState by mutableStateOf(false)

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
        checkPermissions()

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
                            darkTheme = darkTheme,
                            onToggleTheme = {
                                darkTheme = !darkTheme
                                getSharedPreferences("wifi_pulse_settings", MODE_PRIVATE).edit().putBoolean("dark_theme", darkTheme).apply()
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        checkPermissions()
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
}

@Composable
fun MainAppContent(
    viewModel: WifiViewModel,
    uiState: com.wifipulse.analyzer.ui.viewmodel.WifiUiState,
    darkTheme: Boolean,
    onToggleTheme: () -> Unit
) {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Dashboard) }
    var showDonationDialog by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current
    val uriHandler = LocalUriHandler.current

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

    val diagnosticViewModel: com.wifipulse.analyzer.ui.viewmodel.DiagnosticViewModel = viewModel()
    val diagnosticState by diagnosticViewModel.uiState.collectAsState()

    val navItems = listOf(
        Screen.Dashboard,
        Screen.SmartAudit,
        Screen.Diagnostics,
        Screen.SignalAnalysis
    )

    if (showDonationDialog) {
        AlertDialog(
            onDismissRequest = { showDonationDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Paid,
                    contentDescription = null,
                    tint = Color(0xFFFFC857),
                    modifier = Modifier.size(30.dp)
                )
            },
            title = {
                Text(
                    text = "Поддержать WiFi Pulse",
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Если приложение оказалось полезным, можно поддержать его донатом в рублях РФ на карту Сбербанка.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Карта Сбербанка",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            Color(0xFFFFC857).copy(alpha = 0.55f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "5336 6903 0435 1846",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                            )
                            IconButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString("5336690304351846"))
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Скопировать номер карты",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                    Text(
                        text = "Нажмите на номер, чтобы скопировать",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Связь по электронной почте",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "mgn.kick@gmail.com",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            textDecoration = TextDecoration.Underline
                        ),
                        modifier = Modifier.clickable {
                            uriHandler.openUri("mailto:mgn.kick@gmail.com")
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showDonationDialog = false }) {
                    Text("Готово")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString("5336690304351846"))
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Копировать карту")
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurface
        )
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
                    onClick = { showDonationDialog = true },
                    modifier = Modifier
                        .size(40.dp)
                        .border(
                            width = 1.dp,
                            color = Color(0xFFFFC857).copy(alpha = 0.8f),
                            shape = RoundedCornerShape(50)
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.Paid,
                        contentDescription = "Поддержать проект донатом",
                        tint = Color(0xFFFFC857)
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
                    onRefreshNetworks = { viewModel.triggerScan() }
                )
                is Screen.SmartAudit -> SmartAuditScreen(
                    report = uiState.auditReport,
                    isScanning = uiState.isScanning,
                    onRefreshAudit = { viewModel.triggerScan() },
                    connectionDetails = uiState.connectionDetails,
                    visibleNetworkCount = uiState.networks.size,
                    isRefreshingNetworks = uiState.isScanning,
                    onRefreshNetworks = { viewModel.triggerScan() },
                    lastScanTimestamp = uiState.lastScanTimestamp
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
                    lastScanTimestamp = uiState.lastScanTimestamp
                )
                is Screen.SignalAnalysis -> SignalAnalysisScreen(
                    state = uiState,
                    onSelectNetwork = { viewModel.selectNetworkForAnalysis(it) },
                    visibleNetworkCount = uiState.networks.size,
                    isRefreshingNetworks = uiState.isScanning,
                    onRefreshNetworks = { viewModel.triggerScan() },
                    lastScanTimestamp = uiState.lastScanTimestamp
                )
            }
        }
    }
}
