package com.example.cad.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.cad.R
import com.example.cad.protocol.BasicInfoCodec
import com.example.cad.protocol.BloodGroup
import com.example.cad.protocol.Gender
import com.example.cad.protocol.PatientBasicInfo
import java.time.LocalDate

@Composable
fun CadApp(viewModel: ScanViewModel) {
    val screen by viewModel.screen.collectAsState()
    val scanState by viewModel.scanState.collectAsState()
    val seenToday by viewModel.seenToday.collectAsState()
    when (val current = screen) {
        CadScreen.Home -> HomeScreen(seenToday, viewModel::startScan)
        CadScreen.Scan -> ScanScreen(scanState, viewModel::cancelScan, viewModel::retry)
        is CadScreen.PatientFound -> PatientFoundScreen(current.patient, viewModel::startScan)
    }
}

@Composable
private fun HomeScreen(seenToday: List<PatientBasicInfo>, onScan: () -> Unit) = Screen {
    Text(stringResource(R.string.home_title), style = MaterialTheme.typography.headlineLarge)
    Spacer(Modifier.height(20.dp))
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        MapleToken(TokenState.Ready, 188.dp, stringResource(R.string.token_ready))
    }
    Spacer(Modifier.height(20.dp))
    Text(stringResource(R.string.home_subtitle), style = MaterialTheme.typography.bodyLarge)
    Spacer(Modifier.weight(1f, true))
    PrimaryAction(R.string.scan_patient, onScan)
    Spacer(Modifier.height(24.dp))
    Text(stringResource(R.string.seen_today), style = MaterialTheme.typography.titleLarge)
    Spacer(Modifier.height(12.dp))
    Surface(shape = MaterialTheme.shapes.medium, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
        if (seenToday.isEmpty()) {
            Text(stringResource(R.string.no_patients_seen), Modifier.padding(20.dp), style = MaterialTheme.typography.bodyMedium)
        } else {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                seenToday.forEach { patient ->
                    Text(patient.name, style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.patient_id_value, patient.patientId), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun ScanScreen(state: ScanState, onCancel: () -> Unit, onRetry: () -> Unit) = Screen {
    val copy = when (state) {
        ScanState.Waiting -> ScanCopy(TokenState.Ready, R.string.token_ready, R.string.scan_waiting)
        ScanState.Working -> ScanCopy(TokenState.Working, R.string.token_working, R.string.scan_working)
        is ScanState.Problem -> ScanCopy(
            TokenState.Problem, R.string.token_problem,
            if (state.kind == ProblemKind.CARD_REMOVED) R.string.scan_lost else R.string.scan_not_supported,
        )
    }
    Text(stringResource(R.string.scan_title), style = MaterialTheme.typography.headlineLarge)
    Spacer(Modifier.height(28.dp))
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        MapleToken(copy.token, 188.dp, stringResource(copy.description))
    }
    Spacer(Modifier.height(28.dp))
    Text(
        stringResource(copy.message),
        modifier = Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.weight(1f, true))
    if (state is ScanState.Problem) { PrimaryAction(R.string.try_again, onRetry); Spacer(Modifier.height(12.dp)) }
    TextButton(onCancel, Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
        Text(stringResource(R.string.cancel), style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun PatientFoundScreen(patient: PatientBasicInfo, onScanDifferent: () -> Unit) = Screen {
    Text(stringResource(R.string.patient_found), style = MaterialTheme.typography.headlineMedium)
    Spacer(Modifier.height(16.dp))
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        MapleToken(TokenState.Done, 92.dp, stringResource(R.string.token_done))
    }
    Spacer(Modifier.height(16.dp))
    Surface(shape = MaterialTheme.shapes.large, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(patient.name, style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.age_years, BasicInfoCodec.ageOn(patient, LocalDate.now())), style = MaterialTheme.typography.bodyLarge)
            Detail(R.string.gender, stringResource(patient.gender.label()))
            Detail(R.string.blood_group, stringResource(patient.bloodGroup.label()))
            Detail(R.string.patient_id, patient.patientId)
        }
    }
    Spacer(Modifier.weight(1f, true))
    PrimaryAction(R.string.add_vitals, {})
    Spacer(Modifier.height(12.dp))
    Button(
        onClick = {}, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        colors = ButtonDefaults.outlinedButtonColors(), border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
    ) { Text(stringResource(R.string.past_notes), style = MaterialTheme.typography.labelLarge) }
    TextButton(onScanDifferent, Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
        Text(stringResource(R.string.scan_different), style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun Detail(@StringRes label: Int, value: String) {
    Text(stringResource(label), style = MaterialTheme.typography.labelMedium)
    Text(value, style = MaterialTheme.typography.bodyLarge)
}

@Composable
private fun PrimaryAction(@StringRes label: Int, onClick: () -> Unit) = Button(
    onClick, Modifier.fillMaxWidth().heightIn(min = 64.dp),
) { Text(stringResource(label), style = MaterialTheme.typography.labelLarge) }

@Composable
private fun Screen(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) = Scaffold(
    containerColor = MaterialTheme.colorScheme.background,
) { padding ->
    Column(
        Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp, vertical = 20.dp)
            .widthIn(max = 600.dp).verticalScroll(rememberScrollState()),
        content = content,
    )
}

private data class ScanCopy(val token: TokenState, @param:StringRes val description: Int, @param:StringRes val message: Int)

@StringRes
private fun Gender.label(): Int = when (this) {
    Gender.MALE -> R.string.gender_male
    Gender.FEMALE -> R.string.gender_female
    Gender.NON_BINARY -> R.string.gender_non_binary
    Gender.NOT_STATED -> R.string.gender_not_stated
}

@StringRes
private fun BloodGroup.label(): Int = when (this) {
    BloodGroup.UNKNOWN -> R.string.blood_unknown
    BloodGroup.A_POSITIVE -> R.string.blood_a_positive
    BloodGroup.A_NEGATIVE -> R.string.blood_a_negative
    BloodGroup.B_POSITIVE -> R.string.blood_b_positive
    BloodGroup.B_NEGATIVE -> R.string.blood_b_negative
    BloodGroup.AB_POSITIVE -> R.string.blood_ab_positive
    BloodGroup.AB_NEGATIVE -> R.string.blood_ab_negative
    BloodGroup.O_POSITIVE -> R.string.blood_o_positive
    BloodGroup.O_NEGATIVE -> R.string.blood_o_negative
}
