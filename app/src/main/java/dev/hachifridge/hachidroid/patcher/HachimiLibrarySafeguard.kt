package dev.hachifridge.hachidroid.patcher

import android.content.Context
import android.net.Uri
import androidx.annotation.StringRes
import dev.hachifridge.hachidroid.R
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream

/**
 * Safeguards to ensure that a selected or patched native library (.so)
 * is indeed a valid Hachimi-Edge core mod library for arm64-v8a and NOT
 * a plugin, wrong-architecture binary, or unrelated game library (e.g. libunity, libil2cpp).
 *
 * Failures carry a string resource rather than a finished message, because the reasons are shown
 * to the user and belong in the reader's language. The validator itself stays free of a Context so
 * it remains unit-testable.
 */
object HachimiLibrarySafeguard {

    sealed class ValidationResult {
        data class Valid(val version: String) : ValidationResult()

        data class Error(
            @StringRes val messageRes: Int,
            val formatArgs: List<Any> = emptyList(),
            val isPluginOrOtherType: Boolean = false
        ) : ValidationResult() {
            fun message(context: Context): String = if (formatArgs.isEmpty()) {
                context.getString(messageRes)
            } else {
                context.getString(messageRes, *formatArgs.toTypedArray())
            }
        }
    }

    private val ELF_MAGIC = byteArrayOf(0x7F, 'E'.code.toByte(), 'L'.code.toByte(), 'F'.code.toByte())
    private const val ELF_CLASS_64: Byte = 2
    private const val ELF_DATA_LSB: Byte = 1
    private const val EM_AARCH64: Int = 183 // 0x00B7

    private val REJECTED_CORE_LIB_NAMES = setOf(
        "libunity.so",
        "libil2cpp.so",
        "libc++_shared.so",
        "libcri_ware_unity.so",
        "libvulkan.so",
        "libmain_orig.so"
    )

    /**
     * Inspects and validates a Hachimi mod library candidate.
     */
    fun validateModLibrary(inputStream: InputStream, candidateName: String = ""): ValidationResult {
        return try {
            BufferedInputStream(inputStream).use { stream ->
                // 1. Read ELF Header (64 bytes for ELF64)
                val header = ByteArray(64)
                val bytesRead = stream.read(header)
                if (bytesRead < 64) {
                    return ValidationResult.Error(R.string.lib_guard_too_small)
                }

                // 2. Validate ELF Magic
                for (i in 0 until 4) {
                    if (header[i] != ELF_MAGIC[i]) {
                        return ValidationResult.Error(R.string.lib_guard_not_elf)
                    }
                }

                // 3. Validate ELF Class (must be 64-bit)
                if (header[4] != ELF_CLASS_64) {
                    return ValidationResult.Error(R.string.lib_guard_not_64_bit)
                }

                // 4. Validate Byte Ordering (Little Endian)
                if (header[5] != ELF_DATA_LSB) {
                    return ValidationResult.Error(R.string.lib_guard_big_endian)
                }

                // 5. Validate Machine (e_machine at offset 18, 2 bytes little-endian)
                val machine = (header[18].toInt() and 0xFF) or ((header[19].toInt() and 0xFF) shl 8)
                if (machine != EM_AARCH64) {
                    return ValidationResult.Error(R.string.lib_guard_wrong_machine)
                }

                // 6. Check for disallowed stock game libraries
                val lowerName = candidateName.lowercase()
                if (REJECTED_CORE_LIB_NAMES.any { lowerName.endsWith(it) }) {
                    return ValidationResult.Error(
                        R.string.lib_guard_stock_library,
                        listOf(candidateName),
                        isPluginOrOtherType = true
                    )
                }

                // 7. Check for HACHIMI_VERSION signature
                val hachimiVersion = scanForHachimiMarker(stream)
                if (hachimiVersion != null) {
                    ValidationResult.Valid(version = hachimiVersion)
                } else {
                    ValidationResult.Error(R.string.lib_guard_no_signature, isPluginOrOtherType = true)
                }
            }
        } catch (e: Exception) {
            ValidationResult.Error(R.string.lib_guard_inspect_failed, listOf(reason(e)))
        }
    }

