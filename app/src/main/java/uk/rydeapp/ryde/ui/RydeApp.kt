package uk.rydeapp.ryde.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import uk.rydeapp.ryde.R
import uk.rydeapp.ryde.app.RydeAppStateHolder
import uk.rydeapp.ryde.app.RydeAppUiState
import uk.rydeapp.ryde.data.AppMode
import uk.rydeapp.ryde.data.RydeAppComposition
import uk.rydeapp.ryde.data.RydeRepository
import uk.rydeapp.ryde.domain.model.OfferRideValidator
import uk.rydeapp.ryde.domain.model.RepeatJourneyPrefillResult
import uk.rydeapp.ryde.ui.components.DestinationIcon
import uk.rydeapp.ryde.ui.components.DestinationIconType
import uk.rydeapp.ryde.ui.destinations.ProfileScreen
import uk.rydeapp.ryde.ui.destinations.TripsScreen
import uk.rydeapp.ryde.ui.find.FindScreen
import uk.rydeapp.ryde.ui.home.HomeScreen
import uk.rydeapp.ryde.ui.offer.OfferScreen
import uk.rydeapp.ryde.ui.theme.RydeTheme

private enum class RydeDestination(@param:StringRes val labelRes: Int, val iconType: DestinationIconType) {
    HOME(R.string.nav_home, DestinationIconType.HOME),
    FIND(R.string.nav_find, DestinationIconType.FIND),
    OFFER(R.string.nav_offer, DestinationIconType.OFFER),
    TRIPS(R.string.nav_trips, DestinationIconType.TRIPS),
    PROFILE(R.string.nav_profile, DestinationIconType.PROFILE),
}

@Composable
fun RydeApp(
    repository: RydeRepository? = null,
    appMode: AppMode = AppMode.LOCAL_DEMO,
) {
    val appRepository = remember(repository, appMode) {
        RydeAppComposition.repository(appMode, repository)
    }
    val commandScope = rememberCoroutineScope()
    val controller = remember(appRepository, appMode, commandScope) {
        RydeAppStateHolder(appRepository, appMode, commandScope)
    }
    val uiState by controller.uiState.collectAsState()

    when (val state = uiState) {
        RydeAppUiState.Loading -> AppLoading()
        RydeAppUiState.SignedOut -> AppSignedOut()
        is RydeAppUiState.Error -> AppError(state.userMessage, controller::retry)
        is RydeAppUiState.Ready -> ReadyApp(state, controller, commandScope)
    }
}

@Composable
private fun ReadyApp(
    state: RydeAppUiState.Ready,
    controller: RydeAppStateHolder,
    commandScope: kotlinx.coroutines.CoroutineScope,
) {
    var selectedDestination by rememberSaveable { mutableStateOf(RydeDestination.HOME) }
    val stateHolder = rememberSaveableStateHolder()
    val snapshot = state.snapshot
    val openRepeatJourney: (String, String) -> Unit = { tripId, personId ->
        if (controller.prepareRepeatJourney(tripId, personId) is RepeatJourneyPrefillResult.Ready) {
            selectedDestination = RydeDestination.FIND
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
                    content = snapshot.homeContent,
                    circleMembership = snapshot.circleMembership,
                    onJoinCircle = {
                        commandScope.launch { controller.joinCircle(snapshot.homeContent.hostedCircle.id) }
                    },
                    onLeaveCircle = {
                        commandScope.launch { controller.leaveCircle(snapshot.homeContent.hostedCircle.id) }
                    },
                    onFindRide = { selectedDestination = RydeDestination.FIND },
                    onOfferRide = { selectedDestination = RydeDestination.OFFER },
                    modifier = Modifier.padding(innerPadding),
                )
                RydeDestination.FIND -> FindScreen(
                    content = snapshot.findRideContent,
                    joinedCircle = snapshot.circleMembership.takeIf { it.isJoined }?.circle,
                    repeatPrefill = state.repeatJourneyPrefill,
                    onSearch = controller::findRides,
                    requestForMatch = controller::seatRequestForMatch,
                    onCreateRequest = controller::createSeatRequest,
                    onOpenTrips = { selectedDestination = RydeDestination.TRIPS },
                    modifier = Modifier.padding(innerPadding),
                )
                RydeDestination.OFFER -> OfferScreen(
                    content = snapshot.offerRideContent,
                    joinedCircle = snapshot.circleMembership.takeIf { it.isJoined }?.circle,
                    offeredJourneys = snapshot.offeredJourneys,
                    validate = OfferRideValidator::validate,
                    onCreateOffer = controller::createOfferedJourney,
                    onOpenTrips = { selectedDestination = RydeDestination.TRIPS },
                    modifier = Modifier.padding(innerPadding),
                )
                RydeDestination.TRIPS -> TripsScreen(
                    requests = snapshot.seatRequests,
                    offeredJourneys = snapshot.offeredJourneys,
                    incomingRequests = snapshot.incomingRequests,
                    confirmedTrips = snapshot.confirmedTrips,
                    completedJourneyHistory = snapshot.completedJourneyHistory,
                    coordinationActivities = snapshot.coordinationActivities,
                    unreadCounts = snapshot.coordinationUnreadCounts,
                    onCancelRequest = { controller.cancelSeatRequest(it); Unit },
                    onCancelOffer = { controller.cancelOfferedJourney(it); Unit },
                    onDecideIncomingRequest = controller::decideIncomingSeatRequest,
                    onOpenConversation = controller::openConversation,
                    onSendMessage = controller::sendMessage,
                    onMarkActivityRead = { controller.markActivityRead(it); Unit },
                    onUpdateJourneyStatus = controller::updateJourneyStatus,
                    onCompleteJourney = controller::completeJourney,
                    onTravelTogetherAgain = openRepeatJourney,
                    modifier = Modifier.padding(innerPadding),
                )
                RydeDestination.PROFILE -> ProfileScreen(
                    content = snapshot.profileContent,
                    onSetPersonTrusted = controller::setPersonTrusted,
                    onTravelTogetherAgain = openRepeatJourney,
                    modifier = Modifier.padding(innerPadding),
                )
            }
        }
    }
}

@Composable
private fun AppLoading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator()
            Text("Loading Ryde…")
        }
    }
}

@Composable
private fun AppSignedOut() {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("You're signed out", style = MaterialTheme.typography.titleMedium)
            Text("Sign in will be available in a future update.", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun AppError(message: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(message, style = MaterialTheme.typography.bodyLarge)
            Button(onClick = onRetry) { Text("Try again") }
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun RydeAppPreview() {
    RydeTheme(darkTheme = false) { RydeApp() }
}
