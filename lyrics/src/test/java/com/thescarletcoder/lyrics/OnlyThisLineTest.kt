package com.thescarletcoder.lyrics

import org.junit.Assert.assertEquals
import org.junit.Test

/** Only this line (owner, 2026-11-05): a line sung a little off moves alone; the lines either side stay where they were. */
class OnlyThisLineTest {
    // A made-up synced record: a line every 10 s from 0:10 to 1:00.
    private val lines = (1..6).map { LyricLine(it * 10_000L, "line $it") }
    private val times = lines.map { it.timeMs!! }
    private fun shown(base: Long, a: List<Anchor>) = YourTiming.apply(lines, base, a).map { it.timeMs!! }

    @Test fun movesOnlyThatLine() {
        val before = shown(0, emptyList())
        val a = YourTiming.withOnlyLine(emptyList(), 0, lineMs = 30_000, offsetMs = 600, times = times)
        val after = shown(0, a)
        assertEquals(29_400L, after[2])
        assertEquals(before.filterIndexed { i, _ -> i != 2 }, after.filterIndexed { i, _ -> i != 2 })
    }

    @Test fun afterATapTheRestOfTheSongStaysWithThatTap() {
        // Fix lines put line 2 a whole second earlier; everything after it followed. Line 4 is sung late: only it moves.
        val tapped = YourTiming.withAnchor(emptyList(), 0, 20_000, 1_000)
        val before = shown(0, tapped)
        val a = YourTiming.withOnlyLine(tapped, 0, lineMs = 40_000, offsetMs = 500, times = times)
        val after = shown(0, a)
        assertEquals(39_500L, after[3])
        assertEquals(before.filterIndexed { i, _ -> i != 3 }, after.filterIndexed { i, _ -> i != 3 })
    }

    @Test fun keepsANeighboursOwnTapAndWorksWithTheBaseNudge() {
        val tapped = YourTiming.withAnchor(emptyList(), 250, 20_000, 2_000)
        val a = YourTiming.withOnlyLine(tapped, 250, lineMs = 30_000, offsetMs = -400, times = times)
        assertEquals(1_750L, a.first { it.lineMs == 20_000L }.deltaMs)   // line 2's tap, untouched
        val before = shown(250, tapped); val after = shown(250, a)
        assertEquals(30_400L, after[2])
        assertEquals(before.filterIndexed { i, _ -> i != 2 }, after.filterIndexed { i, _ -> i != 2 })
    }

    @Test fun theFirstAndLastLinesHaveOneNeighbour() {
        val first = YourTiming.withOnlyLine(emptyList(), 0, 10_000, 300, times)
        assertEquals(listOf(10_000L, 20_000L), first.map { it.lineMs })
        assertEquals(listOf(9_700L) + times.drop(1), shown(0, first))
        val last = YourTiming.withOnlyLine(emptyList(), 0, 60_000, -300, times)
        assertEquals(listOf(50_000L, 60_000L), last.map { it.lineMs })
        assertEquals(times.dropLast(1) + 60_300L, shown(0, last))
    }

    @Test fun aJumpAtALongPauseStaysWhereItWas() {
        // A made-up record with one long pause, 0:30 to 0:45. Two taps 5 s apart in their moves make the change one jump
        // at that pause (0.5.1 Q53). Only this line on either side of the pause moves that line alone, and the jump stays.
        val gapped = listOf(10_000L, 20_000L, 30_000L, 45_000L, 50_000L, 60_000L).mapIndexed { i, t -> LyricLine(t, "line ${i + 1}") }
        val gTimes = gapped.map { it.timeMs!! }
        fun gShown(a: List<Anchor>) = YourTiming.apply(gapped, 0, a).map { it.timeMs!! }
        val jumped = YourTiming.withAnchor(YourTiming.withAnchor(emptyList(), 0, 10_000, 0), 0, 60_000, 5_000)
        val before = gShown(jumped)
        assertEquals(listOf(10_000L, 20_000L, 30_000L, 40_000L, 45_000L, 55_000L), before)
        for ((i, lineMs, offsetMs) in listOf(Triple(1, 20_000L, 400L), Triple(4, 50_000L, 5_300L))) {
            val after = gShown(YourTiming.withOnlyLine(jumped, 0, lineMs, offsetMs, gTimes))
            assertEquals(lineMs - offsetMs, after[i])
            assertEquals(before.filterIndexed { j, _ -> j != i }, after.filterIndexed { j, _ -> j != i })
        }
    }

    @Test fun aSkippedLineIsNotANeighbour() {
        // Line 3 (0:30) is marked not in this song, so it's left out of the times, as the repo does. Only this line on
        // line 4 pins lines 2 and 5, never the hidden line, and every line still showing keeps its time.
        val visible = lines.filter { it.timeMs != 30_000L }
        val vTimes = visible.map { it.timeMs!! }
        val a = YourTiming.withOnlyLine(emptyList(), 0, lineMs = 40_000, offsetMs = -500, times = vTimes)
        assertEquals(listOf(20_000L, 40_000L, 50_000L), a.map { it.lineMs })
        val before = YourTiming.apply(visible, 0, emptyList()).map { it.timeMs!! }
        val after = YourTiming.apply(visible, 0, a).map { it.timeMs!! }
        assertEquals(40_500L, after[2])
        assertEquals(before.filterIndexed { i, _ -> i != 2 }, after.filterIndexed { i, _ -> i != 2 })
    }

    @Test fun tappingTheSameLineAgainReplacesIt() {
        val once = YourTiming.withOnlyLine(emptyList(), 0, 30_000, 600, times)
        val twice = YourTiming.withOnlyLine(once, 0, 30_000, 200, times)
        assertEquals(3, twice.size)
        assertEquals(29_800L, shown(0, twice)[2])
    }
}
