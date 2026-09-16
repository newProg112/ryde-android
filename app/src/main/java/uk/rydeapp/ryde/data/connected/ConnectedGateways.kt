package uk.rydeapp.ryde.data.connected

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.tasks.await
import uk.rydeapp.ryde.domain.model.SavedPlace

interface ConnectedAuthGateway {
    val currentUserId: String?
    suspend fun register(email: String, password: String): String
    suspend fun signIn(email: String, password: String): String
    suspend fun signOut()
}

interface ConnectedProfileStore {
    suspend fun create(profile: ConnectedUserProfile)
    suspend fun load(uid: String): ConnectedProfile?
    suspend fun save(uid: String, draft: ConnectedProfileDraft)
}

interface ConnectedJourneyStore {
    suspend fun load(uid: String): ConnectedJourneySnapshot
    suspend fun create(uid: String, draft: ConnectedJourneyDraft)
    suspend fun requestSeat(uid: String, journeyId: String)
    suspend fun cancelRequest(uid: String, requestId: String)
    suspend fun decide(uid: String, requestId: String, accept: Boolean)
}

class FirebaseAuthGateway(private val auth: FirebaseAuth) : ConnectedAuthGateway {
    override val currentUserId: String? get() = auth.currentUser?.uid

    override suspend fun register(email: String, password: String): String =
        checkNotNull(auth.createUserWithEmailAndPassword(email, password).await().user?.uid)

    override suspend fun signIn(email: String, password: String): String =
        checkNotNull(auth.signInWithEmailAndPassword(email, password).await().user?.uid)

    override suspend fun signOut() = auth.signOut()
}

class FirestoreConnectedProfileStore(private val firestore: FirebaseFirestore) : ConnectedProfileStore {
    override suspend fun create(profile: ConnectedUserProfile) {
        userDocument(profile.uid).set(FirestoreProfileMapper.userData(profile)).await()
    }

    override suspend fun load(uid: String): ConnectedProfile? {
        val userSnapshot = userDocument(uid).get(Source.SERVER).await()
        if (!userSnapshot.exists()) return null
        val user = FirestoreProfileMapper.user(uid, userSnapshot.data.orEmpty()) ?: return null
        val places = userDocument(uid).collection(SAVED_PLACES).get(Source.SERVER).await()
            .documents.mapNotNull { FirestoreProfileMapper.place(it.id, uid, it.data.orEmpty()) }
            .sortedBy { it.label }
        return ConnectedProfile(user, places)
    }

    override suspend fun save(uid: String, draft: ConnectedProfileDraft) {
        val user = ConnectedUserProfile(uid, draft.displayName)
        val home = SavedPlace("Home", draft.homeArea)
        val work = SavedPlace("Work", draft.workArea)
        firestore.runBatch { batch ->
            batch.set(userDocument(uid), FirestoreProfileMapper.userData(user))
            batch.set(userDocument(uid).collection(SAVED_PLACES).document("home"), FirestoreProfileMapper.placeData(uid, home))
            batch.set(userDocument(uid).collection(SAVED_PLACES).document("work"), FirestoreProfileMapper.placeData(uid, work))
        }.await()
    }

    private fun userDocument(uid: String) = firestore.collection(USERS).document(uid)

    private companion object {
        const val USERS = "users"
        const val SAVED_PLACES = "savedPlaces"
    }
}

class FirestoreConnectedJourneyStore(private val firestore: FirebaseFirestore) : ConnectedJourneyStore {
    override suspend fun load(uid: String): ConnectedJourneySnapshot {
        val journeys = firestore.collection(JOURNEYS).get(Source.SERVER).await().documents
            .mapNotNull { FirestoreJourneyMapper.journey(it.id, it.data.orEmpty()) }
            .sortedBy { it.departureEpochMillis }
        val asDriver = firestore.collection(REQUESTS).whereEqualTo("driverUid", uid).get(Source.SERVER).await()
        val asRider = firestore.collection(REQUESTS).whereEqualTo("riderUid", uid).get(Source.SERVER).await()
        val requests = (asDriver.documents + asRider.documents)
            .distinctBy { it.id }
            .mapNotNull { FirestoreJourneyMapper.request(it.id, it.data.orEmpty()) }
        val tripsAsDriver = firestore.collection(CONFIRMED_TRIPS)
            .whereEqualTo("driverUid", uid).get(Source.SERVER).await()
        val tripsAsRider = firestore.collection(CONFIRMED_TRIPS)
            .whereEqualTo("riderUid", uid).get(Source.SERVER).await()
        val confirmedTrips = (tripsAsDriver.documents + tripsAsRider.documents)
            .distinctBy { it.id }
            .mapNotNull { FirestoreJourneyMapper.confirmedTrip(it.id, it.data.orEmpty()) }
            .sortedBy { it.departureEpochMillis }
        return ConnectedJourneySnapshot(journeys, requests, confirmedTrips)
    }

