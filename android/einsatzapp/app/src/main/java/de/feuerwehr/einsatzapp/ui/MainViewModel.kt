package de.feuerwehr.einsatzapp.ui

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import de.feuerwehr.einsatzapp.data.DiveraAlarmSummary
import de.feuerwehr.einsatzapp.data.FeuerwehrApiClient
import de.feuerwehr.einsatzapp.data.ServerConfigStore
import de.feuerwehr.einsatzapp.data.SessionStore
import de.feuerwehr.einsatzapp.fcm.DeviceRegistrar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(
    private val serverConfigStore: ServerConfigStore,
    private val sessionStore: SessionStore,
) : ViewModel() {

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
                withContext(Dispatchers.IO) {
                    DeviceRegistrar.registerIfPossible(sessionStore, serverConfigStore, deviceName).getOrThrow()
                }
                refreshAlarms()
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
            if (current != null) {
                withContext(Dispatchers.IO) {
                    FeuerwehrApiClient.instance.logout(baseUrl, current.sessionCookie)
                }
            }
            sessionStore.clear()
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
            val current = session.value ?: return@launch
            if (current.unitId <= 0) {
                _statusMessage.value = "Keine Einheit am Benutzer — Admin muss Einheit zuweisen"
                return@launch
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
            }.onFailure {
                _statusMessage.value = it.message
            }
            _isBusy.value = false
        }
    }

    fun alarmById(alarmId: Long): DiveraAlarmSummary? =
        _alarms.value.firstOrNull { it.id == alarmId }

    class Factory(
        private val serverConfigStore: ServerConfigStore,
        private val sessionStore: SessionStore,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            MainViewModel(serverConfigStore, sessionStore) as T
    }
}
