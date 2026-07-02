package de.feuerwehr.einsatzapp.fcm

import android.content.Context
import com.google.firebase.messaging.FirebaseMessaging
import de.feuerwehr.einsatzapp.data.CredentialStore
import de.feuerwehr.einsatzapp.data.FeuerwehrApiClient
import de.feuerwehr.einsatzapp.data.ServerConfigStore
import de.feuerwehr.einsatzapp.data.SessionRefreshHelper
import de.feuerwehr.einsatzapp.data.SessionStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await

/** FCM-Token holen und am Feuerwehr-Manager registrieren (inkl. stiller Wiederanmeldung). */
object DeviceRegistrationHelper {

    suspend fun ensureRegistered(context: Context): Result<Unit> = runCatching {
        val credentials = CredentialStore(context).load()
            ?: return@runCatching
        val sessionStore = SessionStore(context)
        if (sessionStore.session.first() == null) {
            if (!SessionRefreshHelper.silentReLogin(context)) {
                throw IllegalStateException("Wiederanmeldung für Geräteregistrierung fehlgeschlagen")
            }
        }
        val session = sessionStore.session.first()
            ?: throw IllegalStateException("Keine Session")
        if (session.unitId <= 0) {
            throw IllegalStateException("Keine Einheit am Benutzer")
        }
        val token = FirebaseMessaging.getInstance().token.await()
        sessionStore.saveFcmToken(token)
        val baseUrl = ServerConfigStore(context).currentServerBaseUrl()
        FeuerwehrApiClient.instance.registerDevice(
            baseUrl = baseUrl,
            sessionCookie = session.sessionCookie,
            unitId = session.unitId,
            fcmToken = token,
            deviceLabel = credentials.deviceName,
        ).getOrThrow()
    }
}
