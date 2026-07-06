package de.feuerwehr.einsatzapp.ui

import android.content.Context
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import de.feuerwehr.einsatzapp.data.AlarmRefreshBus
import de.feuerwehr.einsatzapp.data.CredentialStore
import de.feuerwehr.einsatzapp.data.DiveraAlarmSummary
import de.feuerwehr.einsatzapp.data.FeuerwehrApiClient
import de.feuerwehr.einsatzapp.data.ServerConfigStore
import de.feuerwehr.einsatzapp.data.SessionExpiredException
import de.feuerwehr.einsatzapp.data.SessionRefreshHelper
import de.feuerwehr.einsatzapp.data.SessionStore
import de.feuerwehr.einsatzapp.fcm.AlarmNotificationHelper
import de.feuerwehr.einsatzapp.fcm.DeviceRegistrar
import de.feuerwehr.einsatzapp.fcm.TokenRefreshWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(
    private val appContext: Context,
    private val serverConfigStore: ServerConfigStore,
    private val sessionStore: SessionStore,
    private val credentialStore: CredentialStore,
) : ViewModel() {

    init {
        viewModelScope.launch {
            AlarmRefreshBus.alarms.collect { pushed ->
                if (pushed != null) {
                    _alarms.value = pushed
                }
            }
        }
    }

    val serverUrl: StateFlow<String> = serverConfigStore.serverBaseUrl.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        "",
    )

    val session = sessionStore.session.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _alarms = MutableStateFlow<List<DiveraAlarmSummary>>(emptyList())
    val alarms: StateFlow<List<DiveraAlarmSummary>> = _alarms.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    fun clearStatus() {
        _statusMessage.value = null
    }

    fun restoreSession() {
        viewModelScope.launch {
            if (sessionStore.session.first() == null) return@launch
            _isBusy.value = true
            ensureSessionOrReLogin()
            refreshAlarmsInternal(retryOnExpired = false)
            _isBusy.value = false
        }
    }

    fun testServerConnection(serverUrlInput: String) {
        viewModelScope.launch {
            _isBusy.value = true
            _statusMessage.value = runCatching {
                val normalized = ServerConfigStore.normalizeServerUrl(serverUrlInput)
                serverConfigStore.saveServerBaseUrl(normalized)
                withContext(Dispatchers.IO) {
                    FeuerwehrApiClient.instance.testServerConnection(normalized).getOrThrow()
                }
            }.fold(
                onSuccess = { "Verbindung zum Server OK" },
                onFailure = { it.message ?: "Verbindung fehlgeschlagen" },
            )
            _isBusy.value = false
        }
    }

    fun login(serverUrlInput: String, username: String, password: String, deviceName: String) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = runCatching {
                val baseUrl = ServerConfigStore.normalizeServerUrl(serverUrlInput)
                serverConfigStore.saveServerBaseUrl(baseUrl)
                val (loginResponse, cookie) = withContext(Dispatchers.IO) {
                    FeuerwehrApiClient.instance.login(baseUrl, username, password).getOrThrow()
                }
                if (loginResponse.totpRequired) {
                    throw IllegalStateException(
                        "Zweiter Faktor (2FA) ist aktiv — bitte zuerst in der Web-Oberfläche anmelden oder 2FA deaktivieren.",
                    )
                }
                val userId = loginResponse.userId ?: throw IllegalStateException("Benutzer-ID fehlt")
                val unitId = loginResponse.unitId ?: 0L
                val displayName = loginResponse.displayName ?: username
                sessionStore.save(
                    SessionStore.UserSession(
                        sessionCookie = cookie,
                        userId = userId,
                        displayName = displayName,
                        unitId = unitId,
                    ),
                )
                credentialStore.save(username, password, deviceName)
                withContext(Dispatchers.IO) {
                    DeviceRegistrar.registerIfPossible(sessionStore, serverConfigStore, deviceName).getOrThrow()
                }
                TokenRefreshWorker.schedule(appContext)
                refreshAlarmsInternal(retryOnExpired = false)
            }
            _statusMessage.value = result.fold(
                onSuccess = { "Angemeldet — Gerät registriert" },
                onFailure = { it.message ?: "Anmeldung fehlgeschlagen" },
            )
            _isBusy.value = false
        }
    }

    fun logout() {
        viewModelScope.launch {
            val current = session.value
            val baseUrl = serverConfigStore.currentServerBaseUrl()
            withContext(Dispatchers.IO) {
                DeviceRegistrar.unregisterIfPossible(sessionStore, serverConfigStore)
                if (current != null) {
                    FeuerwehrApiClient.instance.logout(baseUrl, current.sessionCookie)
                }
            }
            AlarmNotificationHelper.dismissActiveAlarm(appContext)
            sessionStore.clear()
            credentialStore.clear()
            TokenRefreshWorker.cancel(appContext)
            _alarms.value = emptyList()
            _statusMessage.value = "Abgemeldet"
        }
    }

    fun updateServerUrl(serverUrlInput: String) {
        viewModelScope.launch {
            runCatching {
                serverConfigStore.saveServerBaseUrl(serverUrlInput)
            }.onFailure {
                _statusMessage.value = it.message
            }.onSuccess {
                _statusMessage.value = "Server-Adresse gespeichert — bitte neu anmelden"
            }
        }
    }

    fun refreshAlarms() {
        viewModelScope.launch {
            refreshAlarmsInternal(retryOnExpired = true)
        }
    }

    private suspend fun refreshAlarmsInternal(retryOnExpired: Boolean) {
        val current = sessionStore.session.first() ?: return
        if (current.unitId <= 0) {
            _statusMessage.value = "Keine Einheit am Benutzer — Admin muss Einheit zuweisen"
            return
        }
        _isBusy.value = true
        val baseUrl = serverConfigStore.currentServerBaseUrl()
        val result = withContext(Dispatchers.IO) {
            FeuerwehrApiClient.instance.fetchAlarms(baseUrl, current.sessionCookie, current.unitId)
        }
        result.onSuccess { response ->
            if (response.success) {
                _alarms.value = response.alarms
            } else {
                _statusMessage.value = response.message ?: "Einsätze nicht verfügbar"
            }
        }.onFailure { error ->
            if (retryOnExpired && error is SessionExpiredException && trySilentReLogin()) {
                refreshAlarmsInternal(retryOnExpired = false)
                return
            }
            if (error is SessionExpiredException) {
                sessionStore.clear()
                _statusMessage.value = "Sitzung abgelaufen — bitte erneut anmelden"
            } else {
                _statusMessage.value = error.message
            }
        }
        _isBusy.value = false
    }

    private suspend fun ensureSessionOrReLogin() {
        val current = sessionStore.session.first() ?: return
        val baseUrl = serverConfigStore.currentServerBaseUrl()
        val valid = withContext(Dispatchers.IO) {
            FeuerwehrApiClient.instance.fetchSession(baseUrl, current.sessionCookie).isSuccess
        }
        if (!valid) {
            if (!trySilentReLogin()) {
                sessionStore.clear()
            }
        }
    }

    private suspend fun trySilentReLogin(): Boolean {
        val ok = withContext(Dispatchers.IO) {
            SessionRefreshHelper.silentReLogin(appContext)
        }
        if (ok) {
            _statusMessage.value = null
        }
        return ok
    }

    fun alarmById(alarmId: Long): DiveraAlarmSummary? =
        _alarms.value.firstOrNull { it.id == alarmId }

    class Factory(
        private val appContext: Context,
        private val serverConfigStore: ServerConfigStore,
        private val sessionStore: SessionStore,
        private val credentialStore: CredentialStore,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            MainViewModel(appContext, serverConfigStore, sessionStore, credentialStore) as T
    }
}
