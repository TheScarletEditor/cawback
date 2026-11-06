package com.thescarletcoder.lyrics

/**
 * Back to split screen after the fixing window (D-062; PR #7 phone emulator run, 2026-11-06). On phones the split is
 * gone by then, so the lyrics screen rebuilds it by opening your music app beside itself, as "Cawback + your app" does.
 * It waits until its own window has focus: asked any earlier, while the fixing window is still closing, Android pairs
 * the music app with that closing window and Cawback's half stays black. The wait is short ([WAIT_MS]): focus that comes
 * later (the other half of a split had it, then you tap Cawback) doesn't open the music app out of the blue.
 * Pure, so it is unit tested.
 */
class SplitReturn(private val waitMs: Long = WAIT_MS) {
    private var askedAt: Long? = null
    private var whenOpened: String? = null

    /**
     * The fixing window is opening. [playing]: the app playing now, remembered for "whatever is playing", since right
     * after the window closes what's playing isn't known again yet. Only from split screen: otherwise nothing comes back.
     */
    fun opening(inSplit: Boolean, playing: String?) {
        whenOpened = if (inSplit) playing else null
        askedAt = null
    }

    /** The fixing window closed and wants the split back, at [nowMs]. True: the lyrics screen already has focus, open it now. */
    fun ask(focused: Boolean, nowMs: Long): Boolean {
        askedAt = if (focused) null else nowMs
        return focused
    }

    /** True once, when the lyrics screen's window gets focus soon enough after [ask]: open the music app beside it now. */
    fun onFocus(hasFocus: Boolean, nowMs: Long): Boolean {
        // Nothing asked: the remembered app stays. The phone may hand focus back for a moment while it leaves split screen
        // to show the fixing window (re-check of 50de844); it's cleared when used, when a request lapses, or outside split.
        val at = askedAt ?: return false
        if (nowMs - at > waitMs) { askedAt = null; whenOpened = null; return false }
        if (!hasFocus) return false
        askedAt = null
        return true
    }

    /** Cawback left the screen before it got focus: the music app doesn't open later. */
    fun onStop() { askedAt = null }

    /**
     * Which app opens beside Cawback: the one chosen in Settings ([chosen]), else the one playing when the fixing window
     * opened, else the one playing [now]. The remembered app is used once.
     */
    fun player(chosen: String?, now: String?): String? = (chosen ?: whenOpened ?: now).also { whenOpened = null }

    companion object {
        /** How long after the fixing window closes Cawback may still open the music app beside itself. */
        const val WAIT_MS = 5_000L
    }
}
