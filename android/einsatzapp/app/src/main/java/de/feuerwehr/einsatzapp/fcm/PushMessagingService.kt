package de.feuerwehr.einsatzapp.fcm

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import de.feuerwehr.einsatzapp.data.SessionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PushMessagingService : FirebaseMessagingService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(message: RemoteMessage) {
        val alarmId = message.data["alarmId"]?.toLongOrNull() ?: 0L
        val title = message.data["title"] ?: message.notification?.title ?: "Einsatz"
        val body = message.data["body"] ?: message.notification?.body ?: "Neuer DIVERA-Einsatz"
        AlarmNotificationHelper.showAlarm(
            context = this,
            alarmId = alarmId,
            title = title,
            body = body,
        )
    }

    override fun onNewToken(token: String) {
        serviceScope.launch {
            SessionStore(this@PushMessagingService).saveFcmToken(token)
            DeviceRegistrationHelper.ensureRegistered(this@PushMessagingService)
        }
    }

    companion object {
        const val EXTRA_ALARM_ID = "alarm_id"
    }
}
