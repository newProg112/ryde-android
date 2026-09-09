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
import uk.rydeapp.ryde.domain.model.CompletedJourneyHistory
import uk.rydeapp.ryde.domain.model.DemoProfileIdentity
import uk.rydeapp.ryde.domain.model.DemoVehicle
import uk.rydeapp.ryde.domain.model.PersonalSafetyStatus
import uk.rydeapp.ryde.domain.model.ProfileContent
import uk.rydeapp.ryde.domain.model.RepeatJourneyMode
import uk.rydeapp.ryde.domain.model.RepeatJourneyPrefill
import uk.rydeapp.ryde.domain.model.RepeatJourneyPrefillResult
import uk.rydeapp.ryde.domain.model.SavePlaceResult
import uk.rydeapp.ryde.domain.model.SavedPlacePolicy
import uk.rydeapp.ryde.domain.model.TrustedPerson
import uk.rydeapp.ryde.domain.model.UpdateTrustedPersonResult
import uk.rydeapp.ryde.domain.model.CompleteJourneyResult
import uk.rydeapp.ryde.domain.model.ConversationId
import uk.rydeapp.ryde.domain.model.ConversationThread
import uk.rydeapp.ryde.domain.model.CoordinationActivityItem
import uk.rydeapp.ryde.domain.model.CoordinationActivityType
import uk.rydeapp.ryde.domain.model.CoordinationMessage
import uk.rydeapp.ryde.domain.model.CoordinationMessagePolicy
import uk.rydeapp.ryde.domain.model.CoordinationUnreadCounts
import uk.rydeapp.ryde.domain.model.GetConversationResult
import uk.rydeapp.ryde.domain.model.JourneyLifecyclePolicy
import uk.rydeapp.ryde.domain.model.JourneyLifecycleStatus
import uk.rydeapp.ryde.domain.model.JourneyParticipant
import uk.rydeapp.ryde.domain.model.JourneyParticipantRole
import uk.rydeapp.ryde.domain.model.JourneyStatusUpdateResult
import uk.rydeapp.ryde.domain.model.MessageRejectionReason
import uk.rydeapp.ryde.domain.model.MessagingUnavailableReason
import uk.rydeapp.ryde.domain.model.SendMessageResult

