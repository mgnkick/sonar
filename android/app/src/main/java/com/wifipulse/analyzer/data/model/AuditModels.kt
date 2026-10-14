package com.wifipulse.analyzer.data.model

enum class AuditSeverity(
    val displayName: String,
    val badgeColor: Long,
    val containerColor: Long
) {
    OPTIMAL("Оптимально", 0xFF10B981, 0x1A10B981),
    WARNING("Предупреждение", 0xFFF59E0B, 0x1AF59E0B),
    CRITICAL("Критическая угроза", 0xFFEF4444, 0x1AEF4444)
}

enum class AuditCategory(val displayName: String) {
    CHANNEL_CONGESTION("Загруженность радиочастот"),
    SIGNAL_COVERAGE("Качество покрытия и сигнал"),
    SECURITY_PROTOCOL("Безопасность и шифрование"),
    TOPOLOGY_EFFICIENCY("Топология сети (Mesh & Репитеры)"),
    NETWORK_LATENCY("Сетевая задержка и стабильность")
}

data class AuditRecommendation(
    val id: String,
    val category: AuditCategory,
    val severity: AuditSeverity,
    val title: String,
    val summary: String,
    val actionGuide: String,
    val metricBadge: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class AuditReport(
    val overallScore: Int, // 0 to 100
    val statusTitle: String,
    val statusDescription: String,
    val recommendations: List<AuditRecommendation>,
    val scannedNetworksCount: Int,
    val optimalCount: Int,
    val warningCount: Int,
    val criticalCount: Int,
    val evaluatedAt: Long = System.currentTimeMillis()
)
