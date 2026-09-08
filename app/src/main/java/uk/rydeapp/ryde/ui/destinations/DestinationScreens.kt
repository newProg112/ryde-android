package uk.rydeapp.ryde.ui.destinations

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import uk.rydeapp.ryde.R
import uk.rydeapp.ryde.domain.model.SavedPlace
import uk.rydeapp.ryde.ui.components.DestinationIcon
import uk.rydeapp.ryde.ui.components.DestinationIconType
import uk.rydeapp.ryde.ui.components.InfoCard
import uk.rydeapp.ryde.ui.components.LabelPill
import uk.rydeapp.ryde.ui.components.RouteMark
import uk.rydeapp.ryde.ui.theme.Mint
import uk.rydeapp.ryde.ui.theme.RydeTheme

@Composable
fun FindScreen(modifier: Modifier = Modifier) {
    DestinationShell(
        headingRes = R.string.find_heading,
        bodyRes = R.string.find_body,
        iconType = DestinationIconType.FIND,
        modifier = modifier,
    ) {
        InfoCard(
            title = stringResource(R.string.find_info_title),
            body = stringResource(R.string.find_info_body),
        )
    }
}

@Composable
fun OfferScreen(modifier: Modifier = Modifier) {
    DestinationShell(
        headingRes = R.string.offer_heading,
        bodyRes = R.string.offer_body,
        iconType = DestinationIconType.OFFER,
        modifier = modifier,
    ) {
        InfoCard(
            title = stringResource(R.string.offer_info_title),
            body = stringResource(R.string.offer_info_body),
        )
    }
}

@Composable
fun TripsScreen(modifier: Modifier = Modifier) {
    DestinationShell(
        headingRes = R.string.trips_heading,
        bodyRes = R.string.trips_body,
        iconType = DestinationIconType.TRIPS,
        modifier = modifier,
    ) {
        EmptyStateCard()
    }
}

@Composable
fun ProfileScreen(
    savedPlaces: List<SavedPlace>,
    modifier: Modifier = Modifier,
) {
    DestinationShell(
        headingRes = R.string.profile_heading,
        bodyRes = R.string.profile_body,
        iconType = DestinationIconType.PROFILE,
        modifier = modifier,
    ) {
        Text(stringResource(R.string.saved_places), style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            savedPlaces.forEach { place -> SavedPlaceCard(place, Modifier.weight(1f)) }
        }
        InfoCard(
            title = stringResource(R.string.trusted_connections),
            body = stringResource(R.string.trusted_connections_body),
        )
    }
}

@Composable
private fun DestinationShell(
    @StringRes headingRes: Int,
    @StringRes bodyRes: Int,
    iconType: DestinationIconType,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RouteMark(stringResource(R.string.route_mark_description), modifier = Modifier.size(34.dp))
                Spacer(Modifier.weight(1f))
                LabelPill(stringResource(R.string.phase_one_label))
            }
        }
        item {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                DestinationIcon(
                    type = iconType,
                    contentDescription = stringResource(headingRes),
                    modifier = Modifier.padding(14.dp),
                )
            }
        }
        item {
            Column {
                Text(stringResource(headingRes), style = MaterialTheme.typography.headlineLarge)
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(bodyRes),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
        item { content() }
    }
}

@Composable
private fun EmptyStateCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val description = stringResource(R.string.trips_info_title)
            Canvas(
                modifier = Modifier
                    .size(48.dp)
                    .semantics { contentDescription = description },
            ) {
                drawCircle(Mint.copy(alpha = .22f))
                drawCircle(Mint, radius = size.minDimension * .10f)
            }
            Spacer(Modifier.height(14.dp))
            Text(
                stringResource(R.string.trips_info_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(5.dp))
            Text(
                stringResource(R.string.trips_info_body),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun SavedPlaceCard(place: SavedPlace, modifier: Modifier = Modifier) {
    val placeDescription = stringResource(
        R.string.saved_place_description,
        place.label,
        place.area,
    )
    Card(
        modifier = modifier.semantics { contentDescription = placeDescription },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(15.dp)) {
            Text(place.label, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(3.dp))
            Text(
                place.area,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun OfferScreenPreview() {
    RydeTheme(darkTheme = false) { OfferScreen() }
}
