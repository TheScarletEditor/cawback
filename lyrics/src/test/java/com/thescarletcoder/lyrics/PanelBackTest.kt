package com.thescarletcoder.lyrics

import org.junit.Assert.assertEquals
import org.junit.Test

/** One step back from each page: never a page with no way out, never past the lyrics. */
class PanelBackTest {
    @Test fun eachPageGoesBackOneStep() {
        assertEquals(Panel.SETTINGS, Panel.TUNING.back(false))
        assertEquals(Panel.LIBRARY, Panel.EXPORT.back(false))
        assertEquals(Panel.LIBRARY, Panel.SONG.back(songFromLibrary = true))
        assertEquals(Panel.NONE, Panel.SONG.back(songFromLibrary = false))
        listOf(Panel.PICKER, Panel.SETTINGS, Panel.LIBRARY, Panel.NONE).forEach { assertEquals(Panel.NONE, it.back(true)) }
    }

    @Test fun everyPageReachesTheLyrics() {
        Panel.entries.forEach { start ->
            var p = start; var steps = 0
            while (p != Panel.NONE) { p = p.back(true); steps++ }
            assert(steps <= 2) { "$start takes $steps steps" }
        }
    }
}
