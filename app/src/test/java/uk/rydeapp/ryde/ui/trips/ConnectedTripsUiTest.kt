package uk.rydeapp.ryde.ui.trips

import org.junit.Assert.*
import org.junit.Test
import uk.rydeapp.ryde.R
import uk.rydeapp.ryde.data.connected.*

class ConnectedTripsUiTest {
    private val journey = ConnectedJourney("offer", "private-driver", "York", "Leeds", 1000, 2, 1)
    private val request = ConnectedSeatRequest("offer_private-rider", journey.id, journey.driverUid,
        "private-rider", ConnectedRequestStatus.PENDING, "Riley Rider")
    private val trip = ConnectedConfirmedTrip(request.id, journey.id, request.id, journey.driverUid,
        request.riderUid, journey.originArea, journey.destinationArea, journey.departureEpochMillis,
        ConnectedTripStatus.CONFIRMED)
    private fun content(j: List<ConnectedJourney> = listOf(journey), r: List<ConnectedSeatRequest> = listOf(request),
        t: List<ConnectedConfirmedTrip> = emptyList(), uid: String = request.riderUid, now: Long = 0) =
        connectedTripsContent(ConnectedJourneySnapshot(j, r, t), uid, now)

    @Test fun `entries carry explicit journey ids for requests bookings owners and missing links`() {
        assertEquals(journey.id, content().rider.single().journeyId)
        assertEquals(journey.id, content(t = listOf(trip)).rider.single().journeyId)
        assertEquals(journey.id, content(uid = journey.driverUid).driver.single().journeyId)
        assertEquals(journey.id, content(j = emptyList()).rider.single().journeyId)
        assertEquals(journey.id, content(j = emptyList(), t = listOf(trip)).rider.single().journeyId)
    }

    @Test fun `empty repository and unrelated user have no trips`() {
        assertEquals(ConnectedTripsContent(emptyList(), emptyList()), content(emptyList(), emptyList()))
        assertEquals(ConnectedTripsContent(emptyList(), emptyList()), content(t = listOf(trip), uid = "stranger"))
    }

