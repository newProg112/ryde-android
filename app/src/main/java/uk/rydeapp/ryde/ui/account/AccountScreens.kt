package uk.rydeapp.ryde.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import uk.rydeapp.ryde.data.AccountCommandResult
import uk.rydeapp.ryde.data.AccountSession
import uk.rydeapp.ryde.domain.model.ProfileContent

@Composable
fun SignedOutAccountScreen(
    onRegister: suspend (String, String, String) -> AccountCommandResult?,
    onSignIn: suspend (String, String) -> AccountCommandResult?,
) {
    var creatingAccount by rememberSaveable { mutableStateOf(false) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var displayName by rememberSaveable { mutableStateOf("") }
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    var busy by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Ryde connected account", style = MaterialTheme.typography.headlineSmall)
        Text("Local Firebase emulators only. No production account or data is used.")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (creatingAccount) {
                OutlinedButton(onClick = { creatingAccount = false; message = null }) { Text("Sign in") }
                Button(onClick = {}, enabled = false) { Text("Create account") }
            } else {
                Button(onClick = {}, enabled = false) { Text("Sign in") }
                OutlinedButton(onClick = { creatingAccount = true; message = null }) { Text("Create account") }
            }
        }
        if (creatingAccount) {
            OutlinedTextField(
                value = displayName,
                onValueChange = { displayName = it },
                label = { Text("Display name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Text("Use at least 8 characters. Emulator accounts are disposable local test data.", style = MaterialTheme.typography.bodySmall)
        Button(
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                busy = true
                message = null
                scope.launch {
                    try {
                        val result = if (creatingAccount) {
                            onRegister(email, password, displayName)
                        } else {
                            onSignIn(email, password)
                        }
                        message = result.userMessageOrNull()
                    } finally {
                        busy = false
                    }
                }
            },
        ) { Text(if (busy) "Please wait…" else if (creatingAccount) "Create local account" else "Sign in") }
        message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
fun ConnectedProfileScreen(
    session: AccountSession.Authenticated,
    profile: ProfileContent,
    onSave: suspend (String, String, String) -> AccountCommandResult?,
    onSignOut: suspend () -> AccountCommandResult?,
) {
    val initialHome = profile.savedPlaces.firstOrNull { it.label == "Home" }?.area.orEmpty()
    val initialWork = profile.savedPlaces.firstOrNull { it.label == "Work" }?.area.orEmpty()
    var displayName by rememberSaveable(session.accountId, session.displayName) { mutableStateOf(session.displayName) }
    var homeArea by rememberSaveable(session.accountId, initialHome) { mutableStateOf(initialHome) }
    var workArea by rememberSaveable(session.accountId, initialWork) { mutableStateOf(initialWork) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    var busy by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Your Ryde profile", style = MaterialTheme.typography.headlineSmall)
        Text("Connected to local Firebase emulators", color = MaterialTheme.colorScheme.primary)
        OutlinedTextField(displayName, { displayName = it }, label = { Text("Display name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(homeArea, { homeArea = it }, label = { Text("Home broad area") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(workArea, { workArea = it }, label = { Text("Work broad area") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Text(
            "Use town, district or broad-area names only. Never enter a street address, postcode, exact location or live location.",
            style = MaterialTheme.typography.bodySmall,
        )
        Button(
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                busy = true
                message = null
                scope.launch {
                    try {
                        val result = onSave(displayName, homeArea, workArea)
                        message = when (result) {
                            AccountCommandResult.Success -> "Profile saved to the local emulator."
                            else -> result.userMessageOrNull()
                        }
                    } finally {
                        busy = false
                    }
                }
            },
        ) { Text(if (busy) "Saving…" else "Save profile") }
        OutlinedButton(
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                busy = true
                message = null
                scope.launch {
                    try {
                        message = onSignOut().userMessageOrNull()
                    } finally {
                        busy = false
                    }
                }
            },
        ) { Text("Sign out") }
        message?.let {
            Text(it, color = if (it.startsWith("Profile saved")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
        }
    }
}

private fun AccountCommandResult?.userMessageOrNull(): String? = when (this) {
    AccountCommandResult.Success -> null
    is AccountCommandResult.InvalidInput -> userMessage
    is AccountCommandResult.Failure -> userMessage
    null -> "Ryde couldn't complete that request. Please try again."
}
