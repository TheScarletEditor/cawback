package com.thescarletcoder.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Choosing the record (0.5.0, owner Q16, Q26). Synthetic records: made-up words, timings shaped like the real cases
 * checked by ear on 2026-10-11 (a lone mistimed upload against a group that agrees; album timing filed under a radio
 * edit's length).
 */
class MatchingTest {
    private fun lrc(times: List<Double>) = times.mapIndexed { i, t ->
        "[%02d:%05.2f] line %d".format((t / 60).toInt(), t % 60, i)
    }.joinToString("\n")

    private fun rec(id: Long, dur: Double, times: List<Double>?, plain: Boolean = true) =
        Candidate(id, "Song", "Artist", "Album", dur, false, if (plain) "words" else null, times?.let { lrc(it) })

    // The timing most records share: 40 lines from 0:06 to 3:58.
    private val good = (0 until 40).map { 6.1 + it * 5.8 }
    // A lone upload: the same lines, late at first, early later on.
    private val lone = good.mapIndexed { i, t -> if (i < 10) t + 4.5 else t - 4.4 }

    @Test fun aLoneUploadLosesToTheTimingMostRecordsShare() {
        val exact = rec(1, 248.0, lone)
        val group = (2L..6L).map { rec(it, 249.0, good.map { t -> t + 0.05 * (it % 3) }) }
        val c = Matching.choose(exact, group, 248.0)!!
        assertTrue(c.candidate.id in 2L..6L)
        assertFalse(c.unsure)
    }

    @Test fun theExactHitWinsInsideItsGroup() {
        val exact = rec(9, 250.0, good)
        val c = Matching.choose(exact, listOf(rec(2, 248.0, good), rec(3, 248.5, good)), 248.0)!!
        assertEquals(9L, c.candidate.id)   // agrees with the others, so it stays, even if another length is closer
    }

    @Test fun albumTimingOnAnEditsLengthIsNotUsed() {
        // A 3:33 edit; the synced records carry the album's lines up to 3:58: they don't fit.
        val c = Matching.choose(null, listOf(rec(1, 213.0, good), rec(2, 212.6, null)), 213.0)!!
        assertEquals(2L, c.candidate.id)   // words only beats wrong timing
        assertFalse(c.unsure)
    }

    @Test fun onlyRecordsThatRunPastTheEndAreShownAsUnsure() {
        val c = Matching.choose(null, listOf(rec(1, 213.0, good, plain = false)), 213.0)!!
        assertEquals(1L, c.candidate.id)
        assertTrue(c.unsure)
    }

    @Test fun anEndMarkerJustAfterTheMusicStillFits() {
        val withMarker = good + (248.0 + 4.0)
        assertTrue(Matching.fits(rec(1, 248.0, withMarker), 248.0))
        assertFalse(Matching.fits(rec(1, 248.0, good + (248.0 + 6.0)), 248.0))
    }

    @Test fun twoTimingsWithNoMajorityAreUnsure() {
        val c = Matching.choose(rec(1, 248.0, lone), listOf(rec(2, 248.0, good)), 248.0)!!
        assertEquals(1L, c.candidate.id)   // a tie keeps the exact hit, as before 0.5.0
        assertTrue(c.unsure)
    }

    @Test fun moreLinesWinWhenLengthsTie() {
        val shorter = good.take(30)
        val c = Matching.choose(null, listOf(rec(1, 248.0, shorter), rec(2, 248.0, good)), 248.0)!!
        // Different line counts are different timings; the group sizes and lengths tie, so more lines decide (D-038).
        assertEquals(2L, c.candidate.id)
        assertTrue(c.unsure)
    }

    @Test fun aSyncedFieldWithNoTimedLinesCountsAsWords() {
        val tagsOnly = Candidate(1, "Song", "Artist", "Album", 248.0, false, null, "[ar:Artist]\n[length:04:08]")
        assertEquals(1L, Matching.choose(null, listOf(tagsOnly), 248.0)!!.candidate.id)   // no crash
        // Real words beat a closer record that has only tags.
        assertEquals(2L, Matching.choose(null, listOf(tagsOnly, rec(2, 250.0, null)), 248.0)!!.candidate.id)
    }

    @Test fun nothingCloseIsNoMatch() {
        assertNull(Matching.choose(null, listOf(rec(1, 300.0, good)), 248.0))
        assertNull(Matching.choose(null, emptyList(), 248.0))
    }

    @Test fun anOldAutomaticMatchIsLookedUpAgainButAPickIsNot() {
        val c = rec(1, 248.0, good)
        assertFalse(LyricsRepo.isCurrent(LyricsStore.Entry(c, pinned = false, offsetMs = 0, fetchedAt = 1)))
        assertTrue(LyricsRepo.isCurrent(LyricsStore.Entry(c, pinned = true, offsetMs = 0, fetchedAt = 1)))
        assertTrue(LyricsRepo.isCurrent(LyricsStore.Entry(c, pinned = false, offsetMs = 0, fetchedAt = 1, matchVersion = LyricsRepo.MATCHING)))
    }

    @Test fun aNudgeStaysOnlyWithTheSameRecord() {
        val old = LyricsStore.Entry(rec(1, 248.0, good), pinned = false, offsetMs = 750, fetchedAt = 1)
        assertEquals(750L, LyricsRepo.keptOffset(old, 1))
        assertEquals(0L, LyricsRepo.keptOffset(old, 2))
        assertEquals(0L, LyricsRepo.keptOffset(null, 1))
    }

}
