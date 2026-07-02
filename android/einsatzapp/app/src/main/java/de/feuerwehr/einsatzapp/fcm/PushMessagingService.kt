package de.feuerwehr.einsatzapp.fcm

import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import de.feuerwehr.einsatzapp.FeuerwehrEinsatzApp
import de.feuerwehr.einsatzapp.MainActivity
import de.feuerwehr.einsatzapp.R
import de.feuerwehr.einsatzapp.data.CredentialStore
import de.feuerwehr.einsatzapp.data.ServerConfigStore
import de.feuerwehr.einsatzapp.data.SessionRefreshHelper
import de.feuerwehr.einsatzapp.data.SessionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class PushMessagingService : FirebaseMessagingService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(message: RemoteMessage) {
        val alarmId = message.data["alarmId"]?.toLongOrNull() ?: 0L
        val title = message.notification?.title ?: message.data["title"] ?: "Einsatz"
        val body = message.notification?.body ?: message.data["body"] ?: "Neuer DIVERA-Einsatz"
        showNotification(alarmId, title, body)
    }

    override fun onNewToken(token: String) {
        serviceScope.launch {
            val sessionStore = SessionStore(this@PushMessagingService)
            sessionStore.saveFcmToken(token)
            val credentials = CredentialStore(this@PushMessagingService).load() ?: return@launch
            if (sessionStore.session.first() == null) {
                SessionRefreshHelper.silentReLogin(this@PushMessagingService)
            } else {
                val serverConfigStore = ServerConfigStore(this@PushMessagingService)
                DeviceRegistrar.registerIfPossible(sessionStore, serverConfigStore, credentials.deviceName)
            }
        }
    }

    private fun showNotification(alarmId: Long, title: String, body: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            action = "de.feuerwehr.einsatzapp.OPEN_ALARM"
            putExtra(EXTRA_ALARM_ID, alarmId)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            this,
            alarmId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, FeuerwehrEinsatzApp.CHANNEL_ALARMS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        NotificationManagerCompat.from(this).notify(alarmId.toInt(), notification)
    }

    companion object {
        const val EXTRA_ALARM_ID = "alarm_id"
    }
}
