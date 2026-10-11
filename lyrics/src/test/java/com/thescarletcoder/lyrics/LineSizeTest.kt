package com.thescarletcoder.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** One size table for the lyrics and the Settings preview (0.4.1, Q2 and Q7). */
class LineSizeTest {
    @Test fun everySizeAndLayoutComesFromTheTable() {
        LyricsSettings.TEXT_SCALE.forEachIndexed { i, k ->
            assertEquals(26f * k to 34f * k, LyricsSettings.lineSp(compact = false, textSize = i))
            assertEquals(18f * k to 24f * k, LyricsSettings.lineSp(compact = true, textSize = i))
        }
        assertEquals(26f to 34f, LyricsSettings.lineSp(compact = false, textSize = 1))   // Medium is the base size
    }

    @Test fun eachStepIsBiggerThanTheLast() {
        listOf(false, true).forEach { compact ->
            val sizes = LyricsSettings.TEXT_SIZES.indices.map { LyricsSettings.lineSp(compact, it).first }
            assertTrue(sizes.zipWithNext().all { (a, b) -> b > a })
        }
    }

    @Test fun aSavedStepOutOfRangeStillDraws() {
        // A value from a newer or damaged settings file never crashes the lyrics or the preview.
        assertEquals(LyricsSettings.lineSp(false, 0), LyricsSettings.lineSp(false, -1))
        assertEquals(LyricsSettings.lineSp(false, 3), LyricsSettings.lineSp(false, 9))
    }
}
