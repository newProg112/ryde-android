package uk.rydeapp.ryde.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import uk.rydeapp.ryde.domain.model.GeographicCoordinate

class PlaceResolutionTest {
    @Test
    fun `unique resolution retains only neutral label and coordinate`() {
        val match = PlaceMatch("Mansfield", GeographicCoordinate(53.1432, -1.1984))
        val resolution = PlaceResolution.Unique(match)

        assertEquals("Mansfield", resolution.match.broadAreaLabel)
        assertEquals(GeographicCoordinate(53.1432, -1.1984), resolution.match.coordinate)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `multiple resolution requires at least two candidates`() {
        PlaceResolution.Multiple(
            listOf(PlaceMatch("Mansfield", GeographicCoordinate(53.1432, -1.1984))),
        )
    }
}
