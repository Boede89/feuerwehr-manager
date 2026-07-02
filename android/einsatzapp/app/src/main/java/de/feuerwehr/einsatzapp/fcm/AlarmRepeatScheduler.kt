package de.feuerwehr.einsatzapp.fcm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock

object AlarmRepeatScheduler {

    private const val ACTION_REPEAT = "de.feuerwehr.einsatzapp.ALARM_REPEAT"
    private const val EXTRA_ALARM_ID = "alarm_id"
    private const val EXTRA_TITLE = "title"
    private const val EXTRA_BODY = "body"
    private const val EXTRA_REMAINING = "remaining"
    private const val REQUEST_CODE = 42_001
    private const val INTERVAL_MS = 20_000L

    fun schedule(context: Context, alarmId: Long, title: String, body: String, repeatCount: Int) {
        cancel(context)
        trigger(context, alarmId, title, body, repeatCount)
    }

    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        alarmManager.cancel(buildPendingIntent(context, 0L, "", "", 0))
    }

    private fun trigger(context: Context, alarmId: Long, title: String, body: String, remaining: Int) {
        if (remaining <= 0) return
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val pending = buildPendingIntent(context, alarmId, title, body, remaining)
        val triggerAt = SystemClock.elapsedRealtime() + INTERVAL_MS
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pending)
        } else {
            alarmManager.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pending)
        }
    }

    private fun buildPendingIntent(
        context: Context,
        alarmId: Long,
        title: String,
        body: String,
        remaining: Int,
    ): PendingIntent {
        val intent = Intent(context, AlarmRepeatReceiver::class.java).apply {
            action = ACTION_REPEAT
            putExtra(EXTRA_ALARM_ID, alarmId)
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_BODY, body)
            putExtra(EXTRA_REMAINING, remaining)
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun onRepeatAlarm(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_REPEAT) return
        val alarmId = intent.getLongExtra(EXTRA_ALARM_ID, 0L)
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val body = intent.getStringExtra(EXTRA_BODY).orEmpty()
        val remaining = intent.getIntExtra(EXTRA_REMAINING, 0)
        if (alarmId <= 0L || remaining <= 0) return

        AlarmNotificationHelper.showAlarm(
            context = context,
            alarmId = alarmId,
            title = title,
            body = body,
            isTest = false,
            scheduleRepeats = false,
        )
        trigger(context, alarmId, title, body, remaining - 1)
    }
}
