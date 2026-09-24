package uk.rydeapp.ryde.ui.place

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.rydeapp.ryde.domain.PlaceMatch
import uk.rydeapp.ryde.domain.PlaceResolution
import uk.rydeapp.ryde.domain.model.GeographicCoordinate

class BroadAreaPlaceSelectionTest {
    private val mansfield = PlaceMatch("Mansfield", GeographicCoordinate(53.1432, -1.1984))
    private val nottingham = PlaceMatch("Nottingham", GeographicCoordinate(52.9548, -1.1581))
    private val londonRichmond = PlaceMatch(
        "Richmond — Greater London",
        GeographicCoordinate(51.4613, -0.3037),
    )
    private val yorkshireRichmond = PlaceMatch(
        "Richmond — North Yorkshire",
        GeographicCoordinate(54.4037, -1.7375),
    )
    private val richmondMatches = listOf(londonRichmond, yorkshireRichmond)

    @Test
    fun `unique From and To resolutions carry both neutral coordinates`() {
        val selection = BroadAreaPlaceSelection.from(
            "Mansfield",
            "Nottingham",
            PlaceResolution.Unique(mansfield),
            PlaceResolution.Unique(nottingham),
        )

        assertTrue(selection.isComplete)
        assertNull(selection.prompt)
        assertEquals(mansfield.coordinate, selection.coordinates.from)
        assertEquals(nottingham.coordinate, selection.coordinates.to)
    }

    @Test
    fun `ambiguous From waits for explicit selection and carries selected coordinate`() {
        val unresolved = BroadAreaPlaceSelection.from(
            "Richmond",
            "Nottingham",
            PlaceResolution.Multiple(richmondMatches),
            PlaceResolution.Unique(nottingham),
        )

        assertFalse(unresolved.isComplete)
        assertEquals(BroadAreaEndpoint.FROM, unresolved.prompt?.endpoint)
        assertEquals(richmondMatches, unresolved.prompt?.candidates)

        val selected = unresolved.select(BroadAreaEndpoint.FROM, londonRichmond)

        assertTrue(selected.isComplete)
        assertEquals(londonRichmond.coordinate, selected.coordinates.from)
        assertEquals(nottingham.coordinate, selected.coordinates.to)
    }

    @Test
    fun `ambiguous To identifies destination field and selection`() {
        val unresolved = BroadAreaPlaceSelection.from(
            "Mansfield",
            "Richmond",
            PlaceResolution.Unique(mansfield),
            PlaceResolution.Multiple(richmondMatches),
        )

        assertEquals(BroadAreaEndpoint.TO, unresolved.prompt?.endpoint)

        val selected = unresolved.select(BroadAreaEndpoint.TO, yorkshireRichmond)

        assertEquals(mansfield.coordinate, selected.coordinates.from)
        assertEquals(yorkshireRichmond.coordinate, selected.coordinates.to)
    }

    @Test
    fun `unresolved From or To remains independently coordinate-less`() {
        val unresolvedFrom = BroadAreaPlaceSelection.from(
            "Unknown",
            "Nottingham",
            PlaceResolution.NoMatches,
            PlaceResolution.Unique(nottingham),
        )
        val unresolvedTo = BroadAreaPlaceSelection.from(
            "Mansfield",
            "Unknown",
            PlaceResolution.Unique(mansfield),
            PlaceResolution.NoMatches,
        )

        assertNull(unresolvedFrom.coordinates.from)
        assertEquals(nottingham.coordinate, unresolvedFrom.coordinates.to)
        assertEquals(mansfield.coordinate, unresolvedTo.coordinates.from)
        assertNull(unresolvedTo.coordinates.to)
    }

    @Test
    fun `resolver failure remains a safe coordinate-less endpoint`() {
        val selection = BroadAreaPlaceSelection.from(
            "Unavailable",
            "Nottingham",
            PlaceResolution.Failure,
            PlaceResolution.Unique(nottingham),
        )

        assertTrue(selection.isComplete)
        assertNull(selection.coordinates.from)
        assertEquals(nottingham.coordinate, selection.coordinates.to)
    }
}
