package com.thescarletcoder.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Choosing the record (0.5.0, owner Q16, Q26). Synthetic records: made-up words, timings shaped like real cases
 * (a lone mistimed upload against a group that agrees; album timing filed under a radio
 * edit's length).
 */
class MatchingTest {
    private fun lrc(times: List<Double>) = times.mapIndexed { i, t ->
        "[%02d:%05.2f] line %d".format((t / 60).toInt(), t % 60, i)
    }.joinToString("\n")

    private fun rec(id: Long, dur: Double, times: List<Double>?, plain: Boolean = true) =
        Candidate(id, "Song", "Artist", "Album", dur, false, if (plain) "words" else null, times?.let { lrc(it) })

    // The timing most records share: 40 lines from 0:06 to 3:52.
    private val good = (0 until 40).map { 6.1 + it * 5.8 }
    // A lone upload: the same lines, late at first, early later on.
    private val lone = good.mapIndexed { i, t -> if (i < 10) t + 3.9 else t - 3.7 }

    @Test fun aLoneUploadLosesToTheTimingMostRecordsShare() {
        val exact = rec(1, 262.0, lone)
        val group = (2L..6L).map { rec(it, 263.0, good.map { t -> t + 0.05 * (it % 3) }) }
        val c = Matching.choose(exact, group, 262.0)!!
        assertTrue(c.candidate.id in 2L..6L)
        assertFalse(c.unsure)
    }

    @Test fun theExactHitWinsInsideItsGroup() {
        val exact = rec(9, 264.0, good)
        val c = Matching.choose(exact, listOf(rec(2, 262.0, good), rec(3, 262.5, good)), 262.0)!!
        assertEquals(9L, c.candidate.id)   // agrees with the others, so it stays, even if another length is closer
    }

    @Test fun albumTimingOnAnEditsLengthIsNotUsed() {
        // A 3:41 edit; the synced records carry the album's lines up to 3:52: they don't fit.
        val c = Matching.choose(null, listOf(rec(1, 221.0, good), rec(2, 220.6, null)), 221.0)!!
        assertEquals(2L, c.candidate.id)   // words only beats wrong timing
        assertFalse(c.unsure)
    }

    @Test fun onlyRecordsThatRunPastTheEndAreShownAsUnsure() {
        val c = Matching.choose(null, listOf(rec(1, 221.0, good, plain = false)), 221.0)!!
        assertEquals(1L, c.candidate.id)
        assertTrue(c.unsure)
    }

    @Test fun anEndMarkerJustAfterTheMusicStillFits() {
        val withMarker = good + (262.0 + 4.0)
        assertTrue(Matching.fits(rec(1, 262.0, withMarker), 262.0))
        assertFalse(Matching.fits(rec(1, 262.0, good + (262.0 + 6.0)), 262.0))
    }

    @Test fun twoTimingsWithNoMajorityAreUnsure() {
        val c = Matching.choose(rec(1, 262.0, lone), listOf(rec(2, 262.0, good)), 262.0)!!
        assertEquals(1L, c.candidate.id)   // a tie keeps the exact hit, as before 0.5.0
        assertTrue(c.unsure)
    }

    @Test fun moreLinesWinWhenLengthsTie() {
        val shorter = good.take(30)
        val c = Matching.choose(null, listOf(rec(1, 262.0, shorter), rec(2, 262.0, good)), 262.0)!!
        // Different line counts are different timings; the group sizes and lengths tie, so more lines decide (D-038).
        assertEquals(2L, c.candidate.id)
        assertTrue(c.unsure)
    }

    @Test fun aSyncedFieldWithNoTimedLinesCountsAsWords() {
        val tagsOnly = Candidate(1, "Song", "Artist", "Album", 262.0, false, null, "[ar:Artist]\n[length:04:22]")
        assertEquals(1L, Matching.choose(null, listOf(tagsOnly), 262.0)!!.candidate.id)   // no crash
        // Real words beat a closer record that has only tags.
        assertEquals(2L, Matching.choose(null, listOf(tagsOnly, rec(2, 264.0, null)), 262.0)!!.candidate.id)
    }

    @Test fun otherVersionsShowHowEachOneFits() {
        val cands = listOf(
            rec(1, 262.0, lone),
            rec(2, 263.0, good), rec(3, 263.0, good),
            rec(4, 221.0, good).copy(artist = "Someone Else"),
            rec(5, 221.0, good),
        )
        val f = Matching.fitOf(cands, 221.0, "Artist, Guest")
        assertTrue(f.getValue(1).fits.not())          // runs past the end of a 3:41 song
        assertEquals(3, f.getValue(2).agreeing)       // 2, 3, 4 and 5 share a timing: 3 others each
        assertEquals(0, f.getValue(1).agreeing)
        assertTrue(f.getValue(4).otherArtist)
        assertFalse(f.getValue(2).otherArtist)
        // Best fit first: the song's artist before another's; then fitting synced, words only, too long.
        val words = rec(6, 221.0, null)
        val order = Matching.bestFirst(cands + words, 262.0, "Artist").ordered.map { it.id }
        // At 4:22 every synced record fits: those that agree first (2, 3, 5 by the song's artist, then 4 by another),
        // then the lone one (1), then words only (6).
        assertEquals(listOf(2L, 3L, 5L, 4L, 1L, 6L), order)
        // At 3:41 the album timing runs past the end: words only come before records that are too long.
        assertEquals(6L, Matching.bestFirst(cands + words, 221.0, "Artist").ordered.first().id)
    }

    @Test fun sameArtistComparesWholeNamesInAnyCredit() {
        // Invented credits with the shapes that matter.
        assertTrue(Matching.sameArtist("Q-Tones, Alex Rowe, Mr. Kay", "The Q-Tones"))
        assertTrue(Matching.sameArtist("Q-Tones, Alex Rowe, Mr. Kay", "Alex Rowe/Q-Tones/Mr. Kay"))
        assertTrue(Matching.sameArtist("Tessa, Bolt", "Bolt Feat. Te\$sa"))
        assertTrue(Matching.sameArtist("K9", "K9, Blue Harbor"))
        assertTrue(Matching.sameArtist("LØ", "Minor Static & LØ"))
        assertFalse(Matching.sameArtist("Wind, Rain & Fire", "Paper Fire"))
        assertFalse(Matching.sameArtist("Milo, the Maker", "Annie Milo"))
        assertFalse(Matching.sameArtist("R.E.N.", "The Renfields"))
        assertFalse(Matching.sameArtist("QX7", "Dune"))
        // Split words only as whole words.
        assertEquals(setOf("featherstone"), Matching.names("Featherstone"))
        assertEquals(setOf("ftharbor"), Matching.names("FTHARBOR"))
        assertEquals(setOf("qtones"), Matching.names("The Q-Tones"))
        assertEquals(setOf("marzipan", "anne-lise".filter { it.isLetterOrDigit() }), Matching.names("Marzipan x Anne-Lise"))
        assertFalse(Matching.sameArtist("The Weekly", "The Q-Tones"))   // no bare "the" left over to match
    }

    @Test fun anUnknownArtistGivesNoArtistNote() {
        val c = listOf(rec(1, 262.0, good))
        assertFalse(Matching.fitOf(c, 262.0, "<unknown>").getValue(1).otherArtist)
        assertFalse(Matching.fitOf(c, 262.0, "Various Artists").getValue(1).otherArtist)
        assertFalse(Matching.fitOf(c, 262.0, "").getValue(1).otherArtist)
        assertTrue(Matching.fitOf(c, 262.0, "Someone").getValue(1).otherArtist)
    }

    @Test fun nothingCloseIsNoMatch() {
        assertNull(Matching.choose(null, listOf(rec(1, 300.0, good)), 262.0))
        assertNull(Matching.choose(null, emptyList(), 262.0))
    }

    @Test fun anOldAutomaticMatchIsLookedUpAgainButAPickIsNot() {
        val c = rec(1, 262.0, good)
        assertFalse(LyricsRepo.isCurrent(LyricsStore.Entry(c, pinned = false, offsetMs = 0, fetchedAt = 1)))
        assertTrue(LyricsRepo.isCurrent(LyricsStore.Entry(c, pinned = true, offsetMs = 0, fetchedAt = 1)))
        assertTrue(LyricsRepo.isCurrent(LyricsStore.Entry(c, pinned = false, offsetMs = 0, fetchedAt = 1, matchVersion = LyricsRepo.MATCHING)))
        // The user's own timing keeps its version (finalize 0.5.0 Q1): a 0.4.1 nudge, fixed lines, a Line up at 0.
        assertTrue(LyricsRepo.isCurrent(LyricsStore.Entry(c, pinned = false, offsetMs = 300, fetchedAt = 1)))
        assertTrue(LyricsRepo.isCurrent(LyricsStore.Entry(c, pinned = false, offsetMs = 0, fetchedAt = 1, anchors = listOf(Anchor(6_100, -200)))))
        assertTrue(LyricsRepo.isCurrent(LyricsStore.Entry(c, pinned = false, offsetMs = 0, fetchedAt = 1, tapped = true)))
        assertFalse(LyricsRepo.isCurrent(LyricsStore.Entry(null, pinned = false, offsetMs = 300, fetchedAt = 1)))   // nothing to keep
    }

    @Test fun aPickShowsTheDotOnlyWhenItRunsPastTheEnd() {
        assertTrue(LyricsRepo.pickUnsure(true, false, rec(1, 262.0, good), 221.0))    // runs past a 3:41 song
        assertFalse(LyricsRepo.pickUnsure(true, true, rec(1, 262.0, good), 262.0))    // records disagree: the user chose it
        assertTrue(LyricsRepo.pickUnsure(false, true, rec(1, 262.0, good), 262.0))    // automatic: as matched
    }

    @Test fun yourTimingStaysOnlyWithTheSameRecord() {
        val anchors = listOf(Anchor(60_000, 300))
        val old = LyricsStore.Entry(rec(1, 262.0, good), pinned = false, offsetMs = 750, fetchedAt = 1, anchors = anchors)
        assertEquals(750L to anchors, LyricsRepo.keptTiming(old, 1))
        assertEquals(0L to emptyList<Anchor>(), LyricsRepo.keptTiming(old, 2))   // a new version starts at 0 (owner Q17)
        assertEquals(0L to emptyList<Anchor>(), LyricsRepo.keptTiming(null, 1))
    }

}
