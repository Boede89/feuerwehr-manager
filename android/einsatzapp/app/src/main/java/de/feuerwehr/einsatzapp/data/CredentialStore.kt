package de.feuerwehr.einsatzapp.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** Verschlüsselt gespeicherte Anmeldedaten für automatische Wiederanmeldung (Alarmierungs-App). */
class CredentialStore(context: Context) {

    private val prefs = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    data class SavedCredentials(
        val username: String,
        val password: String,
        val deviceName: String,
    )

    fun save(username: String, password: String, deviceName: String) {
        prefs.edit()
            .putString(KEY_USERNAME, username.trim())
            .putString(KEY_PASSWORD, password)
            .putString(KEY_DEVICE, deviceName.trim())
            .apply()
    }

    fun load(): SavedCredentials? {
        val username = prefs.getString(KEY_USERNAME, null)?.takeIf { it.isNotBlank() } ?: return null
        val password = prefs.getString(KEY_PASSWORD, null)?.takeIf { it.isNotEmpty() } ?: return null
        val deviceName = prefs.getString(KEY_DEVICE, null)?.takeIf { it.isNotBlank() } ?: return null
        return SavedCredentials(username, password, deviceName)
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val PREFS_NAME = "einsatzapp_credentials"
        private const val KEY_USERNAME = "username"
        private const val KEY_PASSWORD = "password"
        private const val KEY_DEVICE = "device_name"
    }
}
