package uk.rydeapp.ryde.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.rydeapp.ryde.domain.model.CancelOfferedJourneyResult
import uk.rydeapp.ryde.domain.model.CreateOfferedJourneyResult
import uk.rydeapp.ryde.domain.model.OfferRideField
import uk.rydeapp.ryde.domain.model.OfferRideValidator
import uk.rydeapp.ryde.domain.model.OfferedJourneyStatus
import uk.rydeapp.ryde.domain.model.CreateSeatRequestResult
import uk.rydeapp.ryde.domain.model.SeatRequestStatus

class FakeRydeRepositoryOfferedJourneyTest {
    private fun repositoryAndCriteria() = FakeRydeRepository().let {
        it to it.getOfferRideContent().defaultCriteria
    }

    @Test
    fun `valid offer creation stores an open deterministic journey`() {
        val (repository, criteria) = repositoryAndCriteria()

        val result = repository.createOfferedJourney(criteria)

        assertTrue(result is CreateOfferedJourneyResult.Created)
        val journey = (result as CreateOfferedJourneyResult.Created).journey
        assertEquals("demo-offer-1", journey.id)
        assertEquals(OfferedJourneyStatus.OPEN, journey.status)
        assertEquals(journey, repository.getOfferedJourneys().single())
    }

    @Test
    fun `offer areas are trimmed and whitespace is normalised`() {
        val (repository, criteria) = repositoryAndCriteria()

        val result = repository.createOfferedJourney(
            criteria.copy(originArea = "  Sutton-in-Ashfield  ", destinationArea = "  Nottingham   city centre "),
        ) as CreateOfferedJourneyResult.Created

        assertEquals("Sutton-in-Ashfield", result.journey.originArea)
        assertEquals("Nottingham city centre", result.journey.destinationArea)
    }

    @Test
    fun `identical normalised endpoints are invalid`() {
        val (_, criteria) = repositoryAndCriteria()

        val errors = OfferRideValidator.validate(
            criteria.copy(originArea = " Nottingham ", destinationArea = "nottingham"),
        )

        assertTrue(errors.any { it.field == OfferRideField.ENDPOINTS })
    }

    @Test
    fun `spare seat count outside one to four is invalid`() {
        val (_, criteria) = repositoryAndCriteria()

        val errors = OfferRideValidator.validate(criteria.copy(spareSeats = 5))

        assertTrue(errors.any { it.field == OfferRideField.SEATS })
    }

    @Test
    fun `unsupported detour choice is invalid`() {
        val (_, criteria) = repositoryAndCriteria()

        val errors = OfferRideValidator.validate(criteria.copy(maximumDetourMiles = 2))

        assertTrue(errors.any { it.field == OfferRideField.DETOUR })
    }

    @Test
    fun `normalised active route date and time cannot be duplicated`() {
        val (repository, criteria) = repositoryAndCriteria()
        val created = repository.createOfferedJourney(criteria) as CreateOfferedJourneyResult.Created

        val duplicate = repository.createOfferedJourney(
            criteria.copy(originArea = "  ${criteria.originArea.uppercase()} ", destinationArea = criteria.destinationArea.lowercase()),
        )

        assertTrue(duplicate is CreateOfferedJourneyResult.DuplicateActive)
        assertSame(created.journey, (duplicate as CreateOfferedJourneyResult.DuplicateActive).journey)
        assertEquals(1, repository.getOfferedJourneys().size)
    }

    @Test
    fun `open offer can be cancelled and remains as history`() {
        val (repository, criteria) = repositoryAndCriteria()
        val created = repository.createOfferedJourney(criteria) as CreateOfferedJourneyResult.Created

        val result = repository.cancelOfferedJourney(created.journey.id)

        assertTrue(result is CancelOfferedJourneyResult.Cancelled)
        assertEquals(OfferedJourneyStatus.CANCELLED, repository.getOfferedJourneys().single().status)
    }

    @Test
    fun `cancelling non-open or unknown offer is safely rejected`() {
        val (repository, criteria) = repositoryAndCriteria()
        val created = repository.createOfferedJourney(criteria) as CreateOfferedJourneyResult.Created
        repository.cancelOfferedJourney(created.journey.id)

        val repeated = repository.cancelOfferedJourney(created.journey.id)
        val unknown = repository.cancelOfferedJourney("missing-offer")

        assertTrue(repeated is CancelOfferedJourneyResult.NotOpen)
        assertEquals(OfferedJourneyStatus.CANCELLED, (repeated as CancelOfferedJourneyResult.NotOpen).journey?.status)
        assertTrue(unknown is CancelOfferedJourneyResult.NotOpen)
        assertNull((unknown as CancelOfferedJourneyResult.NotOpen).journey)
    }

    @Test
    fun `offer state is retained across reads in the same repository session`() {
        val (repository, criteria) = repositoryAndCriteria()
        val created = repository.createOfferedJourney(criteria) as CreateOfferedJourneyResult.Created

        assertEquals(created.journey, repository.getOfferedJourneys().single())
        assertEquals(created.journey, repository.getOfferedJourneys().single())
    }

    @Test
    fun `existing seat request behaviour remains intact alongside offers`() {
        val repository = FakeRydeRepository()
        val offer = repository.createOfferedJourney(repository.getOfferRideContent().defaultCriteria)
        val request = repository.createSeatRequest(
            "alex-mansfield-nottingham",
            repository.getFindRideContent().defaultCriteria,
        )

        assertTrue(offer is CreateOfferedJourneyResult.Created)
        assertTrue(request is CreateSeatRequestResult.Created)
        assertEquals(SeatRequestStatus.PENDING, repository.getSeatRequests().single().status)
        assertEquals(OfferedJourneyStatus.OPEN, repository.getOfferedJourneys().single().status)
    }
}
