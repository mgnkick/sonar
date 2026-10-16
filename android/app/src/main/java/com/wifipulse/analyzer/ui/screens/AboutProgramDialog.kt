package com.wifipulse.analyzer.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun AboutProgramDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
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
                    Text("Страница проекта ↗")
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
                    "WiFi Sonar помогает обследовать Wi-Fi-сети, оценивать качество связи и сохранять результаты замеров.",
                    style = MaterialTheme.typography.bodyMedium
                )
                AboutFeature("Сканер (Mesh)", "Находит доступные сети и показывает точки доступа, уровень сигнала и каналы.")
                AboutFeature("Смарт-Аудит", "Сводит результаты сканирования и помогает оценить загруженность радиоэфира.")
                AboutFeature("Диагностика", "Запускает проверку связи и трассировку с анализом потерь (ТСАП). Вывод консоли хранится, пока вы его не очистите.")
                AboutFeature("Спектр/Инфо", "Показывает сигнал, спектр каналов, сведения о текущем подключении и историю подключений.")
                AboutFeature("Карта", "Позволяет загрузить план помещения или сделать фото, рисовать объекты и сохранять замеры сигнала на плане.")
                Text(
                    "Показания Wi-Fi зависят от Android, выданных разрешений и возможностей устройства.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Поддержка проекта и контакты автора появятся здесь после добавления публичных ссылок.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Понятно") }
        }
    )
}

private const val PROJECT_URL = "https://72.4.67.209/"

@Composable
private fun AboutFeature(title: String, description: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
        Text(description, style = MaterialTheme.typography.bodySmall)
    }
}
