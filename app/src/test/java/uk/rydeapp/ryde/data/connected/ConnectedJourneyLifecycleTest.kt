package uk.rydeapp.ryde.data.connected

import com.google.firebase.Timestamp
import org.junit.Assert.*
import org.junit.Test
import uk.rydeapp.ryde.domain.model.GeographicCoordinate

class ConnectedJourneyLifecycleTest {
    private val journey = ConnectedJourney("j", "driver", "Mansfield", "Nottingham", 4_070_908_800_000L, 2, 1)
    private val request = ConnectedSeatRequest("j_rider", "j", "driver", "rider", ConnectedRequestStatus.PENDING)
    private val trip = ConnectedConfirmedTrip("j_rider", "j", "j_rider", "driver", "rider", "Mansfield", "Nottingham", journey.departureEpochMillis, ConnectedTripStatus.CONFIRMED)
    private val cancelled = journey.copy(status = ConnectedJourneyStatus.CANCELLED, cancelledAtEpochMillis = 100_000)
    private val completed = journey.copy(status = ConnectedJourneyStatus.COMPLETED, completedAtEpochMillis = 100_000)

    @Test
    fun `confirmed trip becomes departure passed exactly at departure without mutating persisted truth`() {
        assertEquals(
            ConnectedTripLifecycle.CONFIRMED,
            ConnectedJourneyLifecycle.trip(trip, journey, journey.departureEpochMillis - 1),
        )
        assertEquals(
            ConnectedTripLifecycle.DEPARTURE_PASSED,
            ConnectedJourneyLifecycle.trip(trip, journey, journey.departureEpochMillis),
        )
        assertEquals(
            ConnectedTripLifecycle.DEPARTURE_PASSED,
            ConnectedJourneyLifecycle.trip(trip, journey, journey.departureEpochMillis + 1),
        )
        assertEquals(ConnectedTripStatus.CONFIRMED, trip.status)
        assertEquals(ConnectedJourneyStatus.OPEN, journey.status)
    }

    @Test
    fun `request departure presentation is derived without changing persisted status`() {
        assertEquals(
            ConnectedRequestLifecycle.PENDING,
            ConnectedJourneyLifecycle.request(request, journey, journey.departureEpochMillis - 1),
        )
        assertEquals(
            ConnectedRequestLifecycle.DEPARTURE_PASSED_PENDING,
            ConnectedJourneyLifecycle.request(request, journey, journey.departureEpochMillis),
        )
        val accepted = request.copy(status = ConnectedRequestStatus.ACCEPTED)
        assertEquals(
            ConnectedRequestLifecycle.DEPARTURE_PASSED_ACCEPTED,
            ConnectedJourneyLifecycle.request(accepted, journey, journey.departureEpochMillis),
        )
        assertEquals(ConnectedRequestStatus.PENDING, request.status)
        assertEquals(ConnectedRequestStatus.ACCEPTED, accepted.status)
    }

    @Test
    fun `driver cancellation resolves confirmed history without changing source records`() {
        assertEquals(ConnectedTripLifecycle.CONFIRMED, ConnectedJourneyLifecycle.trip(trip, journey))
        assertEquals(ConnectedTripLifecycle.CANCELLED_BY_DRIVER, ConnectedJourneyLifecycle.trip(trip, cancelled))
        assertEquals(ConnectedTripStatus.CONFIRMED, trip.status)
        assertTrue(ConnectedJourneyLifecycle.requestCancelledByDriver(request, cancelled))
        assertTrue(ConnectedJourneyLifecycle.requestCancelledByDriver(request.copy(status = ConnectedRequestStatus.ACCEPTED), cancelled))
        assertFalse(ConnectedJourneyLifecycle.requestJourneyOpen(request, cancelled))
        for (status in listOf(ConnectedRequestStatus.DECLINED, ConnectedRequestStatus.CANCELLED, ConnectedRequestStatus.CANCELLED_AFTER_ACCEPTANCE)) {
            assertFalse(ConnectedJourneyLifecycle.requestCancelledByDriver(request.copy(status = status), cancelled))
        }
    }

