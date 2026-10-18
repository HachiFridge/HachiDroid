package dev.hachifridge.hachidroid.core

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeLibAlignmentTest {

    @Test
    fun sixteenKilobyteLoadSegmentsAreCompatible() {
        val result = NativeLibAlignment.inspect(elf64(PAGE_16K, PAGE_16K))

        assertEquals(16_384L, result?.maxLoadAlignment)
        assertTrue(result?.is16KbCompatible == true)
        assertTrue(result?.is64Bit == true)
    }

    @Test
    fun fourKilobyteLoadSegmentsAreFlagged() {
        // The real failure mode: a library built by an older NDK on stock 4 KB alignment.
        val result = NativeLibAlignment.inspect(elf64(4096L, 4096L))

        assertEquals(4096L, result?.maxLoadAlignment)
        assertTrue(result?.is16KbCompatible == false)
    }

    @Test
    fun onlyLoadableSegmentsDecideTheVerdict() {
        // A PT_NOTE aligned to 16 KB must not rescue 4 KB loadable segments.
        val result = NativeLibAlignment.inspect(elf64(4096L, 4096L, extraType = PT_NOTE, extraAlign = PAGE_16K))

        assertEquals(4096L, result?.maxLoadAlignment)
        assertTrue(result?.is16KbCompatible == false)
    }

    @Test
    fun libraryWithoutLoadSegmentsIsNotRunnable() {
        assertNull(NativeLibAlignment.inspect(elf64(extraType = PT_NOTE, extraAlign = PAGE_16K)))
    }

    @Test
    fun thirtyTwoBitLibrariesAreReadToo() {
        val result = NativeLibAlignment.inspect(elf32(PAGE_16K))

        assertEquals(16_384L, result?.maxLoadAlignment)
        assertTrue(result?.is64Bit == false)
    }

    @Test
    fun nonElfDataIsRejected() {
        assertNull(NativeLibAlignment.inspect(ByteArray(128) { 0x41 }))
        assertNull(NativeLibAlignment.inspect(ByteArray(0)))
        assertNull(NativeLibAlignment.inspect(ByteArray(16)))
    }

    @Test
    fun truncatedProgramHeaderTableIsRejected() {
        val truncated = elf64(PAGE_16K).copyOf(80)

        assertNull(NativeLibAlignment.inspect(truncated))
    }

    @Test
    fun filesAreReadFromDisk() {
        val file = File.createTempFile("libprobe", ".so")
        try {
            file.writeBytes(elf64(PAGE_16K))
            assertEquals(16_384L, NativeLibAlignment.inspect(file)?.maxLoadAlignment)
        } finally {
            file.delete()
        }
    }

    private fun elf64(vararg alignments: Long, extraType: Int? = null, extraAlign: Long = 0L): ByteArray {
        val entries = alignments.toMutableList()
        val types = MutableList(alignments.size) { PT_LOAD }
        if (extraType != null) {
            entries += extraAlign
            types += extraType
        }
        val buffer = ByteBuffer.allocate(HEADER_64 + PROGRAM_HEADER_64 * entries.size)
            .order(ByteOrder.LITTLE_ENDIAN)
        writeIdent(buffer, is64Bit = true)
        buffer.putLong(0x20, HEADER_64.toLong())
        buffer.putShort(0x36, PROGRAM_HEADER_64.toShort())
        buffer.putShort(0x38, entries.size.toShort())
        entries.forEachIndexed { index, align ->
            val base = HEADER_64 + index * PROGRAM_HEADER_64
            buffer.putInt(base, types[index])
            buffer.putLong(base + 0x30, align)
        }
        return buffer.array()
    }

    private fun elf32(vararg alignments: Long): ByteArray {
        val buffer = ByteBuffer.allocate(HEADER_32 + PROGRAM_HEADER_32 * alignments.size)
            .order(ByteOrder.LITTLE_ENDIAN)
        writeIdent(buffer, is64Bit = false)
        buffer.putInt(0x1C, HEADER_32)
        buffer.putShort(0x2A, PROGRAM_HEADER_32.toShort())
        buffer.putShort(0x2C, alignments.size.toShort())
        alignments.forEachIndexed { index, align ->
            val base = HEADER_32 + index * PROGRAM_HEADER_32
            buffer.putInt(base, PT_LOAD)
            buffer.putInt(base + 0x1C, align.toInt())
        }
        return buffer.array()
    }

    private fun writeIdent(buffer: ByteBuffer, is64Bit: Boolean) {
        buffer.put(0, 0x7F.toByte())
        buffer.put(1, 'E'.code.toByte())
        buffer.put(2, 'L'.code.toByte())
        buffer.put(3, 'F'.code.toByte())
        buffer.put(4, (if (is64Bit) 2 else 1).toByte())
        buffer.put(5, 1)
        buffer.put(6, 1)
    }

    private companion object {
        const val PAGE_16K = 16_384L
        const val PT_LOAD = 1
        const val PT_NOTE = 4
        const val HEADER_64 = 64
        const val HEADER_32 = 52
        const val PROGRAM_HEADER_64 = 56
        const val PROGRAM_HEADER_32 = 32
    }
}
