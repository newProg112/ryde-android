package uk.rydeapp.ryde.ui.circle

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import uk.rydeapp.ryde.domain.model.CircleMembership
import uk.rydeapp.ryde.ui.components.LabelPill
import uk.rydeapp.ryde.ui.components.RouteMark

@Composable
fun CircleDetailScreen(
    membership: CircleMembership,
    onBack: () -> Unit,
    onJoin: () -> Unit,
    onLeave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val circle = membership.circle
    var confirmation by rememberSaveable { mutableStateOf<CircleAction?>(null) }
    BackHandler(onBack = onBack)

    confirmation?.let { action ->
        AlertDialog(
            onDismissRequest = { confirmation = null },
            title = { Text(if (action == CircleAction.JOIN) "Join this fictional Circle?" else "Leave this Circle?") },
            text = {
                Text(
                    if (action == CircleAction.JOIN) {
                        "Joining only unlocks Circle choices in this app session. It does not create an account, contact the host or share your location."
                    } else {
                        "Circle choices will be removed. Existing Circle-labelled demo trip history will remain."
                    },
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (action == CircleAction.JOIN) onJoin() else onLeave()
                    confirmation = null
                }) { Text(if (action == CircleAction.JOIN) "Join Circle" else "Leave Circle") }
            },
            dismissButton = {
                OutlinedButton(onClick = { confirmation = null }) { Text("Not now") }
            },
        )
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, top = 14.dp, end = 18.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            OutlinedButton(onClick = onBack) { Text("← Back to Home") }
            Spacer(Modifier.height(14.dp))
            Row {
                RouteMark("Ryde Circle route mark", Modifier.size(40.dp))
                Spacer(Modifier.weight(1f))
                LabelPill(if (membership.isJoined) "Joined" else circle.status)
            }
            Spacer(Modifier.height(12.dp))
            Text(circle.name, style = MaterialTheme.typography.headlineMedium)
            Text("Hosted by ${circle.hostName}", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium)
            Text("${circle.type} · ${circle.location}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("Service fee covered by host", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("You pay only the shared-distance contribution. The £0.50 Ryde service fee is shown, but excluded from your total; the driver receives the same contribution.")
                }
            }
        }
        item {
            CircleCard("About this Circle") {
                Text(circle.purpose)
                CircleRow("Broad destination", circle.destinationArea)
                CircleRow("Fictional event date", circle.eventDate.displayName)
                CircleRow("Fictional event time", circle.eventTime)
                CircleRow("Illustrative members", circle.illustrativeMembers.toString())
                CircleRow("Illustrative shared trips", circle.illustrativeTrips.toString())
                Text("Member and trip figures are fictional illustrative data.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            CircleCard("Benefits") {
                Benefit("Find people travelling to the same event")
                Benefit("Contributions remain based on shared distance")
                Benefit("Host covers the Ryde service fee")
                Benefit("Home addresses and exact/live locations are not shown")
            }
        }
        item {
            CircleCard("Community expectations") {
                Benefit("Respectful conduct")
                Benefit("Only offer journeys already planned")
                Benefit("Public pickup points first")
                Benefit("Report and block tools would be required in a real service; they remain deferred in this demo")
            }
        }
        item {
            Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                Text(
                    "Fictional local-demo Circle. No real organisation, event or partnership exists, and no real organisation hosts or sponsors this Circle.",
                    modifier = Modifier.padding(16.dp),
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        item {
            if (membership.isJoined) {
                OutlinedButton(onClick = { confirmation = CircleAction.LEAVE }, modifier = Modifier.fillMaxWidth()) {
                    Text("Leave Circle")
                }
            } else {
                Button(onClick = { confirmation = CircleAction.JOIN }, modifier = Modifier.fillMaxWidth()) {
                    Text("Join fictional Circle")
                }
            }
        }
    }
}

private enum class CircleAction { JOIN, LEAVE }

@Composable
private fun CircleCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun CircleRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, Modifier.weight(1f), fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Benefit(text: String) {
    Text("• $text")
}
