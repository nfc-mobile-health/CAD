package com.example.cad.nfc

import com.example.cad.protocol.Apdu
import com.example.cad.protocol.BasicInfoCodec
import com.example.cad.protocol.PatientBasicInfo

class MCardReader(private val transport: CardTransport) {

    suspend fun select(): Boolean {
        val response = transport.exchange(Apdu.SELECT)
        return Apdu.status(response) == Apdu.SW_SUCCESS
    }

    suspend fun readPatient(): PatientBasicInfo {
        requireSuccess(transport.exchange(Apdu.SELECT))
        val infoResponse = transport.exchange(Apdu.GET_INFO)
        requireSuccess(infoResponse)
        val info = Apdu.data(infoResponse)
        check(info.size == 11 && info[0] == 0x01.toByte() && info[1] == 0x01.toByte()) {
            "Unsupported card response"
        }
        check((info[10].toInt() and 0x01) == 1) { "Patient information is not available" }
        val basicResponse = transport.exchange(Apdu.GET_BASIC_INFO)
        requireSuccess(basicResponse)
        return BasicInfoCodec.decode(Apdu.data(basicResponse))
    }

    suspend fun addRecord(blob: ByteArray): Apdu.AddRecordResponse {
        val response = transport.exchange(Apdu.addRecord(blob))
        requireSuccess(response)
        return Apdu.parseAddRecordResponse(response)
    }

    suspend fun getRecords(startIndex: Int = 0): Apdu.RecordPage {
        val response = transport.exchange(Apdu.getRecords(startIndex))
        requireSuccess(response)
        return Apdu.parseRecordPage(response)
    }

    /**
     * Reads all stored records from the card newest-first using GET_RECORDS paging.
     */
    suspend fun readAllRecords(): List<ByteArray> {
        val allRecords = mutableListOf<ByteArray>()
        var start = 0
        while (true) {
            val page = getRecords(start)
            allRecords.addAll(page.records)
            if (page.remaining == 0 || page.records.isEmpty()) {
                break
            }
            start += page.records.size
        }
        return allRecords
    }

    /**
     * Writes a record blob to the card and verifies it by reading back the newest record.
     * Returns true if the read-back record matches the written blob byte-for-byte.
     */
    suspend fun writeAndVerify(blob: ByteArray): Boolean {
        requireSuccess(transport.exchange(Apdu.SELECT))
        addRecord(blob)
        val page = getRecords(startIndex = 0)
        if (page.records.isEmpty()) return false
        return page.records[0].contentEquals(blob)
    }

    private fun requireSuccess(response: ByteArray) {
        val st = Apdu.status(response)
        check(st == Apdu.SW_SUCCESS) { "Card response status was not 9000: ${st?.toString(16)}" }
    }
}
