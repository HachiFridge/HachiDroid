package dev.hachifridge.hachidroid.patcher

import com.reandroid.arsc.chunk.xml.AndroidManifestBlock
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Exercises both patches against a real Priconne split set.
 *
 * The split's manifest is a compiled binary whose attribute layout cannot be faked, so this is the
 * only check that the reandroid rewrite actually lands. Fixtures are never committed; point the
 * environment at them to run it:
 *
 * ```
 * PRICONNE_BASE_APK=.../base.apk \
 * PRICONNE_CONFIG_APK=.../config.arm64_v8a.apk \
 * PRICONNE_LIBS_DIR=.../priconne-arm64-v8a \
 *   ./gradlew :app:testDebugUnitTest --tests '*AstraeaApkPatcherFixtureTest*'
 * ```
 *
 * Without them every case here is skipped, which is why the plain unit tests stay independent.
 */
class AstraeaApkPatcherFixtureTest {

    private val baseApk = System.getenv("PRICONNE_BASE_APK")?.let(::File)
    private val configApk = System.getenv("PRICONNE_CONFIG_APK")?.let(::File)
    private val libsDir = System.getenv("PRICONNE_LIBS_DIR")?.let(::File)

    private fun unzip(apk: File, into: File): File {
        ZipInputStream(apk.inputStream().buffered()).use { zip ->
            generateSequence { zip.nextEntry }.forEach { entry ->
                val target = into.resolve(entry.name)
                if (entry.isDirectory) {
                    target.mkdirs()
                } else {
                    target.parentFile?.mkdirs()
                    target.outputStream().buffered().use { output -> zip.copyTo(output) }
                }
                zip.closeEntry()
            }
        }
        return into
    }

    private fun tempDir(prefix: String): File = Files.createTempDirectory(prefix).toFile()

    @Test
    fun baseSplitIsPatchedIntoANewApplicationAndPermissions() {
        assumeTrue("PRICONNE_BASE_APK not set", baseApk?.isFile == true)

        val extractDir = unzip(baseApk!!, tempDir("priconne-base"))
        assertTrue(AstraeaApkPatcher.isBaseSplit(extractDir))

        val dexCountBefore = extractDir.listFiles { f -> f.name.matches(Regex("^classes(\\d+)?\\.dex$")) }!!.size
        val logs = mutableListOf<String>()

        // The patcher only writes the blob; dex validity belongs to d8, not to this step.
        val injected = "dex\n035\u0000".toByteArray()
        assertTrue(logs.joinToString("\n"), AstraeaApkPatcher.patchBaseSplit(extractDir, injected) { logs += it })

        val manifest = AndroidManifestBlock.load(extractDir.resolve("AndroidManifest.xml"))
        val applicationName = manifest.applicationElement
            ?.searchAttributeByName(AndroidManifestBlock.NAME_name)
            ?.valueAsString
        assertEquals(AstraeaApkPatcher.APPLICATION_CLASS, applicationName)

        val permissions = manifest.manifestElement
            ?.listElements("uses-permission")
            ?.mapNotNull { it.searchAttributeByName(AndroidManifestBlock.NAME_name)?.valueAsString }
            ?.toSet()
            .orEmpty()
        assertTrue("READ_MEDIA_IMAGES missing", "android.permission.READ_MEDIA_IMAGES" in permissions)
        assertTrue("MANAGE_EXTERNAL_STORAGE missing", "android.permission.MANAGE_EXTERNAL_STORAGE" in permissions)

        val dexCountAfter = extractDir.listFiles { f -> f.name.matches(Regex("^classes(\\d+)?\\.dex$")) }!!.size
        assertEquals(dexCountBefore + 1, dexCountAfter)
        assertTrue(extractDir.listFiles()!!.any { it.name == "classes2.dex" || it.name == "classes${dexCountAfter}.dex" })
        assertFalse("lib/ must not stay in the base split", extractDir.resolve("lib").exists())

        println("[priconne] base split patched:\n" + logs.joinToString("\n"))
        extractDir.deleteRecursively()
    }

    @Test
    fun configSplitEndsUpWithExactlyTheThreeLibraries() {
        assumeTrue("PRICONNE_CONFIG_APK not set", configApk?.isFile == true)
        assumeTrue("PRICONNE_LIBS_DIR not set", libsDir?.isDirectory == true)

        val extractDir = unzip(configApk!!, tempDir("priconne-config"))
        assertTrue(AstraeaApkPatcher.isConfigSplit(extractDir))

        val downloaded = libsDir!!.resolve("libdaringtact.so")
        assumeTrue("libs dir lacks the required libraries", downloaded.isFile)

        val logs = mutableListOf<String>()
        assertTrue(logs.joinToString("\n"), AstraeaApkPatcher.patchConfigSplit(extractDir, libsDir) { logs += it })

        val arm64 = extractDir.resolve("lib/arm64-v8a")
        val stubs = arm64.listFiles()!!.filter { AstraeaApkPatcher.isCrackProofStub(it.name) }
        assertTrue("CrackProof stubs survived: $stubs", stubs.isEmpty())

        AstraeaApkPatcher.REQUIRED_LIBS.forEach { name ->
            val patched = arm64.resolve(name)
            assertTrue("$name missing after patch", patched.isFile)
            assertEquals("$name size differs from the imported copy", libsDir.resolve(name).length(), patched.length())
            assertTrue("$name is empty", patched.length() > 0L)
        }

        println("[priconne] config split patched:\n" + logs.joinToString("\n"))
        extractDir.deleteRecursively()
    }
}
