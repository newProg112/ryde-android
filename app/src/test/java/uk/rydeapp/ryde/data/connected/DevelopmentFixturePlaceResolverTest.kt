package uk.rydeapp.ryde.data.connected

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import uk.rydeapp.ryde.domain.PlaceResolution
import uk.rydeapp.ryde.domain.PlaceMatch
import uk.rydeapp.ryde.domain.model.GeographicCoordinate

class DevelopmentFixturePlaceResolverTest {
    private val resolver = DevelopmentFixturePlaceResolver()

    @Test
    fun `known fixture places resolve deterministically after safe normalisation`() = runBlocking {
        assertEquals(
            PlaceResolution.Unique(PlaceMatch("Mansfield", GeographicCoordinate(53.1432, -1.1984))),
            resolver.resolve("  MANSFIELD "),
        )
        assertEquals(
            PlaceResolution.Unique(PlaceMatch("Nottingham", GeographicCoordinate(52.9548, -1.1581))),
            resolver.resolve("Nottingham"),
        )
    }

    @Test
    fun `ambiguous fixture returns clearly distinguishable broad-area candidates`() = runBlocking {
        assertEquals(
            PlaceResolution.Multiple(
                listOf(
                    PlaceMatch("Richmond — Greater London", GeographicCoordinate(51.4613, -0.3037)),
                    PlaceMatch("Richmond — North Yorkshire", GeographicCoordinate(54.4037, -1.7375)),
                ),
            ),
            resolver.resolve(" RICHMOND "),
        )
    }

    @Test
    fun `unknown place has no result and receives no fallback coordinate`() = runBlocking {
        assertSame(PlaceResolution.NoMatches, resolver.resolve("Somewhere Else"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `resolution results cannot contain invalid coordinates`() {
        PlaceResolution.Unique(PlaceMatch("Invalid", GeographicCoordinate(91.0, 0.0)))
    }
}
