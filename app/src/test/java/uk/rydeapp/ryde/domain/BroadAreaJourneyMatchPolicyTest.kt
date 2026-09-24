package uk.rydeapp.ryde.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.rydeapp.ryde.domain.model.GeographicCoordinate

class BroadAreaJourneyMatchPolicyTest {
    private val mansfield = GeographicCoordinate(53.1432, -1.1984)
    private val nottingham = GeographicCoordinate(52.9548, -1.1581)
    private val londonRichmond = GeographicCoordinate(51.4613, -0.3037)
    private val rider = JourneyGeographicEndpoints(mansfield, nottingham)

    @Test
    fun `both compatible endpoints produce geographically compatible result`() {
        val result = BroadAreaJourneyMatchPolicy().assess(rider, rider)

        assertEquals(
            GeographicJourneyMatch.Compatible(GeographicDistance(0.0), GeographicDistance(0.0)),
            result,
        )
    }

    @Test
    fun `either endpoint beyond tolerance makes journey incompatible`() {
        val result = BroadAreaJourneyMatchPolicy().assess(
            rider,
            JourneyGeographicEndpoints(londonRichmond, nottingham),
        )

        assertTrue(result is GeographicJourneyMatch.Incompatible)
        result as GeographicJourneyMatch.Incompatible
        assertTrue(result.originDistance > BroadAreaJourneyMatchDefaults.maximumEndpointDistance)
        assertEquals(0.0, result.destinationDistance.kilometres, 0.0)
    }

    @Test
    fun `missing rider or offered endpoints are insufficient rather than compatible`() {
        val policy = BroadAreaJourneyMatchPolicy()

        assertEquals(GeographicJourneyMatch.InsufficientGeographicData, policy.assess(null, rider))
        assertEquals(GeographicJourneyMatch.InsufficientGeographicData, policy.assess(rider, null))
    }

    @Test
    fun `configured tolerance is inclusive and centralized`() {
        val atBoundary = BroadAreaJourneyMatchPolicy(
            distanceCalculator = GeographicDistanceCalculator { _, _ -> GeographicDistance(15.0) },
        ).assess(rider, rider)
        val beyondBoundary = BroadAreaJourneyMatchPolicy(
            distanceCalculator = GeographicDistanceCalculator { _, _ -> GeographicDistance(15.001) },
        ).assess(rider, rider)

        assertTrue(atBoundary is GeographicJourneyMatch.Compatible)
        assertTrue(beyondBoundary is GeographicJourneyMatch.Incompatible)
        assertEquals(15.0, BroadAreaJourneyMatchDefaults.maximumEndpointDistance.kilometres, 0.0)
    }
}
