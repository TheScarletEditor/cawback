package com.thescarletcoder.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** PR #7 phone emulator run (2026-11-06): after Done or Back, the music app opens beside Cawback once Cawback has focus. */
class SplitReturnTest {
    @Test fun waitsForFocusThenOpensOnce() {
        val s = SplitReturn()
        assertFalse(s.ask(focused = false, nowMs = 1_000))   // the fixing window is still closing on top
        assertFalse(s.onFocus(false, 1_200))
        assertTrue(s.onFocus(true, 1_500))                   // the lyrics screen has focus: open the music app now
        assertFalse(s.onFocus(false, 1_600))
        assertFalse(s.onFocus(true, 1_700))                  // later focus changes (a sheet, a dialog) don't open it again
    }

    @Test fun alreadyFocusedOpensAtOnce() {
        val s = SplitReturn()
        assertTrue(s.ask(focused = true, nowMs = 0))
        assertFalse(s.onFocus(true, 10))
    }

    @Test fun leavingFirstCancelsIt() {
        val s = SplitReturn()
        s.ask(focused = false, nowMs = 0)
        s.onStop()                                           // Home before Cawback showed
        assertFalse(s.onFocus(true, 100))                    // coming back doesn't open the music app
    }

    @Test fun focusThatComesLateDoesNotOpenIt() {
        // Review of deeaeba: in a split that stays (tablets) the other half may keep focus; tapping Cawback a minute
        // later must not open the music app.
        val s = SplitReturn()
        s.opening(inSplit = true, playing = "before.app")
        s.ask(focused = false, nowMs = 0)
        assertFalse(s.onFocus(true, SplitReturn.WAIT_MS + 1))
        assertFalse(s.onFocus(true, 60_000))
        assertNull(s.player(null, null))                     // the remembered app went with it
    }

    @Test fun whateverIsPlayingUsesTheAppFromBefore() {
        val s = SplitReturn()
        s.opening(inSplit = true, playing = "before.app")
        assertEquals("chosen.app", SplitReturn().apply { opening(true, "before.app") }.player("chosen.app", "now.app"))
        assertEquals("before.app", s.player(null, null))     // not known again yet
        assertNull(s.player(null, null))                     // used once
        assertEquals("now.app", s.player(null, "now.app"))
    }

    @Test fun nothingIsRememberedOutsideSplitOrOnceBackOnScreen() {
        val s = SplitReturn()
        s.opening(inSplit = false, playing = "before.app")
        assertNull(s.player(null, null))
        s.opening(inSplit = true, playing = "before.app")
        s.onFocus(true, 0)                                   // back on screen with nothing asked
        assertNull(s.player(null, null))
    }
}
