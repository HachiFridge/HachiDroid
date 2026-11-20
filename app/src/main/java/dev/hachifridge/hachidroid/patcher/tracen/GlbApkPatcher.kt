package dev.hachifridge.hachidroid.patcher.tracen

import dev.hachifridge.hachidroid.patcher.ApplicationRedirect
import dev.hachifridge.hachidroid.patcher.ShellSplitPatcher
import java.io.File

/**
 * Rewrites the splits of UM:PD's Global build (`com.cygames.umamusume`).
 *
 * Global is the one UM:PD release built like PC:RD: a native packer owns `Application`, the
 * `libil2cpp.so` shipped in the APK is encrypted behind a `lib__<hash>__.so` stub, and the plain
 * image only exists after that stub decrypts it. Running Hachimi here therefore cannot be the JP
 * libmain swap alone. This is the hybrid:
 *
 * - Base split: [ApplicationRedirect] points the manifest at [APPLICATION_CLASS], so
 *   the shell entrypoint never initialises and the stub never loads; the Senbei-restored
 *   `global-metadata.dat` replaces the obfuscated one the packer left in `assets`.
 * - Config split: the `lib__*.so` stub is removed, the user's Senbei-restored `libil2cpp.so` is
 *   written in, the stock `libmain.so` is kept as `libmain_orig.so`, and the Hachimi runtime
 *   replaces `libmain.so` exactly as on JP.
 *
 * Only `libil2cpp.so` needs unpacking: Global's `libnative.so` and `libunity.so` are ordinary
 * libraries, unlike Priconne's. The metadata is the second artifact that does: the packer's
 * `Application` deobfuscates it at startup, so redirecting that class would leave IL2CPP resolving
 * null method infos and dying in `il2cpp_init` even with a clean image.
 */
internal object GlbApkPatcher : ShellSplitPatcher {

    /** Class in extensions.dex that replaces the packer's Application. */
    const val APPLICATION_CLASS = "dev.hachifridge.GlbApplication"

    const val GAME_LIB_NAME = "libil2cpp.so"

    /** Local name of the restored il2cpp metadata, stored beside the restored library. */
    const val METADATA_NAME = "global-metadata.dat"

    /** Where Unity's loader reads the metadata from inside the APK. */
    const val METADATA_ARCHIVE_PATH = "assets/bin/Data/Managed/Metadata/$METADATA_NAME"

    /** The mod runtime, installed under the name the game's loader calls. */
    const val MOD_LIB_NAME = "libmain-arm64-v8a.so"

    /** Stock loader, kept so the Hachimi runtime has something to hand off to. */
    const val ORIG_MOD_LIB_NAME = "libmain_orig.so"

    /** Libraries a patched install needs in its library folder, in write order. */
    val REQUIRED_LIBS = listOf(GAME_LIB_NAME, MOD_LIB_NAME)

    private const val ARM64_LIB_DIR = "lib/arm64-v8a"
    private const val LIB_ENTRY_NAME = "libmain.so"

    /**
     * Archive paths the patch writes. Store splits deflate every `.so`, so without listing these
     * the rebuilt APK would carry them compressed and the 16 KB alignment would never apply.
     */
    override val archivePaths: List<String> = listOf(
        archivePath(GAME_LIB_NAME),
        archivePath(LIB_ENTRY_NAME),
        archivePath(ORIG_MOD_LIB_NAME)
    )

    /** Archive path a library or plugin lands at, stored so the 16 KB alignment survives. */
    override fun archivePath(name: String): String = "$ARM64_LIB_DIR/$name"

    /** The packer ships its payload as `lib__<hash>__.so`; every one of them must go. */
    fun isPackerStub(fileName: String): Boolean = ApplicationRedirect.isPackerStub(fileName)

    /** A config split is the one carrying the arm64 libraries; it has no classes.dex. */
    override fun isConfigSplit(extractDir: File): Boolean = extractDir.resolve(ARM64_LIB_DIR).isDirectory

    /** The base split is identified by its dex payload. */
    override fun isBaseSplit(extractDir: File): Boolean = extractDir.resolve("classes.dex").isFile

