package dev.hachifridge.hachidroid.patcher

import com.reandroid.arsc.chunk.xml.AndroidManifestBlock
import java.io.File

/**
 * Rewrites the two split APKs a Priconne install needs, so the game runs with the libraries the
 * user restored instead of the CrackProof shell that shipped with it.
 *
 * This is deliberately not part of [AppPatcher]: nothing here resembles the Hachimi flow (no
 * libmain swap, no plugins, no translation files), and the two paths only share the extract →
 * rebuild → sign plumbing around them.
 *
 * Base split: the shell's Application class owns startup, so a clean [APPLICATION_CLASS] from
 * extensions.dex replaces it, the manifest is redirected to that class, the storage permissions
 * the game needs on Android 13+ are declared, and any lib/ directory is dropped (the shell's
 * stub library lives there and must not be loaded).
 *
 * Config split: the CrackProof stub libraries are removed and `libil2cpp.so`, `libnative.so` and
 * `libdaringtact.so` are written in from the user's library folder.
 */
internal object AstraeaApkPatcher {

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
    val LIB_ARCHIVE_PATHS = REQUIRED_LIBS.map { "$ARM64_LIB_DIR/$it" }
    private const val LIB_DIR = "lib"
    private const val MANIFEST_FILE_NAME = "AndroidManifest.xml"

    private const val ATTR_NAME = 0x01010003

    private const val TAG_USES_PERMISSION = "uses-permission"

    private val DEX_FILE_PATTERN = Regex("^classes(?:([0-9]+))?\\.dex$")

    /** CrackProof ships its payload as `lib__<hash>__.so`; every one of them must go. */
    private val CRACKPROOF_STUB_PATTERN = Regex("^lib__.*__\\.so$")

    private val PERMISSIONS = listOf(
        "android.permission.READ_EXTERNAL_STORAGE",
        "android.permission.WRITE_EXTERNAL_STORAGE",
        "android.permission.READ_MEDIA_IMAGES",
        "android.permission.READ_MEDIA_VIDEO",
        "android.permission.READ_MEDIA_AUDIO",
        "android.permission.MANAGE_EXTERNAL_STORAGE"
    )

    /** A config split is the one carrying the arm64 libraries; it has no classes.dex. */
    fun isConfigSplit(extractDir: File): Boolean = extractDir.resolve(ARM64_LIB_DIR).isDirectory

    /** The base split is identified by its dex payload. */
    fun isBaseSplit(extractDir: File): Boolean = extractDir.resolve("classes.dex").isFile

    fun isCrackProofStub(fileName: String): Boolean =
        CRACKPROOF_STUB_PATTERN.matches(fileName)

    /**
     * Redirects the base split's Application to [APPLICATION_CLASS], declares the storage
     * permissions it requests at runtime, injects [applicationDex] as the next classesN.dex and
     * removes any lib/ directory. Returns `false` (after logging) if the manifest cannot be
     * rewritten, since a half-patched base would install and then crash.
     */
    fun patchBaseSplit(
        extractDir: File,
        applicationDex: ByteArray,
        log: (String) -> Unit
    ): Boolean {
        val manifestFile = extractDir.resolve(MANIFEST_FILE_NAME)
        if (!manifestFile.isFile) {
            log("AndroidManifest.xml not found in the base split")
            return false
        }

        try {
            val manifest = AndroidManifestBlock.load(manifestFile)

            val application = manifest.getOrCreateApplicationElement()
            application.getOrCreateAndroidAttribute(AndroidManifestBlock.NAME_name, ATTR_NAME)
                .setValueAsString(APPLICATION_CLASS)

            val existing = manifest.manifestElement
                ?.listElements(TAG_USES_PERMISSION)
                ?.mapNotNull { element ->
                    (element.searchAttributeByName(AndroidManifestBlock.NAME_name)
                        ?: element.searchAttributeByResourceId(ATTR_NAME))?.valueAsString
                }
                ?.toSet()
                .orEmpty()

            val added = PERMISSIONS.filter { it !in existing }
            if (added.isNotEmpty()) {
                val root = manifest.manifestElement
                for (permission in added) {
                    val element = root.createChildElement(TAG_USES_PERMISSION)
                    element.getOrCreateAndroidAttribute(AndroidManifestBlock.NAME_name, ATTR_NAME)
                        .setValueAsString(permission)
                }
            }

            manifest.refreshFull()
            manifestFile.writeBytes(manifest.bytes)
            log("Application redirected to $APPLICATION_CLASS, ${added.size} permission(s) added")

            val dexName = nextDexEntryName(extractDir)
            extractDir.resolve(dexName).writeBytes(applicationDex)
            log("Application class injected as $dexName")

            // The base split's lib/ only ever held the shell's stub; the real libraries belong to
            // the config split and are written there.
            val libDir = extractDir.resolve(LIB_DIR)
            if (libDir.exists() && libDir.deleteRecursively()) {
                log("Removed lib/ from the base split")
            }

            return true
        } catch (ex: Exception) {
            log("Failed to patch the base split: ${ex.message}")
            return false
        }
    }

    /**
     * Replaces the protected libraries in a config split with [libsDir]'s copies and drops the
     * CrackProof stubs. Every required library must exist and be non-empty: a missing one would
     * produce an install that dies at startup with no way to tell why.
     */
    fun patchConfigSplit(
        extractDir: File,
        libsDir: File,
        log: (String) -> Unit
    ): Boolean {
        val arm64Dir = extractDir.resolve(ARM64_LIB_DIR)
        if (!arm64Dir.isDirectory) {
            log("lib/arm64-v8a not found in the config split")
            return false
        }

        arm64Dir.listFiles()?.forEach { file ->
            if (file.isFile && isCrackProofStub(file.name)) {
                if (file.delete()) log("Removed CrackProof stub ${file.name}")
                else {
                    log("Could not remove CrackProof stub ${file.name}")
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

        return true
    }

    private fun nextDexEntryName(extractDir: File): String {
        var maxIndex = 0
        extractDir.listFiles { file -> DEX_FILE_PATTERN.matches(file.name) }?.forEach { file ->
            val index = DEX_FILE_PATTERN.matchEntire(file.name)?.groupValues?.get(1)
            maxIndex = maxOf(maxIndex, index?.toIntOrNull() ?: 1)
        }
        val nextIndex = maxIndex + 1
        return if (nextIndex == 1) "classes.dex" else "classes$nextIndex.dex"
    }
}
