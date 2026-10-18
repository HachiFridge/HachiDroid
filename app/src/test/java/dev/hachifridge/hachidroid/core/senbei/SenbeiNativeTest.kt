package dev.hachifridge.hachidroid.core.senbei

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The bridge writes snake_case JSON in Rust; these tests pin the field mapping so a rename on
 * either side fails here instead of silently reporting zeroes on a device.
 */
class SenbeiNativeTest {

    @Test
    fun readsASuccessfulRestoreReport() {
        val report = SenbeiNative.parseReport(
            """
            {
              "ok": true,
              "output_bytes": 212459976,
              "elapsed_ms": 3498,
              "sections": 28,
              "segments": 4,
              "dynamic_symbols": 2978,
              "dynamic_relocations": 1092928,
              "private_payload_section": false
            }
            """.trimIndent()
        )

        assertEquals(true, report?.ok)
        assertEquals(212459976L, report?.outputBytes)
        assertEquals(3498L, report?.elapsedMs)
        assertEquals(28, report?.sections)
        assertEquals(4, report?.segments)
        assertEquals(2978, report?.dynamicSymbols)
        assertEquals(1092928, report?.dynamicRelocations)
        assertFalse(report?.privatePayloadSection ?: true)
    }

    @Test
    fun readsAFailureReport() {
        val report = SenbeiNative.parseReport("""{"ok":false,"error":"input is not a file"}""")

        assertEquals(false, report?.ok)
        assertEquals("input is not a file", report?.error)
        // A failure carries none of the success fields; defaults must not look like a real restore.
        assertEquals(0L, report?.outputBytes)
        assertEquals(0, report?.sections)
    }

    @Test
    fun toleratesFieldsTheEngineMayAddLater() {
        val report = SenbeiNative.parseReport(
            """{"ok":true,"sections":3,"segments":2,"future_counter":7,"nested":{"a":1}}"""
        )

        assertTrue(report?.ok ?: false)
        assertEquals(3, report?.sections)
        assertEquals(2, report?.segments)
    }

    @Test
    fun rejectsUnreadableInput() {
        assertNull(SenbeiNative.parseReport("not json at all"))
    }
}
