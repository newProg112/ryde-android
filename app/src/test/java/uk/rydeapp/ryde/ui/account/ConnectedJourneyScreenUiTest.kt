package uk.rydeapp.ryde.ui.account

import org.junit.Assert.assertEquals
import org.junit.Test
import uk.rydeapp.ryde.data.connected.ConnectedRequestStatus

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
}