    @Test
    fun `completion is canonical for confirmed trips and accepted requests`() {
        assertEquals(ConnectedTripLifecycle.COMPLETED, ConnectedJourneyLifecycle.trip(trip, completed, Long.MAX_VALUE))
        assertEquals(
            ConnectedRequestLifecycle.COMPLETED,
            ConnectedJourneyLifecycle.request(request.copy(status = ConnectedRequestStatus.ACCEPTED), completed, Long.MAX_VALUE),
        )
        assertEquals(
            ConnectedRequestLifecycle.DEPARTURE_PASSED_PENDING,
            ConnectedJourneyLifecycle.request(request, completed, Long.MAX_VALUE),
        )
        assertFalse(ConnectedJourneyLifecycle.canSendMessages(trip, completed, "rider"))
        assertFalse(ConnectedJourneyLifecycle.discoverable(completed, "rider", 0))
        assertFalse(ConnectedJourneyLifecycle.canCompleteJourney(journey, "rider", journey.departureEpochMillis))
        assertFalse(ConnectedJourneyLifecycle.canCompleteJourney(journey, "driver", journey.departureEpochMillis - 1))
        assertTrue(ConnectedJourneyLifecycle.canCompleteJourney(journey, "driver", journey.departureEpochMillis))
        assertFalse(ConnectedJourneyLifecycle.canCompleteJourney(completed, "driver", Long.MAX_VALUE))
    }

    @Test
    fun `earlier rider cancellation keeps its attribution and timestamp`() {
        val riderCancelled = trip.copy(status = ConnectedTripStatus.CANCELLED_BY_RIDER, cancelledAtEpochMillis = 1)
        assertEquals(ConnectedTripLifecycle.CANCELLED_BY_RIDER, ConnectedJourneyLifecycle.trip(riderCancelled, cancelled, Long.MAX_VALUE))
        assertEquals(ConnectedTripLifecycle.CANCELLED_BY_RIDER, ConnectedJourneyLifecycle.trip(riderCancelled, null, Long.MAX_VALUE))
        assertEquals(1L, riderCancelled.cancelledAtEpochMillis)
    }

    @Test
    fun `driver cancellation and unavailable links take precedence over passed departure`() {
        assertEquals(
            ConnectedTripLifecycle.CANCELLED_BY_DRIVER,
            ConnectedJourneyLifecycle.trip(trip, cancelled, Long.MAX_VALUE),
        )
        assertEquals(
            ConnectedTripLifecycle.UNAVAILABLE,
            ConnectedJourneyLifecycle.trip(trip, null, Long.MAX_VALUE),
        )
        assertEquals(
            ConnectedTripLifecycle.UNAVAILABLE,
            ConnectedJourneyLifecycle.trip(trip, journey.copy(driverUid = "other"), Long.MAX_VALUE),
        )
        assertEquals(
            ConnectedRequestLifecycle.CANCELLED_BY_DRIVER,
            ConnectedJourneyLifecycle.request(request, cancelled, Long.MAX_VALUE),
        )
        assertEquals(
            ConnectedRequestLifecycle.UNAVAILABLE,
            ConnectedJourneyLifecycle.request(request, null, Long.MAX_VALUE),
        )
    }

    @Test
    fun `missing and mismatched journeys fail closed`() {
        for (linked in listOf(null, journey.copy(id = "other"), journey.copy(driverUid = "other"))) {
            assertEquals(ConnectedTripLifecycle.UNAVAILABLE, ConnectedJourneyLifecycle.trip(trip, linked))
            assertFalse(ConnectedJourneyLifecycle.requestJourneyOpen(request, linked))
            assertFalse(ConnectedJourneyLifecycle.requestCancelledByDriver(request, linked))
        }
    }

    @Test
    fun `discovery and driver action require open upcoming journeys and correct role`() {
        assertTrue(ConnectedJourneyLifecycle.discoverable(journey, "rider", 0))
        assertFalse(ConnectedJourneyLifecycle.discoverable(cancelled, "rider", 0))
        assertFalse(ConnectedJourneyLifecycle.discoverable(journey, "driver", 0))
        assertTrue(ConnectedJourneyLifecycle.canCancelJourney(journey, "driver", 0))
        assertFalse(ConnectedJourneyLifecycle.canCancelJourney(journey, "rider", 0))
        assertFalse(ConnectedJourneyLifecycle.canCancelJourney(cancelled, "driver", 0))
        assertFalse(ConnectedJourneyLifecycle.canCancelJourney(journey, "driver", journey.departureEpochMillis))
    }

