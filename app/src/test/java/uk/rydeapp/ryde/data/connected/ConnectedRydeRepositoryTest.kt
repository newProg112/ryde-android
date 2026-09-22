package uk.rydeapp.ryde.data.connected

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import uk.rydeapp.ryde.data.AccountCommandResult
import uk.rydeapp.ryde.data.AccountSession
import uk.rydeapp.ryde.data.AsyncState
import uk.rydeapp.ryde.data.RydeRepository
import uk.rydeapp.ryde.data.RydeSnapshot
import uk.rydeapp.ryde.app.RydeAppStateHolder
import uk.rydeapp.ryde.app.RydeAppUiState
import uk.rydeapp.ryde.data.AppMode
import uk.rydeapp.ryde.domain.model.SavedPlace
import uk.rydeapp.ryde.R
import uk.rydeapp.ryde.ui.trips.connectedTripsContent

class ConnectedRydeRepositoryTest {
    @Test
    fun `remote journey closure updates normal snapshot and stops observing on sign out`() = runBlocking {
        val uid = "rider"
        val auth = FakeAuth(initialUid = uid)
        val profiles = FakeProfiles().apply {
            saved = ConnectedProfile(ConnectedUserProfile(uid, "Riley"), emptyList())
        }
        val journey = ConnectedJourney("journey", "driver", "Derby", "Nottingham", 4_070_908_800_000L, 2, 1)
        val request = ConnectedSeatRequest("journey_rider", journey.id, journey.driverUid, uid,
            ConnectedRequestStatus.ACCEPTED, "Riley")
        val trip = ConnectedConfirmedTrip(request.id, journey.id, request.id, journey.driverUid, uid,
            journey.originArea, journey.destinationArea, journey.departureEpochMillis,
            ConnectedTripStatus.CONFIRMED, driverDisplayName = "Morgan")
        val store = ObservingJourneys(ConnectedJourneySnapshot(listOf(journey), listOf(request), listOf(trip)))
        val repository = ConnectedRydeRepository(auth, profiles, journeys = store)
        repository.refresh()
        val observation = launch { repository.synchronizeConnectedJourneyLifecycles() }
        withTimeout(1_000) { while (store.activeObservers == 0) yield() }

        store.publish(listOf(journey.copy(
            seatsRemaining = 0,
            status = ConnectedJourneyStatus.CANCELLED,
            cancelledAtEpochMillis = 100,
        )))
        withTimeout(1_000) {
            while (repository.journeyState.value.journeys.single().status == ConnectedJourneyStatus.OPEN) yield()
        }

        val updated = repository.journeyState.value
        assertEquals(ConnectedJourneyStatus.CANCELLED, updated.journeys.single().status)
        assertEquals(listOf(request), updated.requests)
        assertEquals(listOf(trip), updated.confirmedTrips)
        val rider = connectedTripsContent(updated, uid, 0).rider.single()
        assertEquals(R.string.connected_trips_driver_cancelled, rider.statusText)
        assertEquals(null, rider.cancellableTripId)
        assertEquals(trip.id, rider.messageTarget?.tripId)

        repository.signOut()
        observation.join()
        assertEquals(0, store.activeObservers)
        assertEquals(ConnectedJourneySnapshot(), repository.journeyState.value)
    }

    @Test
    fun `journey lifecycle reconciliation ignores additions reopen and changed identity`() {
        val open = ConnectedJourney("journey", "driver", "Derby", "Nottingham", 100, 2, 1)
        val request = ConnectedSeatRequest("journey_rider", open.id, open.driverUid, "rider", ConnectedRequestStatus.ACCEPTED)
        val trip = ConnectedConfirmedTrip(request.id, open.id, request.id, open.driverUid, "rider",
            open.originArea, open.destinationArea, open.departureEpochMillis, ConnectedTripStatus.CONFIRMED)
        val snapshot = ConnectedJourneySnapshot(listOf(open), listOf(request), listOf(trip))

        assertEquals(snapshot, snapshot.reconcileRemoteJourneyClosures(listOf(
            open.copy(driverUid = "other", status = ConnectedJourneyStatus.CANCELLED, cancelledAtEpochMillis = 1),
            open.copy(id = "new", status = ConnectedJourneyStatus.CANCELLED, cancelledAtEpochMillis = 1),
        )))
        val closed = open.copy(status = ConnectedJourneyStatus.CANCELLED, cancelledAtEpochMillis = 1)
        assertEquals(snapshot.copy(journeys = listOf(closed)), snapshot.reconcileRemoteJourneyClosures(listOf(closed)))
        val declined = snapshot.copy(requests = listOf(request.copy(status = ConnectedRequestStatus.DECLINED)))
        assertEquals(
            declined.copy(journeys = listOf(closed)),
            declined.reconcileRemoteJourneyClosures(listOf(closed)),
        )
        assertEquals(
            snapshot.copy(journeys = listOf(closed)),
            snapshot.copy(journeys = listOf(closed)).reconcileRemoteJourneyClosures(listOf(open)),
        )
    }

