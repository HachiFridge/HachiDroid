package dev.hachifridge.hachidroid.client

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the archive-shape probes that the studio and the patcher both rely on.
 *
 * An XAPK is the case worth covering: it carries the split APKs instead of their contents, so its
 * manifest and packer stub are one archive deeper, and a probe that only reads the outer entries
 * reports a Global XAPK as a plain build.
 */
class ShellDetectionTest {

    private fun zip(entries: List<Pair<String, ByteArray>>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { stream ->
            entries.forEach { (name, bytes) ->
                stream.putNextEntry(ZipEntry(name))
                stream.write(bytes)
                stream.closeEntry()
            }
        }
        return out.toByteArray()
    }

    @Test
    fun aStubInsideAnXapkStillMarksTheBuildPacked() {
        val inner = zip(listOf("lib/arm64-v8a/lib__4e06__.so" to byteArrayOf(1)))
        val outer = zip(
            listOf(
                "base.apk" to inner,
                "config.arm64_v8a.apk" to zip(listOf("AndroidManifest.xml" to byteArrayOf(1)))
            )
        )

        val info = ShellDetection.inspectStream(ByteArrayInputStream(outer))

        assertTrue(info.packerStub)
        assertTrue(info.protected)
    }

    @Test
    fun aNestedArchiveWithoutTheStubIsNotPacked() {
        val outer = zip(listOf("base.apk" to zip(listOf("classes.dex" to byteArrayOf(1)))))

        val info = ShellDetection.inspectStream(ByteArrayInputStream(outer))

        assertFalse(info.packerStub)
        assertFalse(info.protected)
        // No readable manifest in the fixture, so there is nothing to name or version.
        assertTrue(info.packageName == null)
        assertTrue(info.versionStamp == null)
    }

    @Test
    fun aPlainArchiveWithNoNestedApkIsNotPacked() {
        val outer = zip(listOf("classes.dex" to byteArrayOf(1)))

        val info = ShellDetection.inspectStream(ByteArrayInputStream(outer))

        assertFalse(info.protected)
    }
}
