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
import uk.rydeapp.ryde.domain.model.CancelOfferedJourneyResult
import uk.rydeapp.ryde.domain.model.CreateOfferedJourneyResult
import uk.rydeapp.ryde.domain.model.OfferRideContent
import uk.rydeapp.ryde.domain.model.OfferRideCriteria
import uk.rydeapp.ryde.domain.model.OfferRideValidator
import uk.rydeapp.ryde.domain.model.OfferedJourney
import uk.rydeapp.ryde.domain.model.OfferedJourneyStatus
import uk.rydeapp.ryde.domain.model.ConfirmedSharedTrip
import uk.rydeapp.ryde.domain.model.DecideIncomingRequestResult
import uk.rydeapp.ryde.domain.model.DemoRiderProfile
import uk.rydeapp.ryde.domain.model.IncomingRequestDecision
import uk.rydeapp.ryde.domain.model.IncomingSeatRequest
import uk.rydeapp.ryde.domain.model.IncomingSeatRequestStatus
import uk.rydeapp.ryde.domain.model.CircleMembership
import uk.rydeapp.ryde.domain.model.JoinCircleResult
import uk.rydeapp.ryde.domain.model.LeaveCircleResult
import uk.rydeapp.ryde.domain.model.ServiceFeeResponsibility
import uk.rydeapp.ryde.domain.model.TravelDatePolicy
import uk.rydeapp.ryde.domain.model.DemoDepartureTimePolicy
import uk.rydeapp.ryde.domain.model.formatDemoTime

