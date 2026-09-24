package uk.rydeapp.ryde.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class GeographicCoordinateTest {
    @Test
    fun `boundary coordinates are valid`() {
        assertEquals(-90.0, GeographicCoordinate(-90.0, -180.0).latitude, 0.0)
        assertEquals(180.0, GeographicCoordinate(90.0, 180.0).longitude, 0.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `non finite coordinate is rejected`() {
        GeographicCoordinate(Double.NaN, 0.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `out of range longitude is rejected`() {
        GeographicCoordinate(0.0, 180.1)
    }
}
