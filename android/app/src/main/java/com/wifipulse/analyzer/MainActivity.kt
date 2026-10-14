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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
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
            WiFiPulseTheme {
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
                            uiState = uiState
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
    uiState: com.wifipulse.analyzer.ui.viewmodel.WifiUiState
) {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Dashboard) }

    val diagnosticViewModel: com.wifipulse.analyzer.ui.viewmodel.DiagnosticViewModel = viewModel()
    val diagnosticState by diagnosticViewModel.uiState.collectAsState()

    val navItems = listOf(
        Screen.Dashboard,
        Screen.SmartAudit,
        Screen.Diagnostics,
        Screen.SignalAnalysis
    )

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                navItems.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.title) },
                        label = { Text(screen.title, fontSize = 11.sp) },
                        selected = currentScreen == screen,
                        onClick = { currentScreen = screen }
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (currentScreen) {
                is Screen.Dashboard -> DashboardScreen(
                    state = uiState,
                    onScanClick = { viewModel.triggerScan() },
                    onBandSelected = { viewModel.selectBand(it) },
                    onSearchChange = { viewModel.setSearchQuery(it) },
                    onSortChange = { viewModel.setSortOption(it) },
                    onToggleGroup = { viewModel.toggleGroupExpansion(it) },
                    onNetworkSelected = { viewModel.selectNetworkForAnalysis(it) },
                    onNavigateToAnalysis = { currentScreen = Screen.SignalAnalysis }
                )
                is Screen.SmartAudit -> SmartAuditScreen(
                    report = uiState.auditReport,
                    isScanning = uiState.isScanning,
                    onRefreshAudit = { viewModel.triggerScan() }
                )
                is Screen.Diagnostics -> com.wifipulse.analyzer.ui.screens.DiagnosticScreen(
                    state = diagnosticState,
                    onTargetChange = { diagnosticViewModel.setTarget(it) },
                    onCountChange = { diagnosticViewModel.setPacketCount(it) },
                    onTimeoutChange = { diagnosticViewModel.setTimeoutSeconds(it) },
                    onStartPing = { diagnosticViewModel.startPing() },
                    onStartTraceroute = { diagnosticViewModel.startTraceroute() },
                    onStop = { diagnosticViewModel.stopDiagnostic() },
                    onClear = { diagnosticViewModel.clearLogs() }
                )
                is Screen.SignalAnalysis -> SignalAnalysisScreen(
                    state = uiState,
                    onSelectNetwork = { viewModel.selectNetworkForAnalysis(it) }
                )
            }
        }
    }
}
