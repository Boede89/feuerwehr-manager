package de.feuerwehr.einsatzapp.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import de.feuerwehr.einsatzapp.BuildConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class ServerConfigStore(private val context: Context) {

    val serverBaseUrl: Flow<String> = context.appDataStore.data.map { prefs ->
        prefs[KEY_SERVER_URL] ?: BuildConfig.DEFAULT_SERVER_URL
    }

    suspend fun saveServerBaseUrl(rawUrl: String) {
        val normalized = normalizeServerUrl(rawUrl)
        context.appDataStore.edit { prefs ->
            prefs[KEY_SERVER_URL] = normalized
        }
    }

    suspend fun currentServerBaseUrl(): String = serverBaseUrl.first()

    companion object {
        private val KEY_SERVER_URL = stringPreferencesKey("server_base_url")

        fun normalizeServerUrl(raw: String): String {
            var url = raw.trim()
            if (url.isEmpty()) {
                throw IllegalArgumentException("Server-Adresse fehlt")
            }
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "https://$url"
            }
            while (url.endsWith("/")) {
                url = url.dropLast(1)
            }
            return url
        }
    }
}
