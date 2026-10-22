package com.example.cad.nfc

import com.example.cad.crypto.CadCrypto
import com.example.cad.crypto.TestPki
import com.example.cad.protocol.Apdu
import java.io.ByteArrayOutputStream
import java.security.PrivateKey
import java.security.PublicKey
import java.security.cert.X509Certificate
import kotlin.math.min

/**
 * Deterministic in-memory card model following S-MAPLE specification v1.
 * Supports Phase 1 (SELECT, GET_INFO, GET_BASIC_INFO), Phase 2 (ADD_RECORD, GET_RECORDS),
 * and Phase 3 (SecureXfer 5-message mutual authentication and AES-128-GCM secure messaging).
 */
class InMemoryCardTransport(
    private val basicInfo: ByteArray? = null,
    private val cardCertDer: ByteArray = TestPki.CardCertDer,
    private val cardPrivateKey: PrivateKey = CadCrypto.parsePrivateKey(TestPki.CardPrivateKeyPem),
    private val rootCaCert: X509Certificate = CadCrypto.parseCertificate(TestPki.RootCaCertPem),
    var requireSecureSession: Boolean = false
) : CardTransport {
    companion object {
        const val MAX_RECORDS = 10
        const val RING_SIZE = 2048
        const val MAX_PAGE_DATA_BYTES = 200
    }

    // Newest record at index 0
    private val storedRecords = ArrayList<ByteArray>()

    // Session state
    var sessionHtk: ByteArray? = null
        private set

    private val cadCertBuffer = ByteArrayOutputStream()
    private var cadCertChunksExpected = 0
    private var peerCadPublicKey: PublicKey? = null
    private var cardNonceN1: ByteArray? = null
    private var cadNonceN2: ByteArray? = null
    private var cachedEncryptedN1: ByteArray? = null
    private var cachedEncryptedAck: ByteArray? = null
    private val mutualNonceBuffer = ByteArrayOutputStream()
    private val htkBuffer = ByteArrayOutputStream()

    fun resetSession() {
        sessionHtk = null
        cadCertBuffer.reset()
        cadCertChunksExpected = 0
        peerCadPublicKey = null
        cardNonceN1 = null
        cadNonceN2 = null
        cachedEncryptedN1 = null
        cachedEncryptedAck = null
        mutualNonceBuffer.reset()
        htkBuffer.reset()
    }

    override suspend fun exchange(command: ByteArray): ByteArray = when {
        command.contentEquals(Apdu.SELECT) -> {
            resetSession()
            Apdu.response()
        }
        command.size < 4 -> Apdu.response(status = Apdu.SW_WRONG_LENGTH)
        command[0] != 0x80.toByte() -> Apdu.response(status = Apdu.SW_CLA_NOT_SUPPORTED)
        command[1] == Apdu.INS_AUTH_SEND_CAD_CERT.toByte() -> handleSendCadCert(command)
        command[1] == Apdu.INS_AUTH_GET_CARD_CERT.toByte() -> handleGetCardCert(command)
        command[1] == Apdu.INS_AUTH_GET_N1.toByte() -> handleGetN1(command)
        command[1] == Apdu.INS_AUTH_SEND_MUTUAL_NONCE.toByte() -> handleSendMutualNonce(command)
        command[1] == Apdu.INS_AUTH_GET_NONCE_ACK.toByte() -> handleGetNonceAck(command)
        command[1] == Apdu.INS_AUTH_ESTABLISH_HTK.toByte() -> handleEstablishHtk(command)
        command[1] == Apdu.INS_SECURE_APDU.toByte() -> handleSecureApdu(command)
        command[1] == 0x00.toByte() -> if (requireSecureSession && sessionHtk == null) Apdu.response(status = Apdu.SW_NO_SECURE_SESSION) else handleGetInfo(command)
        command[1] == 0x02.toByte() -> if (requireSecureSession && sessionHtk == null) Apdu.response(status = Apdu.SW_NO_SECURE_SESSION) else handleGetBasicInfo(command)
        command[1] == 0x10.toByte() -> if (requireSecureSession && sessionHtk == null) Apdu.response(status = Apdu.SW_NO_SECURE_SESSION) else handleAddRecord(command)
        command[1] == 0x12.toByte() -> if (requireSecureSession && sessionHtk == null) Apdu.response(status = Apdu.SW_NO_SECURE_SESSION) else handleGetRecords(command)
        else -> Apdu.response(status = Apdu.SW_INS_NOT_SUPPORTED)
    }

    override fun close() = Unit

    private fun handleSecureApdu(command: ByteArray): ByteArray {
        val htk = sessionHtk ?: return Apdu.response(status = Apdu.SW_NO_SECURE_SESSION)
        if (command.size < 5) return Apdu.response(status = Apdu.SW_WRONG_LENGTH)
        val lc = command[4].toInt() and 0xFF
        if (command.size < 5 + lc) return Apdu.response(status = Apdu.SW_WRONG_LENGTH)
        val envelope = command.copyOfRange(5, 5 + lc)
        val decrypted = try {
            CadCrypto.decryptAesGcm(envelope, htk)
        } catch (_: Exception) {
            return Apdu.response(status = Apdu.SW_INCORRECT_DATA)
        }
        if (decrypted.size < 4) return Apdu.response(status = Apdu.SW_WRONG_LENGTH)
        if (decrypted[0] != 0x80.toByte()) return Apdu.response(status = Apdu.SW_CLA_NOT_SUPPORTED)
        val innerResp = when (decrypted[1]) {
            0x00.toByte() -> handleGetInfo(decrypted)
            0x02.toByte() -> handleGetBasicInfo(decrypted)
            0x10.toByte() -> handleAddRecord(decrypted)
            0x12.toByte() -> handleGetRecords(decrypted)
            else -> Apdu.response(status = Apdu.SW_INS_NOT_SUPPORTED)
        }
        val encryptedResp = CadCrypto.encryptAesGcm(innerResp, htk)
        return Apdu.response(encryptedResp, status = Apdu.SW_SUCCESS)
    }

    private fun handleSendCadCert(command: ByteArray): ByteArray {
        if (command.size < 5) return Apdu.response(status = Apdu.SW_WRONG_LENGTH)
        val chunkIdx = command[2].toInt() and 0xFF
        val moreFlag = command[3].toInt() and 0xFF
        val lc = command[4].toInt() and 0xFF
        if (command.size < 5 + lc) return Apdu.response(status = Apdu.SW_WRONG_LENGTH)
        if (chunkIdx != cadCertChunksExpected) {
            resetSession()
            return Apdu.response(status = Apdu.SW_WRONG_P1P2)
        }
        val chunk = command.copyOfRange(5, 5 + lc)
        cadCertBuffer.write(chunk)
        cadCertChunksExpected++
        if (moreFlag == 0) {
            val fullCert = cadCertBuffer.toByteArray()
            try {
                val cert = CadCrypto.parseCertificate(fullCert)
                if (!CadCrypto.verify(cert, rootCaCert)) {
                    resetSession()
                    return Apdu.response(status = Apdu.SW_INCORRECT_DATA)
                }
                peerCadPublicKey = cert.publicKey
            } catch (_: Exception) {
                resetSession()
                return Apdu.response(status = Apdu.SW_INCORRECT_DATA)
            }
        }
        return Apdu.response(status = Apdu.SW_SUCCESS)
    }

    private fun handleGetCardCert(command: ByteArray): ByteArray {
        if (command.size != 5) return Apdu.response(status = Apdu.SW_WRONG_LENGTH)
        val chunkIdx = command[2].toInt() and 0xFF
        val chunkSize = 160
        val offset = chunkIdx * chunkSize
        if (offset >= cardCertDer.size) return Apdu.response(status = Apdu.SW_WRONG_P1P2)
        val end = min(offset + chunkSize, cardCertDer.size)
        val hasMore = end < cardCertDer.size
        val chunk = cardCertDer.copyOfRange(offset, end)
        return Apdu.response(byteArrayOf(if (hasMore) 1 else 0) + chunk, status = Apdu.SW_SUCCESS)
    }

    private fun handleGetN1(command: ByteArray): ByteArray {
        if (command.size != 5) return Apdu.response(status = Apdu.SW_WRONG_LENGTH)
        val peerKey = peerCadPublicKey ?: return Apdu.response(status = Apdu.SW_CONDITIONS_NOT_SATISFIED)
        val chunkIdx = command[2].toInt() and 0xFF
        return when (chunkIdx) {
            0 -> {
                val n1 = CadCrypto.generateNonce(16)
                cardNonceN1 = n1
                val encrypted = CadCrypto.encryptRsa(n1, peerKey)
                cachedEncryptedN1 = encrypted
                Apdu.response(byteArrayOf(0x01) + encrypted.copyOfRange(0, 128), status = Apdu.SW_SUCCESS)
            }
            1 -> {
                val enc = cachedEncryptedN1 ?: return Apdu.response(status = Apdu.SW_CONDITIONS_NOT_SATISFIED)
                Apdu.response(byteArrayOf(0x00) + enc.copyOfRange(128, 256), status = Apdu.SW_SUCCESS)
            }
            else -> Apdu.response(status = Apdu.SW_WRONG_P1P2)
        }
    }

    private fun handleSendMutualNonce(command: ByteArray): ByteArray {
        val n1 = cardNonceN1 ?: return Apdu.response(status = Apdu.SW_CONDITIONS_NOT_SATISFIED)
        if (command.size < 5) return Apdu.response(status = Apdu.SW_WRONG_LENGTH)
        val moreFlag = command[3].toInt() and 0xFF
        val lc = command[4].toInt() and 0xFF
        if (command.size < 5 + lc) return Apdu.response(status = Apdu.SW_WRONG_LENGTH)
        val chunk = command.copyOfRange(5, 5 + lc)
        mutualNonceBuffer.write(chunk)
        if (moreFlag == 0) {
            val ciphertext = mutualNonceBuffer.toByteArray()
            if (ciphertext.size != 256) {
                resetSession()
                return Apdu.response(status = Apdu.SW_WRONG_LENGTH)
            }
            val decrypted = try {
                CadCrypto.decryptRsa(ciphertext, cardPrivateKey)
            } catch (_: Exception) {
                resetSession()
                return Apdu.response(status = Apdu.SW_INCORRECT_DATA)
            }
            if (decrypted.size != 32) {
                resetSession()
                return Apdu.response(status = Apdu.SW_INCORRECT_DATA)
            }
            val receivedN1Plus1 = decrypted.copyOfRange(0, 16)
            val expectedN1Plus1 = CadCrypto.incrementNonce(n1)
            if (!receivedN1Plus1.contentEquals(expectedN1Plus1)) {
                resetSession()
                return Apdu.response(status = Apdu.SW_INCORRECT_DATA)
            }
            cadNonceN2 = decrypted.copyOfRange(16, 32)
        }
        return Apdu.response(status = Apdu.SW_SUCCESS)
    }

    private fun handleGetNonceAck(command: ByteArray): ByteArray {
        if (command.size != 5) return Apdu.response(status = Apdu.SW_WRONG_LENGTH)
        val n2 = cadNonceN2 ?: return Apdu.response(status = Apdu.SW_CONDITIONS_NOT_SATISFIED)
        val peerKey = peerCadPublicKey ?: return Apdu.response(status = Apdu.SW_CONDITIONS_NOT_SATISFIED)
        val chunkIdx = command[2].toInt() and 0xFF
        return when (chunkIdx) {
            0 -> {
                val n2Plus1 = CadCrypto.incrementNonce(n2)
                val encrypted = CadCrypto.encryptRsa(n2Plus1, peerKey)
                cachedEncryptedAck = encrypted
                Apdu.response(byteArrayOf(0x01) + encrypted.copyOfRange(0, 128), status = Apdu.SW_SUCCESS)
            }
            1 -> {
                val ack = cachedEncryptedAck ?: return Apdu.response(status = Apdu.SW_CONDITIONS_NOT_SATISFIED)
                Apdu.response(byteArrayOf(0x00) + ack.copyOfRange(128, 256), status = Apdu.SW_SUCCESS)
            }
            else -> Apdu.response(status = Apdu.SW_WRONG_P1P2)
        }
    }

    private fun handleEstablishHtk(command: ByteArray): ByteArray {
        if (cadNonceN2 == null) return Apdu.response(status = Apdu.SW_CONDITIONS_NOT_SATISFIED)
        if (command.size < 5) return Apdu.response(status = Apdu.SW_WRONG_LENGTH)
        val moreFlag = command[3].toInt() and 0xFF
        val lc = command[4].toInt() and 0xFF
        if (command.size < 5 + lc) return Apdu.response(status = Apdu.SW_WRONG_LENGTH)
        val chunk = command.copyOfRange(5, 5 + lc)
        htkBuffer.write(chunk)
        if (moreFlag == 0) {
            val ciphertext = htkBuffer.toByteArray()
            if (ciphertext.size != 256) {
                resetSession()
                return Apdu.response(status = Apdu.SW_WRONG_LENGTH)
            }
            val decrypted = try {
                CadCrypto.decryptRsa(ciphertext, cardPrivateKey)
            } catch (_: Exception) {
                resetSession()
                return Apdu.response(status = Apdu.SW_INCORRECT_DATA)
            }
            if (decrypted.size != 16) {
                resetSession()
                return Apdu.response(status = Apdu.SW_INCORRECT_DATA)
            }
            sessionHtk = decrypted
        }
        return Apdu.response(status = Apdu.SW_SUCCESS)
    }

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
