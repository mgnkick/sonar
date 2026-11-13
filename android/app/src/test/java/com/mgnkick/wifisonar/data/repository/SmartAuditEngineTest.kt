package com.mgnkick.wifisonar.data.repository

import com.mgnkick.wifisonar.data.model.AuditCategory
import com.mgnkick.wifisonar.data.model.AuditSeverity
import com.mgnkick.wifisonar.data.model.NetworkTopologyType
import com.mgnkick.wifisonar.data.model.SecurityType
import com.mgnkick.wifisonar.data.model.WifiConnectionDetails
import com.mgnkick.wifisonar.data.model.WifiNetworkGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartAuditEngineTest {
    @Test
    fun unknownConnectedFrequencyDoesNotInventBandOrChannel() {
        val report = SmartAuditEngine().generateReport(
            networkGroups = emptyList(),
            connectionDetails = WifiConnectionDetails(
                isConnected = true,
                ssid = "Corp",
                frequency = 0
            )
        )

        val channelRecommendation = report.recommendations.single {
            it.category == AuditCategory.CHANNEL_CONGESTION
        }
        assertEquals(AuditSeverity.WARNING, channelRecommendation.severity)
        assertEquals("rec_channel_unknown", channelRecommendation.id)
        assertTrue(channelRecommendation.summary.contains("актуальную частоту"))
    }

    @Test
    fun emptyNetworkGroupDoesNotImplyOpenSecurity() {
        val group = WifiNetworkGroup(
            ssid = "Corp",
            nodes = emptyList(),
            topologyType = NetworkTopologyType.STANDALONE,
            primaryVendor = "Unknown",
            hasMixedVendors = false
        )

        assertEquals(SecurityType.UNKNOWN, group.bestSecurity)
    }
}
