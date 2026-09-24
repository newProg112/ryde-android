package uk.rydeapp.ryde.ui.trips

import org.junit.Assert.*
import org.junit.Test
import uk.rydeapp.ryde.R
import uk.rydeapp.ryde.data.connected.*
import uk.rydeapp.ryde.ui.home.connectedHomeJourneys
import uk.rydeapp.ryde.ui.map.JourneyMapLine
import uk.rydeapp.ryde.ui.map.JourneyMapPointRole

class ConnectedTripDetailsUiTest {
    private val journey = ConnectedJourney("journey", "driver", "Mansfield", "Sheffield", 1000, 3, 2)
    private val request = ConnectedSeatRequest("journey_rider", journey.id, "driver", "rider",
        ConnectedRequestStatus.PENDING, "Riley Rider")
    private val trip = ConnectedConfirmedTrip(request.id, journey.id, request.id, "driver", "rider",
        "Persisted area", "Sheffield", 1000, ConnectedTripStatus.CONFIRMED,
        driverDisplayName = "Morgan Driver")
    private fun details(snapshot: ConnectedJourneySnapshot, uid: String = "rider", id: String = journey.id, now: Long = 0) =
        connectedTripDetailsContent(snapshot, uid, id, connectedHomeJourneys(snapshot, uid, now),
            connectedTripsContent(snapshot, uid, now), now)

    @Test fun ownerUsesExistingDriverPresentationAndDecisionFlags() {
        val snapshot = ConnectedJourneySnapshot(listOf(journey), listOf(request))
        val details = details(snapshot, "driver")
        assertEquals(connectedTripsContent(snapshot, "driver", 0).driver.single(), details.summary)
        assertEquals(journey.id, details.summary!!.cancellableJourneyId)
        assertEquals(request.id, details.summary.incoming.single().id)
        assertEquals("Riley Rider", details.summary.incoming.single().riderDisplayName)
        assertTrue(details.summary.incoming.single().canAccept)
        assertFalse(details.canRequest)
        assertFalse(details( snapshot, "driver", now = 1000).summary!!.incoming.single().canAccept)
        assertTrue(details(snapshot, "driver", now = 1000).summary!!.incoming.single().canDecline)
    }

    @Test fun unrequestedRiderReusesDiscoveryEligibilityAndOtherUsersRequestsStayPrivate() {
        val snapshot = ConnectedJourneySnapshot(listOf(journey), listOf(request.copy(riderUid = "other")))
        val open = details(snapshot)
        assertNull(open.summary)
        assertEquals(R.string.connected_trips_offer_open, open.unrequestedStatusText)
        assertTrue(open.canRequest)

        val full = details(snapshot.copy(journeys = listOf(journey.copy(seatsRemaining = 0))))
        assertEquals(R.string.connected_offer_full, full.unrequestedStatusText)
        assertFalse(full.canRequest)

        listOf(1000L, 1001L).forEach { now ->
            val departed = details(snapshot, now = now)
            assertEquals(R.string.connected_offer_departed, departed.unrequestedStatusText)
            assertFalse(departed.canRequest)
        }

        val cancelled = details(snapshot.copy(journeys = listOf(journey.copy(status = ConnectedJourneyStatus.CANCELLED))))
        assertEquals(R.string.connected_trips_offer_cancelled, cancelled.unrequestedStatusText)
        assertFalse(cancelled.canRequest)
        assertEquals(ConnectedTripDetailsContent(), details(snapshot, id = "unknown"))
        assertEquals(ConnectedTripDetailsContent(), details(snapshot, id = " "))
    }

    @Test fun riderRequestStatusesUseTripsPresentationAndOnlyWithdrawnPendingRequestCanRerequest() {
        ConnectedRequestStatus.entries.forEach { status ->
            val snapshot = ConnectedJourneySnapshot(listOf(journey), listOf(request.copy(status = status)))
            val result = details(snapshot)
            assertEquals(connectedTripsContent(snapshot, "rider", 0).rider.single(), result.summary)
            assertNull(result.unrequestedStatusText)
            assertEquals(status == ConnectedRequestStatus.CANCELLED, result.canRequest)
            assertNull(result.summary!!.cancellableTripId)
            assertEquals(request.id.takeIf { status == ConnectedRequestStatus.PENDING }, result.summary.cancellableRequestId)
        }
    }

    @Test fun confirmedBookingTakesPrecedenceUsesPersistedFieldsAndCancelsByTripId() {
        val snapshot = ConnectedJourneySnapshot(listOf(journey), listOf(request.copy(status = ConnectedRequestStatus.ACCEPTED)), listOf(trip))
        val result = details(snapshot)
        assertEquals("Persisted area", result.summary!!.origin)
        assertEquals(R.string.connected_request_accepted, result.summary.statusText)
        assertEquals(trip.id, result.summary.cancellableTripId)
        assertEquals(journey.id, result.summary.journeyId)
        assertEquals("Morgan Driver", result.summary.driverDisplayName)
        assertNull(result.unrequestedStatusText)
        assertFalse(result.canRequest)
        assertNull(result.summary.cancellableRequestId)
        assertEquals(listOf("Persisted area", "Sheffield"), result.routeMap!!.points.map { it.label })
        assertEquals(
            listOf(JourneyMapPointRole.JOURNEY_START, JourneyMapPointRole.JOURNEY_DESTINATION),
            result.routeMap.points.map { it.role },
        )
        assertEquals(JourneyMapLine.VisualConnection, result.routeMap.line)
        // A partial read without the accepted request must never re-enable a new request.
        assertFalse(details(snapshot.copy(requests = emptyList())).canRequest)
        val cancelled = details(snapshot.copy(confirmedTrips = listOf(trip.copy(status = ConnectedTripStatus.CANCELLED_BY_RIDER))))
        assertEquals(R.string.connected_trips_cancelled, cancelled.summary!!.statusText)
        assertEquals("Morgan Driver", cancelled.summary.driverDisplayName)
        assertNull(cancelled.summary.cancellableTripId)
        assertFalse(cancelled.canRequest)
    }

