package uk.rydeapp.ryde.data

import kotlinx.coroutines.runBlocking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.rydeapp.ryde.domain.model.CompleteJourneyResult
import uk.rydeapp.ryde.domain.model.ConversationId
import uk.rydeapp.ryde.domain.model.CreateOfferedJourneyResult
import uk.rydeapp.ryde.domain.model.DecideIncomingRequestResult
import uk.rydeapp.ryde.domain.model.GetConversationResult
import uk.rydeapp.ryde.domain.model.IncomingRequestDecision
import uk.rydeapp.ryde.domain.model.JourneyLifecyclePolicy
import uk.rydeapp.ryde.domain.model.JourneyLifecycleStatus
import uk.rydeapp.ryde.domain.model.JourneyStatusUpdateResult
import uk.rydeapp.ryde.domain.model.MessageRejectionReason
import uk.rydeapp.ryde.domain.model.MessagingUnavailableReason
import uk.rydeapp.ryde.domain.model.PersonalSafetyStatus
import uk.rydeapp.ryde.domain.model.SendMessageResult

class FakeRydeRepositoryCoordinationTest {
    private suspend fun confirmedTrip(
        repository: FakeRydeRepository = FakeRydeRepository(),
    ): Pair<FakeRydeRepository, String> {
        val offer = repository.createOfferedJourney(repository.getOfferRideContent().defaultCriteria)
            as CreateOfferedJourneyResult.Created
        val request = repository.getIncomingSeatRequestForJourney(offer.journey.id)!!
        val result = repository.decideIncomingSeatRequest(request.id, IncomingRequestDecision.ACCEPT)
            as DecideIncomingRequestResult.Decided
        return repository to result.confirmedTrip!!.id
    }

    private suspend fun progressToUnderway(repository: FakeRydeRepository, tripId: String) {
        listOf(
            JourneyLifecycleStatus.DRIVER_EN_ROUTE,
            JourneyLifecycleStatus.READY_AT_PICKUP,
            JourneyLifecycleStatus.JOURNEY_UNDERWAY,
        ).forEach { status ->
            assertTrue(repository.updateConfirmedJourneyStatus(tripId, status) is JourneyStatusUpdateResult.Updated)
        }
    }

    @Test
    fun `messaging is unavailable without a confirmed trip`() = runBlocking {
        val repository = FakeRydeRepository()

        val result = repository.getConversationForConfirmedTrip("missing")

        assertEquals(
            MessagingUnavailableReason.NO_CONFIRMED_TRIP,
            (result as GetConversationResult.Unavailable).reason,
        )
        assertEquals(
            MessageRejectionReason.CONVERSATION_NOT_FOUND,
            (repository.sendMessage(ConversationId("missing"), "Hello") as SendMessageResult.Rejected).reason,
        )
    }

    @Test
    fun `blocked and reported participants cannot message`() = runBlocking {
        listOf(PersonalSafetyStatus.BLOCKED, PersonalSafetyStatus.REPORTED).forEach { safetyStatus ->
            val (repository, tripId) = confirmedTrip(FakeRydeRepository(safetyStatus))

            val conversation = repository.getConversationForConfirmedTrip(tripId)
            val send = repository.sendMessage(ConversationId("conversation-$tripId"), "Hello Jamie")

            assertEquals(
                MessagingUnavailableReason.PARTICIPANT_BLOCKED_OR_REPORTED,
                (conversation as GetConversationResult.Unavailable).reason,
            )
            assertEquals(
                MessageRejectionReason.PARTICIPANT_BLOCKED_OR_REPORTED,
                (send as SendMessageResult.Rejected).reason,
            )
        }
    }

    @Test
    fun `cancelled and completed trips reject new messages but retain history`() = runBlocking {
        val (cancelledRepository, cancelledTripId) = confirmedTrip()
        val cancelledConversation = (
            cancelledRepository.getConversationForConfirmedTrip(cancelledTripId) as GetConversationResult.Available
            ).conversation
        cancelledRepository.updateConfirmedJourneyStatus(cancelledTripId, JourneyLifecycleStatus.CANCELLED)

        assertFalse(
            (cancelledRepository.getConversationForConfirmedTrip(cancelledTripId) as GetConversationResult.Available)
                .conversation.canSendMessages,
        )
        assertEquals(
            MessageRejectionReason.JOURNEY_CANCELLED,
            (cancelledRepository.sendMessage(cancelledConversation.id, "Hello") as SendMessageResult.Rejected).reason,
        )

        val (completedRepository, completedTripId) = confirmedTrip()
        val completedConversation = (
            completedRepository.getConversationForConfirmedTrip(completedTripId) as GetConversationResult.Available
            ).conversation
        progressToUnderway(completedRepository, completedTripId)
        completedRepository.completeJourney(completedTripId)

        assertFalse(
            (completedRepository.getConversationForConfirmedTrip(completedTripId) as GetConversationResult.Available)
                .conversation.canSendMessages,
        )
        assertEquals(
            MessageRejectionReason.JOURNEY_COMPLETED,
            (completedRepository.sendMessage(completedConversation.id, "Hello") as SendMessageResult.Rejected).reason,
        )
    }

