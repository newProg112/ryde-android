package uk.rydeapp.ryde.data.connected

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ConnectedJourneyFlowTest {
    @Test
    fun `offer validation rejects private-looking areas past times and seat limits`() {
        assertTrue(ConnectedJourneyValidator.offer("12 High Street", "Derby", "2099-01-01 10:00", "1") is ValidationResult.Invalid)
        assertTrue(ConnectedJourneyValidator.offer("Nottingham", "Derby", "2000-01-01 10:00", "1") is ValidationResult.Invalid)
        assertTrue(ConnectedJourneyValidator.offer("Nottingham", "Derby", "2099-01-01 10:00", "9") is ValidationResult.Invalid)
        assertTrue(ConnectedJourneyValidator.offer("Nottingham", "Derby", "2099-01-01 10:00", "1") is ValidationResult.Valid)
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
        assertTrue(driver.decideConnectedRequest(request.id, true) is ConnectedJourneyCommandResult.Failure)
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

        override suspend fun load(uid: String) = ConnectedJourneySnapshot(
            journeys.values.toList(),
            requests.values.filter { it.driverUid == uid || it.riderUid == uid },
        )

        override suspend fun create(uid: String, draft: ConnectedJourneyDraft) {
            val id = "journey-${journeys.size + 1}"
            journeys[id] = ConnectedJourney(id, uid, draft.originArea, draft.destinationArea, draft.departureEpochMillis, draft.seats, draft.seats)
        }

        override suspend fun requestSeat(uid: String, journeyId: String) {
            val journey = checkNotNull(journeys[journeyId])
            check(journey.driverUid != uid && journey.seatsRemaining > 0)
            val id = "${journeyId}_${uid}"
            check(id !in requests)
            requests[id] = ConnectedSeatRequest(id, journeyId, journey.driverUid, uid, ConnectedRequestStatus.PENDING)
        }

        override suspend fun decide(uid: String, requestId: String, accept: Boolean) {
            val request = checkNotNull(requests[requestId])
            check(request.driverUid == uid && request.status == ConnectedRequestStatus.PENDING)
            val journey = checkNotNull(journeys[request.journeyId])
            if (accept) {
                check(journey.seatsRemaining > 0)
                journeys[journey.id] = journey.copy(seatsRemaining = journey.seatsRemaining - 1)
            }
            requests[requestId] = request.copy(status = if (accept) ConnectedRequestStatus.ACCEPTED else ConnectedRequestStatus.DECLINED)
        }
    }
}