class FakeRydeRepository : RydeRepository {
    private val requestsByMatchId = linkedMapOf<String, SeatRequest>()
    private val offeredJourneys = mutableListOf<OfferedJourney>()
    private val incomingRequests = mutableListOf<IncomingSeatRequest>()
    private val confirmedTrips = mutableListOf<ConfirmedSharedTrip>()
    private val joinedCircleIds = mutableSetOf<String>()
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
                serviceFeePence = DEMO_SERVICE_FEE_PENCE,
            ),
            hostedCircle = demoCircle,
        )
    }

    override fun getCircleMembership(circleId: String): CircleMembership? =
        demoCircle.takeIf { it.id == circleId }?.let { CircleMembership(it, circleId in joinedCircleIds) }

    override fun joinCircle(circleId: String): JoinCircleResult {
        val circle = demoCircle.takeIf { it.id == circleId } ?: return JoinCircleResult.CircleNotFound
        val membership = CircleMembership(circle, isJoined = true)
        return if (joinedCircleIds.add(circleId)) {
            JoinCircleResult.Joined(membership)
        } else {
            JoinCircleResult.AlreadyJoined(membership)
        }
    }

    override fun leaveCircle(circleId: String): LeaveCircleResult {
        if (circleId != demoCircle.id) return LeaveCircleResult.NotJoined(null)
        val membership = CircleMembership(demoCircle, isJoined = false)
        return if (joinedCircleIds.remove(circleId)) {
            LeaveCircleResult.Left(membership)
        } else {
            LeaveCircleResult.NotJoined(membership)
        }
    }

    override fun getFindRideContent() = FindRideContent(
        savedPlaces = savedPlaces,
        defaultCriteria = FindRideCriteria(
            origin = "Sutton-in-Ashfield",
            destination = "Nottingham",
            travelDate = DemoTravelDate.TODAY,
            departureMinutes = DemoDepartureTimePolicy.ORDINARY_FIND_DEFAULT,
            flexibility = Flexibility.THIRTY,
            seatsRequired = 1,
        ),
    )

    override fun findRides(criteria: FindRideCriteria): FindRideSearchResult {
        val circle = joinedCircle(criteria.circleId)
        val effectiveCriteria = criteria.copy(
            travelDate = TravelDatePolicy.forMode(criteria.travelDate, circle),
            departureMinutes = DemoDepartureTimePolicy.resolveFind(
                criteria.departureMinutes,
                isCircleMode = circle != null,
            ),
        )
        val errors = FindRideMatcher.validate(effectiveCriteria)
        return FindRideSearchResult(
            criteria = effectiveCriteria,
            validationErrors = errors,
            matches = if (errors.isEmpty()) {
                val candidates = if (circle == null) demoMatches else demoMatches.mapIndexed { index, match ->
                    val timing = DemoDepartureTimePolicy.circleMatchTiming(index)
                    match.copy(
                        driverJourney = match.driverJourney.copy(
                            departureTime = formatDemoTime(timing.driverDepartureMinutes),
                        ),
                        travelDate = circle.eventDate,
                        pickupMinutes = timing.pickupMinutes,
                    )
                }
                FindRideMatcher.rankCompatible(effectiveCriteria, candidates).map { match ->
                    if (circle == null) match else match.copy(
                        serviceFeeResponsibility = ServiceFeeResponsibility.HOST,
                        circle = circle.identity,
                    )
                }
            } else emptyList(),
        )
    }

    override fun getSeatRequests(): List<SeatRequest> = requestsByMatchId.values.toList()

    override fun getSeatRequestForMatch(matchId: String, circleId: String?): SeatRequest? =
        requestsByMatchId[requestKey(matchId, circleId)]

    override fun createSeatRequest(
        matchId: String,
        criteria: FindRideCriteria,
    ): CreateSeatRequestResult {
        val circle = joinedCircle(criteria.circleId)
        val requestKey = requestKey(matchId, circle?.id)
        requestsByMatchId[requestKey]
            ?.takeIf { it.status == SeatRequestStatus.PENDING }
            ?.let { return CreateSeatRequestResult.DuplicateActive(it) }

        val matchIndex = demoMatches.indexOfFirst { it.id == matchId }
        val match = demoMatches.getOrNull(matchIndex) ?: return CreateSeatRequestResult.MatchNotFound
        val effectiveTravelDate = circle?.eventDate ?: match.travelDate
        val effectivePickupMinutes = if (circle == null) {
            match.pickupMinutes
        } else {
            DemoDepartureTimePolicy.circleMatchTiming(matchIndex).pickupMinutes
        }
        if (criteria.seatsRequired !in 1..match.availableSeats) {
            return CreateSeatRequestResult.InvalidSeatCount
        }

        val request = SeatRequest(
            id = "demo-request-$matchId${if (circle == null) "" else "-${circle.id}"}",
            matchId = matchId,
            status = SeatRequestStatus.PENDING,
            driver = match.driver,
            originArea = criteria.origin,
            destinationArea = criteria.destination,
            travelDate = effectiveTravelDate,
            approximatePickupMinutes = effectivePickupMinutes,
            pickupArea = match.pickupArea,
            requestedSeats = criteria.seatsRequired,
            sharedMiles = match.sharedMiles,
            contributionPence = ContributionCalculator.calculatePence(match.sharedMiles),
            serviceFeePence = match.serviceFeePence,
            serviceFeeResponsibility = if (circle == null) ServiceFeeResponsibility.RIDER else ServiceFeeResponsibility.HOST,
            circle = circle?.identity,
        )
        requestsByMatchId[requestKey] = request
        return CreateSeatRequestResult.Created(request)
    }

    override fun cancelSeatRequest(requestId: String): CancelSeatRequestResult {
        val existing = requestsByMatchId.values.firstOrNull { it.id == requestId }
        if (existing?.status != SeatRequestStatus.PENDING) {
            return CancelSeatRequestResult.NotPending(existing)
        }
        val cancelled = existing.copy(status = SeatRequestStatus.CANCELLED)
        requestsByMatchId[requestKey(existing.matchId, existing.circle?.id)] = cancelled
        return CancelSeatRequestResult.Cancelled(cancelled)
    }

    override fun getOfferRideContent() = OfferRideContent(
        savedPlaces = savedPlaces,
        defaultCriteria = OfferRideCriteria(
            originArea = savedPlaces.first { it.label == "Home" }.area,
            destinationArea = savedPlaces.first { it.label == "Work" }.area,
            travelDate = DemoTravelDate.TODAY,
            departureMinutes = DemoDepartureTimePolicy.ORDINARY_OFFER_DEFAULT,
            flexibility = Flexibility.THIRTY,
            spareSeats = 1,
            maximumDetourMiles = 3,
        ),
    )

    override fun getOfferedJourneys(): List<OfferedJourney> = offeredJourneys.toList()

    override fun createOfferedJourney(criteria: OfferRideCriteria): CreateOfferedJourneyResult {
        val errors = OfferRideValidator.validate(criteria)
        if (errors.isNotEmpty()) return CreateOfferedJourneyResult.Invalid(errors)

        val requestedCircle = joinedCircle(criteria.circleId)
        val normalized = OfferRideValidator.normalize(criteria).copy(
            travelDate = TravelDatePolicy.forMode(criteria.travelDate, requestedCircle),
            departureMinutes = DemoDepartureTimePolicy.resolveOffer(
                criteria.departureMinutes,
                isCircleMode = requestedCircle != null,
            ),
        )
        offeredJourneys.firstOrNull {
            it.status == OfferedJourneyStatus.OPEN &&
                OfferRideValidator.routeKey(it.originArea) == OfferRideValidator.routeKey(normalized.originArea) &&
                OfferRideValidator.routeKey(it.destinationArea) == OfferRideValidator.routeKey(normalized.destinationArea) &&
                it.travelDate == normalized.travelDate &&
                it.departureMinutes == normalized.departureMinutes &&
                it.circle?.id == requestedCircle?.id
        }?.let { return CreateOfferedJourneyResult.DuplicateActive(it) }

        val journey = OfferedJourney(
            id = "demo-offer-${offeredJourneys.size + 1}",
            status = OfferedJourneyStatus.OPEN,
            originArea = normalized.originArea,
            destinationArea = normalized.destinationArea,
            travelDate = normalized.travelDate,
            departureMinutes = normalized.departureMinutes,
            flexibility = normalized.flexibility,
            spareSeats = normalized.spareSeats,
            maximumDetourMiles = normalized.maximumDetourMiles,
            circle = requestedCircle?.identity,
        )
        offeredJourneys += journey
        incomingRequests += demoIncomingRequest(journey)
        return CreateOfferedJourneyResult.Created(journey)
    }

    override fun cancelOfferedJourney(journeyId: String): CancelOfferedJourneyResult {
        val index = offeredJourneys.indexOfFirst { it.id == journeyId }
        val existing = offeredJourneys.getOrNull(index)
        if (existing?.status != OfferedJourneyStatus.OPEN) {
            return CancelOfferedJourneyResult.NotOpen(existing)
        }
        val cancelled = existing.copy(status = OfferedJourneyStatus.CANCELLED)
        offeredJourneys[index] = cancelled
        return CancelOfferedJourneyResult.Cancelled(cancelled)
    }

    override fun getIncomingSeatRequests(): List<IncomingSeatRequest> = incomingRequests.toList()

    override fun getIncomingSeatRequestForJourney(journeyId: String): IncomingSeatRequest? =
        incomingRequests.firstOrNull { it.offeredJourneyId == journeyId }

    override fun decideIncomingSeatRequest(
        requestId: String,
        decision: IncomingRequestDecision,
    ): DecideIncomingRequestResult {
        val requestIndex = incomingRequests.indexOfFirst { it.id == requestId }
        val request = incomingRequests.getOrNull(requestIndex)
        if (request?.status != IncomingSeatRequestStatus.PENDING) {
            return DecideIncomingRequestResult.AlreadyDecided(request)
        }

        val journeyIndex = offeredJourneys.indexOfFirst { it.id == request.offeredJourneyId }
        val journey = offeredJourneys.getOrNull(journeyIndex)
        if (journey?.status != OfferedJourneyStatus.OPEN) {
            return DecideIncomingRequestResult.RelatedOfferUnavailable(journey)
        }

        if (decision == IncomingRequestDecision.ACCEPT && journey.spareSeats < request.requestedSeats) {
            return DecideIncomingRequestResult.NotEnoughSeats(journey)
        }

        val decidedRequest = request.copy(
            status = if (decision == IncomingRequestDecision.ACCEPT) {
                IncomingSeatRequestStatus.ACCEPTED
            } else {
                IncomingSeatRequestStatus.DECLINED
            },
        )
        incomingRequests[requestIndex] = decidedRequest

        if (decision == IncomingRequestDecision.DECLINE) {
            return DecideIncomingRequestResult.Decided(decidedRequest, journey, confirmedTrip = null)
        }

        val confirmedJourney = journey.copy(
            status = OfferedJourneyStatus.CONFIRMED,
            spareSeats = journey.spareSeats - request.requestedSeats,
        )
        offeredJourneys[journeyIndex] = confirmedJourney
        val confirmedTrip = ConfirmedSharedTrip(
            id = "confirmed-${request.id}",
            offeredJourneyId = journey.id,
            incomingRequestId = request.id,
            driverName = "Sam",
            rider = request.rider,
            originArea = request.originArea,
            destinationArea = request.destinationArea,
            travelDate = journey.travelDate,
            approximatePickupMinutes = request.approximatePickupMinutes,
            pickupArea = request.pickupArea,
            requestedSeats = request.requestedSeats,
            remainingSpareSeats = confirmedJourney.spareSeats,
            sharedMiles = request.sharedMiles,
            contributionPence = request.contributionPence,
            serviceFeePence = request.serviceFeePence,
            serviceFeeResponsibility = request.serviceFeeResponsibility,
            circle = request.circle,
        )
        confirmedTrips += confirmedTrip
        return DecideIncomingRequestResult.Decided(decidedRequest, confirmedJourney, confirmedTrip)
    }

    override fun getConfirmedSharedTrips(): List<ConfirmedSharedTrip> = confirmedTrips.toList()

    private fun demoIncomingRequest(journey: OfferedJourney): IncomingSeatRequest {
        val sharedMiles = 17
        return IncomingSeatRequest(
            id = "incoming-${journey.id}",
            offeredJourneyId = journey.id,
            status = IncomingSeatRequestStatus.PENDING,
            rider = DemoRiderProfile(firstName = "Jamie", rating = 4.8, isDemoVerified = true),
            originArea = journey.originArea,
            destinationArea = journey.destinationArea,
            travelDate = journey.travelDate,
            approximatePickupMinutes = (journey.departureMinutes + 5).coerceAtMost(23 * 60 + 59),
            pickupArea = "${journey.originArea} town centre",
            walkMinutes = 6,
            detourMiles = 0.8,
            requestedSeats = 1,
            sharedMiles = sharedMiles,
            contributionPence = ContributionCalculator.calculatePence(sharedMiles),
            serviceFeePence = DEMO_SERVICE_FEE_PENCE,
            serviceFeeResponsibility = if (journey.circle == null) ServiceFeeResponsibility.RIDER else ServiceFeeResponsibility.HOST,
            circle = journey.circle,
        )
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

    private companion object {
        const val DEMO_SERVICE_FEE_PENCE = 50
    }

    private fun joinedCircle(circleId: String?): HostedCircle? =
        demoCircle.takeIf { circleId == it.id && circleId in joinedCircleIds }

    private fun requestKey(matchId: String, circleId: String?): String = "$matchId|${circleId.orEmpty()}"

    private val demoCircle = HostedCircle(
        id = "nottingham-live",
        name = "Nottingham Live — Event Travel",
        hostName = "Nottingham Live Demo Events",
        type = "Event travel",
        location = "Nottingham",
        status = "Open",
        summary = "Share routes with people heading to the same fictional event.",
        purpose = "Help people share planned journeys to the same fictional event.",
        destinationArea = "Nottingham city centre",
        eventDate = DemoTravelDate.EVENT_DAY,
        eventTime = DemoDepartureTimePolicy.eventDoorsDisplayName,
        illustrativeMembers = 128,
        illustrativeTrips = 34,
        serviceFeeCoveredByHost = true,
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
        serviceFeePence = DEMO_SERVICE_FEE_PENCE,
    )
}
