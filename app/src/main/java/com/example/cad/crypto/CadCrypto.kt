package com.example.cad.crypto

import java.io.ByteArrayInputStream
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

interface CertificateVerifier {
    fun parseCertificate(data: ByteArray): X509Certificate
    fun verify(peerCert: X509Certificate, caCert: X509Certificate): Boolean
}

object CadCrypto : CertificateVerifier {
    private val secureRandom = SecureRandom()
    private val certFactory = CertificateFactory.getInstance("X.509")
    private val keyFactory = KeyFactory.getInstance("RSA")

    override fun parseCertificate(data: ByteArray): X509Certificate {
        return certFactory.generateCertificate(ByteArrayInputStream(data)) as X509Certificate
    }

    fun parseCertificate(pem: String): X509Certificate =
        parseCertificate(pem.toByteArray(Charsets.US_ASCII))

    override fun verify(peerCert: X509Certificate, caCert: X509Certificate): Boolean {
        return try {
            peerCert.checkValidity()
            peerCert.verify(caCert.publicKey)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun parsePrivateKey(pem: String): PrivateKey {
        val clean = pem
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("\\s".toRegex(), "")
        val bytes = Base64.getDecoder().decode(clean)
        return keyFactory.generatePrivate(PKCS8EncodedKeySpec(bytes))
    }

    fun generateNonce(size: Int = 16): ByteArray {
        val nonce = ByteArray(size)
        secureRandom.nextBytes(nonce)
        return nonce
    }

    fun incrementNonce(nonce: ByteArray): ByteArray {
        val copy = nonce.copyOf()
        for (i in copy.size - 1 downTo 0) {
            val v = (copy[i].toInt() and 0xFF) + 1
            copy[i] = v.toByte()
            if (v <= 0xFF) break
        }
        return copy
    }

    fun generateSessionKey(size: Int = 16): ByteArray {
        val key = ByteArray(size)
        secureRandom.nextBytes(key)
        return key
    }

    fun encryptRsa(data: ByteArray, publicKey: PublicKey): ByteArray {
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, publicKey)
        return cipher.doFinal(data)
    }

    fun decryptRsa(ciphertext: ByteArray, privateKey: PrivateKey): ByteArray {
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.DECRYPT_MODE, privateKey)
        return cipher.doFinal(ciphertext)
    }

    fun encryptAesGcm(plaintext: ByteArray, key: ByteArray, iv: ByteArray = generateNonce(12)): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val keySpec = SecretKeySpec(key, "AES")
        val gcmSpec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)
        val ciphertext = cipher.doFinal(plaintext)
        return iv + ciphertext
    }

    fun decryptAesGcm(envelope: ByteArray, key: ByteArray): ByteArray {
        require(envelope.size >= 12 + 16) { "Encrypted envelope too short: ${envelope.size}" }
        val iv = envelope.copyOfRange(0, 12)
        val ciphertextWithTag = envelope.copyOfRange(12, envelope.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val keySpec = SecretKeySpec(key, "AES")
        val gcmSpec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)
        return cipher.doFinal(ciphertextWithTag)
    }
}
