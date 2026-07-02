package de.feuerwehr.einsatzapp

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import de.feuerwehr.einsatzapp.data.CredentialStore
import de.feuerwehr.einsatzapp.fcm.TokenRefreshWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class FeuerwehrEinsatzApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        appScope.launch {
            if (CredentialStore(this@FeuerwehrEinsatzApp).load() != null) {
                TokenRefreshWorker.schedule(this@FeuerwehrEinsatzApp)
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ALARMS,
            "Einsätze",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "DIVERA-Einsatzbenachrichtigungen"
            enableVibration(true)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ALARMS = "divera_alarms"
    }
}
