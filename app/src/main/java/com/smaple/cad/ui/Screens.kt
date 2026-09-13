/**
 * UI Screens for CAD.
 */
package com.smaple.cad.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.smaple.cad.R
import com.smaple.core.payload.MedicalRecordTextCodec
import com.smaple.core.protocol.TransferResult
import com.smaple.core.session.SessionEvent

@Composable
fun LoginScreen(viewModel: AuthViewModel, onLoginSuccess: () -> Unit) {
    var id by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var showRegister by remember { mutableStateOf(false) }
    
    val error by viewModel.loginError.collectAsState()
    
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(text = stringResource(R.string.login_title), style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(32.dp))
        OutlinedTextField(value = id, onValueChange = { id = it }, label = { Text(stringResource(R.string.login_id_hint)) }, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(value = pin, onValueChange = { pin = it }, label = { Text(stringResource(R.string.login_pin_hint)) }, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(24.dp))
        
        if (error != null) {
            Text(text = error!!, color = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.height(16.dp))
        }
        
        Button(
            onClick = { viewModel.login(id, pin) },
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text(stringResource(R.string.login_button))
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        TextButton(onClick = { showRegister = !showRegister }) {
            Text(stringResource(R.string.register_toggle))
        }
        
        if (showRegister) {
            Text("Registration form stub")
        }
    }
}

@Composable
fun HomeScreen(authViewModel: AuthViewModel, mainViewModel: MainViewModel, navController: NavController) {
    val currentPatient by mainViewModel.currentPatient.collectAsState()
    
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        if (currentPatient == null) {
            Button(
                onClick = { 
                    mainViewModel.startScan()
                    navController.navigate("scan") 
                },
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Text(stringResource(R.string.home_scan_patient))
            }
        } else {
            Button(
                onClick = { navController.navigate("vitals") },
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Text(stringResource(R.string.home_fill_vitals, currentPatient!!.name))
            }
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(onClick = { navController.navigate("history") }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text(stringResource(R.string.history_title))
            }
            Spacer(modifier = Modifier.height(16.dp))
            TextButton(onClick = { mainViewModel.clearPatient() }) {
                Text("Clear Patient Context")
            }
        }
        
        Spacer(modifier = Modifier.height(48.dp))
        TextButton(onClick = { 
            mainViewModel.clearSessionRecords()
            authViewModel.logout() 
        }) {
            Text(stringResource(R.string.home_logout))
        }
    }
}

@Composable
fun ScanScreen(mainViewModel: MainViewModel, navController: NavController) {
    val sessionState by mainViewModel.sessionState.collectAsState()
    
    LaunchedEffect(sessionState) {
        if (sessionState is SessionEvent.Completed) {
            val result = (sessionState as SessionEvent.Completed).result
            if (result is TransferResult.ProfileReceived) {
                mainViewModel.onProfileReceived(result.profile)
                navController.popBackStack()
            }
        }
    }
    
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(stringResource(R.string.scan_title), style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(24.dp))
        
        when (sessionState) {
            is SessionEvent.Idle -> Text(stringResource(R.string.scan_instructions))
            is SessionEvent.Connecting -> CircularProgressIndicator()
            is SessionEvent.Verifying -> {
                CircularProgressIndicator()
                Text(stringResource(R.string.session_verifying))
            }
            is SessionEvent.Transferring -> {
                LinearProgressIndicator()
                Text(stringResource(R.string.session_receiving))
            }
            is SessionEvent.Error -> {
                Text(stringResource(R.string.session_error), color = MaterialTheme.colorScheme.error)
                Text((sessionState as SessionEvent.Error).error.toString())
                Button(onClick = { mainViewModel.startScan() }) { Text("Retry") }
            }
            is SessionEvent.PeerMismatch -> {
                Text(stringResource(R.string.session_peer_mismatch), color = MaterialTheme.colorScheme.error)
                Button(onClick = { mainViewModel.startScan() }) { Text("Retry Scan") }
            }
            else -> {}
        }
    }
}

