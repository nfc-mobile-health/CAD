package com.example.cad.ui

import android.app.Application
import android.nfc.Tag
import android.nfc.tech.IsoDep
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.cad.crypto.AuthenticationException
import com.example.cad.crypto.CardLockedException
import com.example.cad.nfc.CardTransportException
import com.example.cad.nfc.IsoDepCardTransport
import com.example.cad.nfc.MCardReader
import com.example.cad.protocol.BloodPressure
import com.example.cad.protocol.MedicalRecord
import com.example.cad.protocol.PatientBasicInfo
import com.example.cad.protocol.RecordCodec
import com.example.cad.storage.RecordStore
import com.example.cad.storage.SQLiteRecordStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant

sealed interface CadScreen {
    data object Home : CadScreen
    data object Scan : CadScreen
    data class PatientFound(val patient: PatientBasicInfo) : CadScreen
    data class VitalsForm(val patient: PatientBasicInfo) : CadScreen
    data class ReviewNote(
        val patient: PatientBasicInfo,
        val record: MedicalRecord,
        val blob: ByteArray,
    ) : CadScreen
    data class SaveNote(
        val patient: PatientBasicInfo,
        val record: MedicalRecord,
        val blob: ByteArray,
    ) : CadScreen
    data class Saved(
        val patient: PatientBasicInfo,
        val record: MedicalRecord,
    ) : CadScreen
    data class PastNotes(
        val patient: PatientBasicInfo,
        val notes: List<MedicalRecord> = emptyList(),
        val isLoading: Boolean = false,
        val errorMessage: String? = null,
    ) : CadScreen
}

sealed interface ScanState {
    data object Waiting : ScanState
    data object Working : ScanState
    data class Problem(val kind: ProblemKind) : ScanState
}

enum class ProblemKind {
    CARD_REMOVED,
    COULD_NOT_READ,
    AUTH_REFUSED,
    CARD_LOCKED,
    TIMED_OUT
}

sealed interface SaveState {
    data object Waiting : SaveState
    data object Working : SaveState
    data class Problem(val messageRes: Int) : SaveState
}

data class VitalsFormState(
    val systolic: String = "",
    val diastolic: String = "",
    val heartRate: String = "",
    val temperature: String = "",
    val isFahrenheit: Boolean = false,
    val medicines: String = "",
    val notes: String = "",
    val error: String? = null,
) {
    val isAnyFieldFilled: Boolean
        get() = systolic.isNotBlank() ||
            diastolic.isNotBlank() ||
            heartRate.isNotBlank() ||
            temperature.isNotBlank() ||
            medicines.isNotBlank() ||
            notes.isNotBlank()
}

class ScanViewModel(application: Application) : AndroidViewModel(application) {
    private val recordStore: RecordStore = SQLiteRecordStore(application)

    private val _screen = MutableStateFlow<CadScreen>(CadScreen.Home)
    val screen: StateFlow<CadScreen> = _screen.asStateFlow()

    private val _scanState = MutableStateFlow<ScanState>(ScanState.Waiting)
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    private val _saveState = MutableStateFlow<SaveState>(SaveState.Waiting)
    val saveState: StateFlow<SaveState> = _saveState.asStateFlow()

    private val _formState = MutableStateFlow(VitalsFormState())
    val formState: StateFlow<VitalsFormState> = _formState.asStateFlow()

    private val _seenToday = MutableStateFlow<List<PatientBasicInfo>>(emptyList())
    val seenToday: StateFlow<List<PatientBasicInfo>> = _seenToday.asStateFlow()

    private val _pendingCount = MutableStateFlow(0)
    val pendingCount: StateFlow<Int> = _pendingCount.asStateFlow()

    // HP ID default for Phase 2 (login will be added in Phase 4)
    var currentHpId: String = "N101"

    init {
        refreshPendingCount()
    }

    private fun refreshPendingCount() {
        viewModelScope.launch {
            _pendingCount.value = recordStore.countPending()
        }
    }

    fun startScan() {
        _scanState.value = ScanState.Waiting
        _screen.value = CadScreen.Scan
    }

    fun cancelScan() {
        _screen.value = CadScreen.Home
        _scanState.value = ScanState.Waiting
    }

    fun retry() = startScan()

    // ── Vitals Form Actions ─────────────────────────────────────────────

    fun openVitalsForm(patient: PatientBasicInfo) {
        _formState.value = VitalsFormState()
        _screen.value = CadScreen.VitalsForm(patient)
    }

    fun editVitalsForm(patient: PatientBasicInfo) {
        _screen.value = CadScreen.VitalsForm(patient)
    }

    fun updateSystolic(value: String) {
        _formState.value = _formState.value.copy(systolic = value.filter { it.isDigit() }, error = null)
    }

    fun updateDiastolic(value: String) {
        _formState.value = _formState.value.copy(diastolic = value.filter { it.isDigit() }, error = null)
    }

