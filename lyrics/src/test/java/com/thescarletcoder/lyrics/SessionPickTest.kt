package com.thescarletcoder.lyrics

import com.thescarletcoder.lyrics.SessionPick.Rules
import com.thescarletcoder.lyrics.SessionPick.Session
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Which app Lyrics follows (0.3 "Your player"): the chosen one, never an ignored one. */
class SessionPickTest {
    private val spotify = Session("com.spotify.music", playing = false)
    private val podcasts = Session("com.example.podcasts", playing = true)
    private val ytm = Session("com.google.android.apps.youtube.music", playing = false)
    private val all = listOf(podcasts, spotify, ytm)

    @Test fun onlyTheChosenAppIsFollowed() {
        assertEquals(spotify, SessionPick.choose(all, Rules("com.spotify.music", onlyPlayer = true, ignored = emptySet())))
        // a podcast playing while Spotify is closed: nothing to follow, so nothing goes to lrclib.net
        assertNull(SessionPick.choose(listOf(podcasts), Rules("com.spotify.music", onlyPlayer = true, ignored = emptySet())))
    }

    @Test fun preferredAppFirstThenWhateverPlays() {
        val r = Rules("com.spotify.music", onlyPlayer = false, ignored = emptySet())
        assertEquals(spotify, SessionPick.choose(all, r))
        assertEquals(podcasts, SessionPick.choose(listOf(podcasts, ytm), r))
    }

    @Test fun ignoredAppsAreNeverFollowed() {
        val r = Rules(null, onlyPlayer = false, ignored = setOf("com.example.podcasts"))
        assertEquals(spotify, SessionPick.choose(all, r))   // the playing podcast app is skipped
        assertNull(SessionPick.choose(listOf(podcasts), r))
        // even if it's the chosen app
        assertNull(SessionPick.choose(listOf(podcasts), Rules("com.example.podcasts", onlyPlayer = true, ignored = setOf("com.example.podcasts"))))
    }

    @Test fun whateverIsPlaying() {
        assertEquals(podcasts, SessionPick.choose(all, Rules(null, onlyPlayer = false, ignored = emptySet())))
        assertEquals(spotify, SessionPick.choose(listOf(spotify, ytm), Rules(null, onlyPlayer = false, ignored = emptySet())))
    }
}
