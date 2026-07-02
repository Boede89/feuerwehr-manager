package de.feuerwehr.einsatzapp.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.feuerwehr.einsatzapp.data.DiveraAlarmSummary
import de.feuerwehr.einsatzapp.data.SessionStore

@Composable
fun HomeScreen(
    session: SessionStore.UserSession,
    serverUrl: String,
    alarms: List<DiveraAlarmSummary>,
    statusMessage: String?,
    isBusy: Boolean,
    onRefresh: () -> Unit,
    onLogout: () -> Unit,
    onOpenAlarm: (Long) -> Unit,
    onSaveServerUrl: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    var editingServer by rememberSaveable { mutableStateOf(false) }
    var serverDraft by rememberSaveable(serverUrl) { mutableStateOf(serverUrl) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Hallo, ${session.displayName}", style = MaterialTheme.typography.headlineSmall)
        Text("Einheit-ID: ${session.unitId}", style = MaterialTheme.typography.bodySmall)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Server", style = MaterialTheme.typography.titleMedium)
                if (editingServer) {
                    OutlinedTextField(
                        value = serverDraft,
                        onValueChange = { serverDraft = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Server-Adresse") },
                        singleLine = true,
                    )
                    Button(onClick = {
                        onSaveServerUrl(serverDraft)
                        editingServer = false
                    }) {
                        Text("Server speichern")
                    }
                } else {
                    Text(serverUrl, style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(onClick = { editingServer = true }) {
                        Text("Server-Adresse ändern")
                    }
                }
            }
        }

        if (statusMessage != null) {
            Text(statusMessage, color = MaterialTheme.colorScheme.primary)
        }

        Button(onClick = onRefresh, enabled = !isBusy, modifier = Modifier.fillMaxWidth()) {
            Text(if (isBusy) "Lädt…" else "Einsätze aktualisieren")
        }
        OutlinedButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
            Text("Einstellungen")
        }
        OutlinedButton(onClick = onLogout, modifier = Modifier.fillMaxWidth()) {
            Text("Abmelden")
        }

        Text("Offene Einsätze", style = MaterialTheme.typography.titleMedium)
        if (alarms.isEmpty()) {
            Text("Keine offenen Einsätze", style = MaterialTheme.typography.bodyMedium)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(alarms, key = { it.id }) { alarm ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenAlarm(alarm.id) },
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(alarm.title ?: "Einsatz", style = MaterialTheme.typography.titleMedium)
                            if (!alarm.address.isNullOrBlank()) {
                                Text(alarm.address, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}
