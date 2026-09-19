package uk.rydeapp.ryde.data.connected

import com.google.firebase.Timestamp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ConnectedJourneyFlowTest {
    @Test
    fun `driver cancellation freezes capacity closes pending and confirmed bookings and preserves rider cancellations`() = runBlocking {
        val store = MemoryJourneyStore()
        val driver = repository("driver", store)
        val rider = repository("rider", store)
        val pending = repository("pending", store)
        val withdrew = repository("withdrew", store)
        driver.createConnectedJourney("Mansfield", "Nottingham", "2099-01-01 10:00", "3")
        rider.requestConnectedSeat("journey-1")
        withdrew.requestConnectedSeat("journey-1")
        pending.requestConnectedSeat("journey-1")
        driver.decideConnectedRequest("journey-1_rider", true)
        driver.decideConnectedRequest("journey-1_withdrew", true)
        withdrew.cancelConnectedConfirmedSeat("journey-1_withdrew")
        val before = store.load("driver")
        val beforeGuard = store.guards.getValue("journey-1")
        assertTrue(rider.cancelConnectedJourney("journey-1") is ConnectedJourneyCommandResult.Failure)
        assertTrue(repository("stranger", store).cancelConnectedJourney("journey-1") is ConnectedJourneyCommandResult.Failure)
        assertEquals(ConnectedJourneyCommandResult.Success, driver.cancelConnectedJourney("journey-1"))
        val after = driver.journeyState.value
        val closed = after.journeys.single()
        assertEquals(ConnectedJourneyStatus.CANCELLED, closed.status)
        assertTrue(closed.cancelledAtEpochMillis != null)
        assertEquals(before.journeys.single().seatsRemaining, closed.seatsRemaining)
        assertEquals(beforeGuard, store.guards.getValue(closed.id))
        assertEquals(before.requests, after.requests)
        assertEquals(before.confirmedTrips, after.confirmedTrips)
        rider.refresh()
        pending.refresh()
        withdrew.refresh()
        assertEquals(ConnectedTripLifecycle.CANCELLED_BY_DRIVER, ConnectedJourneyLifecycle.trip(rider.journeyState.value.confirmedTrips.single(), closed))
        assertEquals(ConnectedTripLifecycle.CANCELLED_BY_RIDER, ConnectedJourneyLifecycle.trip(withdrew.journeyState.value.confirmedTrips.single(), closed))
        assertTrue(ConnectedJourneyLifecycle.requestCancelledByDriver(pending.journeyState.value.requests.single(), closed))
        assertTrue(pending.cancelConnectedRequest("journey-1_pending") is ConnectedJourneyCommandResult.Failure)
        assertTrue(driver.decideConnectedRequest("journey-1_pending", true) is ConnectedJourneyCommandResult.Failure)
        assertTrue(driver.decideConnectedRequest("journey-1_pending", false) is ConnectedJourneyCommandResult.Failure)
        assertTrue(rider.cancelConnectedConfirmedSeat("journey-1_rider") is ConnectedJourneyCommandResult.Failure)
        assertTrue(withdrew.requestConnectedSeat("journey-1") is ConnectedJourneyCommandResult.Failure)
        assertTrue(repository("new", store).requestConnectedSeat("journey-1") is ConnectedJourneyCommandResult.Failure)
        assertTrue(driver.cancelConnectedJourney("journey-1") is ConnectedJourneyCommandResult.Failure)
        assertEquals(after, store.load("driver"))
    }

    @Test
    fun `empty driver offer cancellation leaves other offers open and rejects departed offers`() = runBlocking {
        var now = 0L
        val store = MemoryJourneyStore { now }
        val driver = repository("driver", store)
        repeat(2) { driver.createConnectedJourney("Mansfield", "Nottingham", "2099-01-01 10:00", "1") }
        assertEquals(ConnectedJourneyCommandResult.Success, driver.cancelConnectedJourney("journey-1"))
        assertEquals(ConnectedJourneyStatus.OPEN, store.load("driver").journeys.single { it.id == "journey-2" }.status)
        now = store.load("driver").journeys.single { it.id == "journey-2" }.departureEpochMillis
        assertTrue(driver.cancelConnectedJourney("journey-2") is ConnectedJourneyCommandResult.Failure)
        assertEquals(ConnectedJourneyStatus.OPEN, store.load("driver").journeys.single { it.id == "journey-2" }.status)
    }

    @Test
    fun `confirmed cancellation is bounded and safe when store hangs`() = runBlocking {
        val backing = MemoryJourneyStore()
        val hanging = object : ConnectedJourneyStore by backing {
            override suspend fun cancelConfirmedSeat(uid: String, tripId: String) = awaitCancellation()
        }
        val rider = ConnectedRydeRepository(StaticAuth("rider"), StaticProfiles("rider"),
            journeys = hanging, firebaseOperationTimeoutMillis = 50)
        val result = rider.cancelConnectedConfirmedSeat("j_rider")
        assertEquals(ConnectedJourneyCommandResult.Failure(ConnectedRydeRepository.SAFE_JOURNEY_ERROR), result)
    }

    @Test
    fun `committed confirmed cancellation survives failed refresh and becomes visible on retry`() = runBlocking {
        val backing = MemoryJourneyStore()
        var failLoad = false
        val store = object : ConnectedJourneyStore by backing {
            override suspend fun load(uid: String): ConnectedJourneySnapshot {
                check(!failLoad) { "secret backend details" }
                return backing.load(uid)
            }
        }
        val driver = repository("driver", store)
        val rider = repository("rider", store)
        driver.createConnectedJourney("Mansfield", "Nottingham", "2099-01-01 10:00", "1")
        rider.requestConnectedSeat("journey-1")
        driver.decideConnectedRequest("journey-1_rider", true)
        rider.refresh()
        failLoad = true
        assertEquals(ConnectedJourneyCommandResult.Failure(ConnectedRydeRepository.SAFE_JOURNEY_ERROR),
            rider.cancelConnectedConfirmedSeat("journey-1_rider"))
        assertEquals(ConnectedTripStatus.CONFIRMED, rider.journeyState.value.confirmedTrips.single().status)
        failLoad = false
        rider.refresh()
        assertEquals(ConnectedTripStatus.CANCELLED_BY_RIDER, rider.journeyState.value.confirmedTrips.single().status)
        assertEquals(1, rider.journeyState.value.journeys.single().seatsRemaining)
        assertEquals(0, backing.guards.getValue("journey-1").acceptanceCount)
    }

    @Test
    fun `confirmed cancellation restores exactly one seat retains private history and allows another rider`() = runBlocking {
        val store = MemoryJourneyStore()
        val driver = repository("driver", store)
        val rider = repository("rider", store)
        val other = repository("other", store)
        driver.createConnectedJourney("Mansfield", "Nottingham", "2099-01-01 10:00", "1")
        rider.requestConnectedSeat("journey-1")
        driver.decideConnectedRequest("journey-1_rider", true)
        rider.refresh()
        val before = rider.journeyState.value.confirmedTrips.single()

        assertTrue(driver.cancelConnectedConfirmedSeat(before.id) is ConnectedJourneyCommandResult.Failure)
        assertTrue(repository("stranger", store).cancelConnectedConfirmedSeat(before.id) is ConnectedJourneyCommandResult.Failure)
        assertEquals(ConnectedJourneyCommandResult.Success, rider.cancelConnectedConfirmedSeat(before.id))
        val after = rider.journeyState.value.confirmedTrips.single()
        assertEquals(before.copy(status = ConnectedTripStatus.CANCELLED_BY_RIDER, cancelledAtEpochMillis = after.cancelledAtEpochMillis), after)
        assertTrue(after.cancelledAtEpochMillis != null)
        assertEquals(ConnectedRequestStatus.CANCELLED_AFTER_ACCEPTANCE, rider.journeyState.value.requests.single().status)
        assertEquals(1, rider.journeyState.value.journeys.single().seatsRemaining)
        assertEquals(ConnectedJourneyAcceptanceGuard("driver", 0, before.id, before.id), store.guards.getValue("journey-1"))
        driver.refresh()
        assertEquals(after, driver.journeyState.value.confirmedTrips.single())
        assertTrue(rider.cancelConnectedConfirmedSeat(before.id) is ConnectedJourneyCommandResult.Failure)
        assertTrue(rider.requestConnectedSeat("journey-1") is ConnectedJourneyCommandResult.Failure)
        assertEquals(1, store.load("driver").journeys.single().seatsRemaining)
        assertEquals(0, store.guards.getValue("journey-1").acceptanceCount)
        assertEquals(ConnectedJourneyCommandResult.Success, other.requestConnectedSeat("journey-1"))
        assertEquals(ConnectedJourneyCommandResult.Success, driver.decideConnectedRequest("journey-1_other", true))
        assertEquals(0, driver.journeyState.value.journeys.single().seatsRemaining)
        assertEquals(1, store.guards.getValue("journey-1").acceptanceCount)
        assertEquals(2, driver.journeyState.value.confirmedTrips.size)
        val stranger = repository("stranger", store)
        stranger.refresh()
        assertTrue(stranger.journeyState.value.confirmedTrips.isEmpty())
        assertTrue(stranger.journeyState.value.requests.isEmpty())
    }

    @Test
    fun `confirmed cancellation after departure fails without releasing allocation`() = runBlocking {
        var now = 0L
        val store = MemoryJourneyStore { now }
        val driver = repository("driver", store)
        val rider = repository("rider", store)
        driver.createConnectedJourney("Mansfield", "Nottingham", "2099-01-01 10:00", "1")
        rider.requestConnectedSeat("journey-1")
        driver.decideConnectedRequest("journey-1_rider", true)
        now = store.load("driver").journeys.single().departureEpochMillis
        assertTrue(rider.cancelConnectedConfirmedSeat("journey-1_rider") is ConnectedJourneyCommandResult.Failure)
        assertEquals(0, store.load("driver").journeys.single().seatsRemaining)
        assertEquals(1, store.guards.getValue("journey-1").acceptanceCount)
        assertEquals(ConnectedTripStatus.CONFIRMED, store.load("driver").confirmedTrips.single().status)
    }

    @Test
    fun `cancelled trip and zero allocation guard mapping preserve history and reject malformed fields`() {
        val journey = ConnectedJourney("j", "driver", "Mansfield", "Nottingham", 4_070_908_800_000L, 1, 0)
        val request = ConnectedSeatRequest("j_rider", "j", "driver", "rider", ConnectedRequestStatus.ACCEPTED)
        val confirmed = FirestoreJourneyMapper.confirmedTripData(journey, request)
        val cancelled = confirmed + mapOf("status" to "CANCELLED_BY_RIDER", "cancelledAt" to Timestamp(100, 0))
        assertEquals(100_000L, FirestoreJourneyMapper.confirmedTrip(request.id, cancelled)?.cancelledAtEpochMillis)
        assertEquals(null, FirestoreJourneyMapper.confirmedTrip(request.id, cancelled - "cancelledAt"))
        assertEquals(null, FirestoreJourneyMapper.confirmedTrip(request.id, cancelled + ("cancelledAt" to "bad")))
        assertEquals(null, FirestoreJourneyMapper.confirmedTrip(request.id, confirmed + ("cancelledAt" to Timestamp(100, 0))))
        val guard = mapOf("driverUid" to "driver", "acceptanceCount" to 0, "lastAcceptedRequestId" to request.id, "lastCancelledRequestId" to request.id)
        assertEquals(ConnectedJourneyAcceptanceGuard("driver", 0, request.id, request.id), FirestoreJourneyMapper.acceptanceGuard(guard))
        assertEquals(null, FirestoreJourneyMapper.acceptanceGuard(guard + ("lastCancelledRequestId" to null)))
    }

    @Test
    fun `acceptance guard mapping starts zeroed and rejects inconsistent states`() {
        val initial = FirestoreJourneyMapper.initialAcceptanceGuardData("driver")
        assertEquals(
            ConnectedJourneyAcceptanceGuard("driver", 0, null),
            FirestoreJourneyMapper.acceptanceGuard(initial),
        )
        assertEquals(
            ConnectedJourneyAcceptanceGuard("driver", 1, "journey-1_rider"),
            FirestoreJourneyMapper.acceptanceGuard(
                mapOf(
                    "driverUid" to "driver",
                    "acceptanceCount" to 1,
                    "lastAcceptedRequestId" to "journey-1_rider",
                ),
            ),
        )
        assertEquals(
            null,
            FirestoreJourneyMapper.acceptanceGuard(
                mapOf(
                    "driverUid" to "driver",
                    "acceptanceCount" to 1,
                    "lastAcceptedRequestId" to null,
                ),
            ),
        )
        assertEquals(
            ConnectedRequestStatus.CANCELLED,
            FirestoreJourneyMapper.request(
                "journey-1_rider",
                mapOf(
                    "journeyId" to "journey-1",
                    "driverUid" to "driver",
                    "riderUid" to "rider",
                    "status" to "CANCELLED",
                ),
            )?.status,
        )
        val namedRequestData = mapOf(
            "journeyId" to "journey-1",
            "driverUid" to "driver",
            "riderUid" to "rider",
            "status" to "PENDING",
            "riderDisplayName" to "Riley Rider",
        )
        assertEquals(
            "Riley Rider",
            FirestoreJourneyMapper.request("journey-1_rider", namedRequestData)?.riderDisplayName,
        )
        assertNull(FirestoreJourneyMapper.request("journey-1_rider", namedRequestData + ("riderDisplayName" to "\u0007")))
        assertNull(FirestoreJourneyMapper.request("journey-1_rider", namedRequestData + ("extra" to true)))
        assertEquals(
            namedRequestData,
            FirestoreJourneyMapper.requestData(
                ConnectedJourney("journey-1", "driver", "Mansfield", "Nottingham", 4_070_908_800_000L, 1, 1),
                "rider",
                "Riley Rider",
            ),
        )

        val journey = ConnectedJourney(
            id = "journey-1",
            driverUid = "driver",
            originArea = "Mansfield",
            destinationArea = "Nottingham",
            departureEpochMillis = 4_070_908_800_000L,
            seatCapacity = 1,
            seatsRemaining = 1,
        )
        val request = ConnectedSeatRequest(
            id = "journey-1_rider",
            journeyId = journey.id,
            driverUid = journey.driverUid,
            riderUid = "rider",
            status = ConnectedRequestStatus.PENDING,
        )
        val tripData = FirestoreJourneyMapper.confirmedTripData(journey, request)
        assertEquals(
            ConnectedConfirmedTrip(
                id = request.id,
                journeyId = journey.id,
                acceptedRequestId = request.id,
                driverUid = "driver",
                riderUid = "rider",
                originArea = "Mansfield",
                destinationArea = "Nottingham",
                departureEpochMillis = journey.departureEpochMillis,
                status = ConnectedTripStatus.CONFIRMED,
            ),
            FirestoreJourneyMapper.confirmedTrip(request.id, tripData),
        )
        assertEquals(null, FirestoreJourneyMapper.confirmedTrip("wrong", tripData))
        assertEquals(null, FirestoreJourneyMapper.confirmedTrip(request.id, tripData + ("extra" to true)))
        assertEquals(
            null,
            FirestoreJourneyMapper.confirmedTrip(
                request.id,
                tripData + ("departureAt" to Timestamp(0, 0)) + ("status" to "PENDING"),
            ),
        )
    }

    @Test
    fun `offer validation rejects private-looking areas past times and seat limits`() {
        assertTrue(ConnectedJourneyValidator.offer("12 High Street", "Derby", "2099-01-01 10:00", "1") is ValidationResult.Invalid)
        assertTrue(ConnectedJourneyValidator.offer("Nottingham", "Derby", "2000-01-01 10:00", "1") is ValidationResult.Invalid)
        assertTrue(ConnectedJourneyValidator.offer("Nottingham", "Derby", "2099-01-01 10:00", "9") is ValidationResult.Invalid)
        assertTrue(ConnectedJourneyValidator.offer("Nottingham", "Derby", "2099-01-01 10:00", "1") is ValidationResult.Valid)
    }

    @Test
    fun `pending request cancellation persists for rider and driver`() = runBlocking {
        val store = MemoryJourneyStore()
        val driver = repository("driver", store)
        val rider = repository("rider", store)
        driver.refresh()
        driver.createConnectedJourney("Mansfield", "Nottingham", "2099-01-01 10:00", "1")
        rider.refresh()
        val journeyId = rider.journeyState.value.journeys.single().id
        rider.requestConnectedSeat(journeyId)
        val requestId = rider.journeyState.value.requests.single().id

        assertEquals(ConnectedJourneyCommandResult.Success, rider.cancelConnectedRequest(requestId))
        assertEquals(ConnectedRequestStatus.CANCELLED, rider.journeyState.value.requests.single().status)
        driver.refresh()
        assertEquals(ConnectedRequestStatus.CANCELLED, driver.journeyState.value.requests.single().status)
    }

    @Test
    fun `cancelled request cannot be decided by driver`() = runBlocking {
        val store = MemoryJourneyStore()
        val driver = repository("driver", store)
        val rider = repository("rider", store)
        driver.refresh()
        driver.createConnectedJourney("Mansfield", "Nottingham", "2099-01-01 10:00", "1")
        rider.refresh()
        rider.requestConnectedSeat(rider.journeyState.value.journeys.single().id)
        val requestId = rider.journeyState.value.requests.single().id
        rider.cancelConnectedRequest(requestId)

        assertTrue(driver.decideConnectedRequest(requestId, true) is ConnectedJourneyCommandResult.Failure)
        assertTrue(driver.decideConnectedRequest(requestId, false) is ConnectedJourneyCommandResult.Failure)
        driver.refresh()
        assertEquals(ConnectedRequestStatus.CANCELLED, driver.journeyState.value.requests.single().status)
        assertEquals(1, driver.journeyState.value.journeys.single().seatsRemaining)
    }

    @Test
    fun `rider can re-request a cancelled request while capacity remains`() = runBlocking {
        val store = MemoryJourneyStore()
        val driver = repository("driver", store)
        val rider = repository("rider", store)
        driver.refresh()
        driver.createConnectedJourney("Mansfield", "Nottingham", "2099-01-01 10:00", "1")
        rider.refresh()
        val journeyId = rider.journeyState.value.journeys.single().id
        rider.requestConnectedSeat(journeyId)
        rider.cancelConnectedRequest(rider.journeyState.value.requests.single().id)

        assertEquals(ConnectedJourneyCommandResult.Success, rider.requestConnectedSeat(journeyId))
        assertEquals(ConnectedRequestStatus.PENDING, rider.journeyState.value.requests.single().status)
    }

    @Test
    fun `re-request fails after another acceptance consumes remaining capacity`() = runBlocking {
        val store = MemoryJourneyStore()
        val driver = repository("driver", store)
        val riderA = repository("rider-a", store)
        val riderB = repository("rider-b", store)
        driver.refresh()
        driver.createConnectedJourney("Mansfield", "Nottingham", "2099-01-01 10:00", "1")
        riderA.refresh()
        val journeyId = riderA.journeyState.value.journeys.single().id
        riderA.requestConnectedSeat(journeyId)
        riderA.cancelConnectedRequest(riderA.journeyState.value.requests.single().id)
        riderB.refresh()
        riderB.requestConnectedSeat(journeyId)
        driver.refresh()
        driver.decideConnectedRequest(
            driver.journeyState.value.requests.single { it.riderUid == "rider-b" }.id,
            true,
        )

        assertTrue(riderA.requestConnectedSeat(journeyId) is ConnectedJourneyCommandResult.Failure)
        riderA.refresh()
        assertEquals(
            ConnectedRequestStatus.CANCELLED,
            riderA.journeyState.value.requests.single { it.riderUid == "rider-a" }.status,
        )
    }

    @Test
    fun `accepted and declined requests remain terminal for rider actions`() = runBlocking {
        val store = MemoryJourneyStore()
        val driver = repository("driver", store)
        val acceptedRider = repository("accepted-rider", store)
        val declinedRider = repository("declined-rider", store)
        driver.refresh()
        driver.createConnectedJourney("Mansfield", "Nottingham", "2099-01-01 10:00", "2")
        acceptedRider.refresh()
        val journeyId = acceptedRider.journeyState.value.journeys.single().id
        acceptedRider.requestConnectedSeat(journeyId)
        declinedRider.refresh()
        declinedRider.requestConnectedSeat(journeyId)
        driver.refresh()
        val acceptedId = driver.journeyState.value.requests.single { it.riderUid == "accepted-rider" }.id
        val declinedId = driver.journeyState.value.requests.single { it.riderUid == "declined-rider" }.id
        driver.decideConnectedRequest(acceptedId, true)
        driver.decideConnectedRequest(declinedId, false)

        assertTrue(acceptedRider.cancelConnectedRequest(acceptedId) is ConnectedJourneyCommandResult.Failure)
        assertTrue(acceptedRider.requestConnectedSeat(journeyId) is ConnectedJourneyCommandResult.Failure)
        assertTrue(declinedRider.cancelConnectedRequest(declinedId) is ConnectedJourneyCommandResult.Failure)
        assertTrue(declinedRider.requestConnectedSeat(journeyId) is ConnectedJourneyCommandResult.Failure)
    }

    @Test
    fun `two accounts observe pending then accepted request and decremented seat after refresh`() = runBlocking {
        val store = MemoryJourneyStore()
        val driver = repository("driver", store)
        val rider = repository("rider", store)
        driver.refresh()
        rider.refresh()

        assertEquals(ConnectedJourneyCommandResult.Success, driver.createConnectedJourney("Mansfield", "Nottingham", "2099-01-01 10:00", "1"))
        rider.refresh()
        val journey = rider.journeyState.value.journeys.single()
        assertEquals(ConnectedJourneyCommandResult.Success, rider.requestConnectedSeat(journey.id))
        driver.refresh()
        val pending = driver.journeyState.value.requests.single()
        assertEquals(ConnectedRequestStatus.PENDING, pending.status)

        assertEquals(ConnectedJourneyCommandResult.Success, driver.decideConnectedRequest(pending.id, true))
        rider.refresh()
        assertEquals(ConnectedRequestStatus.ACCEPTED, rider.journeyState.value.requests.single().status)
        assertEquals(0, rider.journeyState.value.journeys.single().seatsRemaining)
        val driverTrip = driver.journeyState.value.confirmedTrips.single()
        val riderTrip = rider.journeyState.value.confirmedTrips.single()
        assertEquals(pending.id, driverTrip.id)
        assertEquals(driverTrip, riderTrip)
        assertEquals(ConnectedTripStatus.CONFIRMED, riderTrip.status)

        val stranger = repository("stranger", store)
        stranger.refresh()
        assertTrue(stranger.journeyState.value.confirmedTrips.isEmpty())
    }

    @Test
    fun `decline preserves seats and repeat decision fails safely`() = runBlocking {
        val store = MemoryJourneyStore()
        val driver = repository("driver", store)
        val rider = repository("rider", store)
        driver.refresh()
        driver.createConnectedJourney("Mansfield", "Nottingham", "2099-01-01 10:00", "1")
        rider.refresh()
        rider.requestConnectedSeat(rider.journeyState.value.journeys.single().id)
        driver.refresh()
        val request = driver.journeyState.value.requests.single()

        assertEquals(ConnectedJourneyCommandResult.Success, driver.decideConnectedRequest(request.id, false))
        assertEquals(1, driver.journeyState.value.journeys.single().seatsRemaining)
        assertTrue(driver.journeyState.value.confirmedTrips.isEmpty())
        assertTrue(driver.decideConnectedRequest(request.id, true) is ConnectedJourneyCommandResult.Failure)
        assertTrue(driver.journeyState.value.confirmedTrips.isEmpty())
    }

    @Test
    fun `duplicate acceptance leaves one deterministic confirmed trip`() = runBlocking {
        val store = MemoryJourneyStore()
        val driver = repository("driver", store)
        val rider = repository("rider", store)
        driver.refresh()
        driver.createConnectedJourney("Mansfield", "Nottingham", "2099-01-01 10:00", "1")
        rider.refresh()
        rider.requestConnectedSeat(rider.journeyState.value.journeys.single().id)
        driver.refresh()
        val requestId = driver.journeyState.value.requests.single().id

        assertEquals(ConnectedJourneyCommandResult.Success, driver.decideConnectedRequest(requestId, true))
        assertTrue(driver.decideConnectedRequest(requestId, true) is ConnectedJourneyCommandResult.Failure)
        assertEquals(listOf(requestId), driver.journeyState.value.confirmedTrips.map { it.id })
    }

    @Test
    fun `second acceptance cannot overbook a one-seat journey`() = runBlocking {
        val store = MemoryJourneyStore()
        val driver = repository("driver", store)
        val riderA = repository("rider-a", store)
        val riderB = repository("rider-b", store)
        driver.refresh()
        driver.createConnectedJourney("Mansfield", "Nottingham", "2099-01-01 10:00", "1")
        riderA.refresh()
        riderA.requestConnectedSeat(riderA.journeyState.value.journeys.single().id)
        riderB.refresh()
        riderB.requestConnectedSeat(riderB.journeyState.value.journeys.single().id)
        driver.refresh()

        val requests = driver.journeyState.value.requests
        assertEquals(ConnectedJourneyCommandResult.Success, driver.decideConnectedRequest(requests[0].id, true))
        assertTrue(driver.decideConnectedRequest(requests[1].id, true) is ConnectedJourneyCommandResult.Failure)
        assertEquals(0, driver.journeyState.value.journeys.single().seatsRemaining)
    }

    @Test
    fun `journey cancellation propagates instead of becoming a safe failure`() = runBlocking {
        val cancellingStore = object : ConnectedJourneyStore {
            override suspend fun load(uid: String) = ConnectedJourneySnapshot()
            override suspend fun create(uid: String, draft: ConnectedJourneyDraft) { throw CancellationException("cancel") }
            override suspend fun requestSeat(uid: String, journeyId: String) = Unit
            override suspend fun cancelRequest(uid: String, requestId: String) = Unit
            override suspend fun cancelConfirmedSeat(uid: String, tripId: String) { throw CancellationException("cancel") }
            override suspend fun cancelJourney(uid: String, journeyId: String) { throw CancellationException("cancel") }
            override suspend fun decide(uid: String, requestId: String, accept: Boolean) = Unit
        }
        val repository = repository("driver", cancellingStore)
        try {
            repository.createConnectedJourney("Mansfield", "Nottingham", "2099-01-01 10:00", "1")
            fail("Cancellation should propagate")
        } catch (_: CancellationException) {
            assertTrue(true)
        }
        try {
            repository.cancelConnectedConfirmedSeat("trip")
            fail("Cancellation should propagate")
        } catch (_: CancellationException) {
            assertTrue(true)
        }
        try {
            repository.cancelConnectedJourney("journey")
            fail("Cancellation should propagate")
        } catch (_: CancellationException) {
            assertTrue(true)
        }
    }

    private fun repository(uid: String, store: ConnectedJourneyStore): ConnectedRydeRepository =
        ConnectedRydeRepository(
            auth = StaticAuth(uid),
            profiles = StaticProfiles(uid),
            journeys = store,
        )

    private class StaticAuth(private val uid: String) : ConnectedAuthGateway {
        override val currentUserId: String = uid
        override suspend fun register(email: String, password: String) = uid
        override suspend fun signIn(email: String, password: String) = uid
        override suspend fun signOut() = Unit
    }

    private class StaticProfiles(private val uid: String) : ConnectedProfileStore {
        override suspend fun create(profile: ConnectedUserProfile) = Unit
        override suspend fun load(uid: String) = ConnectedProfile(ConnectedUserProfile(uid, uid), emptyList())
        override suspend fun save(uid: String, draft: ConnectedProfileDraft) = Unit
    }

    private class MemoryJourneyStore(private val nowMillis: () -> Long = System::currentTimeMillis) : ConnectedJourneyStore {
        val guards = linkedMapOf<String, ConnectedJourneyAcceptanceGuard>()
        private val journeys = linkedMapOf<String, ConnectedJourney>()
        private val requests = linkedMapOf<String, ConnectedSeatRequest>()
        private val confirmedTrips = linkedMapOf<String, ConnectedConfirmedTrip>()

        override suspend fun load(uid: String) = ConnectedJourneySnapshot(
            journeys.values.toList(),
            requests.values.filter { it.driverUid == uid || it.riderUid == uid },
            confirmedTrips.values.filter { it.driverUid == uid || it.riderUid == uid },
        )

        override suspend fun create(uid: String, draft: ConnectedJourneyDraft) {
            val id = "journey-${journeys.size + 1}"
            journeys[id] = ConnectedJourney(id, uid, draft.originArea, draft.destinationArea, draft.departureEpochMillis, draft.seats, draft.seats)
            guards[id] = ConnectedJourneyAcceptanceGuard(uid, 0, null)
        }

        override suspend fun requestSeat(uid: String, journeyId: String) {
            val journey = checkNotNull(journeys[journeyId])
            check(journey.status == ConnectedJourneyStatus.OPEN)
            check(journey.driverUid != uid && journey.seatsRemaining > 0 && journey.departureEpochMillis > System.currentTimeMillis())
            val id = "${journeyId}_${uid}"
            val existing = requests[id]
            check(existing == null || existing.status == ConnectedRequestStatus.CANCELLED)
            requests[id] = existing?.copy(status = ConnectedRequestStatus.PENDING)
                ?: ConnectedSeatRequest(id, journeyId, journey.driverUid, uid, ConnectedRequestStatus.PENDING)
        }

        override suspend fun cancelRequest(uid: String, requestId: String) {
            val request = checkNotNull(requests[requestId])
            check(journeys.getValue(request.journeyId).status == ConnectedJourneyStatus.OPEN)
            check(request.riderUid == uid && request.status == ConnectedRequestStatus.PENDING)
            requests[requestId] = request.copy(status = ConnectedRequestStatus.CANCELLED)
        }

        override suspend fun cancelConfirmedSeat(uid: String, tripId: String) {
            val trip = checkNotNull(confirmedTrips[tripId])
            val request = checkNotNull(requests[tripId])
            val journey = checkNotNull(journeys[trip.journeyId])
            check(journey.status == ConnectedJourneyStatus.OPEN)
            val guard = checkNotNull(guards[journey.id])
            check(trip.riderUid == uid && trip.status == ConnectedTripStatus.CONFIRMED)
            check(request.status == ConnectedRequestStatus.ACCEPTED && journey.departureEpochMillis > nowMillis())
            check(guard.acceptanceCount == journey.seatCapacity - journey.seatsRemaining && guard.acceptanceCount > 0)
            requests[tripId] = request.copy(status = ConnectedRequestStatus.CANCELLED_AFTER_ACCEPTANCE)
            confirmedTrips[tripId] = trip.copy(status = ConnectedTripStatus.CANCELLED_BY_RIDER, cancelledAtEpochMillis = nowMillis())
            journeys[journey.id] = journey.copy(seatsRemaining = journey.seatsRemaining + 1)
            guards[journey.id] = guard.copy(acceptanceCount = guard.acceptanceCount - 1, lastCancelledRequestId = tripId)
        }

        override suspend fun cancelJourney(uid: String, journeyId: String) {
            val journey = checkNotNull(journeys[journeyId])
            check(ConnectedJourneyLifecycle.canCancelJourney(journey, uid, nowMillis()))
            val guard = guards.getValue(journeyId)
            check(guard.driverUid == uid && guard.acceptanceCount == journey.seatCapacity - journey.seatsRemaining)
            journeys[journeyId] = journey.copy(status = ConnectedJourneyStatus.CANCELLED, cancelledAtEpochMillis = nowMillis())
        }

        override suspend fun decide(uid: String, requestId: String, accept: Boolean) {
            val request = checkNotNull(requests[requestId])
            check(request.driverUid == uid && request.status == ConnectedRequestStatus.PENDING)
            val journey = checkNotNull(journeys[request.journeyId])
            check(journey.status == ConnectedJourneyStatus.OPEN)
            if (accept) {
                check(journey.seatsRemaining > 0 && journey.departureEpochMillis > System.currentTimeMillis())
                check(requestId !in confirmedTrips)
                journeys[journey.id] = journey.copy(seatsRemaining = journey.seatsRemaining - 1)
                guards[journey.id] = guards.getValue(journey.id).let { it.copy(acceptanceCount = it.acceptanceCount + 1, lastAcceptedRequestId = requestId) }
                confirmedTrips[requestId] = ConnectedConfirmedTrip(
                    id = requestId,
                    journeyId = journey.id,
                    acceptedRequestId = requestId,
                    driverUid = request.driverUid,
                    riderUid = request.riderUid,
                    originArea = journey.originArea,
                    destinationArea = journey.destinationArea,
                    departureEpochMillis = journey.departureEpochMillis,
                    status = ConnectedTripStatus.CONFIRMED,
                )
            }
            requests[requestId] = request.copy(status = if (accept) ConnectedRequestStatus.ACCEPTED else ConnectedRequestStatus.DECLINED)
        }
    }
}
