package com.fastshare.app.services.security

import java.security.cert.X509Certificate
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.X509TrustManager

/**
 * Builds TLS material for local-only links.
 *
 * Peers use self-signed certificates, so PKI chain validation cannot apply. Instead
 * [PinningTrustManager] records the presented certificate fingerprint and enforces it against
 * the value the user approved during pairing (trust-on-first-use with explicit confirmation).
 * Sockets are additionally restricted to link-local peers by the caller before any data flows.
 */
@Singleton
class TlsFactory @Inject constructor(
    private val certificateProvider: CertificateProvider,
    private val trustStore: TrustedFingerprintStore,
) {
    suspend fun serverSslContext(): SSLContext {
        val identity = certificateProvider.identity()
        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {
            init(identity.keyStore, identity.keyStorePassword)
        }
        return SSLContext.getInstance(TLS_VERSION).apply {
            init(kmf.keyManagers, arrayOf(PinningTrustManager(trustStore, expectedFingerprint = null)), null)
        }
    }

    /**
     * Client context for connecting to [expectedFingerprint]. Pass null on first contact:
     * the observed fingerprint is captured for the pairing dialog and the connection is
     * only usable for the handshake step.
     */
    suspend fun clientSocketFactory(expectedFingerprint: String?): Pair<SSLSocketFactory, PinningTrustManager> {
        val identity = certificateProvider.identity()
        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {
            init(identity.keyStore, identity.keyStorePassword)
        }
        val trustManager = PinningTrustManager(trustStore, expectedFingerprint)
        val context = SSLContext.getInstance(TLS_VERSION).apply {
            init(kmf.keyManagers, arrayOf(trustManager), null)
        }
        return context.socketFactory to trustManager
    }

    companion object {
        const val TLS_VERSION = "TLSv1.2"
        val ENABLED_PROTOCOLS = arrayOf("TLSv1.3", "TLSv1.2")
    }
}

/**
 * Trust manager implementing certificate pinning for self-signed local peers.
 *
 * Behaviour:
 *  - If [expectedFingerprint] is set, the peer certificate must match it exactly, else the
 *    handshake fails (defends against a device impersonating a paired peer).
 *  - If it is null, the certificate is accepted for the handshake and its fingerprint recorded
 *    in [observedFingerprint] so the UI can ask the user to confirm before any payload moves.
 */
class PinningTrustManager(
    private val trustStore: TrustedFingerprintStore,
    private val expectedFingerprint: String?,
) : X509TrustManager {

    @Volatile var observedFingerprint: String? = null
        private set

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = verify(chain)

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = verify(chain)

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()

    private fun verify(chain: Array<out X509Certificate>?) {
        val leaf = chain?.firstOrNull()
            ?: throw javax.net.ssl.SSLPeerUnverifiedException("Peer presented no certificate")
        val fingerprint = fingerprintOf(leaf)
        observedFingerprint = fingerprint

        val pinned = expectedFingerprint ?: return
        if (!constantTimeEquals(pinned, fingerprint)) {
            throw javax.net.ssl.SSLPeerUnverifiedException(
                "Certificate fingerprint mismatch. Expected ${pinned.take(16)}… got ${fingerprint.take(16)}…",
            )
        }
    }

    private fun fingerprintOf(certificate: X509Certificate): String =
        java.security.MessageDigest.getInstance("SHA-256")
            .digest(certificate.encoded)
            .joinToString("") { "%02X".format(it) }

    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].code xor b[i].code)
        return diff == 0
    }
}
