package com.wifipulse.analyzer.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ChannelInterferenceAnalyzerTest {
    @Test fun emptyScanDoesNotClaimPerfectChannel() {
        val result = ChannelInterferenceAnalyzer.assess(5180, 40, emptyList())
        assertNull(result.score)
        assertEquals(ChannelAssessment.Confidence.NO_DATA, result.confidence)
    }

    @Test fun sameChannelNetworkIsCounted() {
        val result = ChannelInterferenceAnalyzer.assess(
            5180, 40, listOf(ChannelObservation("aa", 5180, 40, -60))
        )
        assertEquals(1, result.overlappingCount)
        assertNotNull(result.score)
    }

    @Test fun partialOverlapIsCounted() {
        val result = ChannelInterferenceAnalyzer.assess(
            5180, 20, listOf(ChannelObservation("aa", 5190, 20, -65))
        )
        assertEquals(1, result.overlappingCount)
    }

    @Test fun widerNeighborCanOverlapCandidate() {
        val result = ChannelInterferenceAnalyzer.assess(
            5220, 20, listOf(ChannelObservation("aa", 5180, 80, -55))
        )
        assertEquals(1, result.overlappingCount)
    }

    @Test fun strongNetworkScoresWorseThanWeakNetwork() {
        val strong = ChannelInterferenceAnalyzer.assess(
            5180, 40, listOf(ChannelObservation("strong", 5180, 40, -45))
        )
        val weak = ChannelInterferenceAnalyzer.assess(
            5180, 40, listOf(ChannelObservation("weak", 5180, 40, -90))
        )
        assertNotNull(strong.score)
        assertNotNull(weak.score)
        assert("Strong overlapping AP should materially reduce the score" , strong.score!! <= 5)
        assert("Weak AP should have limited impact", weak.score!! >= 9)
        assert(strong.score!! < weak.score!!)
    }

    @Test fun noFrequencyOverlapIsNotCounted() {
        val result = ChannelInterferenceAnalyzer.assess(
            5180, 20, listOf(ChannelObservation("far", 5260, 20, -40))
        )
        assertEquals(0, result.overlappingCount)
        assertEquals(10, result.score)
    }

    @Test fun weakNetworkHasLessInfluenceThanStrongNetwork() {
        val strong = ChannelInterferenceAnalyzer.assess(
            2412, 20, listOf(ChannelObservation("strong", 2412, 20, -45))
        )
        val weak = ChannelInterferenceAnalyzer.assess(
            2412, 20, listOf(ChannelObservation("weak", 2412, 20, -85))
        )
        assert(strong.score!! < weak.score!!)
    }

    @Test fun duplicateBssidIsCountedOnce() {
        val result = ChannelInterferenceAnalyzer.assess(
            2412, 20, listOf(
                ChannelObservation("AA", 2412, 20, -60),
                ChannelObservation("aa", 2412, 20, -60)
            )
        )
        assertEquals(1, result.overlappingCount)
    }
}
