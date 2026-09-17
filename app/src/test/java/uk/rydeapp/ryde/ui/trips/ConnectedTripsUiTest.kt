package uk.rydeapp.ryde.ui.trips

import org.junit.Assert.*
import org.junit.Test
import uk.rydeapp.ryde.R
import uk.rydeapp.ryde.data.connected.*

class ConnectedTripsUiTest {
    private val journey = ConnectedJourney("offer", "private-driver", "York", "Leeds", 1000, 2, 1)
    private val request = ConnectedSeatRequest("offer_private-rider", journey.id, journey.driverUid,
        "private-rider", ConnectedRequestStatus.PENDING)
    private val trip = ConnectedConfirmedTrip(request.id, journey.id, request.id, journey.driverUid,
        request.riderUid, journey.originArea, journey.destinationArea, journey.departureEpochMillis,
        ConnectedTripStatus.CONFIRMED)
    private fun content(j: List<ConnectedJourney> = listOf(journey), r: List<ConnectedSeatRequest> = listOf(request),
        t: List<ConnectedConfirmedTrip> = emptyList(), uid: String = request.riderUid, now: Long = 0) =
        connectedTripsContent(ConnectedJourneySnapshot(j, r, t), uid, now)

    @Test fun `empty repository and unrelated user have no trips`() {
        assertEquals(ConnectedTripsContent(emptyList(), emptyList()), content(emptyList(), emptyList()))
        assertEquals(ConnectedTripsContent(emptyList(), emptyList()), content(t = listOf(trip), uid = "stranger"))
    }

    @Test fun `pending joins genuine broad areas departure and role without cancellation`() {
        val item = content().rider.single()
        assertEquals("York", item.origin)
        assertEquals("Leeds", item.destination)
        assertEquals(1000L, item.departureEpochMillis)
        assertEquals(R.string.connected_request_pending, item.statusText)
        assertEquals(R.string.connected_trips_rider, item.roleText)
        assertNull(item.cancellableTripId)
    }

    @Test fun `confirmed trip replaces linked request and uses persisted trip fields`() {
        val item = content(r = listOf(request.copy(status = ConnectedRequestStatus.ACCEPTED)),
            t = listOf(trip.copy(originArea = "Sheffield", departureEpochMillis = 999))).rider.single()
        assertEquals("Sheffield", item.origin)
        assertEquals("Leeds", item.destination)
        assertEquals(999L, item.departureEpochMillis)
        assertEquals(R.string.connected_request_accepted, item.statusText)
        assertEquals(trip.id, item.cancellableTripId)
    }

    @Test fun `cancellation follows existing lifecycle ownership and departure`() {
        assertNull(content(t = listOf(trip), now = 1000).rider.single().cancellableTripId)
        val closed = journey.copy(status = ConnectedJourneyStatus.CANCELLED)
        val driverCancelled = content(j = listOf(closed), t = listOf(trip)).rider.single()
        assertEquals(R.string.connected_trips_driver_cancelled, driverCancelled.statusText)
        assertNull(driverCancelled.cancellableTripId)
        val missing = content(j = emptyList(), t = listOf(trip)).rider.single()
        assertEquals(R.string.connected_trips_unavailable, missing.statusText)
        assertNull(missing.cancellableTripId)
        val riderCancelled = content(j = listOf(closed), t = listOf(trip.copy(status = ConnectedTripStatus.CANCELLED_BY_RIDER))).rider.single()
        assertEquals(R.string.connected_trips_cancelled, riderCancelled.statusText)
        assertNull(riderCancelled.cancellableTripId)
        assertTrue(content(t = listOf(trip), uid = journey.driverUid).rider.isEmpty())
    }

    @Test fun `terminal request states and driver cancellation are genuine`() {
        mapOf(
            ConnectedRequestStatus.ACCEPTED to R.string.connected_request_accepted,
            ConnectedRequestStatus.DECLINED to R.string.connected_request_declined,
            ConnectedRequestStatus.CANCELLED to R.string.connected_request_cancelled,
            ConnectedRequestStatus.CANCELLED_AFTER_ACCEPTANCE to R.string.connected_seat_cancelled,
        ).forEach { (status, label) ->
            val item = content(r = listOf(request.copy(status = status))).rider.single()
            assertEquals(label, item.statusText)
            assertNull(item.cancellableTripId)
        }
        assertEquals(R.string.connected_trips_driver_cancelled,
            content(j = listOf(journey.copy(status = ConnectedJourneyStatus.CANCELLED))).rider.single().statusText)
    }

    @Test fun `missing or mismatched journey keeps request without fictional route`() {
        listOf(emptyList(), listOf(journey.copy(driverUid = "someone-else"))).forEach { journeys ->
            val item = content(j = journeys).rider.single()
            assertNull(item.origin)
            assertNull(item.destination)
            assertNull(item.departureEpochMillis)
            assertEquals(R.string.connected_trips_unavailable, item.statusText)
        }
    }

    @Test fun `owned journey is separate driver state with no rider action`() {
        val item = content(uid = journey.driverUid).driver.single()
        assertEquals(R.string.connected_trips_driver, item.roleText)
        assertEquals(R.string.connected_trips_offer_open, item.statusText)
        assertEquals("York", item.origin)
        assertNull(item.cancellableTripId)
        assertEquals(R.string.connected_trips_offer_cancelled,
            content(uid = journey.driverUid, j = listOf(journey.copy(status = ConnectedJourneyStatus.CANCELLED))).driver.single().statusText)
    }
}
