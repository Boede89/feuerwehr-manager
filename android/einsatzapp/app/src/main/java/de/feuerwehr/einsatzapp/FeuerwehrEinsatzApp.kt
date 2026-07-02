package de.feuerwehr.einsatzapp

import android.app.Application
import de.feuerwehr.einsatzapp.AppForegroundTracker
import de.feuerwehr.einsatzapp.data.CredentialStore
import de.feuerwehr.einsatzapp.data.PushPreferencesStore
import de.feuerwehr.einsatzapp.fcm.AlarmNotificationHelper
import de.feuerwehr.einsatzapp.fcm.TokenRefreshWorker
import de.feuerwehr.einsatzapp.settings.NotificationChannelHelper
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class FeuerwehrEinsatzApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                AppForegroundTracker.setInForeground(true)
                AlarmNotificationHelper.dismissActiveAlarm(this@FeuerwehrEinsatzApp)
            }

            override fun onStop(owner: LifecycleOwner) {
                AppForegroundTracker.setInForeground(false)
            }
        })
        appScope.launch {
            val prefs = PushPreferencesStore(this@FeuerwehrEinsatzApp).current()
            NotificationChannelHelper.syncChannels(this@FeuerwehrEinsatzApp, prefs)
            if (CredentialStore(this@FeuerwehrEinsatzApp).load() != null) {
                TokenRefreshWorker.schedule(this@FeuerwehrEinsatzApp)
            }
        }
    }
}
