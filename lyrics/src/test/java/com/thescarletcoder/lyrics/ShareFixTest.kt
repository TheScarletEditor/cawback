package com.thescarletcoder.lyrics

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Share your fix (0.5.0, owner Q23): LRCLIB's proof of work and exactly what's sent. Synthetic data only. */
class ShareFixTest {
    @Test fun theProofOfWorkMeetsLrclibsOwnCheck() {
        val easy = "0F" + "FF".repeat(31)          // about 1 in 16
        val harder = "00FF" + "FF".repeat(30)      // about 1 in 256
        listOf(easy, harder).forEach { target ->
            val nonce = ProofOfWork.solve("abcDEF123", target)!!
            assertTrue(ProofOfWork.verify("abcDEF123", target, nonce))
        }
        assertFalse(ProofOfWork.verify("abcDEF123", "00" + "00".repeat(31), "0"))   // nothing is at most all zeros (in practice)
        assertNull(ProofOfWork.solve("p", "not hex"))
    }

    @Test fun theProofOfWorkStopsWhenCancelled() {
        assertNull(ProofOfWork.solve("p", "00".repeat(32)) { true })
    }

    @Test fun atMostComparesUnsignedBytesLikeTheServer() {
        val t = byteArrayOf(0x10, 0x00)
        assertTrue(ProofOfWork.atMost(byteArrayOf(0x0F, 0x7F), t))
        assertTrue(ProofOfWork.atMost(byteArrayOf(0x10, 0x00), t))   // equal counts
        assertFalse(ProofOfWork.atMost(byteArrayOf(0x10, 0x01), t))
        assertFalse(ProofOfWork.atMost(byteArrayOf(0x80.toByte(), 0x00), byteArrayOf(0x7F, 0x00)))   // 0x80 is 128, not -128
    }

    @Test fun whatIsSentIsTheSongAsThePlayerNamesItWithYourTiming() {
        val t = Track("Song (feat. Guest)", "Artist, Guest", "Album", 248_400)
        val c = Candidate(7, "Song", "Artist", "Other album", 249.0, false, "line one\nline two", "[00:06.10] line one\n[00:09.00] line two")
        val timed = YourTiming.apply(Lrc.parseSynced(c.synced!!), 0, listOf(Anchor(6_100, -1_000)))   // a tapped first line
        val body = ShareFix.body(t, c, timed)
        assertEquals("Song (feat. Guest)", body.getString("trackName"))
        assertEquals("Artist, Guest", body.getString("artistName"))
        assertEquals("Album", body.getString("albumName"))
        assertEquals(248.4, body.getDouble("duration"), 0.001)
        assertEquals("line one\nline two", body.getString("plainLyrics"))
        assertEquals("[00:07.10] line one\n[00:10.00] line two", body.getString("syncedLyrics"))
    }

    @Test fun anInstrumentalGapKeepsItsTime() {
        assertEquals("[01:02.35] words\n[01:30.00]", ShareFix.lrc(listOf(LyricLine(62_350, "words"), LyricLine(90_000, ""), LyricLine(null, "untimed"))))
        // Rounding never makes a 60th second.
        assertEquals("[01:00.00] x\n[00:59.99] y", ShareFix.lrc(listOf(LyricLine(59_995, "x"), LyricLine(59_994, "y"))))
    }

    @Test fun tappedAndSentAreSaved() {
        val c = Candidate(7, "Song", "Artist", "Album", 248.0, false, "w", "[00:06.10] w")
        val e = LyricsStore.Entry(c, pinned = false, offsetMs = 0, fetchedAt = 1, anchors = listOf(Anchor(6_100, -1_000)), tapped = true, sentAt = 99)
        assertEquals(e, LyricsStore.decode(LyricsStore.encode(e)))
    }

    @Test fun aCancelledJobLeavesNoMessage() = runBlocking {
        val app = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val runner = WorkRunner(app)
        val started = CompletableDeferred<Unit>()
        assertTrue(runner.run("share") { started.complete(Unit); delay(60_000); WorkRunner.Result(true, "Sent.") })
        started.await()
        runner.cancel("share")
        withTimeout(5_000) { while (runner.busy("share")) delay(5) }
        assertNull(runner.result("share"))   // not "That didn't finish": the user stopped it
        app.cancel()
    }
}
