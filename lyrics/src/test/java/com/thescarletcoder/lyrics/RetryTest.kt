package com.thescarletcoder.lyrics

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException

/** A dropped connection is tried again before Lyrics gives up; "not found" is an answer and never retried. */
class RetryTest {
    private val quick = listOf(1L, 1L)

    @Test fun aDroppedConnectionIsTriedAgain() = runBlocking {
        var calls = 0
        val got = LyricsRepo.withRetries(quick) { calls++; if (calls < 3) throw IOException("dropped") else "lyrics" }
        assertEquals("lyrics", got)
        assertEquals(3, calls)
    }

    @Test fun threeFailuresGiveUp() {
        var calls = 0
        assertThrows(IOException::class.java) {
            runBlocking { LyricsRepo.withRetries(quick) { calls++; throw IOException("down") } }
        }
        assertEquals(3, calls)
    }

    @Test fun notFoundIsNeverRetried() {
        var calls = 0
        assertThrows(LrcLib.NotFound::class.java) {
            runBlocking { LyricsRepo.withRetries(quick) { calls++; throw LrcLib.NotFound() } }
        }
        assertEquals(1, calls)
    }
}
