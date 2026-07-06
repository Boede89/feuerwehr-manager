package de.feuerwehr.einsatzapp.settings

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import de.feuerwehr.einsatzapp.data.PushPreferencesStore

object NotificationChannelHelper {

    const val CHANNEL_ALARMS = "divera_alarms_v3"
    private const val CHANNEL_ALARMS_APP_TONE_PREFIX = "divera_alarms_app_v3_"
    private const val LEGACY_CHANNEL_ALARMS = "divera_alarms"
    private const val LEGACY_CHANNEL_ALARMS_OREO = "divera_alarms_oreo"
    private const val LEGACY_CHANNEL_ALARMS_V2 = "divera_alarms_v2"
    private const val LEGACY_CHANNEL_ALARMS_APP_V2_PREFIX = "divera_alarms_app_v2_"

    fun appToneChannelId(alarmToneUri: String): String =
        CHANNEL_ALARMS_APP_TONE_PREFIX + alarmToneUri.hashCode()

    fun channelIdFor(prefs: PushPreferencesStore.PushPreferences): String =
        if (prefs.overrideAndroidTones) appToneChannelId(prefs.alarmToneUri) else CHANNEL_ALARMS

    fun syncChannels(context: Context, prefs: PushPreferencesStore.PushPreferences) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        deleteLegacyChannels(manager)
        ensureDefaultChannel(manager)

        if (!prefs.overrideAndroidTones) return

        val channelId = appToneChannelId(prefs.alarmToneUri)
        if (manager.getNotificationChannel(channelId) != null) return

        val channel = NotificationChannel(
            channelId,
            "Einsatz-Alarm (App-Ton)",
            NotificationManager.IMPORTANCE_HIGH,
        )
        configureAlarmChannel(channel, prefs)
        manager.createNotificationChannel(channel)
    }

    private fun ensureDefaultChannel(manager: NotificationManager) {
        if (manager.getNotificationChannel(CHANNEL_ALARMS) != null) return
        val channel = NotificationChannel(
            CHANNEL_ALARMS,
            "Einsätze",
            NotificationManager.IMPORTANCE_HIGH,
        )
        configureAlarmChannel(channel)
        manager.createNotificationChannel(channel)
    }

    private fun configureAlarmChannel(
        channel: NotificationChannel,
        prefs: PushPreferencesStore.PushPreferences? = null,
    ) {
        channel.description = if (prefs != null) {
            "Einsatz-Alarme — Ton und Lautstärke werden von der App gesteuert"
        } else {
            "Einsatzbenachrichtigungen (Android-Systemton)"
        }
        channel.enableVibration(true)
        channel.enableLights(true)
        channel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        if (prefs != null) {
            channel.setSound(null, null)
            if (prefs.alarmInSilentMode) {
                channel.setBypassDnd(true)
            }
        }
    }

    private fun deleteLegacyChannels(manager: NotificationManager) {
        manager.deleteNotificationChannel(LEGACY_CHANNEL_ALARMS)
        manager.deleteNotificationChannel(LEGACY_CHANNEL_ALARMS_OREO)
        manager.deleteNotificationChannel(LEGACY_CHANNEL_ALARMS_V2)
    }
}
