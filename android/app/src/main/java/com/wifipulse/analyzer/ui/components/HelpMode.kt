package com.wifipulse.analyzer.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp

val LocalHelpMode = compositionLocalOf { false }
val LocalHelpToggle = compositionLocalOf<() -> Unit> { {} }

@Composable
fun HelpHint(description: String, modifier: Modifier = Modifier) {
    if (!LocalHelpMode.current) return
    val language = LocalAppLanguage.current
    var show by remember(description) { mutableStateOf(false) }
    IconButton(onClick = { show = true }, modifier = modifier.size(30.dp)) {
        Icon(Icons.Outlined.Info, contentDescription = appUiText("Подсказка", language), modifier = Modifier.size(16.dp))
    }
    if (show) {
        AlertDialog(
            onDismissRequest = { show = false },
            title = { Text("Подсказка") },
            text = { Text(description) },
            confirmButton = { TextButton(onClick = { show = false }) { Text("Понятно") } }
        )
    }
}
