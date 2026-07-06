package de.feuerwehr.einsatzapp.data

import com.squareup.moshi.Json

data class LoginRequest(
    val username: String,
    val password: String,
)

data class LoginResponse(
    val success: Boolean,
    val message: String? = null,
    @Json(name = "totpRequired") val totpRequired: Boolean = false,
    @Json(name = "userId") val userId: Long? = null,
    @Json(name = "displayName") val displayName: String? = null,
    @Json(name = "unitId") val unitId: Long? = null,
)

data class SessionResponse(
    val success: Boolean,
    val message: String? = null,
    @Json(name = "userId") val userId: Long? = null,
    @Json(name = "displayName") val displayName: String? = null,
    @Json(name = "unitId") val unitId: Long? = null,
)

data class RegisterDeviceRequest(
    @Json(name = "unitId") val unitId: Long,
    @Json(name = "fcmToken") val fcmToken: String,
    @Json(name = "deviceLabel") val deviceLabel: String,
    val platform: String = "android",
)

data class UnregisterDeviceRequest(
    @Json(name = "fcmToken") val fcmToken: String,
)

data class ApiMessageResponse(
    val success: Boolean,
    val message: String? = null,
)

data class DiveraAlarmsResponse(
    val success: Boolean,
    val message: String? = null,
    val alarms: List<DiveraAlarmSummary> = emptyList(),
)

data class DiveraAlarmSummary(
    val id: Long,
    val title: String? = null,
    val text: String? = null,
    val address: String? = null,
    @Json(name = "dateEpochSeconds") val dateEpochSeconds: Long = 0,
    @Json(name = "tsCreate") val tsCreate: Long = 0,
    val closed: Boolean = false,
)
