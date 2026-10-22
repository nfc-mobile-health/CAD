package com.example.cad

import com.example.cad.crypto.CadCrypto
import com.example.cad.crypto.TestPki
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.crypto.AEADBadTagException

class Phase3CryptoTest {

    @Test
    fun certificatesVerifyAgainstRootCa() {
        val ca = CadCrypto.parseCertificate(TestPki.CA_CERT_PEM)
        val cadCert = CadCrypto.parseCertificate(TestPki.CAD_CERT_PEM)
        val mcardCert = CadCrypto.parseCertificate(TestPki.MCARD_CERT_PEM)

        assertTrue(CadCrypto.verify(cadCert, ca))
        assertTrue(CadCrypto.verify(mcardCert, ca))

        // CA self-verification
        assertTrue(CadCrypto.verify(ca, ca))
    }

    @Test
    fun rsaEncryptionAndDecryptionRoundTrip() {
        val cadCert = CadCrypto.parseCertificate(TestPki.CAD_CERT_PEM)
        val cadKey = CadCrypto.parsePrivateKey(TestPki.CAD_KEY_PEM)

        val plaintext = "Hello SecureXfer Phase 3".toByteArray(Charsets.UTF_8)
        val encrypted = CadCrypto.encryptRsa(plaintext, cadCert.publicKey)
        assertEquals(256, encrypted.size) // RSA-2048 produces 256 bytes

        val decrypted = CadCrypto.decryptRsa(encrypted, cadKey)
        assertEquals("Hello SecureXfer Phase 3", String(decrypted, Charsets.UTF_8))
    }

    @Test
    fun nonceIncrementFunctionsCorrectly() {
        val nonce = byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x05)
        val inc = CadCrypto.incrementNonce(nonce)
        assertEquals(0x06.toByte(), inc.last())

        // Rollover byte
        val nonceOverflow = byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x01, 0xFF.toByte())
        val incOverflow = CadCrypto.incrementNonce(nonceOverflow)
        assertEquals(0x02.toByte(), incOverflow[14])
        assertEquals(0x00.toByte(), incOverflow[15])
    }

    @Test
    fun aesGcmEncryptDecryptAndTamperDetection() {
        val sessionKey = CadCrypto.generateSessionKey(16)
        val data = "Medical Record Secret Payload".toByteArray(Charsets.UTF_8)

        val envelope = CadCrypto.encryptAesGcm(data, sessionKey)
        // 12 bytes IV + data length + 16 bytes tag
        assertEquals(12 + data.size + 16, envelope.size)

        val decrypted = CadCrypto.decryptAesGcm(envelope, sessionKey)
        assertEquals("Medical Record Secret Payload", String(decrypted, Charsets.UTF_8))

        // Tamper with ciphertext by flipping 1 bit
        val tampered = envelope.copyOf()
        tampered[15] = (tampered[15].toInt() xor 0x01).toByte()

        assertThrows(AEADBadTagException::class.java) {
            CadCrypto.decryptAesGcm(tampered, sessionKey)
        }
    }
}
