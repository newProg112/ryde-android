package uk.rydeapp.ryde.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
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
        assertTrue(route.hasCompleteCoordinates)
    }

    @Test
    fun persistedViaProducesOneOrderedDeclaredRoutePoint() {
        val origin = GeographicCoordinate(53.1432, -1.1984)
        val via = GeographicCoordinate(53.0380, -1.2034)
        val destination = GeographicCoordinate(52.9548, -1.1581)

        val route = journeyMap(
            "Mansfield", "Nottingham", origin, destination, "Hucknall", via,
        )

        assertEquals(
            listOf(
                JourneyMapPointRole.JOURNEY_START,
                JourneyMapPointRole.JOURNEY_VIA,
                JourneyMapPointRole.JOURNEY_DESTINATION,
            ),
            route.points.map(JourneyMapPoint::role),
        )
        assertEquals(listOf("Mansfield", "Hucknall", "Nottingham"), route.points.map(JourneyMapPoint::label))
        assertEquals(listOf(origin, via, destination), route.points.map(JourneyMapPoint::coordinate))
        assertSame(JourneyMapLine.VisualConnection, route.line)
        assertTrue(route.hasCompleteCoordinates)
    }

    @Test
    fun originAndDestinationCoordinatesAreGeographicallyEligible() {
        val route = journeyMap(
            "Mansfield",
            "Nottingham",
            GeographicCoordinate(53.1432, -1.1984),
            GeographicCoordinate(52.9548, -1.1581),
        )

        assertTrue(route.hasCompleteCoordinates)
    }

    @Test
    fun originViaAndDestinationCoordinatesAreGeographicallyEligible() {
        val route = journeyMap(
            "Mansfield",
            "Nottingham",
            GeographicCoordinate(53.1432, -1.1984),
            GeographicCoordinate(52.9548, -1.1581),
            "Hucknall",
            GeographicCoordinate(53.0380, -1.2034),
        )

        assertTrue(route.hasCompleteCoordinates)
    }

    @Test
    fun missingCoordinatesAreNotGeographicallyEligible() {
        assertFalse(broadAreaJourneyMap("York", "Leeds").hasCompleteCoordinates)
    }

    @Test
    fun mixedCoordinateDataIsNotGeographicallyEligible() {
        val route = JourneyMapPresentation(
            points = listOf(
                JourneyMapPoint(
                    "Mansfield",
                    JourneyMapPointRole.JOURNEY_START,
                    GeographicCoordinate(53.1432, -1.1984),
                ),
                JourneyMapPoint("Hucknall", JourneyMapPointRole.JOURNEY_VIA),
                JourneyMapPoint(
                    "Nottingham",
                    JourneyMapPointRole.JOURNEY_DESTINATION,
                    GeographicCoordinate(52.9548, -1.1581),
                ),
            ),
            line = JourneyMapLine.VisualConnection,
        )

        assertFalse(route.hasCompleteCoordinates)
    }

    @Test
    fun rendererRequiresBothConfigurationAndCompleteCoordinates() {
        val completeRoute = journeyMap(
            "Mansfield",
            "Nottingham",
            GeographicCoordinate(53.1432, -1.1984),
            GeographicCoordinate(52.9548, -1.1581),
        )

        assertSame(
            JourneyRouteRenderer.SCHEMATIC,
            journeyRouteRenderer(completeRoute, mapsConfigured = false),
        )
        assertSame(
            JourneyRouteRenderer.SCHEMATIC,
            journeyRouteRenderer(broadAreaJourneyMap("York", "Leeds"), mapsConfigured = true),
        )
        assertSame(
            JourneyRouteRenderer.GOOGLE_GEOGRAPHIC_PREVIEW,
            journeyRouteRenderer(completeRoute, mapsConfigured = true),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun roadRouteRequiresAtLeastTwoGeometryPoints() {
        JourneyMapLine.RoadRoute(listOf(GeographicCoordinate(53.0, -1.0)))
    }
}
