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
import uk.rydeapp.ryde.ui.destinations.OfferScreen
import uk.rydeapp.ryde.ui.destinations.ProfileScreen
import uk.rydeapp.ryde.ui.destinations.TripsScreen
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
    var requestRevision by remember { mutableIntStateOf(0) }
    val stateHolder = rememberSaveableStateHolder()
    val homeContent = remember(appRepository) { appRepository.getHomeContent() }
    val findContent = remember(appRepository) { appRepository.getFindRideContent() }
    val requests = remember(appRepository, requestRevision) { appRepository.getSeatRequests() }

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
                    onFindRide = { selectedDestination = RydeDestination.FIND },
                    onOfferRide = { selectedDestination = RydeDestination.OFFER },
                    modifier = Modifier.padding(innerPadding),
                )
                RydeDestination.FIND -> FindScreen(
                    content = findContent,
                    onSearch = appRepository::findRides,
                    requestForMatch = appRepository::getSeatRequestForMatch,
                    onCreateRequest = { matchId, criteria ->
                        appRepository.createSeatRequest(matchId, criteria).also { requestRevision += 1 }
                    },
                    onOpenTrips = { selectedDestination = RydeDestination.TRIPS },
                    modifier = Modifier.padding(innerPadding),
                )
                RydeDestination.OFFER -> OfferScreen(Modifier.padding(innerPadding))
                RydeDestination.TRIPS -> TripsScreen(
                    requests = requests,
                    onCancelRequest = { requestId ->
                        appRepository.cancelSeatRequest(requestId)
                        requestRevision += 1
                    },
                    modifier = Modifier.padding(innerPadding),
                )
                RydeDestination.PROFILE -> ProfileScreen(
                    savedPlaces = homeContent.currentUser.savedPlaces,
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
