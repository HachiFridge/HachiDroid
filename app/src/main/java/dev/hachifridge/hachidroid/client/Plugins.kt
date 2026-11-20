package dev.hachifridge.hachidroid.client

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dev.hachifridge.hachidroid.R
import dev.hachifridge.hachidroid.core.NativeLibAlignment
import java.io.File

/** One plugin library kept in a client's plugin folder. */
data class PluginEntry(
    val name: String,
    val fileName: String,
    val enabled: Boolean = true
)

/**
 * A client's plugin folder and manifest.
 *
 * Both clients follow the same workflow — pick a `.so`, keep it in app storage, toggle it, and let
 * the patch copy the enabled ones next to the mod library — so the handling lives here once. What
 * differs is only where the files live and how the patch names them: Tracen loads
 * `libhachimi_*.so`, DaringTact discovers `libdaringtact_*.so`.
 */
class PluginStore(
    val directoryName: String,
    /** The mod runtime prefixes the libraries it discovers with this. */
    val prefix: String,
    /** Core names this client's own libraries use; a plugin must not shadow one. */
    val reservedNames: Set<String>
) {
    private val gson = Gson()
    private val listType = object : TypeToken<List<PluginEntry>>() {}.type

    fun directory(context: Context): File = context.filesDir.resolve(directoryName)

    private fun manifestFile(context: Context): File =
        directory(context).resolve(MANIFEST_FILE)

    fun listPlugins(context: Context): MutableList<PluginEntry> {
        val manifest = manifestFile(context)
        if (!manifest.isFile) return mutableListOf()
        return try {
            val text = manifest.readText()
            gson.fromJson<List<PluginEntry>>(text, listType)?.toMutableList() ?: mutableListOf()
        } catch (_: Exception) {
            mutableListOf()
        }
    }

    fun savePlugins(context: Context, plugins: List<PluginEntry>) {
        val dir = directory(context)
        if (!dir.exists()) dir.mkdirs()
        manifestFile(context).writeText(gson.toJson(plugins))
    }

    fun addPlugin(context: Context, uri: Uri): PluginEntry? {
        val doc = DocumentFile.fromSingleUri(context, uri) ?: return null
        // The provider's display name is user-controlled; a separator in it would write outside
        // the plugin folder, so only the file name itself is kept.
        val displayName = (doc.name ?: "plugin.so").substringAfterLast('/').substringAfterLast('\\')
            .ifEmpty { "plugin.so" }
        val safeName = ensureSoSuffix(displayName)
        val dir = directory(context)
        if (!dir.exists()) dir.mkdirs()

        val targetName = uniqueName(dir, safeName)
        val targetFile = dir.resolve(targetName)

        context.contentResolver.openInputStream(uri)?.use { input ->
            targetFile.outputStream().use { output -> input.copyTo(output) }
        } ?: return null

        val entry = PluginEntry(
            name = targetName.removeSuffix(".so"),
            fileName = targetName,
            enabled = true
        )
        val plugins = listPlugins(context).apply { add(entry) }
        savePlugins(context, plugins)
        return entry
    }

    fun setEnabled(context: Context, fileName: String, enabled: Boolean) {
        val updated = listPlugins(context)
            .map { if (it.fileName == fileName) it.copy(enabled = enabled) else it }
        savePlugins(context, updated)
    }

    fun removePlugin(context: Context, fileName: String) {
        val plugins = listPlugins(context).filterNot { it.fileName == fileName }
        directory(context).resolve(fileName).delete()
        savePlugins(context, plugins)
    }

    fun enabledPluginFiles(context: Context): List<File> {
        val dir = directory(context)
        return listPlugins(context)
            .filter { it.enabled }
            .map { dir.resolve(it.fileName) }
            .filter { it.isFile }
    }

    /** The archive name the patch writes; the mod runtime's discovery relies on this prefix. */
    fun prefixedName(fileName: String): String {
        if (fileName.startsWith(prefix)) return fileName
        val base = fileName.removePrefix("lib")
        return prefix + base
    }

    /**
     * Rejects a picked file that is not an arm64 shared object, or that would shadow one of the
     * client's own libraries. Returns the message to show, or null when the file is usable.
     */
    fun validationError(context: Context, uri: Uri, name: String): String? {
        val header = try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val probe = ByteArray(NativeLibAlignment.HEADER_PROBE_BYTES)
                var read = 0
                while (read < probe.size) {
                    val count = stream.read(probe, read, probe.size - read)
                    if (count <= 0) break
                    read += count
                }
                probe.copyOf(read)
            } ?: return context.getString(R.string.lib_guard_unreadable, uri.toString())
        } catch (ex: Exception) {
            return context.getString(
                R.string.lib_guard_read_failed,
                uri.toString(),
                ex.message ?: ex.javaClass.simpleName
            )
        }

        if (header.size < 64) return context.getString(R.string.lib_guard_too_small)

        val isElf = header[0] == 0x7F.toByte() &&
            header[1] == 'E'.code.toByte() &&
            header[2] == 'L'.code.toByte() &&
            header[3] == 'F'.code.toByte()
        if (!isElf) return context.getString(R.string.plugin_guard_not_elf)

        if (header[4] != 2.toByte()) return context.getString(R.string.plugin_guard_not_64_bit)

        val machine = (header[18].toInt() and 0xFF) or ((header[19].toInt() and 0xFF) shl 8)
        if (machine != 183) return context.getString(R.string.plugin_guard_wrong_machine)

        val lower = name.lowercase()
        if (lower in reservedNames) {
            return context.getString(R.string.plugin_guard_stock_name, name)
        }

        val alignment = NativeLibAlignment.inspect(header)
        if (NativeLibAlignment.isDevice16KbPage && alignment != null && !alignment.is16KbCompatible) {
            return context.getString(
                R.string.plugin_guard_not_16kb_aligned,
                name,
                alignment.maxLoadAlignment
            )
        }

        return null
    }

    private fun ensureSoSuffix(name: String): String =
        if (name.endsWith(".so")) name else "$name.so"

    private fun uniqueName(dir: File, name: String): String {
        var candidate = name
        var counter = 1
        while (dir.resolve(candidate).exists()) {
            val base = name.removeSuffix(".so")
            candidate = "${base}_$counter.so"
            counter += 1
        }
        return candidate
    }

    private companion object {
        const val MANIFEST_FILE = "plugins.json"
    }
}

/** Tracen's plugins: Hachimi loads `libhachimi_*.so` from the same native dir as `libmain.so`. */
val TracenPlugins = PluginStore(
    directoryName = "plugins",
    prefix = "libhachimi_",
    reservedNames = setOf(
        "libmain.so",
        "libmain-arm64-v8a.so",
        "libmain_orig.so",
        "libunity.so",
        "libil2cpp.so",
        "libc++_shared.so"
    )
)

/** Astraea's plugins: DaringTact discovers `libdaringtact_*.so` next to `libdaringtact.so`. */
val AstraeaPlugins = PluginStore(
    directoryName = "priconne_plugins",
    prefix = "libdaringtact_",
    reservedNames = setOf(
        "libdaringtact.so",
        "libmain.so",
        "libunity.so",
        "libil2cpp.so",
        "libnative.so",
        "libc++_shared.so"
    )
)
