package com.wifipulse.analyzer.data.repository

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** Heuristic based on detected AP scan results; never represents measured airtime. */
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
    enum class Confidence { NO_DATA, LIMITED }
}

object ChannelInterferenceAnalyzer {
    fun assess(
        candidateFrequencyMhz: Int,
        candidateWidthMhz: Int,
        observations: List<ChannelObservation>
    ): ChannelAssessment {
        if (observations.isEmpty()) return ChannelAssessment(
            score = null,
            overlappingCount = 0,
            strongestRssiDbm = null,
            confidence = ChannelAssessment.Confidence.NO_DATA,
            explanation = "Нет обнаруженных сетей. Это не доказывает, что эфир свободен."
        )

        val width = candidateWidthMhz.coerceAtLeast(20)
        val candidateLow = candidateFrequencyMhz - width / 2.0
        val candidateHigh = candidateFrequencyMhz + width / 2.0
        // A BSSID is one AP; repeated rows must not amplify its influence.
        val unique = observations.filter { it.bssid.isNotBlank() }
            .distinctBy { it.bssid.lowercase() }
        val contributions = unique.mapNotNull { ap ->
            if (ap.frequencyMhz <= 0) return@mapNotNull null
            val apWidth = ap.widthMhz.coerceIn(20, 160)
            val apLow = ap.frequencyMhz - apWidth / 2.0
            val apHigh = ap.frequencyMhz + apWidth / 2.0
            val overlapMhz = (min(candidateHigh, apHigh) - max(candidateLow, apLow)).coerceAtLeast(0.0)
            if (overlapMhz <= 0.0) return@mapNotNull null

            val overlapRatio = (overlapMhz / min(width.toDouble(), apWidth.toDouble())).coerceIn(0.0, 1.0)
            // Convert dBm to relative linear power. -50 dBm is the reference;
            // clamp extreme values to avoid one AP dominating the entire model.
            val relativePower = 10.0.pow((ap.rssiDbm.coerceIn(-100, -20) + 50) / 10.0)
            val powerWeight = relativePower.coerceIn(0.01, 10.0)
            // Co-channel contention and partial-channel overlap are different cases.
            // This factor is a heuristic, not a claim about measured interference.
            val interaction = if (ap.frequencyMhz == candidateFrequencyMhz) 0.55 else 1.0
            ap to (powerWeight * overlapRatio * interaction)
        }

        val totalPenalty = contributions.sumOf { it.second }
        val score = (10.0 / (1.0 + totalPenalty)).toInt().coerceIn(0, 10)
        val strongest = contributions.maxOfOrNull { it.first.rssiDbm }
        val explanation = when {
            contributions.isEmpty() ->
                "Перекрытие с обнаруженными сетями не найдено. Фактическая загрузка эфира неизвестна."
            strongest != null && strongest >= -60 ->
                "Обнаружена сильная соседняя сеть; возможна заметная конкуренция."
            else ->
                "Есть частотное перекрытие с обнаруженными сетями; фактическая загрузка эфира неизвестна."
        }
        // A single Android scan cannot justify moderate/high confidence.
        return ChannelAssessment(
            score = score,
            overlappingCount = contributions.size,
            strongestRssiDbm = strongest,
            confidence = ChannelAssessment.Confidence.LIMITED,
            explanation = explanation
        )
    }
}
