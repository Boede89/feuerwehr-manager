package de.feuerwehr.einsatzapp.data

import android.content.Context
import android.media.RingtoneManager
import android.net.Uri
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class PushPreferencesStore(private val context: Context) {

    val preferences: Flow<PushPreferences> = context.appDataStore.data.map { prefs ->
        PushPreferences(
            overrideAndroidTones = prefs[KEY_OVERRIDE_ANDROID_TONES] ?: true,
            alarmToneUri = prefs[KEY_ALARM_TONE_URI] ?: defaultAlarmToneUri(),
            alarmRepeatCount = prefs[KEY_ALARM_REPEAT_COUNT] ?: DEFAULT_REPEAT_COUNT,
            alarmInSilentMode = prefs[KEY_ALARM_IN_SILENT_MODE] ?: true,
        )
    }

    suspend fun current(): PushPreferences = preferences.first()

    suspend fun setOverrideAndroidTones(enabled: Boolean) {
        context.appDataStore.edit { it[KEY_OVERRIDE_ANDROID_TONES] = enabled }
    }

    suspend fun setAlarmToneUri(uri: String) {
        context.appDataStore.edit { it[KEY_ALARM_TONE_URI] = uri }
    }

    suspend fun setAlarmRepeatCount(count: Int) {
        context.appDataStore.edit { it[KEY_ALARM_REPEAT_COUNT] = count.coerceIn(0, MAX_REPEAT_COUNT) }
    }

    suspend fun setAlarmInSilentMode(enabled: Boolean) {
        context.appDataStore.edit { it[KEY_ALARM_IN_SILENT_MODE] = enabled }
    }

    data class PushPreferences(
        val overrideAndroidTones: Boolean,
        val alarmToneUri: String,
        val alarmRepeatCount: Int,
        val alarmInSilentMode: Boolean,
    ) {
        fun alarmToneUriParsed(): Uri = Uri.parse(alarmToneUri)
    }

    companion object {
        const val DEFAULT_REPEAT_COUNT = 3
        const val MAX_REPEAT_COUNT = 10

        private val KEY_OVERRIDE_ANDROID_TONES = booleanPreferencesKey("push_override_android_tones")
        private val KEY_ALARM_TONE_URI = stringPreferencesKey("push_alarm_tone_uri")
        private val KEY_ALARM_REPEAT_COUNT = intPreferencesKey("push_alarm_repeat_count")
        private val KEY_ALARM_IN_SILENT_MODE = booleanPreferencesKey("push_alarm_in_silent_mode")

        fun defaultAlarmToneUri(): String =
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM).toString()
    }
}
