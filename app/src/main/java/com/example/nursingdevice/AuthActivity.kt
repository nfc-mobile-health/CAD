package com.example.nursingdevice

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch

class AuthActivity : AppCompatActivity() {

    private lateinit var manager: NursePatientManager
    private val repo = NurseRepository()

    private lateinit var nurseIdInput: EditText
    private lateinit var pinInput: EditText
    private lateinit var loginBtn: MaterialButton
    private lateinit var toggleLink: TextView
    private lateinit var registerSection: LinearLayout
    private lateinit var nameInput: EditText
    private lateinit var ageInput: EditText
    private lateinit var genderInput: EditText
    private lateinit var pocSpinner: Spinner
    private lateinit var contactInput: EditText
    private lateinit var registerBtn: MaterialButton
    private lateinit var pinVisibilityToggle: TextView

    private var isRegisterMode = false
    private var isPinVisible = false

    private val pocValues = listOf("homecare", "first_responder", "ambulance", "hospital")
    private val pocLabels = listOf("Home Care", "First Responder", "Ambulance", "Hospital")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_auth)

        manager = NursePatientManager(this)

        nurseIdInput    = findViewById(R.id.nurseIdInput)
        pinInput        = findViewById(R.id.pinInput)
        loginBtn        = findViewById(R.id.loginBtn)
        toggleLink      = findViewById(R.id.toggleLink)
        registerSection = findViewById(R.id.registerSection)
        nameInput       = findViewById(R.id.nurseNameInput)
        ageInput        = findViewById(R.id.nurseAgeInput)
        genderInput     = findViewById(R.id.nurseGenderInput)
        pocSpinner      = findViewById(R.id.pocSpinner)
        contactInput    = findViewById(R.id.nurseContactInput)
        registerBtn     = findViewById(R.id.registerBtn)
        pinVisibilityToggle = findViewById(R.id.pinVisibilityToggle)

        pocSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, pocLabels).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        pinVisibilityToggle.setOnClickListener { togglePinVisibility() }
        toggleLink.setOnClickListener { toggleMode() }
        loginBtn.setOnClickListener { handleLogin() }
        registerBtn.setOnClickListener { handleRegister() }
    }

    private fun togglePinVisibility() {
        isPinVisible = !isPinVisible
        pinInput.inputType = InputType.TYPE_CLASS_NUMBER or if (isPinVisible) {
            InputType.TYPE_NUMBER_VARIATION_NORMAL
        } else {
            InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }
        pinInput.setSelection(pinInput.text.length)
        pinVisibilityToggle.text = if (isPinVisible) "Hide" else "Show"
    }

    private fun toggleMode() {
        isRegisterMode = !isRegisterMode
        registerSection.visibility = if (isRegisterMode) View.VISIBLE else View.GONE
        toggleLink.text = if (isRegisterMode) "Already registered? Login" else "New nurse? Register here"
    }

    private fun handleLogin() {
        val nurseId = nurseIdInput.text.toString().trim()
        val pin     = pinInput.text.toString().trim()
        if (nurseId.isEmpty()) {
            Toast.makeText(this, "Enter your Nurse ID", Toast.LENGTH_SHORT).show()
            return
        }
        if (pin.isEmpty()) {
            Toast.makeText(this, "Enter your PIN", Toast.LENGTH_SHORT).show()
            return
        }

        // Check if database exists on device before attempting local unlock.
        // If it doesn't exist, avoid opening Room to prevent creating a database file with an unverified PIN.
        val hasDb = CredentialStore.hasDatabase(this)
        val unlock = if (hasDb) CredentialStore.unlock(this, pin, nurseId) else UnlockResult.NoCredential

        // Local fast-path: same nurse, valid credentials already on this device unlocked with PIN.
        val cached = manager.getNurse()
        if (cached.id == nurseId && cached.id.isNotEmpty() && unlock is UnlockResult.Success) {
            Toast.makeText(this, "Welcome back, ${cached.name}!", Toast.LENGTH_SHORT).show()
            goToMain()
            return
        }

        // If not fast-path (e.g. store has no credentials, or key mismatch from earlier/stale DB,
        // or a different nurse logging in), authenticate against backend.
        setLoading(true)
        lifecycleScope.launch {
            repo.login(nurseId, pin).fold(
                onSuccess = { reg ->
                    val data = reg.nurse
                    manager.saveNurseData(data)
                    val creds = reg.credentials
                    if (creds?.privateKey != null) {
                        val note = when (val r = CredentialStore.saveFromServer(
                            this@AuthActivity, pin, nurseId, "nurse", creds
                        )) {
                            is SaveResult.Success -> "credentials restored"
                            is SaveResult.Failed  -> "credential restore failed: ${r.reason}"
                        }
                        Toast.makeText(this@AuthActivity, "Welcome, ${data.name} ($note)", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this@AuthActivity, "Welcome, ${data.name}!", Toast.LENGTH_SHORT).show()
                    }
                    goToMain()
                },
                onFailure = { err ->
                    val msg = err.message.orEmpty()
                    val isInvalidPin = msg.contains("PIN", ignoreCase = true) || msg.contains("401")
                    if (isInvalidPin) {
                        Toast.makeText(this@AuthActivity, err.message ?: "Invalid PIN. Please check your PIN and try again.", Toast.LENGTH_LONG).show()
                    } else if (unlock is UnlockResult.Failed) {
                        // Offline or network error and local DB failed to decrypt
                        Toast.makeText(this@AuthActivity, "Login failed: ${unlock.reason}", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this@AuthActivity, err.message ?: "Login failed", Toast.LENGTH_LONG).show()
                    }
                    setLoading(false)
                }
            )
        }
    }

    private fun handleRegister() {
        val nurseId = nurseIdInput.text.toString().trim()
        val pin     = pinInput.text.toString().trim()
        val name    = nameInput.text.toString().trim()
        val age     = ageInput.text.toString().trim().toIntOrNull()
        val gender  = genderInput.text.toString().trim().takeIf { it.isNotBlank() }
        val poc     = pocValues[pocSpinner.selectedItemPosition]
        val contact = contactInput.text.toString().trim().takeIf { it.isNotBlank() }

        if (nurseId.isEmpty() || name.isEmpty()) {
            Toast.makeText(this, "Nurse ID and Name are required", Toast.LENGTH_SHORT).show()
            return
        }
        if (pin.length < 4) {
            Toast.makeText(this, "Set a PIN of at least 4 digits", Toast.LENGTH_SHORT).show()
            return
        }

        setLoading(true)
        lifecycleScope.launch {
            repo.register(nurseId, name, age, gender, poc, contact, pin).fold(
                onSuccess = { reg ->
                    manager.saveNurseData(reg.nurse)
                    // Persist the server-issued credentials, encrypted under this PIN.
                    val creds = reg.credentials
                    if (creds?.privateKey != null) {
                        val note = when (val r = CredentialStore.saveFromServer(
                            this@AuthActivity, pin, nurseId, "nurse", creds
                        )) {
                            is SaveResult.Success -> "credentials secured"
                            is SaveResult.Failed  -> "credential save failed: ${r.reason}"
                        }
                        Toast.makeText(this@AuthActivity, "Registered as ${reg.nurse.name} ($note)", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(
                            this@AuthActivity,
                            "Registered as ${reg.nurse.name}, but no credentials returned. Try 'rotate' later.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    goToMain()
                },
                onFailure = { err ->
                    val msg = err.message.orEmpty()
                    val isClientRejection = msg.contains("already registered", ignoreCase = true) ||
                            msg.contains("different PIN", ignoreCase = true) ||
                            msg.contains("at least 4 digits", ignoreCase = true) ||
                            msg.contains("required", ignoreCase = true) ||
                            msg.contains("400") || msg.contains("409")
                    if (isClientRejection) {
                        Toast.makeText(this@AuthActivity, err.message ?: "Registration failed", Toast.LENGTH_LONG).show()
                        setLoading(false)
                        return@fold
                    }

                    // Save locally only if backend is unreachable (offline network error)
                    manager.saveNurse(Nurse(name = name, id = nurseId))
                    Toast.makeText(
                        this@AuthActivity,
                        "Saved locally (backend: ${err.message})",
                        Toast.LENGTH_LONG
                    ).show()
                    goToMain()
                }
            )
        }
    }

    private fun setLoading(loading: Boolean) {
        loginBtn.isEnabled = !loading
        registerBtn.isEnabled = !loading
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
