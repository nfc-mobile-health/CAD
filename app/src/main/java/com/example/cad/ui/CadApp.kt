package com.example.cad.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cad.R
import com.example.cad.protocol.BasicInfoCodec
import com.example.cad.protocol.BloodGroup
import com.example.cad.protocol.Gender
import com.example.cad.protocol.MedicalRecord
import com.example.cad.protocol.PatientBasicInfo
import com.example.cad.protocol.RecordCodec
import com.example.cad.ui.theme.MapleColors
import java.time.LocalDate

enum class FlowStep(val stepIndex: Int) {
    Scan(0), Vitals(1), Save(2)
}

@Composable
fun CadApp(viewModel: ScanViewModel) {
    val screen by viewModel.screen.collectAsState()
    val scanState by viewModel.scanState.collectAsState()
    val saveState by viewModel.saveState.collectAsState()
    val formState by viewModel.formState.collectAsState()
    val seenToday by viewModel.seenToday.collectAsState()
    val pendingCount by viewModel.pendingCount.collectAsState()

    when (val current = screen) {
        CadScreen.Home -> HomeScreen(
            seenToday = seenToday,
            pendingCount = pendingCount,
            onScan = viewModel::startScan,
        )
        CadScreen.Scan -> ScanScreen(
            state = scanState,
            onCancel = viewModel::cancelScan,
            onRetry = viewModel::retry,
        )
        is CadScreen.PatientFound -> PatientFoundScreen(
            patient = current.patient,
            onAddVitals = { viewModel.openVitalsForm(current.patient) },
            onPastNotes = { viewModel.openPastNotes(current.patient) },
            onScanDifferent = viewModel::startScan,
        )
        is CadScreen.VitalsForm -> VitalsFormScreen(
            patient = current.patient,
            form = formState,
            onUpdateSystolic = viewModel::updateSystolic,
            onUpdateDiastolic = viewModel::updateDiastolic,
            onUpdateHeartRate = viewModel::updateHeartRate,
            onUpdateTemperature = viewModel::updateTemperature,
            onToggleTempUnit = viewModel::toggleTemperatureUnit,
            onUpdateMedicines = viewModel::updateMedicines,
            onUpdateNotes = viewModel::updateNotes,
            onReviewNote = { viewModel.reviewNote(current.patient) },
            onCancel = { viewModel.exitFlow(current.patient) },
        )
        is CadScreen.ReviewNote -> ReviewNoteScreen(
            patient = current.patient,
            record = current.record,
            onEdit = { viewModel.editVitalsForm(current.patient) },
            onConfirmSave = { viewModel.proceedToSave(current.patient, current.record, current.blob) },
        )
        is CadScreen.SaveNote -> SaveNoteScreen(
            patient = current.patient,
            state = saveState,
            onRetry = viewModel::retrySave,
            onCancel = { viewModel.exitFlow(current.patient) },
        )
        is CadScreen.Saved -> SavedScreen(
            patient = current.patient,
            pendingCount = pendingCount,
            onScanNext = viewModel::startScan,
            onHome = viewModel::navigateToHome,
        )
        is CadScreen.PastNotes -> PastNotesScreen(
            patient = current.patient,
            notes = current.notes,
            isLoading = current.isLoading,
            errorMessage = current.errorMessage,
            onBack = { viewModel.exitFlow(current.patient) },
        )
    }
}

// ── Step Bar Component ──────────────────────────────────────────────────

@Composable
fun StepBar(currentStep: FlowStep, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FlowStep.entries.forEach { step ->
                val isActiveOrDone = step.stepIndex <= currentStep.stepIndex
                val color = if (isActiveOrDone) MapleColors.Pine else MapleColors.Mist2
                Box(
                    Modifier
                        .weight(1f)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(color)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            FlowStep.entries.forEach { step ->
                val isCurrent = step == currentStep
                val label = when (step) {
                    FlowStep.Scan -> stringResource(R.string.step_scan)
                    FlowStep.Vitals -> stringResource(R.string.step_vitals)
                    FlowStep.Save -> stringResource(R.string.step_save)
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                        color = if (isCurrent) MapleColors.Pine else MapleColors.Moss,
                        fontSize = 16.sp,
                    ),
                )
            }
        }
    }
}

// ── Patient Chip ────────────────────────────────────────────────────────

