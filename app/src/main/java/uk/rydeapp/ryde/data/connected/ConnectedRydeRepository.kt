package uk.rydeapp.ryde.data.connected

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import uk.rydeapp.ryde.data.AccountCommandResult
import uk.rydeapp.ryde.data.AccountSession
import uk.rydeapp.ryde.data.AsyncState
import uk.rydeapp.ryde.data.FakeRydeRepository
import uk.rydeapp.ryde.data.RydeRepository
import uk.rydeapp.ryde.data.RydeSnapshot
import uk.rydeapp.ryde.domain.model.ProfileContent
import uk.rydeapp.ryde.domain.model.SavePlaceResult
import uk.rydeapp.ryde.domain.model.SavedPlace

/** Firebase-emulator repository. Connected capabilities are explicit; demo data is never rendered in connected UI. */
class ConnectedRydeRepository(
    private val auth: ConnectedAuthGateway,
    private val profiles: ConnectedProfileStore,
    private val legacyCapabilities: RydeRepository = FakeRydeRepository(),
    private val firebaseOperationTimeoutMillis: Long = FIREBASE_OPERATION_TIMEOUT_MILLIS,
    private val journeys: ConnectedJourneyStore? = null,
) : RydeRepository by legacyCapabilities {
    private val mutableSessionState = MutableStateFlow<AccountSession>(
        if (auth.currentUserId == null) AccountSession.SignedOut else AccountSession.Checking,
    )
    override val sessionState: StateFlow<AccountSession> = mutableSessionState.asStateFlow()

    private val mutableAppState = MutableStateFlow<AsyncState<RydeSnapshot>>(AsyncState.Empty)
    override val appState: StateFlow<AsyncState<RydeSnapshot>> = mutableAppState.asStateFlow()

    private var connectedProfile: ConnectedProfile? = null
    private val mutableJourneyState = MutableStateFlow(ConnectedJourneySnapshot())
    val journeyState: StateFlow<ConnectedJourneySnapshot> = mutableJourneyState.asStateFlow()

    override suspend fun register(email: String, password: String, displayName: String): AccountCommandResult {
        val input = when (val result = ConnectedAccountValidator.registration(email, password, displayName)) {
            is ValidationResult.Invalid -> return AccountCommandResult.InvalidInput(result.userMessage)
            is ValidationResult.Valid -> result.value
        }
        return safely {
            val uid = firebaseCall { auth.register(input.email, input.password) }
            try {
                firebaseCall { profiles.create(ConnectedUserProfile(uid, checkNotNull(input.displayName))) }
                refresh()
            } catch (failure: Throwable) {
                if (failure is CancellationException) throw failure
                try {
                    firebaseCall { auth.signOut() }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Throwable) {
                    // The original safe failure is more useful than a cleanup failure.
                } finally {
                    clearSignedOut()
                }
                throw failure
            }
        }
    }

    override suspend fun signIn(email: String, password: String): AccountCommandResult {
        val input = when (val result = ConnectedAccountValidator.credentials(email, password)) {
            is ValidationResult.Invalid -> return AccountCommandResult.InvalidInput(result.userMessage)
            is ValidationResult.Valid -> result.value
        }
        return safely {
            firebaseCall { auth.signIn(input.email, input.password) }
            try {
                refresh()
            } catch (failure: Throwable) {
                if (failure is CancellationException) throw failure
                try {
                    firebaseCall { auth.signOut() }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Throwable) {
                    // The original safe failure is more useful than a cleanup failure.
                } finally {
                    clearSignedOut()
                }
                throw failure
            }
        }
    }

    override suspend fun signOut(): AccountCommandResult = try {
        firebaseCall { auth.signOut() }
        AccountCommandResult.Success
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Throwable) {
        AccountCommandResult.Failure(SAFE_ACCOUNT_ERROR)
    } finally {
        clearSignedOut()
    }

    override suspend fun updateConnectedProfile(
        displayName: String,
        homeArea: String,
        workArea: String,
    ): AccountCommandResult {
        val draft = when (val result = ConnectedAccountValidator.profile(displayName, homeArea, workArea)) {
            is ValidationResult.Invalid -> return AccountCommandResult.InvalidInput(result.userMessage)
            is ValidationResult.Valid -> result.value
        }
        return safely {
            val uid = requireNotNull(auth.currentUserId)
            firebaseCall { profiles.save(uid, draft) }
            refresh()
        }
    }

    suspend fun createConnectedJourney(
        originArea: String,
        destinationArea: String,
        departure: String,
        seats: String,
    ): ConnectedJourneyCommandResult {
        val draft = when (val validated = ConnectedJourneyValidator.offer(originArea, destinationArea, departure, seats)) {
            is ValidationResult.Invalid -> return ConnectedJourneyCommandResult.InvalidInput(validated.userMessage)
            is ValidationResult.Valid -> validated.value
        }
        return journeyCommand { store, uid -> store.create(uid, draft) }
    }

    suspend fun requestConnectedSeat(journeyId: String): ConnectedJourneyCommandResult =
        journeyCommand { store, uid -> store.requestSeat(uid, journeyId) }

    suspend fun decideConnectedRequest(requestId: String, accept: Boolean): ConnectedJourneyCommandResult =
        journeyCommand { store, uid -> store.decide(uid, requestId, accept) }

    override suspend fun refresh() {
        val uid = auth.currentUserId
        if (uid == null) {
            clearSignedOut()
            return
        }
        val previousAppState = mutableAppState.value
        val previousSessionState = mutableSessionState.value
        // Keep an already rendered profile visible during refresh. Emitting Loading here
        // would dispose its Compose command scope and cancel this very refresh.
        if (previousAppState !is AsyncState.Data) {
            mutableAppState.value = AsyncState.Loading
        }
        try {
            val profile = requireNotNull(firebaseCall { profiles.load(uid) })
            val connectedJourneys = journeys?.let { firebaseCall { it.load(uid) } } ?: ConnectedJourneySnapshot()
            legacyCapabilities.refresh()
            val base = (legacyCapabilities.appState.value as? AsyncState.Data)?.value
                ?: error("Phase 9A fallback capabilities did not provide a snapshot")
            val displayName = profile.user.displayName
            val places = profile.savedPlaces
            val connectedSnapshot = base.copy(
                homeContent = base.homeContent.copy(
                    currentUser = base.homeContent.currentUser.copy(
                        firstName = displayName,
                        savedPlaces = places,
                    ),
                ),
                profileContent = base.profileContent.copy(
                    identity = base.profileContent.identity.copy(
                        firstName = displayName,
                        initials = initials(displayName),
                    ),
                    savedPlaces = places,
                    people = emptyList(),
                    completedJourneys = emptyList(),
                ),
            )
            connectedProfile = profile
            mutableJourneyState.value = connectedJourneys
            mutableSessionState.value = AccountSession.Authenticated(uid, displayName, isFictionalDemo = false)
            mutableAppState.value = AsyncState.Data(connectedSnapshot)
        } catch (cancelled: CancellationException) {
            mutableAppState.value = previousAppState
            mutableSessionState.value = previousSessionState
            throw cancelled
        } catch (failure: Throwable) {
            mutableAppState.value = if (previousAppState is AsyncState.Data) {
                previousAppState
            } else {
                AsyncState.Error(SAFE_ACCOUNT_ERROR)
            }
            if (previousAppState !is AsyncState.Data) {
                mutableSessionState.value = AccountSession.Failure(SAFE_ACCOUNT_ERROR)
            }
            throw failure
        }
    }

    override fun getProfileContent(): ProfileContent =
        (mutableAppState.value as? AsyncState.Data)?.value?.profileContent
            ?: legacyCapabilities.getProfileContent().copy(savedPlaces = emptyList(), people = emptyList(), completedJourneys = emptyList())

    override fun getSavedPlaces(): List<SavedPlace> = connectedProfile?.savedPlaces.orEmpty()

    override suspend fun savePlace(place: SavedPlace): SavePlaceResult {
        val current = connectedProfile ?: return SavePlaceResult.PrivateOrInvalidArea
        if (place.label !in setOf("Home", "Work")) return SavePlaceResult.PrivateOrInvalidArea
        val places = current.savedPlaces.associateBy { it.label }.toMutableMap().apply { put(place.label, place) }
        val home = places["Home"]?.area ?: return SavePlaceResult.PrivateOrInvalidArea
        val work = places["Work"]?.area ?: return SavePlaceResult.PrivateOrInvalidArea
        return when (updateConnectedProfile(current.user.displayName, home, work)) {
            AccountCommandResult.Success -> SavePlaceResult.Saved(place.copy(area = place.area.trim()))
            else -> SavePlaceResult.PrivateOrInvalidArea
        }
    }

    private suspend fun safely(action: suspend () -> Unit): AccountCommandResult = try {
        action()
        AccountCommandResult.Success
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Throwable) {
        if (auth.currentUserId == null) clearSignedOut()
        AccountCommandResult.Failure(SAFE_ACCOUNT_ERROR)
    }

    private suspend fun journeyCommand(
        action: suspend (ConnectedJourneyStore, String) -> Unit,
    ): ConnectedJourneyCommandResult {
        val store = journeys ?: return ConnectedJourneyCommandResult.Failure(SAFE_JOURNEY_ERROR)
        val uid = auth.currentUserId ?: return ConnectedJourneyCommandResult.Failure(SAFE_JOURNEY_ERROR)
        return try {
            firebaseCall { action(store, uid) }
            refresh()
            ConnectedJourneyCommandResult.Success
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            ConnectedJourneyCommandResult.Failure(SAFE_JOURNEY_ERROR)
        }
    }

    private suspend fun <T> firebaseCall(action: suspend () -> T): T = try {
        withTimeout(firebaseOperationTimeoutMillis) { action() }
    } catch (_: TimeoutCancellationException) {
        throw FirebaseOperationTimedOutException()
    }

    private fun clearSignedOut() {
        connectedProfile = null
        mutableJourneyState.value = ConnectedJourneySnapshot()
        mutableAppState.value = AsyncState.Empty
        mutableSessionState.value = AccountSession.SignedOut
    }

    private fun initials(name: String): String = name.split(Regex("\\s+"))
        .filter(String::isNotBlank)
        .take(2)
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
        .joinToString("")
        .ifEmpty { "R" }

    companion object {
        const val FIREBASE_OPERATION_TIMEOUT_MILLIS = 15_000L
        const val SAFE_ACCOUNT_ERROR = "Ryde couldn't complete that account request. Check the local emulators and try again."
        const val SAFE_JOURNEY_ERROR = "Ryde couldn't complete that journey request. Refresh and check the local emulators."
    }
}

private class FirebaseOperationTimedOutException : Exception()
