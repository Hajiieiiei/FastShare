package com.fastshare.app.services.security

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.bouncycastle.asn1.x500.X500NameBuilder
import org.bouncycastle.asn1.x500.style.BCStyle
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.ExtendedKeyUsage
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralNames
import org.bouncycastle.asn1.x509.KeyPurposeId
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.Security
import java.security.cert.X509Certificate
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Generates and persists a long-lived self-signed RSA certificate used for TLS on both the
 * embedded server and outbound client sockets.
 *
 * Self-signed is the correct choice here: there is no CA reachable offline, and trust is
 * established by pinning the certificate fingerprint at pairing time (SSH-style TOFU) rather
 * than by chain validation. The keystore lives in app-private storage.
 */
@Singleton
class CertificateProvider @Inject constructor(
    private val context: Context,
) {
    private val mutex = Mutex()

    @Volatile private var cached: Identity? = null

    data class Identity(
        val keyPair: KeyPair,
        val certificate: X509Certificate,
        val keyStore: KeyStore,
        val keyStorePassword: CharArray,
        val fingerprint: String,
    )

    suspend fun identity(): Identity = cached ?: mutex.withLock {
        cached ?: withContext(Dispatchers.IO) { loadOrCreate().also { cached = it } }
    }

    suspend fun fingerprint(): String = identity().fingerprint

    suspend fun privateKey(): PrivateKey = identity().keyPair.private

    suspend fun certificate(): X509Certificate = identity().certificate

    private fun loadOrCreate(): Identity {
        registerProvider()
        val file = File(context.filesDir, KEYSTORE_FILE)
        val password = keyStorePassword()
        val keyStore = KeyStore.getInstance(KEYSTORE_TYPE)

        if (file.exists()) {
            runCatching {
                file.inputStream().use { keyStore.load(it, password) }
                val cert = keyStore.getCertificate(KEY_ALIAS) as X509Certificate
                val key = keyStore.getKey(KEY_ALIAS, password) as PrivateKey
                // Rotate if the certificate is expired rather than failing every handshake.
                cert.checkValidity()
                return Identity(
                    keyPair = KeyPair(cert.publicKey, key),
                    certificate = cert,
                    keyStore = keyStore,
                    keyStorePassword = password,
                    fingerprint = sha256Hex(cert.encoded),
                )
            }.onFailure { file.delete() }
        }

        val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(KEY_SIZE, SecureRandom()) }.generateKeyPair()
        val certificate = selfSign(keyPair)
        val freshStore = KeyStore.getInstance(KEYSTORE_TYPE).apply {
            load(null, password)
            setKeyEntry(KEY_ALIAS, keyPair.private, password, arrayOf(certificate))
        }
        file.outputStream().use { freshStore.store(it, password) }

        return Identity(
            keyPair = keyPair,
            certificate = certificate,
            keyStore = freshStore,
            keyStorePassword = password,
            fingerprint = sha256Hex(certificate.encoded),
        )
    }

    private fun selfSign(keyPair: KeyPair): X509Certificate {
        val now = System.currentTimeMillis()
        val notBefore = Date(now - CLOCK_SKEW_MS)
        val notAfter = Date(now + VALIDITY_MS)
        val subject = X500NameBuilder(BCStyle.INSTANCE)
            .addRDN(BCStyle.CN, "FastShare")
            .addRDN(BCStyle.O, "FastShare Local")
            .build()

        val builder = JcaX509v3CertificateBuilder(
            subject,
            BigInteger.valueOf(now),
            notBefore,
            notAfter,
            subject,
            keyPair.public,
        ).apply {
            addExtension(Extension.basicConstraints, true, BasicConstraints(false))
            addExtension(
                Extension.extendedKeyUsage, false,
                ExtendedKeyUsage(arrayOf(KeyPurposeId.id_kp_serverAuth, KeyPurposeId.id_kp_clientAuth)),
            )
            // SANs cover any local address the device may hold; certificates are pinned, not name-validated.
            addExtension(
                Extension.subjectAlternativeName, false,
                GeneralNames(
                    arrayOf(
                        GeneralName(GeneralName.dNSName, "fastshare.local"),
                        GeneralName(GeneralName.iPAddress, "0.0.0.0"),
                    ),
                ),
            )
        }

        val signer = JcaContentSignerBuilder(SIGNATURE_ALGORITHM)
            .setProvider(BouncyCastleProvider.PROVIDER_NAME)
            .build(keyPair.private)

        return JcaX509CertificateConverter()
            .setProvider(BouncyCastleProvider.PROVIDER_NAME)
            .getCertificate(builder.build(signer))
    }

    /**
     * Keystore password derived from a random value persisted in app-private prefs.
     * The keystore file itself is already inside the app sandbox; this guards against
     * casual extraction from a backup.
     */
    private fun keyStorePassword(): CharArray {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = prefs.getString(PREF_KEY_PASSWORD, null)
        if (existing != null) return existing.toCharArray()
        val generated = ByteArray(32).also { SecureRandom().nextBytes(it) }.toBase64()
        prefs.edit().putString(PREF_KEY_PASSWORD, generated).apply()
        return generated.toCharArray()
    }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02X".format(it) }

    private fun registerProvider() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(BouncyCastleProvider())
        }
    }

    private companion object {
        const val KEYSTORE_FILE = "fastshare_tls.p12"
        const val KEYSTORE_TYPE = "PKCS12"
        const val KEY_ALIAS = "fastshare"
        const val KEY_SIZE = 2048
        const val SIGNATURE_ALGORITHM = "SHA256WithRSA"
        const val VALIDITY_MS = 10L * 365 * 24 * 60 * 60 * 1000
        const val CLOCK_SKEW_MS = 24L * 60 * 60 * 1000
        const val PREFS_NAME = "fastshare_secure"
        const val PREF_KEY_PASSWORD = "ks_pw"
    }
}
