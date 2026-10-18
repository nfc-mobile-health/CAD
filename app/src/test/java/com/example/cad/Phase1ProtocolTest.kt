package com.example.cad

import com.example.cad.nfc.InMemoryCardTransport
import com.example.cad.nfc.MCardReader
import com.example.cad.protocol.Apdu
import com.example.cad.protocol.BasicInfoCodec
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class Phase1ProtocolTest {
    private val asha = byteArrayOf(
        0x10, 0x4C, 0x67, 0x45, 0x07, 0x41, 0x56, 0x2D, 0x32, 0x30, 0x34, 0x31,
        0x0A, 0x41, 0x73, 0x68, 0x61, 0x20, 0x56, 0x65, 0x72, 0x6D, 0x61,
    )

    @Test
    fun basicInfoVectorDecodesExactly() {
        val info = BasicInfoCodec.decode(asha)
        assertEquals("AV-2041", info.patientId)
        assertEquals("Asha Verma", info.name)
        assertEquals(LocalDate.of(1972, 5, 20), info.dateOfBirth)
        assertEquals("Female", info.gender.displayName)
        assertEquals("B+", info.bloodGroup.displayName)
    }

    @Test
    fun fakeCardReadsTheSpecificationVector() = runTest {
        val patient = MCardReader(InMemoryCardTransport(asha)).readPatient()
        assertEquals("Asha Verma", patient.name)
    }

    @Test
    fun fakeCardReportsFreshInfoAndNoBasicInfo() = runTest {
        val transport = InMemoryCardTransport()
        val info = transport.exchange(Apdu.GET_INFO)
        assertTrue(info.contentEquals(byteArrayOf(0x01, 0x01, 0, 0, 0, 0x0A, 0x08, 0, 0x08, 0, 0, 0x90.toByte(), 0)))
        val basic = transport.exchange(Apdu.GET_BASIC_INFO)
        assertEquals(Apdu.SW_NOT_FOUND, Apdu.status(basic))
    }

}
