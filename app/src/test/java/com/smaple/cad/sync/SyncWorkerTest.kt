package com.smaple.cad.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker.Result
import androidx.work.testing.TestListenableWorkerBuilder
import com.smaple.core.fakes.FakeBackendApi
import com.smaple.core.fakes.FakeCredentialStore
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.junit.Assert.assertTrue

@RunWith(RobolectricTestRunner::class)
class SyncWorkerTest {

    @Test
    fun testSyncFailsFastWhenNotLoggedIn() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val worker = TestListenableWorkerBuilder<SyncWorker>(context).build()
        
        val fakeStore = FakeCredentialStore()
        fakeStore.logout()
        
        worker.testCredentialStore = fakeStore
        worker.testBackendApi = FakeBackendApi()
        
        val result = worker.doWork()
        
        assertTrue(result is Result.Success)
    }
}
