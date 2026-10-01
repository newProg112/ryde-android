package uk.rydeapp.ryde.ui.trips

import org.junit.Assert.*
import org.junit.Test
import uk.rydeapp.ryde.R
import uk.rydeapp.ryde.data.connected.*
import uk.rydeapp.ryde.domain.model.GeographicCoordinate

class ConnectedTripsUiTest {
    private val journey = ConnectedJourney("offer", "private-driver", "York", "Leeds", 1000, 2, 1)
    private val request = ConnectedSeatRequest("offer_private-rider", journey.id, journey.driverUid,
        "private-rider", ConnectedRequestStatus.PENDING, "Riley Rider")
    private val trip = ConnectedConfirmedTrip(request.id, journey.id, request.id, journey.driverUid,
        request.riderUid, journey.originArea, journey.destinationArea, journey.departureEpochMillis,
        ConnectedTripStatus.CONFIRMED, driverDisplayName = "Morgan Driver")
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
        val result = content()
        val item = result.riderCurrent.single()
        assertEquals("York", item.origin)
        assertEquals("Leeds", item.destination)
        assertEquals(1000L, item.departureEpochMillis)
        assertEquals(R.string.connected_request_pending, item.statusText)
        assertEquals(R.string.connected_trips_rider, item.roleText)
        assertNull(item.cancellableTripId)
        assertEquals(request.id, item.cancellableRequestId)
        assertTrue(result.riderHistory.isEmpty())
    }

    @Test fun `pending request notice follows the exact journey and stops at accepted truth`() {
        assertTrue(content().keepsPendingRequestNotice(journey.id))
        assertFalse(content().keepsPendingRequestNotice("another-offer"))
        assertFalse(content().keepsPendingRequestNotice(null))
        assertFalse(content(r = listOf(request.copy(status = ConnectedRequestStatus.ACCEPTED)))
            .keepsPendingRequestNotice(journey.id))
        // A confirmed trip wins even if listeners briefly retain the old pending request record.
        assertFalse(content(t = listOf(trip)).keepsPendingRequestNotice(journey.id))
    }

    @Test fun `confirmed trip replaces linked request and uses persisted trip fields`() {
        val result = content(r = listOf(request.copy(status = ConnectedRequestStatus.ACCEPTED)),
            t = listOf(trip.copy(originArea = "Sheffield", departureEpochMillis = 999)))
        val item = result.riderCurrent.single()
        assertEquals("Sheffield", item.origin)
        assertEquals("Leeds", item.destination)
        assertEquals(999L, item.departureEpochMillis)
        assertEquals(R.string.connected_request_accepted, item.statusText)
        assertEquals(trip.id, item.cancellableTripId)
        assertEquals("Morgan Driver", item.driverDisplayName)
        assertTrue(result.riderHistory.isEmpty())
        assertNull(content(t = listOf(trip.copy(driverDisplayName = null))).rider.single().driverDisplayName)
    }

    @Test fun `requested segment reaches rider pending driver incoming and safely linked confirmation`() {
        val segment = ConnectedRequestedBroadAreaSegment("Hucknall", "Nottingham")
        val segmented = request.copy(requestedBroadAreaSegment = segment)
        assertEquals(segment, content(r = listOf(segmented)).rider.single().requestedBroadAreaSegment)
        assertEquals(
            segment,
            content(r = listOf(segmented), uid = journey.driverUid)
                .driver.single().incoming.single().requestedBroadAreaSegment,
        )
        val accepted = segmented.copy(status = ConnectedRequestStatus.ACCEPTED)
        assertEquals(
            segment,
            content(r = listOf(accepted), t = listOf(trip)).rider.single().requestedBroadAreaSegment,
        )
        listOf(
            accepted.copy(id = "wrong"),
            accepted.copy(journeyId = "wrong"),
            accepted.copy(driverUid = "wrong"),
            accepted.copy(riderUid = "wrong"),
        ).forEach { mismatch ->
            assertNull(content(r = listOf(mismatch), t = listOf(trip)).rider
                .single { it.key == "trip:${trip.id}" }.requestedBroadAreaSegment)
        }
    }

    @Test fun `declared Via reaches driver pending rider and confirmed rider only through a valid journey link`() {
        val routed = journey.copy(
            originCoordinate = GeographicCoordinate(53.1432, -1.1984),
            destinationCoordinate = GeographicCoordinate(52.9548, -1.1581),
            routeWaypoints = listOf(ConnectedRouteWaypoint(
                "Hucknall", GeographicCoordinate(53.0380, -1.2034),
            )),
        )
        val accepted = request.copy(status = ConnectedRequestStatus.ACCEPTED)

        assertEquals("Hucknall", content(j = listOf(routed), uid = journey.driverUid).driver.single().viaArea)
        assertEquals("Hucknall", content(j = listOf(routed)).rider.single().viaArea)
        assertEquals("Hucknall", content(j = listOf(routed), r = listOf(accepted), t = listOf(trip)).rider.single().viaArea)

        assertNull(content(j = emptyList(), r = listOf(accepted), t = listOf(trip)).rider.single().viaArea)
        assertNull(content(
            j = listOf(routed.copy(destinationArea = "Different destination")),
            r = listOf(accepted),
            t = listOf(trip),
        ).rider.single().viaArea)
        assertNull(content().rider.single().viaArea)
    }

    @Test fun `rider messaging target exists only for a structurally valid confirmed trip`() {
        val item = content(r = listOf(request.copy(status = ConnectedRequestStatus.ACCEPTED)), t = listOf(trip)).rider.single()
        assertEquals(trip.id, item.messageTarget?.tripId)
        assertEquals("Morgan Driver", item.messageTarget?.otherDisplayName)
        assertNull(content(t = listOf(trip.copy(acceptedRequestId = "wrong"))).rider
            .single { it.key == "trip:${trip.id}" }.messageTarget)
        assertNull(content().rider.single().messageTarget)
    }

    @Test fun `driver gets one distinct conversation target per accepted rider only`() {
        val second = ConnectedSeatRequest("offer_second-rider", journey.id, journey.driverUid,
            "second-rider", ConnectedRequestStatus.ACCEPTED, "Second Rider")
        val secondTrip = trip.copy(
            id = second.id, acceptedRequestId = second.id, riderUid = second.riderUid,
        )
        val result = content(
            r = listOf(second, request.copy(status = ConnectedRequestStatus.ACCEPTED)),
            t = listOf(secondTrip, trip),
            uid = journey.driverUid,
        )
        assertEquals(1, result.driverCurrent.size)
        val incoming = result.driverCurrent.single().incoming
        assertEquals(listOf(trip.id, secondTrip.id), incoming.map { it.id })
        assertEquals(setOf(trip.id, secondTrip.id), incoming.mapNotNull { it.messageTarget?.tripId }.toSet())
        assertEquals(setOf("Riley Rider", "Second Rider"), incoming.mapNotNull { it.messageTarget?.otherDisplayName }.toSet())

        val ineligible = listOf(ConnectedRequestStatus.PENDING, ConnectedRequestStatus.DECLINED, ConnectedRequestStatus.CANCELLED)
        ineligible.forEach { status ->
            assertNull(content(r = listOf(request.copy(status = status)), uid = journey.driverUid)
                .driver.single().incoming.single().messageTarget)
        }
    }

    @Test fun `driver retains accepted conversation entry when linked journey disappears`() {
        val result = content(
            j = emptyList(),
            r = listOf(request.copy(status = ConnectedRequestStatus.ACCEPTED)),
            t = listOf(trip),
            uid = journey.driverUid,
        )
        val historical = result.driver.single()
        assertEquals(R.string.connected_trips_unavailable, historical.statusText)
        assertEquals(journey.id, historical.journeyId)
        assertEquals(trip.id, historical.incoming.single().messageTarget?.tripId)
        assertTrue(result.unavailableIncoming.isEmpty())
    }

    @Test fun `departed open trip remains current awaiting completion and retains rider name`() {
        val accepted = request.copy(status = ConnectedRequestStatus.ACCEPTED)
        val result = content(r = listOf(accepted), t = listOf(trip), now = 1000)
        val rider = result.riderCurrent.single()
        assertEquals(R.string.connected_trips_awaiting_completion, rider.statusText)
        assertNull(rider.cancellableTripId)
        assertEquals("York", rider.origin)
        assertEquals(1000L, rider.departureEpochMillis)
        assertEquals("Morgan Driver", rider.driverDisplayName)
        assertTrue(result.riderHistory.isEmpty())

        val driverResult = content(r = listOf(accepted), uid = journey.driverUid, now = 1000)
        val driver = driverResult.driverCurrent.single()
        assertEquals(R.string.connected_offer_awaiting_completion, driver.statusText)
        assertNull(driver.cancellableJourneyId)
        assertEquals(journey.id, driver.completableJourneyId)
        assertTrue(driverResult.driverHistory.isEmpty())
        val incoming = driver.incoming.single()
        assertEquals(R.string.connected_incoming_accepted_departed, incoming.statusText)
        assertEquals("Riley Rider", incoming.riderDisplayName)
        assertFalse(incoming.canAccept)
        assertFalse(incoming.canDecline)
    }

    @Test fun `driver can complete only a departed open journey and completion reaches rider history`() {
        val accepted = request.copy(status = ConnectedRequestStatus.ACCEPTED)
        val departedDriver = content(r = listOf(accepted), uid = journey.driverUid, now = journey.departureEpochMillis)
            .driverCurrent.single()
        assertEquals(journey.id, departedDriver.completableJourneyId)
        assertNull(departedDriver.cancellableJourneyId)

        val completed = journey.copy(status = ConnectedJourneyStatus.COMPLETED, completedAtEpochMillis = 1_001)
        val driverResult = content(j = listOf(completed), r = listOf(accepted), uid = journey.driverUid, now = 2_000)
        val driver = driverResult.driverHistory.single()
        assertEquals(R.string.connected_trips_completed, driver.statusText)
        assertNull(driver.completableJourneyId)
        assertEquals(R.string.connected_trips_completed, driver.incoming.single().statusText)
        assertTrue(driverResult.driverCurrent.isEmpty())
        val riderResult = content(j = listOf(completed), r = listOf(accepted), t = listOf(trip), now = 2_000)
        val rider = riderResult.riderHistory.single()
        assertEquals(R.string.connected_trips_completed, rider.statusText)
        assertNull(rider.cancellableTripId)
        assertNotNull(rider.messageTarget)
        assertTrue(riderResult.riderCurrent.isEmpty())

        val pendingAtCompletion = content(j = listOf(completed), now = 2_000)
        assertEquals(R.string.connected_request_pending_departed, pendingAtCompletion.riderHistory.single().statusText)
        assertTrue(pendingAtCompletion.riderCurrent.isEmpty())
    }

    @Test fun `departed pending request remains current while authorised cleanup is routed by request id`() {
        val result = content(now = 1000)
        val rider = result.riderCurrent.single()
        assertEquals(R.string.connected_request_pending_departed, rider.statusText)
        assertEquals(request.id, rider.cancellableRequestId)
        assertTrue(result.riderHistory.isEmpty())

        val incoming = content(uid = journey.driverUid, now = 1000).driver.single().incoming.single()
        assertEquals(R.string.connected_incoming_pending_departed, incoming.statusText)
        assertFalse(incoming.canAccept)
        assertTrue(incoming.canDecline)
        assertEquals(request.id, incoming.id)
    }

    @Test fun `departed open driver journeys sort before nearest upcoming current journeys`() {
        fun offer(id: String, departure: Long) = journey.copy(id = id, departureEpochMillis = departure)
        val result = content(
            j = listOf(offer("past-old", 100), offer("future-late", 500), offer("past-new", 200), offer("future-soon", 400)),
            r = emptyList(),
            uid = journey.driverUid,
            now = 300,
        )
        assertEquals(
            listOf("past-new", "past-old", "future-soon", "future-late"),
            result.driverCurrent.map { it.journeyId },
        )
        assertTrue(result.driverHistory.isEmpty())
    }

    @Test fun `rider current ordering surfaces overdue cleanup then nearest upcoming departure`() {
        fun pending(id: String, departure: Long): Pair<ConnectedJourney, ConnectedSeatRequest> {
            val offered = journey.copy(id = id, departureEpochMillis = departure)
            return offered to request.copy(id = "${id}_private-rider", journeyId = id)
        }
        val values = listOf(
            pending("overdue-old", 100),
            pending("future-late", 500),
            pending("overdue-new", 200),
            pending("future-soon", 400),
        )
        val result = content(values.map { it.first }, values.map { it.second }, now = 300)

        assertEquals(
            listOf("overdue-new", "overdue-old", "future-soon", "future-late"),
            result.riderCurrent.map { it.journeyId },
        )
        assertTrue(result.riderHistory.isEmpty())
    }

    @Test fun `terminal rider activity is history despite future departure and sorts newest first`() {
        fun requestFor(id: String, departure: Long, status: ConnectedRequestStatus): Pair<ConnectedJourney, ConnectedSeatRequest> {
            val offered = journey.copy(id = id, departureEpochMillis = departure)
            return offered to request.copy(id = "${id}_private-rider", journeyId = id, status = status)
        }
        val declined = requestFor("declined", 5_000, ConnectedRequestStatus.DECLINED)
        val cancelled = requestFor("cancelled", 5_000, ConnectedRequestStatus.CANCELLED)
        val withdrawn = requestFor("withdrawn", 4_000, ConnectedRequestStatus.CANCELLED)
        val pending = requestFor("pending", 3_000, ConnectedRequestStatus.PENDING)
        val result = content(
            j = listOf(declined.first, withdrawn.first, pending.first, cancelled.first),
            r = listOf(declined.second, withdrawn.second, pending.second, cancelled.second),
            now = 0,
        )

        assertEquals(listOf("pending"), result.riderCurrent.map { it.journeyId })
        assertEquals(listOf("cancelled", "declined", "withdrawn"), result.riderHistory.map { it.journeyId })
        assertTrue(result.riderHistory.all {
            it.cancellableTripId == null && it.cancellableRequestId == null
        })
    }

    @Test fun `cancellation follows existing lifecycle ownership and departure`() {
        assertNull(content(t = listOf(trip), now = 1000).rider.single().cancellableTripId)
        val closed = journey.copy(status = ConnectedJourneyStatus.CANCELLED)
        val driverCancelledResult = content(j = listOf(closed), t = listOf(trip))
        val driverCancelled = driverCancelledResult.riderHistory.single()
        assertEquals(R.string.connected_trips_driver_cancelled, driverCancelled.statusText)
        assertEquals("Morgan Driver", driverCancelled.driverDisplayName)
        assertNull(driverCancelled.cancellableTripId)
        assertTrue(driverCancelledResult.riderCurrent.isEmpty())
        val missing = content(j = emptyList(), t = listOf(trip)).riderHistory.single()
        assertEquals(R.string.connected_trips_unavailable, missing.statusText)
        assertNull(missing.cancellableTripId)
        val riderCancelled = content(
            j = listOf(closed),
            t = listOf(trip.copy(status = ConnectedTripStatus.CANCELLED_BY_RIDER)),
        ).riderHistory.single()
        assertEquals(R.string.connected_trips_cancelled, riderCancelled.statusText)
        assertEquals("Morgan Driver", riderCancelled.driverDisplayName)
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
        val open = content(uid = journey.driverUid)
        val item = open.driverCurrent.single()
        assertEquals(R.string.connected_trips_driver, item.roleText)
        assertEquals(R.string.connected_trips_offer_open, item.statusText)
        assertEquals("York", item.origin)
        assertNull(item.cancellableTripId)
        assertNull(item.cancellableRequestId)
        assertTrue(open.driverHistory.isEmpty())
        val cancelled = content(
            uid = journey.driverUid,
            j = listOf(journey.copy(status = ConnectedJourneyStatus.CANCELLED)),
        )
        assertEquals(R.string.connected_trips_offer_cancelled, cancelled.driverHistory.single().statusText)
        assertTrue(cancelled.driverCurrent.isEmpty())
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
        assertFalse(canDecideConnectedRequest(request, journey, journey.driverUid, true, 0, trip))
        assertFalse(canDecideConnectedRequest(request, journey, journey.driverUid, false, 0, trip))
    }

    @Test fun `driver lifecycle retains request history without invalid actions`() {
        val closed = content(uid = journey.driverUid, j = listOf(journey.copy(status = ConnectedJourneyStatus.CANCELLED))).driver.single()
        assertNull(closed.cancellableJourneyId)
        assertEquals(R.string.connected_trips_offer_cancelled, closed.incoming.single().statusText)
        assertFalse(closed.incoming.single().canAccept)
        assertFalse(closed.incoming.single().canDecline)
        assertNull(content(uid = journey.driverUid, now = 1000).driver.single().cancellableJourneyId)
        assertEquals(R.string.connected_offer_awaiting_completion,
            content(uid = journey.driverUid, now = 1000).driver.single().statusText)
        val missing = content(uid = journey.driverUid, j = emptyList()).unavailableIncoming.single()
        assertEquals(R.string.connected_trips_unavailable, missing.statusText)
        assertFalse(missing.canAccept)
        assertFalse(missing.canDecline)
        val unrelated = request.copy(driverUid = "someone-else")
        assertTrue(content(uid = journey.driverUid, r = listOf(unrelated)).driver.single().incoming.isEmpty())
        assertTrue(content(uid = journey.driverUid, r = listOf(unrelated)).unavailableIncoming.isEmpty())
    }

    @Test fun `past presentation does not override cancellation or unavailable precedence`() {
        val riderCancelled = trip.copy(status = ConnectedTripStatus.CANCELLED_BY_RIDER)
        assertEquals(
            R.string.connected_trips_cancelled,
            content(t = listOf(riderCancelled), now = 1000).rider.single().statusText,
        )
        assertEquals(
            R.string.connected_trips_driver_cancelled,
            content(j = listOf(journey.copy(status = ConnectedJourneyStatus.CANCELLED)), t = listOf(trip), now = 1000)
                .rider.single().statusText,
        )
        assertEquals(
            R.string.connected_trips_unavailable,
            content(j = emptyList(), t = listOf(trip), now = 1000).rider.single().statusText,
        )
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
