package uk.rydeapp.ryde.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import uk.rydeapp.ryde.domain.model.CreateOfferedJourneyResult
import uk.rydeapp.ryde.domain.model.DecideIncomingRequestResult
import uk.rydeapp.ryde.domain.model.GetConversationResult
import uk.rydeapp.ryde.domain.model.IncomingRequestDecision

class BackendReadinessContractTest {
    @Test
    fun `fake repository satisfies aggregate and capability contracts`() {
        val aggregate: RydeRepository = FakeRydeRepository()
        assertTrue(aggregate is HomeContentRepository)
        assertTrue(aggregate is AccountSessionRepository)
        assertTrue(aggregate is AccountAccessRepository)
        assertTrue(aggregate is ProfileRepository)
        assertTrue(aggregate is RideDiscoveryRepository)
        assertTrue(aggregate is OfferedJourneyRepository)
        assertTrue(aggregate is TripRepository)
        assertTrue(aggregate is CircleRepository)
        assertTrue(aggregate is CoordinationRepository)
        assertTrue(aggregate is ObservableRydeRepository)
    }

    @Test
    fun `local demo resolves to an explicitly fictional session`() {
        val repository = RydeAppComposition.repository(AppMode.LOCAL_DEMO)
        val session = repository.sessionState.value as AccountSession.Authenticated

        assertTrue(session.isFictionalDemo)
        assertEquals("fictional-sam-demo", session.accountId)
    }

    @Test
    fun `connected mode cannot silently fall back to fictional data`() {
        try {
            RydeAppComposition.repository(AppMode.CONNECTED)
            fail("CONNECTED mode should require a configured repository")
        } catch (expected: IllegalArgumentException) {
            assertFalse(expected.message.orEmpty().contains("Firebase"))
        }

        val connected = FakeRydeRepository()
        assertSame(connected, RydeAppComposition.repository(AppMode.CONNECTED, connected))
    }

    @Test
    fun `refresh only republishes snapshots without duplicating domain state`() = runBlocking {
        val repository = FakeRydeRepository()
        val criteria = repository.getFindRideContent().defaultCriteria
        repository.createSeatRequest("alex-mansfield-nottingham", criteria)
        val offer = repository.createOfferedJourney(repository.getOfferRideContent().defaultCriteria)
            as CreateOfferedJourneyResult.Created
        val incoming = checkNotNull(repository.getIncomingSeatRequestForJourney(offer.journey.id))
        val trip = (repository.decideIncomingSeatRequest(incoming.id, IncomingRequestDecision.ACCEPT)
            as DecideIncomingRequestResult.Decided).confirmedTrip!!
        val conversation = (repository.getConversationForConfirmedTrip(trip.id)
            as GetConversationResult.Available).conversation
        repository.sendMessage(conversation.id, "See you at the public pickup point")
        repository.setPersonTrusted("jamie-demo", true)
        repository.refresh()
        repository.refresh()

        val snapshot = (repository.appState.value as AsyncState.Data<RydeSnapshot>).value
        assertEquals(1, snapshot.seatRequests.size)
        assertEquals(1, snapshot.confirmedTrips.size)
        assertEquals(1, snapshot.profileContent.people.count { it.id == "jamie-demo" })
        assertEquals(2, snapshot.coordinationActivities.map { it.id }.distinct().size)
        val refreshedConversation = (repository.getConversationForConfirmedTrip(trip.id)
            as GetConversationResult.Available).conversation
        assertEquals(4, refreshedConversation.messages.map { it.id }.distinct().size)
    }
}
