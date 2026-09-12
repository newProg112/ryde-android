package uk.rydeapp.ryde.app

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.rydeapp.ryde.data.AsyncState
import uk.rydeapp.ryde.data.AccountSession
import uk.rydeapp.ryde.data.AppMode
import uk.rydeapp.ryde.data.FakeRydeRepository
import uk.rydeapp.ryde.data.RydeRepository
import uk.rydeapp.ryde.data.RydeSnapshot
import java.util.ArrayDeque
import kotlin.coroutines.CoroutineContext

class RydeAppStateHolderTest {
    @Test
    fun `loading transitions to data`() {
        val dispatcher = QueuedDispatcher()
        val repository = ControllableRepository()
        val holder = RydeAppStateHolder(
            repository,
            AppMode.LOCAL_DEMO,
            CoroutineScope(SupervisorJob() + dispatcher),
        )

        assertEquals(RydeAppUiState.Loading, holder.uiState.value)
        dispatcher.runAll()
        assertTrue(holder.uiState.value is RydeAppUiState.Ready)
    }

    @Test
    fun `representative command updates observable immutable state`() = runBlocking {
        val repository = ControllableRepository()
        val holder = RydeAppStateHolder(
            repository,
            AppMode.LOCAL_DEMO,
            CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        )

        holder.createSeatRequest(
            "alex-mansfield-nottingham",
            repository.getFindRideContent().defaultCriteria,
        )

        val ready = holder.uiState.value as RydeAppUiState.Ready
        assertEquals(1, ready.snapshot.seatRequests.size)
        repository.refresh()
        assertEquals(1, (holder.uiState.value as RydeAppUiState.Ready).snapshot.seatRequests.size)
    }

    @Test
    fun `repository failures become safe error and retry starts a fresh load`() {
        val repository = ControllableRepository(failuresRemaining = 1)
        val holder = RydeAppStateHolder(
            repository,
            AppMode.CONNECTED,
            CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        )

        val error = holder.uiState.value as RydeAppUiState.Error
        assertEquals(RydeAppStateHolder.SAFE_LOAD_ERROR, error.userMessage)
        assertFalse(error.userMessage.contains("secret-token"))

        holder.retry()

        assertEquals(2, repository.refreshCount)
        assertTrue(holder.uiState.value is RydeAppUiState.Ready)
    }

    @Test
    fun `signed out session never renders application data as ready`() {
        val repository = ControllableRepository(accountSession = AccountSession.SignedOut)
        val holder = RydeAppStateHolder(
            repository,
            AppMode.CONNECTED,
            CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        )

        assertTrue(repository.appState.value is AsyncState.Data)
        assertEquals(RydeAppUiState.SignedOut, holder.uiState.value)
        assertFalse(holder.uiState.value is RydeAppUiState.Ready)
    }

    @Test
    fun `command failure becomes safe error without escaping into UI caller`() = runBlocking {
        val repository = ControllableRepository(commandFailuresRemaining = 1)
        val holder = RydeAppStateHolder(
            repository,
            AppMode.CONNECTED,
            CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        )

        val result = holder.createSeatRequest(
            "alex-mansfield-nottingham",
            repository.getFindRideContent().defaultCriteria,
        )

        assertNull(result)
        val error = holder.uiState.value as RydeAppUiState.Error
        assertEquals(RydeAppStateHolder.SAFE_LOAD_ERROR, error.userMessage)
        assertFalse(error.userMessage.contains("secret-command-detail"))
    }

    @Test
    fun `command cancellation is propagated`() = runBlocking {
        val repository = ControllableRepository(cancelCommands = true)
        val holder = RydeAppStateHolder(
            repository,
            AppMode.CONNECTED,
            CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        )

        try {
            holder.createSeatRequest(
                "alex-mansfield-nottingham",
                repository.getFindRideContent().defaultCriteria,
            )
            org.junit.Assert.fail("Cancellation should propagate")
        } catch (_: CancellationException) {
            assertTrue(holder.uiState.value is RydeAppUiState.Ready)
        }
    }

    private class ControllableRepository(
        private var failuresRemaining: Int = 0,
        private var commandFailuresRemaining: Int = 0,
        private val cancelCommands: Boolean = false,
        accountSession: AccountSession? = null,
        private val fake: FakeRydeRepository = FakeRydeRepository(),
    ) : RydeRepository by fake {
        private val mutableState = MutableStateFlow<AsyncState<RydeSnapshot>>(AsyncState.Loading)
        override val appState: StateFlow<AsyncState<RydeSnapshot>> = mutableState
        private val mutableSession = MutableStateFlow(accountSession ?: fake.sessionState.value)
        override val sessionState: StateFlow<AccountSession> = mutableSession
        var refreshCount = 0
            private set

        override fun getFindRideContent() = fake.getFindRideContent()
        override suspend fun createSeatRequest(
            matchId: String,
            criteria: uk.rydeapp.ryde.domain.model.FindRideCriteria,
        ): uk.rydeapp.ryde.domain.model.CreateSeatRequestResult {
            if (cancelCommands) throw CancellationException("expected cancellation")
            if (commandFailuresRemaining > 0) {
                commandFailuresRemaining -= 1
                throw IllegalStateException("secret-command-detail")
            }
            return fake.createSeatRequest(matchId, criteria)
        }

        override suspend fun refresh() {
            refreshCount += 1
            if (failuresRemaining > 0) {
                failuresRemaining -= 1
                throw IllegalStateException("secret-token backend detail")
            }
            fake.refresh()
            mutableState.value = fake.appState.value
        }
    }

    private class QueuedDispatcher : CoroutineDispatcher() {
        private val tasks = ArrayDeque<Runnable>()
        override fun dispatch(context: CoroutineContext, block: Runnable) {
            tasks.addLast(block)
        }
        fun runAll() {
            while (tasks.isNotEmpty()) tasks.removeFirst().run()
        }
    }
}
