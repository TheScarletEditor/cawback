package com.thescarletcoder.lyrics

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The optional cleanup of saved lyrics (0.5.1, owner Q48 and Q56). */
class UnusedLyricsTest {
    private val day = 86_400_000L
    private val now = 1_000 * day
    private val c = Candidate(1, "Song", "Artist", "Album", 200.0, false, null, "[00:01.00] a")

    @Test fun onlyAfterTheDaysHavePassed() {
        assertFalse(LyricsStore.unused(lastUsed = now - 90 * day, trackedSince = 0, now = now, days = 90))
        assertTrue(LyricsStore.unused(lastUsed = now - 91 * day, trackedSince = 0, now = now, days = 90))
    }

    @Test fun nothingCountsFromBeforeTrackingBegan() {
        // A song last written long ago but saved before 0.5.1 noted use: its days start when tracking began.
        assertFalse(LyricsStore.unused(lastUsed = now - 400 * day, trackedSince = now - 10 * day, now = now, days = 90))
        assertTrue(LyricsStore.unused(lastUsed = now - 400 * day, trackedSince = now - 91 * day, now = now, days = 90))
    }

    @Test fun yourPicksAndTimingAreAlwaysKept() {
        val plain = LyricsStore.Entry(c, pinned = false, offsetMs = 0, fetchedAt = 0)
        assertFalse(LyricsStore.isYours(plain))
        assertTrue(LyricsStore.isYours(plain.copy(pinned = true)))
        assertTrue(LyricsStore.isYours(plain.copy(offsetMs = 250)))
        assertTrue(LyricsStore.isYours(plain.copy(anchors = listOf(Anchor(1_000, 500)))))
        assertTrue(LyricsStore.isYours(plain.copy(tapped = true)))   // lined up by ear, landing on no nudge at all
        assertTrue(LyricsStore.isYours(plain.copy(sentAt = 5)))
        assertTrue(LyricsStore.isYours(LyricsStore.Entry(null, pinned = true, offsetMs = 0, fetchedAt = 0, pendingId = 7)))
    }
}
