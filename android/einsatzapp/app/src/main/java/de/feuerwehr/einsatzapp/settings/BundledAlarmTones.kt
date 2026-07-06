package de.feuerwehr.einsatzapp.settings

import android.content.Context
import android.net.Uri
import de.feuerwehr.einsatzapp.R

/**
 * Eingebaute Alarm-MP3s aus [res/raw] — Dateinamen müssen mit `tone_` beginnen
 * (z. B. [tone_feuerwehr_alarm.mp3] → Anzeige „Feuerwehr Alarm“).
 *
 * Dateien ablegen: [android/einsatzapp/alarm-tones-source/], dann
 * `scripts/install-alarm-tones.ps1` oder `.sh` ausführen und App neu bauen.
 */
object BundledAlarmTones {

    private val TITLE_OVERRIDES = mapOf(
        "tone_quattrolang" to R.string.alarm_tone_quattrolang,
        "tone_quattro_96_schleife" to R.string.alarm_tone_quattro_96_schleife,
        "tone_quattro_98_1" to R.string.alarm_tone_quattro_98_1,
        "tone_quattro_98_2" to R.string.alarm_tone_quattro_98_2,
        "tone_swissphone_q96" to R.string.alarm_tone_swissphone_q96,
    )

    fun load(context: Context): List<AlarmToneCatalog.AlarmTone> {
        return R.raw::class.java.fields
            .asSequence()
            .filter { field -> field.name.startsWith("tone_") }
            .mapNotNull { field ->
                val resId = runCatching { field.getInt(null) }.getOrNull() ?: return@mapNotNull null
                if (resId == 0) return@mapNotNull null
                val uri = Uri.parse("android.resource://${context.packageName}/$resId")
                val titleRes = TITLE_OVERRIDES[field.name]
                val title = if (titleRes != null) {
                    context.getString(titleRes)
                } else {
                    titleFromResourceName(field.name)
                }
                AlarmToneCatalog.AlarmTone(
                    id = uri.toString(),
                    title = title,
                    uri = uri,
                    bundled = true,
                )
            }
            .sortedBy { it.title.lowercase() }
            .toList()
    }

    fun titleForResourceName(resourceName: String): String =
        titleFromResourceName(resourceName)

    private fun titleFromResourceName(resourceName: String): String =
        resourceName
            .removePrefix("tone_")
            .split('_')
            .filter { it.isNotBlank() }
            .joinToString(" ") { part -> part.replaceFirstChar { c -> c.titlecase() } }
            .ifBlank { "App-Alarmton" }
}
