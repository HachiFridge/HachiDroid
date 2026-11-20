package dev.hachifridge.hachidroid.patcher

import dev.hachifridge.hachidroid.patcher.tracen.GlbApkPatcher
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlbApkPatcherTest {

    private fun tempDir(): File = Files.createTempDirectory("glb-patcher").toFile()

    private fun File.touch(path: String, bytes: ByteArray = byteArrayOf(1, 2, 3)): File {
        val file = resolve(path)
        file.parentFile?.mkdirs()
        file.writeBytes(bytes)
        return file
    }

    @Test
    fun baseAndConfigSplitsAreToldApartByPayload() {
        val base = tempDir()
        base.touch("classes.dex")
        base.touch("AndroidManifest.xml")

        val config = tempDir()
        config.touch("lib/arm64-v8a/libil2cpp.so")

        assertTrue(GlbApkPatcher.isBaseSplit(base))
        assertFalse(GlbApkPatcher.isConfigSplit(base))
        assertTrue(GlbApkPatcher.isConfigSplit(config))
        assertFalse(GlbApkPatcher.isBaseSplit(config))
    }

    /**
     * The packer's Application is what deobfuscates `global-metadata.dat`, and the patched base
     * split never runs it — the restored copy has to replace the shipped one.
     */
    @Test
    fun basePatchWritesTheRestoredMetadataIntoTheAssetsTree() {
        val libsDir = tempDir()
        val baseDir = tempDir()
        val restored = ByteArray(32) { (it + 7).toByte() }
        libsDir.touch(GlbApkPatcher.METADATA_NAME, restored)
        baseDir.touch(GlbApkPatcher.METADATA_ARCHIVE_PATH, byteArrayOf(0)) // the obfuscated copy

        val logs = mutableListOf<String>()
        assertTrue(
            logs.joinToString("\n"),
            GlbApkPatcher.writeMetadata(baseDir, libsDir) { logs += it }
        )
        assertArrayEquals(restored, baseDir.resolve(GlbApkPatcher.METADATA_ARCHIVE_PATH).readBytes())
    }

    @Test
    fun basePatchRefusesToWriteWithoutTheRestoredMetadata() {
        val libsDir = tempDir()
        val baseDir = tempDir()

        val logs = mutableListOf<String>()
        assertFalse(GlbApkPatcher.writeMetadata(baseDir, libsDir) { logs += it })
        assertTrue(logs.any { it.contains(GlbApkPatcher.METADATA_NAME) })
        assertFalse(baseDir.resolve(GlbApkPatcher.METADATA_ARCHIVE_PATH).exists())
    }

    /**
     * A build that keeps its metadata somewhere else must still get the restored copy over the
     * file it loads; writing only to the documented path would silently do nothing there.
     */
    @Test
    fun basePatchReplacesTheShippedMetadataWhereverItLives() {
        val libsDir = tempDir()
        val baseDir = tempDir()
        val restored = byteArrayOf(9, 9, 9)
        libsDir.touch(GlbApkPatcher.METADATA_NAME, restored)
        val shipped = baseDir.touch(
            "assets/custom/Metadata/${GlbApkPatcher.METADATA_NAME}",
            byteArrayOf(1)
        )

        assertTrue(GlbApkPatcher.writeMetadata(baseDir, libsDir) {})
        assertArrayEquals(restored, shipped.readBytes())
        assertFalse(baseDir.resolve(GlbApkPatcher.METADATA_ARCHIVE_PATH).exists())
    }

    @Test
    fun packerStubIsRecognisedByShape() {
        assertTrue(GlbApkPatcher.isPackerStub("lib__4e06__.so"))
        assertTrue(GlbApkPatcher.isPackerStub("lib__abcdef0123456789__.so"))
        assertFalse(GlbApkPatcher.isPackerStub("libmain.so"))
        assertFalse(GlbApkPatcher.isPackerStub("libil2cpp.so"))
        assertFalse(GlbApkPatcher.isPackerStub("lib__stub.so"))
    }

    @Test
    fun configPatchRestoresIl2cppRemovesStubAndSwapsTheLoader() {
        val libsDir = tempDir()
        val configDir = tempDir()

        val restored = ByteArray(64) { it.toByte() }
        val runtime = ByteArray(32) { (it + 1).toByte() }
        libsDir.touch(GlbApkPatcher.GAME_LIB_NAME, restored)
        libsDir.touch(GlbApkPatcher.MOD_LIB_NAME, runtime)

        configDir.touch("lib/arm64-v8a/lib__4e06__.so")
        configDir.touch("lib/arm64-v8a/libil2cpp.so") // the encrypted stock copy
        configDir.touch("lib/arm64-v8a/libmain.so", byteArrayOf(7, 7, 7))
        configDir.touch("lib/arm64-v8a/libnative.so", byteArrayOf(5))

        val logs = mutableListOf<String>()
        val patched = GlbApkPatcher.patchConfigSplit(configDir, libsDir, emptyList()) { logs += it }
        val report = logs.joinToString("\n")

        assertTrue(report, patched)
        assertFalse("the packer stub must go", configDir.resolve("lib/arm64-v8a/lib__4e06__.so").exists())
        assertArrayEquals(restored, configDir.resolve("lib/arm64-v8a/libil2cpp.so").readBytes())
        assertArrayEquals(runtime, configDir.resolve("lib/arm64-v8a/libmain.so").readBytes())
        assertArrayEquals(
            "the stock loader is kept for the runtime to hand off to",
            byteArrayOf(7, 7, 7),
            configDir.resolve("lib/arm64-v8a/${GlbApkPatcher.ORIG_MOD_LIB_NAME}").readBytes()
        )
        // Untouched libraries stay as they shipped.
        assertArrayEquals(byteArrayOf(5), configDir.resolve("lib/arm64-v8a/libnative.so").readBytes())
    }

    @Test
    fun repatchKeepsTheFirstStockLoader() {
        val libsDir = tempDir()
        val configDir = tempDir()
        libsDir.touch(GlbApkPatcher.GAME_LIB_NAME, byteArrayOf(1))
        libsDir.touch(GlbApkPatcher.MOD_LIB_NAME, byteArrayOf(2))
        configDir.touch("lib/arm64-v8a/libmain.so", byteArrayOf(9))

        GlbApkPatcher.patchConfigSplit(configDir, libsDir, emptyList()) {}
        libsDir.touch(GlbApkPatcher.MOD_LIB_NAME, byteArrayOf(3))
        val patched = GlbApkPatcher.patchConfigSplit(configDir, libsDir, emptyList()) {}

        assertTrue(patched)
        assertArrayEquals(byteArrayOf(9), configDir.resolve("lib/arm64-v8a/${GlbApkPatcher.ORIG_MOD_LIB_NAME}").readBytes())
        assertArrayEquals(byteArrayOf(3), configDir.resolve("lib/arm64-v8a/libmain.so").readBytes())
    }

    @Test
    fun configPatchWritesPluginsUnderTheLoaderPrefix() {
        val libsDir = tempDir()
        val configDir = tempDir()
        libsDir.touch(GlbApkPatcher.GAME_LIB_NAME, byteArrayOf(1))
        libsDir.touch(GlbApkPatcher.MOD_LIB_NAME, byteArrayOf(2))
        configDir.touch("lib/arm64-v8a/libmain.so", byteArrayOf(0))

        val plugin = libsDir.touch("libspeed.so", byteArrayOf(9, 9))
        val logs = mutableListOf<String>()
        val patched = GlbApkPatcher.patchConfigSplit(
            configDir,
            libsDir,
            listOf("libhachimi_speed.so" to plugin)
        ) { logs += it }

        assertTrue(logs.joinToString("\n"), patched)
        assertArrayEquals(
            byteArrayOf(9, 9),
            configDir.resolve("lib/arm64-v8a/libhachimi_speed.so").readBytes()
        )
    }

    @Test
    fun configPatchFailsWhenTheRestoredIl2cppIsMissing() {
        val libsDir = tempDir()
        val configDir = tempDir()
        libsDir.touch(GlbApkPatcher.MOD_LIB_NAME)
        configDir.touch("lib/arm64-v8a/libmain.so")

        val logs = mutableListOf<String>()
        val patched = GlbApkPatcher.patchConfigSplit(configDir, libsDir, emptyList()) { logs += it }

        assertFalse(patched)
        assertTrue(logs.any { it.contains(GlbApkPatcher.GAME_LIB_NAME) })
    }
}
