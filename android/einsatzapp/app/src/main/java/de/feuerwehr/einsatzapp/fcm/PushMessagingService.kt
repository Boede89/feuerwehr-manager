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

class PushMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        val alarmId = message.data["alarmId"]?.toLongOrNull() ?: 0L
        val title = message.notification?.title ?: message.data["title"] ?: "Einsatz"
        val body = message.notification?.body ?: message.data["body"] ?: "Neuer DIVERA-Einsatz"
        showNotification(alarmId, title, body)
    }

    override fun onNewToken(token: String) {
        // Registrierung erfolgt nach Login in MainViewModel
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
