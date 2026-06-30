package de.feuerwehr.einsatzapp.fcm

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessaging
import de.feuerwehr.einsatzapp.data.FeuerwehrApiClient
import de.feuerwehr.einsatzapp.data.ServerConfigStore
import de.feuerwehr.einsatzapp.data.SessionStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await

object DeviceRegistrar {

    suspend fun registerIfPossible(
        sessionStore: SessionStore,
        serverConfigStore: ServerConfigStore,
        deviceLabel: String,
    ): Result<Unit> {
        val session = sessionStore.session.first() ?: return Result.failure(
            IllegalStateException("Nicht angemeldet"),
        )
        if (session.unitId <= 0) {
            return Result.failure(IllegalStateException("Keine Einheit am Benutzer hinterlegt"))
        }
        val token = fetchFcmToken() ?: return Result.failure(
            IllegalStateException("FCM-Token nicht verfügbar — google-services.json in Android Studio prüfen"),
        )
        sessionStore.saveFcmToken(token)
        val baseUrl = serverConfigStore.currentServerBaseUrl()
        return FeuerwehrApiClient.instance.registerDevice(
            baseUrl = baseUrl,
            sessionCookie = session.sessionCookie,
            unitId = session.unitId,
            fcmToken = token,
            deviceLabel = deviceLabel,
        )
    }

    private suspend fun fetchFcmToken(): String? = runCatching {
        FirebaseMessaging.getInstance().token.await()
    }.getOrNull()

    fun hasNotificationPermission(context: android.content.Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }
}
