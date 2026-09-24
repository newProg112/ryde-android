package uk.rydeapp.ryde.ui.offer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.rydeapp.ryde.domain.PlaceMatch
import uk.rydeapp.ryde.domain.PlaceResolution
import uk.rydeapp.ryde.domain.model.GeographicCoordinate

class OfferPlaceSelectionTest {
    private val mansfield = PlaceMatch("Mansfield", GeographicCoordinate(53.1432, -1.1984))
    private val londonRichmond = PlaceMatch(
        "Richmond — Greater London",
        GeographicCoordinate(51.4613, -0.3037),
    )
    private val yorkshireRichmond = PlaceMatch(
        "Richmond — North Yorkshire",
        GeographicCoordinate(54.4037, -1.7375),
    )

    @Test
    fun `unique endpoints are immediately complete`() {
        val selection = OfferPlaceSelection.from(
            "Mansfield",
            "Richmond",
            PlaceResolution.Unique(mansfield),
            PlaceResolution.Unique(yorkshireRichmond),
        )

        assertTrue(selection.isComplete)
        assertNull(selection.prompt)
        assertEquals(mansfield.coordinate, selection.coordinates?.origin)
        assertEquals(yorkshireRichmond.coordinate, selection.coordinates?.destination)
    }

    @Test
    fun `ambiguous endpoint requires explicit candidate and uses only selected coordinate`() {
        val unresolved = OfferPlaceSelection.from(
            "Mansfield",
            "Richmond",
            PlaceResolution.Unique(mansfield),
            PlaceResolution.Multiple(listOf(londonRichmond, yorkshireRichmond)),
        )

        assertFalse(unresolved.isComplete)
        assertEquals(OfferPlaceEndpoint.DESTINATION, unresolved.prompt?.endpoint)
        assertEquals(listOf(londonRichmond, yorkshireRichmond), unresolved.prompt?.candidates)

        val selected = unresolved.select(OfferPlaceEndpoint.DESTINATION, yorkshireRichmond)

        assertTrue(selected.isComplete)
        assertEquals(mansfield.coordinate, selected.coordinates?.origin)
        assertEquals(yorkshireRichmond.coordinate, selected.coordinates?.destination)
    }

    @Test
    fun `no match or failure completes with coordinate-less fallback`() {
        listOf(PlaceResolution.NoMatches, PlaceResolution.Failure).forEach { unavailable ->
            val selection = OfferPlaceSelection.from(
                "Unknown",
                "Mansfield",
                unavailable,
                PlaceResolution.Unique(mansfield),
            )

            assertTrue(selection.isComplete)
            assertNull(selection.coordinates)
        }
    }
}
