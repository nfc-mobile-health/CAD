package com.smaple.cad.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import com.smaple.core.session.BackendApi
import com.smaple.core.session.CredentialStore

class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface SyncWorkerEntryPoint {
        fun credentialStore(): CredentialStore
        fun backendApi(): BackendApi
    }

    override suspend fun doWork(): Result {
        val entryPoint = EntryPointAccessors.fromApplication(applicationContext, SyncWorkerEntryPoint::class.java)
        val credentialStore = entryPoint.credentialStore()
        val backendApi = entryPoint.backendApi()

        if (!credentialStore.isLoggedIn) {
            return Result.success() // Do not sync while logged out
        }

        val unsynced = credentialStore.getUnsyncedRecords()
        if (unsynced.isEmpty()) return Result.success()

        var anyFailed = false
        for (record in unsynced) {
            val success = backendApi.submitRecord(record)
            if (success) {
                credentialStore.markRecordSynced(record.id)
            } else {
                anyFailed = true
            }
        }

        return if (anyFailed) Result.retry() else Result.success()
    }
}
