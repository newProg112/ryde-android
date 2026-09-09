package uk.rydeapp.ryde.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import uk.rydeapp.ryde.R
import uk.rydeapp.ryde.data.FakeRydeRepository
import uk.rydeapp.ryde.data.RydeRepository
import uk.rydeapp.ryde.ui.components.DestinationIcon
import uk.rydeapp.ryde.ui.components.DestinationIconType
import uk.rydeapp.ryde.ui.destinations.ProfileScreen
import uk.rydeapp.ryde.ui.destinations.TripsScreen
import uk.rydeapp.ryde.ui.offer.OfferScreen
import uk.rydeapp.ryde.domain.model.OfferRideValidator
import uk.rydeapp.ryde.domain.model.RepeatJourneyPrefill
import uk.rydeapp.ryde.domain.model.RepeatJourneyPrefillResult
import uk.rydeapp.ryde.domain.model.GetConversationResult
import uk.rydeapp.ryde.ui.home.HomeScreen
import uk.rydeapp.ryde.ui.find.FindScreen
import uk.rydeapp.ryde.ui.theme.RydeTheme

private enum class RydeDestination(
    @param:StringRes val labelRes: Int,
    val iconType: DestinationIconType,
) {
    HOME(R.string.nav_home, DestinationIconType.HOME),
    FIND(R.string.nav_find, DestinationIconType.FIND),
    OFFER(R.string.nav_offer, DestinationIconType.OFFER),
    TRIPS(R.string.nav_trips, DestinationIconType.TRIPS),
    PROFILE(R.string.nav_profile, DestinationIconType.PROFILE),
}

