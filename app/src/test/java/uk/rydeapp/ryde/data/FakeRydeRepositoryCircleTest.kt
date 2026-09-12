package uk.rydeapp.ryde.data

import kotlinx.coroutines.runBlocking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.rydeapp.ryde.domain.model.CreateOfferedJourneyResult
import uk.rydeapp.ryde.domain.model.CreateSeatRequestResult
import uk.rydeapp.ryde.domain.model.DecideIncomingRequestResult
import uk.rydeapp.ryde.domain.model.IncomingRequestDecision
import uk.rydeapp.ryde.domain.model.JoinCircleResult
import uk.rydeapp.ryde.domain.model.LeaveCircleResult
import uk.rydeapp.ryde.domain.model.ServiceFeeResponsibility
import uk.rydeapp.ryde.domain.model.DemoTravelDate
import uk.rydeapp.ryde.domain.model.TravelDatePolicy
import uk.rydeapp.ryde.domain.model.DemoDepartureTimePolicy

class FakeRydeRepositoryCircleTest {
    private val circleId = "nottingham-live"

    @Test
    fun `membership starts unjoined and is retained across reads`() = runBlocking {
        val repository = FakeRydeRepository()

        assertFalse(repository.getCircleMembership(circleId)!!.isJoined)
        repository.joinCircle(circleId)

        assertTrue(repository.getCircleMembership(circleId)!!.isJoined)
        assertTrue(repository.getCircleMembership(circleId)!!.isJoined)
    }

    @Test
    fun `join prevents duplicates and leave permits rejoin`() = runBlocking {
        val repository = FakeRydeRepository()

        assertTrue(repository.joinCircle(circleId) is JoinCircleResult.Joined)
        assertTrue(repository.joinCircle(circleId) is JoinCircleResult.AlreadyJoined)
        assertTrue(repository.leaveCircle(circleId) is LeaveCircleResult.Left)
        assertFalse(repository.getCircleMembership(circleId)!!.isJoined)
        assertTrue(repository.joinCircle(circleId) is JoinCircleResult.Joined)
        assertTrue(repository.getCircleMembership(circleId)!!.isJoined)
    }

    @Test
    fun `circle search keeps deterministic matches and applies host-covered pricing`() = runBlocking {
        val repository = FakeRydeRepository()
        repository.joinCircle(circleId)
        val criteria = repository.getFindRideContent().defaultCriteria.copy(
            travelDate = DemoTravelDate.TOMORROW,
            circleId = circleId,
        )

        val circleResult = repository.findRides(criteria)
        val circleMatch = circleResult.matches.first()
        val repeatedCircleResult = repository.findRides(criteria)
        val ordinaryMatch = repository.findRides(
            criteria.copy(travelDate = DemoTravelDate.TODAY, circleId = null),
        ).matches.first()

        assertEquals("alex-mansfield-nottingham", circleMatch.id)
        assertEquals(DemoTravelDate.EVENT_DAY, circleResult.criteria.travelDate)
        assertEquals(DemoDepartureTimePolicy.CIRCLE_DEFAULT, circleResult.criteria.departureMinutes)
        assertEquals(DemoTravelDate.EVENT_DAY, circleMatch.travelDate)
        assertEquals("17:25", circleMatch.driverJourney.departureTime)
        assertEquals(17 * 60 + 30, circleMatch.pickupMinutes)
        assertEquals(
            listOf("Alex", "Morgan", "Jamie"),
            circleResult.matches.map { it.driver.firstName },
        )
        assertEquals(circleResult.matches, repeatedCircleResult.matches)
        assertEquals(circleId, circleMatch.circle?.id)
        assertEquals(ServiceFeeResponsibility.HOST, circleMatch.serviceFeeResponsibility)
        assertEquals(350, circleMatch.riderTotalPence)
        assertEquals(400, ordinaryMatch.riderTotalPence)
        assertEquals(circleMatch.driverReceivesPence, ordinaryMatch.driverReceivesPence)
    }

