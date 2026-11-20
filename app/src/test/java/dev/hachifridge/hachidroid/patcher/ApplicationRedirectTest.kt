package dev.hachifridge.hachidroid.patcher

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApplicationRedirectTest {

    private fun tempDir(): File = Files.createTempDirectory("application-redirect").toFile()

    private fun File.touch(path: String, bytes: ByteArray = byteArrayOf(1, 2, 3)): File {
        val file = resolve(path)
        file.parentFile?.mkdirs()
        file.writeBytes(bytes)
        return file
    }

    @Test
    fun packerStubMatchesOnlyTheStubShape() {
        assertTrue(ApplicationRedirect.isPackerStub("lib__4e06__.so"))
        assertTrue(ApplicationRedirect.isPackerStub("lib__abcdef0123456789__.so"))
        assertFalse(ApplicationRedirect.isPackerStub("lib__stub.so"))
        assertFalse(ApplicationRedirect.isPackerStub("libil2cpp.so"))
        assertFalse(ApplicationRedirect.isPackerStub("libmain.so"))
    }

    /**
     * A merged APK has the base and the config payloads in one tree. Dropping all of `lib/` there
     * would delete the libraries the config patch is about to write, so only stubs may go.
     */
    @Test
    fun stubRemovalKeepsTheGameLibrariesOfAMergedApk() {
        val dir = tempDir()
        dir.touch("lib/arm64-v8a/lib__4e06__.so")
        dir.touch("lib/arm64-v8a/libil2cpp.so")
        dir.touch("lib/arm64-v8a/libmain.so")
        dir.touch("lib/arm64-v8a/libunity.so")

        val removed = ApplicationRedirect.removeShellStubs(dir)

        assertEquals(listOf("lib__4e06__.so"), removed)
        assertFalse(dir.resolve("lib/arm64-v8a/lib__4e06__.so").exists())
        listOf("libil2cpp.so", "libmain.so", "libunity.so").forEach { name ->
            assertTrue("$name must survive", dir.resolve("lib/arm64-v8a/$name").isFile)
        }
    }

    @Test
    fun stubRemovalPrunesTheDirectoriesItEmpties() {
        val dir = tempDir()
        dir.touch("lib/arm64-v8a/lib__1b40__.so")

        assertEquals(listOf("lib__1b40__.so"), ApplicationRedirect.removeShellStubs(dir))
        assertFalse("an emptied lib/ tree must not stay in the archive", dir.resolve("lib").exists())
    }

    @Test
    fun stubRemovalIsANoOpWithoutALibDirectory() {
        val dir = tempDir()
        dir.touch("classes.dex")

        assertTrue(ApplicationRedirect.removeShellStubs(dir).isEmpty())
        assertTrue(dir.resolve("classes.dex").isFile)
    }
}
