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
  private const val PREVIEW_MAX_MS = 15_000L

  private val previewHandler = Handler(Looper.getMainLooper())
  @Volatile
  private var previewPlayer: MediaPlayer? = null

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
          .setPriority(NotificationCompat.PRIORITY_MAX)
          .setCategory(NotificationCompat.CATEGORY_ALARM)
          .setAutoCancel(true)
          .setContentIntent(pending)
          .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

      val notificationId = if (isTest) TEST_NOTIFICATION_ID else alarmId.toInt()
      NotificationManagerCompat.from(context).notify(notificationId, builder.build())

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

  fun playPreviewSound(context: Context, uri: Uri) {
      stopPreviewSound()
      runCatching {
          val player = MediaPlayer().apply {
              setDataSource(context, uri)
              setAudioAttributes(
                  AudioAttributes.Builder()
                      .setUsage(AudioAttributes.USAGE_ALARM)
                      .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                      .build(),
              )
              isLooping = false
              setOnCompletionListener { stopPreviewSound() }
              prepare()
          }
          previewPlayer = player
          player.start()
          val durationMs = player.duration
          val stopAfterMs = when {
              durationMs > 0 -> (durationMs + 500L).coerceAtMost(PREVIEW_MAX_MS)
              else -> 8_000L
          }
          previewHandler.postDelayed({ stopPreviewSound() }, stopAfterMs)
      }.onFailure {
          stopPreviewSound()
      }
  }

  fun stopPreviewSound() {
      previewHandler.removeCallbacksAndMessages(null)
      previewPlayer?.runCatching {
          if (isPlaying) stop()
          release()
      }
      previewPlayer = null
  }

  fun cancelRepeats(context: Context) {
      AlarmRepeatScheduler.cancel(context)
  }
}
