package com.example.cad

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.cad.nfc.NfcReaderMode
import com.example.cad.ui.CadApp
import com.example.cad.ui.ScanViewModel
import com.example.cad.ui.theme.CADTheme

class MainActivity : ComponentActivity() {
    private val scanViewModel: ScanViewModel by viewModels()
    private lateinit var readerMode: NfcReaderMode

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        readerMode = NfcReaderMode(this, scanViewModel::onTagDiscovered)
        setContent { CADTheme { CadApp(scanViewModel) } }
    }

    override fun onResume() {
        super.onResume()
        readerMode.enable()
    }

    override fun onPause() {
        readerMode.disable()
        super.onPause()
    }
}
