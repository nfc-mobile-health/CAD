package com.example.cad.nfc

import com.example.cad.protocol.Apdu
import com.example.cad.protocol.BasicInfoCodec
import com.example.cad.protocol.PatientBasicInfo

class MCardReader(private val transport: CardTransport) {
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

    private fun requireSuccess(response: ByteArray) {
        check(Apdu.status(response) == Apdu.SW_SUCCESS) { "Card response was not successful" }
    }
}
