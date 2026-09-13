package com.smaple.cad

import android.nfc.NfcAdapter
import android.nfc.tech.IsoDep
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.smaple.cad.nfc.NfcReaderTransport
import com.smaple.cad.ui.Navigation
import com.smaple.cad.ui.theme.CadTheme
import com.smaple.core.session.SessionCoordinator
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity(), NfcAdapter.ReaderCallback {

    @Inject
    lateinit var nfcTransport: NfcReaderTransport

    @Inject
    lateinit var sessionCoordinator: SessionCoordinator

    private var nfcAdapter: NfcAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        
        setContent {
            CadTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Navigation()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        nfcAdapter?.enableReaderMode(
            this,
            this,
            NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
            null
        )
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableReaderMode(this)
    }

    override fun onTagDiscovered(tag: android.nfc.Tag?) {
        val isoDep = IsoDep.get(tag) ?: return
        nfcTransport.activeIsoDep = isoDep
        
        lifecycleScope.launch {
            try {
                sessionCoordinator.startReaderSession()
            } finally {
                nfcTransport.close()
            }
        }
    }
}
