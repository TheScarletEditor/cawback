package com.thescarletcoder.lyrics

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** "Not this song": offered only for a hand-picked version, and it asks first only when timing changes would go. */
class UndoPickTest {
    @Test fun onlyForAVersionPickedByHand() {
        assertTrue(UndoPick.offered(pinned = true))
        assertFalse(UndoPick.offered(pinned = false))
    }

    @Test fun undoesAtOnceWhenNothingWouldBeLost() {
        assertFalse(UndoPick.asksFirst(offsetMs = 0, anchors = 0, skips = 0))
    }

    @Test fun asksFirstWhenTimingChangesWouldGo() {
        assertTrue(UndoPick.asksFirst(offsetMs = 250, anchors = 0, skips = 0))
        assertTrue(UndoPick.asksFirst(offsetMs = -250, anchors = 0, skips = 0))
        assertTrue(UndoPick.asksFirst(offsetMs = 0, anchors = 2, skips = 0))
        assertTrue(UndoPick.asksFirst(offsetMs = 0, anchors = 0, skips = 1))
    }
}
