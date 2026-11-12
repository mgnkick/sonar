package com.wifipulse.analyzer.ui.screens

import com.wifipulse.analyzer.ui.components.Text
import com.wifipulse.analyzer.ui.components.LocalAppLanguage
import com.wifipulse.analyzer.ui.components.appUiText

import android.content.Intent
import android.net.Uri
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun AboutProgramDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val copiedCardMessage = appUiText("Номер карты скопирован", LocalAppLanguage.current)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("О программе")
                TextButton(
                    onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PROJECT_URL)))
                    }
                ) {
                    Icon(Icons.Default.OpenInNew, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Страница проекта · WiFi Sonar ↗")
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "WiFi Sonar объединяет сканирование Wi-Fi, аудит эфира, диагностику маршрута и карту замеров сигнала.",
                    style = MaterialTheme.typography.bodyMedium
                )
                AboutFeature("Сканер (Mesh)", "Сети, точки доступа, уровень сигнала и каналы.")
                AboutFeature("Смарт-Аудит", "Загруженность радиоэфира, качество сигнала и безопасность.")
                AboutFeature("Диагностика", "Пинг, трассировка с анализом потерь и сохранение вывода консоли.")
                AboutFeature("Спектр/Инфо", "Спектр каналов, подключение и история переключений.")
                AboutFeature("Карта", "План помещения, фигуры, подписи и замеры сигнала.")
                Text("Контакты и поддержка", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                ContactEntry(
                    title = "Сбербанк · рубли",
                    value = CARD_NUMBER,
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Номер карты", CARD_NUMBER))
                        Toast.makeText(context, copiedCardMessage, Toast.LENGTH_SHORT).show()
                    },
                    trailingIcon = true
                )
                ContactEntry("Telegram", "@mgnkick") {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(TELEGRAM_URL))) }
                }
                ContactEntry("Email", "mgn.kick@gmail.com") {
                    runCatching { context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:mgn.kick@gmail.com"))) }
                }
                Text(
                    "Показания Wi-Fi зависят от Android, разрешений и возможностей устройства.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Понятно") }
        }
    )
}

private const val PROJECT_URL = "https://179.253.79.218/#about"
private const val TELEGRAM_URL = "https://t.me/mgnkick"
private const val CARD_NUMBER = "5336 6903 0435 1846"

@Composable
private fun ContactEntry(title: String, value: String, trailingIcon: Boolean = false, onClick: () -> Unit) {
    androidx.compose.material3.Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            }
            if (trailingIcon) Icon(Icons.Default.ContentCopy, contentDescription = "Скопировать номер карты", tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun AboutFeature(title: String, description: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
        Text(description, style = MaterialTheme.typography.bodySmall)
    }
}
