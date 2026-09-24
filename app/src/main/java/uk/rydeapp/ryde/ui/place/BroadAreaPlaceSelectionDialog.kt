package uk.rydeapp.ryde.ui.place

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import uk.rydeapp.ryde.R
import uk.rydeapp.ryde.domain.PlaceMatch

@Composable
internal fun BroadAreaPlaceSelectionDialog(
    prompt: BroadAreaPlaceSelectionPrompt,
    busy: Boolean,
    question: String,
    onPlaceSelected: (BroadAreaEndpoint, PlaceMatch) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.connected_place_choose_area)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(question)
                Text(
                    stringResource(R.string.connected_place_choice_privacy),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                prompt.candidates.forEach { candidate ->
                    OutlinedButton(
                        onClick = { onPlaceSelected(prompt.endpoint, candidate) },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(candidate.broadAreaLabel) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, enabled = !busy) {
                Text(stringResource(R.string.connected_picker_cancel))
            }
        },
    )
}
