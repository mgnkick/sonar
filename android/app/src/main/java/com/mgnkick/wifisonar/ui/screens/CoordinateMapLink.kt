package com.mgnkick.wifisonar.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mgnkick.wifisonar.ui.components.AppLanguage
import com.mgnkick.wifisonar.ui.components.LocalAppLanguage
import com.mgnkick.wifisonar.ui.components.Text
import java.util.Locale

/** A tap on the coordinates opens a Google Maps pin, with the system browser as fallback. */
@Composable
fun CoordinateMapLink(
    label: String,
    latitude: Double,
    longitude: Double,
    accuracyMeters: Float? = null
) {
    val context = LocalContext.current
    val language = LocalAppLanguage.current
    val coordinateText = String.format(Locale.US, "%.6f, %.6f", latitude, longitude) +
        (accuracyMeters?.let { " · ±${it.toInt()} ${if (language == AppLanguage.EN) "m" else "м"}" } ?: "")
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
        Text(
            text = coordinateText,
            modifier = Modifier
                .semantics { role = Role.Button }
                .clickable {
                    val url = Uri.Builder()
                        .scheme("https")
                        .authority("www.google.com")
                        .path("/maps/search/")
                        .appendQueryParameter("api", "1")
                        .appendQueryParameter("query", "$latitude,$longitude")
                        .build()
                    context.startActivity(Intent(Intent.ACTION_VIEW, url))
                },
            color = MaterialTheme.colorScheme.primary,
            textDecoration = TextDecoration.Underline,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
