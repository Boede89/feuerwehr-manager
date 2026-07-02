package de.feuerwehr.einsatzapp.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import de.feuerwehr.einsatzapp.settings.SystemSettingsHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var refreshKey by remember { mutableIntStateOf(0) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshKey++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val notificationsOk = remember(refreshKey) { SystemSettingsHelper.hasNotificationPermission(context) }
    val batteryOk = remember(refreshKey) { SystemSettingsHelper.isBatteryOptimizationDisabled(context) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Einstellungen") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Damit Einsatz-Push zuverlässig ankommt, sollten diese Punkte am Handy erledigt sein. " +
                    "Tippen Sie auf einen Eintrag — die passenden Android-Einstellungen öffnen sich.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 4.dp),
            )

            SettingsItem(
                title = "Benachrichtigungen",
                description = "Push-Meldungen für DIVERA-Einsätze erlauben. Kanal „Einsätze“ sollte aktiv sein.",
                statusOk = notificationsOk,
                onClick = { SystemSettingsHelper.openNotificationSettings(context) },
            )

            SettingsItem(
                title = "Akku-Optimierung",
                description = "App von der Akku-Optimierung ausnehmen, damit Hintergrund-Jobs und Push nicht verzögert werden.",
                statusOk = batteryOk,
                onClick = { SystemSettingsHelper.openBatteryOptimizationSettings(context) },
            )

            SettingsItem(
                title = "App-Info & Berechtigungen",
                description = "Alle Berechtigungen der Einsatz-App prüfen (Netzwerk, Benachrichtigungen usw.).",
                statusOk = notificationsOk && batteryOk,
                onClick = { SystemSettingsHelper.openAppDetails(context) },
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Hersteller-Hinweise", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Bei Samsung, Xiaomi, Huawei, Oppo u. a. gibt es oft zusätzlich:",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text("• Autostart für diese App erlauben", style = MaterialTheme.typography.bodyMedium)
                    Text("• App im Hintergrund nicht einschränken", style = MaterialTheme.typography.bodyMedium)
                    Text("• Energiesparmodus für die App deaktivieren", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Diese Optionen finden Sie meist unter „Akku“ oder „Apps“ in den Systemeinstellungen — " +
                            "je nach Hersteller unterschiedlich benannt.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Text(
                "Weitere App-Einstellungen (z. B. Push-Inhalte pro Benutzer) folgen in späteren Versionen.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
            )
        }
    }
}

@Composable
private fun SettingsItem(
    title: String,
    description: String,
    statusOk: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = if (statusOk) "OK" else "Prüfen",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (statusOk) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )
                }
                Text(
                    description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
