package uk.rydeapp.ryde.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.rydeapp.ryde.domain.model.GeographicCoordinate

class BroadAreaRouteOverlapPolicyTest {
    private val origin = GeographicCoordinate(53.1432, -1.1984)
    private val via = GeographicCoordinate(53.0380, -1.2034)
    private val destination = GeographicCoordinate(52.9548, -1.1581)
    private val route = JourneyGeographicRoute(listOf(origin, via, destination))

    @Test fun `rider may join at Via and leave at destination`() {
        val result = BroadAreaRouteOverlapPolicy().assess(
            JourneyGeographicEndpoints(via, destination),
            route,
        )

        assertEquals(
            GeographicJourneyMatch.Compatible(GeographicDistance(0.0), GeographicDistance(0.0)),
            result,
        )
    }

    @Test fun `pickup and dropoff may use the same broad route point`() {
        val result = BroadAreaRouteOverlapPolicy().assess(
            JourneyGeographicEndpoints(via, via),
            route,
        )

        assertTrue(result is GeographicJourneyMatch.Compatible)
    }

    @Test fun `reversed pickup and dropoff order is incompatible`() {
        val result = BroadAreaRouteOverlapPolicy().assess(
            JourneyGeographicEndpoints(destination, via),
            route,
        )

        assertTrue(result is GeographicJourneyMatch.Incompatible)
    }

    @Test fun `missing rider or route data is insufficient`() {
        val policy = BroadAreaRouteOverlapPolicy()

        assertEquals(GeographicJourneyMatch.InsufficientGeographicData, policy.assess(null, route))
        assertEquals(
            GeographicJourneyMatch.InsufficientGeographicData,
            policy.assess(JourneyGeographicEndpoints(origin, destination), null),
        )
    }
}
