package com.example.cad.nfc

import android.app.Activity
import android.nfc.NfcAdapter
import android.nfc.Tag

class NfcReaderMode(activity: Activity, onTagDiscovered: (Tag) -> Unit) {
    private val activity = activity
    private val adapter: NfcAdapter? = NfcAdapter.getDefaultAdapter(activity)
    private val callback = NfcAdapter.ReaderCallback(onTagDiscovered)

    fun enable() = adapter?.enableReaderMode(activity, callback, NfcAdapter.FLAG_READER_NFC_A, null) ?: Unit
    fun disable() = adapter?.disableReaderMode(activity) ?: Unit
}
