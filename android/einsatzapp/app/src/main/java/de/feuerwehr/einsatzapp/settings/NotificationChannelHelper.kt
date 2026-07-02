package de.feuerwehr.einsatzapp.settings

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import de.feuerwehr.einsatzapp.data.PushPreferencesStore

object NotificationChannelHelper {

    const val CHANNEL_ALARMS = "divera_alarms"
    const val CHANNEL_ALARMS_OREO = "divera_alarms_oreo"

    fun syncChannels(context: Context, prefs: PushPreferencesStore.PushPreferences) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.deleteNotificationChannel(CHANNEL_ALARMS_OREO)
        manager.deleteNotificationChannel(CHANNEL_ALARMS)

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        if (prefs.overrideAndroidTones) {
            val oreoChannel = NotificationChannel(
                CHANNEL_ALARMS_OREO,
                "Einsatz-Alarm (App-Ton)",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "DIVERA-Einsätze mit in der App gewähltem Alarmton"
                enableVibration(true)
                setSound(prefs.alarmToneUriParsed(), audioAttributes)
                if (prefs.alarmInSilentMode) {
                    setBypassDnd(true)
                }
            }
            manager.createNotificationChannel(oreoChannel)
        }

        val defaultChannel = NotificationChannel(
            CHANNEL_ALARMS,
            "Einsätze",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "DIVERA-Einsatzbenachrichtigungen (Android-Systemton)"
            enableVibration(true)
        }
        manager.createNotificationChannel(defaultChannel)
    }

    fun channelIdFor(prefs: PushPreferencesStore.PushPreferences): String =
        if (prefs.overrideAndroidTones) CHANNEL_ALARMS_OREO else CHANNEL_ALARMS
}
