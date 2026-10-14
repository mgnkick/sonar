package com.wifipulse.analyzer.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wifipulse.analyzer.data.model.WifiConnectionDetails

@Composable
fun ConnectionStatusBanner(
    details: WifiConnectionDetails,
    modifier: Modifier = Modifier
) {
    val is24Ghz = details.isConnected && details.frequency in 2400..2500
    val hasWarning = !details.isConnected || details.isVpnActive ||
        details.isMobileDataActive || is24Ghz
    val container = if (hasWarning) Color(0xFF3B252B) else Color(0xFF15352E)
    val title = when {
        !details.isConnected && details.isMobileDataActive -> "Wi-Fi не подключён • используется мобильный интернет"
        !details.isConnected -> "Wi-Fi не подключён"
        else -> "Подключено: ${details.ssid}"
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = container,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.Icon(
                    imageVector = if (hasWarning) Icons.Default.Error else Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = if (hasWarning) Color(0xFFFFB4AB) else Color(0xFF8BE0BD)
                )
                Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (details.isConnected) {
                        Text(
                            text = "${details.frequency} МГц • ${details.rssi} дБм • ${details.linkSpeedMbps} Мбит/с",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFD7E3E0)
                        )
                    }
                }
            }
            if (!details.isConnected) {
                WarningLine("Подключитесь к Wi-Fi для анализа локальной беспроводной сети.")
            }
            if (details.isVpnActive) {
                WarningLine("Обнаружен активный VPN. Он может менять маршруты, DNS и результаты проверки.")
            }
            if (details.isMobileDataActive) {
                WarningLine("Передача данных идёт через мобильную сеть, а не через Wi-Fi.")
            }
            if (is24Ghz) {
                WarningLine("Подключение 2,4 ГГц: скорость обычно ниже, чем у 5 ГГц. Проверьте доступность 5 ГГц.")
            }
        }
    }
}

@Composable
private fun WarningLine(text: String) {
    Row(verticalAlignment = Alignment.Top) {
        androidx.compose.material3.Icon(
            imageVector = Icons.Default.Info,
            contentDescription = null,
            tint = Color(0xFFFFD18B)
        )
        Text(
            text = text,
            modifier = Modifier.padding(start = 6.dp),
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFFFFE0B2)
        )
    }
}
