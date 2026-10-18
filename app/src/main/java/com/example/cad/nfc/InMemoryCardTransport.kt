package com.example.cad.nfc

import com.example.cad.protocol.Apdu

/** Deterministic Phase 1 card model. It deliberately exposes no dev-only commands. */
class InMemoryCardTransport(
    private val basicInfo: ByteArray? = null,
) : CardTransport {
    override suspend fun exchange(command: ByteArray): ByteArray = when {
        command.contentEquals(Apdu.SELECT) -> Apdu.response()
        command.size < 4 -> Apdu.response(status = 0x6700)
        command[0] != 0x80.toByte() -> Apdu.response(status = 0x6E00)
        command.size != 5 -> Apdu.response(status = 0x6700)
        command[2] != 0.toByte() || command[3] != 0.toByte() -> Apdu.response(status = 0x6A86)
        command[1] == 0.toByte() -> info()
        command[1] == 0x02.toByte() -> basicInfo()
        else -> Apdu.response(status = 0x6D00)
    }

    override fun close() = Unit

    private fun info(): ByteArray = Apdu.response(
        byteArrayOf(
            0x01, 0x01, 0x00, 0x00, 0x00, 0x0A, 0x08, 0x00, 0x08, 0x00,
            if (basicInfo == null) 0x00 else 0x01,
        ),
    )

    private fun basicInfo(): ByteArray = basicInfo?.let(Apdu::response)
        ?: Apdu.response(status = Apdu.SW_NOT_FOUND)
}
