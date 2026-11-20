package dev.hachifridge.hachidroid.client

import dev.hachifridge.hachidroid.R

/**
 * The game client HachiDroid is aimed at.
 *
 * A client is more than a package filter: it decides which packages are offered, which native
 * library set a patch writes, where its mod runtime comes from, what the hero card presents and
 * which palette themes the app. It is persisted so the app reopens where the user left it.
 *
 * One entry covers one game family, not one store build. Umamusume's Japan and Global releases are
 * the same game with the same patch shape — Hachimi in the native library directory — and differ
 * only in whether the build is packer-protected, which [ShellDetection] decides from the files the
 * user picks. Splitting them into two modes asked the user to know that, and to keep two library
 * folders and two plugin lists for one game.
 */
enum class Client(
    val prefValue: String,
    val labelRes: Int,
    val packageNames: List<String>,
    /** The native libraries a patched install of this client needs. */
    val libraries: LibrarySet,
    /** Where this client's plugins are kept, and how the patch names them. */
    val plugins: PluginStore,
    /** Where this client's mod runtime comes from, and how it is fetched. */
    val modSource: ModSource,
    /**
     * True when every build of this game sits behind a packer, so the split-set patch is the only
     * one that can ever apply. Uma is decided per build instead; see [ShellDetection].
     */
    val alwaysShellProtected: Boolean
) {
    /** Umamusume: Pretty Derby (JP / Global / TW / KR), patched with a Hachimi library. */
    TRACEN(
        prefValue = "tracen",
        labelRes = R.string.mode_tracen,
        packageNames = listOf(
            "jp.co.cygames.umamusume",
            "com.cygames.umamusume",
            "com.komoe.kmumamusumegp",
            "com.komoe.umamusumeofficial",
            "com.kakaogames.umamusume"
        ),
        libraries = TracenLibraries,
        plugins = TracenPlugins,
        modSource = TracenModSource,
        alwaysShellProtected = false
    ),

    /** Princess Connect! Re:Dive (JP), patched with DaringTact. */
    ASTRAEA(
        prefValue = "astraea",
        labelRes = R.string.mode_astraea,
        packageNames = listOf("jp.co.cygames.princessconnectredive"),
        libraries = AstraeaLibraries,
        plugins = AstraeaPlugins,
        modSource = AstraeaModSource,
        alwaysShellProtected = true
    );

    val defaultPackageName: String
        get() = packageNames.first()

    companion object {
        /** Unknown or missing values fall back to [TRACEN], so a fresh install just works. */
        fun fromPref(value: String?): Client =
            entries.firstOrNull { it.prefValue == value } ?: TRACEN
    }
}
