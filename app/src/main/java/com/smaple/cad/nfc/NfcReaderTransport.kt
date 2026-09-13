package com.smaple.cad.nfc

import android.nfc.tech.IsoDep
import com.smaple.core.transport.Transport
import com.smaple.core.transport.TransportRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import java.io.IOException

@Singleton
class NfcReaderTransport @Inject constructor() : Transport {
    var activeIsoDep: IsoDep? = null
    
    override val role = TransportRole.READER
    
    override suspend fun transceive(frame: ByteArray): ByteArray = withContext(Dispatchers.IO) {
        val iso = activeIsoDep ?: throw IOException("No active NFC connection")
        if (!iso.isConnected) {
            iso.connect()
        }
        iso.timeout = 5000
        iso.transceive(frame)
    }
    
    override fun close() {
        try {
            activeIsoDep?.close()
        } catch (e: Exception) {}
        activeIsoDep = null
    }
}
