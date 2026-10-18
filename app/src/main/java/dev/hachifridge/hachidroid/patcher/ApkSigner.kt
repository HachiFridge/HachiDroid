package dev.hachifridge.hachidroid.patcher

import com.android.apksig.ApkSigner
import com.android.apksig.KeyConfig
import dev.hachifridge.hachidroid.core.UpeMigration
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.ContentSigner
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
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

// Password for HachiDroid-generated keystores only; keys imported from UPE keep UPE's password.
internal const val HACHIDROID_KS_PASSWORD = "H@ch1m13697"
private const val HACHIDROID_KS_ALIAS = "hachidroid"

internal class ApkSigner(private val cn: String) {
    private val passwordCharArray = HACHIDROID_KS_PASSWORD.toCharArray()

    internal fun newKeystore(out: File) {
        val (publicKey, privateKey) = createKey()
        val privateKS = KeyStore.getInstance("BKS", "BC")
        privateKS.load(null, passwordCharArray)
        privateKS.setKeyEntry(HACHIDROID_KS_ALIAS, privateKey, passwordCharArray, arrayOf(publicKey))
        privateKS.store(FileOutputStream(out), passwordCharArray)
    }

    private fun createKey(): Pair<X509Certificate, PrivateKey> {
        val gen = KeyPairGenerator.getInstance("RSA")
        gen.initialize(2048)
        val pair = gen.generateKeyPair()
        var serialNumber: BigInteger
        do serialNumber =
            BigInteger.valueOf(SecureRandom().nextLong()) while (serialNumber < BigInteger.ZERO)
        val x500Name = X500Name("CN=$cn")
        val builder = X509v3CertificateBuilder(
            x500Name,
            serialNumber,
            Date(System.currentTimeMillis() - 1000L * 60L * 60L * 24L * 30L),
            Date(System.currentTimeMillis() + 1000L * 60L * 60L * 24L * 366L * 30L),
            Locale.ENGLISH,
            x500Name,
            SubjectPublicKeyInfo.getInstance(pair.public.encoded)
        )
        val signer: ContentSigner = JcaContentSignerBuilder("SHA256withRSA").build(pair.private)
        return JcaX509CertificateConverter().getCertificate(builder.build(signer)) to pair.private
    }

    fun signApk(input: File, output: File, ks: File) {
        Security.addProvider(BouncyCastleProvider())

        if (!ks.exists()) newKeystore(ks)

        val keyStore = KeyStore.getInstance("BKS", "BC")
        FileInputStream(ks).use { fis -> keyStore.load(fis, null) }
        val alias = keyStore.aliases().nextElement()
        val privateKey = loadSigningKey(keyStore, alias)

        val config = ApkSigner.SignerConfig.Builder(
            cn,
            KeyConfig.Jca(privateKey),
            listOf(keyStore.getCertificate(alias) as X509Certificate)
        ).build()

        val signer = ApkSigner.Builder(listOf(config))
        // v1 (JAR signing) digests every entry separately and is only needed below
        // Android 7.0; the app and the game both require API 24+, so v2/v3 suffice.
        signer.setV1SigningEnabled(false)
        signer.setV2SigningEnabled(true)
        signer.setV3SigningEnabled(true)
        signer.setV4SigningEnabled(false)
        signer.setCreatedBy(cn)
        signer.setInputApk(input)
        signer.setOutputApk(output)

        signer.build().sign()
    }
}

/**
 * Reads a signing key with HachiDroid's password (default keystores), falling back
 * to UPE's password for keystores imported by UpeMigration.
 */
internal fun loadSigningKey(keyStore: KeyStore, alias: String): PrivateKey {
    for (password in listOf(HACHIDROID_KS_PASSWORD, UpeMigration.KS_PASSWORD)) {
        try {
            val key = keyStore.getKey(alias, password.toCharArray()) as? PrivateKey
            if (key != null) return key
        } catch (_: UnrecoverableKeyException) {
            // Wrong password; try the next candidate.
        }
    }
    throw IllegalStateException("Signing key is unreadable with HachiDroid's and UPE's passwords")
}
