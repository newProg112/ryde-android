package uk.rydeapp.ryde.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

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
        GeoCoordinate(90.1, 0.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun roadRouteRequiresAtLeastTwoGeometryPoints() {
        JourneyMapLine.RoadRoute(listOf(GeoCoordinate(53.0, -1.0)))
    }
}
