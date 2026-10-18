package com.example.cad.nfc

import android.nfc.TagLostException
import android.nfc.tech.IsoDep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException

class IsoDepCardTransport(
    private val isoDep: IsoDep,
    private val timeoutMillis: Int = DEFAULT_TIMEOUT_MILLIS,
) : CardTransport {
    private val exchangeMutex = Mutex()

    override suspend fun exchange(command: ByteArray): ByteArray = withContext(Dispatchers.IO) {
        exchangeMutex.withLock {
            try {
                if (!isoDep.isConnected) {
                    isoDep.connect()
                }
                isoDep.timeout = timeoutMillis
                isoDep.transceive(command)
            } catch (error: TagLostException) {
                throw CardTransportException.CardRemoved(error)
            } catch (error: IOException) {
                throw CardTransportException.Communication(error)
            }
        }
    }

    override fun close() {
        runCatching { isoDep.close() }
    }

    companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 15_000
    }
}
