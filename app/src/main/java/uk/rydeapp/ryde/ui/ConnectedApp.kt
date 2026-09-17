package uk.rydeapp.ryde.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import uk.rydeapp.ryde.R
import uk.rydeapp.ryde.data.AccountCommandResult
import uk.rydeapp.ryde.data.AccountSession
import uk.rydeapp.ryde.data.connected.ConnectedJourneyCommandResult
import uk.rydeapp.ryde.data.connected.ConnectedRydeRepository
import uk.rydeapp.ryde.domain.model.ProfileContent
import uk.rydeapp.ryde.ui.account.ConnectedJourneyScreen
import uk.rydeapp.ryde.ui.account.ConnectedJourneySection
import uk.rydeapp.ryde.ui.account.ConnectedProfileScreen
import uk.rydeapp.ryde.ui.components.InfoCard
import uk.rydeapp.ryde.ui.find.ConnectedFindScreen
import uk.rydeapp.ryde.ui.home.ConnectedHomeScreen
import uk.rydeapp.ryde.ui.home.connectedHomeJourneys

private data class ConnectedNavigation(
    val destination: RydeDestination = RydeDestination.HOME,
    val labSection: ConnectedJourneySection? = null,
)

/** The only new connected repository observation/command boundary; tabs receive data and callbacks. */
@Composable
internal fun ConnectedReadyApp(
    session: AccountSession.Authenticated,
    profile: ProfileContent,
    repository: ConnectedRydeRepository,
    onSave: suspend (String, String, String) -> AccountCommandResult?,
    onSignOut: suspend () -> AccountCommandResult?,
) {
    val snapshot by repository.journeyState.collectAsState()
    val resources = LocalResources.current
    // Include the owner in saved values: rememberSaveable inputs alone do not validate restored state.
    val navigationSaver = remember(session.accountId) {
        listSaver<ConnectedNavigation, String>(
            save = { listOf(session.accountId, it.destination.name, it.labSection?.name.orEmpty()) },
            restore = {
                if (it[0] != session.accountId) ConnectedNavigation()
                else ConnectedNavigation(
                    RydeDestination.valueOf(it[1]),
                    it[2].takeIf(String::isNotEmpty)?.let(ConnectedJourneySection::valueOf),
                )
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
        runCommand {
            repository.refresh()
            refreshRequired = false
            resources.getString(R.string.connected_refreshed)
        }
    }
    val openLab: (ConnectedJourneySection) -> Unit = { section ->
        if (!busy) navigation = navigation.copy(labSection = section)
    }
    val closeLab: () -> Unit = {
        if (!busy && !labBusy) navigation = navigation.copy(labSection = null)
    }
    val labSection = navigation.labSection
    BackHandler(enabled = labSection != null, onBack = closeLab)
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

    val discoveryJourneys = connectedHomeJourneys(snapshot, session.accountId, System.currentTimeMillis())
    val requestSeat: (String) -> Unit = { journeyId ->
        // Check repository truth and time again at the shared Home/Find command boundary.
        val canRequest = connectedHomeJourneys(
            repository.journeyState.value, session.accountId, System.currentTimeMillis(),
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
    RydeShell(navigation.destination, { navigation = navigation.copy(destination = it) }) { padding ->
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
                    onManageRequests = { openLab(ConnectedJourneySection.YOUR_REQUESTS) },
                    modifier = modifier,
                )
                RydeDestination.FIND -> ConnectedFindScreen(
                    journeys = discoveryJourneys,
                    busy = busy,
                    requestsEnabled = !refreshRequired,
                    message = message,
                    onRefresh = refresh,
                    onRequestSeat = requestSeat,
                    modifier = modifier,
                )
                RydeDestination.OFFER -> ConnectedTransition(
                    R.string.connected_offer_title, R.string.connected_offer_body, R.string.connected_open_offer_lab,
                    onAction = { openLab(ConnectedJourneySection.OFFER) }, enabled = !busy, modifier = modifier,
                )
                RydeDestination.TRIPS -> ConnectedTransition(
                    R.string.connected_trips_title, R.string.connected_trips_body, R.string.connected_open_trips_lab,
                    onAction = { openLab(ConnectedJourneySection.TRIPS) }, enabled = !busy, modifier = modifier,
                )
                RydeDestination.PROFILE -> ConnectedProfileScreen(
                    session, profile, onSave, onSignOut, modifier,
                    onOpenJourneyLab = { openLab(ConnectedJourneySection.PROFILE) },
                )
            }
        }
    }
}

@Composable
private fun ConnectedTransition(
    title: Int,
    body: Int,
    action: Int,
    onAction: () -> Unit,
    modifier: Modifier,
    enabled: Boolean = true,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(stringResource(title), style = MaterialTheme.typography.headlineSmall)
        InfoCard(stringResource(R.string.connected_transition_heading), stringResource(body))
        Button(onClick = onAction, enabled = enabled) { Text(stringResource(action)) }
    }
}
