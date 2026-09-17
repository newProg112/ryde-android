package uk.rydeapp.ryde.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.rydeapp.ryde.data.connected.ConnectedJourney
import uk.rydeapp.ryde.data.connected.ConnectedJourneyLifecycle
import uk.rydeapp.ryde.data.connected.ConnectedJourneySnapshot
import uk.rydeapp.ryde.data.connected.ConnectedJourneyStatus
import uk.rydeapp.ryde.data.connected.ConnectedRequestStatus
import uk.rydeapp.ryde.data.connected.ConnectedSeatRequest
import uk.rydeapp.ryde.ui.account.ConnectedRiderRequestItem
import uk.rydeapp.ryde.ui.account.canRerequestConnectedSeat

class ConnectedHomeUiTest {
    private val journey = ConnectedJourney("genuine", "driver", "Mansfield", "Nottingham", 200, 2, 1)

    @Test
    fun `home eligibility agrees with connected lifecycle and orders by departure`() {
        val journeys = listOf(
            journey.copy(id = "later", departureEpochMillis = 300),
            journey.copy(id = "own", driverUid = "rider"),
            journey.copy(id = "cancelled", status = ConnectedJourneyStatus.CANCELLED),
            journey.copy(id = "departed", departureEpochMillis = 100),
            journey,
        )
        val items = connectedHomeJourneys(ConnectedJourneySnapshot(journeys), "rider", 100)
        assertEquals(listOf("genuine", "later"), items.map { it.journey.id })
        assertEquals(
            journeys.filter { ConnectedJourneyLifecycle.discoverable(it, "rider", 100) }.toSet(),
            items.map { it.journey }.toSet(),
        )
        assertTrue(items.all { it.canRequest })
        assertTrue(connectedHomeJourneys(ConnectedJourneySnapshot(journeys), "rider", 300).isEmpty())
    }

    @Test
    fun `full journeys and every existing rider request cannot create a new request on home`() {
        assertFalse(connectedHomeJourneys(
            ConnectedJourneySnapshot(listOf(journey.copy(seatsRemaining = 0))), "rider", 100,
        ).single().canRequest)
        ConnectedRequestStatus.entries.forEach { status ->
            val request = ConnectedSeatRequest("genuine_rider", journey.id, "driver", "rider", status)
            val item = connectedHomeJourneys(
                ConnectedJourneySnapshot(listOf(journey), listOf(request)), "rider", 100,
            ).single()
            assertEquals(request, item.request)
            assertFalse(item.canRequest)
            assertEquals(status == ConnectedRequestStatus.CANCELLED, item.canRerequest)
        }
    }

    @Test
    fun `another riders request never appears as the viewers request`() {
        val otherRequest = ConnectedSeatRequest("genuine_other", journey.id, "driver", "other", ConnectedRequestStatus.PENDING)
        val item = connectedHomeJourneys(
            ConnectedJourneySnapshot(listOf(journey), listOf(otherRequest)), "rider", 100,
        ).single()
        assertEquals(null, item.request)
        assertTrue(item.canRequest)
    }

    @Test
    fun `find re-request eligibility reuses the existing connected predicate and fails closed`() {
        val request = ConnectedSeatRequest("genuine_rider", journey.id, "driver", "rider", ConnectedRequestStatus.CANCELLED)
        listOf(
            journey,
            journey.copy(seatsRemaining = 0),
            journey.copy(driverUid = "different-driver"),
        ).forEach { linkedJourney ->
            val item = connectedHomeJourneys(
                ConnectedJourneySnapshot(listOf(linkedJourney), listOf(request)), "rider", 100,
            ).single()
            assertEquals(
                canRerequestConnectedSeat(ConnectedRiderRequestItem(request, linkedJourney), 100),
                item.canRerequest,
            )
            assertFalse(item.canRequest)
        }
        assertTrue(connectedHomeJourneys(
            ConnectedJourneySnapshot(listOf(journey), listOf(request)), "rider", 200,
        ).isEmpty())
    }

    @Test
    fun `an empty connected snapshot supplies no fabricated journeys`() {
        assertTrue(connectedHomeJourneys(ConnectedJourneySnapshot(), "rider", 100).isEmpty())
    }
}