    @Test fun messageTargetsSurviveJourneyLossButMalformedTripIdentityFailsClosed() {
        val snapshot = ConnectedJourneySnapshot(
            emptyList(),
            listOf(request.copy(status = ConnectedRequestStatus.ACCEPTED)),
            listOf(trip),
        )
        assertEquals(trip.id, details(snapshot).summary?.messageTarget?.tripId)
        assertNull(details(snapshot.copy(confirmedTrips = listOf(trip.copy(acceptedRequestId = "wrong"))))
            .summary?.messageTarget)
    }

    @Test fun pastConfirmedDetailsRetainRouteAndHistoryWithoutCancellation() {
        val snapshot = ConnectedJourneySnapshot(
            listOf(journey),
            listOf(request.copy(status = ConnectedRequestStatus.ACCEPTED)),
            listOf(trip),
        )
        val result = details(snapshot, now = 1000)
        assertEquals("Persisted area", result.summary!!.origin)
        assertEquals("Sheffield", result.summary.destination)
        assertEquals(1000L, result.summary.departureEpochMillis)
        assertEquals(R.string.connected_trips_departure_passed, result.summary.statusText)
        assertEquals("Morgan Driver", result.summary.driverDisplayName)
        assertNull(result.summary.cancellableTripId)
        assertFalse(result.canRequest)
    }

    @Test fun pastPendingDetailsRetainCleanupIdsButCloseFutureActions() {
        val snapshot = ConnectedJourneySnapshot(listOf(journey), listOf(request))
        val rider = details(snapshot, now = 1000)
        assertEquals(R.string.connected_request_pending_departed, rider.summary!!.statusText)
        assertEquals(request.id, rider.summary.cancellableRequestId)
        assertFalse(rider.canRequest)

        val driver = details(snapshot, uid = "driver", now = 1000)
        val incoming = driver.summary!!.incoming.single()
        assertEquals(R.string.connected_incoming_pending_departed, incoming.statusText)
        assertEquals("Riley Rider", incoming.riderDisplayName)
        assertFalse(incoming.canAccept)
        assertTrue(incoming.canDecline)
        assertNull(driver.summary.cancellableJourneyId)
    }

    @Test fun driverCancellationAndTerminalHistoryReuseResolvedTripsStatuses() {
        val closed = journey.copy(status = ConnectedJourneyStatus.CANCELLED)
        val pending = details(ConnectedJourneySnapshot(listOf(closed), listOf(request)))
        assertEquals(R.string.connected_trips_driver_cancelled, pending.summary!!.statusText)
        assertFalse(pending.canRequest)
        val confirmed = details(ConnectedJourneySnapshot(listOf(closed), listOf(request), listOf(trip)))
        assertEquals(R.string.connected_trips_driver_cancelled, confirmed.summary!!.statusText)
        assertEquals("Morgan Driver", confirmed.summary.driverDisplayName)
        assertNull(confirmed.summary.cancellableTripId)
        val declined = details(ConnectedJourneySnapshot(listOf(closed), listOf(request.copy(status = ConnectedRequestStatus.DECLINED))))
        assertEquals(R.string.connected_request_declined, declined.summary!!.statusText)
        assertEquals(R.string.connected_trips_driver_cancelled, declined.summary.journeyStatusText)
    }

    @Test fun missingAndMismatchedJourneyLinksRetainSafeHistoryWithoutActionsOrBorrowedFields() {
        listOf(emptyList(), listOf(journey.copy(driverUid = "different"))).forEach { journeys ->
            val pending = details(ConnectedJourneySnapshot(journeys, listOf(request)))
            assertNull(pending.journey)
            assertNull(pending.summary!!.origin)
            assertEquals(R.string.connected_trips_unavailable, pending.summary.statusText)
            assertNull(pending.summary.cancellableRequestId)
            assertNull(pending.routeMap)
            assertFalse(pending.canRequest)
            val confirmed = details(ConnectedJourneySnapshot(journeys, listOf(request), listOf(trip)))
            assertNull(confirmed.journey)
            assertEquals("Persisted area", confirmed.summary!!.origin)
            assertEquals(R.string.connected_trips_unavailable, confirmed.summary.statusText)
            assertNull(confirmed.summary.cancellableTripId)
        }
    }

    @Test fun blankRequiredActionIdsFailClosed() {
        val snapshot = ConnectedJourneySnapshot(listOf(journey), listOf(request), listOf(trip.copy(id = "")))
        assertNull(details(snapshot).summary!!.cancellableTripId)
        val driver = details(snapshot.copy(requests = listOf(request.copy(id = ""))), "driver")
        assertFalse(driver.summary!!.incoming.single().canAccept)
        assertFalse(driver.summary.incoming.single().canDecline)
        assertNull(details(ConnectedJourneySnapshot(listOf(journey), listOf(request.copy(id = "")))).summary!!.cancellableRequestId)
    }
}
