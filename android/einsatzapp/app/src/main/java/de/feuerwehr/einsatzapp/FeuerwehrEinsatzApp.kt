package de.feuerwehr.einsatzapp

import android.app.Application
import de.feuerwehr.einsatzapp.data.CredentialStore
import de.feuerwehr.einsatzapp.data.PushPreferencesStore
import de.feuerwehr.einsatzapp.fcm.TokenRefreshWorker
import de.feuerwehr.einsatzapp.settings.NotificationChannelHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class FeuerwehrEinsatzApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            val prefs = PushPreferencesStore(this@FeuerwehrEinsatzApp).current()
            NotificationChannelHelper.syncChannels(this@FeuerwehrEinsatzApp, prefs)
            if (CredentialStore(this@FeuerwehrEinsatzApp).load() != null) {
                TokenRefreshWorker.schedule(this@FeuerwehrEinsatzApp)
            }
        }
    }
}