@Composable
fun PatientChip(patient: PatientBasicInfo, modifier: Modifier = Modifier) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MapleColors.Mist,
        border = BorderStroke(1.dp, MapleColors.Line),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    patient.name,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MapleColors.PineDeep,
                    )
                )
                Text(
                    stringResource(R.string.patient_id_value, patient.patientId),
                    style = MaterialTheme.typography.bodySmall.copy(color = MapleColors.Moss),
                )
            }
            Surface(
                shape = CircleShape,
                color = MapleColors.White,
                border = BorderStroke(1.dp, MapleColors.Line),
            ) {
                Text(
                    stringResource(patient.bloodGroup.label()),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MapleColors.Pine,
                    )
                )
            }
        }
    }
}

// ── 1. Home Screen ──────────────────────────────────────────────────────

@Composable
private fun HomeScreen(
    seenToday: List<PatientBasicInfo>,
    pendingCount: Int,
    onScan: () -> Unit,
) = Screen {
    Text(stringResource(R.string.home_title), style = MaterialTheme.typography.headlineLarge)
    Spacer(Modifier.height(20.dp))
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        MapleToken(TokenState.Ready, 188.dp, stringResource(R.string.token_ready))
    }
    Spacer(Modifier.height(20.dp))
    Text(stringResource(R.string.home_subtitle), style = MaterialTheme.typography.bodyLarge)

    if (pendingCount > 0) {
        Spacer(Modifier.height(16.dp))
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MapleColors.AmberTint,
            border = BorderStroke(1.dp, MapleColors.Amber),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    if (pendingCount == 1) stringResource(R.string.saved_subtitle_single)
                    else stringResource(R.string.saved_subtitle_multiple, pendingCount),
                    style = MaterialTheme.typography.labelLarge.copy(color = MapleColors.Ink),
                )
            }
        }
    }

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

// ── 2. Scan Screen ──────────────────────────────────────────────────────

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
    if (state is ScanState.Problem) {
        PrimaryAction(R.string.try_again, onRetry)
        Spacer(Modifier.height(12.dp))
    }
    TextButton(onCancel, Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
        Text(stringResource(R.string.cancel), style = MaterialTheme.typography.labelLarge)
    }
}

// ── 3. Patient Found Screen ─────────────────────────────────────────────

@Composable
private fun PatientFoundScreen(
    patient: PatientBasicInfo,
    onAddVitals: () -> Unit,
    onPastNotes: () -> Unit,
    onScanDifferent: () -> Unit,
) = Screen {
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
    PrimaryAction(R.string.add_vitals, onAddVitals)
    Spacer(Modifier.height(12.dp))
    Button(
        onClick = onPastNotes,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        colors = ButtonDefaults.outlinedButtonColors(),
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
    ) {
        Text(stringResource(R.string.past_notes), style = MaterialTheme.typography.labelLarge)
    }
    TextButton(onScanDifferent, Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
        Text(stringResource(R.string.scan_different), style = MaterialTheme.typography.labelLarge)
    }
}

// ── 4. Add Vitals Form Screen ───────────────────────────────────────────

