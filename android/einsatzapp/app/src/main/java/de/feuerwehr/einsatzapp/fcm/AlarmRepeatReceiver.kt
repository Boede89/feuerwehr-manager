package de.feuerwehr.einsatzapp.fcm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlarmRepeatReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        AlarmRepeatScheduler.onRepeatAlarm(context, intent)
    }
}
