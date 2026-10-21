package com.example.cad.protocol

object Apdu {
    val AID = byteArrayOf(0xF0.toByte(), 0x12, 0x34, 0x56, 0x78, 0x21)
    val SELECT = byteArrayOf(0x00, 0xA4.toByte(), 0x04, 0x00, AID.size.toByte()) + AID
    val GET_INFO = byteArrayOf(0x80.toByte(), 0x00, 0x00, 0x00, 0x00)
    val GET_BASIC_INFO = byteArrayOf(0x80.toByte(), 0x02, 0x00, 0x00, 0x00)

    const val SW_SUCCESS = 0x9000
    const val SW_WRONG_LENGTH = 0x6700
    const val SW_NO_SECURE_SESSION = 0x6982
    const val SW_CONDITIONS_NOT_SATISFIED = 0x6985
    const val SW_INCORRECT_DATA = 0x6A80
    const val SW_WRONG_P1P2 = 0x6A86
    const val SW_NOT_FOUND = 0x6A88
    const val SW_INS_NOT_SUPPORTED = 0x6D00
    const val SW_CLA_NOT_SUPPORTED = 0x6E00

    fun addRecord(blob: ByteArray): ByteArray {
        require(blob.size in 1..180) { "Record blob size must be 1 to 180, was ${blob.size}" }
        // 80 10 00 00 Lc blob 00
        return byteArrayOf(
            0x80.toByte(),
            0x10.toByte(),
            0x00,
            0x00,
            blob.size.toByte(),
        ) + blob + byteArrayOf(0x00)
    }

    fun getRecords(startIndex: Int): ByteArray {
        require(startIndex in 0..0xFFFF) { "Start index must be uint16, was $startIndex" }
        // 80 12 P1 P2 00
        return byteArrayOf(
            0x80.toByte(),
            0x12.toByte(),
            ((startIndex ushr 8) and 0xFF).toByte(),
            (startIndex and 0xFF).toByte(),
            0x00,
        )
    }

    fun response(data: ByteArray = byteArrayOf(), status: Int = SW_SUCCESS): ByteArray =
        data + byteArrayOf((status ushr 8).toByte(), status.toByte())

    fun status(response: ByteArray): Int? =
        if (response.size < 2) null else ((response[response.size - 2].toInt() and 0xFF) shl 8) or
            (response.last().toInt() and 0xFF)

    fun data(response: ByteArray): ByteArray =
        if (response.size < 2) byteArrayOf() else response.dropLast(2).toByteArray()

    data class AddRecordResponse(val alreadyStored: Boolean, val recordCount: Int)

    fun parseAddRecordResponse(response: ByteArray): AddRecordResponse {
        val st = status(response)
        require(st == SW_SUCCESS) { "ADD_RECORD status was not 9000: ${st?.toString(16)}" }
        val d = data(response)
        require(d.size == 3) { "ADD_RECORD data must be 3 bytes, was ${d.size}" }
        val alreadyStored = when (d[0].toInt() and 0xFF) {
            0x00 -> false
            0x01 -> true
            else -> error("Invalid ADD_RECORD result byte: ${d[0]}")
        }
        val count = ((d[1].toInt() and 0xFF) shl 8) or (d[2].toInt() and 0xFF)
        return AddRecordResponse(alreadyStored = alreadyStored, recordCount = count)
    }

    data class RecordPage(val totalCount: Int, val remaining: Int, val records: List<ByteArray>)

    fun parseRecordPage(response: ByteArray): RecordPage {
        val st = status(response)
        require(st == SW_SUCCESS) { "GET_RECORDS status was not 9000: ${st?.toString(16)}" }
        val d = data(response)
        require(d.size >= 5) { "GET_RECORDS data must be at least 5 bytes, was ${d.size}" }
        val totalCount = ((d[0].toInt() and 0xFF) shl 8) or (d[1].toInt() and 0xFF)
        val remaining = ((d[2].toInt() and 0xFF) shl 8) or (d[3].toInt() and 0xFF)
        val numInPage = d[4].toInt() and 0xFF

        val records = ArrayList<ByteArray>(numInPage)
        var offset = 5
        for (i in 0 until numInPage) {
            require(offset < d.size) { "Malformed GET_RECORDS page at record $i" }
            val len = d[offset].toInt() and 0xFF
            offset += 1
            require(offset + len <= d.size) { "Record $i length $len exceeds page buffer" }
            records.add(d.copyOfRange(offset, offset + len))
            offset += len
        }
        require(offset == d.size) { "Unused bytes in GET_RECORDS page: $offset vs ${d.size}" }
        return RecordPage(totalCount = totalCount, remaining = remaining, records = records)
    }
}