    @Test
    fun `circle identity is retained on seat request`() = runBlocking {
        val repository = FakeRydeRepository()
        repository.joinCircle(circleId)
        val criteria = repository.getFindRideContent().defaultCriteria.copy(
            travelDate = DemoTravelDate.TOMORROW,
            circleId = circleId,
        )

        val request = (repository.createSeatRequest("alex-mansfield-nottingham", criteria) as CreateSeatRequestResult.Created).request

        assertEquals(circleId, request.circle?.id)
        assertEquals(DemoTravelDate.EVENT_DAY, request.travelDate)
        assertEquals(17 * 60 + 30, request.approximatePickupMinutes)
        assertEquals(350, request.riderTotalPence)
        assertEquals(350, request.driverReceivesPence)
        assertEquals(request, repository.getSeatRequestForMatch(request.matchId, circleId))
    }

    @Test
    fun `circle identity flows from offer through incoming request to confirmed trip`() = runBlocking {
        val repository = FakeRydeRepository()
        repository.joinCircle(circleId)
        val criteria = repository.getOfferRideContent().defaultCriteria.copy(
            travelDate = DemoTravelDate.TOMORROW,
            circleId = circleId,
        )

        val offer = (repository.createOfferedJourney(criteria) as CreateOfferedJourneyResult.Created).journey
        val incoming = repository.getIncomingSeatRequestForJourney(offer.id)!!
        val accepted = repository.decideIncomingSeatRequest(incoming.id, IncomingRequestDecision.ACCEPT)
            as DecideIncomingRequestResult.Decided
        val trip = accepted.confirmedTrip!!

        assertEquals(circleId, offer.circle?.id)
        assertEquals(DemoTravelDate.EVENT_DAY, offer.travelDate)
        assertEquals(17 * 60 + 30, offer.departureMinutes)
        assertEquals(circleId, incoming.circle?.id)
        assertEquals(DemoTravelDate.EVENT_DAY, incoming.travelDate)
        assertEquals(17 * 60 + 35, incoming.approximatePickupMinutes)
        assertEquals(circleId, trip.circle?.id)
        assertEquals(DemoTravelDate.EVENT_DAY, trip.travelDate)
        assertEquals(17 * 60 + 35, trip.approximatePickupMinutes)
        assertEquals(350, trip.riderTotalPence)
        assertEquals(350, trip.driverReceivesPence)
    }

    @Test
    fun `leaving circle does not erase historical circle identity`() = runBlocking {
        val repository = FakeRydeRepository()
        repository.joinCircle(circleId)
        val criteria = repository.getOfferRideContent().defaultCriteria.copy(circleId = circleId)
        val offer = (repository.createOfferedJourney(criteria) as CreateOfferedJourneyResult.Created).journey
        val incoming = repository.getIncomingSeatRequestForJourney(offer.id)!!
        repository.decideIncomingSeatRequest(incoming.id, IncomingRequestDecision.ACCEPT)

        repository.leaveCircle(circleId)

        assertFalse(repository.getCircleMembership(circleId)!!.isJoined)
        assertEquals(circleId, repository.getOfferedJourneys().single().circle?.id)
        assertEquals(circleId, repository.getIncomingSeatRequestForJourney(offer.id)?.circle?.id)
        assertEquals(circleId, repository.getConfirmedSharedTrips().single().circle?.id)
    }

