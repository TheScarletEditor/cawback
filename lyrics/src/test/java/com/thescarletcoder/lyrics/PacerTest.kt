package com.thescarletcoder.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** LRCLIB's client rules (lrclib.net/docs): one request at a time, a short gap, and Retry-After honored. */
class PacerTest {
    @Test fun requestsAreSpacedOut() {
        var t = 1_000L
        val p = Pacer { t }
        assertEquals(0L, p.slot())
        assertEquals(Pacer.GAP_MS, p.slot())          // the next one waits its turn
        assertEquals(2 * Pacer.GAP_MS, p.slot())
        t += 10_000
        assertEquals(0L, p.slot())                    // long after: no wait
    }

    @Test fun nothingIsSentUntilRetryAfterHasPassed() {
        var t = 0L
        val p = Pacer { t }
        p.backOff(5_000)
        assertEquals(5_000L, assertThrows(LrcLib.TooBusy::class.java) { p.slot() }.waitMs)
        t = 4_999
        assertThrows(LrcLib.TooBusy::class.java) { p.slot() }
        t = 5_000
        assertEquals(0L, p.slot())
    }

    @Test fun retryAfterIsReadCarefully() {
        assertEquals(7_000L, Pacer.retryAfterMs("7", 429))
        assertEquals(30_000L, Pacer.retryAfterMs(null, 429))
        assertEquals(30_000L, Pacer.retryAfterMs("Wed, 21 Oct 2026 07:28:00 GMT", 429))   // a date: the careful default
        assertEquals(1_000L, Pacer.retryAfterMs(null, 503))
        assertEquals(1_000L, Pacer.retryAfterMs("0", 503))                               // never a busy loop
        assertEquals(3_600_000L, Pacer.retryAfterMs("999999", 429))                       // capped at an hour
    }
}
