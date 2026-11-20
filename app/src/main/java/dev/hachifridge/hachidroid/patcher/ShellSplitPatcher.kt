package dev.hachifridge.hachidroid.patcher

import java.io.File

/**
 * The split-rewriting half of a packer-protected client's patch.
 *
 * PC:RD and UM:PD's Global build ship behind the same native packer, so their patches are the
 * same two moves: replace the Application in the base split, and swap the protected libraries in
 * the config split. What differs is only which libraries exist and how the mod runtime is named,
 * which each implementation owns. [ClientPatcher] drives both through this interface, so the
 * extract → patch → rebuild → sign plumbing is written once.
 */
internal interface ShellSplitPatcher {

    /** Archive paths of the libraries this patch writes, kept stored for 16 KB alignment. */
    val archivePaths: List<String>

    /** Archive path a library or plugin lands at. */
    fun archivePath(name: String): String

    /** The base split is identified by its dex payload. */
    fun isBaseSplit(extractDir: File): Boolean

    /** The config split is the one carrying the arm64 libraries. */
    fun isConfigSplit(extractDir: File): Boolean

    fun patchBaseSplit(
        extractDir: File,
        /** The client's library folder, for files the base split has to carry as well. */
        libsDir: File,
        applicationDex: ByteArray,
        log: (String) -> Unit
    ): Boolean

    fun patchConfigSplit(
        extractDir: File,
        libsDir: File,
        /** Archive name to source file, already carrying the mod runtime's plugin prefix. */
        plugins: List<Pair<String, File>>,
        log: (String) -> Unit
    ): Boolean
}
