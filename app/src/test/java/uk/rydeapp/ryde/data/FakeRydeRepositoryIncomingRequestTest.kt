package uk.rydeapp.ryde.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.rydeapp.ryde.domain.ContributionCalculator
import uk.rydeapp.ryde.domain.model.CreateOfferedJourneyResult
import uk.rydeapp.ryde.domain.model.CreateSeatRequestResult
import uk.rydeapp.ryde.domain.model.DecideIncomingRequestResult
import uk.rydeapp.ryde.domain.model.IncomingRequestDecision
import uk.rydeapp.ryde.domain.model.IncomingSeatRequestStatus
import uk.rydeapp.ryde.domain.model.OfferedJourney
import uk.rydeapp.ryde.domain.model.OfferedJourneyStatus
import uk.rydeapp.ryde.domain.model.SeatRequestStatus

class FakeRydeRepositoryIncomingRequestTest {
    private fun repositoryWithOffer(spareSeats: Int = 2): Pair<FakeRydeRepository, OfferedJourney> {
        val repository = FakeRydeRepository()
        val criteria = repository.getOfferRideContent().defaultCriteria.copy(spareSeats = spareSeats)
        val offer = (repository.createOfferedJourney(criteria) as CreateOfferedJourneyResult.Created).journey
        return repository to offer
    }

    @Test
    fun `open offer deterministically creates one fictional incoming request`() {
        val (repository, offer) = repositoryWithOffer()

        val firstRead = repository.getIncomingSeatRequests().single()
        val secondRead = repository.getIncomingSeatRequestForJourney(offer.id)

        assertEquals("incoming-demo-offer-1", firstRead.id)
        assertEquals("Jamie", firstRead.rider.firstName)
        assertTrue(firstRead.rider.isDemoVerified)
        assertEquals(IncomingSeatRequestStatus.PENDING, firstRead.status)
        assertEquals(firstRead, secondRead)
    }

    @Test
    fun `incoming request is associated with its offered journey and overlapping route`() {
        val (repository, offer) = repositoryWithOffer()
        val request = repository.getIncomingSeatRequests().single()

        assertEquals(offer.id, request.offeredJourneyId)
        assertEquals(offer.originArea, request.originArea)
        assertEquals(offer.destinationArea, request.destinationArea)
    }

    @Test
    fun `incoming request pricing uses contribution calculator and current fee`() {
        val (repository, _) = repositoryWithOffer()
        val request = repository.getIncomingSeatRequests().single()

        assertEquals(ContributionCalculator.calculatePence(request.sharedMiles), request.contributionPence)
        assertEquals(request.contributionPence + request.serviceFeePence, request.riderTotalPence)
        assertEquals(request.contributionPence, request.driverReceivesPence)
        assertEquals(50, request.serviceFeePence)
    }

    @Test
    fun `accepting pending request confirms journey and creates shared trip`() {
        val (repository, offer) = repositoryWithOffer()
        val request = repository.getIncomingSeatRequests().single()

        val result = repository.decideIncomingSeatRequest(request.id, IncomingRequestDecision.ACCEPT)

        assertTrue(result is DecideIncomingRequestResult.Decided)
        assertEquals(IncomingSeatRequestStatus.ACCEPTED, repository.getIncomingSeatRequests().single().status)
        assertEquals(OfferedJourneyStatus.CONFIRMED, repository.getOfferedJourneys().single().status)
        val trip = repository.getConfirmedSharedTrips().single()
        assertEquals(offer.id, trip.offeredJourneyId)
        assertEquals(request.id, trip.incomingRequestId)
    }

    @Test
    fun `acceptance reduces spare seats once and duplicate acceptance is rejected`() {
        val (repository, _) = repositoryWithOffer(spareSeats = 3)
        val request = repository.getIncomingSeatRequests().single()
        repository.decideIncomingSeatRequest(request.id, IncomingRequestDecision.ACCEPT)

        val repeated = repository.decideIncomingSeatRequest(request.id, IncomingRequestDecision.ACCEPT)

        assertTrue(repeated is DecideIncomingRequestResult.AlreadyDecided)
        assertEquals(2, repository.getOfferedJourneys().single().spareSeats)
        assertEquals(1, repository.getConfirmedSharedTrips().size)
    }

    @Test
    fun `declining pending request retains history and leaves offer open`() {
        val (repository, _) = repositoryWithOffer(spareSeats = 3)
        val request = repository.getIncomingSeatRequests().single()

        val result = repository.decideIncomingSeatRequest(request.id, IncomingRequestDecision.DECLINE)

        assertTrue(result is DecideIncomingRequestResult.Decided)
        assertEquals(IncomingSeatRequestStatus.DECLINED, repository.getIncomingSeatRequests().single().status)
        assertEquals(OfferedJourneyStatus.OPEN, repository.getOfferedJourneys().single().status)
        assertEquals(3, repository.getOfferedJourneys().single().spareSeats)
        assertTrue(repository.getConfirmedSharedTrips().isEmpty())
        assertTrue(repository.decideIncomingSeatRequest(request.id, IncomingRequestDecision.ACCEPT) is DecideIncomingRequestResult.AlreadyDecided)
    }

    @Test
    fun `decision is rejected when related offer was cancelled`() {
        val (repository, offer) = repositoryWithOffer()
        val request = repository.getIncomingSeatRequests().single()
        repository.cancelOfferedJourney(offer.id)

        val result = repository.decideIncomingSeatRequest(request.id, IncomingRequestDecision.ACCEPT)

        assertTrue(result is DecideIncomingRequestResult.RelatedOfferUnavailable)
        assertEquals(IncomingSeatRequestStatus.PENDING, repository.getIncomingSeatRequests().single().status)
        assertTrue(repository.getConfirmedSharedTrips().isEmpty())
    }

    @Test
    fun `accepted and declined decisions remain stable across repository reads`() {
        val (acceptedRepository, _) = repositoryWithOffer()
        val accepted = acceptedRepository.getIncomingSeatRequests().single()
        acceptedRepository.decideIncomingSeatRequest(accepted.id, IncomingRequestDecision.ACCEPT)

        val (declinedRepository, _) = repositoryWithOffer()
        val declined = declinedRepository.getIncomingSeatRequests().single()
        declinedRepository.decideIncomingSeatRequest(declined.id, IncomingRequestDecision.DECLINE)

        assertEquals(IncomingSeatRequestStatus.ACCEPTED, acceptedRepository.getIncomingSeatRequests().single().status)
        assertEquals(IncomingSeatRequestStatus.ACCEPTED, acceptedRepository.getIncomingSeatRequests().single().status)
        assertEquals(IncomingSeatRequestStatus.DECLINED, declinedRepository.getIncomingSeatRequests().single().status)
        assertEquals(IncomingSeatRequestStatus.DECLINED, declinedRepository.getIncomingSeatRequests().single().status)
    }

    @Test
    fun `existing outgoing request and offered journey reads remain intact`() {
        val (repository, offer) = repositoryWithOffer()
        val outgoing = repository.createSeatRequest(
            "alex-mansfield-nottingham",
            repository.getFindRideContent().defaultCriteria,
        ) as CreateSeatRequestResult.Created

        assertEquals(SeatRequestStatus.PENDING, repository.getSeatRequests().single().status)
        assertSame(outgoing.request, repository.getSeatRequestForMatch(outgoing.request.matchId))
        assertEquals(offer, repository.getOfferedJourneys().single())
    }
}
