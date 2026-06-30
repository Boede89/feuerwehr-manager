package de.feuerwehr.einsatzapp.data

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class FeuerwehrApiClient {

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun testServerConnection(baseUrl: String): Result<String> = runCatching {
        val request = Request.Builder()
            .url("${normalize(baseUrl)}/actuator/health")
            .get()
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Server antwortet mit HTTP ${response.code}")
            }
            "Verbindung OK"
        }
    }

    suspend fun login(baseUrl: String, username: String, password: String): Result<Pair<LoginResponse, String>> =
        runCatching {
            val body = moshi.adapter(LoginRequest::class.java)
                .toJson(LoginRequest(username.trim(), password))
                .toRequestBody(jsonMedia)
            val request = Request.Builder()
                .url("${normalize(baseUrl)}/api/v1/auth/login")
                .post(body)
                .build()
            http.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                val parsed = moshi.adapter(LoginResponse::class.java).fromJson(raw)
                    ?: throw IllegalStateException("Ungültige Server-Antwort")
                if (!response.isSuccessful && !parsed.success) {
                    throw IllegalStateException(parsed.message ?: "Anmeldung fehlgeschlagen")
                }
                if (!parsed.success) {
                    throw IllegalStateException(parsed.message ?: "Anmeldung fehlgeschlagen")
                }
                val cookie = extractSessionCookie(response.headers("Set-Cookie"))
                    ?: throw IllegalStateException("Keine Session vom Server erhalten")
                parsed to cookie
            }
        }

    suspend fun fetchSession(baseUrl: String, sessionCookie: String): Result<SessionResponse> = runCatching {
        val request = authorizedGet("${normalize(baseUrl)}/api/v1/auth/session", sessionCookie)
        http.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            val parsed = moshi.adapter(SessionResponse::class.java).fromJson(raw)
                ?: throw IllegalStateException("Ungültige Server-Antwort")
            if (!response.isSuccessful || !parsed.success) {
                throw IllegalStateException(parsed.message ?: "Session ungültig")
            }
            parsed
        }
    }

    suspend fun logout(baseUrl: String, sessionCookie: String): Result<Unit> = runCatching {
        val request = Request.Builder()
            .url("${normalize(baseUrl)}/api/v1/auth/logout")
            .header("Cookie", sessionCookie)
            .post("".toRequestBody(jsonMedia))
            .build()
        http.newCall(request).execute().use { /* ignore */ }
    }

    suspend fun registerDevice(
        baseUrl: String,
        sessionCookie: String,
        unitId: Long,
        fcmToken: String,
        deviceLabel: String,
    ): Result<Unit> = runCatching {
        val payload = moshi.adapter(RegisterDeviceRequest::class.java)
            .toJson(RegisterDeviceRequest(unitId, fcmToken, deviceLabel))
            .toRequestBody(jsonMedia)
        val request = Request.Builder()
            .url("${normalize(baseUrl)}/api/v1/einsatzapp/devices")
            .header("Cookie", sessionCookie)
            .post(payload)
            .build()
        http.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            val parsed = moshi.adapter(ApiMessageResponse::class.java).fromJson(raw)
            if (!response.isSuccessful || parsed?.success != true) {
                throw IllegalStateException(parsed?.message ?: "Geräteregistrierung fehlgeschlagen")
            }
        }
    }

    suspend fun fetchAlarms(baseUrl: String, sessionCookie: String, unitId: Long): Result<DiveraAlarmsResponse> =
        runCatching {
            val request = authorizedGet("${normalize(baseUrl)}/api/v1/units/$unitId/divera/alarms", sessionCookie)
            http.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                val parsed = moshi.adapter(DiveraAlarmsResponse::class.java).fromJson(raw)
                    ?: throw IllegalStateException("Ungültige Server-Antwort")
                if (!response.isSuccessful) {
                    throw IllegalStateException(parsed.message ?: "Einsätze nicht abrufbar")
                }
                parsed
            }
        }

    private fun authorizedGet(url: String, sessionCookie: String): Request =
        Request.Builder()
            .url(url)
            .header("Cookie", sessionCookie)
            .get()
            .build()

    private fun extractSessionCookie(setCookies: List<String>): String? {
        for (header in setCookies) {
            val part = header.substringBefore(';').trim()
            if (part.startsWith("JSESSIONID=")) {
                return part
            }
        }
        return null
    }

    private fun normalize(baseUrl: String): String = ServerConfigStore.normalizeServerUrl(baseUrl)

    companion object {
        val instance = FeuerwehrApiClient()
    }
}
