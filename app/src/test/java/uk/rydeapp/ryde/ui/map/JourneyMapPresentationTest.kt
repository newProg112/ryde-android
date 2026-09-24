package uk.rydeapp.ryde.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import uk.rydeapp.ryde.domain.model.GeographicCoordinate

class JourneyMapPresentationTest {
    @Test
    fun broadAreasProduceAnExplicitNonGeographicConnection() {
        val route = broadAreaJourneyMap("York", "Leeds")

        assertEquals(
            listOf(JourneyMapPointRole.JOURNEY_START, JourneyMapPointRole.JOURNEY_DESTINATION),
            route.points.map(JourneyMapPoint::role),
        )
        assertEquals(listOf("York", "Leeds"), route.points.map(JourneyMapPoint::label))
        route.points.forEach { assertNull(it.coordinate) }
        assertSame(JourneyMapLine.VisualConnection, route.line)
    }

    @Test(expected = IllegalArgumentException::class)
    fun coordinateRejectsOutOfRangeLatitude() {
        GeographicCoordinate(90.1, 0.0)
    }

    @Test
    fun persistedCoordinatesReachMapPointsWithoutInventingRoadGeometry() {
        val origin = GeographicCoordinate(53.1432, -1.1984)
        val destination = GeographicCoordinate(52.9548, -1.1581)

        val route = journeyMap("Mansfield", "Nottingham", origin, destination)

        assertEquals(listOf(origin, destination), route.points.map(JourneyMapPoint::coordinate))
        assertSame(JourneyMapLine.VisualConnection, route.line)
    }

    @Test(expected = IllegalArgumentException::class)
    fun roadRouteRequiresAtLeastTwoGeometryPoints() {
        JourneyMapLine.RoadRoute(listOf(GeographicCoordinate(53.0, -1.0)))
    }
}
