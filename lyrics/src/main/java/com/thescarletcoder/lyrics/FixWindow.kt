package com.thescarletcoder.lyrics

/**
 * Where Line up now and Fix lines run (owner, D-062): split screen leaves too little room to tap. From split screen
 * they open in their own full-screen window, which Android lets leave split screen. Where Android keeps the split
 * anyway (tablets ignore that, and some phones may too), that window says it needs the full screen and starts
 * fixing once it's big enough. Pure, so it is unit tested.
 */
object FixWindow {
    /** Shorter than this and the lyrics screen goes compact; in split screen it's too small to tap a line in time. */
    const val MIN_HEIGHT_DP = 560f

    enum class Where {
        /** Fix right here. */
        HERE,
        /** Open the full-screen window for it (from split screen). */
        FULL_SCREEN,
        /** In the full-screen window, but Android kept the split and it's too small: show how to make room. */
        NEEDS_ROOM,
    }

    /** [fullScreenWindow]: this is the window opened for fixing. [heightDp]: the lyrics area's height. */
    fun where(inSplit: Boolean, fullScreenWindow: Boolean, heightDp: Float): Where = when {
        !inSplit -> Where.HERE
        !fullScreenWindow -> Where.FULL_SCREEN
        heightDp < MIN_HEIGHT_DP -> Where.NEEDS_ROOM
        else -> Where.HERE
    }

    enum class Start {
        /** Not yet: the song or its lyrics are still on the way. */
        WAIT,
        /** Start the tool it was opened for. */
        NOW,
        /** Close the window without fixing: the song changed on the way, it stopped, or it has no synced lyrics. */
        GIVE_UP,
    }

    /**
     * In the full-screen window: whether the tool asked for can start. Only for the song it was asked for, and only once
     * its synced lyrics show, as in the This song sheet. [loaded]: something shows for what's playing (lyrics, a miss,
     * offline or nothing playing), so a blank moment between player updates is waited out.
     */
    fun start(askedFor: String, playing: String?, loaded: Boolean, synced: Boolean): Start = when {
        playing == null -> if (loaded) Start.GIVE_UP else Start.WAIT
        playing != askedFor -> Start.GIVE_UP
        !loaded -> Start.WAIT
        synced -> Start.NOW
        else -> Start.GIVE_UP
    }
}
