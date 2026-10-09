package com.thescarletcoder.lyrics

import org.junit.Assert.assertEquals
import org.junit.Test

/** Synthetic lyrics only. */
class LrcTest {
    @Test fun parsesAndSorts() {
        val l = Lrc.parseSynced("[00:05.50] second\n[00:01.00] first\n[ar:Nobody]\n\n[01:02.123]third")
        assertEquals(listOf(1000L, 5500L, 62123L), l.map { it.timeMs })
        assertEquals(listOf("first", "second", "third"), l.map { it.text })
    }

    @Test fun repeatedTimestampsAndBlankLines() {
        val l = Lrc.parseSynced("[00:10.00][00:30.00]la la\n[00:20.00]")
        assertEquals(listOf(10_000L, 20_000L, 30_000L), l.map { it.timeMs })
        assertEquals("", l[1].text)
    }

    @Test fun dropsWordTimingsAndAppliesOffset() {
        val l = Lrc.parseSynced("[offset:+500]\n[00:02.00]<00:02.00>one <00:02.40>two")
        assertEquals(1500L, l[0].timeMs)
        assertEquals("one two", l[0].text)
    }

    @Test fun indexAtFindsCurrentLine() {
        val l = Lrc.parseSynced("[00:01.00]a\n[00:03.00]b\n[00:06.00]c")
        assertEquals(-1, Lrc.indexAt(l, 500))
        assertEquals(0, Lrc.indexAt(l, 1000))
        assertEquals(1, Lrc.indexAt(l, 5999))
        assertEquals(2, Lrc.indexAt(l, 99_000))
    }

    @Test fun cleansTitles() {
        assertEquals("Song", Matching.cleanTitle("Song - Remastered 2011"))
        assertEquals("Song", Matching.cleanTitle("Song (feat. Someone)"))
        assertEquals("Song (Live)", Matching.cleanTitle("Song (Live)"))
        assertEquals("Song", Matching.cleanTitle("Song - 2009 Remaster"))
    }

    @Test fun picksClosestSyncedCandidate() {
        val a = Candidate(1, "Song", "Artist", "Album", 200.0, false, plain = "x", synced = null)
        val b = Candidate(2, "Song", "Artist", "Album", 201.0, false, plain = "x", synced = "[00:01.00]x")
        val c = Candidate(3, "Song", "Artist", "Album", 260.0, false, plain = "x", synced = "[00:01.00]x")
        assertEquals(2L, Matching.best(listOf(a, b, c), durationSec = 200.0)?.id)
        // Nothing within 10 seconds: no confident match.
        assertEquals(null, Matching.best(listOf(c), durationSec = 200.0)?.id)
    }
}
