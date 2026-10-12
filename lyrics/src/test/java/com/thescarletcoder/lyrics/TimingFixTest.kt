package com.thescarletcoder.lyrics

import com.thescarletcoder.lyrics.library.LibraryBackup
import com.thescarletcoder.lyrics.library.LibrarySnapshot
import com.thescarletcoder.lyrics.library.TimingFix
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/** The user's timing in the Library backup (0.5.0, owner Q25 and the slice 6 answers). Synthetic data only. */
class TimingFixTest {
    private val key = "a".repeat(64)
    private val fix = TimingFix(key, versionId = 42, pinned = true, offsetMs = 250, anchors = listOf(Anchor(30_000, -400)), song = "song|artist")
    private val c42 = Candidate(42, "Song", "Artist", "Album", 200.0, false, "words", "[00:10.00] line 0")

    @Test fun aBackupCarriesTheFixesAndNoLyrics() {
        val snap = LibrarySnapshot(fixes = listOf(fix))
        val json = snap.toJson()
        assertEquals(2, json.getInt("schema"))
        assertEquals(listOf(fix), LibrarySnapshot.fromJson(json).fixes)
        assertTrue("no lyric text", !json.toString().contains("line 0"))
        // Through the real passphrase backup too.
        val out = ByteArrayOutputStream()
        LibraryBackup.write(out, snap, "correct horse".toCharArray(), params = LibraryBackup.Params(memoryKiB = 8, passes = 1, lanes = 1))
        assertEquals(listOf(fix), LibraryBackup.read(ByteArrayInputStream(out.toByteArray()), "correct horse".toCharArray()).fixes)
    }

    @Test fun anOldBackupStillReadsAndADamagedFixIsLeftOutOnItsOwn() {
        val old = JSONObject("""{"schema":1,"songs":[],"plays":[]}""")
        assertEquals(emptyList<TimingFix>(), LibrarySnapshot.fromJson(old).fixes)
        val json = LibrarySnapshot(fixes = listOf(fix)).toJson()
        json.getJSONArray("fixes").put(JSONObject().put("key", "not a hash").put("id", 1)).put(JSONObject().put("key", key))
        assertEquals(listOf(fix), LibrarySnapshot.fromJson(json).fixes)
    }

    @Test fun aCraftedBackupIsCappedAndClamped() {
        val json = LibrarySnapshot().toJson()
        val arr = org.json.JSONArray()
        arr.put("not an object")   // skipped on its own, not the whole restore
        arr.put(JSONObject().put("key", key).put("id", 1).put("offset", Long.MAX_VALUE)
            .put("anchors", org.json.JSONArray().put(org.json.JSONArray().put(5_000).put(Long.MIN_VALUE)).put(org.json.JSONArray().put(-1).put(0))))
        arr.put(JSONObject().put("key", key).put("id", 2))   // a second fix for the same song is dropped
        json.put("fixes", arr)
        val f = LibrarySnapshot.fromJson(json).fixes.single()
        assertEquals(1L, f.versionId)
        assertEquals(YourTiming.LIMIT_MS, f.offsetMs)
        assertEquals(listOf(Anchor(5_000, -2 * YourTiming.LIMIT_MS)), f.anchors)   // clamped; a negative line time left out
    }

    @Test fun aForgottenSongGetsNoFixBack() {
        val other = fix.copy(key = "b".repeat(64))
        assertEquals(listOf(other), LibrarySnapshot.toRestore(listOf(fix, other), forgotten = setOf(key)))
    }

    @Test fun onlyRealFixesAreBackedUp() {
        val plain = LyricsStore.Entry(c42, pinned = false, offsetMs = 0, fetchedAt = 1)
        assertNull(LyricsStore.fixOf(key, plain, null))   // an automatic match with no timing isn't a fix
        assertEquals(fix.copy(song = null), LyricsStore.fixOf(key, plain.copy(pinned = true, offsetMs = 250, anchors = fix.anchors), null))
    }

    @Test fun theDevicesOwnTimingWins() {
        val mine = LyricsStore.Entry(c42, pinned = false, offsetMs = 750, fetchedAt = 1)
        assertNull(LyricsStore.merged(mine, fix))                                   // timing here: kept
        assertNull(LyricsStore.merged(mine.copy(offsetMs = 0, pinned = true), fix)) // a pick here: kept
        // The same version here with no timing: the timing is added at once.
        val same = LyricsStore.merged(mine.copy(offsetMs = 0), fix)!!
        assertEquals(c42, same.candidate); assertEquals(250L, same.offsetMs); assertEquals(fix.anchors, same.anchors)
        assertTrue(LyricsRepo.isCurrent(same))   // not matched again on the next play
        // Another version or nothing here: the version is fetched by id on the next play.
        val other = LyricsStore.merged(mine.copy(offsetMs = 0, candidate = c42.copy(id = 7)), fix)!!
        assertEquals(42L, other.pendingId); assertNull(other.candidate); assertTrue(other.pinned)
        assertEquals(42L, LyricsStore.merged(null, fix)!!.pendingId)
    }

    @Test fun aPendingFixSurvivesTheCacheFormat() {
        val pending = LyricsStore.merged(null, fix)!!
        assertEquals(pending, LyricsStore.decode(LyricsStore.encode(pending)))
        assertEquals(fix.copy(song = null), LyricsStore.fixOf(key, pending, null))   // and backs up again as it is
    }
}