    @Test
    fun `non-circle request and offer flows remain ordinary`() = runBlocking {
        val repository = FakeRydeRepository()

        val request = (repository.createSeatRequest(
            "alex-mansfield-nottingham",
            repository.getFindRideContent().defaultCriteria,
        ) as CreateSeatRequestResult.Created).request
        val offer = (repository.createOfferedJourney(
            repository.getOfferRideContent().defaultCriteria,
        ) as CreateOfferedJourneyResult.Created).journey

        assertNull(request.circle)
        assertNull(offer.circle)
        assertEquals(ServiceFeeResponsibility.RIDER, request.serviceFeeResponsibility)
        assertEquals(400, request.riderTotalPence)
        assertEquals(DemoTravelDate.TODAY, request.travelDate)
        assertEquals(DemoTravelDate.TODAY, offer.travelDate)
        assertEquals(8 * 60 + 5, request.approximatePickupMinutes)
        assertEquals(8 * 60, offer.departureMinutes)
        assertTrue(repository.findRides(repository.getFindRideContent().defaultCriteria).matches.isNotEmpty())
        assertTrue(
            repository.findRides(
                repository.getFindRideContent().defaultCriteria.copy(travelDate = DemoTravelDate.TOMORROW),
            ).matches.isEmpty(),
        )
    }

    @Test
    fun `ordinary tomorrow offer remains tomorrow`() = runBlocking {
        val repository = FakeRydeRepository()
        val criteria = repository.getOfferRideContent().defaultCriteria.copy(travelDate = DemoTravelDate.TOMORROW)

        val offer = (repository.createOfferedJourney(criteria) as CreateOfferedJourneyResult.Created).journey

        assertEquals(DemoTravelDate.TOMORROW, offer.travelDate)
        assertNull(offer.circle)
    }

    @Test
    fun `date policy switches modes without retaining a mismatched date`() = runBlocking {
        val repository = FakeRydeRepository()
        val circle = repository.getCircleMembership(circleId)!!.circle

        val circleDate = TravelDatePolicy.forMode(DemoTravelDate.TOMORROW, circle)
        val restoredOrdinaryDate = TravelDatePolicy.forMode(DemoTravelDate.TOMORROW, circle = null)
        val invalidOrdinaryDate = TravelDatePolicy.forMode(DemoTravelDate.EVENT_DAY, circle = null)

        assertEquals(DemoTravelDate.EVENT_DAY, circleDate)
        assertEquals(DemoTravelDate.TOMORROW, restoredOrdinaryDate)
        assertEquals(DemoTravelDate.TODAY, invalidOrdinaryDate)
        assertEquals("Event day · 18 October 2026", circle.eventDate.displayName)
        assertEquals(listOf(DemoTravelDate.TODAY, DemoTravelDate.TOMORROW), DemoTravelDate.ordinaryChoices)
    }

    @Test
    fun `departure policy keeps ordinary and circle modes valid when switching`() = runBlocking {
        assertEquals(
            listOf(17 * 60, 17 * 60 + 30, 18 * 60),
            DemoDepartureTimePolicy.circleChoices,
        )
        assertEquals(
            listOf(7 * 60 + 35, 8 * 60 + 5, 8 * 60 + 35),
            DemoDepartureTimePolicy.ordinaryFindChoices,
        )
        assertEquals(
            listOf(7 * 60 + 30, 8 * 60, 8 * 60 + 30),
            DemoDepartureTimePolicy.ordinaryOfferChoices,
        )

        assertEquals(17 * 60 + 30, DemoDepartureTimePolicy.resolveFind(8 * 60 + 5, isCircleMode = true))
        assertEquals(8 * 60 + 5, DemoDepartureTimePolicy.resolveFind(17 * 60 + 30, isCircleMode = false))
        assertEquals(17 * 60, DemoDepartureTimePolicy.resolveFind(17 * 60, isCircleMode = true))
        assertEquals(7 * 60 + 35, DemoDepartureTimePolicy.resolveFind(7 * 60 + 35, isCircleMode = false))
        assertEquals(17 * 60 + 30, DemoDepartureTimePolicy.resolveOffer(8 * 60, isCircleMode = true))
        assertEquals(8 * 60, DemoDepartureTimePolicy.resolveOffer(17 * 60 + 30, isCircleMode = false))
        assertEquals("Doors 18:30 · fictional demo time", DemoDepartureTimePolicy.eventDoorsDisplayName)
    }
}
