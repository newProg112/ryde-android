package uk.rydeapp.ryde.ui.account

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.rydeapp.ryde.data.connected.ConnectedRequestStatus
import uk.rydeapp.ryde.data.connected.ConnectedJourney
import uk.rydeapp.ryde.data.connected.ConnectedJourneySnapshot
import uk.rydeapp.ryde.data.connected.ConnectedSeatRequest
import java.time.LocalDateTime
import java.time.ZoneId

class ConnectedJourneyScreenUiTest {
    @Test
    fun `connected journey navigation exposes persistent rider requests`() {
        assertEquals(
            listOf(
                "Profile",
                "Offer a journey",
                "Discover offers",
                "Your requests",
                "Your offers",
                "Incoming requests",
            ),
            ConnectedJourneySection.entries.map { it.label },
        )
    }

    @Test
    fun `seat availability and every request status remain explicit`() {
        assertEquals("2/3 seats remaining", connectedSeatAvailabilityLabel(2, 3))
        assertEquals("Status: PENDING", connectedRequestStatusLabel(ConnectedRequestStatus.PENDING))
        assertEquals("Status: ACCEPTED", connectedRequestStatusLabel(ConnectedRequestStatus.ACCEPTED))
        assertEquals("Status: DECLINED", connectedRequestStatusLabel(ConnectedRequestStatus.DECLINED))
        assertEquals("Status: CANCELLED", connectedRequestStatusLabel(ConnectedRequestStatus.CANCELLED))
    }

    @Test
    fun `rider request presentation joins persisted request to genuine journey data`() {
        val departure = 4_070_908_800_000L
        val journey = ConnectedJourney(
            id = "journey-1",
            driverUid = "driver",
            originArea = "Mansfield",
            destinationArea = "Nottingham",
            departureEpochMillis = departure,
            seatCapacity = 2,
            seatsRemaining = 1,
        )
        val request = ConnectedSeatRequest(
            id = "journey-1_rider",
            journeyId = journey.id,
            driverUid = journey.driverUid,
            riderUid = "rider",
            status = ConnectedRequestStatus.CANCELLED,
        )
        val snapshot = ConnectedJourneySnapshot(
            journeys = listOf(journey),
            requests = listOf(request),
        )

        val item = connectedRiderRequestItems(snapshot, "rider").single()

        assertEquals("Mansfield", item.journey.originArea)
        assertEquals("Nottingham", item.journey.destinationArea)
        assertEquals(departure, item.journey.departureEpochMillis)
        assertEquals(ConnectedRequestStatus.CANCELLED, item.request.status)
        assertTrue(canRerequestConnectedSeat(item, nowEpochMillis = departure - 1))
        assertFalse(canRerequestConnectedSeat(item, nowEpochMillis = departure + 1))
        assertFalse(canRerequestConnectedSeat(item.copy(journey = journey.copy(seatsRemaining = 0)), departure - 1))
        assertFalse(canRerequestConnectedSeat(item.copy(request = request.copy(status = ConnectedRequestStatus.ACCEPTED)), departure - 1))
    }

    @Test
    fun `connected departure defaults to tomorrow morning`() {
        val zone = ZoneId.of("Europe/London")
        val now = LocalDateTime.of(2026, 9, 15, 19, 30).atZone(zone).toInstant().toEpochMilli()

        val selection = defaultConnectedDeparture(now, zone)

        assertEquals("2026-09-16 09:00", formatConnectedDepartureForSubmission(selection))
        assertEquals("Wed, 16 Sept 2026 at 09:00", formatConnectedDepartureForDisplay(selection))
        assertTrue(isConnectedDepartureFuture(selection, now, zone))
    }

    @Test
    fun `connected departure future check uses the selected local date and time`() {
        val zone = ZoneId.of("Europe/London")
        val now = LocalDateTime.of(2026, 9, 15, 12, 0).atZone(zone).toInstant().toEpochMilli()
        val selectedDate = LocalDateTime.of(2026, 9, 15, 0, 0).toLocalDate().toEpochDay()

        assertFalse(isConnectedDepartureFuture(ConnectedDepartureSelection(selectedDate, 11 * 60 + 59), now, zone))
        assertTrue(isConnectedDepartureFuture(ConnectedDepartureSelection(selectedDate, 12 * 60 + 1), now, zone))
    }
}
