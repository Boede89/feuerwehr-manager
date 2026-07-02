package de.feuerwehr.einsatzapp.settings

import android.content.Context
import android.media.RingtoneManager
import android.net.Uri

/** Verfügbare Alarmtöne (System-Klingeltöne — gleiches Prinzip wie DIVERA 24/7). */
object AlarmToneCatalog {

    data class AlarmTone(
        val id: String,
        val title: String,
        val uri: Uri,
    )

    fun load(context: Context): List<AlarmTone> {
        val tones = linkedMapOf<String, AlarmTone>()
        val defaultUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        if (defaultUri != null) {
            tones[defaultUri.toString()] = AlarmTone(defaultUri.toString(), "Standard-Alarm", defaultUri)
        }
        val manager = RingtoneManager(context)
        manager.setType(RingtoneManager.TYPE_ALARM)
        val cursor = manager.cursor ?: return tones.values.toList()
        while (cursor.moveToNext()) {
            val uri = manager.getRingtoneUri(cursor.position) ?: continue
            val key = uri.toString()
            if (tones.containsKey(key)) continue
            val title = cursor.getString(RingtoneManager.TITLE_COLUMN_INDEX)?.trim().orEmpty()
            tones[key] = AlarmTone(
                id = key,
                title = title.ifBlank { "Alarmton" },
                uri = uri,
            )
        }
        cursor.close()
        return tones.values.toList()
    }

    fun titleForUri(context: Context, uriString: String): String {
        val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return "Standard-Alarm"
        val ringtone = RingtoneManager.getRingtone(context, uri)
        return ringtone?.getTitle(context)?.toString()?.trim().orEmpty().ifBlank { "Alarmton" }
    }
}