    fun updateHeartRate(value: String) {
        _formState.value = _formState.value.copy(heartRate = value.filter { it.isDigit() }, error = null)
    }

    fun updateTemperature(value: String) {
        val filtered = value.filter { it.isDigit() || it == '.' }
        _formState.value = _formState.value.copy(temperature = filtered, error = null)
    }

    fun toggleTemperatureUnit() {
        val current = _formState.value
        val newUnit = !current.isFahrenheit
        val converted = current.temperature.toDoubleOrNull()?.let { temp ->
            if (newUnit) {
                // C to F: (C * 9/5) + 32
                String.format(java.util.Locale.US, "%.1f", (temp * 9.0 / 5.0) + 32.0)
            } else {
                // F to C: (F - 32) * 5/9
                String.format(java.util.Locale.US, "%.1f", (temp - 32.0) * 5.0 / 9.0)
            }
        } ?: current.temperature
        _formState.value = current.copy(isFahrenheit = newUnit, temperature = converted, error = null)
    }

    fun updateMedicines(value: String) {
        _formState.value = _formState.value.copy(medicines = value, error = null)
    }

    fun updateNotes(value: String) {
        _formState.value = _formState.value.copy(notes = value, error = null)
    }

    fun reviewNote(patient: PatientBasicInfo): Boolean {
        val f = _formState.value
        if (!f.isAnyFieldFilled) {
            _formState.value = f.copy(error = "Fill at least one field")
            return false
        }

        val bp: BloodPressure? = when {
            f.systolic.isNotBlank() || f.diastolic.isNotBlank() -> {
                val sys = f.systolic.toIntOrNull()
                val dia = f.diastolic.toIntOrNull()
                if (sys == null || sys !in 40..295) {
                    _formState.value = f.copy(error = "Systolic must be 40 to 295 mmHg")
                    return false
                }
                if (dia == null || dia !in 20..275) {
                    _formState.value = f.copy(error = "Diastolic must be 20 to 275 mmHg")
                    return false
                }
                BloodPressure(sys, dia)
            }
            else -> null
        }

        val hr: Int? = if (f.heartRate.isNotBlank()) {
            val h = f.heartRate.toIntOrNull()
            if (h == null || h !in 0..255) {
                _formState.value = f.copy(error = "Heart rate must be 0 to 255 bpm")
                return false
            }
            h
        } else null

        val tempCelsius: Double? = if (f.temperature.isNotBlank()) {
            val t = f.temperature.toDoubleOrNull()
            if (t == null) {
                _formState.value = f.copy(error = "Invalid temperature")
                return false
            }
            val rawCelsius = if (f.isFahrenheit) (t - 32.0) * 5.0 / 9.0 else t
            val roundedCelsius = Math.round(rawCelsius * 10.0) / 10.0
            if (roundedCelsius < 30.0 || roundedCelsius > 55.5) {
                val unitStr = if (f.isFahrenheit) "86.0 to 131.9 °F" else "30.0 to 55.5 °C"
                _formState.value = f.copy(error = "Temperature must be $unitStr")
                return false
            }
            roundedCelsius
        } else null

        val medsList = if (f.medicines.isNotBlank()) {
            val items = f.medicines.split(';').map { it.trim() }.filter { it.isNotEmpty() }
            if (items.isEmpty()) {
                emptyList()
            } else {
                val joined = items.joinToString(";")
                if (joined.length > 60) {
                    _formState.value = f.copy(error = "Medicines text exceeds 60 characters limit")
                    return false
                }
                if (!joined.all { it.code in 0x20..0x7E }) {
                    _formState.value = f.copy(error = "Medicines must be printable ASCII text")
                    return false
                }
                items
            }
        } else emptyList()

        val desc = if (f.notes.isNotBlank()) {
            val trimmed = f.notes.trim()
            if (trimmed.length > 100) {
                _formState.value = f.copy(error = "Notes exceed 100 characters limit")
                return false
            }
            if (!trimmed.all { it.code in 0x20..0x7E }) {
                _formState.value = f.copy(error = "Notes must be printable ASCII text")
                return false
            }
            trimmed
        } else null

        val minutesNow = RecordCodec.minutesFromEpoch(Instant.now())
        val record = try {
            MedicalRecord(
                timestampMinutes = minutesNow,
                hpId = currentHpId,
                bloodPressure = bp,
                temperatureCelsius = tempCelsius,
                heartRateBpm = hr,
                medications = medsList,
                description = desc,
            )
        } catch (e: Exception) {
            _formState.value = f.copy(error = e.message ?: "Invalid record values")
            return false
        }

        val blob = RecordCodec.encode(record)
        _screen.value = CadScreen.ReviewNote(patient, record, blob)
        return true
    }

    fun proceedToSave(patient: PatientBasicInfo, record: MedicalRecord, blob: ByteArray) {
        _saveState.value = SaveState.Waiting
        _screen.value = CadScreen.SaveNote(patient, record, blob)
    }

