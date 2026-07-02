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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import de.feuerwehr.einsatzapp.data.PushPreferencesStore
import de.feuerwehr.einsatzapp.data.SessionStore
import de.feuerwehr.einsatzapp.fcm.AlarmNotificationHelper
import de.feuerwehr.einsatzapp.settings.AlarmToneCatalog
import de.feuerwehr.einsatzapp.settings.NotificationChannelHelper
import de.feuerwehr.einsatzapp.settings.SystemSettingsHelper
import de.feuerwehr.einsatzapp.ui.theme.FeuerwehrRot
import kotlinx.coroutines.launch

private enum class SettingsTab(val label: String) {
    GENERAL("Allgemein"),
    PUSH("Push"),
    DEVICE("Gerät"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    session: SessionStore.UserSession,
    serverUrl: String,
    onSaveServerUrl: (String) -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = SettingsTab.entries

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Einstellungen", fontWeight = FontWeight.SemiBold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = FeuerwehrRot,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = FeuerwehrRot,
                indicator = { tabPositions ->
                    if (selectedTab < tabPositions.size) {
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = FeuerwehrRot,
                        )
                    }
                },
            ) {
                tabs.forEachIndexed { index, tab ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(tab.label) },
                    )
                }
            }

            when (tabs[selectedTab]) {
                SettingsTab.GENERAL -> GeneralSettingsTab(
                    session = session,
                    serverUrl = serverUrl,
                    onSaveServerUrl = onSaveServerUrl,
                    onLogout = onLogout,
                )
                SettingsTab.PUSH -> PushSettingsTab()
                SettingsTab.DEVICE -> DeviceSettingsTab()
            }
        }
    }
}