    @Test
    fun `opening a conversation clears its unread message count`() = runBlocking {
        val (repository, tripId) = confirmedTrip()
        val conversation = (repository.getConversationForConfirmedTrip(tripId) as GetConversationResult.Available).conversation
        assertEquals(1, repository.getCoordinationUnreadCounts().messages)

        repository.markConversationRead(conversation.id)

        assertEquals(0, repository.getCoordinationUnreadCounts().messages)
        assertTrue(
            (repository.getConversationForConfirmedTrip(tripId) as GetConversationResult.Available)
                .conversation.messages.all { it.isRead },
        )
        assertTrue(
            repository.getCoordinationActivityItems()
                .filter { it.conversationId == conversation.id }
                .all { it.isRead },
        )
    }

    @Test
    fun `opening an activity marks that in-app item read`() = runBlocking {
        val (repository, _) = confirmedTrip()
        val item = repository.getCoordinationActivityItems().first { !it.isRead }
        val before = repository.getCoordinationUnreadCounts().activity

        assertTrue(repository.markCoordinationActivityRead(item.id))

        assertEquals(before - 1, repository.getCoordinationUnreadCounts().activity)
        assertTrue(repository.getCoordinationActivityItems().first { it.id == item.id }.isRead)
    }

    @Test
    fun `only valid lifecycle transitions succeed`() = runBlocking {
        val (repository, tripId) = confirmedTrip()

        assertFalse(
            JourneyLifecyclePolicy.canTransition(
                JourneyLifecycleStatus.CONFIRMED,
                JourneyLifecycleStatus.JOURNEY_UNDERWAY,
            ),
        )
        assertTrue(
            repository.updateConfirmedJourneyStatus(tripId, JourneyLifecycleStatus.JOURNEY_UNDERWAY)
                is JourneyStatusUpdateResult.Rejected,
        )
        progressToUnderway(repository, tripId)
        assertTrue(
            repository.updateConfirmedJourneyStatus(tripId, JourneyLifecycleStatus.DRIVER_EN_ROUTE)
                is JourneyStatusUpdateResult.Rejected,
        )
    }

    @Test
    fun `completion archives once and feeds trust eligibility`() = runBlocking {
        val (repository, tripId) = confirmedTrip()
        progressToUnderway(repository, tripId)

        val first = repository.completeJourney(tripId)
        val second = repository.completeJourney(tripId)

        assertTrue(first is CompleteJourneyResult.Completed)
        assertTrue(second is CompleteJourneyResult.AlreadyCompleted)
        assertTrue(repository.getConfirmedSharedTrips().isEmpty())
        assertEquals(1, repository.getCompletedJourneyHistory().count { it.id == tripId })
        assertEquals(2, repository.getCompletedJourneyHistory().size)
        val jamie = repository.getProfileContent().people.first { it.id == "jamie-demo" }
        assertTrue(tripId in jamie.completedTripIds)
        assertTrue(jamie.isEligibleForTrust)
    }

    @Test
    fun `coordination content is privacy safe and never claims live sharing`() = runBlocking {
        val (repository, tripId) = confirmedTrip()
        val conversation = (repository.getConversationForConfirmedTrip(tripId) as GetConversationResult.Available).conversation
        val exposedText = buildString {
            conversation.messages.forEach { append(it.body).append(' ') }
            repository.getCoordinationActivityItems().forEach { append(it.title).append(' ').append(it.body).append(' ') }
        }.lowercase()

        assertFalse("home address" in exposedText)
        assertFalse("live location" in exposedText)
        assertFalse("location sharing" in exposedText)
        assertTrue(conversation.messages.any { "pickup" in it.body.lowercase() })
        assertEquals(
            MessageRejectionReason.PRIVATE_OR_LIVE_LOCATION,
            (repository.sendMessage(conversation.id, "Meet me at 14 Example Street") as SendMessageResult.Rejected).reason,
        )
        assertTrue(
            repository.sendMessage(conversation.id, "Meet me at Sutton Bus Station") is SendMessageResult.Sent,
        )
    }
}