    /**
     * Inspects a plugin candidate before adding it.
     */
    fun validatePluginLibrary(inputStream: InputStream, fileName: String): ValidationResult {
        return try {
            BufferedInputStream(inputStream).use { stream ->
                val header = ByteArray(64)
                val bytesRead = stream.read(header)
                if (bytesRead < 64) {
                    return ValidationResult.Error(R.string.lib_guard_too_small)
                }

                for (i in 0 until 4) {
                    if (header[i] != ELF_MAGIC[i]) {
                        return ValidationResult.Error(R.string.plugin_guard_not_elf)
                    }
                }

                if (header[4] != ELF_CLASS_64) {
                    return ValidationResult.Error(R.string.plugin_guard_not_64_bit)
                }

                val machine = (header[18].toInt() and 0xFF) or ((header[19].toInt() and 0xFF) shl 8)
                if (machine != EM_AARCH64) {
                    return ValidationResult.Error(R.string.plugin_guard_wrong_machine)
                }

                val lower = fileName.lowercase()
                if (lower == "libmain.so" || lower == "libmain-arm64-v8a.so") {
                    return ValidationResult.Error(R.string.plugin_guard_reserved_name, listOf(fileName))
                }

                if (REJECTED_CORE_LIB_NAMES.contains(lower)) {
                    return ValidationResult.Error(R.string.plugin_guard_stock_name, listOf(fileName))
                }

                // Verify it doesn't accidentally contain the main Hachimi engine marker
                val hachimiMarker = scanForHachimiMarker(stream)
                if (hachimiMarker != null) {
                    return ValidationResult.Error(R.string.plugin_guard_is_engine, listOf(hachimiMarker))
                }

                ValidationResult.Valid(version = "plugin")
            }
        } catch (e: Exception) {
            ValidationResult.Error(R.string.plugin_guard_failed, listOf(reason(e)))
        }
    }

    /** Exception messages are often null; fall back to the type so the sentence still reads. */
    private fun reason(e: Exception): String = e.message ?: e.javaClass.simpleName

    private fun scanForHachimiMarker(stream: BufferedInputStream): String? {
        val target = "HACHIMI_VERSION:".toByteArray(Charsets.US_ASCII)
        var matchIndex = 0
        var b: Int = -1

        // Scan up to 16MB for the marker to prevent unbounded processing
        var scanned = 0
        val maxScan = 16 * 1024 * 1024

        while (scanned < maxScan && stream.read().also { b = it } != -1) {
            scanned++
            if (b.toByte() == target[matchIndex]) {
                matchIndex++
                if (matchIndex == target.size) {
                    val sb = StringBuilder()
                    for (i in 0 until 64) {
                        val next = stream.read()
                        if (next == -1 || next == 0 || next < 32 || next > 126) break
                        sb.append(next.toChar())
                    }
                    val regex = Regex("^(v[0-9]+\\.[0-9]+\\.[0-9]+(?:-[a-fA-F0-9]{7,12})?(?:-dirty)?)")
                    val match = regex.find(sb.toString())
                    if (match != null) return match.groupValues[1]
                    matchIndex = 0
                }
            } else {
                matchIndex = if (b.toByte() == target[0]) 1 else 0
            }
        }
        return null
    }

    /**
     * Validates a file directly on disk.
     */
    fun validateModLibraryFile(file: File): ValidationResult {
        if (!file.exists() || !file.isFile) {
            return ValidationResult.Error(R.string.lib_guard_file_missing, listOf(file.path))
        }
        return file.inputStream().use { validateModLibrary(it, file.name) }
    }

    /**
     * Validates a file from a content URI.
     */
    fun validateModLibraryUri(context: Context, uri: Uri, name: String): ValidationResult {
        return try {
            context.contentResolver.openInputStream(uri)?.use {
                validateModLibrary(it, name)
            } ?: ValidationResult.Error(R.string.lib_guard_unreadable, listOf(uri.toString()))
        } catch (e: Exception) {
            ValidationResult.Error(R.string.lib_guard_read_failed, listOf(uri.toString(), reason(e)))
        }
    }

    /**
     * Validates a plugin candidate from a content URI.
     */
    fun validatePluginUri(context: Context, uri: Uri, name: String): ValidationResult {
        return try {
            context.contentResolver.openInputStream(uri)?.use {
                validatePluginLibrary(it, name)
            } ?: ValidationResult.Error(R.string.lib_guard_unreadable, listOf(uri.toString()))
        } catch (e: Exception) {
            ValidationResult.Error(R.string.lib_guard_read_failed, listOf(uri.toString(), reason(e)))
        }
    }
}
