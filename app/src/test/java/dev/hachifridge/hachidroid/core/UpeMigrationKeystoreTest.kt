package dev.hachifridge.hachidroid.core

import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Security
import java.security.cert.X509Certificate
import java.util.Date
import java.util.Locale

class UpeMigrationKeystoreTest {

    private fun selfSignedCertificate(pair: java.security.KeyPair): X509Certificate {
        val x500Name = X500Name("CN=UmaPatcher Edge")
        val builder = X509v3CertificateBuilder(
            x500Name,
            BigInteger.valueOf(1),
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
    fun convertPkcs12ToBks_preservesKeyAndCertificate() {
        Security.addProvider(BouncyCastleProvider())

        // PKCS12 keystore shaped like upstream's universal key (alias "patcheduma").
        val generator = KeyPairGenerator.getInstance("RSA")
        generator.initialize(2048)
        val pair = generator.generateKeyPair()
        val certificate = selfSignedCertificate(pair)

        val pkcs12 = KeyStore.getInstance("PKCS12")
        pkcs12.load(null, null)
        pkcs12.setKeyEntry(
            "patcheduma",
            pair.private,
            UpeMigration.KS_PASSWORD.toCharArray(),
            arrayOf(certificate)
        )

        val pkcs12File = File.createTempFile("universal", ".p12").apply { delete() }
        val bksFile = File.createTempFile("converted", ".bks").apply { delete() }
        try {
            pkcs12File.outputStream().use {
                pkcs12.store(it, UpeMigration.KS_PASSWORD.toCharArray())
            }

            UpeMigration.convertPkcs12ToBks(pkcs12File, bksFile)

            // Consume it the same way ApkSigner.signApk() does.
            val importedKey = UpeMigration.verifyBks(bksFile)
            assertArrayEquals(pair.private.encoded, importedKey.encoded)

            val bks = KeyStore.getInstance("BKS", "BC")
            bksFile.inputStream().use { bks.load(it, null) }
            assertTrue(bks.aliases().hasMoreElements())
            val alias = bks.aliases().nextElement()
            assertEquals(certificate, bks.getCertificate(alias))
        } finally {
            pkcs12File.delete()
            bksFile.delete()
        }
    }

    @Test
    fun verifyBks_readsKeystoreWithHachidroidPassword() {
        Security.addProvider(BouncyCastleProvider())

        val generator = KeyPairGenerator.getInstance("RSA")
        generator.initialize(2048)
        val pair = generator.generateKeyPair()
        val certificate = selfSignedCertificate(pair)

        val bks = KeyStore.getInstance("BKS", "BC")
        bks.load(null, dev.hachifridge.hachidroid.patcher.HACHIDROID_KS_PASSWORD.toCharArray())
        bks.setKeyEntry(
            "hachidroid",
            pair.private,
            dev.hachifridge.hachidroid.patcher.HACHIDROID_KS_PASSWORD.toCharArray(),
            arrayOf(certificate)
        )

        val bksFile = File.createTempFile("hachidroid_test", ".bks")
        try {
            bksFile.outputStream().use {
                bks.store(it, dev.hachifridge.hachidroid.patcher.HACHIDROID_KS_PASSWORD.toCharArray())
            }

            val key = UpeMigration.verifyBks(bksFile)
            assertArrayEquals(pair.private.encoded, key.encoded)
        } finally {
            bksFile.delete()
        }
    }

    @Test(expected = Exception::class)
    fun verifyBks_rejectsInvalidFile() {
        Security.addProvider(BouncyCastleProvider())
        val dummy = File.createTempFile("invalid", ".bin")
        try {
            dummy.writeText("not a valid keystore")
            UpeMigration.verifyBks(dummy)
        } finally {
            dummy.delete()
        }
    }
}