@Composable
private fun VitalsFormScreen(
    patient: PatientBasicInfo,
    form: VitalsFormState,
    onUpdateSystolic: (String) -> Unit,
    onUpdateDiastolic: (String) -> Unit,
    onUpdateHeartRate: (String) -> Unit,
    onUpdateTemperature: (String) -> Unit,
    onToggleTempUnit: () -> Unit,
    onUpdateMedicines: (String) -> Unit,
    onUpdateNotes: (String) -> Unit,
    onReviewNote: () -> Unit,
    onCancel: () -> Unit,
) = Screen {
    var showLeaveDialog by remember { mutableStateOf(false) }

    if (showLeaveDialog) {
        AlertDialog(
            onDismissRequest = { showLeaveDialog = false },
            title = { Text(stringResource(R.string.dialog_leave_title), style = MaterialTheme.typography.titleMedium) },
            confirmButton = {
                TextButton(onClick = {
                    showLeaveDialog = false
                    onCancel()
                }) {
                    Text(stringResource(R.string.dialog_leave_confirm), style = MaterialTheme.typography.labelLarge, color = MapleColors.Red)
                }
            },
            dismissButton = {
                Button(
                    onClick = { showLeaveDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = MapleColors.Pine),
                ) {
                    Text(stringResource(R.string.dialog_stay), style = MaterialTheme.typography.labelLarge)
                }
            },
        )
    }

    StepBar(FlowStep.Vitals)
    Spacer(Modifier.height(16.dp))

    PatientChip(patient)
    Spacer(Modifier.height(16.dp))

    Text(stringResource(R.string.vitals_title), style = MaterialTheme.typography.headlineMedium)
    Spacer(Modifier.height(4.dp))
    Text(
        stringResource(R.string.vitals_helper),
        style = MaterialTheme.typography.bodyMedium.copy(color = MapleColors.Moss),
    )
    Spacer(Modifier.height(16.dp))

    // Blood Pressure
    Text(stringResource(R.string.label_bp), style = MaterialTheme.typography.labelMedium)
    Spacer(Modifier.height(4.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = form.systolic,
            onValueChange = onUpdateSystolic,
            placeholder = { Text("120") },
            label = { Text(stringResource(R.string.label_systolic)) },
            modifier = Modifier.weight(1f),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            shape = RoundedCornerShape(16.dp),
            singleLine = true,
        )
        Text("/", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(
            value = form.diastolic,
            onValueChange = onUpdateDiastolic,
            placeholder = { Text("80") },
            label = { Text(stringResource(R.string.label_diastolic)) },
            modifier = Modifier.weight(1f),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            shape = RoundedCornerShape(16.dp),
            singleLine = true,
        )
        MicButton(stringResource(R.string.label_bp))
    }
    Spacer(Modifier.height(16.dp))

    // Heart Rate
    Text(stringResource(R.string.label_heart_rate), style = MaterialTheme.typography.labelMedium)
    Spacer(Modifier.height(4.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = form.heartRate,
            onValueChange = onUpdateHeartRate,
            placeholder = { Text("72") },
            modifier = Modifier.weight(1f),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            shape = RoundedCornerShape(16.dp),
            singleLine = true,
            trailingIcon = { Text(stringResource(R.string.unit_bpm), Modifier.padding(end = 12.dp), style = MaterialTheme.typography.bodyMedium.copy(color = MapleColors.Moss)) },
        )
        MicButton(stringResource(R.string.label_heart_rate))
    }
    Spacer(Modifier.height(16.dp))

    // Temperature
    Text(stringResource(R.string.label_temperature), style = MaterialTheme.typography.labelMedium)
    Spacer(Modifier.height(4.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = form.temperature,
            onValueChange = onUpdateTemperature,
            placeholder = { Text(if (form.isFahrenheit) "98.6" else "37.0") },
            modifier = Modifier.weight(1f),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            shape = RoundedCornerShape(16.dp),
            singleLine = true,
        )
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MapleColors.Mist,
            border = BorderStroke(1.dp, MapleColors.Line),
            modifier = Modifier.clickable { onToggleTempUnit() }.height(56.dp),
        ) {
            Box(Modifier.padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                Text(
                    if (form.isFahrenheit) "°F" else "°C",
                    style = MaterialTheme.typography.labelLarge.copy(color = MapleColors.PineDeep),
                )
            }
        }
        MicButton(stringResource(R.string.label_temperature))
    }
    Spacer(Modifier.height(16.dp))

    // Medicines
    Text(stringResource(R.string.label_medicines), style = MaterialTheme.typography.labelMedium)
    Spacer(Modifier.height(4.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = form.medicines,
            onValueChange = onUpdateMedicines,
            placeholder = { Text("Paracetamol 500 mg; ORS") },
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(16.dp),
            supportingText = { Text(stringResource(R.string.medicines_helper)) },
        )
        MicButton(stringResource(R.string.label_medicines))
    }
    Spacer(Modifier.height(16.dp))

    // Notes
    Text(stringResource(R.string.label_notes), style = MaterialTheme.typography.labelMedium)
    Spacer(Modifier.height(4.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = form.notes,
            onValueChange = onUpdateNotes,
            placeholder = { Text("Cough, advised rest.") },
            modifier = Modifier.weight(1f).heightIn(min = 84.dp),
            shape = RoundedCornerShape(16.dp),
            maxLines = 4,
        )
        MicButton(stringResource(R.string.label_notes))
    }

    if (form.error != null) {
        Spacer(Modifier.height(16.dp))
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MapleColors.RedTint,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                form.error,
                Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium.copy(color = MapleColors.Red),
            )
        }
    }

    Spacer(Modifier.height(24.dp))

    if (!form.isAnyFieldFilled) {
        Text(
            stringResource(R.string.vitals_fill_at_least_one),
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodyMedium.copy(color = MapleColors.Moss, textAlign = TextAlign.Center),
        )
        Spacer(Modifier.height(8.dp))
    }

    Button(
        onClick = onReviewNote,
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
        enabled = form.isAnyFieldFilled,
        colors = ButtonDefaults.buttonColors(
            containerColor = MapleColors.Pine,
            disabledContainerColor = MapleColors.Mist2,
            disabledContentColor = MapleColors.Moss,
        ),
    ) {
        Text(stringResource(R.string.btn_review_note), style = MaterialTheme.typography.labelLarge)
    }

    Spacer(Modifier.height(12.dp))
    TextButton(
        onClick = {
            if (form.isAnyFieldFilled) showLeaveDialog = true else onCancel()
        },
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
    ) {
        Text(stringResource(R.string.cancel), style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun MicButton(fieldLabel: String) {
    Surface(
        shape = CircleShape,
        color = MapleColors.Mist,
        modifier = Modifier
            .size(48.dp)
            .semantics { contentDescription = "Voice input for $fieldLabel" }
            .clickable { /* SpeechRecognizer or audio prompt */ },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text("🎤", fontSize = 20.sp)
        }
    }
}

// ── 5. Review Note Screen ───────────────────────────────────────────────

@Composable
private fun ReviewNoteScreen(
    patient: PatientBasicInfo,
    record: MedicalRecord,
    onEdit: () -> Unit,
    onConfirmSave: () -> Unit,
) = Screen {
    StepBar(FlowStep.Vitals)
    Spacer(Modifier.height(16.dp))

    PatientChip(patient)
    Spacer(Modifier.height(16.dp))

    Text(stringResource(R.string.review_title), style = MaterialTheme.typography.headlineMedium)
    Spacer(Modifier.height(16.dp))

    Surface(
        shape = MaterialTheme.shapes.large,
        color = MapleColors.White,
        border = BorderStroke(1.dp, MapleColors.Line),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            record.bloodPressure?.let {
                Detail(R.string.label_bp, "${it.systolic} / ${it.diastolic} mmHg")
            }
            record.temperatureCelsius?.let {
                Detail(R.string.label_temperature, String.format(java.util.Locale.US, "%.1f °C", it))
            }
            record.heartRateBpm?.let {
                Detail(R.string.label_heart_rate, "$it per minute")
            }
            if (record.medications.isNotEmpty()) {
                Detail(R.string.label_medicines, record.medications.joinToString("; "))
            }
            record.description?.let {
                Detail(R.string.label_notes, it)
            }
        }
    }

    Spacer(Modifier.weight(1f, true))
    PrimaryAction(R.string.btn_save_to_card, onConfirmSave)
    Spacer(Modifier.height(12.dp))
    Button(
        onClick = onEdit,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        colors = ButtonDefaults.outlinedButtonColors(),
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
    ) {
        Text(stringResource(R.string.btn_edit), style = MaterialTheme.typography.labelLarge)
    }
}

