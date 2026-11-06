package com.thescarletcoder.lyrics

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** D-062 review: a listen ends when Cawback leaves the screen, not when the fixing window opens or closes. */
class OpenScreensTest {
    @Test fun oneScreenOnAndOff() {
        val s = OpenScreens()
        s.start()
        assertTrue(s.stop())
    }

    @Test fun openingTheFixingWindowIsNotLeaving() {
        val s = OpenScreens()
        s.start()             // the lyrics screen
        s.start()             // the fixing window starts before the lyrics screen stops
        assertFalse(s.stop()) // the lyrics screen stops: still open
        s.start()             // Done: the lyrics screen starts again before the window stops
        assertFalse(s.stop()) // the window stops
        assertTrue(s.stop())  // Home: now Cawback is off screen
    }

    @Test fun neverBelowZero() {
        val s = OpenScreens()
        assertTrue(s.stop())
        s.start()
        assertTrue(s.stop())
    }
}
