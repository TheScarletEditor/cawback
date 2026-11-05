package com.thescarletcoder.lyrics

import com.thescarletcoder.lyrics.FixWindow.Start
import com.thescarletcoder.lyrics.FixWindow.Where
import org.junit.Assert.assertEquals
import org.junit.Test

/** D-062: where Line up now and Fix lines run, and when the full-screen window starts or gives up. */
class FixWindowTest {
    private val song = "Glass Harbor\u0001The Lanterns"
    private val other = "Paper Saints\u0001Mara Vell"

    @Test fun outsideSplitScreenNothingChanges() {
        // Full screen, landscape (short) included: fix right here, as before.
        listOf(300f, 411f, 559f, 560f, 900f).forEach { h ->
            assertEquals(Where.HERE, FixWindow.where(inSplit = false, fullScreenWindow = false, heightDp = h))
            assertEquals(Where.HERE, FixWindow.where(inSplit = false, fullScreenWindow = true, heightDp = h))
        }
    }

    @Test fun fromSplitScreenTheToolsOpenFullScreen() {
        // However tall the half is: the owner's ask was out of split screen.
        listOf(0f, 300f, 700f).forEach { h -> assertEquals(Where.FULL_SCREEN, FixWindow.where(true, false, h)) }
    }

    @Test fun whereAndroidKeepsTheSplitTheWindowAsksForRoom() {
        assertEquals(Where.NEEDS_ROOM, FixWindow.where(true, true, 300f))
        assertEquals(Where.NEEDS_ROOM, FixWindow.where(true, true, 559f))
        // A tall enough half (a tablet's): fix there.
        assertEquals(Where.HERE, FixWindow.where(true, true, FixWindow.MIN_HEIGHT_DP))
        assertEquals(Where.HERE, FixWindow.where(true, true, 800f))
    }

    @Test fun startsOnlyForTheSongItWasOpenedForOnceSyncedLyricsShow() {
        assertEquals(Start.NOW, FixWindow.start(song, song, loaded = true, synced = true))
        assertEquals(Start.WAIT, FixWindow.start(song, song, loaded = false, synced = false))
    }

    @Test fun waitsOutABlankMomentBetweenPlayerUpdates() {
        assertEquals(Start.WAIT, FixWindow.start(song, null, loaded = false, synced = false))
    }

    @Test fun givesUpRatherThanFixTheWrongThing() {
        // Another song came on while the window opened: left alone, the window closes.
        assertEquals(Start.GIVE_UP, FixWindow.start(song, other, loaded = true, synced = true))
        assertEquals(Start.GIVE_UP, FixWindow.start(song, other, loaded = false, synced = false))
        // Nothing playing any more (shown after the blank moment), words only, a miss or offline.
        assertEquals(Start.GIVE_UP, FixWindow.start(song, null, loaded = true, synced = false))
        assertEquals(Start.GIVE_UP, FixWindow.start(song, song, loaded = true, synced = false))
    }
}
