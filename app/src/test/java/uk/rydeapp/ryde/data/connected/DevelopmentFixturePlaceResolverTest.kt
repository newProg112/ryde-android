package uk.rydeapp.ryde.data.connected

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import uk.rydeapp.ryde.domain.PlaceResolution
import uk.rydeapp.ryde.domain.model.GeographicCoordinate

class DevelopmentFixturePlaceResolverTest {
    private val resolver = DevelopmentFixturePlaceResolver()

    @Test
    fun `known fixture places resolve deterministically after safe normalisation`() = runBlocking {
        assertEquals(
            PlaceResolution.Resolved(GeographicCoordinate(53.1432, -1.1984)),
            resolver.resolve("  MANSFIELD "),
        )
        assertEquals(
            PlaceResolution.Resolved(GeographicCoordinate(52.9548, -1.1581)),
            resolver.resolve("Nottingham"),
        )
    }

    @Test
    fun `unknown place has no result and receives no fallback coordinate`() = runBlocking {
        assertSame(PlaceResolution.NoResult, resolver.resolve("Somewhere Else"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `resolution results cannot contain invalid coordinates`() {
        PlaceResolution.Resolved(GeographicCoordinate(91.0, 0.0))
    }
}
