package uk.rydeapp.ryde.data.connected

import com.google.firebase.Timestamp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ConnectedJourneyFlowTest {
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
            override suspend fun decide(uid: String, requestId: String, accept: Boolean) = Unit
        }
        val repository = repository("driver", cancellingStore)
        try {
            repository.createConnectedJourney("Mansfield", "Nottingham", "2099-01-01 10:00", "1")
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

    private class MemoryJourneyStore : ConnectedJourneyStore {
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
        }

        override suspend fun requestSeat(uid: String, journeyId: String) {
            val journey = checkNotNull(journeys[journeyId])
            check(journey.driverUid != uid && journey.seatsRemaining > 0 && journey.departureEpochMillis > System.currentTimeMillis())
            val id = "${journeyId}_${uid}"
            val existing = requests[id]
            check(existing == null || existing.status == ConnectedRequestStatus.CANCELLED)
            requests[id] = existing?.copy(status = ConnectedRequestStatus.PENDING)
                ?: ConnectedSeatRequest(id, journeyId, journey.driverUid, uid, ConnectedRequestStatus.PENDING)
        }

        override suspend fun cancelRequest(uid: String, requestId: String) {
            val request = checkNotNull(requests[requestId])
            check(request.riderUid == uid && request.status == ConnectedRequestStatus.PENDING)
            requests[requestId] = request.copy(status = ConnectedRequestStatus.CANCELLED)
        }

        override suspend fun decide(uid: String, requestId: String, accept: Boolean) {
            val request = checkNotNull(requests[requestId])
            check(request.driverUid == uid && request.status == ConnectedRequestStatus.PENDING)
            val journey = checkNotNull(journeys[request.journeyId])
            if (accept) {
                check(journey.seatsRemaining > 0 && journey.departureEpochMillis > System.currentTimeMillis())
                check(requestId !in confirmedTrips)
                journeys[journey.id] = journey.copy(seatsRemaining = journey.seatsRemaining - 1)
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
