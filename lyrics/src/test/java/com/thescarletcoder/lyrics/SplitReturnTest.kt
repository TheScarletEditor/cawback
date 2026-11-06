package com.thescarletcoder.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** PR #7 phone emulator run (2026-11-06): after Done or Back, the music app opens beside Cawback only once Cawback has focus. */
class SplitReturnTest {
    @Test fun waitsForFocusThenOpensOnce() {
        val s = SplitReturn()
        assertFalse(s.ask(focused = false))   // the fixing window is still closing on top
        assertFalse(s.onFocus(false))
        assertTrue(s.onFocus(true))           // the lyrics screen has focus: open the music app now
        assertFalse(s.onFocus(false))
        assertFalse(s.onFocus(true))          // later focus changes (a sheet, a dialog) don't open it again
    }

    @Test fun alreadyFocusedOpensAtOnce() {
        val s = SplitReturn()
        assertTrue(s.ask(focused = true))
        assertFalse(s.onFocus(true))
    }

    @Test fun leavingFirstCancelsIt() {
        val s = SplitReturn()
        s.ask(focused = false)
        s.onStop()                            // left Cawback before it showed
        assertFalse(s.onFocus(true))          // coming back later doesn't open the music app out of the blue
    }

    @Test fun whateverIsPlayingUsesTheAppFromBefore() {
        assertEquals("chosen.app", SplitReturn.player("chosen.app", "before.app", "now.app"))
        assertEquals("before.app", SplitReturn.player(null, "before.app", null))   // not known again yet
        assertEquals("now.app", SplitReturn.player(null, null, "now.app"))
        assertEquals(null, SplitReturn.player(null, null, null))
    }
}
