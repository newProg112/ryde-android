package uk.rydeapp.ryde.data

import kotlinx.coroutines.runBlocking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.rydeapp.ryde.domain.model.CancelSeatRequestResult
import uk.rydeapp.ryde.domain.model.CreateSeatRequestResult
import uk.rydeapp.ryde.domain.model.SeatRequestStatus

class FakeRydeRepositorySeatRequestTest {
    private fun repositoryAndCriteria() = FakeRydeRepository().let {
        it to it.getFindRideContent().defaultCriteria
    }

    @Test
    fun `creating a request stores it as pending`() = runBlocking {
        val (repository, criteria) = repositoryAndCriteria()

        val result = repository.createSeatRequest("alex-mansfield-nottingham", criteria)

        assertTrue(result is CreateSeatRequestResult.Created)
        val request = (result as CreateSeatRequestResult.Created).request
        assertEquals(SeatRequestStatus.PENDING, request.status)
        assertEquals(request, repository.getSeatRequestForMatch(request.matchId))
    }

    @Test
    fun `duplicate active request returns existing record without adding another`() = runBlocking {
        val (repository, criteria) = repositoryAndCriteria()
        val first = repository.createSeatRequest("alex-mansfield-nottingham", criteria) as CreateSeatRequestResult.Created

        val duplicate = repository.createSeatRequest("alex-mansfield-nottingham", criteria)

        assertTrue(duplicate is CreateSeatRequestResult.DuplicateActive)
        assertSame(first.request, (duplicate as CreateSeatRequestResult.DuplicateActive).request)
        assertEquals(1, repository.getSeatRequests().size)
    }

    @Test
    fun `request captures seat count and contribution totals`() = runBlocking {
        val (repository, criteria) = repositoryAndCriteria()

        val request = (repository.createSeatRequest(
            "alex-mansfield-nottingham",
            criteria.copy(seatsRequired = 2),
        ) as CreateSeatRequestResult.Created).request

        assertEquals(2, request.requestedSeats)
        assertEquals(350, request.contributionPence)
        assertEquals(50, request.serviceFeePence)
        assertEquals(400, request.riderTotalPence)
        assertEquals(350, request.driverReceivesPence)
    }

    @Test
    fun `pending request can be cancelled and remains as history`() = runBlocking {
        val (repository, criteria) = repositoryAndCriteria()
        val created = repository.createSeatRequest("alex-mansfield-nottingham", criteria) as CreateSeatRequestResult.Created

        val result = repository.cancelSeatRequest(created.request.id)

        assertTrue(result is CancelSeatRequestResult.Cancelled)
        assertEquals(SeatRequestStatus.CANCELLED, repository.getSeatRequests().single().status)
    }

    @Test
    fun `cancelling a non-pending or unknown request is safely rejected`() = runBlocking {
        val (repository, criteria) = repositoryAndCriteria()
        val created = repository.createSeatRequest("alex-mansfield-nottingham", criteria) as CreateSeatRequestResult.Created
        repository.cancelSeatRequest(created.request.id)

        val repeated = repository.cancelSeatRequest(created.request.id)
        val unknown = repository.cancelSeatRequest("missing-request")

        assertTrue(repeated is CancelSeatRequestResult.NotPending)
        assertEquals(SeatRequestStatus.CANCELLED, (repeated as CancelSeatRequestResult.NotPending).request?.status)
        assertTrue(unknown is CancelSeatRequestResult.NotPending)
        assertNull((unknown as CancelSeatRequestResult.NotPending).request)
    }

    @Test
    fun `request state remains available across repository reads in one session`() = runBlocking {
        val (repository, criteria) = repositoryAndCriteria()
        val created = repository.createSeatRequest("alex-mansfield-nottingham", criteria) as CreateSeatRequestResult.Created

        val firstRead = repository.getSeatRequests().single()
        val secondRead = repository.getSeatRequestForMatch("alex-mansfield-nottingham")

        assertEquals(created.request, firstRead)
        assertEquals(firstRead, secondRead)
    }
}
