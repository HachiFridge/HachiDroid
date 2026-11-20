package dev.hachifridge.hachidroid.client.tracen

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TracenLibrarySafeguardTest {

    private val hachimiBinary = File("/home/hachifridge/Desktop/personal-repos/Hachimi-Edge/target/aarch64-linux-android/release/libhachimi.so")
    private val gallopPlugin = File("/home/hachifridge/Desktop/ChimiPlugins/libgallop.so")
    private val skillHelperPlugin = File("/home/hachifridge/Desktop/ChimiPlugins/libskill_helper.so")

    @Test
    fun testValidHachimiBinary() {
        if (!hachimiBinary.exists()) return
        val res = TracenLibrarySafeguard.validateModLibraryFile(hachimiBinary)
        assertTrue("Expected valid Hachimi library, got: $res", res is TracenLibrarySafeguard.ValidationResult.Valid)
        val valid = res as TracenLibrarySafeguard.ValidationResult.Valid
        assertTrue(valid.version.startsWith("v0.30.0"))
    }

    @Test
    fun testRejectPluginAsModLibrary() {
        if (!gallopPlugin.exists()) return
        val res = TracenLibrarySafeguard.validateModLibraryFile(gallopPlugin)
        assertTrue("Expected plugin to be rejected as mod library, got: $res", res is TracenLibrarySafeguard.ValidationResult.Error)
        val err = res as TracenLibrarySafeguard.ValidationResult.Error
        assertTrue(err.isPluginOrOtherType)
    }

    @Test
    fun testValidPluginImport() {
        if (!gallopPlugin.exists()) return
        val res = gallopPlugin.inputStream().use {
            TracenLibrarySafeguard.validatePluginLibrary(it, gallopPlugin.name)
        }
        assertTrue("Expected gallop to be valid plugin, got: $res", res is TracenLibrarySafeguard.ValidationResult.Valid)
    }

    @Test
    fun testRejectHachimiAsPlugin() {
        if (!hachimiBinary.exists()) return
        val res = hachimiBinary.inputStream().use {
            TracenLibrarySafeguard.validatePluginLibrary(it, "libhachimi.so")
        }
        assertTrue("Expected Hachimi to be rejected when imported as plugin, got: $res", res is TracenLibrarySafeguard.ValidationResult.Error)
    }

    @Test
    fun testRejectReservedCoreLibAsPlugin() {
        if (!skillHelperPlugin.exists()) return
        val res = skillHelperPlugin.inputStream().use {
            TracenLibrarySafeguard.validatePluginLibrary(it, "libmain.so")
        }
        assertTrue("Expected libmain.so name to be rejected as plugin, got: $res", res is TracenLibrarySafeguard.ValidationResult.Error)
    }
}
