package com.wifipulse.analyzer.data.repository

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Heuristic estimate from Android scan results only.
 * It is not an airtime/utilization measurement.
 */
data class ChannelObservation(
    val bssid: String,
    val frequencyMhz: Int,
    val widthMhz: Int,
    val rssiDbm: Int
)

data class ChannelAssessment(
    val score: Int?,
    val overlappingCount: Int,
    val strongestRssiDbm: Int?,
    val confidence: Confidence,
    val explanation: String
) {
    enum class Confidence { NO_DATA, LIMITED, MODERATE }
}

object ChannelInterferenceAnalyzer {
    fun assess(
        candidateFrequencyMhz: Int,
        candidateWidthMhz: Int,
        observations: List<ChannelObservation>
    ): ChannelAssessment {
        if (observations.isEmpty()) {
            return ChannelAssessment(
                score = null,
                overlappingCount = 0,
                strongestRssiDbm = null,
                confidence = ChannelAssessment.Confidence.NO_DATA,
                explanation = "Нет данных сканирования; оценка не рассчитана."
            )
        }

        val width = candidateWidthMhz.coerceAtLeast(20)
        val candidateLow = candidateFrequencyMhz - width / 2.0
        val candidateHigh = candidateFrequencyMhz + width / 2.0
        val unique = observations.distinctBy { it.bssid.lowercase() }
        val contributions = unique.mapNotNull { ap ->
            val apWidth = ap.widthMhz.coerceAtLeast(20)
            val apLow = ap.frequencyMhz - apWidth / 2.0
            val apHigh = ap.frequencyMhz + apWidth / 2.0
            val overlap = (min(candidateHigh, apHigh) - max(candidateLow, apLow)).coerceAtLeast(0.0)
            if (overlap <= 0.0) return@mapNotNull null

            // Power is normalized to -100 dBm and capped to prevent one AP
            // from making the score negative by itself.
            val relativePower = 10.0.pow(((ap.rssiDbm.coerceIn(-100, -20) + 100) / 10.0))
            val powerWeight = (relativePower / 1000.0).coerceIn(0.03, 4.0)
            val overlapRatio = (overlap / width).coerceIn(0.0, 1.0)
            // Same-channel networks are potential contention; partial overlap
            // is treated as a more uncertain interference estimate.
            val interactionFactor = if (ap.frequencyMhz == candidateFrequencyMhz) 0.65 else 1.0
            ap to (powerWeight * overlapRatio * interactionFactor)
        }

        val totalLoad = contributions.sumOf { it.second }
        val score = (10.0 / (1.0 + totalLoad)).toInt().coerceIn(0, 10)
        val strongest = contributions.maxOfOrNull { it.first.rssiDbm }
        val confidence = when {
            unique.size >= 8 -> ChannelAssessment.Confidence.MODERATE
            else -> ChannelAssessment.Confidence.LIMITED
        }
        val explanation = when {
            contributions.isEmpty() -> "Перекрытие с обнаруженными сетями не найдено; загрузка эфира не измерялась."
            strongest != null && strongest >= -60 -> "Есть сильные соседние сети; возможна заметная конкуренция."
            else -> "Обнаружено частотное перекрытие; реальная загрузка эфира неизвестна."
        }
        return ChannelAssessment(score, contributions.size, strongest, confidence, explanation)
    }
}
