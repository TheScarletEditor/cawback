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
        assertNull(ProofOfWork.solve("p", "000000" + "FF".repeat(29), cancelled = { true }))
    }

    @Test fun aChallengeTheDeviceCantFinishIsRefused() {
        assertNull(ProofOfWork.solve("p", "00".repeat(32)))                         // far too hard: refused at once
        assertEquals(33, ProofOfWork.zeroBits(ProofOfWork.run { ByteArray(32) { if (it < 4) 0 else 0x7F } }))
        assertEquals(8, ProofOfWork.zeroBits(byteArrayOf(0x00, 0xFF.toByte())))
        var t = 0L
        assertNull(ProofOfWork.solve("p", "00000000" + "FF".repeat(28), now = { t.also { t += 60_000 } }))   // time's up
    }

    @Test fun atMostComparesUnsignedBytesLikeTheServer() {
        val t = byteArrayOf(0x10, 0x00)
        assertTrue(ProofOfWork.atMost(byteArrayOf(0x0F, 0x7F), t))
        assertTrue(ProofOfWork.atMost(byteArrayOf(0x10, 0x00), t))   // equal counts
        assertFalse(ProofOfWork.atMost(byteArrayOf(0x10, 0x01), t))
        assertFalse(ProofOfWork.atMost(byteArrayOf(0x80.toByte(), 0x00), byteArrayOf(0x7F, 0x00)))   // 0x80 is 128, not -128
    }

    @Test fun whatIsSentIsTheSongAsTheFixedVersionNamesItWithYourTiming() {
        // The player calls it "Song (feat. Guest)" by "Artist, Guest" on "Album", 248.4 s: none of that is sent.
        val c = Candidate(7, "Song", "Artist", "Other album", 249.0, false, "line one\nline two", "[00:06.10] line one\n[00:09.00] line two")
        val timed = YourTiming.apply(Lrc.parseSynced(c.synced!!), 0, listOf(Anchor(6_100, -1_000)))   // a tapped first line
        val body = ShareFix.body(c, timed)
        // The version's own names and length, so LRCLIB files the fix under that version (owner, 0.5.1 Q47).
        assertEquals("Song", body.getString("trackName"))
        assertEquals("Artist", body.getString("artistName"))
        assertEquals("Other album", body.getString("albumName"))
        assertEquals(249.0, body.getDouble("duration"), 0.001)
        assertEquals("line one\nline two", body.getString("plainLyrics"))
        assertEquals("[00:07.10] line one\n[00:10.00] line two", body.getString("syncedLyrics"))
    }

    @Test fun aVersionWithoutAnAlbumOrLengthIsSentAsLrclibHasIt() {
        val c = Candidate(8, "Song", "Artist", "", 0.0, false, null, "[00:01.00] a")
        val body = ShareFix.body(c, Lrc.parseSynced(c.synced!!))
        assertEquals("", body.getString("albumName"))   // as LRCLIB has it, so it still finds the same version
        assertEquals(0.0, body.getDouble("duration"), 0.001)   // not the song's length: that would miss the version
    }

    @Test fun onlyANamedVersionCanBeShared() {
        val c = Candidate(9, "Song", "Artist", "", 200.0, false, null, "[00:01.00] a")
        assertEquals(true, ShareFix.shareable(c))                       // no album is fine
        assertEquals(false, ShareFix.shareable(c.copy(track = " ")))
        assertEquals(false, ShareFix.shareable(c.copy(artist = "")))
    }

    @Test fun aMissingAlbumIsEmptyNotTheWordNull() {
        val c = LrcLib.parse(org.json.JSONObject("""{"id":9,"trackName":"Song","artistName":"Artist","albumName":null,"duration":200}"""))
        assertEquals("", c.album)
        assertEquals("", ShareFix.body(c, emptyList()).getString("albumName"))
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

    @Test fun aSendThatWentOutStillSaysSoAfterCancel() = runBlocking {
        val app = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val runner = WorkRunner(app)
        val settled = CompletableDeferred<Unit>()
        val sent = WorkRunner.Result(true, "Sent.")
        assertTrue(runner.run("share") { runner.settle("share", sent); settled.complete(Unit); delay(60_000); sent })
        settled.await()
        runner.cancel("share")
        withTimeout(5_000) { while (runner.busy("share")) delay(5) }
        assertEquals(sent, runner.result("share"))
        // And after an error once it went out (the "Sent on" mark couldn't be saved).
        assertTrue(runner.run("share") { runner.settle("share", sent); error("store") })
        withTimeout(5_000) { while (runner.busy("share")) delay(5) }
        assertEquals(sent, runner.result("share"))
        app.cancel()
    }
}