    @Test
    fun `pending request cancellation requires rider ownership and a consistent open journey`() {
        assertTrue(ConnectedJourneyLifecycle.canCancelRequest(request, journey, "rider"))
        assertFalse(ConnectedJourneyLifecycle.canCancelRequest(request, journey, "driver"))
        ConnectedRequestStatus.entries.filter { it != ConnectedRequestStatus.PENDING }.forEach {
            assertFalse(ConnectedJourneyLifecycle.canCancelRequest(request.copy(status = it), journey, "rider"))
        }
        for (linked in listOf(null, journey.copy(id = "other"), journey.copy(driverUid = "other"), cancelled)) {
            assertFalse(ConnectedJourneyLifecycle.canCancelRequest(request, linked, "rider"))
        }
    }

    @Test
    fun `journey mapping supports open cancellation and completion exact shapes`() {
        val data = FirestoreJourneyMapper.journeyData("driver", ConnectedJourneyDraft("Mansfield", "Nottingham", journey.departureEpochMillis, 2))
        assertEquals(ConnectedJourneyStatus.OPEN, FirestoreJourneyMapper.journey("j", data)?.status)
        val closed = data + mapOf("status" to "CANCELLED", "cancelledAt" to Timestamp(100, 0))
        assertEquals(ConnectedJourneyStatus.CANCELLED, FirestoreJourneyMapper.journey("j", closed)?.status)
        assertEquals(100_000L, FirestoreJourneyMapper.journey("j", closed)?.cancelledAtEpochMillis)
        val complete = data + mapOf("status" to "COMPLETED", "completedAt" to Timestamp(200, 0))
        assertEquals(ConnectedJourneyStatus.COMPLETED, FirestoreJourneyMapper.journey("j", complete)?.status)
        assertEquals(200_000L, FirestoreJourneyMapper.journey("j", complete)?.completedAtEpochMillis)
        for (invalid in listOf(closed - "cancelledAt", closed + ("cancelledAt" to "bad"), data + ("cancelledAt" to Timestamp(100, 0)), closed + ("extra" to true), data + ("status" to "CONFIRMED"), complete - "completedAt", complete + ("completedAt" to "bad"), complete + ("cancelledAt" to Timestamp(100, 0)))) {
            assertNull(FirestoreJourneyMapper.journey("j", invalid))
        }
    }

    @Test
    fun `journey mapping persists valid coordinate pairs and accepts legacy records`() {
        val origin = GeographicCoordinate(53.1432, -1.1984)
        val destination = GeographicCoordinate(52.9548, -1.1581)
        val legacy = FirestoreJourneyMapper.journeyData(
            "driver",
            ConnectedJourneyDraft("Mansfield", "Nottingham", journey.departureEpochMillis, 2),
        )
        val coordinateData = FirestoreJourneyMapper.journeyData(
            "driver",
            ConnectedJourneyDraft(
                "Mansfield", "Nottingham", journey.departureEpochMillis, 2,
                origin, destination,
            ),
        )

        assertNull(FirestoreJourneyMapper.journey("legacy", legacy)?.originCoordinate)
        assertEquals(origin, FirestoreJourneyMapper.journey("located", coordinateData)?.originCoordinate)
        assertEquals(destination, FirestoreJourneyMapper.journey("located", coordinateData)?.destinationCoordinate)
        assertNull(FirestoreJourneyMapper.journey("partial", coordinateData - "destinationCoordinate"))
        assertNull(FirestoreJourneyMapper.journey(
            "invalid",
            coordinateData + ("originCoordinate" to mapOf("latitude" to 91.0, "longitude" to 0.0)),
        ))
        assertNull(FirestoreJourneyMapper.journey(
            "extra",
            coordinateData + ("originCoordinate" to mapOf(
                "latitude" to 53.1432,
                "longitude" to -1.1984,
                "providerPlaceId" to "forbidden",
            )),
        ))
    }
}
