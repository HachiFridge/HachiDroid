package dev.hachifridge.hachidroid.patcher

import dev.hachifridge.hachidroid.core.UpeMigration
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.Security
import java.security.UnrecoverableKeyException
import java.security.cert.X509Certificate
import java.util.Date
import java.util.Locale

class ApkSignerTest {

    init {
        Security.addProvider(BouncyCastleProvider())
    }

    private fun load(file: File): KeyStore {
        val ks = KeyStore.getInstance("BKS", "BC")
        file.inputStream().use { ks.load(it, null) }
        return ks
    }

    private fun selfSignedCertificate(pair: java.security.KeyPair): X509Certificate {
        val x500Name = X500Name("CN=UmaPatcher Edge")
        val builder = X509v3CertificateBuilder(
            x500Name,
            BigInteger.valueOf(SecureRandom().nextLong().coerceAtLeast(1L)),
            Date(System.currentTimeMillis() - 60_000L),
            Date(System.currentTimeMillis() + 3_600_000_000L),
            Locale.ENGLISH,
            x500Name,
            SubjectPublicKeyInfo.getInstance(pair.public.encoded)
        )
        val signer = JcaContentSignerBuilder("SHA256withRSA").build(pair.private)
        return JcaX509CertificateConverter().getCertificate(builder.build(signer))
    }

    @Test
    fun defaultKeystore_usesHachiDroidIdentityNotUpes() {
        val file = File.createTempFile("hachidroid", ".bks").apply { deleteOnExit() }

        ApkSigner("HachiDroid").newKeystore(file)

        val ks = load(file)
        val alias = ks.aliases().nextElement()
        assertEquals("hachidroid", alias)
        assertTrue(ks.getCertificate(alias).toString().contains("CN=HachiDroid"))

        // HachiDroid's password opens it; UPE's must not.
        assertTrue(loadSigningKey(ks, alias) is PrivateKey)
        assertThrows(UnrecoverableKeyException::class.java) {
            ks.getKey(alias, UpeMigration.KS_PASSWORD.toCharArray())
        }
    }

    @Test
    fun loadSigningKey_fallsBackToUpePasswordForImportedKeystores() {
        val file = File.createTempFile("upe", ".bks").apply { deleteOnExit() }

        // Shaped like a keystore UpeMigration copies in as-is: UPE's password and alias.
        val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val certificate = selfSignedCertificate(pair)
        val upeKs = KeyStore.getInstance("BKS", "BC")
        upeKs.load(null, UpeMigration.KS_PASSWORD.toCharArray())
        upeKs.setKeyEntry(
            "patcheduma",
            pair.private,
            UpeMigration.KS_PASSWORD.toCharArray(),
            arrayOf(certificate)
        )
        file.outputStream().use { upeKs.store(it, UpeMigration.KS_PASSWORD.toCharArray()) }

        val key = loadSigningKey(load(file), "patcheduma")
        assertEquals(pair.private.encoded.toList(), key.encoded.toList())
    }
}
