package de.feuerwehr.einsatzapp.fcm

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import de.feuerwehr.einsatzapp.data.CredentialStore
import java.util.concurrent.TimeUnit

/** Hält FCM-Token und Server-Registrierung auch ohne App-Start aktuell (WorkManager). */
class TokenRefreshWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (CredentialStore(applicationContext).load() == null) {
            return Result.success()
        }
        return DeviceRegistrationHelper.ensureRegistered(applicationContext).fold(
            onSuccess = { Result.success() },
            onFailure = {
                if (runAttemptCount < MAX_RETRIES) {
                    Result.retry()
                } else {
                    Result.success()
                }
            },
        )
    }

    companion object {
        private const val WORK_NAME = "einsatzapp_token_refresh"
        private const val MAX_RETRIES = 3

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = PeriodicWorkRequestBuilder<TokenRefreshWorker>(INTERVAL_DAYS, TimeUnit.DAYS)
                .setConstraints(constraints)
                .setInitialDelay(INITIAL_DELAY_HOURS, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }

        /** Mindestens 24 h — WorkManager erlaubt periodische Jobs ab 15 Minuten. */
        private const val INTERVAL_DAYS = 1L
        private const val INITIAL_DELAY_HOURS = 6L
    }
}
