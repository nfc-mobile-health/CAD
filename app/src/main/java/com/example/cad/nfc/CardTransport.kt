package com.example.cad.nfc

/** All NFC card commands pass through this boundary. */
interface CardTransport : AutoCloseable {
    /** Exchanges exactly one short APDU; it must never run on the main thread. */
    suspend fun exchange(command: ByteArray): ByteArray

    override fun close()
}

sealed class CardTransportException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class CardRemoved(cause: Throwable? = null) : CardTransportException("Card was removed", cause)
    class Communication(cause: Throwable) : CardTransportException("Card communication failed", cause)
}
