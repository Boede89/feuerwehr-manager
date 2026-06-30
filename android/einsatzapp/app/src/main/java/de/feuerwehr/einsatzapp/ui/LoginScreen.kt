package de.feuerwehr.einsatzapp.ui

import android.os.Build as AndroidBuild
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun LoginScreen(
    initialServerUrl: String,
    isBusy: Boolean,
    statusMessage: String?,
    onTestConnection: (serverUrl: String) -> Unit,
    onLogin: (serverUrl: String, username: String, password: String, deviceName: String) -> Unit,
    onClearStatus: () -> Unit,
) {
    var serverUrl by rememberSaveable(initialServerUrl) { mutableStateOf(initialServerUrl) }
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var deviceName by rememberSaveable { mutableStateOf(AndroidBuild.MODEL ?: "Android") }

    LaunchedEffect(statusMessage) {
        if (statusMessage != null) {
            kotlinx.coroutines.delay(100)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Feuerwehr Einsatz-App", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Zuerst die Adresse deines Feuerwehr-Managers eintragen — danach Benutzername und Passwort.",
            style = MaterialTheme.typography.bodyMedium,
        )

        OutlinedTextField(
            value = serverUrl,
            onValueChange = { serverUrl = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Server-Adresse") },
            placeholder = { Text("https://fw-manager.home.arpa") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        )

        OutlinedButton(
            onClick = {
                onClearStatus()
                onTestConnection(serverUrl)
            },
            enabled = !isBusy && serverUrl.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Verbindung testen")
        }

        Spacer(Modifier.height(8.dp))
        Text("Anmeldung", style = MaterialTheme.typography.titleMedium)

        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Benutzername") },
            singleLine = true,
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Passwort") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
        )
        OutlinedTextField(
            value = deviceName,
            onValueChange = { deviceName = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Gerätename") },
            singleLine = true,
        )

        if (statusMessage != null) {
            Text(statusMessage, color = MaterialTheme.colorScheme.error)
        }

        Button(
            onClick = {
                onClearStatus()
                onLogin(serverUrl, username, password, deviceName)
            },
            enabled = !isBusy && serverUrl.isNotBlank() && username.isNotBlank() && password.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (isBusy) {
                CircularProgressIndicator(modifier = Modifier.height(20.dp))
            } else {
                Text("Anmelden & Gerät registrieren")
            }
        }
    }
}
