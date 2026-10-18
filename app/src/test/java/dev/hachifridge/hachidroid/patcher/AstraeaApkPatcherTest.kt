package dev.hachifridge.hachidroid.patcher

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AstraeaApkPatcherTest {

    private fun tempDir(): File = Files.createTempDirectory("priconne-patcher").toFile()

    private fun File.touch(path: String, bytes: ByteArray = byteArrayOf(1, 2, 3)): File {
        val file = resolve(path)
        file.parentFile?.mkdirs()
        file.writeBytes(bytes)
        return file
    }

    @Test
    fun baseSplitIsTheOneWithClassesDex() {
        val dir = tempDir()
        dir.touch("classes.dex")
        dir.touch("AndroidManifest.xml")

        assertTrue(AstraeaApkPatcher.isBaseSplit(dir))
        assertFalse(AstraeaApkPatcher.isConfigSplit(dir))
    }

    @Test
    fun configSplitIsTheOneWithArm64Libraries() {
        val dir = tempDir()
        dir.touch("lib/arm64-v8a/libil2cpp.so")
        dir.touch("AndroidManifest.xml")

        assertTrue(AstraeaApkPatcher.isConfigSplit(dir))
        assertFalse(AstraeaApkPatcher.isBaseSplit(dir))
    }

    @Test
    fun crackProofStubsAreRecognisedByIdShape() {
        assertTrue(AstraeaApkPatcher.isCrackProofStub("lib__1b40__.so"))
        assertTrue(AstraeaApkPatcher.isCrackProofStub("lib__abcdef0123456789__.so"))

        // The libraries a patched install needs must never be treated as stubs.
        AstraeaApkPatcher.REQUIRED_LIBS.forEach { name ->
            assertFalse(name, AstraeaApkPatcher.isCrackProofStub(name))
        }
        assertFalse(AstraeaApkPatcher.isCrackProofStub("libmain.so"))
        assertFalse(AstraeaApkPatcher.isCrackProofStub("lib__stub.so"))
    }

    @Test
    fun configPatchWritesEveryRequiredLibraryAndDropsStubs() {
        val libsDir = tempDir()
        val configDir = tempDir()

        val payloads = AstraeaApkPatcher.REQUIRED_LIBS.associateWith { name ->
            ByteArray(name.length) { it.toByte() }
        }
        payloads.forEach { (name, bytes) -> libsDir.touch(name, bytes) }

        configDir.touch("lib/arm64-v8a/lib__1b40__.so")

        val logs = mutableListOf<String>()
        val patched = AstraeaApkPatcher.patchConfigSplit(configDir, libsDir) { logs += it }

        assertTrue(logs.joinToString("\n"), patched)
        assertFalse(configDir.resolve("lib/arm64-v8a/lib__1b40__.so").exists())

        payloads.forEach { (name, bytes) ->
            val written = configDir.resolve("lib/arm64-v8a/$name")
            assertTrue("$name missing", written.isFile)
            assertArrayEquals(bytes, written.readBytes())
        }
    }

    @Test
    fun configPatchFailsWhenALibraryIsMissing() {
        val libsDir = tempDir()
        val configDir = tempDir()
        configDir.touch("lib/arm64-v8a/libil2cpp.so")

        libsDir.touch("libil2cpp.so")
        libsDir.touch("libnative.so")
        // libdaringtact.so deliberately absent

        val logs = mutableListOf<String>()
        val patched = AstraeaApkPatcher.patchConfigSplit(configDir, libsDir) { logs += it }

        assertFalse(patched)
        assertTrue(logs.any { it.contains("libdaringtact.so") })
    }

    @Test
    fun emptyLibraryIsTreatedAsMissing() {
        val libsDir = tempDir()
        val configDir = tempDir()
        configDir.touch("lib/arm64-v8a/libil2cpp.so")

        AstraeaApkPatcher.REQUIRED_LIBS.forEach { libsDir.touch(it) }
        // A truncated download leaves a zero-byte file that would still "exist".
        libsDir.resolve("libnative.so").writeBytes(ByteArray(0))

        val logs = mutableListOf<String>()
        val patched = AstraeaApkPatcher.patchConfigSplit(configDir, libsDir) { logs += it }

        assertFalse(patched)
        assertEquals(1, logs.count { it == "Missing library: libnative.so" })
    }
}
