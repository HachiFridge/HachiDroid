package dev.hachifridge.hachidroid.core.senbei

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

/**
 * Binding to the bundled `libsenbei_jni.so`.
 *
 * The library is built from `app/src/main/rust/senbei-jni` and only ships for `arm64-v8a`, so on
 * any other ABI loading fails. That is not an error worth crashing over: [isAvailable] reports it
 * once, and the UI simply does not offer on-device unpacking. Everything else in the app still works
 * with libraries the user supplies directly.
 */
internal object SenbeiNative {

    private val gson = Gson()

    private val loadResult: Result<Unit> = runCatching { System.loadLibrary("senbei_jni") }

    /** False on unsupported ABIs and in builds where the native library was not packaged. */
    val isAvailable: Boolean get() = loadResult.isSuccess

    fun isProtectedLibrary(path: String): Boolean =
        isAvailable && runCatching { nativeIsProtectedLibrary(path) }.getOrDefault(false)

    /** Unpacks [input] into [output], using [workDir] for intermediates. */
    fun unpackLibrary(input: String, output: String, workDir: String): UnpackReport {
        if (!isAvailable) return UnpackReport(ok = false, error = "senbei_jni is not loaded")
        val json = runCatching { nativeUnpackLibrary(input, output, workDir) }.getOrNull()
            ?: return UnpackReport(ok = false, error = "the native unpack returned no report")
        return parseReport(json)
            ?: UnpackReport(ok = false, error = "the native unpack returned an unreadable report")
    }

    /**
     * Decodes the bridge's JSON. Split out from [unpackLibrary] so the field contract can be
     * tested on a JVM without loading the native library.
     */
    internal fun parseReport(json: String): UnpackReport? =
        runCatching { gson.fromJson(json, UnpackReport::class.java) }.getOrNull()

    /**
     * The native side answers with JSON rather than exceptions, so the failure reaches the UI as
     * data instead of a link-time crash. Unknown fields are harmless: Gson ignores them, which
     * keeps this binding valid when the engine grows its report.
     */
    data class UnpackReport(
        val ok: Boolean = false,
        val error: String? = null,
        @SerializedName("output_bytes") val outputBytes: Long = 0L,
        @SerializedName("elapsed_ms") val elapsedMs: Long = 0L,
        val sections: Int = 0,
        val segments: Int = 0,
        @SerializedName("dynamic_symbols") val dynamicSymbols: Int = 0,
        @SerializedName("dynamic_relocations") val dynamicRelocations: Int = 0,
        @SerializedName("private_payload_section") val privatePayloadSection: Boolean = false
    )

    private external fun nativeIsProtectedLibrary(path: String): Boolean

    private external fun nativeUnpackLibrary(input: String, output: String, workDir: String): String
}
