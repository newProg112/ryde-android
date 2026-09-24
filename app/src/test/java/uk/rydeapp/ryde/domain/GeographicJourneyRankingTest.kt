package uk.rydeapp.ryde.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeographicJourneyRankingTest {
    @Test
    fun `score combines origin and destination distances in kilometres`() {
        val score = GeographicJourneyMatch.Compatible(
            originDistance = GeographicDistance(2.25),
            destinationDistance = GeographicDistance(4.75),
        ).combinedEndpointScore()

        assertEquals(GeographicJourneyMatchScore(GeographicDistance(7.0)), score)
    }

    @Test
    fun `combined score resolves an origin and destination trade-off`() {
        val closerOrigin = GeographicJourneyMatch.Compatible(
            originDistance = GeographicDistance(1.0),
            destinationDistance = GeographicDistance(8.0),
        ).combinedEndpointScore()
        val closerDestination = GeographicJourneyMatch.Compatible(
            originDistance = GeographicDistance(4.0),
            destinationDistance = GeographicDistance(2.0),
        ).combinedEndpointScore()

        assertTrue(closerDestination < closerOrigin)
    }
}
