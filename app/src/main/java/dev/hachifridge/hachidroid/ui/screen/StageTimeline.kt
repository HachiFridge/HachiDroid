package dev.hachifridge.hachidroid.ui.screen

import kotlin.math.max

/**
 * Pure rules for the run timeline: how many times a stage is expected to run and how a repeat
 * folds into the row it already has. Kept out of [PatchingViewModel] so both can be unit tested.
 */
internal object StageTimeline {

    /** Stages that run once per patched APK; everything else runs once per run. */
    val PER_FILE_STAGES = setOf(
        "extract",
        "patch libraries",
        "patch extensions",
        "index apk entries",
        "rebuild apk",
        "sign apk",
        "install plugins"
    )

    /**
     * How many runs [label] is expected to make: per-file stages follow the patcher's file
     * count, everything else runs once. [completed] wins when a run exceeds the estimate,
     * so the badge never reads "3/2" — an XAPK extracts its own container on top of the APKs.
     */
    fun expectedRuns(label: String, completed: Int, patchUnitCount: Int): Int {
        val expected = if (label in PER_FILE_STAGES) max(patchUnitCount, 1) else 1
        return max(expected, completed)
    }

    /**
     * The row for [label] after one more run: a fresh row on first sight, otherwise the previous
     * row with this run folded in. [PatchStage.detail] is dropped on a repeat because one file
     * name no longer describes a row that covers several files; each run is kept in
     * [PatchStage.entries] so the row can still be opened file by file.
     */
    fun nextStage(
        previous: PatchStage?,
        label: String,
        detail: String?,
        seconds: Float,
        patchUnitCount: Int
    ): PatchStage {
        val entry = StageEntry(detail, seconds)
        if (previous == null) {
            return PatchStage(
                label = label,
                detail = detail,
                seconds = seconds,
                completed = 1,
                total = expectedRuns(label, 1, patchUnitCount),
                entries = listOf(entry)
            )
        }

        val completed = previous.completed + 1
        return previous.copy(
            detail = null,
            seconds = previous.seconds + seconds,
            completed = completed,
            total = expectedRuns(label, completed, patchUnitCount),
            entries = previous.entries + entry
        )
    }

    /**
     * A successful run finished every stage it started, so the estimate's leftover runs
     * ("Patch libraries 1/2" when only the config split carries the libraries) are dropped
     * instead of reading as an unfinished step. A failed run keeps them: "1/2" is then the
     * honest hint that it stopped early.
     */
    fun clampTotals(stages: List<PatchStage>): List<PatchStage> =
        stages.map { stage ->
            if (stage.total != stage.completed) stage.copy(total = stage.completed) else stage
        }
}
