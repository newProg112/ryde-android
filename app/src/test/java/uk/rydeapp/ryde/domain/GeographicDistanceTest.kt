package uk.rydeapp.ryde.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.rydeapp.ryde.domain.model.GeographicCoordinate

class GeographicDistanceTest {
    @Test
    fun `identical coordinates have zero kilometre distance`() {
        val mansfield = GeographicCoordinate(53.1432, -1.1984)

        assertEquals(0.0, HaversineGeographicDistanceCalculator.between(mansfield, mansfield).kilometres, 0.0)
    }

    @Test
    fun `Mansfield to Nottingham has expected approximate great-circle distance`() {
        val distance = HaversineGeographicDistanceCalculator.between(
            GeographicCoordinate(53.1432, -1.1984),
            GeographicCoordinate(52.9548, -1.1581),
        )

        assertEquals(21.12, distance.kilometres, 0.15)
    }

    @Test
    fun `antimeridian crossing uses short great-circle path`() {
        val distance = HaversineGeographicDistanceCalculator.between(
            GeographicCoordinate(0.0, 179.9),
            GeographicCoordinate(0.0, -179.9),
        )

        assertEquals(22.24, distance.kilometres, 0.1)
    }

    @Test
    fun `antipodal boundary remains finite`() {
        val distance = HaversineGeographicDistanceCalculator.between(
            GeographicCoordinate(90.0, 0.0),
            GeographicCoordinate(-90.0, 180.0),
        )

        assertTrue(distance.kilometres.isFinite())
        assertEquals(20_015.1, distance.kilometres, 0.2)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `distance value rejects invalid negative kilometres`() {
        GeographicDistance(-0.001)
    }
}