class FakeRydeRepository(
    jamieSafetyStatus: PersonalSafetyStatus = PersonalSafetyStatus.CLEAR,
) : RydeRepository {
    private val requestsByMatchId = linkedMapOf<String, SeatRequest>()
    private val offeredJourneys = mutableListOf<OfferedJourney>()
    private val incomingRequests = mutableListOf<IncomingSeatRequest>()
    private val confirmedTrips = mutableListOf<ConfirmedSharedTrip>()
    private val tripRecordsById = linkedMapOf<String, ConfirmedSharedTrip>()
    private val conversationsById = linkedMapOf<ConversationId, ConversationThread>()
    private val conversationIdByTripId = linkedMapOf<String, ConversationId>()
    private val coordinationActivities = mutableListOf<CoordinationActivityItem>()
    private var messageSequence = 3
    private var activitySequence = 0
    private val joinedCircleIds = mutableSetOf<String>()
    private val savedPlaces = mutableListOf(
        SavedPlace(label = "Home", area = "Sutton-in-Ashfield"),
        SavedPlace(label = "Work", area = "Nottingham"),
    )
    private val completedJourneyHistory = mutableListOf(
        CompletedJourneyHistory(
            id = "completed-jamie-nottingham-1",
            personId = "jamie-demo",
            personName = "Jamie",
            originArea = "Sutton-in-Ashfield",
            destinationArea = "Nottingham",
            completedLabel = "Completed last month",
        ),
    )
    private val peopleById = linkedMapOf(
        "jamie-demo" to TrustedPerson(
            id = "jamie-demo",
            firstName = "Jamie",
            initials = "JM",
            rating = 4.8,
            completedTripIds = setOf("completed-jamie-nottingham-1"),
            personallyTrusted = false,
            safetyStatus = jamieSafetyStatus,
        ),
        "casey-demo" to TrustedPerson(
            id = "casey-demo",
            firstName = "Casey",
            initials = "CK",
            rating = 4.7,
            completedTripIds = emptySet(),
            personallyTrusted = false,
        ),
    )

    override fun getHomeContent(): HomeContent {
        val sharedMiles = 17
        return HomeContent(
            currentUser = RydeUser(
                firstName = "Sam",
                savedPlaces = savedPlaces.toList(),
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
        savedPlaces = savedPlaces.toList(),
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
        savedPlaces = savedPlaces.toList(),
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
        tripRecordsById[confirmedTrip.id] = confirmedTrip
        seedCoordinationFor(confirmedTrip)
        return DecideIncomingRequestResult.Decided(decidedRequest, confirmedJourney, confirmedTrip)
    }

    override fun getConfirmedSharedTrips(): List<ConfirmedSharedTrip> = confirmedTrips.toList()

    override fun getConversationForConfirmedTrip(confirmedTripId: String): GetConversationResult {
        val trip = tripRecordsById[confirmedTripId]
            ?: return GetConversationResult.Unavailable(MessagingUnavailableReason.NO_CONFIRMED_TRIP)
        if (!participantCanMessage()) {
            return GetConversationResult.Unavailable(MessagingUnavailableReason.PARTICIPANT_BLOCKED_OR_REPORTED)
        }
        val conversationId = conversationIdByTripId[trip.id]
            ?: return GetConversationResult.Unavailable(MessagingUnavailableReason.NO_CONFIRMED_TRIP)
        val conversation = conversationsById.getValue(conversationId)
        val canSend = trip.lifecycleStatus !in setOf(
            JourneyLifecycleStatus.COMPLETED,
            JourneyLifecycleStatus.CANCELLED,
        )
        return GetConversationResult.Available(
            conversation.copy(
                participants = conversation.participants.toList(),
                messages = conversation.messages.toList(),
                canSendMessages = canSend,
            ),
        )
    }

    override fun sendMessage(conversationId: ConversationId, body: String): SendMessageResult {
        val conversation = conversationsById[conversationId]
            ?: return SendMessageResult.Rejected(MessageRejectionReason.CONVERSATION_NOT_FOUND)
        if (!participantCanMessage()) {
            return SendMessageResult.Rejected(MessageRejectionReason.PARTICIPANT_BLOCKED_OR_REPORTED)
        }
        val trip = tripRecordsById[conversation.confirmedTripId]
            ?: return SendMessageResult.Rejected(MessageRejectionReason.CONVERSATION_NOT_FOUND)
        when (trip.lifecycleStatus) {
            JourneyLifecycleStatus.CANCELLED ->
                return SendMessageResult.Rejected(MessageRejectionReason.JOURNEY_CANCELLED)
            JourneyLifecycleStatus.COMPLETED ->
                return SendMessageResult.Rejected(MessageRejectionReason.JOURNEY_COMPLETED)
            else -> Unit
        }
        CoordinationMessagePolicy.rejectionReason(body)?.let {
            return SendMessageResult.Rejected(it)
        }
        messageSequence += 1
        val message = CoordinationMessage(
            id = "message-${conversation.confirmedTripId}-$messageSequence",
            senderId = CURRENT_USER_ID,
            body = body.trim(),
            sentAtEpochMillis = 1_780_000_000_000L + messageSequence * 60_000L,
            displayTime = "08:${messageSequence.toString().padStart(2, '0')}",
            isRead = true,
        )
        val updated = conversation.copy(messages = conversation.messages + message)
        conversationsById[conversationId] = updated
        return SendMessageResult.Sent(message, updated.copy(messages = updated.messages.toList()))
    }

    override fun markConversationRead(conversationId: ConversationId): GetConversationResult {
        val conversation = conversationsById[conversationId]
            ?: return GetConversationResult.Unavailable(MessagingUnavailableReason.NO_CONFIRMED_TRIP)
        if (!participantCanMessage()) {
            return GetConversationResult.Unavailable(MessagingUnavailableReason.PARTICIPANT_BLOCKED_OR_REPORTED)
        }
        conversationsById[conversationId] = conversation.copy(
            messages = conversation.messages.map { message ->
                if (message.senderId != CURRENT_USER_ID) message.copy(isRead = true) else message
            },
        )
        coordinationActivities.indices.forEach { index ->
            val item = coordinationActivities[index]
            if (item.conversationId == conversationId && item.type == CoordinationActivityType.NEW_MESSAGE) {
                coordinationActivities[index] = item.copy(isRead = true)
            }
        }
        return getConversationForConfirmedTrip(conversation.confirmedTripId)
    }

    override fun getCoordinationUnreadCounts(): CoordinationUnreadCounts = CoordinationUnreadCounts(
        messages = if (participantCanMessage()) {
            conversationsById.values.sumOf { conversation ->
                conversation.messages.count { !it.isRead && it.senderId != CURRENT_USER_ID }
            }
        } else {
            0
        },
        activity = visibleCoordinationActivities().count { !it.isRead },
    )

    override fun getCoordinationActivityItems(): List<CoordinationActivityItem> =
        visibleCoordinationActivities().toList()

    override fun markCoordinationActivityRead(activityId: String): Boolean {
        val index = coordinationActivities.indexOfFirst { it.id == activityId }
        if (index < 0) return false
        coordinationActivities[index] = coordinationActivities[index].copy(isRead = true)
        return true
    }

    override fun updateConfirmedJourneyStatus(
        confirmedTripId: String,
        status: JourneyLifecycleStatus,
    ): JourneyStatusUpdateResult {
        val trip = tripRecordsById[confirmedTripId]
            ?: return JourneyStatusUpdateResult.Rejected(null, status)
        if (!JourneyLifecyclePolicy.canTransition(trip.lifecycleStatus, status)) {
            return JourneyStatusUpdateResult.Rejected(trip.lifecycleStatus, status)
        }
        val updated = trip.copy(lifecycleStatus = status)
        tripRecordsById[confirmedTripId] = updated
        val activeIndex = confirmedTrips.indexOfFirst { it.id == confirmedTripId }
        if (activeIndex >= 0) confirmedTrips[activeIndex] = updated

        when (status) {
            JourneyLifecycleStatus.DRIVER_EN_ROUTE -> addActivity(
                trip = updated,
                type = CoordinationActivityType.DRIVER_EN_ROUTE,
                title = "Driver en route",
                body = "Sam is heading to the public pickup point.",
                displayTime = "08:10",
            )
            JourneyLifecycleStatus.READY_AT_PICKUP -> addActivity(
                trip = updated,
                type = CoordinationActivityType.READY_AT_PICKUP,
                title = "Ready at pickup",
                body = "Sam is ready at the agreed public pickup point.",
                displayTime = "08:15",
            )
            JourneyLifecycleStatus.COMPLETED -> archiveCompletedJourney(updated)
            JourneyLifecycleStatus.CANCELLED -> confirmedTrips.removeAll { it.id == confirmedTripId }
            JourneyLifecycleStatus.CONFIRMED,
            JourneyLifecycleStatus.JOURNEY_UNDERWAY,
            -> Unit
        }
        return JourneyStatusUpdateResult.Updated(updated)
    }

    override fun completeJourney(confirmedTripId: String): CompleteJourneyResult {
        completedJourneyHistory.firstOrNull { it.id == confirmedTripId }?.let {
            return CompleteJourneyResult.AlreadyCompleted(it)
        }
        return when (
            val result = updateConfirmedJourneyStatus(
                confirmedTripId,
                JourneyLifecycleStatus.COMPLETED,
            )
        ) {
            is JourneyStatusUpdateResult.Updated -> CompleteJourneyResult.Completed(
                completedJourneyHistory.first { it.id == result.trip.id },
            )
            is JourneyStatusUpdateResult.Rejected -> CompleteJourneyResult.Rejected(result.currentStatus)
        }
    }

    override fun getProfileContent() = ProfileContent(
        identity = DemoProfileIdentity(
            firstName = "Sam",
            initials = "SR",
            memberSince = "Member since spring 2026",
            completedSharedJourneys = completedJourneyHistory.size,
            reliabilityPercent = 96,
            rating = 4.9,
            vehicle = DemoVehicle(description = "5-door hatchback", colour = "Blue"),
        ),
        savedPlaces = getSavedPlaces(),
        people = peopleById.values.filter { it.completedTripIds.isNotEmpty() },
        completedJourneys = getCompletedJourneyHistory(),
    )

    override fun getSavedPlaces(): List<SavedPlace> = savedPlaces.toList()

    override fun savePlace(place: SavedPlace): SavePlaceResult {
        if (!SavedPlacePolicy.isBroadDisplayArea(place.area)) return SavePlaceResult.PrivateOrInvalidArea
        val normalized = place.copy(label = place.label.trim(), area = place.area.trim())
        val existingIndex = savedPlaces.indexOfFirst { it.label.equals(normalized.label, ignoreCase = true) }
        if (existingIndex >= 0) savedPlaces[existingIndex] = normalized else savedPlaces += normalized
        return SavePlaceResult.Saved(normalized)
    }

    override fun getCompletedJourneyHistory(): List<CompletedJourneyHistory> = completedJourneyHistory.toList()

    override fun getTrustedPeople(): List<TrustedPerson> = peopleById.values.filter { it.isTrusted }

    override fun setPersonTrusted(personId: String, trusted: Boolean): UpdateTrustedPersonResult {
        val person = peopleById[personId] ?: return UpdateTrustedPersonResult.NotEligible(null)
        if (trusted && !person.isEligibleForTrust) return UpdateTrustedPersonResult.NotEligible(person)
        val updated = person.copy(
            personallyTrusted = trusted,
            safetyStatus = if (trusted) PersonalSafetyStatus.CLEAR else person.safetyStatus,
        )
        peopleById[personId] = updated
        return UpdateTrustedPersonResult.Updated(updated)
    }

    override fun prepareRepeatJourney(
        completedTripId: String,
        personId: String,
        mode: RepeatJourneyMode,
    ): RepeatJourneyPrefillResult {
        val trip = completedJourneyHistory.firstOrNull { it.id == completedTripId }
            ?: return RepeatJourneyPrefillResult.CompletedTripNotFound
        if (trip.personId != personId) return RepeatJourneyPrefillResult.PersonNotOnCompletedTrip
        return RepeatJourneyPrefillResult.Ready(
            RepeatJourneyPrefill(
                requestId = "repeat-${trip.id}-${mode.name.lowercase()}",
                mode = mode,
                sourceCompletedTripId = trip.id,
                preferredPersonId = personId,
                preferredPersonName = trip.personName,
                originArea = trip.originArea,
                destinationArea = trip.destinationArea,
            ),
        )
    }

    private fun seedCoordinationFor(trip: ConfirmedSharedTrip) {
        val conversationId = ConversationId("conversation-${trip.id}")
        val messages = listOf(
            CoordinationMessage(
                id = "message-${trip.id}-1",
                senderId = JAMIE_ID,
                body = "Hi Sam — I’ll meet you at the agreed public pickup point.",
                sentAtEpochMillis = 1_780_000_060_000L,
                displayTime = "08:01",
                isRead = true,
            ),
            CoordinationMessage(
                id = "message-${trip.id}-2",
                senderId = CURRENT_USER_ID,
                body = "Great — I’ll look for you at the public pickup point.",
                sentAtEpochMillis = 1_780_000_120_000L,
                displayTime = "08:02",
                isRead = true,
            ),
            CoordinationMessage(
                id = "message-${trip.id}-3",
                senderId = JAMIE_ID,
                body = "Thanks — I’m on my way.",
                sentAtEpochMillis = 1_780_000_180_000L,
                displayTime = "08:03",
                isRead = false,
            ),
        )
        conversationsById[conversationId] = ConversationThread(
            id = conversationId,
            confirmedTripId = trip.id,
            journeyLabel = "${trip.originArea} to ${trip.destinationArea}",
            participants = listOf(
                JourneyParticipant(CURRENT_USER_ID, "Sam", JourneyParticipantRole.DRIVER),
                JourneyParticipant(JAMIE_ID, trip.rider.firstName, JourneyParticipantRole.RIDER),
            ),
            messages = messages,
            canSendMessages = true,
        )
        conversationIdByTripId[trip.id] = conversationId
        addActivity(
            trip = trip,
            type = CoordinationActivityType.REQUEST_ACCEPTED,
            title = "Request accepted",
            body = "Your shared trip with Jamie is confirmed.",
            displayTime = "08:00",
        )
        addActivity(
            trip = trip,
            type = CoordinationActivityType.NEW_MESSAGE,
            title = "New message from Jamie",
            body = "Thanks — I’m on my way.",
            displayTime = "08:03",
            conversationId = conversationId,
        )
    }

    private fun participantCanMessage(): Boolean =
        peopleById[JAMIE_ID]?.safetyStatus == PersonalSafetyStatus.CLEAR

    private fun visibleCoordinationActivities(): List<CoordinationActivityItem> =
        if (participantCanMessage()) {
            coordinationActivities
        } else {
            coordinationActivities.filter { it.type != CoordinationActivityType.NEW_MESSAGE }
        }

    private fun addActivity(
        trip: ConfirmedSharedTrip,
        type: CoordinationActivityType,
        title: String,
        body: String,
        displayTime: String,
        conversationId: ConversationId? = null,
    ) {
        activitySequence += 1
        coordinationActivities.add(
            index = 0,
            element = CoordinationActivityItem(
                id = "activity-${trip.id}-$activitySequence",
                confirmedTripId = trip.id,
                conversationId = conversationId,
                type = type,
                title = title,
                body = body,
                displayTime = displayTime,
                isRead = false,
            ),
        )
    }

    private fun archiveCompletedJourney(trip: ConfirmedSharedTrip) {
        if (completedJourneyHistory.none { it.id == trip.id }) {
            completedJourneyHistory += CompletedJourneyHistory(
                id = trip.id,
                personId = JAMIE_ID,
                personName = trip.rider.firstName,
                originArea = trip.originArea,
                destinationArea = trip.destinationArea,
                completedLabel = "Completed just now",
            )
        }
        confirmedTrips.removeAll { it.id == trip.id }
        peopleById[JAMIE_ID]?.let { person ->
            peopleById[JAMIE_ID] = person.copy(completedTripIds = person.completedTripIds + trip.id)
        }
        addActivity(
            trip = trip,
            type = CoordinationActivityType.JOURNEY_COMPLETED,
            title = "Journey completed",
            body = "Your shared journey with Jamie is now in trip history.",
            displayTime = "08:55",
        )
    }

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
        const val CURRENT_USER_ID = "sam-demo"
        const val JAMIE_ID = "jamie-demo"
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
