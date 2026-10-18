package dev.hachifridge.hachidroid.core

import dev.hachifridge.hachidroid.R

/**
 * The game family HachiDroid is set up for.
 *
 * A mode is more than a filter over the Tracen flow: it decides which packages are offered, what
 * identity the hero card presents and what the second dashboard tile reports. It is persisted so
 * the app reopens where the user left it.
 */
enum class PatchMode(
    val prefValue: String,
    val labelRes: Int,
    val packageNames: List<String>
) {
    /** Umamusume: Pretty Derby (JP / TW / CN / KR), patched with a Hachimi library. */
    TRACEN(
        prefValue = "tracen",
        labelRes = R.string.mode_tracen,
        packageNames = listOf(
            "jp.co.cygames.umamusume",
            "com.komoe.kmumamusumegp",
            "com.komoe.umamusumeofficial",
            "com.kakaogames.umamusume"
        )
    ),

    /** Princess Connect! Re:Dive (JP), patched with DaringTact. */
    ASTRAEA(
        prefValue = "astraea",
        labelRes = R.string.mode_astraea,
        packageNames = listOf("jp.co.cygames.princessconnectredive")
    );

    val defaultPackageName: String
        get() = packageNames.first()

    companion object {
        /** Unknown or missing values fall back to [TRACEN], so a fresh install just works. */
        fun fromPref(value: String?): PatchMode =
            entries.firstOrNull { it.prefValue == value } ?: TRACEN
    }
}
