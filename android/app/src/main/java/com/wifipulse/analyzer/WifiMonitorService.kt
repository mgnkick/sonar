package com.wifipulse.analyzer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.net.TrafficStats
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.util.Locale

/**
 * Keeps a user-visible Wi-Fi status notification while background monitoring is enabled.
 *
 * Traffic rates are device-wide TrafficStats deltas, not a synthetic Wi-Fi link-speed
 * estimate and not a dedicated LAN throughput test.
 */
class WifiMonitorService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var wifiManager: WifiManager
    private lateinit var notificationManager: NotificationManager
    private var previousRx = TrafficStats.UNSUPPORTED.toLong()
    private var previousTx = TrafficStats.UNSUPPORTED.toLong()
    private var previousSampleAt = 0L
    private var previousInterface: String? = null

    private val sampleTask = object : Runnable {
        override fun run() {
            updateStatusNotification()
            handler.postDelayed(this, SAMPLE_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopMonitoring()
            return START_NOT_STICKY
        }

        val initialNotification = buildNotification(
            ssid = "Получение данных Wi-Fi…",
            rssi = null,
            rxRate = null,
            txRate = null
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                initialNotification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            startForeground(NOTIFICATION_ID, initialNotification)
        }

        getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_RUNNING, true).apply()

        handler.removeCallbacks(sampleTask)
        handler.post(sampleTask)
        return START_STICKY
    }

    private fun updateStatusNotification() {
        val now = System.currentTimeMillis()
        val info = try {
            @Suppress("DEPRECATION")
            wifiManager.connectionInfo
        } catch (_: SecurityException) {
            null
        }

        val ssid = info?.ssid
            ?.takeIf { it.isNotBlank() && it != WifiManager.UNKNOWN_SSID && it != "<unknown ssid>" }
            ?.trim('"')
            ?: "Wi-Fi не подключён"
        val rssi = info?.rssi?.takeIf { it in -126..0 }

        val wifiInterface = findWifiInterface()
        val rx = wifiInterface?.let { TrafficStats.getRxBytes(it) } ?: TrafficStats.getTotalRxBytes()
        val tx = wifiInterface?.let { TrafficStats.getTxBytes(it) } ?: TrafficStats.getTotalTxBytes()
        var rxRate: Long? = null
        var txRate: Long? = null
        val sameInterface = wifiInterface == previousInterface
        if (sameInterface && previousSampleAt > 0L && rx >= 0L && tx >= 0L &&
            previousRx >= 0L && previousTx >= 0L && now > previousSampleAt
        ) {
            val seconds = (now - previousSampleAt) / 1000.0
            rxRate = ((rx - previousRx).coerceAtLeast(0L) / seconds).toLong()
            txRate = ((tx - previousTx).coerceAtLeast(0L) / seconds).toLong()
        }
        previousRx = rx
        previousTx = tx
        previousSampleAt = now
        previousInterface = wifiInterface

        notificationManager.notify(
            NOTIFICATION_ID,
            buildNotification(ssid, rssi, rxRate, txRate, wifiInterface != null)
        )
    }

    private fun findWifiInterface(): String? {
        return try {
            val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            connectivityManager.allNetworks.firstOrNull { network ->
                connectivityManager.getNetworkCapabilities(network)
                    ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            }?.let { network ->
                connectivityManager.getLinkProperties(network)?.interfaceName
            }?.takeIf { it.isNotBlank() }
        } catch (_: SecurityException) {
            null
        }
    }

    private fun buildNotification(
        ssid: String,
        rssi: Int?,
        rxRate: Long?,
        txRate: Long?,
        isWifiInterfaceRate: Boolean = false
    ): Notification {
        val stopIntent = Intent(this, WifiMonitorService::class.java).setAction(ACTION_STOP)
        val stopPendingIntent = PendingIntent.getService(
            this,
            STOP_REQUEST_CODE,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val signal = rssi?.let { "$it dBm" } ?: "уровень недоступен"
        val rates = if (rxRate != null && txRate != null) {
            "${if (isWifiInterfaceRate) "Wi-Fi" else "Трафик устройства"}: ↓ ${formatRate(rxRate)} • ↑ ${formatRate(txRate)}"
        } else {
            "Сбор скорости передачи данных…"
        }
        val sourceNote = if (isWifiInterfaceRate) {
            "Скорость передачи по Wi-Fi-интерфейсу; это не отдельный тест LAN."
        } else {
            "Нет счётчика Wi-Fi-интерфейса: показан общий трафик устройства, это не тест LAN."
        }
        val expandedText = "$ssid • $signal\n$rates\n$sourceNote"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentTitle("WiFi Pulse • Фоновый мониторинг")
            .setContentText("$ssid • $signal")
            .setSubText(rates)
            .setStyle(NotificationCompat.BigTextStyle().bigText(expandedText))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Остановить", stopPendingIntent)
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    OPEN_REQUEST_CODE,
                    Intent(this, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()
    }

    private fun formatRate(bytesPerSecond: Long): String {
        return when {
            bytesPerSecond >= 1024L * 1024L ->
                String.format(Locale.getDefault(), "%.1f МБ/с", bytesPerSecond / (1024.0 * 1024.0))
            bytesPerSecond >= 1024L ->
                String.format(Locale.getDefault(), "%.0f КБ/с", bytesPerSecond / 1024.0)
            else -> "$bytesPerSecond Б/с"
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Фоновый мониторинг Wi-Fi",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Текущее имя сети, уровень сигнала и скорость трафика устройства"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun stopMonitoring() {
        handler.removeCallbacks(sampleTask)
        getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_RUNNING, false).apply()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacks(sampleTask)
        getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_RUNNING, false).apply()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_STOP = "com.wifipulse.analyzer.action.STOP_WIFI_MONITOR"
        const val PREFERENCES = "wifi_pulse_settings"
        const val KEY_RUNNING = "background_monitor_running"

        private const val CHANNEL_ID = "wifi_background_monitor"
        private const val NOTIFICATION_ID = 9201
        private const val SAMPLE_INTERVAL_MS = 2_000L
        private const val STOP_REQUEST_CODE = 9202
        private const val OPEN_REQUEST_CODE = 9203

        fun start(context: Context) {
            val intent = Intent(context, WifiMonitorService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, WifiMonitorService::class.java).setAction(ACTION_STOP)
            )
        }
    }
}
