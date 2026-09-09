package uk.rydeapp.ryde.data

import uk.rydeapp.ryde.domain.ContributionCalculator
import uk.rydeapp.ryde.domain.FindRideMatcher
import uk.rydeapp.ryde.domain.model.DemoTravelDate
import uk.rydeapp.ryde.domain.model.DriverProfile
import uk.rydeapp.ryde.domain.model.FindRideContent
import uk.rydeapp.ryde.domain.model.FindRideCriteria
import uk.rydeapp.ryde.domain.model.FindRideSearchResult
import uk.rydeapp.ryde.domain.model.Flexibility
import uk.rydeapp.ryde.domain.model.HomeContent
import uk.rydeapp.ryde.domain.model.HostedCircle
import uk.rydeapp.ryde.domain.model.Journey
import uk.rydeapp.ryde.domain.model.RydeUser
import uk.rydeapp.ryde.domain.model.SavedPlace
import uk.rydeapp.ryde.domain.model.SuggestedMatch
import uk.rydeapp.ryde.domain.model.RouteMatch
import uk.rydeapp.ryde.domain.model.CancelSeatRequestResult
import uk.rydeapp.ryde.domain.model.CreateSeatRequestResult
import uk.rydeapp.ryde.domain.model.SeatRequest
import uk.rydeapp.ryde.domain.model.SeatRequestStatus

class FakeRydeRepository : RydeRepository {
    private val requestsByMatchId = linkedMapOf<String, SeatRequest>()
    private val savedPlaces = listOf(
        SavedPlace(label = "Home", area = "Sutton-in-Ashfield"),
        SavedPlace(label = "Work", area = "Nottingham"),
    )

    override fun getHomeContent(): HomeContent {
        val sharedMiles = 17
        return HomeContent(
            currentUser = RydeUser(
                firstName = "Sam",
                savedPlaces = savedPlaces,
            ),
            suggestedMatch = SuggestedMatch(
                driver = DriverProfile(
                    firstName = "Alex",
                    rating = 4.9,
                    isDemoVerified = true,
                ),
                driverJourney = Journey(
                    origin = "Mansfield",
                    destination = "Nottingham",
                    departureTime = "08:00",
                ),
                riderJourney = Journey(
                    origin = "Sutton-in-Ashfield",
                    destination = "Nottingham",
                    departureTime = "08:05",
                ),
                sharedMiles = sharedMiles,
                contributionPence = ContributionCalculator.calculatePence(sharedMiles),
                serviceFeePence = 50,
            ),
            hostedCircle = HostedCircle(
                name = "Nottingham Live — Event Travel",
                summary = "Share routes with people heading to the same fictional event.",
                serviceFeeCoveredByHost = true,
            ),
        )
    }

    override fun getFindRideContent() = FindRideContent(
        savedPlaces = savedPlaces,
        defaultCriteria = FindRideCriteria(
            origin = "Sutton-in-Ashfield",
            destination = "Nottingham",
            travelDate = DemoTravelDate.TODAY,
            departureMinutes = 8 * 60 + 5,
            flexibility = Flexibility.THIRTY,
            seatsRequired = 1,
        ),
    )

    override fun findRides(criteria: FindRideCriteria): FindRideSearchResult {
        val errors = FindRideMatcher.validate(criteria)
        return FindRideSearchResult(
            criteria = criteria,
            validationErrors = errors,
            matches = if (errors.isEmpty()) FindRideMatcher.rankCompatible(criteria, demoMatches) else emptyList(),
        )
    }

    override fun getSeatRequests(): List<SeatRequest> = requestsByMatchId.values.toList()

    override fun getSeatRequestForMatch(matchId: String): SeatRequest? = requestsByMatchId[matchId]

