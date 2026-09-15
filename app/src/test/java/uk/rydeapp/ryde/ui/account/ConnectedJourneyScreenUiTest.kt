package uk.rydeapp.ryde.ui.account

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.rydeapp.ryde.data.connected.ConnectedRequestStatus
import java.time.LocalDateTime
import java.time.ZoneId

class ConnectedJourneyScreenUiTest {
    @Test
    fun `connected journey navigation exposes the five focused sections in order`() {
        assertEquals(
            listOf(
                "Profile",
                "Offer a journey",
                "Discover offers",
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
