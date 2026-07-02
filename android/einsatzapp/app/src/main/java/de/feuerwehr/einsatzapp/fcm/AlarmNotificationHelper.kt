package de.feuerwehr.einsatzapp.fcm

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import de.feuerwehr.einsatzapp.MainActivity
import de.feuerwehr.einsatzapp.R
import de.feuerwehr.einsatzapp.data.PushPreferencesStore
import de.feuerwehr.einsatzapp.settings.NotificationChannelHelper
import kotlinx.coroutines.runBlocking

object AlarmNotificationHelper {

  private const val TEST_ALARM_ID = 999_001L
  private const val TEST_NOTIFICATION_ID = 999_001
  private const val ALARM_MAX_PLAY_MS = 15_000L
  private const val ALARM_FALLBACK_PLAY_MS = 8_000L

  private val handler = Handler(Looper.getMainLooper())
  private var previewStopRunnable: Runnable? = null
  private var alarmStopRunnable: Runnable? = null
  @Volatile
  private var previewPlayer: MediaPlayer? = null
  @Volatile
  private var alarmPlayer: MediaPlayer? = null
  @Volatile
  private var activeNotificationId: Int? = null

  fun showAlarm(
      context: Context,
      alarmId: Long,
      title: String,
      body: String,
      isTest: Boolean = false,
      scheduleRepeats: Boolean = true,
  ) {
      stopPreviewSound()
      val prefs = runBlocking { PushPreferencesStore(context).current() }
      NotificationChannelHelper.syncChannels(context, prefs)

      val intent = Intent(context, MainActivity::class.java).apply {
          action = "de.feuerwehr.einsatzapp.OPEN_ALARM"
          putExtra(PushMessagingService.EXTRA_ALARM_ID, alarmId)
          flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
      }
      val pending = PendingIntent.getActivity(
          context,
          alarmId.toInt(),
          intent,
          PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

      val channelId = NotificationChannelHelper.channelIdFor(prefs)
      val builder = NotificationCompat.Builder(context, channelId)
          .setSmallIcon(R.drawable.ic_launcher_foreground)
          .setContentTitle(title)
          .setContentText(body)
          .setStyle(NotificationCompat.BigTextStyle().bigText(body).setBigContentTitle(title))
          .setPriority(NotificationCompat.PRIORITY_MAX)
          .setCategory(NotificationCompat.CATEGORY_ALARM)
          .setAutoCancel(true)
          .setContentIntent(pending)
          .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
          .setShowWhen(true)
          .setOnlyAlertOnce(false)
          .setDefaults(NotificationCompat.DEFAULT_VIBRATE or NotificationCompat.DEFAULT_LIGHTS)

      val notificationId = if (isTest) TEST_NOTIFICATION_ID else alarmId.toInt()
      activeNotificationId = notificationId
      NotificationManagerCompat.from(context).notify(notificationId, builder.build())

      if (prefs.overrideAndroidTones) {
          playAlarmSound(context, prefs.alarmToneUriParsed(), prefs.alarmVolumePercent)
      }

      if (!isTest && scheduleRepeats && prefs.alarmRepeatCount > 0) {
          AlarmRepeatScheduler.schedule(context, alarmId, title, body, prefs.alarmRepeatCount)
      }
  }

  fun showTestAlarm(context: Context) {
      showAlarm(
          context = context,
          alarmId = TEST_ALARM_ID,
          title = "Probe-Alarm",
          body = "Test der Push-Einstellungen — kein echter Einsatz",
          isTest = true,
      )
  }

  /** Stoppt laufenden Alarmton, bricht Wiederholungen ab und entfernt aktive Alarm-Benachrichtigungen. */
  fun dismissActiveAlarm(context: Context) {
      stopPreviewSound()
      stopAlarmSound()
      AlarmRepeatScheduler.cancel(context)
      clearActiveNotifications(context)
  }

  fun playPreviewSound(context: Context, uri: Uri, volumePercent: Int) {
      stopPreviewSound()
      startOneShotPlayer(
          context = context,
          uri = uri,
          volumePercent = volumePercent,
          isPreview = true,
          onComplete = { stopPreviewSound() },
      )
  }

  private fun playAlarmSound(context: Context, uri: Uri, volumePercent: Int) {
      stopAlarmSound()
      startOneShotPlayer(
          context = context,
          uri = uri,
          volumePercent = volumePercent,
          isPreview = false,
          onComplete = { stopAlarmSound() },
      )
  }

  private fun startOneShotPlayer(
      context: Context,
      uri: Uri,
      volumePercent: Int,
      isPreview: Boolean,
      onComplete: () -> Unit,
  ) {
      runCatching {
          val volume = volumePercent.coerceIn(0, 100) / 100f
          val player = MediaPlayer().apply {
              setDataSource(context, uri)
              setAudioAttributes(
                  AudioAttributes.Builder()
                      .setUsage(AudioAttributes.USAGE_ALARM)
                      .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                      .build(),
              )
              isLooping = false
              setVolume(volume, volume)
              setOnCompletionListener { onComplete() }
              prepare()
          }
          if (isPreview) {
              previewPlayer = player
          } else {
              alarmPlayer = player
          }
          player.start()
          val stopAfterMs = playbackStopDelayMs(player.duration)
          if (isPreview) {
              schedulePreviewStop(onComplete, stopAfterMs)
          } else {
              scheduleAlarmStop(onComplete, stopAfterMs)
          }
      }.onFailure {
          onComplete()
      }
  }

  private fun playbackStopDelayMs(durationMs: Int): Long = when {
      durationMs > 0 -> (durationMs + 500L).coerceAtMost(ALARM_MAX_PLAY_MS)
      else -> ALARM_FALLBACK_PLAY_MS
  }

  private fun schedulePreviewStop(onComplete: () -> Unit, delayMs: Long) {
      previewStopRunnable?.let { handler.removeCallbacks(it) }
      val runnable = Runnable { onComplete() }
      previewStopRunnable = runnable
      handler.postDelayed(runnable, delayMs)
  }

  private fun scheduleAlarmStop(onComplete: () -> Unit, delayMs: Long) {
      alarmStopRunnable?.let { handler.removeCallbacks(it) }
      val runnable = Runnable { onComplete() }
      alarmStopRunnable = runnable
      handler.postDelayed(runnable, delayMs)
  }

  fun stopPreviewSound() {
      previewStopRunnable?.let { handler.removeCallbacks(it) }
      previewStopRunnable = null
      releasePlayer(previewPlayer)
      previewPlayer = null
  }

  private fun stopAlarmSound() {
      alarmStopRunnable?.let { handler.removeCallbacks(it) }
      alarmStopRunnable = null
      releasePlayer(alarmPlayer)
      alarmPlayer = null
  }

  private fun clearActiveNotifications(context: Context) {
      val manager = NotificationManagerCompat.from(context)
      activeNotificationId?.let { manager.cancel(it) }
      activeNotificationId = null
      manager.cancel(TEST_NOTIFICATION_ID)
  }

  private fun releasePlayer(player: MediaPlayer?) {
      player?.runCatching {
          if (isPlaying) stop()
          release()
      }
  }

  fun cancelRepeats(context: Context) {
      AlarmRepeatScheduler.cancel(context)
  }
}