    override fun createSeatRequest(
        matchId: String,
        criteria: FindRideCriteria,
    ): CreateSeatRequestResult {
        requestsByMatchId[matchId]
            ?.takeIf { it.status == SeatRequestStatus.PENDING }
            ?.let { return CreateSeatRequestResult.DuplicateActive(it) }

        val match = demoMatches.firstOrNull { it.id == matchId }
            ?: return CreateSeatRequestResult.MatchNotFound
        if (criteria.seatsRequired !in 1..match.availableSeats) {
            return CreateSeatRequestResult.InvalidSeatCount
        }

        val request = SeatRequest(
            id = "demo-request-$matchId",
            matchId = matchId,
            status = SeatRequestStatus.PENDING,
            driver = match.driver,
            originArea = criteria.origin,
            destinationArea = criteria.destination,
            travelDate = match.travelDate,
            approximatePickupMinutes = match.pickupMinutes,
            pickupArea = match.pickupArea,
            requestedSeats = criteria.seatsRequired,
            sharedMiles = match.sharedMiles,
            contributionPence = ContributionCalculator.calculatePence(match.sharedMiles),
            serviceFeePence = match.serviceFeePence,
        )
        requestsByMatchId[matchId] = request
        return CreateSeatRequestResult.Created(request)
    }

    override fun cancelSeatRequest(requestId: String): CancelSeatRequestResult {
        val existing = requestsByMatchId.values.firstOrNull { it.id == requestId }
        if (existing?.status != SeatRequestStatus.PENDING) {
            return CancelSeatRequestResult.NotPending(existing)
        }
        val cancelled = existing.copy(status = SeatRequestStatus.CANCELLED)
        requestsByMatchId[existing.matchId] = cancelled
        return CancelSeatRequestResult.Cancelled(cancelled)
    }

    private val demoMatches = listOf(
        demoMatch(
            id = "alex-mansfield-nottingham",
            name = "Alex",
            rating = 4.9,
            driverOrigin = "Mansfield",
            departureTime = "08:00",
            pickupArea = "Sutton Bus Station",
            pickupMinutes = 8 * 60 + 5,
            walkMinutes = 6,
            sharedMiles = 17,
            detourMiles = 0.8,
            seats = 3,
            score = 92,
        ),
        demoMatch(
            id = "morgan-kirkby-nottingham",
            name = "Morgan",
            rating = 4.8,
            driverOrigin = "Kirkby-in-Ashfield",
            departureTime = "08:10",
            pickupArea = "Sutton town centre",
            pickupMinutes = 8 * 60 + 20,
            walkMinutes = 11,
            sharedMiles = 15,
            detourMiles = 1.0,
            seats = 1,
            score = 84,
        ),
        demoMatch(
            id = "jamie-chesterfield-nottingham",
            name = "Jamie",
            rating = 4.7,
            driverOrigin = "Chesterfield",
            departureTime = "08:05",
            pickupArea = "Sutton Parkway area",
            pickupMinutes = 8 * 60 + 35,
            walkMinutes = 14,
            sharedMiles = 13,
            detourMiles = 0.6,
            seats = 2,
            score = 77,
        ),
    )

    private fun demoMatch(
        id: String,
        name: String,
        rating: Double,
        driverOrigin: String,
        departureTime: String,
        pickupArea: String,
        pickupMinutes: Int,
        walkMinutes: Int,
        sharedMiles: Int,
        detourMiles: Double,
        seats: Int,
        score: Int,
    ) = RouteMatch(
        id = id,
        driver = DriverProfile(name, rating, isDemoVerified = true),
        driverJourney = Journey(driverOrigin, "Nottingham", departureTime),
        compatibleOrigin = "Sutton-in-Ashfield",
        compatibleDestination = "Nottingham",
        travelDate = DemoTravelDate.TODAY,
        pickupArea = pickupArea,
        pickupMinutes = pickupMinutes,
        walkMinutes = walkMinutes,
        sharedMiles = sharedMiles,
        detourMiles = detourMiles,
        availableSeats = seats,
        matchScore = score,
        contributionPence = ContributionCalculator.calculatePence(sharedMiles),
        serviceFeePence = 50,
    )
}
