package com.example.cad.protocol

object Apdu {
    val AID = byteArrayOf(0xF0.toByte(), 0x12, 0x34, 0x56, 0x78, 0x21)
    val SELECT = byteArrayOf(0x00, 0xA4.toByte(), 0x04, 0x00, AID.size.toByte()) + AID
    val GET_INFO = byteArrayOf(0x80.toByte(), 0x00, 0x00, 0x00, 0x00)
    val GET_BASIC_INFO = byteArrayOf(0x80.toByte(), 0x02, 0x00, 0x00, 0x00)

    const val SW_SUCCESS = 0x9000
    const val SW_NOT_FOUND = 0x6A88

    fun response(data: ByteArray = byteArrayOf(), status: Int = SW_SUCCESS): ByteArray =
        data + byteArrayOf((status ushr 8).toByte(), status.toByte())

    fun status(response: ByteArray): Int? =
        if (response.size < 2) null else ((response[response.size - 2].toInt() and 0xFF) shl 8) or
            (response.last().toInt() and 0xFF)

    fun data(response: ByteArray): ByteArray = response.dropLast(2).toByteArray()
}
