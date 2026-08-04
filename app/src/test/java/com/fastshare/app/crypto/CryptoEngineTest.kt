package com.fastshare.app.crypto

import com.fastshare.app.services.security.CryptoEngine
import com.fastshare.app.services.security.SessionKey
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CryptoEngineTest {
    private val crypto = CryptoEngine()

    @Test
    fun `two parties derive the same session key`() {
        // Peer A
        val aKey = crypto.generateEcdhKeyPair()
        val aNonce = crypto.randomNonce()
        val aPublic = crypto.encodePublicKey(aKey)

        // Peer B
        val bKey = crypto.generateEcdhKeyPair()
        val bNonce = crypto.randomNonce()
        val bPublic = crypto.encodePublicKey(bKey)

        val aDerived = crypto.deriveSessionKey(aKey, bPublic, aNonce, bNonce)
        val bDerived = crypto.deriveSessionKey(bKey, aPublic, bNonce, aNonce)

        assertThat(aDerived).isEqualTo(bDerived)
    }

    @Test
    fun `encrypt then decrypt returns original`() {
        val pair = crypto.generateEcdhKeyPair()
        val key = crypto.deriveSessionKey(pair, crypto.encodePublicKey(pair), crypto.randomNonce(), crypto.randomNonce())
        val plaintext = "FastShare end-to-end test payload".toByteArray()

        val ciphertext = crypto.encrypt(key, plaintext)
        val decrypted = crypto.decrypt(key, ciphertext)

        assertThat(decrypted).isEqualTo(plaintext)
    }

    @Test
    fun `fingerprints are stable and hex`() {
        val fp = crypto.fingerprintOf("hello".toByteArray())
        assertThat(fp).matches("^[0-9A-F]+$")
        assertThat(crypto.fingerprintOf("hello".toByteArray())).isEqualTo(fp)
    }

    @Test
    fun `constant time equals matches content`() {
        assertThat(crypto.constantTimeEquals("abc", "abc")).isTrue()
        assertThat(crypto.constantTimeEquals("abc", "abd")).isFalse()
        assertThat(crypto.constantTimeEquals("abc", "ab")).isFalse()
    }

    @Test
    fun `session key redacts to string`() {
        val key = SessionKey.fromBase64("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
        assertThat(key.toString()).contains("redacted")
    }
}
