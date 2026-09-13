package com.smaple.cad.ui

import com.smaple.core.fakes.FakeBackendApi
import com.smaple.core.fakes.FakeCredentialStore
import com.smaple.core.fakes.FakeSessionCoordinator
import com.smaple.core.model.MedicalRecord
import com.smaple.core.model.PatientProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class CadViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var mainViewModel: MainViewModel
    private lateinit var authViewModel: AuthViewModel
    private lateinit var backendApi: FakeBackendApi
    private lateinit var credentialStore: FakeCredentialStore
    private lateinit var sessionCoordinator: FakeSessionCoordinator

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        backendApi = FakeBackendApi()
        credentialStore = FakeCredentialStore()
        sessionCoordinator = FakeSessionCoordinator()
        mainViewModel = MainViewModel(sessionCoordinator, backendApi)
        authViewModel = AuthViewModel(credentialStore)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // regression test for architecture-decisions-log.md 2026-09-09, bug 2: duplicate record display
    @Test
    fun testDuplicateRecordDisplay_Regression2() = runTest {
        // Setup current patient context for loadHistory
        mainViewModel.onProfileReceived(PatientProfile("p1", "John"))
        
        val record = MedicalRecord("id1", "p1", "hp1", "John", "120/80")
        
        // Push record into session sent records
        mainViewModel.onRecordSent(record)
        
        // Push record into backend
        backendApi.submitRecord(record)
        
        // Load history
        mainViewModel.loadHistory()
        testScheduler.advanceUntilIdle()
        
        val history = mainViewModel.historyRecords.value
        assertEquals("Should deduplicate by stable ID, expecting 1 entry", 1, history.size)
    }

    // regression test for architecture-decisions-log.md 2026-09-09, bug 3: blank-field bleed
    @Test
    fun testBlankFieldBleed_Regression3() = runTest {
        mainViewModel.onProfileReceived(PatientProfile("p1", "John"))
        
        // Fill form
        mainViewModel.bp.value = "120/80"
        mainViewModel.hr.value = "72"
        mainViewModel.desc.value = "Full record"
        
        val recordA = mainViewModel.buildRecord()
        assertEquals("120/80", recordA.bloodPressure)
        assertEquals(72, recordA.heartRate)
        
        // Now CAD scans new patient B
        mainViewModel.clearPatient()
        mainViewModel.onProfileReceived(PatientProfile("p2", "Jane"))
        
        // Build without entering new vitals (blank)
        val recordB = mainViewModel.buildRecord()
        
        // If bug is present, recordB will have 120/80 and 72. If fixed, it should be null/blank.
        assertNull("Blank field bled from previous record", recordB.bloodPressure)
        assertNull("Blank field bled from previous record", recordB.heartRate)
        assertNull("Blank field bled from previous record", recordB.description)
    }
}
