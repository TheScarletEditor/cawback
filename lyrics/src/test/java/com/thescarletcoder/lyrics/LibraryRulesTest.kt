package com.thescarletcoder.lyrics

import com.thescarletcoder.lyrics.library.History
import com.thescarletcoder.lyrics.library.History.Mode
import com.thescarletcoder.lyrics.library.History.Where
import com.thescarletcoder.lyrics.library.PlayRule
import com.thescarletcoder.lyrics.library.PlayTracker
import com.thescarletcoder.lyrics.library.SongKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryRulesTest {
    // --- PlayRule ---

    @Test fun twentySecondsOfThreeMinutesIsNotAPlayButNinetyFiveIs() {   // scope section 9
        assertFalse(PlayRule.counts(180_000, 20_000))
        assertTrue(PlayRule.counts(180_000, 95_000))
    }

    @Test fun shortTracksNeverCount() {
        assertFalse(PlayRule.counts(30_000, 30_000))
        assertFalse(PlayRule.counts(20_000, 20_000))
    }

    @Test fun halfTheSongCounts() {
        assertTrue(PlayRule.counts(200_000, 100_000))
        assertFalse(PlayRule.counts(200_000, 99_999))
    }

    @Test fun fourMinutesCountsForLongSongs() {
        assertTrue(PlayRule.counts(20 * 60_000L, 240_000))
        assertFalse(PlayRule.counts(20 * 60_000L, 239_000))
    }

    @Test fun unknownLengthNeedsFourMinutes() {
        assertFalse(PlayRule.counts(null, 200_000))
        assertTrue(PlayRule.counts(null, 240_000))
    }

    // --- PlayTracker ---

    private fun PlayTracker.at(key: String?, playing: Boolean, now: Long, duration: Long? = 200_000) =
        update(key, "Title", "Artist", null, duration, playing, now)

    @Test fun pausedTimeDoesNotCount() {
        val t = PlayTracker()
        assertNull(t.at("a", true, 0))
        t.at("a", false, 30_000)        // 30 s played
        t.at("a", true, 600_000)        // paused ten minutes
        val f = t.at("b", true, 650_000) // 50 s more, then the next song
        assertEquals("a", f!!.trackKey)
        assertEquals(80_000, f.listenedMs)
        assertEquals(0, f.startedAt)
    }

    @Test fun seekingDoesNotAddTime() {
        // Skipping to the end of a song is just more wall-clock time at whatever position: only 5 s were heard.
        val t = PlayTracker()
        t.at("a", true, 0)
        val f = t.at("b", true, 5_000)!!
        assertEquals(5_000, f.listenedMs)
        assertFalse(PlayRule.counts(f.durationMs, f.listenedMs))
    }

    @Test fun lateDurationIsPickedUp() {
        val t = PlayTracker()
        t.at("a", true, 0, duration = null)
        t.at("a", true, 1_000, duration = 180_000)
        assertEquals(180_000L, t.close(100_000)!!.durationMs)
    }

    @Test fun lateDurationKeepsTheSameListen() {
        // Track.key ends with the length, so it changes when the length arrives late: still one listen, not two.
        val t = PlayTracker()
        val early = Track("Glass Harbor", "The Lanterns", null, null).key
        val later = Track("Glass Harbor", "The Lanterns", null, 180_000).key
        assertNull(t.at(early, true, 0, duration = null))
        assertNull(t.at(later, true, 2_000, duration = 180_000))
        val f = t.close(100_000)!!
        assertEquals(100_000, f.listenedMs)
        assertEquals(later, f.trackKey)
    }

    @Test fun comingBackToTheSameSongContinuesTheListen() {
        // "Only while Lyrics is open": 60 s, away for a minute, back while the same song plays, 50 s more.
        val t = PlayTracker()
        t.at("a", true, 0)
        val first = t.close(60_000)!!
        assertFalse(PlayRule.counts(first.durationMs, first.listenedMs))   // not a play on its own
        assertNull(t.at("a", true, 120_000))
        val f = t.close(170_000)!!
        assertEquals(110_000, f.listenedMs)
        assertEquals(0, f.startedAt)
        assertTrue(PlayRule.counts(f.durationMs, f.listenedMs))
        assertFalse(f.earlierCounted)
    }

    @Test fun aLongSongIsNotCountedTwice() {
        // A 10-minute track: 5 min (counted at 4), Lyrics closes, comes back, 5 min more: still one play.
        val t = PlayTracker()
        t.at("a", true, 0, duration = 600_000)
        val first = t.close(300_000)!!
        assertTrue(PlayRule.counts(first.durationMs, first.listenedMs))
        t.at("a", true, 310_000, duration = 600_000)
        assertTrue(t.close(610_000)!!.earlierCounted)
    }

    @Test fun anotherSongAfterClosingStartsFresh() {
        val t = PlayTracker()
        t.at("a", true, 0)
        t.close(60_000)
        t.at("b", true, 70_000)
        val f = t.close(100_000)!!
        assertEquals(30_000, f.listenedMs)
        assertFalse(f.earlierCounted)
    }

    @Test fun closeFinishesTheSongOnce() {
        val t = PlayTracker()
        t.at("a", true, 0)
        assertEquals(120_000, t.close(120_000)!!.listenedMs)
        assertNull(t.close(130_000))
    }

    @Test fun nothingPlayingEndsTheSong() {
        val t = PlayTracker()
        t.at("a", true, 0)
        assertEquals(10_000, t.at(null, false, 10_000)!!.listenedMs)
        assertNull(t.close(20_000))
    }

    // --- SongKey ---

    @Test fun versionsOfATitleShareASong() {
        val k = SongKey.of("Three Ravens", "Example Band")
        assertEquals(k, SongKey.of("Three Ravens - Remastered 2011", "Example Band"))
        assertEquals(k, SongKey.of("Three Ravens (feat. Someone)", "Example Band, Someone"))
        assertEquals(k, SongKey.of("three ravens", "EXAMPLE BAND & Friend"))
    }

    @Test fun differentSongsStayApart() {
        assertNotEquals(SongKey.of("Three Ravens", "Example Band"), SongKey.of("Three Ravens", "Other Band"))
        assertNotEquals(SongKey.of("Three Ravens", "Example Band"), SongKey.of("Two Ravens", "Example Band"))
        // A dash that isn't a version marker is part of the title.
        assertNotEquals(SongKey.of("Part One - The Beginning", "Example Band"), SongKey.of("Part One", "Example Band"))
    }

    // --- History ---

    @Test fun historyOffRecordsNothingAnywhere() {
        for (w in Where.entries) assertFalse(History.records(Mode.OFF, w, 0, 1_000))
    }

    @Test fun eachModeHasExactlyOneRecorder() {
        assertTrue(History.records(Mode.WHILE_OPEN, Where.SCREEN, 0, 1_000))
        assertFalse(History.records(Mode.WHILE_OPEN, Where.BACKGROUND, 0, 1_000))
        assertTrue(History.records(Mode.ALWAYS, Where.BACKGROUND, 0, 1_000))
        assertFalse(History.records(Mode.ALWAYS, Where.SCREEN, 0, 1_000))
    }

    @Test fun pauseStopsRecordingUntilItEnds() {
        assertFalse(History.records(Mode.ALWAYS, Where.BACKGROUND, 5_000, 1_000))
        assertTrue(History.records(Mode.ALWAYS, Where.BACKGROUND, 5_000, 5_000))
        assertFalse(History.records(Mode.ALWAYS, Where.BACKGROUND, History.PAUSED_UNTIL_ON, Long.MAX_VALUE - 1))
    }

    @Test fun unknownModeIsOff() {
        assertEquals(Mode.OFF, Mode.of(null))
        assertEquals(Mode.OFF, Mode.of("something"))
    }
}
