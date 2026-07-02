package de.feuerwehr.einsatzapp.data

import android.content.Context
import de.feuerwehr.einsatzapp.fcm.DeviceRegistrar

object SessionRefreshHelper {

    suspend fun silentReLogin(context: Context): Boolean {
        val credentialStore = CredentialStore(context)
        val credentials = credentialStore.load() ?: return false
        val serverConfigStore = ServerConfigStore(context)
        val sessionStore = SessionStore(context)
        val baseUrl = serverConfigStore.currentServerBaseUrl()
        val (loginResponse, cookie) = FeuerwehrApiClient.instance
            .login(baseUrl, credentials.username, credentials.password)
            .getOrNull() ?: return false
        if (loginResponse.totpRequired) {
            return false
        }
        val userId = loginResponse.userId ?: return false
        sessionStore.save(
            SessionStore.UserSession(
                sessionCookie = cookie,
                userId = userId,
                displayName = loginResponse.displayName ?: credentials.username,
                unitId = loginResponse.unitId ?: 0L,
            ),
        )
        DeviceRegistrar.registerIfPossible(sessionStore, serverConfigStore, credentials.deviceName)
        return true
    }
}