@Composable
fun RydeApp(repository: RydeRepository? = null) {
    val appRepository = remember(repository) { repository ?: FakeRydeRepository() }
    var selectedDestination by rememberSaveable { mutableStateOf(RydeDestination.HOME) }
    var tripRevision by remember { mutableIntStateOf(0) }
    var circleRevision by remember { mutableIntStateOf(0) }
    var profileRevision by remember { mutableIntStateOf(0) }
    var repeatPrefill by remember { mutableStateOf<RepeatJourneyPrefill?>(null) }
    val stateHolder = rememberSaveableStateHolder()
    val homeContent = remember(appRepository) { appRepository.getHomeContent() }
    val findContent = remember(appRepository) { appRepository.getFindRideContent() }
    val offerContent = remember(appRepository) { appRepository.getOfferRideContent() }
    val circleMembership = remember(appRepository, circleRevision) {
        appRepository.getCircleMembership(homeContent.hostedCircle.id)!!
    }
    val requests = remember(appRepository, tripRevision) { appRepository.getSeatRequests() }
    val offeredJourneys = remember(appRepository, tripRevision) { appRepository.getOfferedJourneys() }
    val incomingRequests = remember(appRepository, tripRevision) { appRepository.getIncomingSeatRequests() }
    val confirmedTrips = remember(appRepository, tripRevision) { appRepository.getConfirmedSharedTrips() }
    val completedJourneyHistory = remember(appRepository, tripRevision, profileRevision) {
        appRepository.getCompletedJourneyHistory()
    }
    val coordinationActivities = remember(appRepository, tripRevision) {
        appRepository.getCoordinationActivityItems()
    }
    val coordinationUnreadCounts = remember(appRepository, tripRevision) {
        appRepository.getCoordinationUnreadCounts()
    }
    val profileContent = remember(appRepository, profileRevision) { appRepository.getProfileContent() }
    val openRepeatJourney: (String, String) -> Unit = { tripId, personId ->
        when (val result = appRepository.prepareRepeatJourney(tripId, personId)) {
            is RepeatJourneyPrefillResult.Ready -> {
                repeatPrefill = result.prefill
                selectedDestination = RydeDestination.FIND
            }
            RepeatJourneyPrefillResult.CompletedTripNotFound,
            RepeatJourneyPrefillResult.PersonNotOnCompletedTrip -> Unit
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                RydeDestination.entries.forEach { destination ->
                    val label = stringResource(destination.labelRes)
                    NavigationBarItem(
                        selected = selectedDestination == destination,
                        onClick = { selectedDestination = destination },
                        icon = {
                            DestinationIcon(
                                type = destination.iconType,
                                contentDescription = stringResource(R.string.nav_description, label),
                            )
                        },
                        label = { Text(label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        stateHolder.SaveableStateProvider(selectedDestination.name) {
            when (selectedDestination) {
                RydeDestination.HOME -> HomeScreen(
                    content = homeContent,
                    circleMembership = circleMembership,
                    onJoinCircle = {
                        appRepository.joinCircle(homeContent.hostedCircle.id)
                        circleRevision += 1
                    },
                    onLeaveCircle = {
                        appRepository.leaveCircle(homeContent.hostedCircle.id)
                        circleRevision += 1
                    },
                    onFindRide = { selectedDestination = RydeDestination.FIND },
                    onOfferRide = { selectedDestination = RydeDestination.OFFER },
                    modifier = Modifier.padding(innerPadding),
                )
                RydeDestination.FIND -> FindScreen(
                    content = findContent,
                    joinedCircle = circleMembership.takeIf { it.isJoined }?.circle,
                    repeatPrefill = repeatPrefill,
                    onSearch = appRepository::findRides,
                    requestForMatch = appRepository::getSeatRequestForMatch,
                    onCreateRequest = { matchId, criteria ->
                        appRepository.createSeatRequest(matchId, criteria).also { tripRevision += 1 }
                    },
                    onOpenTrips = { selectedDestination = RydeDestination.TRIPS },
                    modifier = Modifier.padding(innerPadding),
                )
                RydeDestination.OFFER -> OfferScreen(
                    content = offerContent,
                    joinedCircle = circleMembership.takeIf { it.isJoined }?.circle,
                    offeredJourneys = offeredJourneys,
                    validate = OfferRideValidator::validate,
                    onCreateOffer = { criteria ->
                        appRepository.createOfferedJourney(criteria).also { tripRevision += 1 }
                    },
                    onOpenTrips = { selectedDestination = RydeDestination.TRIPS },
                    modifier = Modifier.padding(innerPadding),
                )
                RydeDestination.TRIPS -> TripsScreen(
                    requests = requests,
                    offeredJourneys = offeredJourneys,
                    incomingRequests = incomingRequests,
                    confirmedTrips = confirmedTrips,
                    completedJourneyHistory = completedJourneyHistory,
                    coordinationActivities = coordinationActivities,
                    unreadCounts = coordinationUnreadCounts,
                    onCancelRequest = { requestId ->
                        appRepository.cancelSeatRequest(requestId)
                        tripRevision += 1
                    },
                    onCancelOffer = { journeyId ->
                        appRepository.cancelOfferedJourney(journeyId)
                        tripRevision += 1
                    },
                    onDecideIncomingRequest = { requestId, decision ->
                        appRepository.decideIncomingSeatRequest(requestId, decision).also {
                            tripRevision += 1
                        }
                    },
                    onOpenConversation = { tripId ->
                        when (val result = appRepository.getConversationForConfirmedTrip(tripId)) {
                            is GetConversationResult.Available ->
                                appRepository.markConversationRead(result.conversation.id)
                            is GetConversationResult.Unavailable -> result
                        }.also { tripRevision += 1 }
                    },
                    onSendMessage = { conversationId, body ->
                        appRepository.sendMessage(conversationId, body).also { tripRevision += 1 }
                    },
                    onMarkActivityRead = { activityId ->
                        appRepository.markCoordinationActivityRead(activityId)
                        tripRevision += 1
                    },
                    onUpdateJourneyStatus = { tripId, status ->
                        appRepository.updateConfirmedJourneyStatus(tripId, status).also { tripRevision += 1 }
                    },
                    onCompleteJourney = { tripId ->
                        appRepository.completeJourney(tripId).also {
                            tripRevision += 1
                            profileRevision += 1
                        }
                    },
                    onTravelTogetherAgain = openRepeatJourney,
                    modifier = Modifier.padding(innerPadding),
                )
                RydeDestination.PROFILE -> ProfileScreen(
                    content = profileContent,
                    onSetPersonTrusted = { personId, trusted ->
                        appRepository.setPersonTrusted(personId, trusted).also { profileRevision += 1 }
                    },
                    onTravelTogetherAgain = openRepeatJourney,
                    modifier = Modifier.padding(innerPadding),
                )
            }
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun RydeAppPreview() {
    RydeTheme(darkTheme = false) {
        RydeApp()
    }
}
