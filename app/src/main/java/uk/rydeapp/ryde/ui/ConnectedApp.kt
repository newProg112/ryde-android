package uk.rydeapp.ryde.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import uk.rydeapp.ryde.R
import uk.rydeapp.ryde.data.AccountCommandResult
import uk.rydeapp.ryde.data.AccountSession
import uk.rydeapp.ryde.data.connected.ConnectedJourneyCommandResult
import uk.rydeapp.ryde.data.connected.ConnectedJourneyLifecycle
import uk.rydeapp.ryde.data.connected.ConnectedRydeRepository
import uk.rydeapp.ryde.domain.model.ProfileContent
import uk.rydeapp.ryde.ui.account.ConnectedJourneyScreen
import uk.rydeapp.ryde.ui.account.ConnectedJourneySection
import uk.rydeapp.ryde.ui.account.ConnectedProfileScreen
import uk.rydeapp.ryde.ui.account.canCancelConnectedConfirmedSeat
import uk.rydeapp.ryde.ui.find.ConnectedFindScreen
import uk.rydeapp.ryde.ui.home.ConnectedHomeScreen
import uk.rydeapp.ryde.ui.home.connectedHomeJourneys
import uk.rydeapp.ryde.ui.trips.ConnectedTripsScreen
import uk.rydeapp.ryde.ui.trips.ConnectedConversationRoute
import uk.rydeapp.ryde.ui.trips.ConnectedMessageTarget
import uk.rydeapp.ryde.ui.trips.ConnectedTripDetailsScreen
import uk.rydeapp.ryde.ui.trips.connectedTripDetailsContent
import uk.rydeapp.ryde.ui.trips.connectedTripsContent
import uk.rydeapp.ryde.ui.trips.canDecideConnectedRequest
import uk.rydeapp.ryde.ui.offer.ConnectedOfferScreen

private data class ConnectedNavigation(
    val destination: RydeDestination = RydeDestination.HOME,
    val labSection: ConnectedJourneySection? = null,
    val detailJourneyId: String? = null,
    val conversationTripId: String? = null,
    val conversationName: String? = null,
)

