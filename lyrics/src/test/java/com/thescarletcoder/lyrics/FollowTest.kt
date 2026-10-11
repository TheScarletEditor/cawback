package com.thescarletcoder.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The current line goes to the middle, and a recent hand scroll is never overridden (0.4.1, Q3). */
class FollowTest {
    @Test fun aLineLandsInTheMiddle() {
        // An 80 px line whose top is at 900 in a 0..1000 viewport: its centre (940) goes to 500.
        assertEquals(440f, Follow.delta(900, 80, 0, 1000, 0.5f), 0.001f)
        // Already in the middle: nothing to scroll.
        assertEquals(0f, Follow.delta(460, 80, 0, 1000, 0.5f), 0.001f)
        // A viewport that doesn't start at 0 (content padding): the middle is of the viewport, not the screen.
        assertEquals(0f, Follow.delta(560, 80, 100, 1100, 0.5f), 0.001f)
    }

    @Test fun narrowerSplitStillCentres() {
        // The same line after the split is made shorter (1000 to 400 px) and the text wrapped to two lines (160 px).
        val d = Follow.delta(300, 160, 0, 400, 0.5f)
        assertEquals(180f, d, 0.001f)
        assertEquals(200f, 300 - d + 160 / 2f, 0.001f)   // centre lands at 200, the middle of 400
    }

    @Test fun bothLayoutsDefaultToTheMiddle() {
        assertEquals(0.5f, LyricsTuning.defaultOf(LyricsTuning.ANCHOR), 0f)
        assertEquals(0.5f, LyricsTuning.defaultOf(LyricsTuning.ANCHOR_COMPACT), 0f)
    }

    @Test fun aHandScrollIsLeftAloneForTheFollowDelay() {
        val touched = 10_000L
        assertFalse(Follow.now(dragged = true, nowMs = 60_000L, lastTouchMs = 0L, delaySec = 4))   // dragging now
        assertFalse(Follow.now(dragged = false, nowMs = touched + 3_999L, lastTouchMs = touched, delaySec = 4))
        assertFalse(Follow.now(dragged = false, nowMs = touched + 4_000L, lastTouchMs = touched, delaySec = 4))
        assertTrue(Follow.now(dragged = false, nowMs = touched + 4_001L, lastTouchMs = touched, delaySec = 4))
        assertTrue(Follow.now(dragged = false, nowMs = touched + 8_001L, lastTouchMs = touched, delaySec = 8))
    }
}