    fun retrySave() {
        _saveState.value = SaveState.Waiting
    }

    fun openPastNotes(patient: PatientBasicInfo) {
        _screen.value = CadScreen.PastNotes(patient, isLoading = true)
        // Wait for next tag tap or load cached notes
        viewModelScope.launch {
            val localNotes = recordStore.getNotesForPatient(patient.patientId).map { it.decodedRecord }
            _screen.value = CadScreen.PastNotes(patient, notes = localNotes, isLoading = false)
        }
    }

    fun exitFlow(patient: PatientBasicInfo) {
        _formState.value = VitalsFormState()
        _screen.value = CadScreen.PatientFound(patient)
    }

    fun navigateToHome() {
        _formState.value = VitalsFormState()
        _screen.value = CadScreen.Home
    }

    // ── NFC Tag Dispatcher ──────────────────────────────────────────────

    fun onTagDiscovered(tag: Tag) {
        val currentScreen = _screen.value
        val isoDep = IsoDep.get(tag) ?: return

        when (currentScreen) {
            is CadScreen.Scan -> handleScanTap(isoDep)
            is CadScreen.SaveNote -> handleSaveTap(isoDep, currentScreen)
            is CadScreen.PastNotes -> handlePastNotesTap(isoDep, currentScreen)
            else -> Unit
        }
    }

    private fun handleScanTap(isoDep: IsoDep) {
        if (_scanState.value is ScanState.Working) return
        viewModelScope.launch {
            _scanState.value = ScanState.Working
            val transport = IsoDepCardTransport(isoDep)
            try {
                val reader = MCardReader(transport)
                reader.authenticate()
                val patient = reader.readPatient()
                _seenToday.value = listOf(patient) + _seenToday.value.filterNot { it.patientId == patient.patientId }
                _screen.value = CadScreen.PatientFound(patient)
            } catch (_: CardTransportException.CardRemoved) {
                _scanState.value = ScanState.Problem(ProblemKind.CARD_REMOVED)
            } catch (_: AuthenticationException) {
                _scanState.value = ScanState.Problem(ProblemKind.AUTH_REFUSED)
            } catch (_: CardLockedException) {
                _scanState.value = ScanState.Problem(ProblemKind.CARD_LOCKED)
            } catch (_: Exception) {
                _scanState.value = ScanState.Problem(ProblemKind.COULD_NOT_READ)
            } finally {
                transport.close()
            }
        }
    }

    private fun handleSaveTap(isoDep: IsoDep, screen: CadScreen.SaveNote) {
        if (_saveState.value is SaveState.Working) return
        viewModelScope.launch {
            _saveState.value = SaveState.Working
            val transport = IsoDepCardTransport(isoDep)
            try {
                val reader = MCardReader(transport)
                reader.authenticate()
                val verified = reader.writeAndVerify(screen.blob)
                if (verified) {
                    recordStore.saveNote(screen.patient.patientId, screen.blob)
                    refreshPendingCount()
                    _screen.value = CadScreen.Saved(screen.patient, screen.record)
                } else {
                    _saveState.value = SaveState.Problem(com.example.cad.R.string.save_problem_mismatch)
                }
            } catch (_: CardTransportException.CardRemoved) {
                _saveState.value = SaveState.Problem(com.example.cad.R.string.scan_lost)
            } catch (_: AuthenticationException) {
                _saveState.value = SaveState.Problem(com.example.cad.R.string.auth_refused)
            } catch (_: CardLockedException) {
                _saveState.value = SaveState.Problem(com.example.cad.R.string.card_locked)
            } catch (_: Exception) {
                _saveState.value = SaveState.Problem(com.example.cad.R.string.save_problem_failed)
            } finally {
                transport.close()
            }
        }
    }

    private fun handlePastNotesTap(isoDep: IsoDep, screen: CadScreen.PastNotes) {
        viewModelScope.launch {
            _screen.value = screen.copy(isLoading = true, errorMessage = null)
            val transport = IsoDepCardTransport(isoDep)
            try {
                val reader = MCardReader(transport)
                reader.authenticate()
                val blobs = reader.readAllRecords()
                val decoded = blobs.mapNotNull {
                    runCatching { RecordCodec.decode(it) }.getOrNull()
                }
                _screen.value = screen.copy(notes = decoded, isLoading = false)
            } catch (_: CardTransportException.CardRemoved) {
                _screen.value = screen.copy(isLoading = false, errorMessage = "Phones moved apart")
            } catch (_: AuthenticationException) {
                _screen.value = screen.copy(isLoading = false, errorMessage = "Authentication refused")
            } catch (_: CardLockedException) {
                _screen.value = screen.copy(isLoading = false, errorMessage = "Card is locked")
            } catch (_: Exception) {
                _screen.value = screen.copy(isLoading = false, errorMessage = "Could not read card history")
            } finally {
                transport.close()
            }
        }
    }
}
