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

    @Test fun endComesAfterTheLastSungLine() {
        // Record that stops early: last words at 2:13, then only a blank at 3:14.
        val early = Lrc.withEnd(Lrc.parseSynced("[00:10.00]one\n[00:20.00]\n[02:13.00]two\n[03:14.00]"))!!
        assertEquals(listOf(10_000L, 20_000L, 133_000L, 143_000L), early.map { it.timeMs })
        assertEquals(3, Lrc.indexAt(early, 175_000))
        assertEquals(2, Lrc.indexAt(early, 140_000))
        // A blank soon after the last line ends it there; no trailing blank still gets an end.
        assertEquals(64_000L, Lrc.withEnd(Lrc.parseSynced("[01:00.00]one\n[01:04.00]\n[01:30.00]"))!!.last().timeMs)
        assertEquals(listOf("one", ""), Lrc.withEnd(Lrc.parseSynced("[01:00.00]one"))!!.map { it.text })
        // Nothing sung: nothing to end.
        assertEquals(null, Lrc.withEnd(Lrc.parseSynced("[00:05.00]\n[00:09.00]")))
        assertEquals(null, Lrc.withEnd(emptyList()))
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
        assertEquals(2L, Matching.choose(null, listOf(a, b, c), durationSec = 200.0)?.candidate?.id)
        // Nothing within 10 seconds: no confident match.
        assertEquals(null, Matching.choose(null, listOf(c), durationSec = 200.0))
    }
}
