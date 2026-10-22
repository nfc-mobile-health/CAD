package com.example.cad

import com.example.cad.crypto.AuthenticationException
import com.example.cad.crypto.CadCrypto
import com.example.cad.crypto.CardLockedException
import com.example.cad.crypto.SecureSession
import com.example.cad.crypto.TestPki
import com.example.cad.nfc.InMemoryCardTransport
import com.example.cad.nfc.MCardReader
import com.example.cad.protocol.BloodGroup
import com.example.cad.protocol.Gender
import com.example.cad.protocol.PatientBasicInfo
import com.example.cad.protocol.RecordCodec
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.security.KeyPairGenerator
import java.time.LocalDate

class Phase3ProtocolTest {

    private val sampleBasicInfoBlock = hexToBytes("104C67450741562D323034310A41736861205665726D61")

    @Test
    fun readerAuthenticatesAndReadsPatientOverSecureSession() = runBlocking {
        val transport = InMemoryCardTransport(
            basicInfo = sampleBasicInfoBlock,
            requireSecureSession = true
        )
        val reader = MCardReader(transport)

        // Authenticate (5-message flow)
        val session = reader.authenticate()
        assertNotNull(session)
        assertNotNull(reader.activeSession)
        assertEquals(16, session.htk.size)

        // Read patient over secure channel
        val patient = reader.readPatient()
        assertEquals("AV-2041", patient.patientId)
        assertEquals("Asha Verma", patient.name)
        assertEquals(Gender.FEMALE, patient.gender)
        assertEquals(BloodGroup.B_POSITIVE, patient.bloodGroup)
        assertEquals(LocalDate.of(1972, 5, 20), patient.dateOfBirth)
    }

    @Test
    fun unauthenticatedAccessThrowsCardLockedException() = runBlocking {
        val transport = InMemoryCardTransport(
            basicInfo = sampleBasicInfoBlock,
            requireSecureSession = true
        )
        val reader = MCardReader(transport)

        // Do NOT authenticate; try readPatient()
        try {
            reader.readPatient()
            fail("Expected CardLockedException when unauthenticated")
        } catch (_: CardLockedException) {
            // Success
        }
    }

    @Test
    fun writeAndVerifyOverSecureSession() = runBlocking {
        val transport = InMemoryCardTransport(
            basicInfo = sampleBasicInfoBlock,
            requireSecureSession = true
        )
        val reader = MCardReader(transport)
        reader.authenticate()

        // Write V1 record blob
        val v1Blob = hexToBytes("3D0E3F6C044E313031503C46500C466576657220322064617973")
        val verified = reader.writeAndVerify(v1Blob)
        assertTrue("writeAndVerify must succeed over secure session", verified)

        // Read all records over secure session
        val records = reader.readAllRecords()
        assertEquals(1, records.size)
        assertTrue(records[0].contentEquals(v1Blob))
    }

    @Test
    fun pagingAndEvictionOverSecureSession() = runBlocking {
        val transport = InMemoryCardTransport(
            basicInfo = sampleBasicInfoBlock,
            requireSecureSession = true
        )
        val reader = MCardReader(transport)
        reader.authenticate()

        // Write 12 records R1..R12
        for (i in 1..12) {
            val blob = byteArrayOf(0x24, 0x0E, 0x3F, 0x49, 0x04, 'N'.code.toByte(), '1'.code.toByte(), '0'.code.toByte(), '1'.code.toByte(), i.toByte())
            reader.addRecord(blob)
        }

        // Read all records: should have exactly MAX_RECORDS = 10, newest first (R12..R3)
        val all = reader.readAllRecords()
        assertEquals(10, all.size)
        assertEquals(12.toByte(), all[0].last())
        assertEquals(3.toByte(), all[9].last())

        // Test paging directly: start 0, page 1
        val p1 = reader.getRecords(0)
        assertEquals(10, p1.totalCount)
        assertEquals(12.toByte(), p1.records[0].last())
    }

    @Test
    fun forgedCardCertificateFailsAuthentication() = runBlocking {
        // Generate untrusted self-signed / fake cert for Card
        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(2048)
        val fakeKeyPair = kpg.generateKeyPair()

        // Create transport with fake key
        val transport = InMemoryCardTransport(
            basicInfo = sampleBasicInfoBlock,
            cardPrivateKey = fakeKeyPair.private,
            requireSecureSession = true
        )
        val reader = MCardReader(transport)

        // Authenticate must fail because card cert won't match Root CA or decrypt
        try {
            reader.authenticate()
            fail("Expected AuthenticationException on forged certificate/key")
        } catch (_: AuthenticationException) {
            // Expected
        }
    }

    private fun hexToBytes(hex: String): ByteArray {
        val clean = hex.replace(" ", "")
        val result = ByteArray(clean.length / 2)
        for (i in result.indices) {
            result[i] = clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
        return result
    }
}
