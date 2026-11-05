package com.thescarletcoder.lyrics

/**
 * How many Cawback screens are started: the lyrics screen and the fixing window (D-062). Opening one hides the other,
 * but the new one starts before the old one stops, so the count only reaches 0 when Cawback really leaves the screen.
 * Plays recorded "only while Cawback is open" end then, not at the switch. Pure, so it is unit tested.
 */
class OpenScreens {
    private var started = 0

    fun start() { started++ }

    /** True when this was the last one: Cawback is off screen. */
    fun stop(): Boolean {
        started = (started - 1).coerceAtLeast(0)
        return started == 0
    }
}