/** The only new connected repository observation/command boundary; tabs receive data and callbacks. */
@Composable
internal fun ConnectedReadyApp(
    session: AccountSession.Authenticated,
    profile: ProfileContent,
    repository: ConnectedRydeRepository,
    onSave: suspend (String, String, String) -> AccountCommandResult?,
    onSignOut: suspend () -> AccountCommandResult?,
    currentTimeMillis: () -> Long = System::currentTimeMillis,
) {
    val snapshot by repository.journeyState.collectAsState()
    LaunchedEffect(session.accountId, repository) {
        repository.synchronizeConnectedJourneyLifecycles()
    }
    val resources = LocalResources.current
    // Include the owner in saved values: rememberSaveable inputs alone do not validate restored state.
    val navigationSaver = remember(session.accountId) {
        listSaver<ConnectedNavigation, String>(
            save = { listOf(session.accountId, it.destination.name, it.labSection?.name.orEmpty(), it.detailJourneyId.orEmpty(), it.conversationTripId.orEmpty(), it.conversationName.orEmpty()) },
            restore = {
                if (it[0] != session.accountId) ConnectedNavigation()
                else {
                    val detail = it.getOrNull(3)?.takeIf { id -> id.isNotBlank() && it[2].isEmpty() }
                    ConnectedNavigation(
                        RydeDestination.valueOf(it[1]),
                        it[2].takeIf(String::isNotEmpty)?.let(ConnectedJourneySection::valueOf),
                        detail,
                        it.getOrNull(4)?.takeIf { id -> id.isNotBlank() && detail != null },
                        it.getOrNull(5)?.takeIf(String::isNotBlank),
                    )
                }
            },
        )
    }
    var navigation by rememberSaveable(session.accountId, stateSaver = navigationSaver) {
        mutableStateOf(ConnectedNavigation())
    }
    val tabStateHolder = rememberSaveableStateHolder()
    var busy by remember { mutableStateOf(false) }
    var labBusy by remember { mutableStateOf(false) }
    var refreshRequired by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var createdVersion by rememberSaveable { mutableIntStateOf(0) }
    var lifecycleNowEpochMillis by remember(session.accountId) {
        mutableLongStateOf(currentTimeMillis())
    }
    // This scope survives tab changes and opening the Lab, and is disposed on account change/sign-out.
    val scope = rememberCoroutineScope()

    fun runCommand(action: suspend () -> String) {
        if (busy) return
        busy = true
        message = null
        scope.launch {
            try {
                message = action()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                refreshRequired = true
                message = resources.getString(R.string.connected_refresh_failed)
            } finally {
                busy = false
            }
        }
    }

    val refresh: () -> Unit = {
        // Time-derived lifecycle state must advance even when a server refresh
        // returns a structurally equal snapshot and StateFlow emits nothing.
        lifecycleNowEpochMillis = currentTimeMillis()
        runCommand {
            repository.refresh()
            lifecycleNowEpochMillis = currentTimeMillis()
            refreshRequired = false
            resources.getString(R.string.connected_refreshed)
        }
    }
    val openLab: (ConnectedJourneySection) -> Unit = { section ->
        if (!busy) navigation = navigation.copy(labSection = section, detailJourneyId = null, conversationTripId = null, conversationName = null)
    }
    val closeLab: () -> Unit = {
        if (!busy && !labBusy) navigation = navigation.copy(labSection = null)
    }
    val labSection = navigation.labSection
    BackHandler(enabled = labSection != null, onBack = closeLab)
    val closeDetails: () -> Unit = { navigation = navigation.copy(detailJourneyId = null, conversationTripId = null, conversationName = null) }
    val closeConversation: () -> Unit = { navigation = navigation.copy(conversationTripId = null, conversationName = null) }
    BackHandler(enabled = labSection == null && navigation.conversationTripId != null, onBack = closeConversation)
    BackHandler(enabled = labSection == null && navigation.detailJourneyId != null && navigation.conversationTripId == null, onBack = closeDetails)
    if (labSection != null) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            TextButton(onClick = closeLab, enabled = !busy && !labBusy) {
                Text(stringResource(R.string.connected_back_to_ryde))
            }
            androidx.compose.foundation.layout.Box(Modifier.weight(1f)) {
                ConnectedJourneyScreen(
                    session, profile, repository, onSave, onSignOut, refresh,
                    initialSection = labSection,
                    onBusyChanged = { labBusy = it },
                )
            }
        }
        return
    }

    val nowEpochMillis = lifecycleNowEpochMillis
    val discoveryJourneys = connectedHomeJourneys(snapshot, session.accountId, nowEpochMillis)
    val tripsContent = connectedTripsContent(snapshot, session.accountId, nowEpochMillis)
    val requestSeat: (String) -> Unit = { journeyId ->
        // Check repository truth and time again at the shared Home/Find command boundary.
        val canRequest = connectedHomeJourneys(
            repository.journeyState.value, session.accountId, currentTimeMillis(),
        ).any { it.journey.id == journeyId && (it.canRequest || it.canRerequest) }
        if (canRequest && !refreshRequired) runCommand {
            when (val result = repository.requestConnectedSeat(journeyId)) {
                ConnectedJourneyCommandResult.Success -> resources.getString(R.string.connected_requested)
                is ConnectedJourneyCommandResult.InvalidInput -> result.userMessage
                is ConnectedJourneyCommandResult.Failure -> {
                    // A write may have committed before refresh failed. Require a read before retrying.
                    refreshRequired = true
                    result.userMessage
                }
            }
        } else message = resources.getString(R.string.connected_journey_changed)
    }
    val cancelSeat: (String) -> Unit = { tripId ->
        val current = repository.journeyState.value
        val trip = current.confirmedTrips.firstOrNull { it.id == tripId }
        val canCancel = trip != null && canCancelConnectedConfirmedSeat(
            trip, session.accountId, currentTimeMillis(),
            current.journeys.firstOrNull { it.id == trip.journeyId },
        )
        if (!busy) {
            if (canCancel && !refreshRequired) runCommand {
                when (val result = repository.cancelConnectedConfirmedSeat(tripId)) {
                    ConnectedJourneyCommandResult.Success -> resources.getString(R.string.connected_trips_cancel_success)
                    is ConnectedJourneyCommandResult.InvalidInput -> result.userMessage
                    is ConnectedJourneyCommandResult.Failure -> {
                        refreshRequired = true
                        result.userMessage
                    }
                }
            } else message = resources.getString(R.string.connected_trips_changed)
        }
    }
    val cancelRequest: (String) -> Unit = { requestId ->
        val current = repository.journeyState.value
        val request = current.requests.firstOrNull { it.id == requestId }
        val journey = current.journeys.firstOrNull { it.id == request?.journeyId }
        val canCancel = request != null && request.id.isNotBlank() &&
            ConnectedJourneyLifecycle.canCancelRequest(request, journey, session.accountId)
        if (!busy) {
            if (canCancel && !refreshRequired) runCommand {
                when (val result = repository.cancelConnectedRequest(requestId)) {
                    ConnectedJourneyCommandResult.Success -> resources.getString(R.string.connected_withdraw_success)
                    is ConnectedJourneyCommandResult.InvalidInput -> result.userMessage
                    is ConnectedJourneyCommandResult.Failure -> {
                        refreshRequired = true
                        result.userMessage
                    }
                }
            } else message = resources.getString(R.string.connected_request_changed)
        }
    }
    fun driverResult(result: ConnectedJourneyCommandResult, successText: Int): String = when (result) {
        ConnectedJourneyCommandResult.Success -> resources.getString(successText)
        is ConnectedJourneyCommandResult.InvalidInput -> result.userMessage
        is ConnectedJourneyCommandResult.Failure -> {
            refreshRequired = true
            result.userMessage
        }
    }
    val createOffer: (String, String, String, String) -> Unit = { origin, destination, departure, seats ->
        if (!busy) {
            if (!refreshRequired) runCommand {
                val result = repository.createConnectedJourney(origin, destination, departure, seats)
                if (result == ConnectedJourneyCommandResult.Success) {
                    createdVersion++
                    navigation = navigation.copy(destination = RydeDestination.TRIPS)
                }
                driverResult(result, R.string.connected_offer_created)
            } else message = resources.getString(R.string.connected_trips_refresh_required)
        }
    }
    val decideRequest: (String, Boolean) -> Unit = { requestId, accept ->
        if (!busy) {
            val current = repository.journeyState.value
            val request = current.requests.firstOrNull { it.id == requestId }
            val journey = current.journeys.firstOrNull { it.id == request?.journeyId }
            if (!refreshRequired && request != null && canDecideConnectedRequest(
                    request, journey, session.accountId, accept, currentTimeMillis(),
                )) runCommand {
                driverResult(repository.decideConnectedRequest(requestId, accept),
                    if (accept) R.string.connected_accept_success else R.string.connected_decline_success)
            } else message = resources.getString(R.string.connected_driver_changed)
        }
    }
    val cancelJourney: (String) -> Unit = { journeyId ->
        if (!busy) {
            val journey = repository.journeyState.value.journeys.firstOrNull { it.id == journeyId }
            if (!refreshRequired && journey != null && ConnectedJourneyLifecycle.canCancelJourney(
                    journey, session.accountId, currentTimeMillis(),
                )) runCommand {
                driverResult(repository.cancelConnectedJourney(journeyId), R.string.connected_cancel_journey_success)
            } else message = resources.getString(R.string.connected_driver_changed)
        }
    }
    val completeJourney: (String) -> Unit = { journeyId ->
        if (!busy) {
            val journey = repository.journeyState.value.journeys.firstOrNull { it.id == journeyId }
            if (!refreshRequired && journey != null && ConnectedJourneyLifecycle.canCompleteJourney(
                    journey, session.accountId, currentTimeMillis(),
                )) runCommand {
                driverResult(repository.completeConnectedJourney(journeyId), R.string.connected_complete_journey_success)
            } else message = resources.getString(R.string.connected_driver_changed)
        }
    }
    val openJourney: (String) -> Unit = { id ->
        if (id.isNotBlank()) navigation = navigation.copy(detailJourneyId = id, labSection = null, conversationTripId = null, conversationName = null)
    }
    val openMessages: (ConnectedMessageTarget) -> Unit = { target ->
        if (target.tripId.isNotBlank() && navigation.detailJourneyId != null) {
            navigation = navigation.copy(conversationTripId = target.tripId, conversationName = target.otherDisplayName)
        }
    }
    RydeShell(navigation.destination, {
        navigation = navigation.copy(destination = it, detailJourneyId = null, conversationTripId = null, conversationName = null)
    }) { padding ->
        val detailJourneyId = navigation.detailJourneyId
        val conversationTripId = navigation.conversationTripId
        if (detailJourneyId != null && conversationTripId != null) {
            androidx.compose.runtime.key("${session.accountId}:messages:$conversationTripId") {
                ConnectedConversationRoute(
                    accountId = session.accountId,
                    target = ConnectedMessageTarget(conversationTripId, navigation.conversationName),
                    repository = repository,
                    onBack = closeConversation,
                    modifier = Modifier.padding(padding).consumeWindowInsets(padding),
                )
            }
            return@RydeShell
        }
        if (detailJourneyId != null) {
            tabStateHolder.SaveableStateProvider("${session.accountId}:details:$detailJourneyId") {
                ConnectedTripDetailsScreen(
                    content = connectedTripDetailsContent(snapshot, session.accountId, detailJourneyId,
                        discoveryJourneys, tripsContent, nowEpochMillis),
                    busy = busy, actionsEnabled = !refreshRequired, message = message,
                    onBack = closeDetails, onRefresh = refresh, onRequestSeat = requestSeat,
                    onDecideRequest = decideRequest, onCancelSeat = cancelSeat, onCancelJourney = cancelJourney,
                    onWithdrawRequest = cancelRequest,
                    onOpenMessages = openMessages,
                    onCompleteJourney = completeJourney,
                    modifier = Modifier.padding(padding),
                )
            }
            return@RydeShell
        }
        tabStateHolder.SaveableStateProvider("${session.accountId}:${navigation.destination.name}") {
            val modifier = Modifier.padding(padding)
            when (navigation.destination) {
                RydeDestination.HOME -> ConnectedHomeScreen(
                    displayName = session.displayName,
                    savedPlaces = profile.savedPlaces,
                    journeys = discoveryJourneys.take(3),
                    busy = busy,
                    requestsEnabled = !refreshRequired,
                    message = message,
                    onFind = { navigation = navigation.copy(destination = RydeDestination.FIND) },
                    onOffer = { navigation = navigation.copy(destination = RydeDestination.OFFER) },
                    onRefresh = refresh,
                    onRequestSeat = requestSeat,
                    onManageRequests = { navigation = navigation.copy(destination = RydeDestination.TRIPS) },
                    modifier = modifier,
                    onOpenJourney = openJourney,
                )
                RydeDestination.FIND -> ConnectedFindScreen(
                    journeys = discoveryJourneys,
                    busy = busy,
                    requestsEnabled = !refreshRequired,
                    message = message,
                    onRefresh = refresh,
                    onRequestSeat = requestSeat,
                    onManageRequests = { navigation = navigation.copy(destination = RydeDestination.TRIPS) },
                    modifier = modifier,
                    onOpenJourney = openJourney,
                )
                RydeDestination.OFFER -> ConnectedOfferScreen(
                    busy = busy, actionsEnabled = !refreshRequired, message = message, createdVersion = createdVersion,
                    onCreate = createOffer, onRefresh = refresh,
                    onManageOffers = { navigation = navigation.copy(destination = RydeDestination.TRIPS) }, modifier = modifier,
                )
                RydeDestination.TRIPS -> ConnectedTripsScreen(
                    content = tripsContent,
                    busy = busy, actionsEnabled = !refreshRequired, message = message,
                    onRefresh = refresh, onCancelSeat = cancelSeat, modifier = modifier,
                    onDecideRequest = decideRequest, onCancelJourney = cancelJourney,
                    onWithdrawRequest = cancelRequest,
                    onOpenJourney = openJourney,
                    onCompleteJourney = completeJourney,
                )
                RydeDestination.PROFILE -> ConnectedProfileScreen(
                    session, profile, onSave, onSignOut, modifier,
                    onOpenJourneyLab = { openLab(ConnectedJourneySection.PROFILE) },
                )
            }
        }
    }
}
