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
