package de.feuerwehr.einsatzapp.fcm

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import de.feuerwehr.einsatzapp.data.ServerConfigStore
import de.feuerwehr.einsatzapp.data.SessionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class PushMessagingService : FirebaseMessagingService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(message: RemoteMessage) {
        val type = message.data["type"] ?: "divera_alarm"
        if (type != "divera_alarm" && type != "manual_alarm") {
            return
        }
        val alarmId = message.data["alarmId"]?.toLongOrNull() ?: 0L
        if (alarmId <= 0L) {
            return
        }
        val defaultBody = if (type == "manual_alarm") "Neuer Einsatz" else "Neuer DIVERA-Einsatz"
        val title = message.data["title"] ?: message.notification?.title ?: "Einsatz"
        val body = message.data["body"] ?: message.notification?.body ?: defaultBody
        AlarmNotificationHelper.showAlarm(
            context = this,
            alarmId = alarmId,
            title = title,
            body = body,
        )
        serviceScope.launch {
            refreshAlarmsIfLoggedIn()
        }
    }

    override fun onNewToken(token: String) {
        serviceScope.launch {
            SessionStore(this@PushMessagingService).saveFcmToken(token)
            DeviceRegistrationHelper.ensureRegistered(this@PushMessagingService)
        }
    }

    private suspend fun refreshAlarmsIfLoggedIn() {
        val sessionStore = SessionStore(this)
        val session = sessionStore.session.first() ?: return
        if (session.unitId <= 0) return
        val baseUrl = ServerConfigStore(this).currentServerBaseUrl()
        if (baseUrl.isBlank()) return
        runCatching {
            de.feuerwehr.einsatzapp.data.FeuerwehrApiClient.instance.fetchAlarms(
                baseUrl = baseUrl,
                sessionCookie = session.sessionCookie,
                unitId = session.unitId,
            ).getOrNull()?.takeIf { it.success }?.alarms?.let { alarms ->
                de.feuerwehr.einsatzapp.data.AlarmRefreshBus.publish(alarms)
            }
        }
    }

    companion object {
        const val EXTRA_ALARM_ID = "alarm_id"
    }
}
