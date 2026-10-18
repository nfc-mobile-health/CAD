package com.example.cad.ui

import android.nfc.Tag
import android.nfc.tech.IsoDep
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cad.nfc.CardTransportException
import com.example.cad.nfc.IsoDepCardTransport
import com.example.cad.nfc.MCardReader
import com.example.cad.protocol.PatientBasicInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface CadScreen {
    data object Home : CadScreen
    data object Scan : CadScreen
    data class PatientFound(val patient: PatientBasicInfo) : CadScreen
}

sealed interface ScanState {
    data object Waiting : ScanState
    data object Working : ScanState
    data class Problem(val kind: ProblemKind) : ScanState
}

enum class ProblemKind { CARD_REMOVED, COULD_NOT_READ }

class ScanViewModel : ViewModel() {
    private val _screen = MutableStateFlow<CadScreen>(CadScreen.Home)
    val screen: StateFlow<CadScreen> = _screen.asStateFlow()
    private val _scanState = MutableStateFlow<ScanState>(ScanState.Waiting)
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()
    private val _seenToday = MutableStateFlow<List<PatientBasicInfo>>(emptyList())
    val seenToday: StateFlow<List<PatientBasicInfo>> = _seenToday.asStateFlow()

    fun startScan() { _scanState.value = ScanState.Waiting; _screen.value = CadScreen.Scan }
    fun cancelScan() { _screen.value = CadScreen.Home; _scanState.value = ScanState.Waiting }
    fun retry() = startScan()

    fun onTagDiscovered(tag: Tag) {
        if (_screen.value !is CadScreen.Scan || _scanState.value is ScanState.Working) return
        val isoDep = IsoDep.get(tag) ?: run {
            _scanState.value = ScanState.Problem(ProblemKind.COULD_NOT_READ)
            return
        }
        viewModelScope.launch {
            _scanState.value = ScanState.Working
            val transport = IsoDepCardTransport(isoDep)
            try {
                val patient = MCardReader(transport).readPatient()
                _seenToday.value = listOf(patient) + _seenToday.value.filterNot { it.patientId == patient.patientId }
                _screen.value = CadScreen.PatientFound(patient)
            } catch (_: CardTransportException.CardRemoved) {
                _scanState.value = ScanState.Problem(ProblemKind.CARD_REMOVED)
            } catch (_: Exception) {
                _scanState.value = ScanState.Problem(ProblemKind.COULD_NOT_READ)
            } finally {
                transport.close()
            }
        }
    }

}
