package com.thescarletcoder.lyrics

/**
 * Back to split screen after the fixing window (D-062; PR #7 phone emulator run, 2026-11-06). On phones the split is
 * gone by then, so the lyrics screen rebuilds it by opening your music app beside itself, as "Cawback + your app" does.
 * It waits until its own window has focus: asked any earlier, while the fixing window is still closing, Android pairs
 * the music app with that closing window and Cawback's half stays black. Pure, so it is unit tested.
 */
class SplitReturn {
    private var asked = false

    /** The fixing window closed and wants the split back. True: the lyrics screen already has focus, so open it now. */
    fun ask(focused: Boolean): Boolean {
        asked = !focused
        return focused
    }

    /** True once, the first time the lyrics screen's window gets focus after [ask]: open the music app beside it now. */
    fun onFocus(hasFocus: Boolean): Boolean {
        if (!hasFocus || !asked) return false
        asked = false
        return true
    }

    /** Cawback left the screen before it got focus: the music app doesn't open later, out of the blue. */
    fun onStop() { asked = false }

    companion object {
        /**
         * Which app opens beside Cawback: the one chosen in Settings, else (with "whatever is playing") the one that was
         * playing when the fixing window opened, else the one playing now. Right after the window closes, what's
         * playing isn't known again yet, so the app from before is remembered.
         */
        fun player(chosen: String?, whenOpened: String?, now: String?): String? = chosen ?: whenOpened ?: now
    }
}