    override fun patchBaseSplit(
        extractDir: File,
        libsDir: File,
        applicationDex: ByteArray,
        log: (String) -> Unit
    ): Boolean {
        if (!ApplicationRedirect.patchBaseSplit(
                extractDir = extractDir,
                applicationDex = applicationDex,
                applicationClass = APPLICATION_CLASS,
                log = log
            )
        ) return false

        return writeMetadata(extractDir, libsDir, log)
    }

    /**
     * Replaces the base split's `global-metadata.dat` with the deobfuscated copy.
     *
     * The packer's `Application` is what deobfuscates this file, and [APPLICATION_CLASS] replaces
     * it, so the obfuscated copy that ships in the APK would reach IL2CPP untouched. The write is
     * required, not best-effort: without it the patched build installs and crashes at startup.
     */
    internal fun writeMetadata(
        extractDir: File,
        libsDir: File,
        log: (String) -> Unit
    ): Boolean {
        val source = libsDir.resolve(METADATA_NAME)
        if (!source.isFile || source.length() <= 0L) {
            log("Missing $METADATA_NAME")
            return false
        }

        // The restored copy has to replace the file the game actually loads. Unity keeps it at
        // [METADATA_ARCHIVE_PATH], but locating the shipped entry is what keeps the patch valid
        // for a build that moved it: writing to a path the game never reads would install a build
        // that still starts from the obfuscated copy.
        val dest = findShippedMetadata(extractDir) ?: extractDir.resolve(METADATA_ARCHIVE_PATH)
        return try {
            dest.parentFile?.mkdirs()
            source.copyTo(dest, overwrite = true)
            log("Wrote $METADATA_NAME (${source.length()} bytes)")
            true
        } catch (ex: Exception) {
            log("Failed to write $METADATA_NAME: ${ex.message}")
            false
        }
    }

    /** The metadata entry this base split ships, wherever Unity put it. */
    private fun findShippedMetadata(extractDir: File): File? =
        extractDir.walkTopDown().firstOrNull { it.isFile && it.name == METADATA_NAME }

    /**
     * Writes the restored library set into a config split and swaps the loader for the mod runtime.
     * Every required library must exist and be non-empty; a missing one would produce an install
     * that dies at startup with no way to tell why.
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

        val gameLib = libsDir.resolve(GAME_LIB_NAME)
        if (!gameLib.isFile || gameLib.length() <= 0L) {
            log("Missing library: $GAME_LIB_NAME")
            return false
        }
        if (!writeLibrary(gameLib, arm64Dir, GAME_LIB_NAME, log)) return false

        val modLib = libsDir.resolve(MOD_LIB_NAME)
        if (!modLib.isFile || modLib.length() <= 0L) {
            log("Missing library: $MOD_LIB_NAME")
            return false
        }

        val loader = arm64Dir.resolve(LIB_ENTRY_NAME)
        val origLoader = arm64Dir.resolve(ORIG_MOD_LIB_NAME)
        if (!origLoader.exists()) {
            if (loader.exists() && !loader.renameTo(origLoader)) {
                log("Could not keep the stock loader as $ORIG_MOD_LIB_NAME")
                return false
            }
            if (!origLoader.exists()) {
                log("Warning: $LIB_ENTRY_NAME was not in the split; the mod runtime has no stock loader to hand off to")
            } else {
                log("Kept the stock loader as $ORIG_MOD_LIB_NAME")
            }
        }
        // A repatch goes straight to overwriting the previous runtime.
        if (!writeLibrary(modLib, arm64Dir, LIB_ENTRY_NAME, log)) return false

        val reserved = REQUIRED_LIBS + LIB_ENTRY_NAME + ORIG_MOD_LIB_NAME
        for ((destName, plugin) in plugins) {
            if (reserved.contains(destName)) {
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

    private fun writeLibrary(source: File, arm64Dir: File, name: String, log: (String) -> Unit): Boolean =
        try {
            source.copyTo(arm64Dir.resolve(name), overwrite = true)
            log("Wrote $name (${source.length()} bytes)")
            true
        } catch (ex: Exception) {
            log("Failed to write $name: ${ex.message}")
            false
        }
}
