package de.feuerwehr.einsatzapp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.feuerwehr.einsatzapp.data.DiveraAlarmSummary

@Composable
fun AlarmDetailScreen(
    alarm: DiveraAlarmSummary?,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedButton(onClick = onBack) { Text("Zurück") }
        if (alarm == null) {
            Text("Einsatz nicht gefunden — bitte Liste aktualisieren.")
            return
        }
        Text(alarm.title ?: "Einsatz", style = MaterialTheme.typography.headlineSmall)
        if (!alarm.address.isNullOrBlank()) {
            Text(alarm.address, style = MaterialTheme.typography.titleMedium)
        }
        if (!alarm.text.isNullOrBlank()) {
            Text(alarm.text, style = MaterialTheme.typography.bodyLarge)
        }
        Text("Alarm-ID: ${alarm.id}", style = MaterialTheme.typography.bodySmall)
    }
}