    override suspend fun create(uid: String, draft: ConnectedJourneyDraft) {
        val journeyRef = firestore.collection(JOURNEYS).document()
        val guardRef = firestore.collection(ACCEPTANCE_GUARDS).document(journeyRef.id)
        firestore.runBatch { batch ->
            batch.set(journeyRef, FirestoreJourneyMapper.journeyData(uid, draft))
            batch.set(guardRef, FirestoreJourneyMapper.initialAcceptanceGuardData(uid))
        }.await()
    }

    override suspend fun requestSeat(uid: String, journeyId: String) {
        val journeyRef = firestore.collection(JOURNEYS).document(journeyId)
        val requestRef = firestore.collection(REQUESTS).document("${journeyId}_$uid")
        firestore.runTransaction { transaction ->
            val journey = FirestoreJourneyMapper.journey(journeyId, transaction.get(journeyRef).data.orEmpty())
                ?: error("Journey unavailable")
            check(
                journey.driverUid != uid &&
                    journey.seatsRemaining > 0 &&
                    journey.departureEpochMillis > System.currentTimeMillis(),
            )
            transaction.set(requestRef, FirestoreJourneyMapper.requestData(journey, uid))
        }.await()
    }

    override suspend fun cancelRequest(uid: String, requestId: String) {
        val requestRef = firestore.collection(REQUESTS).document(requestId)
        firestore.runTransaction { transaction ->
            val request = FirestoreJourneyMapper.request(requestId, transaction.get(requestRef).data.orEmpty())
                ?: error("Request unavailable")
            check(request.riderUid == uid && request.status == ConnectedRequestStatus.PENDING)
            transaction.update(requestRef, "status", ConnectedRequestStatus.CANCELLED.name)
        }.await()
    }

    override suspend fun decide(uid: String, requestId: String, accept: Boolean) {
        val requestRef = firestore.collection(REQUESTS).document(requestId)
        firestore.runTransaction { transaction ->
            val request = FirestoreJourneyMapper.request(requestId, transaction.get(requestRef).data.orEmpty())
                ?: error("Request unavailable")
            check(request.driverUid == uid && request.status == ConnectedRequestStatus.PENDING)
            if (accept) {
                val journeyRef = firestore.collection(JOURNEYS).document(request.journeyId)
                val journey = FirestoreJourneyMapper.journey(request.journeyId, transaction.get(journeyRef).data.orEmpty())
                    ?: error("Journey unavailable")
                check(
                    journey.driverUid == uid &&
                        journey.seatsRemaining > 0 &&
                        journey.departureEpochMillis > System.currentTimeMillis(),
                )
                val guardRef = firestore.collection(ACCEPTANCE_GUARDS).document(request.journeyId)
                val guard = FirestoreJourneyMapper.acceptanceGuard(transaction.get(guardRef).data.orEmpty())
                    ?: error("Journey acceptance guard unavailable")
                val tripRef = firestore.collection(CONFIRMED_TRIPS).document(requestId)
                check(guard.driverUid == uid)
                check(guard.acceptanceCount == journey.seatCapacity - journey.seatsRemaining)
                transaction.update(journeyRef, "seatsRemaining", journey.seatsRemaining - 1)
                transaction.update(
                    guardRef,
                    mapOf(
                        "acceptanceCount" to guard.acceptanceCount + 1,
                        "lastAcceptedRequestId" to requestId,
                    ),
                )
                transaction.set(tripRef, FirestoreJourneyMapper.confirmedTripData(journey, request))
            }
            transaction.update(requestRef, "status", if (accept) "ACCEPTED" else "DECLINED")
        }.await()
    }

    private companion object {
        const val JOURNEYS = "journeys"
        const val REQUESTS = "seatRequests"
        const val ACCEPTANCE_GUARDS = "journeyAcceptanceGuards"
        const val CONFIRMED_TRIPS = "confirmedTrips"
    }
}
