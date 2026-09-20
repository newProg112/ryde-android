package uk.rydeapp.ryde.data.connected

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.tasks.await

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class FirestoreConnectedCoordinationStore(
    private val firestore: FirebaseFirestore,
) : ConnectedCoordinationStore {
    override fun observeConversation(uid: String, tripId: String): Flow<ConnectedConversationSnapshot> =
        observeTrip(tripId).flatMapLatest { trip ->
            if (!ConnectedJourneyLifecycle.canReadMessages(trip, uid)) {
                throw ConnectedCoordinationUnavailableException()
            }
            combine(
                observeJourney(trip.journeyId),
                observeMessages(tripId),
            ) { journey, messages ->
                ConnectedConversationSnapshot(trip, journey, messages)
            }
        }

    override suspend fun sendMessage(uid: String, tripId: String, messageId: String, body: String) {
        if (!ConnectedMessagePolicy.validMessageId(messageId)) throw ConnectedCoordinationUnavailableException()
        val tripRef = firestore.collection(CONFIRMED_TRIPS).document(tripId)
        val messageRef = tripRef.collection(MESSAGES).document(messageId)
        firestore.runTransaction { transaction ->
            val existing = transaction.get(messageRef)
            if (existing.exists()) {
                val message = FirestoreConnectedMessageMapper.message(existing.id, existing.data.orEmpty())
                    ?: throw ConnectedCoordinationUnavailableException()
                if (message.senderUid == uid && message.body == body) return@runTransaction
                throw ConnectedCoordinationUnavailableException()
            }

            val trip = FirestoreJourneyMapper.confirmedTrip(
                tripId,
                transaction.get(tripRef).data.orEmpty(),
            ) ?: throw ConnectedCoordinationUnavailableException()
            val journey = FirestoreJourneyMapper.journey(
                trip.journeyId,
                transaction.get(firestore.collection(JOURNEYS).document(trip.journeyId)).data.orEmpty(),
            )
            if (!ConnectedJourneyLifecycle.canSendMessages(trip, journey, uid)) {
                throw ConnectedCoordinationUnavailableException()
            }
            transaction.set(messageRef, FirestoreConnectedMessageMapper.messageData(uid, body))
        }.await()
    }

    private fun observeTrip(tripId: String): Flow<ConnectedConfirmedTrip> = callbackFlow {
        val registration = firestore.collection(CONFIRMED_TRIPS).document(tripId)
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot == null || snapshot.metadata.hasPendingWrites()) return@addSnapshotListener
                val trip = snapshot.takeIf { it.exists() }
                    ?.let { FirestoreJourneyMapper.confirmedTrip(it.id, it.data.orEmpty()) }
                if (trip == null) close(ConnectedCoordinationUnavailableException()) else trySend(trip)
            }
        awaitClose { registration.remove() }
    }

    private fun observeJourney(journeyId: String): Flow<ConnectedJourney?> = callbackFlow {
        val registration = firestore.collection(JOURNEYS).document(journeyId)
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot == null || snapshot.metadata.hasPendingWrites()) return@addSnapshotListener
                if (!snapshot.exists()) {
                    trySend(null)
                    return@addSnapshotListener
                }
                val journey = FirestoreJourneyMapper.journey(snapshot.id, snapshot.data.orEmpty())
                // A missing or malformed linked journey makes sending read-only,
                // but it must not hide participant-authorized retained history.
                trySend(journey)
            }
        awaitClose { registration.remove() }
    }

    private fun observeMessages(tripId: String): Flow<List<ConnectedMessage>> = callbackFlow {
        val registration = firestore.collection(CONFIRMED_TRIPS).document(tripId).collection(MESSAGES)
            .orderBy(SENT_AT, Query.Direction.ASCENDING)
            .limitToLast(MESSAGE_LIMIT)
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener
                val messages = snapshot.toConnectedMessagesOrNull()
                if (messages == null) close(ConnectedCoordinationUnavailableException()) else trySend(messages)
            }
        awaitClose { registration.remove() }
    }

    private fun QuerySnapshot.toConnectedMessagesOrNull(): List<ConnectedMessage>? {
        val committed = documents.filterNot { it.metadata.hasPendingWrites() }
        val messages = committed.map { document ->
            FirestoreConnectedMessageMapper.message(document.id, document.data.orEmpty()) ?: return null
        }
        return orderedLatestConnectedMessages(messages)
    }

    private companion object {
        const val CONFIRMED_TRIPS = "confirmedTrips"
        const val JOURNEYS = "journeys"
        const val MESSAGES = "messages"
        const val SENT_AT = "sentAt"
        const val MESSAGE_LIMIT = 100L
    }
}