@Composable
private fun GeneralSettingsTab(
    session: SessionStore.UserSession,
    serverUrl: String,
    onSaveServerUrl: (String) -> Unit,
    onLogout: () -> Unit,
) {
    var editingServer by rememberSaveable { mutableStateOf(false) }
    var serverDraft by rememberSaveable(serverUrl) { mutableStateOf(serverUrl) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SettingsSectionHeader(
            icon = { Icon(Icons.Default.Settings, contentDescription = null, tint = FeuerwehrRot) },
            title = "Konto & Server",
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(session.displayName, style = MaterialTheme.typography.titleMedium)
                Text(
                    "Benutzer-ID: ${session.userId} · Einheit: ${session.unitId}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Server-Adresse", style = MaterialTheme.typography.titleMedium)
                if (editingServer) {
                    OutlinedTextField(
                        value = serverDraft,
                        onValueChange = { serverDraft = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Server-URL") },
                        singleLine = true,
                    )
                    Button(onClick = {
                        onSaveServerUrl(serverDraft)
                        editingServer = false
                    }) {
                        Text("Speichern")
                    }
                } else {
                    Text(serverUrl, style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(onClick = { editingServer = true }) {
                        Text("Adresse ändern")
                    }
                }
            }
        }

        OutlinedButton(onClick = onLogout, modifier = Modifier.fillMaxWidth()) {
            Text("Abmelden")
        }
    }
}

@Composable
private fun PushSettingsTab() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pushStore = remember { PushPreferencesStore(context) }
    val prefs by pushStore.preferences.collectAsState(
        initial = PushPreferencesStore.PushPreferences(
            overrideAndroidTones = true,
            alarmToneUri = PushPreferencesStore.defaultAlarmToneUri(),
            alarmRepeatCount = PushPreferencesStore.DEFAULT_REPEAT_COUNT,
            alarmInSilentMode = true,
        ),
    )
    val tones = remember { AlarmToneCatalog.load(context) }
    val repeatOptions = listOf(0, 1, 3, 5, 10)

    DisposableEffect(Unit) {
        onDispose { AlarmNotificationHelper.stopPreviewSound() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SettingsSectionHeader(
            icon = { Icon(Icons.Default.Notifications, contentDescription = null, tint = FeuerwehrRot) },
            title = "Push & Alarmton",
        )

        Text(
            "Wie bei DIVERA 24/7 können Sie den Alarmton in der App wählen und Android-Systemtöne überschreiben.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        scope.launch {
                            val newValue = !prefs.overrideAndroidTones
                            pushStore.setOverrideAndroidTones(newValue)
                            NotificationChannelHelper.syncChannels(context, pushStore.current())
                        }
                    }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = prefs.overrideAndroidTones,
                    onCheckedChange = { enabled ->
                        scope.launch {
                            pushStore.setOverrideAndroidTones(enabled)
                            NotificationChannelHelper.syncChannels(context, pushStore.current())
                        }
                    },
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text("Android-Toneinstellungen überschreiben", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Eigener Alarmton aus der Liste unten — unabhängig vom System-Kanal.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (prefs.overrideAndroidTones) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    Text(
                        "Alarmton wählen",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    tones.forEach { tone ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    scope.launch {
                                        pushStore.setAlarmToneUri(tone.id)
                                        NotificationChannelHelper.syncChannels(context, pushStore.current())
                                        AlarmNotificationHelper.playPreviewSound(context, tone.uri)
                                    }
                                }
                                .padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = prefs.alarmToneUri == tone.id,
                                onClick = {
                                    scope.launch {
                                        pushStore.setAlarmToneUri(tone.id)
                                        NotificationChannelHelper.syncChannels(context, pushStore.current())
                                        AlarmNotificationHelper.playPreviewSound(context, tone.uri)
                                    }
                                },
                            )
                            Text(tone.title, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Wiederholungen", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Alarm erneut alle 20 Sekunden (wie DIVERA)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        repeatOptions.forEach { count ->
                            val selected = prefs.alarmRepeatCount == count
                            OutlinedButton(
                                onClick = {
                                    scope.launch { pushStore.setAlarmRepeatCount(count) }
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(
                                    if (count == 0) "Aus" else count.toString(),
                                    color = if (selected) FeuerwehrRot else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                )
                            }
                        }
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            scope.launch {
                                pushStore.setAlarmInSilentMode(!prefs.alarmInSilentMode)
                                NotificationChannelHelper.syncChannels(context, pushStore.current())
                            }
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = prefs.alarmInSilentMode,
                        onCheckedChange = { enabled ->
                            scope.launch {
                                pushStore.setAlarmInSilentMode(enabled)
                                NotificationChannelHelper.syncChannels(context, pushStore.current())
                            }
                        },
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Alarm auch im Lautlosmodus", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Benachrichtigung kann „Nicht stören“ umgehen (wenn erlaubt).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        Button(
            onClick = { AlarmNotificationHelper.showTestAlarm(context) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Probe-Alarm")
        }

        OutlinedButton(
            onClick = { SystemSettingsHelper.openNotificationSettings(context) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Android-Benachrichtigungseinstellungen")
        }
    }
}

@Composable
private fun DeviceSettingsTab() {
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SettingsSectionHeader(
            icon = { Icon(Icons.Default.PhoneAndroid, contentDescription = null, tint = FeuerwehrRot) },
            title = "Gerät optimieren",
        )

        Text(
            "Damit Einsatz-Push zuverlässig ankommt, sollten diese Punkte erledigt sein.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        DeviceSettingsItem(
            title = "Benachrichtigungen",
            description = "Push-Meldungen erlauben. Kanal „Einsätze“ sollte aktiv sein.",
            statusOk = notificationsOk,
            onClick = { SystemSettingsHelper.openNotificationSettings(context) },
        )

        DeviceSettingsItem(
            title = "Akku-Optimierung",
            description = "App von der Akku-Optimierung ausnehmen.",
            statusOk = batteryOk,
            onClick = { SystemSettingsHelper.openBatteryOptimizationSettings(context) },
        )

        DeviceSettingsItem(
            title = "App-Info & Berechtigungen",
            description = "Alle Berechtigungen der Einsatz-App prüfen.",
            statusOk = notificationsOk && batteryOk,
            onClick = { SystemSettingsHelper.openAppDetails(context) },
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Hersteller-Hinweise", style = MaterialTheme.typography.titleMedium)
                Text("• Autostart für diese App erlauben", style = MaterialTheme.typography.bodyMedium)
                Text("• App im Hintergrund nicht einschränken", style = MaterialTheme.typography.bodyMedium)
                Text("• Energiesparmodus für die App deaktivieren", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun SettingsSectionHeader(
    icon: @Composable () -> Unit,
    title: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        icon()
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
}

@Composable
private fun DeviceSettingsItem(
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
                        color = if (statusOk) FeuerwehrRot else MaterialTheme.colorScheme.error,
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