    @Test
    fun `connected implementation satisfies Phase 9A aggregate contract`() {
        val repository: RydeRepository = ConnectedRydeRepository(FakeAuth(), FakeProfiles())
        assertTrue(repository is RydeRepository)
        assertEquals(AccountSession.SignedOut, repository.sessionState.value)
    }

    @Test
    fun `registration creates profile then publishes authenticated data`() = runBlocking {
        val auth = FakeAuth()
        val profiles = FakeProfiles()
        val repository = ConnectedRydeRepository(auth, profiles)

        val result = repository.register("alex@example.test", "password-123", "Alex Rider")

        assertEquals(AccountCommandResult.Success, result)
        val session = repository.sessionState.value as AccountSession.Authenticated
        assertEquals("uid-alex", session.accountId)
        assertFalse(session.isFictionalDemo)
        assertEquals("Alex Rider", session.displayName)
        assertTrue(repository.appState.value is AsyncState.Data)
        assertEquals("Alex Rider", profiles.saved?.user?.displayName)
    }

    @Test
    fun `profile Home and Work survive sign out and sign in while memory is cleared`() = runBlocking {
        val auth = FakeAuth()
        val profiles = FakeProfiles()
        val repository = ConnectedRydeRepository(auth, profiles)
        repository.register("alex@example.test", "password-123", "Alex")
        repository.updateConnectedProfile("Alex R", "Sutton-in-Ashfield", "Nottingham")

        assertEquals(setOf("Home", "Work"), repository.getSavedPlaces().map { it.label }.toSet())
        repository.signOut()
        assertEquals(AccountSession.SignedOut, repository.sessionState.value)
        assertTrue(repository.appState.value is AsyncState.Empty)
        assertTrue(repository.getSavedPlaces().isEmpty())

        repository.signIn("alex@example.test", "password-123")
        val snapshot = (repository.appState.value as AsyncState.Data<RydeSnapshot>).value
        assertEquals("Alex R", (repository.sessionState.value as AccountSession.Authenticated).displayName)
        assertEquals("Sutton-in-Ashfield", snapshot.profileContent.savedPlaces.first { it.label == "Home" }.area)
        assertEquals("Nottingham", snapshot.profileContent.savedPlaces.first { it.label == "Work" }.area)
    }

    @Test
    fun `ordinary auth failure is safe and does not leak exception details`() = runBlocking {
        val repository = ConnectedRydeRepository(FakeAuth(failure = IllegalStateException("secret firebase detail")), FakeProfiles())
        val result = repository.signIn("alex@example.test", "password-123") as AccountCommandResult.Failure
        assertEquals(ConnectedRydeRepository.SAFE_ACCOUNT_ERROR, result.userMessage)
        assertFalse(result.userMessage.contains("secret"))
        assertEquals(AccountSession.SignedOut, repository.sessionState.value)
    }

    @Test
    fun `cancellation is rethrown`() = runBlocking {
        val repository = ConnectedRydeRepository(FakeAuth(failure = CancellationException("cancel")), FakeProfiles())
        try {
            repository.signIn("alex@example.test", "password-123")
            fail("Cancellation should propagate")
        } catch (_: CancellationException) {
            assertTrue(true)
        }
    }

    @Test
    fun `committed profile save followed by stuck load times out and keeps previous Ready profile`() = runBlocking {
        val auth = FakeAuth()
        val profiles = FakeProfiles()
        val repository = ConnectedRydeRepository(auth, profiles, firebaseOperationTimeoutMillis = 50)
        repository.register("alex@example.test", "password-123", "Alex")
        repository.updateConnectedProfile("Alex", "Derby", "Nottingham")
        val holder = RydeAppStateHolder(
            repository,
            AppMode.CONNECTED,
            CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        )
        val before = holder.uiState.value as RydeAppUiState.Ready
        profiles.hangOnLoad = true

        val result = holder.updateConnectedProfile("Alex Updated", "Mansfield", "Nottingham")

        assertTrue(result is AccountCommandResult.Failure)
        val failure = result as AccountCommandResult.Failure
        assertEquals(ConnectedRydeRepository.SAFE_ACCOUNT_ERROR, failure.userMessage)
        assertFalse(failure.userMessage.contains("FirebaseOperation"))
        val recovered = holder.uiState.value as RydeAppUiState.Ready
        assertEquals("Alex", (recovered.session as AccountSession.Authenticated).displayName)
        assertEquals(before.snapshot, recovered.snapshot)
        assertEquals("Alex Updated", profiles.saved?.user?.displayName)
        assertEquals("Mansfield", profiles.saved?.savedPlaces?.first { it.label == "Home" }?.area)
    }

