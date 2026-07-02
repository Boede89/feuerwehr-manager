package de.feuerwehr.einsatzapp.settings

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import de.feuerwehr.einsatzapp.data.PushPreferencesStore

object NotificationChannelHelper {

    const val CHANNEL_ALARMS = "divera_alarms"
    private const val CHANNEL_ALARMS_APP_TONE_PREFIX = "divera_alarms_app_tone_"
    private const val LEGACY_CHANNEL_ALARMS_OREO = "divera_alarms_oreo"

    fun appToneChannelId(alarmToneUri: String): String =
        CHANNEL_ALARMS_APP_TONE_PREFIX + alarmToneUri.hashCode()

    fun channelIdFor(prefs: PushPreferencesStore.PushPreferences): String =
        if (prefs.overrideAndroidTones) appToneChannelId(prefs.alarmToneUri) else CHANNEL_ALARMS

    fun syncChannels(context: Context, prefs: PushPreferencesStore.PushPreferences) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        manager.deleteNotificationChannel(LEGACY_CHANNEL_ALARMS_OREO)
        ensureDefaultChannel(manager)

        if (!prefs.overrideAndroidTones) return

        val channelId = appToneChannelId(prefs.alarmToneUri)
        if (manager.getNotificationChannel(channelId) != null) return

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val channel = NotificationChannel(
            channelId,
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
        manager.createNotificationChannel(channel)
    }

    private fun ensureDefaultChannel(manager: NotificationManager) {
        if (manager.getNotificationChannel(CHANNEL_ALARMS) != null) return
        val channel = NotificationChannel(
            CHANNEL_ALARMS,
            "Einsätze",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "DIVERA-Einsatzbenachrichtigungen (Android-Systemton)"
            enableVibration(true)
        }
        manager.createNotificationChannel(channel)
    }
}
