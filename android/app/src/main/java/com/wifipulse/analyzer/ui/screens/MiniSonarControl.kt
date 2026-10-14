package com.wifipulse.analyzer.ui.screens

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** Compact signal-strength audio indicator shared by every screen. */
@Composable
fun MiniSonarControl(rssi: Int, modifier: Modifier = Modifier) {
    var enabled by remember { mutableStateOf(false) }
    val tone = remember { ToneGenerator(AudioManager.STREAM_MUSIC, 55) }

    DisposableEffect(tone) {
        onDispose {
            tone.stopTone()
            tone.release()
        }
    }

    LaunchedEffect(enabled, rssi) {
        if (enabled) {
            while (true) {
                tone.startTone(ToneGenerator.TONE_PROP_BEEP, 70)
                val interval = (1000 - ((rssi + 100).coerceIn(0, 70) * 10)).coerceIn(300, 1000)
                delay(interval.toLong())
            }
        } else {
            tone.stopTone()
        }
    }

    FilledTonalIconButton(
        onClick = { enabled = !enabled },
        modifier = modifier.size(38.dp)
    ) {
        Icon(
            imageVector = if (enabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
            contentDescription = if (enabled) "Выключить звуковой сонар" else "Включить звуковой сонар",
            tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}
