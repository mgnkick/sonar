package com.wifipulse.analyzer.ui.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.IconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wifipulse.analyzer.data.model.WifiConnectionDetails

@Composable
fun ConnectionStatusBanner(
    details: WifiConnectionDetails,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val is24Ghz = details.isConnected && details.frequency in 2400..2500
    val hasWarning = !details.isConnected || details.isVpnActive ||
        details.isMobileDataActive || is24Ghz
    val container = if (hasWarning) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer
    val currentChannel = when (details.frequency) {
        in 2412..2472 -> ((details.frequency - 2407) / 5)
        2484 -> 14
        in 5000..5895 -> ((details.frequency - 5000) / 5)
        in 5955..7115 -> ((details.frequency - 5950) / 5)
        else -> 0
    }
    val title = when {
        !details.isConnected && details.isMobileDataActive -> "Wi-Fi не подключён • используется мобильный интернет"
        !details.isConnected -> "Wi-Fi не подключён"
        currentChannel > 0 -> "Подключено: ${details.ssid} • CH ${currentChannel}"
        else -> "Подключено: ${details.ssid}"
    }

    fun openSettings(primaryAction: String, fallbackAction: String) {
        val actions = listOf(primaryAction, fallbackAction)
        for (action in actions) {
            try {
                context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (_: ActivityNotFoundException) {
                // Try the generic Android settings screen on devices without this category.
            } catch (_: SecurityException) {
                // Try the fallback action if the OEM blocks the specific settings intent.
            }
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = if (hasWarning) MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                else MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                shape = RoundedCornerShape(14.dp)
            ),
        color = container,
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.Icon(
                    imageVector = if (hasWarning) Icons.Default.Error else Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = if (hasWarning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
                Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (hasWarning) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (details.isConnected) {
                        Text(
                            text = "${details.frequency} МГц • ${details.rssi} дБм • ${details.linkSpeedMbps} Мбит/с",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (hasWarning) MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
            }
            if (!details.isConnected) {
                WarningLine(
                    text = "Подключитесь к Wi-Fi для анализа локальной беспроводной сети.",
                    buttonText = "Открыть настройки Wi-Fi",
                    onClick = { openSettings(Settings.ACTION_WIFI_SETTINGS, Settings.ACTION_WIRELESS_SETTINGS) }
                )
            }
            if (details.isVpnActive) {
                WarningLine(
                    text = "Обнаружен активный VPN. Он может менять маршруты, DNS и результаты проверки.",
                    buttonText = "Настройки VPN",
                    onClick = { openSettings(Settings.ACTION_VPN_SETTINGS, Settings.ACTION_SETTINGS) }
                )
            }
            if (details.isMobileDataActive) {
                WarningLine(
                    text = "Передача данных идёт через мобильную сеть, а не через Wi-Fi.",
                    buttonText = "Настройки сети",
                    onClick = { openSettings(Settings.ACTION_WIRELESS_SETTINGS, Settings.ACTION_DATA_USAGE_SETTINGS) }
                )
            }
            if (is24Ghz) {
                WarningLine(
                    text = "Подключение 2,4 ГГц: скорость обычно ниже, чем у 5 ГГц. Проверьте доступность 5 ГГц.",
                    buttonText = "Выбрать сеть Wi-Fi",
                    onClick = { openSettings(Settings.ACTION_WIFI_SETTINGS, Settings.ACTION_WIRELESS_SETTINGS) }
                )
            }
        }
    }
}

@Composable
private fun WarningLine(
    text: String,
    buttonText: String,
    onClick: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Top) {
            androidx.compose.material3.Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary
            )
            Text(
                text = text,
                modifier = Modifier.padding(start = 6.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        TextButton(
            onClick = onClick,
            modifier = Modifier.align(Alignment.End)
        ) {
            Text(buttonText, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
fun ConnectionStatusPanel(
    details: WifiConnectionDetails,
    visibleNetworkCount: Int,
    isRefreshing: Boolean,
    onRefreshNetworks: () -> Unit,
    lastScanTimestamp: Long = 0L,
    modifier: Modifier = Modifier,
    showRefreshButton: Boolean = true
) {
    var clockNow by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(lastScanTimestamp) {
        while (true) {
            clockNow = System.currentTimeMillis()
            delay(1000)
        }
    }
    val elapsedSeconds = if (lastScanTimestamp > 0L) ((clockNow - lastScanTimestamp).coerceAtLeast(0L) / 1000L) else null
    val elapsedLabel = elapsedSeconds?.let { elapsed ->
        when {
            elapsed < 60 -> "${elapsed}с назад"
            elapsed < 3600 -> "${elapsed / 60}м ${elapsed % 60}с назад"
            else -> "${elapsed / 3600}ч ${(elapsed % 3600) / 60}м назад"
        }
    } ?: "ожидается первое обновление"
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(modifier = Modifier.weight(1f)) {
                ConnectionStatusBanner(details = details)
            }
            MiniSonarControl(rssi = if (details.isConnected) details.rssi else -65)
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 2.dp, top = 2.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "Сетей: $visibleNetworkCount • обновлено $elapsedLabel",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (showRefreshButton) {
                IconButton(
                    onClick = onRefreshNetworks,
                    enabled = !isRefreshing,
                    modifier = Modifier.size(36.dp)
                ) {
                    if (isRefreshing) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        androidx.compose.material3.Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Обновить список видимых сетей",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StickyWifiRefreshButton(isRefreshing: Boolean, onRefresh: () -> Unit) {
    FloatingActionButton(onClick = onRefresh, containerColor = MaterialTheme.colorScheme.primary) {
        if (isRefreshing) CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
        else androidx.compose.material3.Icon(Icons.Default.Refresh, contentDescription = "Обновить статус Wi-Fi и список сетей", tint = MaterialTheme.colorScheme.onPrimary)
    }
}
