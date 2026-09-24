package uk.rydeapp.ryde.ui.find

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.rydeapp.ryde.data.connected.DevelopmentFixturePlaceResolver
import uk.rydeapp.ryde.domain.PlaceResolution
import uk.rydeapp.ryde.ui.place.BroadAreaEndpoint

class ConnectedFindPlaceResolutionTest {
    private val fixture = DevelopmentFixturePlaceResolver()

    @Test
    fun `unique From and To fixture places resolve to coordinates`() = runBlocking {
        val selection = resolveConnectedFindPlaces(
            ConnectedFindCriteria("Mansfield", "Nottingham"),
            fixture::resolve,
        )

        assertTrue(selection.isComplete)
        assertEquals(53.1432, selection.coordinates.from?.latitude ?: 0.0, 0.0)
        assertEquals(-1.1581, selection.coordinates.to?.longitude ?: 0.0, 0.0)
    }

    @Test
    fun `blank unresolved From and To do not call resolver`() = runBlocking {
        val calls = mutableListOf<String>()
        val selection = resolveConnectedFindPlaces(ConnectedFindCriteria()) {
            calls += it
            error("Blank areas must not be resolved")
        }

        assertTrue(calls.isEmpty())
        assertNull(selection.coordinates.from)
        assertNull(selection.coordinates.to)
    }

    @Test
    fun `ambiguous Richmond identifies From field`() = runBlocking {
        val selection = resolveConnectedFindPlaces(
            ConnectedFindCriteria("Richmond", "Nottingham"),
            fixture::resolve,
        )

        assertEquals(BroadAreaEndpoint.FROM, selection.prompt?.endpoint)
        assertEquals(2, selection.prompt?.candidates?.size)
    }

    @Test
    fun `ambiguous Richmond identifies To field`() = runBlocking {
        val selection = resolveConnectedFindPlaces(
            ConnectedFindCriteria("Mansfield", "Richmond"),
            fixture::resolve,
        )

        assertEquals(BroadAreaEndpoint.TO, selection.prompt?.endpoint)
        assertEquals(2, selection.prompt?.candidates?.size)
    }

    @Test
    fun `resolver failure remains complete and coordinate-less`() = runBlocking {
        val selection = resolveConnectedFindPlaces(
            ConnectedFindCriteria("Unavailable", "Nottingham"),
        ) { broadArea ->
            if (broadArea == "Unavailable") PlaceResolution.Failure else fixture.resolve(broadArea)
        }

        assertTrue(selection.isComplete)
        assertNull(selection.coordinates.from)
        assertEquals(-1.1581, selection.coordinates.to?.longitude ?: 0.0, 0.0)
    }
}
