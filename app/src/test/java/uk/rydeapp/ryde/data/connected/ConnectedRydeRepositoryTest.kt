package uk.rydeapp.ryde.data.connected

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
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

class ConnectedRydeRepositoryTest {
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
}
