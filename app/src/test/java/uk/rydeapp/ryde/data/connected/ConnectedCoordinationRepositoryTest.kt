package uk.rydeapp.ryde.data.connected

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectedCoordinationRepositoryTest {
    private val journey = ConnectedJourney("j", "driver", "Derby", "Nottingham", 1_000, 2, 1)
    private val trip = ConnectedConfirmedTrip(
        "j_rider", "j", "j_rider", "driver", "rider", "Derby", "Nottingham", 1_000,
        ConnectedTripStatus.CONFIRMED,
    )

    @Test
    fun `realtime lifecycle changes an open conversation to read only`() = runBlocking {
        val store = FakeCoordinationStore()
        val repository = repository(FakeAuth("rider"), store)
        val states = mutableListOf<ConnectedConversationState>()
        val job = launch { repository.observeConnectedConversation(trip.id).take(3).toList(states) }
        yield()
        store.events.emit(ConnectedConversationSnapshot(trip, journey, emptyList()))
        store.events.emit(ConnectedConversationSnapshot(
            trip.copy(status = ConnectedTripStatus.CANCELLED_BY_RIDER, cancelledAtEpochMillis = 2),
            journey,
            emptyList(),
        ))
        job.join()

        val active = (states[1] as ConnectedConversationState.Data).conversation
        val cancelled = (states[2] as ConnectedConversationState.Data).conversation
        assertTrue(active.canSendMessages)
        assertFalse(cancelled.canSendMessages)
        assertEquals(ConnectedConversationReadOnlyReason.CANCELLED_BY_RIDER, cancelled.readOnlyReason)
    }

    @Test
    fun `completed journey retains conversation history as read only`() = runBlocking {
        val completed = journey.copy(status = ConnectedJourneyStatus.COMPLETED, completedAtEpochMillis = 2)
        val states = repository(FakeAuth("rider"), object : FakeCoordinationStore() {
            override fun observeConversation(uid: String, tripId: String) = flow {
                emit(ConnectedConversationSnapshot(trip, completed, listOf(ConnectedMessage("m", "driver", "Thanks", 1))))
            }
        }).observeConnectedConversation(trip.id).toList()
        val conversation = (states.last() as ConnectedConversationState.Data).conversation
        assertFalse(conversation.canSendMessages)
        assertEquals(ConnectedConversationReadOnlyReason.COMPLETED, conversation.readOnlyReason)
        assertEquals("Thanks", conversation.messages.single().body)
    }

    @Test
    fun `later listener failure retains only this streams rendered history and disables send`() = runBlocking {
        val message = ConnectedMessage("m", "driver", "Earlier", 10)
        val store = object : FakeCoordinationStore() {
            override fun observeConversation(uid: String, tripId: String): Flow<ConnectedConversationSnapshot> = flow {
                emit(ConnectedConversationSnapshot(trip, journey, listOf(message)))
                throw IllegalStateException("secret backend path")
            }
        }
        val states = repository(FakeAuth("rider"), store).observeConnectedConversation(trip.id).toList()
        val error = states.last() as ConnectedConversationState.Error
        assertEquals(listOf(message), error.previousConversation?.messages)
        assertFalse(error.userMessage.contains("secret"))
    }

    @Test
    fun `flow cancellation removes the scoped store observation`() = runBlocking {
        val store = FakeCoordinationStore()
        val job = launch { repository(FakeAuth("rider"), store).observeConnectedConversation(trip.id).collect {} }
        yield()
        assertEquals(1, store.activeObservers)
        job.cancelAndJoin()
        assertEquals(0, store.activeObservers)
    }

    @Test
    fun `account switch fails closed and signed out send is read only`() = runBlocking {
        val auth = FakeAuth("rider")
        val store = FakeCoordinationStore()
        val repository = repository(auth, store)
        val states = mutableListOf<ConnectedConversationState>()
        val job = launch { repository.observeConnectedConversation(trip.id).take(3).toList(states) }
        yield()
        store.events.emit(ConnectedConversationSnapshot(trip, journey, emptyList()))
        auth.uid = "other"
        store.events.emit(ConnectedConversationSnapshot(trip, journey, emptyList()))
        job.join()
        assertTrue(states.last() is ConnectedConversationState.Error)

        auth.uid = null
        assertTrue(repository.sendConnectedMessage(trip.id, "m", "Hello") is ConnectedMessageCommandResult.ReadOnly)
        assertEquals(0, store.sendCalls)
    }

    @Test
    fun `same message id and content is an idempotent retry while conflicts fail safely`() = runBlocking {
        val store = FakeCoordinationStore()
        val repository = repository(FakeAuth("rider"), store)
        assertTrue(repository.sendConnectedMessage(trip.id, "same-id", "Hello") is ConnectedMessageCommandResult.Success)
        assertTrue(repository.sendConnectedMessage(trip.id, "same-id", "Hello") is ConnectedMessageCommandResult.Success)
        assertEquals(1, store.persisted.size)
        assertTrue(repository.sendConnectedMessage(trip.id, "same-id", "Different") is ConnectedMessageCommandResult.ReadOnly)
        assertEquals(1, store.persisted.size)
    }

    private fun repository(auth: FakeAuth, store: ConnectedCoordinationStore) =
        ConnectedRydeRepository(auth, EmptyProfiles, coordination = store)

    private class FakeAuth(var uid: String?) : ConnectedAuthGateway {
        override val currentUserId: String? get() = uid
        override suspend fun register(email: String, password: String): String = error("unused")
        override suspend fun signIn(email: String, password: String): String = error("unused")
        override suspend fun signOut() { uid = null }
    }

    private object EmptyProfiles : ConnectedProfileStore {
        override suspend fun create(profile: ConnectedUserProfile) = Unit
        override suspend fun load(uid: String): ConnectedProfile? = null
        override suspend fun save(uid: String, draft: ConnectedProfileDraft) = Unit
    }

    private open class FakeCoordinationStore : ConnectedCoordinationStore {
        val events = MutableSharedFlow<ConnectedConversationSnapshot>(extraBufferCapacity = 4)
        var activeObservers = 0
        var sendCalls = 0
        val persisted = linkedMapOf<String, Pair<String, String>>()

        override fun observeConversation(uid: String, tripId: String): Flow<ConnectedConversationSnapshot> = flow {
            activeObservers++
            try {
                events.collect { emit(it) }
            } finally {
                activeObservers--
            }
        }

        override suspend fun sendMessage(uid: String, tripId: String, messageId: String, body: String) {
            sendCalls++
            val existing = persisted[messageId]
            if (existing == null) persisted[messageId] = uid to body
            else if (existing != uid to body) throw ConnectedCoordinationUnavailableException()
            else sendCalls--
        }
    }
}
