package dev.hachifridge.hachidroid.patcher.astraea

import dev.hachifridge.hachidroid.patcher.ApplicationRedirect
import dev.hachifridge.hachidroid.patcher.ShellSplitPatcher
import java.io.File

/**
 * Rewrites the two split APKs a Priconne install needs, so the game runs with the libraries the
 * user restored instead of the native shell that shipped with it.
 *
 * This is deliberately not part of [ClientPatcher]: nothing here resembles the Hachimi flow (no
 * libmain swap, no translation files), and the two paths only share the extract → rebuild → sign
 * plumbing around them. Plugins *are* written here, because DaringTact discovers them from the
 * native library directory the split installs.
 *
 * Base split: the shell's Application class owns startup, so a clean [APPLICATION_CLASS] from
 * extensions.dex replaces it, the manifest is redirected to that class, the storage permissions
 * the game needs on Android 13+ are declared, and any lib/ directory is dropped (the shell's
 * stub library lives there and must not be loaded).
 *
 * Config split: the packer stub libraries are removed, `libil2cpp.so`, `libnative.so` and
 * `libdaringtact.so` are written in from the user's library folder, and every enabled DaringTact
 * plugin is written next to them as `libdaringtact_<name>.so` for the mod's own discovery.
 */
internal object AstraeaApkPatcher : ShellSplitPatcher {

    /** Class in extensions.dex that replaces the game's own Application. */
    const val APPLICATION_CLASS = "dev.hachifridge.PriconneApplication"

    /** Native libraries a patched install needs, in the order they are written. */
    val REQUIRED_LIBS = listOf("libil2cpp.so", "libnative.so", "libdaringtact.so")

    private const val ARM64_LIB_DIR = "lib/arm64-v8a"

    /**
     * Archive paths of the libraries the patch writes. The store splits deflate every `.so`, so
     * without listing these the rebuilt APK would carry them compressed and the 16 KB page
     * alignment below would never apply. Stored and aligned is valid whether or not the game
     * extracts its native libraries at install time.
     */
    override val archivePaths: List<String> = REQUIRED_LIBS.map { archivePath(it) }

    /** Archive path a library or plugin lands at, stored so the 16 KB alignment survives. */
    override fun archivePath(name: String): String = "$ARM64_LIB_DIR/$name"

    /** The packer ships its payload as `lib__<hash>__.so`; every one of them must go. */
    fun isPackerStub(fileName: String): Boolean = ApplicationRedirect.isPackerStub(fileName)

    override fun isConfigSplit(extractDir: File): Boolean = extractDir.resolve(ARM64_LIB_DIR).isDirectory

    /** The base split is identified by its dex payload. */
    override fun isBaseSplit(extractDir: File): Boolean = extractDir.resolve("classes.dex").isFile

    /**
     * Redirects the base split's Application to [APPLICATION_CLASS], declares the storage
     * permissions it requests at runtime, injects [applicationDex] as the next classesN.dex and
     * drops the packer's stub libraries. Returns `false` (after logging) if the manifest cannot be
     * rewritten, since a half-patched base would install and then crash.
     */
    override fun patchBaseSplit(
        extractDir: File,
        libsDir: File,
        applicationDex: ByteArray,
        log: (String) -> Unit
    ): Boolean = ApplicationRedirect.patchBaseSplit(
        extractDir = extractDir,
        applicationDex = applicationDex,
        applicationClass = APPLICATION_CLASS,
        log = log
    )

    /**
     * Replaces the protected libraries in a config split with [libsDir]'s copies, drops the
     * packer stubs and writes the enabled [plugins]. Every required library must exist and be
     * non-empty: a missing one would produce an install that dies at startup with no way to tell
     * why.
     */
    override fun patchConfigSplit(
        extractDir: File,
        libsDir: File,
        /** Archive name to source file, already carrying the mod runtime's plugin prefix. */
        plugins: List<Pair<String, File>>,
        log: (String) -> Unit
    ): Boolean {
        val arm64Dir = extractDir.resolve(ARM64_LIB_DIR)
        if (!arm64Dir.isDirectory) {
            log("lib/arm64-v8a not found in the config split")
            return false
        }

        arm64Dir.listFiles()?.forEach { file ->
            if (file.isFile && isPackerStub(file.name)) {
                if (file.delete()) log("Removed packer stub ${file.name}")
                else {
                    log("Could not remove packer stub ${file.name}")
                    return false
                }
            }
        }

        for (name in REQUIRED_LIBS) {
            val source = libsDir.resolve(name)
            if (!source.isFile || source.length() <= 0L) {
                log("Missing library: $name")
                return false
            }
            try {
                source.copyTo(arm64Dir.resolve(name), overwrite = true)
                log("Wrote $name (${source.length()} bytes)")
            } catch (ex: Exception) {
                log("Failed to write $name: ${ex.message}")
                return false
            }
        }

        for ((destName, plugin) in plugins) {
            if (REQUIRED_LIBS.contains(destName)) {
                log("Plugin ${plugin.name} collides with a required library; skipped")
                continue
            }
            try {
                plugin.copyTo(arm64Dir.resolve(destName), overwrite = true)
                log("Wrote plugin $destName (${plugin.length()} bytes)")
            } catch (ex: Exception) {
                log("Failed to write plugin $destName: ${ex.message}")
                return false
            }
        }

        return true
    }

}
