package de.feuerwehr.einsatzapp.settings

import android.content.Context
import android.media.RingtoneManager
import android.net.Uri

/** Verfügbare Alarmtöne: eingebaute App-MP3s und System-Klingeltöne. */
object AlarmToneCatalog {

    data class AlarmTone(
        val id: String,
        val title: String,
        val uri: Uri,
        val bundled: Boolean = false,
    )

    data class Catalog(
        val bundled: List<AlarmTone>,
        val system: List<AlarmTone>,
    ) {
        val all: List<AlarmTone> get() = bundled + system
    }

    fun load(context: Context): Catalog = Catalog(
        bundled = BundledAlarmTones.load(context),
        system = loadSystemTones(context),
    )

    private fun loadSystemTones(context: Context): List<AlarmTone> {
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
        if (uriString.startsWith("android.resource://")) {
            val resourceName = runCatching {
                val resId = Uri.parse(uriString).lastPathSegment?.toIntOrNull() ?: return@runCatching null
                context.resources.getResourceEntryName(resId)
            }.getOrNull()
            if (resourceName != null && resourceName.startsWith("tone_")) {
                return BundledAlarmTones.titleForResourceName(resourceName)
            }
        }
        val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return "Standard-Alarm"
        val ringtone = RingtoneManager.getRingtone(context, uri)
        return ringtone?.getTitle(context)?.toString()?.trim().orEmpty().ifBlank { "Alarmton" }
    }
}
