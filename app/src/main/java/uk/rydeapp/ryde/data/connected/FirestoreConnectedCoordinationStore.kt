package uk.rydeapp.ryde.data.connected

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.Source
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
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
                observePlan(tripId),
            ) { journey, messages, plan ->
                ConnectedConversationSnapshot(trip, journey, messages, plan)
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

    override suspend fun proposePlan(
        uid: String,
        tripId: String,
        plan: ValidatedConnectedJourneyPlan,
    ) {
        val tripRef = firestore.collection(CONFIRMED_TRIPS).document(tripId)
        val planRef = tripRef.collection(COORDINATION).document(DETAILS)
        firestore.runTransaction { transaction ->
            val trip = FirestoreJourneyMapper.confirmedTrip(
                tripId,
                transaction.get(tripRef).data.orEmpty(),
            ) ?: throw ConnectedCoordinationUnavailableException()
            val journey = FirestoreJourneyMapper.journey(
                trip.journeyId,
                transaction.get(firestore.collection(JOURNEYS).document(trip.journeyId)).data.orEmpty(),
            )
            if (!ConnectedJourneyLifecycle.canProposePlan(trip, journey, uid)) {
                throw ConnectedCoordinationUnavailableException()
            }
            val existingSnapshot = transaction.get(planRef)
            val existing = if (existingSnapshot.exists()) {
                FirestoreConnectedJourneyPlanMapper.plan(existingSnapshot.data.orEmpty())
                    ?: throw ConnectedCoordinationUnavailableException()
            } else null
            if (existing?.pickupDetails == plan.pickupDetails && existing.dropOffDetails == plan.dropOffDetails) {
                return@runTransaction
            }
            transaction.set(
                planRef,
                FirestoreConnectedJourneyPlanMapper.proposalData(plan, (existing?.revision ?: 0) + 1),
            )
        }.await()
    }

    override suspend fun agreePlan(uid: String, tripId: String, revision: Int) {
        val tripRef = firestore.collection(CONFIRMED_TRIPS).document(tripId)
        val planRef = tripRef.collection(COORDINATION).document(DETAILS)
        firestore.runTransaction { transaction ->
            val trip = FirestoreJourneyMapper.confirmedTrip(
                tripId,
                transaction.get(tripRef).data.orEmpty(),
            ) ?: throw ConnectedCoordinationUnavailableException()
            val journey = FirestoreJourneyMapper.journey(
                trip.journeyId,
                transaction.get(firestore.collection(JOURNEYS).document(trip.journeyId)).data.orEmpty(),
            )
            val plan = FirestoreConnectedJourneyPlanMapper.plan(transaction.get(planRef).data.orEmpty())
                ?: throw ConnectedCoordinationUnavailableException()
            if (plan.revision != revision) throw ConnectedCoordinationUnavailableException()
            if (plan.isAgreed) return@runTransaction
            if (!ConnectedJourneyLifecycle.canAgreePlan(trip, journey, plan, uid)) {
                throw ConnectedCoordinationUnavailableException()
            }
            transaction.update(planRef, mapOf(
                "acceptedRevision" to revision,
                "acceptedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
            ))
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
        val query = firestore.collection(CONFIRMED_TRIPS).document(tripId).collection(MESSAGES)
            .orderBy(SENT_AT, Query.Direction.ASCENDING)
            .limitToLast(MESSAGE_LIMIT)
        // Messages are immutable and append-only. Retaining committed results prevents a
        // cache-origin snapshot or a slower server catch-up from hiding a newer message.
        val committedMessages = linkedMapOf<String, ConnectedMessage>()
        val catchUpInFlight = AtomicBoolean(false)

        fun publish(snapshot: QuerySnapshot): Boolean {
            val observed = snapshot.toConnectedMessagesOrNull()
            if (observed == null) {
                close(ConnectedCoordinationUnavailableException())
                return false
            }
            val merged = synchronized(committedMessages) {
                observed.forEach { committedMessages[it.id] = it }
                orderedLatestConnectedMessages(committedMessages.values.toList())
            }
            trySend(merged)
            return true
        }

        fun catchUpFromServer() {
            if (!catchUpInFlight.compareAndSet(false, true)) return
            launch {
                try {
                    publish(query.get(Source.SERVER).await())
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Throwable) {
                    // The realtime listener remains authoritative and retries transport errors.
                    // A later cache-origin event will attempt another server catch-up.
                } finally {
                    catchUpInFlight.set(false)
                }
            }
        }

        val registration = query.addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener
                if (!publish(snapshot)) return@addSnapshotListener
                if (snapshot.metadata.isFromCache) catchUpFromServer()
            }
        awaitClose { registration.remove() }
    }

    private fun observePlan(tripId: String): Flow<ConnectedJourneyPlan?> = callbackFlow {
        val registration = firestore.collection(CONFIRMED_TRIPS).document(tripId)
            .collection(COORDINATION).document(DETAILS)
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
                val plan = FirestoreConnectedJourneyPlanMapper.plan(snapshot.data.orEmpty())
                if (plan == null) close(ConnectedCoordinationUnavailableException()) else trySend(plan)
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
        const val COORDINATION = "coordination"
        const val DETAILS = "details"
        const val SENT_AT = "sentAt"
        const val MESSAGE_LIMIT = 100L
    }
}
