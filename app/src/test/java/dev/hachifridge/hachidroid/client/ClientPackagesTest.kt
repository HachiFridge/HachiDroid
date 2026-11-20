package dev.hachifridge.hachidroid.client

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the package → client split, and the build conditions that decide the patch flow.
 *
 * All UM:PD releases are one client now; what differs is the build. Global is packed, Japan and
 * the Taiwanese/Korean releases are not, and that difference is what the library checklist and the
 * split-set flow key off.
 */
class ClientPackagesTest {

    private fun ownersOf(packageName: String): List<Client> =
        Client.entries.filter { packageName in it.packageNames }

    @Test
    fun everyReleaseBelongsToTheOneTracenClient() {
        listOf(
            "jp.co.cygames.umamusume",
            "com.cygames.umamusume",
            "com.komoe.kmumamusumegp",
            "com.komoe.umamusumeofficial",
            "com.kakaogames.umamusume"
        ).forEach { packageName ->
            assertEquals(packageName, listOf(Client.TRACEN), ownersOf(packageName))
        }
    }

    @Test
    fun komoePackagesAreTaiwaneseNotGlobal() {
        listOf("com.komoe.kmumamusumegp", "com.komoe.umamusumeofficial").forEach { packageName ->
            assertTrue(packageName, ShellDetection.isPackedPackage(packageName).not())
        }
    }

    @Test
    fun noPackageIsClaimedByTwoClients() {
        val packages = Client.entries.flatMap { it.packageNames }
        assertEquals(packages.size, packages.distinct().size)
    }

    @Test
    fun onlyGlobalIsKnownToShipThePacker() {
        assertTrue(ShellDetection.isPackedPackage("com.cygames.umamusume"))
        assertFalse(ShellDetection.isPackedPackage("jp.co.cygames.umamusume"))
        assertFalse(ShellDetection.isPackedPackage("com.kakaogames.umamusume"))
        // Priconne is packed in every build, which is why the client is always shell-protected.
        assertTrue(ShellDetection.isPackedPackage("jp.co.cygames.princessconnectredive"))
    }

    @Test
    fun theInstalledCopyDecidesTheBuildOnlyWhenNothingIsPicked() {
        assertTrue(ShellDetection.installedTargetIsPacked(false, "com.cygames.umamusume"))
        assertFalse(ShellDetection.installedTargetIsPacked(false, "jp.co.cygames.umamusume"))
        assertFalse(ShellDetection.installedTargetIsPacked(false, null))
        // Global still installed while Japan's splits are picked: the picks are the build, so the
        // run must not ask for Global's restored libil2cpp.so and global-metadata.dat.
        assertFalse(ShellDetection.installedTargetIsPacked(true, "com.cygames.umamusume"))
    }

    @Test
    fun aPackedBuildsRestoredArtifactsAreNeverStoredForAPlainOne() {
        // Japan is the plain build: Global's restored image and deobfuscated metadata must not be
        // stored while it is the target, or the next Global patch would write Japan's files into a
        // build that assumed its own. One folder serves both releases, so the build decides.
        assertFalse(TracenLibraries.accepts("libil2cpp.so", shellProtected = false))
        assertFalse(TracenLibraries.accepts("global-metadata.dat", shellProtected = false))
        assertTrue(TracenLibraries.accepts("libil2cpp.so", shellProtected = true))
        assertTrue(TracenLibraries.accepts("global-metadata.dat", shellProtected = true))
        // The mod runtime is not a restored artifact, and unknown names are never stored.
        assertTrue(TracenLibraries.accepts("libmain-arm64-v8a.so", shellProtected = false))
        assertFalse(TracenLibraries.accepts("libsomethingelse.so", shellProtected = true))
        // Priconne ships packed in every build, and its images are not packed-only names.
        assertTrue(AstraeaLibraries.accepts("libil2cpp.so", shellProtected = false))
    }

    @Test
    fun tracenAsksForTheRestoredLibraryOnlyWhenTheBuildIsPacked() {
        assertEquals(listOf("libmain-arm64-v8a.so"), TracenLibraries.requiredLibraries(false))
        assertEquals(
            listOf("libil2cpp.so", "global-metadata.dat", "libmain-arm64-v8a.so"),
            TracenLibraries.requiredLibraries(true)
        )
        assertEquals(emptyList<String>(), TracenLibraries.gameLibrariesFor(false))
        assertEquals(listOf("global-metadata.dat"), TracenLibraries.protectedGameAssets)
    }

    @Test
    fun astraeaAlwaysRequiresBothRestoredLibraries() {
        val required = AstraeaLibraries.requiredLibraries(shellProtected = true)
        assertEquals(listOf("libil2cpp.so", "libnative.so", "libdaringtact.so"), required)
        assertTrue(Client.ASTRAEA.alwaysShellProtected)
        assertFalse(Client.TRACEN.alwaysShellProtected)
    }

    @Test
    fun packerStubNamesAreRecognised() {
        assertTrue(ShellDetection.isPackerStub("lib__4e06__.so"))
        assertFalse(ShellDetection.isPackerStub("libmain.so"))
        assertFalse(ShellDetection.isPackerStub("libil2cpp.so"))
        assertFalse(ShellDetection.isPackerStub("libsigner.so"))
    }
}
