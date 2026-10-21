package com.example.cad.nfc

import com.example.cad.protocol.Apdu

/**
 * Deterministic in-memory card model following S-MAPLE specification v1.
 * Supports Phase 1 (SELECT, GET_INFO, GET_BASIC_INFO) and Phase 2 (ADD_RECORD, GET_RECORDS).
 * Implements the 10-record ring buffer, oldest-first eviction, duplicate suppression,
 * and GET_RECORDS paging without exposing dev-only commands.
 */
class InMemoryCardTransport(
    private val basicInfo: ByteArray? = null,
) : CardTransport {
    companion object {
        const val MAX_RECORDS = 10
        const val RING_SIZE = 2048
        const val MAX_PAGE_DATA_BYTES = 200
    }

    // Newest record at index 0
    private val storedRecords = ArrayList<ByteArray>()

    override suspend fun exchange(command: ByteArray): ByteArray = when {
        command.contentEquals(Apdu.SELECT) -> Apdu.response()
        command.size < 4 -> Apdu.response(status = Apdu.SW_WRONG_LENGTH)
        command[0] != 0x80.toByte() -> Apdu.response(status = Apdu.SW_CLA_NOT_SUPPORTED)
        command[1] == 0x00.toByte() -> handleGetInfo(command)
        command[1] == 0x02.toByte() -> handleGetBasicInfo(command)
        command[1] == 0x10.toByte() -> handleAddRecord(command)
        command[1] == 0x12.toByte() -> handleGetRecords(command)
        else -> Apdu.response(status = Apdu.SW_INS_NOT_SUPPORTED)
    }

    override fun close() = Unit

    private fun handleGetInfo(command: ByteArray): ByteArray {
        if (command.size != 5) return Apdu.response(status = Apdu.SW_WRONG_LENGTH)
        if (command[2] != 0.toByte() || command[3] != 0.toByte()) {
            return Apdu.response(status = Apdu.SW_WRONG_P1P2)
        }
        val count = storedRecords.size
        val usedBytes = storedRecords.sumOf { 1 + it.size }
        val freeBytes = RING_SIZE - usedBytes
        val data = byteArrayOf(
            0x01, 0x01, // API v1, Record format v1
            ((count ushr 8) and 0xFF).toByte(), (count and 0xFF).toByte(),
            ((MAX_RECORDS ushr 8) and 0xFF).toByte(), (MAX_RECORDS and 0xFF).toByte(),
            ((RING_SIZE ushr 8) and 0xFF).toByte(), (RING_SIZE and 0xFF).toByte(),
            ((freeBytes ushr 8) and 0xFF).toByte(), (freeBytes and 0xFF).toByte(),
            if (basicInfo == null) 0x00 else 0x01,
        )
        return Apdu.response(data)
    }

    private fun handleGetBasicInfo(command: ByteArray): ByteArray {
        if (command.size != 5) return Apdu.response(status = Apdu.SW_WRONG_LENGTH)
        if (command[2] != 0.toByte() || command[3] != 0.toByte()) {
            return Apdu.response(status = Apdu.SW_WRONG_P1P2)
        }
        return basicInfo?.let(Apdu::response) ?: Apdu.response(status = Apdu.SW_NOT_FOUND)
    }

    private fun handleAddRecord(command: ByteArray): ByteArray {
        // Expected: 80 10 00 00 Lc blob 00 (size is 6 + Lc)
        if (command.size < 6) return Apdu.response(status = Apdu.SW_WRONG_LENGTH)
        if (command[2] != 0.toByte() || command[3] != 0.toByte()) {
            return Apdu.response(status = Apdu.SW_WRONG_P1P2)
        }
        val lc = command[4].toInt() and 0xFF
        if (lc < 1 || lc > 180) return Apdu.response(status = Apdu.SW_WRONG_LENGTH)
        if (command.size != 5 + lc + 1) return Apdu.response(status = Apdu.SW_WRONG_LENGTH)
        if (command.last() != 0x00.toByte()) return Apdu.response(status = Apdu.SW_WRONG_LENGTH)

        val blob = command.copyOfRange(5, 5 + lc)

        // Version bits (first byte >> 5 must be 1)
        val version = (blob[0].toInt() and 0xFF) ushr 5
        if (version != 1) return Apdu.response(status = Apdu.SW_INCORRECT_DATA)

        // Duplicate check: if identical to newest record, result 01
        if (storedRecords.isNotEmpty() && storedRecords.first().contentEquals(blob)) {
            val count = storedRecords.size
            val respData = byteArrayOf(
                0x01, // already stored
                ((count ushr 8) and 0xFF).toByte(),
                (count and 0xFF).toByte(),
            )
            return Apdu.response(respData)
        }

        // Add as newest
        storedRecords.add(0, blob)

        // Evict oldest until count <= MAX_RECORDS and sum(1 + length) <= RING_SIZE
        while (storedRecords.size > MAX_RECORDS) {
            storedRecords.removeAt(storedRecords.lastIndex)
        }
        while (storedRecords.sumOf { 1 + it.size } > RING_SIZE && storedRecords.isNotEmpty()) {
            storedRecords.removeAt(storedRecords.lastIndex)
        }

        val count = storedRecords.size
        val respData = byteArrayOf(
            0x00, // stored
            ((count ushr 8) and 0xFF).toByte(),
            (count and 0xFF).toByte(),
        )
        return Apdu.response(respData)
    }

    private fun handleGetRecords(command: ByteArray): ByteArray {
        // Expected: 80 12 P1 P2 00
        if (command.size != 5) return Apdu.response(status = Apdu.SW_WRONG_LENGTH)
        if (command.last() != 0x00.toByte()) return Apdu.response(status = Apdu.SW_WRONG_LENGTH)

        val startIndex = ((command[2].toInt() and 0xFF) shl 8) or (command[3].toInt() and 0xFF)
        val totalCount = storedRecords.size

        if (startIndex >= totalCount) {
            val respData = byteArrayOf(
                ((totalCount ushr 8) and 0xFF).toByte(), (totalCount and 0xFF).toByte(),
                0x00, 0x00, // remaining 0
                0x00, // records in this page
            )
            return Apdu.response(respData)
        }

        // Max page data: 200 bytes. Header is 5 bytes, so 195 bytes available for entries.
        val maxEntryBytes = MAX_PAGE_DATA_BYTES - 5
        var currentBytes = 0
        var pageCount = 0

        while (startIndex + pageCount < totalCount) {
            val record = storedRecords[startIndex + pageCount]
            val entrySize = 1 + record.size
            if (currentBytes + entrySize > maxEntryBytes) {
                // If even the first record doesn't fit, spec guarantees 180 bytes fits in 195 bytes.
                break
            }
            currentBytes += entrySize
            pageCount++
        }

        val remaining = totalCount - (startIndex + pageCount)
        val header = byteArrayOf(
            ((totalCount ushr 8) and 0xFF).toByte(), (totalCount and 0xFF).toByte(),
            ((remaining ushr 8) and 0xFF).toByte(), (remaining and 0xFF).toByte(),
            (pageCount and 0xFF).toByte(),
        )

        var entries = byteArrayOf()
        for (i in 0 until pageCount) {
            val r = storedRecords[startIndex + i]
            entries += byteArrayOf(r.size.toByte()) + r
        }

        return Apdu.response(header + entries)
    }
}
