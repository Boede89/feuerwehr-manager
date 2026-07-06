package de.feuerwehr.einsatzapp.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Vom FCM-Service befüllt; MainViewModel übernimmt die Liste bei Push. */
object AlarmRefreshBus {
    private val _alarms = MutableStateFlow<List<DiveraAlarmSummary>?>(null)
    val alarms: StateFlow<List<DiveraAlarmSummary>?> = _alarms.asStateFlow()

    fun publish(alarms: List<DiveraAlarmSummary>) {
        _alarms.value = alarms
    }
}
