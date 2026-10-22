package com.example.cad.crypto

import com.example.cad.nfc.CardTransport
import com.example.cad.protocol.Apdu
import java.io.ByteArrayOutputStream
import java.security.PrivateKey
import java.security.PublicKey
import java.security.cert.X509Certificate
import kotlin.math.min

class SecureSession(val htk: ByteArray) {
    fun wrap(plaintextApdu: ByteArray): ByteArray {
        val envelope = CadCrypto.encryptAesGcm(plaintextApdu, htk)
        return Apdu.secureApdu(envelope)
    }

    fun unwrap(secureResponse: ByteArray): ByteArray {
        val st = Apdu.status(secureResponse)
        if (st == Apdu.SW_NO_SECURE_SESSION) {
            throw CardLockedException("Card reported no secure session (6982)")
        }
        require(st == Apdu.SW_SUCCESS) { "SECURE_APDU status was not 9000: ${st?.toString(16)}" }
        val data = Apdu.data(secureResponse)
        return try {
            CadCrypto.decryptAesGcm(data, htk)
        } catch (e: Exception) {
            throw SecurityException("Failed to decrypt secure APDU response envelope", e)
        }
    }

    companion object {
        suspend fun establish(
            transport: CardTransport,
            cadCertDer: ByteArray = TestPki.CadCertDer,
            cadPrivateKey: PrivateKey = CadCrypto.parsePrivateKey(TestPki.CadPrivateKeyPem),
            rootCaCert: X509Certificate = CadCrypto.parseCertificate(TestPki.RootCaCertPem)
        ): SecureSession {
            // Step 1: Send CAD Certificate (chunked, max 160 bytes)
            val chunkSize = 160
            val totalCertChunks = (cadCertDer.size + chunkSize - 1) / chunkSize
            for (i in 0 until totalCertChunks) {
                val start = i * chunkSize
                val end = min(start + chunkSize, cadCertDer.size)
                val moreFlag = end < cadCertDer.size
                val chunk = cadCertDer.copyOfRange(start, end)
                val apdu = Apdu.authSendCadCert(i, moreFlag, chunk)
                val resp = transport.exchange(apdu)
                val st = Apdu.status(resp)
                if (st != Apdu.SW_SUCCESS) {
                    throw AuthenticationException("Send CAD cert chunk $i failed: ${st?.toString(16)}")
                }
            }

            // Step 2: Get Card Certificate (chunked)
            val cardCertBuffer = ByteArrayOutputStream()
            var chunkIdx = 0
            while (true) {
                val apdu = Apdu.authGetCardCert(chunkIdx)
                val resp = transport.exchange(apdu)
                val st = Apdu.status(resp)
                if (st != Apdu.SW_SUCCESS) {
                    throw AuthenticationException("Get Card cert chunk $chunkIdx failed: ${st?.toString(16)}")
                }
                val data = Apdu.data(resp)
                if (data.isEmpty()) throw AuthenticationException("Empty card cert chunk")
                val more = (data[0].toInt() and 0xFF) == 1
                cardCertBuffer.write(data, 1, data.size - 1)
                chunkIdx++
                if (!more) break
            }

            val cardCertBytes = cardCertBuffer.toByteArray()
            val cardCert = try {
                CadCrypto.parseCertificate(cardCertBytes)
            } catch (e: Exception) {
                throw AuthenticationException("Failed to parse Card certificate", e)
            }
            if (!CadCrypto.verify(cardCert, rootCaCert)) {
                throw AuthenticationException("Card certificate verification failed against Root CA")
            }
            val cardPublicKey: PublicKey = cardCert.publicKey

            // Step 2b: Get Card Nonce N1 (encrypted with CAD public key, 2 chunks of 128 bytes)
            val n1EncryptedBuffer = ByteArrayOutputStream()
            val apduN1_0 = Apdu.authGetN1(0)
            val respN1_0 = transport.exchange(apduN1_0)
            if (Apdu.status(respN1_0) != Apdu.SW_SUCCESS) {
                throw AuthenticationException("Get N1 chunk 0 failed")
            }
            val dataN1_0 = Apdu.data(respN1_0)
            if (dataN1_0.size != 129 || dataN1_0[0].toInt() != 1) {
                throw AuthenticationException("Invalid N1 chunk 0 response")
            }
            n1EncryptedBuffer.write(dataN1_0, 1, 128)

            val apduN1_1 = Apdu.authGetN1(1)
            val respN1_1 = transport.exchange(apduN1_1)
            if (Apdu.status(respN1_1) != Apdu.SW_SUCCESS) {
                throw AuthenticationException("Get N1 chunk 1 failed")
            }
            val dataN1_1 = Apdu.data(respN1_1)
            if (dataN1_1.size != 129 || dataN1_1[0].toInt() != 0) {
                throw AuthenticationException("Invalid N1 chunk 1 response")
            }
            n1EncryptedBuffer.write(dataN1_1, 1, 128)

            val n1Encrypted = n1EncryptedBuffer.toByteArray()
            val n1 = try {
                CadCrypto.decryptRsa(n1Encrypted, cadPrivateKey)
            } catch (e: Exception) {
                throw AuthenticationException("Failed to decrypt N1", e)
            }
            if (n1.size != 16) throw AuthenticationException("Decrypted N1 was not 16 bytes: ${n1.size}")

            // Step 3: Compute N1+1, generate random N2 (16 bytes), send E_Card(N1+1 || N2)
            val n1Plus1 = CadCrypto.incrementNonce(n1)
            val n2 = CadCrypto.generateNonce(16)
            val mutualNoncePlain = n1Plus1 + n2
            val mutualNonceEncrypted = CadCrypto.encryptRsa(mutualNoncePlain, cardPublicKey)
            if (mutualNonceEncrypted.size != 256) throw AuthenticationException("Invalid RSA encrypted mutual nonce length")

            // Send chunk 0 (128 bytes)
            val chunk0 = mutualNonceEncrypted.copyOfRange(0, 128)
            val respM0 = transport.exchange(Apdu.authSendMutualNonce(0, true, chunk0))
            if (Apdu.status(respM0) != Apdu.SW_SUCCESS) throw AuthenticationException("Send mutual nonce chunk 0 failed")

            // Send chunk 1 (128 bytes)
            val chunk1 = mutualNonceEncrypted.copyOfRange(128, 256)
            val respM1 = transport.exchange(Apdu.authSendMutualNonce(1, false, chunk1))
            if (Apdu.status(respM1) != Apdu.SW_SUCCESS) throw AuthenticationException("Send mutual nonce chunk 1 failed")

            // Step 4: Get Nonce ACK (E_CAD(N2+1), 2 chunks of 128 bytes)
            val ackEncryptedBuffer = ByteArrayOutputStream()
            val respAck0 = transport.exchange(Apdu.authGetNonceAck(0))
            if (Apdu.status(respAck0) != Apdu.SW_SUCCESS) throw AuthenticationException("Get Nonce ACK chunk 0 failed")
            val dataAck0 = Apdu.data(respAck0)
            if (dataAck0.size != 129 || dataAck0[0].toInt() != 1) throw AuthenticationException("Invalid ACK chunk 0")
            ackEncryptedBuffer.write(dataAck0, 1, 128)

            val respAck1 = transport.exchange(Apdu.authGetNonceAck(1))
            if (Apdu.status(respAck1) != Apdu.SW_SUCCESS) throw AuthenticationException("Get Nonce ACK chunk 1 failed")
            val dataAck1 = Apdu.data(respAck1)
            if (dataAck1.size != 129 || dataAck1[0].toInt() != 0) throw AuthenticationException("Invalid ACK chunk 1")
            ackEncryptedBuffer.write(dataAck1, 1, 128)

            val ackEncrypted = ackEncryptedBuffer.toByteArray()
            val decryptedAck = try {
                CadCrypto.decryptRsa(ackEncrypted, cadPrivateKey)
            } catch (e: Exception) {
                throw AuthenticationException("Failed to decrypt Nonce ACK", e)
            }
            val expectedN2Plus1 = CadCrypto.incrementNonce(n2)
            if (!decryptedAck.contentEquals(expectedN2Plus1)) {
                throw AuthenticationException("Nonce ACK verification failed: Card did not echo N2+1")
            }

            // Step 5: Establish HTK (16 bytes AES key, encrypted with Card Public Key)
            val htk = CadCrypto.generateSessionKey(16)
            val htkEncrypted = CadCrypto.encryptRsa(htk, cardPublicKey)
            if (htkEncrypted.size != 256) throw AuthenticationException("Invalid RSA encrypted HTK length")

            // Send chunk 0
            val htkChunk0 = htkEncrypted.copyOfRange(0, 128)
            val respH0 = transport.exchange(Apdu.authEstablishHtk(0, true, htkChunk0))
            if (Apdu.status(respH0) != Apdu.SW_SUCCESS) throw AuthenticationException("Establish HTK chunk 0 failed")

            // Send chunk 1
            val htkChunk1 = htkEncrypted.copyOfRange(128, 256)
            val respH1 = transport.exchange(Apdu.authEstablishHtk(1, false, htkChunk1))
            if (Apdu.status(respH1) != Apdu.SW_SUCCESS) throw AuthenticationException("Establish HTK chunk 1 failed")

            return SecureSession(htk)
        }
    }
}

open class AuthenticationException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
open class CardLockedException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