// ── 6. Save Note Screen ─────────────────────────────────────────────────

@Composable
private fun SaveNoteScreen(
    patient: PatientBasicInfo,
    state: SaveState,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
) = Screen {
    StepBar(FlowStep.Save)
    Spacer(Modifier.height(24.dp))

    val tokenState = when (state) {
        SaveState.Waiting, SaveState.Working -> TokenState.Working
        is SaveState.Problem -> TokenState.Problem
    }
    val message = when (state) {
        SaveState.Waiting, SaveState.Working -> stringResource(R.string.save_instruction)
        is SaveState.Problem -> stringResource(state.messageRes)
    }

    Text(stringResource(R.string.save_title), style = MaterialTheme.typography.headlineLarge)
    Spacer(Modifier.height(28.dp))
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        MapleToken(tokenState, 188.dp, stringResource(if (tokenState == TokenState.Problem) R.string.token_problem else R.string.token_working))
    }
    Spacer(Modifier.height(28.dp))
    Text(
        message,
        modifier = Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
    )

    Spacer(Modifier.weight(1f, true))
    if (state is SaveState.Problem) {
        PrimaryAction(R.string.btn_save_again, onRetry)
        Spacer(Modifier.height(12.dp))
    }
    TextButton(onCancel, Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
        Text(stringResource(R.string.cancel), style = MaterialTheme.typography.labelLarge)
    }
}

// ── 7. Saved Screen ─────────────────────────────────────────────────────

