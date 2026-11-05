package com.thescarletcoder.lyrics

/**
 * Line up now and Fix lines, opened from split screen in a window of their own (owner, D-062). The manifest marks it
 * as not resizable and gives it its own task, so phones leave split screen to show it full size. Where Android keeps
 * the split anyway, the screen asks for room (FixWindow.Where.NEEDS_ROOM). It closes when the tool does, and the
 * lyrics screen it was opened from reads the song's timing again.
 */
class FixLinesActivity : LyricsActivity() {
    override val fixOnly: Boolean get() = true

    companion object {
        /** The tool, a FixMode name (LINE_UP or FIX). */
        const val EXTRA_MODE = "com.thescarletcoder.lyrics.FIX_MODE"
        /** The song it's for: title and artist, as the lyrics screen keys a song. */
        const val EXTRA_SONG = "com.thescarletcoder.lyrics.FIX_SONG"
        /** Opened from split screen: closing it brings your music app back beside Cawback. */
        const val EXTRA_FROM_SPLIT = "com.thescarletcoder.lyrics.FIX_FROM_SPLIT"
    }
}