    @Test fun `pending joins genuine broad areas departure and exposes request id for withdrawal`() {
        val item = content().rider.single()
        assertEquals("York", item.origin)
        assertEquals("Leeds", item.destination)
        assertEquals(1000L, item.departureEpochMillis)
        assertEquals(R.string.connected_request_pending, item.statusText)
        assertEquals(R.string.connected_trips_rider, item.roleText)
        assertNull(item.cancellableTripId)
        assertEquals(request.id, item.cancellableRequestId)
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
            assertNull(item.cancellableRequestId)
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
            assertNull(item.cancellableRequestId)
        }
        assertNull(content(j = listOf(journey.copy(status = ConnectedJourneyStatus.CANCELLED))).rider.single().cancellableRequestId)
        assertNull(content(r = listOf(request.copy(riderUid = "other"))).rider.singleOrNull())
    }

    @Test fun `owned journey is separate driver state with no rider action`() {
        val item = content(uid = journey.driverUid).driver.single()
        assertEquals(R.string.connected_trips_driver, item.roleText)
        assertEquals(R.string.connected_trips_offer_open, item.statusText)
        assertEquals("York", item.origin)
        assertNull(item.cancellableTripId)
        assertNull(item.cancellableRequestId)
        assertEquals(R.string.connected_trips_offer_cancelled,
            content(uid = journey.driverUid, j = listOf(journey.copy(status = ConnectedJourneyStatus.CANCELLED))).driver.single().statusText)
    }

    @Test fun `withdrawal eligibility uses lifecycle truth and blank action ids fail closed`() {
        assertEquals(request.id, content(now = journey.departureEpochMillis).rider.single().cancellableRequestId)
        assertNull(content(r = listOf(request.copy(id = ""))).rider.single().cancellableRequestId)
        assertNull(content(j = listOf(journey.copy(driverUid = "different"))).rider.single().cancellableRequestId)
        assertNull(content(j = listOf(journey.copy(status = ConnectedJourneyStatus.CANCELLED))).rider.single().cancellableRequestId)
    }

    @Test fun `driver sees genuine capacity requests and valid cancellation`() {
        val item = content(uid = journey.driverUid).driver.single()
        assertEquals(1, item.seatsRemaining)
        assertEquals(2, item.seatCapacity)
        assertEquals(journey.id, item.cancellableJourneyId)
        assertEquals(request.id, item.incoming.single().id)
        assertEquals("Riley Rider", item.incoming.single().riderDisplayName)
        assertTrue(item.incoming.single().canAccept)
        assertTrue(item.incoming.single().canDecline)
        assertEquals(R.string.connected_incoming_pending, item.incoming.single().statusText)
    }

    @Test fun `decision guards match ownership pending open capacity and time`() {
        assertTrue(canDecideConnectedRequest(request, journey, journey.driverUid, true, 0))
        listOf(null, journey.copy(driverUid = "other"), journey.copy(status = ConnectedJourneyStatus.CANCELLED)).forEach {
            assertFalse(canDecideConnectedRequest(request, it, journey.driverUid, true, 0))
            assertFalse(canDecideConnectedRequest(request, it, journey.driverUid, false, 0))
        }
        assertFalse(canDecideConnectedRequest(request, journey, request.riderUid, true, 0))
        ConnectedRequestStatus.entries.filter { it != ConnectedRequestStatus.PENDING }.forEach {
            assertFalse(canDecideConnectedRequest(request.copy(status = it), journey, journey.driverUid, true, 0))
            assertFalse(canDecideConnectedRequest(request.copy(status = it), journey, journey.driverUid, false, 0))
        }
        assertFalse(canDecideConnectedRequest(request, journey.copy(seatsRemaining = 0), journey.driverUid, true, 0))
        assertFalse(canDecideConnectedRequest(request, journey, journey.driverUid, true, 1000))
        assertTrue(canDecideConnectedRequest(request, journey.copy(seatsRemaining = 0), journey.driverUid, false, 1000))
    }

    @Test fun `driver lifecycle retains request history without invalid actions`() {
        val closed = content(uid = journey.driverUid, j = listOf(journey.copy(status = ConnectedJourneyStatus.CANCELLED))).driver.single()
        assertNull(closed.cancellableJourneyId)
        assertEquals(R.string.connected_trips_offer_cancelled, closed.incoming.single().statusText)
        assertFalse(closed.incoming.single().canAccept)
        assertFalse(closed.incoming.single().canDecline)
        assertNull(content(uid = journey.driverUid, now = 1000).driver.single().cancellableJourneyId)
        assertEquals(R.string.connected_offer_departed, content(uid = journey.driverUid, now = 1000).driver.single().statusText)
        val missing = content(uid = journey.driverUid, j = emptyList()).unavailableIncoming.single()
        assertEquals(R.string.connected_trips_unavailable, missing.statusText)
        assertFalse(missing.canAccept)
        assertFalse(missing.canDecline)
        val unrelated = request.copy(driverUid = "someone-else")
        assertTrue(content(uid = journey.driverUid, r = listOf(unrelated)).driver.single().incoming.isEmpty())
        assertTrue(content(uid = journey.driverUid, r = listOf(unrelated)).unavailableIncoming.isEmpty())
    }

    @Test fun `driver terminal request labels retain safe rider snapshot and legacy fallback`() {
        mapOf(
            ConnectedRequestStatus.ACCEPTED to R.string.connected_incoming_accepted,
            ConnectedRequestStatus.DECLINED to R.string.connected_incoming_declined,
            ConnectedRequestStatus.CANCELLED to R.string.connected_incoming_cancelled,
            ConnectedRequestStatus.CANCELLED_AFTER_ACCEPTANCE to R.string.connected_incoming_seat_cancelled,
        ).forEach { (status, label) ->
            val incoming = content(uid = journey.driverUid, r = listOf(request.copy(status = status))).driver.single().incoming.single()
            assertEquals(label, incoming.statusText)
            assertEquals("Riley Rider", incoming.riderDisplayName)
            assertFalse(incoming.canAccept)
            assertFalse(incoming.canDecline)
        }
        assertNull(content(uid = journey.driverUid, r = listOf(request.copy(riderDisplayName = null)))
            .driver.single().incoming.single().riderDisplayName)
    }

    @Test fun `declined request retains history and shows linked driver cancellation without affecting confirmed journey`() {
        val cancelled = journey.copy(id = "declined-offer", destinationArea = "Sheffield", seatsRemaining = 2,
            status = ConnectedJourneyStatus.CANCELLED)
        val declined = request.copy(id = "declined-offer_private-rider", journeyId = cancelled.id,
            status = ConnectedRequestStatus.DECLINED)
        val accepted = request.copy(status = ConnectedRequestStatus.ACCEPTED)
        val snapshot = ConnectedJourneySnapshot(listOf(journey, cancelled), listOf(accepted, declined), listOf(trip))
        val result = connectedTripsContent(snapshot, request.riderUid, 0)
        val history = result.rider.single { it.key == "request:${declined.id}" }
        assertEquals(R.string.connected_request_declined, history.statusText)
        assertEquals(R.string.connected_trips_driver_cancelled, history.journeyStatusText)
        assertEquals("Sheffield", history.destination)
        assertNull(history.cancellableTripId)
        assertEquals(content(r = listOf(accepted), t = listOf(trip)).rider.single(),
            result.rider.single { it.key == "trip:${trip.id}" })
        assertEquals(ConnectedRequestStatus.DECLINED, snapshot.requests.single { it.id == declined.id }.status)
        assertEquals(listOf(1, 2), snapshot.journeys.map { it.seatsRemaining })
    }

    @Test fun `terminal request journey context requires genuine cancelled link and preserves each request status`() {
        mapOf(
            ConnectedRequestStatus.DECLINED to R.string.connected_request_declined,
            ConnectedRequestStatus.CANCELLED to R.string.connected_request_cancelled,
            ConnectedRequestStatus.CANCELLED_AFTER_ACCEPTANCE to R.string.connected_seat_cancelled,
        ).forEach { (requestStatus, label) ->
            val historical = request.copy(status = requestStatus)
            val cancelled = journey.copy(status = ConnectedJourneyStatus.CANCELLED)
            val item = content(j = listOf(cancelled), r = listOf(historical)).rider.single()
            assertEquals(label, item.statusText)
            assertEquals(R.string.connected_trips_driver_cancelled, item.journeyStatusText)
            listOf(emptyList(), listOf(journey), listOf(cancelled.copy(driverUid = "different-driver"))).forEach {
                assertNull(content(j = it, r = listOf(historical)).rider.single().journeyStatusText)
            }
        }
        listOf(ConnectedRequestStatus.PENDING, ConnectedRequestStatus.ACCEPTED).forEach {
            val item = content(j = listOf(journey.copy(status = ConnectedJourneyStatus.CANCELLED)),
                r = listOf(request.copy(status = it))).rider.single()
            assertEquals(R.string.connected_trips_driver_cancelled, item.statusText)
            assertNull(item.journeyStatusText) // Do not repeat the already resolved cancellation label.
        }
    }
}
