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

    @Test fun otherVersionsShowHowEachOneFits() {
        val cands = listOf(
            rec(1, 248.0, lone),
            rec(2, 249.0, good), rec(3, 249.0, good),
            rec(4, 213.0, good).copy(artist = "Someone Else"),
            rec(5, 213.0, good),
        )
        val f = Matching.fitOf(cands, 213.0, "Artist, Guest")
        assertTrue(f.getValue(1).fits.not())          // runs past the end of a 3:33 song
        assertEquals(3, f.getValue(2).agreeing)       // 2, 3, 4 and 5 share a timing: 3 others each
        assertEquals(0, f.getValue(1).agreeing)
        assertTrue(f.getValue(4).otherArtist)
        assertFalse(f.getValue(2).otherArtist)
        // Best fit first: the song's artist before another's; then fitting synced, words only, too long.
        val words = rec(6, 213.0, null)
        val order = Matching.bestFirst(cands + words, 248.0, "Artist").ordered.map { it.id }
        // At 4:08 every synced record fits: those that agree first (2, 3, 5 by the song's artist, then 4 by another),
        // then the lone one (1), then words only (6).
        assertEquals(listOf(2L, 3L, 5L, 4L, 1L, 6L), order)
        // At 3:33 the album timing runs past the end: words only come before records that are too long.
        assertEquals(6L, Matching.bestFirst(cands + words, 213.0, "Artist").ordered.first().id)
    }

    @Test fun sameArtistComparesWholeNamesInAnyCredit() {
        assertTrue(Matching.sameArtist("X-Ecutioners, Mike Shinoda, Mr. Hahn", "The X-Ecutioners"))
        assertTrue(Matching.sameArtist("X-Ecutioners, Mike Shinoda, Mr. Hahn", "Mike Shinoda/X-Ecutioners/Mr. Hahn"))
        assertTrue(Matching.sameArtist("Kesha, Pitbull", "Pitbull Feat. Ke\$ha"))
        assertTrue(Matching.sameArtist("U2", "U2, Green Day"))
        assertTrue(Matching.sameArtist("MØ", "Major Lazer & MØ"))
        assertFalse(Matching.sameArtist("Earth, Wind & Fire", "Arcade Fire"))
        assertFalse(Matching.sameArtist("Tyler, the Creator", "Bonnie Tyler"))
        assertFalse(Matching.sameArtist("R.E.M.", "The Rembrandts"))
        assertFalse(Matching.sameArtist("TX2", "Rush"))
        // Split words only as whole words.
        assertEquals(setOf("featherstone"), Matching.names("Featherstone"))
        assertEquals(setOf("ftisland"), Matching.names("FTISLAND"))
        assertEquals(setOf("xecutioners"), Matching.names("The X-Ecutioners"))
        assertEquals(setOf("marshmello", "anne-marie".filter { it.isLetterOrDigit() }), Matching.names("Marshmello x Anne-Marie"))
        assertFalse(Matching.sameArtist("The Weeknd", "The X-Ecutioners"))   // no bare "the" left over to match
    }

    @Test fun anUnknownArtistGivesNoArtistNote() {
        val c = listOf(rec(1, 248.0, good))
        assertFalse(Matching.fitOf(c, 248.0, "<unknown>").getValue(1).otherArtist)
        assertFalse(Matching.fitOf(c, 248.0, "Various Artists").getValue(1).otherArtist)
        assertFalse(Matching.fitOf(c, 248.0, "").getValue(1).otherArtist)
        assertTrue(Matching.fitOf(c, 248.0, "Someone").getValue(1).otherArtist)
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

    @Test fun yourTimingStaysOnlyWithTheSameRecord() {
        val anchors = listOf(Anchor(60_000, 300))
        val old = LyricsStore.Entry(rec(1, 248.0, good), pinned = false, offsetMs = 750, fetchedAt = 1, anchors = anchors)
        assertEquals(750L to anchors, LyricsRepo.keptTiming(old, 1))
        assertEquals(0L to emptyList<Anchor>(), LyricsRepo.keptTiming(old, 2))   // a new version starts at 0 (owner Q17)
        assertEquals(0L to emptyList<Anchor>(), LyricsRepo.keptTiming(null, 1))
    }

}
