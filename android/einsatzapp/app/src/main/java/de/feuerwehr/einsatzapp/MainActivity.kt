package de.feuerwehr.einsatzapp

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import de.feuerwehr.einsatzapp.data.CredentialStore
import de.feuerwehr.einsatzapp.data.ServerConfigStore
import de.feuerwehr.einsatzapp.data.SessionStore
import de.feuerwehr.einsatzapp.fcm.DeviceRegistrar
import de.feuerwehr.einsatzapp.fcm.PushMessagingService
import de.feuerwehr.einsatzapp.ui.AlarmDetailScreen
import de.feuerwehr.einsatzapp.ui.HomeScreen
import de.feuerwehr.einsatzapp.ui.LoginScreen
import de.feuerwehr.einsatzapp.ui.MainViewModel
import de.feuerwehr.einsatzapp.ui.SettingsScreen
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val serverConfigStore by lazy { ServerConfigStore(this) }
    private val sessionStore by lazy { SessionStore(this) }
    private val credentialStore by lazy { CredentialStore(this) }
    private val viewModel: MainViewModel by viewModels {
        MainViewModel.Factory(applicationContext, serverConfigStore, sessionStore, credentialStore)
    }

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermissionIfNeeded()

        setContent {
            MaterialTheme {
                Surface {
                    val serverUrl by viewModel.serverUrl.collectAsState()
                    val session by viewModel.session.collectAsState()
                    val alarms by viewModel.alarms.collectAsState()
                    val status by viewModel.statusMessage.collectAsState()
                    val busy by viewModel.isBusy.collectAsState()
                    var detailAlarmId by rememberSaveable { mutableLongStateOf(0L) }
                    var showSettings by rememberSaveable { mutableStateOf(false) }

                    when {
                        detailAlarmId > 0L -> {
                            AlarmDetailScreen(
                                alarm = viewModel.alarmById(detailAlarmId),
                                onBack = { detailAlarmId = 0L },
                            )
                        }
                        showSettings && session != null -> {
                            SettingsScreen(onBack = { showSettings = false })
                        }
                        session == null -> {
                            LoginScreen(
                                initialServerUrl = serverUrl,
                                isBusy = busy,
                                statusMessage = status,
                                onTestConnection = viewModel::testServerConnection,
                                onLogin = viewModel::login,
                                onClearStatus = viewModel::clearStatus,
                            )
                        }
                        else -> {
                            HomeScreen(
                                session = session!!,
                                serverUrl = serverUrl,
                                alarms = alarms,
                                statusMessage = status,
                                isBusy = busy,
                                onRefresh = viewModel::refreshAlarms,
                                onLogout = viewModel::logout,
                                onOpenAlarm = { detailAlarmId = it },
                                onSaveServerUrl = viewModel::updateServerUrl,
                                onOpenSettings = { showSettings = true },
                            )
                        }
                    }
                }
            }
        }

        lifecycleScope.launch {
            viewModel.restoreSession()
        }
        handleAlarmIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            viewModel.restoreSession()
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAlarmIntent(intent)
    }

    private fun handleAlarmIntent(intent: android.content.Intent?) {
        val alarmId = intent?.getLongExtra(PushMessagingService.EXTRA_ALARM_ID, 0L) ?: 0L
        if (alarmId > 0L) {
            lifecycleScope.launch {
                viewModel.refreshAlarms()
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (!DeviceRegistrar.hasNotificationPermission(this)) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
