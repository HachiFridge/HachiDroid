package dev.hachifridge.hachidroid.core

import android.system.Os
import android.system.OsConstants
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Reads a native library's ELF program headers and reports how its loadable segments are aligned.
 *
 * Android 15 and later can run with 16 KB memory pages. A library whose `PT_LOAD` segments are
 * aligned to 4096 cannot be mapped on such a device: the loader rejects the `.so` outright, which
 * only surfaces as "failed to load library" once the patched game is installed and launched.
 * Inspecting the file is the only way to catch that before the APK is built.
 */
object NativeLibAlignment {

    /** Android 15's 16 KB page size; every loadable segment must be aligned to at least this. */
    const val PAGE_SIZE_16_KB = 16_384L

    /** 64 KB is sufficient to cover ELF and program header tables in virtually all shared libraries. */
    const val HEADER_PROBE_BYTES = 64 * 1024

    /** Physical memory page size on this device (4096 or 16384). */
    val devicePageSize: Long by lazy {
        runCatching {
            Os.sysconf(OsConstants._SC_PAGESIZE)
        }.getOrDefault(4096L)
    }

    /** True when this device's kernel uses 16 KB memory pages. */
    val isDevice16KbPage: Boolean
        get() = devicePageSize >= PAGE_SIZE_16_KB

    private const val ELF_CLASS_32 = 1
    private const val ELF_CLASS_64 = 2
    private const val ELF_DATA_MSB = 2
    private const val PT_LOAD = 1

    /** ELF64 has a 64-byte header and 56-byte program headers; ELF32 has 52 and 32. */
    private const val HEADER_SIZE_64 = 64
    private const val HEADER_SIZE_32 = 52
    private const val PROGRAM_HEADER_SIZE_64 = 56
    private const val PROGRAM_HEADER_SIZE_32 = 32

    /** Bounds a corrupt header, which could otherwise claim a table of millions of entries. */
    private const val MAX_PROGRAM_HEADERS = 512

    data class Result(
        /** Largest `p_align` across the loadable segments, in bytes. */
        val maxLoadAlignment: Long,
        /** True when the largest loadable alignment reaches [PAGE_SIZE_16_KB]. */
        val is16KbCompatible: Boolean,
        val is64Bit: Boolean
    )

    /** Returns null when [file] is missing, unreadable, or not a well-formed ELF. */
    fun inspect(file: File): Result? =
        runCatching {
            val probe = ByteArray(HEADER_PROBE_BYTES)
            file.inputStream().use { stream ->
                var totalRead = 0
                while (totalRead < probe.size) {
                    val read = stream.read(probe, totalRead, probe.size - totalRead)
                    if (read == -1) break
                    totalRead += read
                }
                if (totalRead < HEADER_SIZE_32) return null
                val probeBytes = if (totalRead == probe.size) probe else probe.copyOf(totalRead)
                inspect(probeBytes) ?: run {
                    // Fallback for uncommon ELFs with program header tables placed beyond the first 64 KB
                    if (file.length() <= 10L * 1024 * 1024) {
                        inspect(file.readBytes())
                    } else null
                }
            }
        }.getOrNull()

    /** Inspects the program headers from the beginning of an [InputStream]. */
    fun inspect(stream: InputStream): Result? =
        runCatching {
            val probe = ByteArray(HEADER_PROBE_BYTES)
            var totalRead = 0
            while (totalRead < probe.size) {
                val read = stream.read(probe, totalRead, probe.size - totalRead)
                if (read == -1) break
                totalRead += read
            }
            if (totalRead < HEADER_SIZE_32) return null
            inspect(if (totalRead == probe.size) probe else probe.copyOf(totalRead))
        }.getOrNull()

    fun inspect(bytes: ByteArray): Result? {
        if (bytes.size < HEADER_SIZE_32) return null
        if (bytes[0] != 0x7F.toByte() ||
            bytes[1] != 'E'.code.toByte() ||
            bytes[2] != 'L'.code.toByte() ||
            bytes[3] != 'F'.code.toByte()
        ) {
            return null
        }

        val is64Bit = when (bytes[4].toInt()) {
            ELF_CLASS_32 -> false
            ELF_CLASS_64 -> true
            else -> return null
        }
        val byteOrder = when (bytes[5].toInt()) {
            1 -> ByteOrder.LITTLE_ENDIAN
            ELF_DATA_MSB -> ByteOrder.BIG_ENDIAN
            else -> return null
        }
        if (bytes.size < if (is64Bit) HEADER_SIZE_64 else HEADER_SIZE_32) return null

        val buffer = ByteBuffer.wrap(bytes).order(byteOrder)
        val tableOffset = if (is64Bit) {
            buffer.getLong(0x20)
        } else {
            buffer.getInt(0x1C).toLong() and 0xFFFF_FFFFL
        }
        val entrySize = buffer.getShort(if (is64Bit) 0x36 else 0x2A).toInt() and 0xFFFF
        val entryCount = buffer.getShort(if (is64Bit) 0x38 else 0x2C).toInt() and 0xFFFF

        val minimumEntrySize = if (is64Bit) PROGRAM_HEADER_SIZE_64 else PROGRAM_HEADER_SIZE_32
        if (entryCount == 0 || entryCount > MAX_PROGRAM_HEADERS) return null
        if (entrySize < minimumEntrySize) return null
        if (tableOffset < 0 || tableOffset > bytes.size.toLong()) return null
        if (tableOffset + entrySize.toLong() * entryCount > bytes.size) return null

        val alignmentOffset = if (is64Bit) 0x30 else 0x1C
        var maxAlignment = 0L
        for (index in 0 until entryCount) {
            val base = (tableOffset + index.toLong() * entrySize).toInt()
            if (buffer.getInt(base) != PT_LOAD) continue
            val alignment = if (is64Bit) {
                buffer.getLong(base + alignmentOffset)
            } else {
                buffer.getInt(base + alignmentOffset).toLong() and 0xFFFF_FFFFL
            }
            if (alignment > maxAlignment) maxAlignment = alignment
        }

        // No loadable segment means nothing can be mapped, so this is not a runnable library.
        if (maxAlignment == 0L) return null

        return Result(
            maxLoadAlignment = maxAlignment,
            is16KbCompatible = maxAlignment >= PAGE_SIZE_16_KB,
            is64Bit = is64Bit
        )
    }
}
