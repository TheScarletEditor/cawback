package com.thescarletcoder.lyrics

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** The user's timing (0.5.0, owner Q17, Q22): a base nudge plus anchors from tapped lines. Synthetic lines only. */
class YourTimingTest {
    private val lines = (0 until 6).map { LyricLine(10_000L + it * 10_000L, "line $it") }   // 0:10, 0:20 ... 0:60

    @Test fun noTimingLeavesTheLinesAlone() {
        assertSame(lines, YourTiming.apply(lines, 0, emptyList()))
    }

    @Test fun theBaseNudgeMovesEveryLine() {
        val moved = YourTiming.apply(lines, 500, emptyList())   // "Earlier" twice: lyrics 0.5 s earlier
        assertEquals(lines.map { it.timeMs!! - 500 }, moved.map { it.timeMs })
    }

    @Test fun linesBetweenTwoAnchorsFollowBoth() {
        // The record runs 4 s late at 0:20 and 4 s early at 0:40 (like a mistimed upload).
        val a = listOf(Anchor(20_000, 4_000), Anchor(40_000, -4_000))
        assertEquals(4_000L, YourTiming.deltaAt(a, 10_000))   // before the first: the first one's
        assertEquals(4_000L, YourTiming.deltaAt(a, 20_000))
        assertEquals(0L, YourTiming.deltaAt(a, 30_000))       // halfway between
        assertEquals(-4_000L, YourTiming.deltaAt(a, 40_000))
        assertEquals(-4_000L, YourTiming.deltaAt(a, 60_000))  // after the last: the last one's
    }

    @Test fun anchorsAddToTheBase() {
        val a = listOf(Anchor(20_000, 1_000))
        assertEquals(1_500L, YourTiming.offsetAt(500, a, 30_000))
        // An anchor saved as the whole move is relative to the base it was tapped with.
        val tapped = YourTiming.withAnchor(emptyList(), baseMs = 500, lineMs = 20_000, offsetMs = 2_000)
        assertEquals(listOf(Anchor(20_000, 1_500)), tapped)
        assertEquals(2_000L, YourTiming.offsetAt(500, tapped, 20_000))
        // Earlier or Later afterwards moves the anchored line with everything else.
        assertEquals(2_250L, YourTiming.offsetAt(750, tapped, 20_000))
    }

    @Test fun aSecondTapOnTheSameLineReplacesTheFirst() {
        val once = YourTiming.withAnchor(emptyList(), 0, 20_000, 1_000)
        val twice = YourTiming.withAnchor(once + Anchor(50_000, 200), 0, 20_000, 3_000)
        assertEquals(listOf(Anchor(20_000, 3_000), Anchor(50_000, 200)), twice)
    }

    @Test fun movesStayWithinTenSeconds() {
        assertEquals(10_000L, YourTiming.offsetAt(9_000, listOf(Anchor(0, 5_000)), 0))
        assertEquals(listOf(Anchor(1_000, 10_000)), YourTiming.withAnchor(emptyList(), 0, 1_000, 60_000))
    }

    @Test fun theOrderOnScreenNeverChanges() {
        // Two anchors pulling hard against each other would put line 2 before line 1.
        val a = listOf(Anchor(20_000, -9_000), Anchor(30_000, 9_000))
        val moved = YourTiming.apply(lines, 0, a)
        val times = moved.map { it.timeMs!! }
        assertTrue(times.zipWithNext().all { (x, y) -> y >= x })
        assertEquals(lines.map { it.text }, moved.map { it.text })
    }

    @Test fun aTapSetsTheLineToWhenItWasHeard() {
        // The line is at 0:30 in the record; it was heard (tap minus reaction) at 0:34; no headphone delay.
        val off = YourTiming.offsetFromTap(lineMs = 30_000, tapPositionMs = 34_200, outputDelayMs = 0, reactionMs = 200)
        assertEquals(-4_000L, off)   // the lyrics come 4 s later
        val shown = YourTiming.apply(listOf(LyricLine(30_000, "x")), 0, listOf(Anchor(30_000, off)))
        assertEquals(34_000L, shown.single().timeMs)
        // With Bluetooth adding 300 ms on its own, the anchor takes it out so it isn't counted twice.
        assertEquals(-4_300L, YourTiming.offsetFromTap(30_000, 34_200, 300, 200))
    }

    @Test fun theTimingLineSaysWhatIsSet() {   // wording approved by the owner (0.5.0)
        assertEquals("Lyrics match the song's timing", timingLabel(0))
        assertEquals("Lyrics earlier by 0.25s", timingLabel(250))
        assertEquals("Lyrics later by 0.50s", timingLabel(-500))
        assertEquals("Your timing: 1 line set", timingLabel(0, 1))
        assertEquals("Your timing: 3 lines set", timingLabel(0, 3))
        assertEquals("Lyrics earlier by 0.50s, 3 lines set", timingLabel(500, 3))
    }

    @Test fun theSavedFormatKeepsAnchorsAndReadsOldEntries() {
        val c = Candidate(7, "Song", "Artist", "Album", 200.0, false, "words", "[00:10.00] line 0")
        val e = LyricsStore.Entry(c, pinned = true, offsetMs = 250, fetchedAt = 9, matchVersion = 1, unsure = false,
            anchors = listOf(Anchor(10_000, -400), Anchor(90_000, 600)))
        assertEquals(e, LyricsStore.decode(LyricsStore.encode(e)))
        // An entry saved by 0.4.x: no matching version, no anchors.
        val old = JSONObject("""{"pinned":false,"offset":500,"at":3,"c":{"id":7,"track":"Song","artist":"Artist","album":"Album","duration":200.0,"instrumental":false,"plain":"words","synced":""}}""")
        val read = LyricsStore.decode(old)
        assertEquals(500L, read.offsetMs); assertEquals(0, read.matchVersion); assertEquals(emptyList<Anchor>(), read.anchors)
        // A damaged anchor is skipped; the rest of the entry and the good anchors stay.
        val damaged = LyricsStore.encode(e).put("anchors", org.json.JSONArray("[[\"x\",1],[90000,600],[5]]"))
        val kept = LyricsStore.decode(damaged)
        assertEquals(listOf(Anchor(90_000, 600)), kept.anchors); assertEquals(c, kept.candidate)
    }
}