@Composable
fun VitalsScreen(mainViewModel: MainViewModel, navController: NavController) {
    val patient = mainViewModel.currentPatient.collectAsState().value
    
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(stringResource(R.string.vitals_title), style = MaterialTheme.typography.headlineMedium)
        Text("Patient: ${patient?.name ?: "Unknown"}")
        Spacer(modifier = Modifier.height(16.dp))
        
        // Locked identity fields are implicit (name above)
        
        OutlinedTextField(value = mainViewModel.bp.collectAsState().value, onValueChange = { mainViewModel.bp.value = it }, label = { Text(stringResource(R.string.vitals_blood_pressure)) }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = mainViewModel.hr.collectAsState().value, onValueChange = { mainViewModel.hr.value = it }, label = { Text(stringResource(R.string.vitals_heart_rate)) }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = mainViewModel.rr.collectAsState().value, onValueChange = { mainViewModel.rr.value = it }, label = { Text(stringResource(R.string.vitals_respiratory_rate)) }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = mainViewModel.temp.collectAsState().value, onValueChange = { mainViewModel.temp.value = it }, label = { Text(stringResource(R.string.vitals_temperature)) }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = mainViewModel.med.collectAsState().value, onValueChange = { mainViewModel.med.value = it }, label = { Text(stringResource(R.string.vitals_medication)) }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = mainViewModel.desc.collectAsState().value, onValueChange = { mainViewModel.desc.value = it }, label = { Text(stringResource(R.string.vitals_description)) }, modifier = Modifier.fillMaxWidth())
        
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = { navController.navigate("review") }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text(stringResource(R.string.vitals_review_button))
        }
    }
}

@Composable
fun ReviewScreen(mainViewModel: MainViewModel, navController: NavController) {
    val record = mainViewModel.buildRecord()
    val text = MedicalRecordTextCodec.encode(record)
    
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(stringResource(R.string.review_title), style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth().weight(1f)) {
            Text(text, modifier = Modifier.padding(16.dp))
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = { 
                mainViewModel.prepareToSend(record)
                mainViewModel.startSendSession()
                navController.navigate("session") 
            },
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text(stringResource(R.string.review_send_button))
        }
    }
}

@Composable
fun SessionScreen(mainViewModel: MainViewModel, navController: NavController) {
    val sessionState by mainViewModel.sessionState.collectAsState()
    
    LaunchedEffect(sessionState) {
        if (sessionState is SessionEvent.Completed) {
            val result = (sessionState as SessionEvent.Completed).result
            if (result is TransferResult.RecordSent) {
                mainViewModel.onRecordSent(result.record)
            }
        }
    }
    
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        when (sessionState) {
            is SessionEvent.Connecting -> CircularProgressIndicator()
            is SessionEvent.Verifying -> {
                CircularProgressIndicator()
                Text(stringResource(R.string.session_verifying))
            }
            is SessionEvent.Transferring -> {
                LinearProgressIndicator()
                Text(stringResource(R.string.session_sending))
            }
            is SessionEvent.Completed -> {
                Text(stringResource(R.string.session_done))
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { navController.popBackStack("home", inclusive = false) }) { Text("Back to Home") }
            }
            is SessionEvent.Error -> {
                Text(stringResource(R.string.session_error), color = MaterialTheme.colorScheme.error)
                Button(onClick = { navController.popBackStack() }) { Text("Go Back") }
            }
            is SessionEvent.PeerMismatch -> {
                Text(stringResource(R.string.session_peer_mismatch), color = MaterialTheme.colorScheme.error)
                Button(onClick = { navController.popBackStack() }) { Text("Back to Review") }
            }
            else -> {}
        }
    }
}

@Composable
fun HistoryScreen(mainViewModel: MainViewModel, navController: NavController) {
    val records by mainViewModel.historyRecords.collectAsState()
    val error by mainViewModel.historyError.collectAsState()
    
    LaunchedEffect(Unit) {
        mainViewModel.loadHistory()
    }
    
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(stringResource(R.string.history_title), style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        
        if (error != null) {
            Text(error!!, color = MaterialTheme.colorScheme.error)
            Button(onClick = { mainViewModel.loadHistory() }) { Text("Retry Sync") }
        } else {
            LazyColumn {
                items(records) { rec ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Record ID: ${rec.id}")
                            Text("Desc: ${rec.description}")
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = { navController.popBackStack() }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text("Back")
        }
    }
}
