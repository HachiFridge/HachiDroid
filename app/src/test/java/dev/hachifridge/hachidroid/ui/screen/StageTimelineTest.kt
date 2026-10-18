package dev.hachifridge.hachidroid.ui.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The timeline freshly merges per-file stages and hides its own estimate, so these rules decide
 * whether a run reads as "Extract 3/3" or as broken. They are pure, hence worth pinning down.
 */
class StageTimelineTest {

    @Test
    fun nextStage_firstRunCreatesRowWithEstimate() {
        val stage = StageTimeline.nextStage(
            previous = null,
            label = "patch libraries",
            detail = "base.apk",
            seconds = 1.5f,
            patchUnitCount = 2
        )

        assertEquals("patch libraries", stage.label)
        assertEquals("base.apk", stage.detail)
        assertEquals(1.5f, stage.seconds, 0.001f)
        assertEquals(1, stage.completed)
        assertEquals(2, stage.total)
    }

    @Test
    fun nextStage_oneShotStageExpectsSingleRun() {
        val stage = StageTimeline.nextStage(
            previous = null,
            label = "merge apks",
            detail = null,
            seconds = 0.2f,
            patchUnitCount = 4
        )

        assertEquals(1, stage.completed)
        assertEquals(1, stage.total)
    }

    @Test
    fun nextStage_repeatFoldsIntoTheSameRow() {
        val first = StageTimeline.nextStage(null, "extract", "base.apk", 1f, 3)
        val second = StageTimeline.nextStage(first, "extract", "config.arm64_v8a.apk", 2f, 3)

        assertEquals(2, second.completed)
        assertEquals(3, second.total)
        assertEquals(3f, second.seconds, 0.001f)
        // One file name no longer describes a row that covers several files.
        assertNull(second.detail)
    }

    @Test
    fun nextStage_keepsEveryRunForTheRowBreakdown() {
        // The row shows one total, but the menu under it has to name each file it worked on.
        val first = StageTimeline.nextStage(
            null, "patch libraries", "config.arm64_v8a.apk", 0.14f, 2
        )
        val second = StageTimeline.nextStage(
            first, "patch libraries", "base.apk", 0.20f, 2
        )

        assertEquals(
            listOf("config.arm64_v8a.apk", "base.apk"),
            second.entries.map { it.detail }
        )
        assertEquals(0.14f, second.entries[0].seconds, 0.001f)
        assertEquals(0.20f, second.entries[1].seconds, 0.001f)
    }

    @Test
    fun nextStage_neverReportsFewerRunsThanCompleted() {
        // An XAPK extracts its own container on top of the APKs, so a per-file stage can run
        // more often than the patcher counted files. "3/1" must never appear.
        val first = StageTimeline.nextStage(null, "extract", null, 1f, 1)
        val second = StageTimeline.nextStage(first, "extract", null, 1f, 1)
        val third = StageTimeline.nextStage(second, "extract", null, 1f, 1)

        assertEquals(3, third.completed)
        assertEquals(3, third.total)
    }

    @Test
    fun expectedRuns_treatsUncountedUnitsAsOne() {
        // patchUnitCount is only set once the split list is known; before that it is 0/absent.
        assertEquals(1, StageTimeline.expectedRuns("extract", completed = 1, patchUnitCount = 0))
        assertEquals(1, StageTimeline.expectedRuns("merge apks", completed = 1, patchUnitCount = 0))
    }

    @Test
    fun clampTotals_dropsEstimatedButUnfinishedRuns() {
        val stages = listOf(
            StageTimeline.nextStage(null, "patch libraries", null, 1f, 2),
            StageTimeline.nextStage(null, "merge apks", null, 1f, 2)
        )

        val clamped = StageTimeline.clampTotals(stages)

        assertEquals(1, clamped[0].total)
        assertEquals(1, clamped[1].total)
    }

    @Test
    fun clampTotals_leavesCompletedRowsUntouched() {
        val complete = StageTimeline.nextStage(
            StageTimeline.nextStage(null, "extract", null, 1f, 2),
            "extract",
            null,
            1f,
            2
        )

        val clamped = StageTimeline.clampTotals(listOf(complete))

        assertEquals(complete, clamped[0])
        assertEquals(2, clamped[0].total)
    }
}
