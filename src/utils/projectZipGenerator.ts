import JSZip from 'jszip';

export interface ProjectFile {
  path: string;
  name: string;
  content: string;
  language: string;
}

export const ANDROID_PROJECT_FILES: ProjectFile[] = [
  {
    path: '.github/workflows/build.yml',
    name: 'build.yml',
    language: 'yaml',
    content: `name: Android Build & APK Generation

on:
  push:
    branches: [ "main", "master" ]
  pull_request:
    branches: [ "main", "master" ]
  workflow_dispatch:

permissions:
  contents: read

jobs:
  build:
    name: Build APK with Gradle
    runs-on: ubuntu-latest

    steps:
      - name: Checkout repository
        uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '17'

      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v3

      - name: Grant execute permission for gradlew
        run: chmod +x ./gradlew

      - name: Assemble Debug APK
        run: ./gradlew assembleDebug --stacktrace

      - name: Find and Upload APK Artifact
        uses: actions/upload-artifact@v4
        with:
          name: wifipulse-debug-apk
          path: app/build/outputs/apk/debug/*.apk
          retention-days: 14
          if-no-files-found: error
`
  },
  {
    path: '.gitignore',
    name: '.gitignore',
    language: 'gitignore',
    content: `*.iml
.gradle
/local.properties
/.idea/caches
/.idea/libraries
/.idea/modules.xml
/.idea/workspace.xml
/.idea/navEditor.xml
/.idea/assetWizardSettings.xml
.DS_Store
/build
/captures
.externalNativeBuild
.cxx
*.apk
*.aab
output.json
**/.cxx/
**/build/
!gradle/wrapper/gradle-wrapper.jar
!gradle/wrapper/gradle-wrapper.properties
`
  },
  {
    path: 'settings.gradle.kts',
    name: 'settings.gradle.kts',
    language: 'kotlin',
    content: `pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\\\.android.*")
                includeGroupByRegex("com\\\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "WiFiPulse"
include(":app")
`
  },
  {
    path: 'build.gradle.kts',
    name: 'build.gradle.kts',
    language: 'kotlin',
    content: `// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.compose.compiler) apply false
}
`
  },
  {
    path: 'gradle/libs.versions.toml',
    name: 'libs.versions.toml',
    language: 'toml',
    content: `[versions]
agp = "8.3.2"
kotlin = "2.0.0"
coreKtx = "1.13.1"
lifecycleRuntimeKtx = "2.8.2"
activityCompose = "1.9.0"
composeBom = "2024.06.00"
navigationCompose = "2.7.7"
materialIconsExtended = "1.6.8"

[libraries]
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
androidx-lifecycle-runtime-ktx = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "lifecycleRuntimeKtx" }
androidx-lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycleRuntimeKtx" }
androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
androidx-ui = { group = "androidx.compose.ui", name = "ui" }
androidx-ui-graphics = { group = "androidx.compose.ui", name = "ui-graphics" }
androidx-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
androidx-material3 = { group = "androidx.compose.material3", name = "material3" }
androidx-material-icons-extended = { group = "androidx.compose.material", name = "material-icons-extended", version.ref = "materialIconsExtended" }
androidx-navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigationCompose" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
compose-compiler = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
`
  },
  {
    path: 'app/build.gradle.kts',
    name: 'app/build.gradle.kts',
    language: 'kotlin',
    content: `plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.wifipulse.analyzer"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.wifipulse.analyzer"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
}
`
  },
  {
    path: 'app/src/main/AndroidManifest.xml',
    name: 'AndroidManifest.xml',
    language: 'xml',
    content: `<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
    <uses-permission android:name="android.permission.CHANGE_WIFI_STATE" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
    <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
    <uses-permission android:name="android.permission.NEARBY_WIFI_DEVICES"
        android:usesPermissionFlags="neverForLocation"
        tools:targetApi="s" />

    <uses-feature android:name="android.hardware.wifi" android:required="true" />

    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="WiFi Pulse"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.WiFiPulse"
        tools:targetApi="31">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:label="WiFi Pulse"
            android:theme="@style/Theme.WiFiPulse"
            android:windowSoftInputMode="adjustResize">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
`
  },
  {
    path: 'app/src/main/java/com/wifipulse/analyzer/data/model/WifiModels.kt',
    name: 'WifiModels.kt',
    language: 'kotlin',
    content: `package com.wifipulse.analyzer.data.model

import kotlin.math.pow

enum class WifiBand(val displayName: String) {
    BAND_2_4_GHZ("2.4 ГГц"),
    BAND_5_GHZ("5 ГГц"),
    BAND_6_GHZ("6 ГГц"),
    UNKNOWN("Неизвестно")
}

enum class SecurityType(val displayName: String) {
    OPEN("Открытая"),
    WEP("WEP"),
    WPA_PSK("WPA-PSK"),
    WPA2_PSK("WPA2-PSK"),
    WPA3_SAE("WPA3-SAE"),
    WPA_WPA2_MIXED("WPA/WPA2"),
    WPA2_WPA3_MIXED("WPA2/WPA3"),
    ENTERPRISE("Enterprise (802.1X)")
}

data class RoamingSupport(
    val has80211k: Boolean = false,
    val has80211r: Boolean = false,
    val has80211v: Boolean = false
) {
    val isSupported: Boolean get() = has80211k || has80211r || has80211v
}

enum class NodeRole(val displayName: String, val badgeColor: Long) {
    MASTER_ROUTER("Основной роутер / Шлюз", 0xFF00E5FF),
    MESH_NODE("Mesh-узел (Сателлит)", 0xFF10B981),
    REPEATER_SUSPECT("Потенциальный репитер / Усилитель", 0xFFF59E0B),
    STANDALONE_AP("Точка доступа (Одиночная)", 0xFF64748B)
}

enum class NetworkTopologyType(val displayName: String, val badgeColor: Long) {
    STANDALONE("Одиночная точка", 0xFF64748B),
    MESH_NETWORK("Mesh-система", 0xFF10B981),
    ENTERPRISE_ROAMING("Корпоративный роуминг", 0xFF8B5CF6),
    REPEATER_DETECTED("Обнаружен репитер", 0xFFF59E0B)
}

data class BssIdNode(
    val bssid: String,
    val rssi: Int,
    val frequency: Int,
    val channel: Int,
    val band: WifiBand,
    val channelWidth: Int,
    val securityType: SecurityType,
    val capabilities: String,
    val vendor: String,
    val nodeRole: NodeRole,
    val roamingSupport: RoamingSupport,
    val timestamp: Long = System.currentTimeMillis()
) {
    val signalPercent: Int get() = ((rssi + 100).coerceIn(0, 50) * 2).coerceIn(0, 100)
    val signalQuality: SignalQuality get() = when {
        rssi >= -50 -> SignalQuality.EXCELLENT
        rssi >= -65 -> SignalQuality.GOOD
        rssi >= -75 -> SignalQuality.FAIR
        rssi >= -85 -> SignalQuality.POOR
        else -> SignalQuality.VERY_POOR
    }
    val estimatedDistanceMeters: Double get() {
        val exp = (27.55 - (20 * kotlin.math.log10(frequency.toDouble())) + kotlin.math.abs(rssi)) / 20.0
        return (10.0.pow(exp) * 10).toInt() / 10.0
    }
}

data class WifiNetworkGroup(
    val ssid: String,
    val nodes: List<BssIdNode>,
    val topologyType: NetworkTopologyType,
    val primaryVendor: String,
    val hasMixedVendors: Boolean,
    val isExpanded: Boolean = false
) {
    val bestNode: BssIdNode? get() = nodes.maxByOrNull { it.rssi }
    val bestRssi: Int get() = bestNode?.rssi ?: -100
    val bestBand: WifiBand get() = bestNode?.band ?: WifiBand.UNKNOWN
    val bestSecurity: SecurityType get() = bestNode?.securityType ?: SecurityType.OPEN
    val availableBands: Set<WifiBand> get() = nodes.map { it.band }.toSet()
    val totalNodesCount: Int get() = nodes.size
    val roamingSummary: RoamingSupport get() = RoamingSupport(
        has80211k = nodes.any { it.roamingSupport.has80211k },
        has80211r = nodes.any { it.roamingSupport.has80211r },
        has80211v = nodes.any { it.roamingSupport.has80211v }
    )
}

data class WifiNetwork(
    val ssid: String,
    val bssid: String,
    val rssi: Int,
    val frequency: Int,
    val channel: Int,
    val band: WifiBand,
    val channelWidth: Int,
    val securityType: SecurityType,
    val capabilities: String,
    val timestamp: Long = System.currentTimeMillis()
)

enum class SignalQuality(val label: String, val hexColor: Long) {
    EXCELLENT("Отличное", 0xFF22C55E),
    GOOD("Хорошее", 0xFF10B981),
    FAIR("Среднее", 0xFFF59E0B),
    POOR("Слабое", 0xFFF97316),
    VERY_POOR("Критическое", 0xFFEF4444)
}

data class WifiConnectionDetails(
    val isConnected: Boolean = false,
    val ssid: String = "",
    val bssid: String = "",
    val rssi: Int = 0,
    val ipAddress: String = "0.0.0.0",
    val gateway: String = "0.0.0.0",
    val subnetMask: String = "255.255.255.0",
    val dns1: String = "8.8.8.8",
    val dns2: String = "8.8.4.4",
    val linkSpeedMbps: Int = 0,
    val txLinkSpeedMbps: Int = 0,
    val rxLinkSpeedMbps: Int = 0,
    val frequency: Int = 0,
    val standard: String = "Wi-Fi 5 (802.11ac)",
    val macAddress: String = "02:00:00:00:00:00",
    val isHidden: Boolean = false
)

data class ChannelRating(
    val channel: Int,
    val frequency: Int,
    val band: WifiBand,
    val networkCount: Int,
    val score: Int,
    val isRecommended: Boolean,
    val overlappingNetworks: List<String> = emptyList()
)

data class SignalHistoryPoint(val timestamp: Long, val rssi: Int)
`
  },
  {
    path: 'app/src/main/java/com/wifipulse/analyzer/data/repository/WifiTopologyAnalyzer.kt',
    name: 'WifiTopologyAnalyzer.kt',
    language: 'kotlin',
    content: `package com.wifipulse.analyzer.data.repository

import com.wifipulse.analyzer.data.model.*

object WifiTopologyAnalyzer {
    private val OUI_DATABASE: Map<String, String> = mapOf(
        "50:D4:F7" to "Keenetic",
        "28:28:5D" to "Keenetic",
        "E4:18:6B" to "Keenetic",
        "AC:84:C6" to "TP-Link",
        "C0:25:E9" to "TP-Link (Deco Mesh)",
        "28:EE:52" to "Xiaomi",
        "D4:61:9D" to "Xiaomi Mesh",
        "E4:38:83" to "ASUS",
        "2C:FD:A1" to "ASUS (AiMesh)",
        "CC:2D:21" to "Tenda",
        "00:0C:43" to "Tenda (Extender)",
        "48:8F:5A" to "MikroTik",
        "74:83:C2" to "Ubiquiti UniFi",
        "C4:AD:34" to "Huawei",
        "F8:B7:E2" to "Netgear (Orbi Mesh)"
    )

    fun resolveVendor(bssid: String): String {
        val clean = bssid.uppercase().replace("-", ":")
        if (clean.length >= 8) {
            val prefix = clean.substring(0, 8)
            OUI_DATABASE[prefix]?.let { return it }
        }
        return "Неизвестный вендор (\${clean.take(8)})"
    }

    fun parseRoamingSupport(capabilities: String): RoamingSupport {
        val upper = capabilities.uppercase()
        val hasR = upper.contains("FT/") || upper.contains("[FT") || upper.contains("FAST-TRANSITION")
        val hasK = upper.contains("RRM") || upper.contains("RADIO-RESOURCE-MANAGEMENT") || upper.contains("[K]")
        val hasV = upper.contains("WNM") || upper.contains("BSS-TRANSITION") || upper.contains("[V]")
        return RoamingSupport(has80211k = hasK, has80211r = hasR, has80211v = hasV)
    }

    fun aggregateIntoGroups(networks: List<WifiNetwork>, previousExpandedSsids: Set<String> = emptySet()): List<WifiNetworkGroup> {
        val groupedBySsid = networks.groupBy { it.ssid }

        return groupedBySsid.map { (ssid, nets) ->
            val sortedNodes = nets.sortedByDescending { it.rssi }
            val first = sortedNodes.first()
            val primaryVendor = resolveVendor(first.bssid)
            val vendors = sortedNodes.map { resolveVendor(it.bssid) }
            val hasMixedVendors = vendors.distinct().size > 1

            val roamingPresent = sortedNodes.any { parseRoamingSupport(it.capabilities).isSupported }

            val topology = when {
                sortedNodes.size == 1 -> NetworkTopologyType.STANDALONE
                first.securityType == SecurityType.ENTERPRISE -> NetworkTopologyType.ENTERPRISE_ROAMING
                hasMixedVendors -> NetworkTopologyType.REPEATER_DETECTED
                else -> NetworkTopologyType.MESH_NETWORK
            }

            val nodes = sortedNodes.mapIndexed { index, net ->
                val nodeVendor = resolveVendor(net.bssid)
                val roaming = parseRoamingSupport(net.capabilities)
                val role = when {
                    sortedNodes.size == 1 -> NodeRole.STANDALONE_AP
                    index == 0 && topology != NetworkTopologyType.REPEATER_DETECTED -> NodeRole.MASTER_ROUTER
                    hasMixedVendors && nodeVendor != primaryVendor -> NodeRole.REPEATER_SUSPECT
                    topology == NetworkTopologyType.REPEATER_DETECTED && index > 0 -> NodeRole.REPEATER_SUSPECT
                    else -> NodeRole.MESH_NODE
                }
                BssIdNode(
                    bssid = net.bssid,
                    rssi = net.rssi,
                    frequency = net.frequency,
                    channel = net.channel,
                    band = net.band,
                    channelWidth = net.channelWidth,
                    securityType = net.securityType,
                    capabilities = net.capabilities,
                    vendor = nodeVendor,
                    nodeRole = role,
                    roamingSupport = roaming,
                    timestamp = net.timestamp
                )
            }

            WifiNetworkGroup(
                ssid = ssid,
                nodes = nodes,
                topologyType = topology,
                primaryVendor = primaryVendor,
                hasMixedVendors = hasMixedVendors,
                isExpanded = previousExpandedSsids.contains(ssid)
            )
        }.sortedByDescending { it.bestRssi }
    }
}
`
  },
  {
    path: 'app/src/main/java/com/wifipulse/analyzer/ui/viewmodel/WifiViewModel.kt',
    name: 'WifiViewModel.kt',
    language: 'kotlin',
    content: `package com.wifipulse.analyzer.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wifipulse.analyzer.data.model.*
import com.wifipulse.analyzer.data.repository.WifiRepository
import com.wifipulse.analyzer.data.repository.WifiTopologyAnalyzer
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class WifiUiState(
    val networks: List<WifiNetwork> = emptyList(),
    val filteredNetworks: List<WifiNetwork> = emptyList(),
    val networkGroups: List<WifiNetworkGroup> = emptyList(),
    val filteredGroups: List<WifiNetworkGroup> = emptyList(),
    val expandedSsids: Set<String> = emptySet(),
    val connectionDetails: WifiConnectionDetails = WifiConnectionDetails(),
    val isScanning: Boolean = false,
    val selectedBand: WifiBand? = null,
    val searchQuery: String = "",
    val selectedNetworkBssid: String? = null,
    val scanThrottleCooldownSeconds: Int = 0
)

class WifiViewModel(private val repository: WifiRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(WifiUiState())
    val uiState: StateFlow<WifiUiState> = _uiState.asStateFlow()

    init {
        refreshConnectionDetails()
        viewModelScope.launch {
            repository.observeScanResults().collect { scanResults ->
                _uiState.update { current ->
                    val groups = WifiTopologyAnalyzer.aggregateIntoGroups(scanResults, current.expandedSsids)
                    current.copy(
                        networks = scanResults,
                        filteredNetworks = scanResults,
                        networkGroups = groups,
                        filteredGroups = groups,
                        isScanning = false
                    )
                }
            }
        }
    }

    fun toggleGroupExpansion(ssid: String) {
        _uiState.update { current ->
            val updated = if (current.expandedSsids.contains(ssid)) current.expandedSsids - ssid else current.expandedSsids + ssid
            val updatedGroups = current.networkGroups.map { it.copy(isExpanded = updated.contains(it.ssid)) }
            current.copy(expandedSsids = updated, networkGroups = updatedGroups, filteredGroups = updatedGroups)
        }
    }

    fun triggerScan() {
        _uiState.update { it.copy(isScanning = true) }
        repository.startScan()
        refreshConnectionDetails()
    }

    fun refreshConnectionDetails() {
        _uiState.update { it.copy(connectionDetails = repository.getCurrentConnectionDetails()) }
    }
}
`
  },
  {
    path: 'app/src/main/java/com/wifipulse/analyzer/data/model/DiagnosticModels.kt',
    name: 'DiagnosticModels.kt',
    language: 'kotlin',
    content: `package com.wifipulse.analyzer.data.model

enum class DiagnosticMode { PING, TRACEROUTE }
enum class DiagnosticStatus { IDLE, RUNNING, COMPLETED, CANCELLED, ERROR }

data class PingPacket(
    val sequence: Int,
    val bytes: Int,
    val ip: String,
    val ttl: Int,
    val rttMs: Float,
    val isTimeout: Boolean = false,
    val rawText: String = ""
)

data class PingSummary(
    val packetsTransmitted: Int = 0,
    val packetsReceived: Int = 0,
    val packetLossPercent: Float = 0f,
    val minRttMs: Float = 0f,
    val avgRttMs: Float = 0f,
    val maxRttMs: Float = 0f,
    val mdevRttMs: Float = 0f
)

data class TracerouteHop(
    val hopNumber: Int,
    val ip: String? = null,
    val hostName: String? = null,
    val rttMs: Float? = null,
    val isTimeout: Boolean = false,
    val isTargetReached: Boolean = false,
    val rawOutput: String = ""
)

sealed class DiagnosticLogEntry {
    data class Output(val text: String, val isHighlight: Boolean = false) : DiagnosticLogEntry()
    data class Error(val message: String) : DiagnosticLogEntry()
    data class HopEntry(val hop: TracerouteHop) : DiagnosticLogEntry()
    data class PingEntry(val packet: PingPacket) : DiagnosticLogEntry()
    data class Summary(val summary: PingSummary) : DiagnosticLogEntry()
}
`
  },
  {
    path: 'app/src/main/java/com/wifipulse/analyzer/data/repository/NetworkDiagnosticRepository.kt',
    name: 'NetworkDiagnosticRepository.kt',
    language: 'kotlin',
    content: `package com.wifipulse.analyzer.data.repository

import com.wifipulse.analyzer.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.util.regex.Pattern

class NetworkDiagnosticRepository {
    private val pingReplyPattern = Pattern.compile("^(\\\\d+)\\\\s+bytes from\\\\s+([^:]+):\\\\s+icmp_seq=(\\\\d+)\\\\s+ttl=(\\\\d+)\\\\s+time=([\\\\d\\\\.]+)\\\\s*ms", Pattern.CASE_INSENSITIVE)
    private val pingTtlExceededPattern = Pattern.compile("From\\\\s+([^ :]+).*Time to live exceeded", Pattern.CASE_INSENSITIVE)
    private val pingStatsPattern = Pattern.compile("(\\\\d+)\\\\s+packets transmitted,\\\\s+(\\\\d+)(?:\\\\s+packets)?\\\\s+received,\\\\s+([\\\\d\\\\.]+)%\\\\s+packet loss", Pattern.CASE_INSENSITIVE)

    fun cleanHost(input: String): String = input.trim().replace("https://", "").replace("http://", "").split("/")[0].split(":")[0].trim()

    fun executePing(rawTarget: String, count: Int = 4, timeoutSeconds: Int = 2): Flow<DiagnosticLogEntry> = callbackFlow {
        val target = cleanHost(rawTarget)
        var process: Process? = null
        try {
            val cmd = mutableListOf("/system/bin/ping")
            if (count > 0) { cmd.add("-c"); cmd.add(count.toString()) }
            cmd.add("-W"); cmd.add(timeoutSeconds.toString())
            cmd.add(target)
            val proc = ProcessBuilder(cmd).redirectErrorStream(true).start()
            process = proc
            val reader = BufferedReader(InputStreamReader(proc.inputStream))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val l = line?.trim() ?: continue
                val m = pingReplyPattern.matcher(l)
                if (m.find()) {
                    trySend(DiagnosticLogEntry.PingEntry(PingPacket(m.group(3)!!.toInt(), m.group(1)!!.toInt(), m.group(2)!!, m.group(4)!!.toInt(), m.group(5)!!.toFloat(), false, l)))
                } else {
                    trySend(DiagnosticLogEntry.Output(l))
                }
            }
            proc.waitFor()
            close()
        } catch (e: Exception) {
            trySend(DiagnosticLogEntry.Error("Процесс остановлен: \${e.message}"))
            close()
        }
        awaitClose { process?.destroy() }
    }.flowOn(Dispatchers.IO)

    fun executeTraceroute(rawTarget: String, maxHops: Int = 30, timeoutSeconds: Int = 2): Flow<DiagnosticLogEntry> = flow {
        val target = cleanHost(rawTarget)
        for (ttl in 1..maxHops) {
            currentCoroutineContext().ensureActive()
            val cmd = listOf("/system/bin/ping", "-c", "1", "-W", timeoutSeconds.toString(), "-t", ttl.toString(), target)
            var proc: Process? = null
            try {
                proc = ProcessBuilder(cmd).redirectErrorStream(true).start()
                val reader = BufferedReader(InputStreamReader(proc.inputStream))
                var hopIp: String? = null
                var isReached = false
                var rtt = 0f
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val l = line ?: continue
                    val mExceeded = pingTtlExceededPattern.matcher(l)
                    if (mExceeded.find()) { hopIp = mExceeded.group(1)?.trim() }
                    val mReply = pingReplyPattern.matcher(l)
                    if (mReply.find()) { isReached = true; hopIp = mReply.group(2)?.trim(); rtt = mReply.group(5)?.toFloatOrNull() ?: 0f }
                }
                proc.waitFor()
                emit(DiagnosticLogEntry.HopEntry(TracerouteHop(ttl, hopIp, null, rtt, hopIp == null, isReached)))
                if (isReached) break
            } catch (e: Exception) {
                emit(DiagnosticLogEntry.HopEntry(TracerouteHop(ttl, null, null, null, true, false)))
            } finally {
                proc?.destroy()
            }
        }
    }.flowOn(Dispatchers.IO)
}
`
  },
  {
    path: 'app/src/main/java/com/wifipulse/analyzer/ui/viewmodel/DiagnosticViewModel.kt',
    name: 'DiagnosticViewModel.kt',
    language: 'kotlin',
    content: `package com.wifipulse.analyzer.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wifipulse.analyzer.data.model.*
import com.wifipulse.analyzer.data.repository.NetworkDiagnosticRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class DiagnosticUiState(
    val target: String = "8.8.8.8",
    val mode: DiagnosticMode = DiagnosticMode.PING,
    val status: DiagnosticStatus = DiagnosticStatus.IDLE,
    val packetCount: Int = 4,
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

    fun setTarget(t: String) { _uiState.update { it.copy(target = t) } }
    fun setPacketCount(c: Int) { _uiState.update { it.copy(packetCount = c) } }
    fun setTimeoutSeconds(s: Int) { _uiState.update { it.copy(timeoutSeconds = s) } }

    fun startPing() {
        stopDiagnostic()
        _uiState.update { it.copy(mode = DiagnosticMode.PING, status = DiagnosticStatus.RUNNING, logs = emptyList(), pingPackets = emptyList(), pingSummary = null) }
        activeJob = viewModelScope.launch {
            repository.executePing(_uiState.value.target, _uiState.value.packetCount, _uiState.value.timeoutSeconds).collect { entry ->
                _uiState.update { it.copy(logs = it.logs + entry) }
            }
            _uiState.update { it.copy(status = DiagnosticStatus.COMPLETED) }
        }
    }

    fun startTraceroute() {
        stopDiagnostic()
        _uiState.update { it.copy(mode = DiagnosticMode.TRACEROUTE, status = DiagnosticStatus.RUNNING, logs = emptyList(), hops = emptyList()) }
        activeJob = viewModelScope.launch {
            repository.executeTraceroute(_uiState.value.target, 30, _uiState.value.timeoutSeconds).collect { entry ->
                _uiState.update { it.copy(logs = it.logs + entry) }
            }
            _uiState.update { it.copy(status = DiagnosticStatus.COMPLETED) }
        }
    }

    fun stopDiagnostic() {
        activeJob?.cancel()
        activeJob = null
        _uiState.update { it.copy(status = DiagnosticStatus.CANCELLED) }
    }

    fun clearLogs() {
        _uiState.update { it.copy(logs = emptyList(), pingPackets = emptyList(), hops = emptyList(), status = DiagnosticStatus.IDLE) }
    }
}
`
  },
  {
    path: 'README.md',
    name: 'README.md',
    language: 'markdown',
    content: `# WiFi Pulse — Анализатор Wi-Fi, Mesh-сетей и диагностика (Ping/Traceroute)

Полнофункциональное приложение для Android (API 26+) с реальным сканером Wi-Fi, детекцией Mesh-сетей (802.11k/r/v) и инструментами сетевой диагностики (Ping и Traceroute на ProcessBuilder).
`
  }
];

export async function downloadAndroidProjectZip(): Promise<void> {
  const zip = new JSZip();

  for (const file of ANDROID_PROJECT_FILES) {
    zip.file(file.path, file.content);
  }

  const blob = await zip.generateAsync({ type: 'blob' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = 'wifi-pulse-android-mesh-project.zip';
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  URL.revokeObjectURL(url);
}
