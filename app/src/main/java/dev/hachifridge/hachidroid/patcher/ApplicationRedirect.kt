package dev.hachifridge.hachidroid.patcher

import com.reandroid.arsc.chunk.xml.AndroidManifestBlock
import dev.hachifridge.hachidroid.client.ShellDetection
import java.io.File

/**
 * Redirects a base split's Application to a class carried by extensions.dex.
 *
 * The shell-protected clients (PC:RD, and UM:PD's Global build) own `Application` through their
 * native packer. Replacing it is what lets the game start once that shell is removed: the
 * replacement sets the entry class's static context and requests the storage permissions the game
 * reads on modern Android, and nothing else.
 *
 * The manifest is rewritten in place, the dex is injected under the next free `classesN.dex` name,
 * and the packer's stub libraries (`lib__<hash>__.so`) are deleted so they never load.
 *
 * A half-patched base would install and then crash with no useful log, so every failure is
 * reported to [log] and answers `false`.
 */
internal object ApplicationRedirect {

    private const val LIB_DIR = "lib"
    private const val MANIFEST_FILE_NAME = "AndroidManifest.xml"

    private const val ATTR_NAME = 0x01010003
    private const val TAG_USES_PERMISSION = "uses-permission"

    private val DEX_FILE_PATTERN = Regex("^classes(?:([0-9]+))?\\.dex$")

    /** The packer ships its loader as `lib__<hash>__.so`; it must never be loaded. */
    fun isPackerStub(fileName: String): Boolean = ShellDetection.isPackerStub(fileName)

    private val PERMISSIONS = listOf(
        "android.permission.READ_EXTERNAL_STORAGE",
        "android.permission.WRITE_EXTERNAL_STORAGE",
        "android.permission.READ_MEDIA_IMAGES",
        "android.permission.READ_MEDIA_VIDEO",
        "android.permission.READ_MEDIA_AUDIO",
        "android.permission.MANAGE_EXTERNAL_STORAGE"
    )

    fun patchBaseSplit(
        extractDir: File,
        applicationDex: ByteArray,
        applicationClass: String,
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
                .setValueAsString(applicationClass)

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
            log("Application redirected to $applicationClass, ${added.size} permission(s) added")

            val dexName = nextDexEntryName(extractDir)
            extractDir.resolve(dexName).writeBytes(applicationDex)
            log("Application class injected as $dexName")

            val removed = removeShellStubs(extractDir)
            if (removed.isNotEmpty()) {
                log("Removed packer stub(s): ${removed.joinToString(", ")}")
            }

            return true
        } catch (ex: Exception) {
            log("Failed to patch the base split: ${ex.message}")
            return false
        }
    }

    /**
     * Deletes every packer stub under `lib/` and prunes the directories that leaves empty.
     *
     * Only the stubs go. A merged APK carries the game's real libraries in the same tree, and the
     * config-split patch is what fills them in, so dropping all of `lib/` would destroy a working
     * merged patch. Returns the file names removed, for the log.
     */
    fun removeShellStubs(extractDir: File): List<String> {
        val libDir = extractDir.resolve(LIB_DIR)
        if (!libDir.isDirectory) return emptyList()

        val stubs = libDir.walkTopDown()
            .filter { it.isFile && isPackerStub(it.name) }
            .toList()
        stubs.forEach { it.delete() }

        // Deepest first, so a directory emptied by its child can be pruned in the same pass.
        libDir.walkBottomUp()
            .filter { it != libDir && it.isDirectory && it.list()?.isEmpty() == true }
            .forEach { it.delete() }
        if (libDir.list()?.isEmpty() == true) libDir.delete()

        return stubs.map { it.name }
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