    @Test
    fun `stuck initial profile load becomes bounded safe error instead of Loading forever`() = runBlocking {
        val auth = FakeAuth(initialUid = "uid-alex")
        val profiles = FakeProfiles().apply {
            saved = ConnectedProfile(ConnectedUserProfile("uid-alex", "Alex"), emptyList())
            hangOnLoad = true
        }
        val repository = ConnectedRydeRepository(auth, profiles, firebaseOperationTimeoutMillis = 50)
        val holder = RydeAppStateHolder(
            repository,
            AppMode.CONNECTED,
            CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        )

        delay(150)

        val error = holder.uiState.value as RydeAppUiState.Error
        assertEquals(RydeAppStateHolder.SAFE_LOAD_ERROR, error.userMessage)
        assertFalse(error.userMessage.contains("Firebase"))
        assertTrue(repository.appState.value is AsyncState.Error)
    }

    @Test
    fun `stuck registration returns safe failure and restores signed-out state`() = runBlocking {
        val hangingAuth = object : ConnectedAuthGateway {
            override val currentUserId: String? = null
            override suspend fun register(email: String, password: String): String = awaitCancellation()
            override suspend fun signIn(email: String, password: String): String = awaitCancellation()
            override suspend fun signOut() = Unit
        }
        val repository = ConnectedRydeRepository(hangingAuth, FakeProfiles(), firebaseOperationTimeoutMillis = 50)

        val result = repository.register("alex@example.test", "password-123", "Alex")

        assertTrue(result is AccountCommandResult.Failure)
        assertEquals(ConnectedRydeRepository.SAFE_ACCOUNT_ERROR, (result as AccountCommandResult.Failure).userMessage)
        assertEquals(AccountSession.SignedOut, repository.sessionState.value)
        assertTrue(repository.appState.value is AsyncState.Empty)
    }

    private class FakeAuth(
        private val failure: Throwable? = null,
        initialUid: String? = null,
    ) : ConnectedAuthGateway {
        private var uid: String? = initialUid
        override val currentUserId: String? get() = uid

        override suspend fun register(email: String, password: String): String {
            failure?.let { throw it }
            return "uid-alex".also { uid = it }
        }

        override suspend fun signIn(email: String, password: String): String {
            failure?.let { throw it }
            return "uid-alex".also { uid = it }
        }

        override suspend fun signOut() {
            uid = null
        }
    }

    private class FakeProfiles : ConnectedProfileStore {
        var saved: ConnectedProfile? = null
        var hangOnLoad: Boolean = false

        override suspend fun create(profile: ConnectedUserProfile) {
            saved = ConnectedProfile(profile, emptyList())
        }

        override suspend fun load(uid: String): ConnectedProfile? {
            if (hangOnLoad) awaitCancellation()
            return saved
        }

        override suspend fun save(uid: String, draft: ConnectedProfileDraft) {
            saved = ConnectedProfile(
                ConnectedUserProfile(uid, draft.displayName),
                listOf(SavedPlace("Home", draft.homeArea), SavedPlace("Work", draft.workArea)),
            )
        }
    }

    private class ObservingJourneys(initial: ConnectedJourneySnapshot) : ConnectedJourneyStore {
        private val updates = MutableStateFlow(initial.journeys)
        private var snapshot = initial
        var activeObservers = 0

        suspend fun publish(journeys: List<ConnectedJourney>) {
            snapshot = snapshot.copy(journeys = journeys)
            updates.emit(journeys)
        }

        override suspend fun load(uid: String): ConnectedJourneySnapshot = snapshot

        override fun observeJourneys(uid: String): Flow<List<ConnectedJourney>> = flow {
            activeObservers++
            try {
                updates.collect { emit(it) }
            } finally {
                activeObservers--
            }
        }

        override suspend fun create(uid: String, draft: ConnectedJourneyDraft) = Unit
        override suspend fun requestSeat(uid: String, journeyId: String) = Unit
        override suspend fun cancelRequest(uid: String, requestId: String) = Unit
        override suspend fun cancelConfirmedSeat(uid: String, tripId: String) = Unit
        override suspend fun cancelJourney(uid: String, journeyId: String) = Unit
        override suspend fun decide(uid: String, requestId: String, accept: Boolean) = Unit
    }
}
