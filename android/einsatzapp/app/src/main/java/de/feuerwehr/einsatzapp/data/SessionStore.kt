package de.feuerwehr.einsatzapp.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.remove
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class SessionStore(private val context: Context) {

    val session: Flow<UserSession?> = context.appDataStore.data.map { prefs ->
        val cookie = prefs[KEY_SESSION_COOKIE]
        val userId = prefs[KEY_USER_ID] ?: return@map null
        if (cookie.isNullOrBlank()) return@map null
        UserSession(
            sessionCookie = cookie,
            userId = userId,
            displayName = prefs[KEY_DISPLAY_NAME] ?: "",
            unitId = prefs[KEY_UNIT_ID] ?: 0L,
        )
    }

    suspend fun save(session: UserSession) {
        context.appDataStore.edit { prefs ->
            prefs[KEY_SESSION_COOKIE] = session.sessionCookie
            prefs[KEY_USER_ID] = session.userId
            prefs[KEY_DISPLAY_NAME] = session.displayName
            prefs[KEY_UNIT_ID] = session.unitId
        }
    }

    suspend fun clear() {
        context.appDataStore.edit { prefs ->
            prefs.remove(KEY_SESSION_COOKIE)
            prefs.remove(KEY_USER_ID)
            prefs.remove(KEY_DISPLAY_NAME)
            prefs.remove(KEY_UNIT_ID)
            prefs.remove(KEY_FCM_TOKEN)
        }
    }

    suspend fun saveFcmToken(token: String) {
        context.appDataStore.edit { prefs ->
            prefs[KEY_FCM_TOKEN] = token
        }
    }

    suspend fun fcmToken(): String? {
        return context.appDataStore.data.map { it[KEY_FCM_TOKEN] }.first()
    }

    data class UserSession(
        val sessionCookie: String,
        val userId: Long,
        val displayName: String,
        val unitId: Long,
    )

    companion object {
        private val KEY_SESSION_COOKIE = stringPreferencesKey("session_cookie")
        private val KEY_USER_ID = longPreferencesKey("user_id")
        private val KEY_DISPLAY_NAME = stringPreferencesKey("display_name")
        private val KEY_UNIT_ID = longPreferencesKey("unit_id")
        private val KEY_FCM_TOKEN = stringPreferencesKey("fcm_token")
    }
}
