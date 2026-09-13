/**
 * State holders bridging the UI layer with Track A fakes and protocol logic. Enforces regression invariants like distinct peer taps and full-record replacement.
 */
package com.smaple.cad.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smaple.core.model.MedicalRecord
import com.smaple.core.model.PatientProfile
import com.smaple.core.session.BackendApi
import com.smaple.core.session.CredentialStore
import com.smaple.core.session.LoginResult
import com.smaple.core.session.SessionCoordinator
import com.smaple.core.session.SessionEvent
import com.smaple.cad.sync.SyncManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val credentialStore: CredentialStore,
    private val backendApi: BackendApi,
    private val syncManager: SyncManager
) : ViewModel() {

    private val _isLoggedIn = MutableStateFlow(credentialStore.isLoggedIn)
    val isLoggedIn = _isLoggedIn.asStateFlow()

    private val _loginError = MutableStateFlow<String?>(null)
    val loginError = _loginError.asStateFlow()

    val registerId = MutableStateFlow("")
    val registerName = MutableStateFlow("")

    fun login(userId: String, pin: String) {
        viewModelScope.launch {
            when (val result = credentialStore.login(userId, pin)) {
                is LoginResult.Success -> {
                    _loginError.value = null
                    _isLoggedIn.value = true
                    syncManager.startSync()
                }
                is LoginResult.Error -> _loginError.value = result.message
                is LoginResult.InvalidCredentials -> _loginError.value = "Invalid PIN"
                is LoginResult.NoStoredData -> _loginError.value = "User not registered"
            }
        }
    }


    fun register(pin: String) {
        viewModelScope.launch {
            val res = backendApi.registerHp(registerId.value, registerName.value, "General", null, null, null)
            if (res.success) {
                login(registerId.value, pin)
            } else {
                _loginError.value = res.message
            }
        }
    }

    fun logout() {
        syncManager.stopSync()
        credentialStore.logout()
        _isLoggedIn.value = false
    }
}

@HiltViewModel
class MainViewModel @Inject constructor(
    private val sessionCoordinator: SessionCoordinator,
    private val backendApi: BackendApi,
    private val credentialStore: CredentialStore
) : ViewModel() {

    val sessionState = sessionCoordinator.sessionState
    
    // Scanned patient profile
    private val _currentPatient = MutableStateFlow<PatientProfile?>(null)
    val currentPatient = _currentPatient.asStateFlow()

    // Form state
    val bp = MutableStateFlow("")
    val hr = MutableStateFlow("")
    val rr = MutableStateFlow("")
    val temp = MutableStateFlow("")
    val med = MutableStateFlow("")
    val desc = MutableStateFlow("")

    // Session-only sent records
    private val _sessionSentRecords = MutableStateFlow<List<MedicalRecord>>(emptyList())
    val sessionSentRecords = _sessionSentRecords.asStateFlow()

    // History
    private val _historyRecords = MutableStateFlow<List<MedicalRecord>>(emptyList())
    val historyRecords = _historyRecords.asStateFlow()
    
    private val _historyError = MutableStateFlow<String?>(null)
    val historyError = _historyError.asStateFlow()

    fun startScan() {
        sessionCoordinator.reset()
        viewModelScope.launch {
            sessionCoordinator.startReaderSession()
        }
    }

    fun onProfileReceived(profile: PatientProfile) {
        _currentPatient.value = profile
    }

    fun buildRecord(): MedicalRecord {
        return MedicalRecord(
            patientId = _currentPatient.value?.patientId ?: "",
            hpId = "hp1", // Mocked HP for demo
            patientName = _currentPatient.value?.name ?: "",
            bloodPressure = bp.value.takeIf { it.isNotBlank() },
            heartRate = hr.value.toIntOrNull(),
            respiratoryRate = rr.value.toIntOrNull(),
            bodyTemperature = temp.value.toFloatOrNull(),
            medication = med.value.takeIf { it.isNotBlank() },
            description = desc.value.takeIf { it.isNotBlank() }
        )
    }

    fun prepareToSend(record: MedicalRecord) {
        sessionCoordinator.prepareRecord(record)
    }

    fun startSendSession() {
        viewModelScope.launch {
            sessionCoordinator.startReaderSession()
        }
    }
    
    fun onRecordSent(record: MedicalRecord) {
        val list = _sessionSentRecords.value.toMutableList()
        list.add(record)
        _sessionSentRecords.value = list
    }
    
    fun loadHistory() {
        val patientId = _currentPatient.value?.patientId ?: return
        viewModelScope.launch {
            try {
                val cloudRecords = backendApi.getRecords(patientId)
                val sessionRecords = _sessionSentRecords.value.filter { it.patientId == patientId }
                
                // Deduplicate by stable ID (Regression class fix)
                val merged = (sessionRecords + cloudRecords).distinctBy { it.id }
                _historyRecords.value = merged
                _historyError.value = null
            } catch (e: Exception) {
                _historyError.value = e.message
            }
        }
    }

    fun clearPatient() {
        _currentPatient.value = null
        // Regression 3 fix: hard-wipe form state
        bp.value = ""
        hr.value = ""
        rr.value = ""
        temp.value = ""
        med.value = ""
        desc.value = ""
    }

    fun clearSessionRecords() {
        _sessionSentRecords.value = emptyList()
    }
}
