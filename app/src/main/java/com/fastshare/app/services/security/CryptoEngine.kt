package com.fastshare.app.services.security

import android.util.Base64
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.experimental.xor

/**
 * Application-layer cryptography.
 *
 * TLS already protects bytes in flight; this layer adds an independent ECDH-derived AES-256-GCM
 * key so control payloads and item tokens stay confidential even if a peer presents a swapped
 * certificate, and so pairing can be verified out-of-band by comparing short fingerprints.
 *
 * - Key agreement: ECDH on secp256r1 (P-256), ephemeral per session.
 * - KDF: HKDF-SHA256 with both nonces as salt, binding the key to this exact handshake.
 * - Cipher: AES-256-GCM, 96-bit random IV prefixed to ciphertext, 128-bit tag.
 */
@Singleton
class CryptoEngine @Inject constructor() {

    private val secureRandom = SecureRandom()

    fun generateEcdhKeyPair(): KeyPair =
        KeyPairGenerator.getInstance(ALGORITHM_EC).apply {
            initialize(java.security.spec.ECGenParameterSpec(EC_CURVE), secureRandom)
        }.generateKeyPair()

    fun encodePublicKey(keyPair: KeyPair): String = keyPair.public.encoded.toBase64()

    fun decodePublicKey(encoded: String): java.security.PublicKey =
        KeyFactory.getInstance(ALGORITHM_EC).generatePublic(X509EncodedKeySpec(encoded.fromBase64()))

    fun randomNonce(sizeBytes: Int = 16): String = ByteArray(sizeBytes).also(secureRandom::nextBytes).toBase64()

    fun randomToken(sizeBytes: Int = 32): String = ByteArray(sizeBytes).also(secureRandom::nextBytes).toBase64Url()

    /**
     * Derives the shared session key. Nonces are sorted before concatenation so both peers
     * compute an identical salt regardless of who initiated.
     */
    fun deriveSessionKey(
        localKeyPair: KeyPair,
        remotePublicKeyEncoded: String,
        localNonce: String,
        remoteNonce: String,
    ): SessionKey {
        val sharedSecret = KeyAgreement.getInstance(ALGORITHM_ECDH).run {
            init(localKeyPair.private)
            doPhase(decodePublicKey(remotePublicKeyEncoded), true)
            generateSecret()
        }
        val salt = listOf(localNonce, remoteNonce).sorted().joinToString(":").toByteArray()
        val keyBytes = hkdf(sharedSecret, salt, HKDF_INFO.toByteArray(), AES_KEY_SIZE_BYTES)
        sharedSecret.fill(0)
        return SessionKey(keyBytes)
    }

    fun encrypt(key: SessionKey, plaintext: ByteArray, aad: ByteArray? = null): ByteArray {
        val iv = ByteArray(GCM_IV_SIZE).also(secureRandom::nextBytes)
        val cipher = Cipher.getInstance(AES_TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, key.spec(), GCMParameterSpec(GCM_TAG_BITS, iv))
            aad?.let { updateAAD(it) }
        }
        return iv + cipher.doFinal(plaintext)
    }

    fun decrypt(key: SessionKey, payload: ByteArray, aad: ByteArray? = null): ByteArray {
        require(payload.size > GCM_IV_SIZE) { "Ciphertext too short" }
        val iv = payload.copyOfRange(0, GCM_IV_SIZE)
        val body = payload.copyOfRange(GCM_IV_SIZE, payload.size)
        val cipher = Cipher.getInstance(AES_TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key.spec(), GCMParameterSpec(GCM_TAG_BITS, iv))
            aad?.let { updateAAD(it) }
        }
        return cipher.doFinal(body)
    }

    fun encryptToString(key: SessionKey, plaintext: String): String = encrypt(key, plaintext.toByteArray()).toBase64()

    fun decryptToString(key: SessionKey, payload: String): String = String(decrypt(key, payload.fromBase64()))

    /**
     * Short, human-comparable fingerprint of a certificate or public key.
     * Rendered in the pairing dialog as four hex groups so users can verify out-of-band.
     */
    fun fingerprintOf(bytes: ByteArray): String =
        MessageDigest.getInstance(HASH_SHA256).digest(bytes)
            .joinToString("") { "%02X".format(it) }

    fun fingerprintOfPublicKey(encodedPublicKey: String): String = fingerprintOf(encodedPublicKey.fromBase64())

    /** Constant-time comparison; avoids leaking match position when verifying tokens/fingerprints. */
    fun constantTimeEquals(a: String, b: String): Boolean {
        val x = a.toByteArray()
        val y = b.toByteArray()
        if (x.size != y.size) return false
        var diff: Byte = 0
        for (i in x.indices) diff = diff or (x[i] xor y[i])
        return diff == 0.toByte()
    }

    /** RFC 5869 HKDF-SHA256. */
    private fun hkdf(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
        val mac = javax.crypto.Mac.getInstance(HMAC_SHA256)
        mac.init(SecretKeySpec(salt.ifEmpty { ByteArray(32) }, HMAC_SHA256))
        val prk = mac.doFinal(ikm)

        mac.init(SecretKeySpec(prk, HMAC_SHA256))
        val output = ByteArray(length)
        var previous = ByteArray(0)
        var generated = 0
        var counter = 1
        while (generated < length) {
            mac.reset()
            mac.update(previous)
            mac.update(info)
            mac.update(counter.toByte())
            previous = mac.doFinal()
            val toCopy = minOf(previous.size, length - generated)
            previous.copyInto(output, generated, 0, toCopy)
            generated += toCopy
            counter++
        }
        return output
    }

    companion object {
        private const val ALGORITHM_EC = "EC"
        private const val ALGORITHM_ECDH = "ECDH"
        private const val EC_CURVE = "secp256r1"
        private const val AES_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val HMAC_SHA256 = "HmacSHA256"
        private const val HASH_SHA256 = "SHA-256"
        private const val HKDF_INFO = "FastShare/v1/session-key"
        const val AES_KEY_SIZE_BYTES = 32
        const val GCM_IV_SIZE = 12
        const val GCM_TAG_BITS = 128
    }
}

/** Wrapper that keeps raw key material out of logs and equals/hashCode. */
class SessionKey(private val keyBytes: ByteArray) {
    init { require(keyBytes.size == CryptoEngine.AES_KEY_SIZE_BYTES) { "Session key must be 256-bit" } }

    internal fun spec(): SecretKeySpec = SecretKeySpec(keyBytes, "AES")

    fun exportBase64(): String = keyBytes.toBase64()

    fun destroy() = keyBytes.fill(0)

    override fun toString(): String = "SessionKey(**redacted**)"
    override fun equals(other: Any?): Boolean = other is SessionKey && keyBytes.contentEquals(other.keyBytes)
    override fun hashCode(): Int = keyBytes.contentHashCode()

    companion object {
        fun fromBase64(encoded: String): SessionKey = SessionKey(encoded.fromBase64())
    }
}

fun ByteArray.toBase64(): String = Base64.encodeToString(this, Base64.NO_WRAP)
fun ByteArray.toBase64Url(): String = Base64.encodeToString(this, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
fun String.fromBase64(): ByteArray = Base64.decode(this, Base64.NO_WRAP)