@Composable
private fun SavedScreen(
    patient: PatientBasicInfo,
    pendingCount: Int,
    onScanNext: () -> Unit,
    onHome: () -> Unit,
) = Screen {
    StepBar(FlowStep.Save)
    Spacer(Modifier.height(24.dp))

    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        MapleToken(TokenState.Done, 188.dp, stringResource(R.string.token_done))
    }
    Spacer(Modifier.height(28.dp))

    Text(
        stringResource(R.string.saved_title, patient.name),
        modifier = Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.headlineMedium,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(8.dp))
    Text(
        if (pendingCount <= 1) stringResource(R.string.saved_subtitle_single)
        else stringResource(R.string.saved_subtitle_multiple, pendingCount),
        modifier = Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.bodyLarge.copy(color = MapleColors.Moss),
        textAlign = TextAlign.Center,
    )

    Spacer(Modifier.weight(1f, true))
    PrimaryAction(R.string.btn_scan_next, onScanNext)
    Spacer(Modifier.height(12.dp))
    TextButton(onHome, Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
        Text("Home", style = MaterialTheme.typography.labelLarge)
    }
}

// ── 8. Past Notes Screen ────────────────────────────────────────────────

@Composable
private fun PastNotesScreen(
    patient: PatientBasicInfo,
    notes: List<MedicalRecord>,
    isLoading: Boolean,
    errorMessage: String?,
    onBack: () -> Unit,
) = Screen {
    PatientChip(patient)
    Spacer(Modifier.height(16.dp))

    Text(stringResource(R.string.past_notes_title), style = MaterialTheme.typography.headlineMedium)
    Spacer(Modifier.height(12.dp))

    if (isLoading) {
        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = MapleColors.Pine)
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.past_notes_loading), style = MaterialTheme.typography.bodyMedium)
            }
        }
    } else if (errorMessage != null) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MapleColors.RedTint,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(errorMessage, Modifier.padding(16.dp), color = MapleColors.Red)
        }
    } else if (notes.isEmpty()) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            border = BorderStroke(1.dp, MapleColors.Line),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.past_notes_empty), Modifier.padding(20.dp), style = MaterialTheme.typography.bodyMedium)
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            notes.forEach { note ->
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MapleColors.White,
                    border = BorderStroke(1.dp, MapleColors.Line),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                stringResource(R.string.written_by, note.hpId),
                                style = MaterialTheme.typography.labelMedium.copy(color = MapleColors.PineDeep),
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            note.bloodPressure?.let {
                                Chip("BP ${it.systolic}/${it.diastolic}")
                            }
                            note.temperatureCelsius?.let {
                                Chip(String.format(java.util.Locale.US, "%.1f°C", it))
                            }
                            note.heartRateBpm?.let {
                                Chip("HR $it")
                            }
                        }
                        if (note.medications.isNotEmpty()) {
                            Text("Meds: " + note.medications.joinToString("; "), style = MaterialTheme.typography.bodySmall)
                        }
                        note.description?.let {
                            Text("Note: $it", style = MaterialTheme.typography.bodySmall.copy(color = MapleColors.Moss))
                        }
                    }
                }
            }
        }
    }

    Spacer(Modifier.weight(1f, true))
    Button(
        onClick = onBack,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MapleColors.Pine),
    ) {
        Text(stringResource(R.string.btn_back), style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun Chip(text: String) {
    Surface(
        shape = CircleShape,
        color = MapleColors.Mist,
    ) {
        Text(
            text,
            Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = MapleColors.PineDeep),
        )
    }
}

// ── Shared Helpers ──────────────────────────────────────────────────────

@Composable
private fun Detail(@StringRes label: Int, value: String) {
    Column {
        Text(stringResource(label), style = MaterialTheme.typography.labelMedium.copy(color = MapleColors.Moss))
        Text(value, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
    }
}

@Composable
private fun PrimaryAction(@StringRes label: Int, onClick: () -> Unit) = Button(
    onClick, Modifier.fillMaxWidth().heightIn(min = 64.dp),
    colors = ButtonDefaults.buttonColors(containerColor = MapleColors.Pine),
) { Text(stringResource(label), style = MaterialTheme.typography.labelLarge) }

@Composable
private fun Screen(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) = Scaffold(
    containerColor = MaterialTheme.colorScheme.background,
) { padding ->
    Column(
        Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = 24.dp, vertical = 20.dp)
            .widthIn(max = 600.dp)
            .verticalScroll(rememberScrollState()),
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
