package com.thescarletcoder.lyrics

import com.thescarletcoder.lyrics.library.LibrarySnapshot
import com.thescarletcoder.lyrics.library.TimingFix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Lines marked "not in this song" (0.5.1, owner Q53 and Q57). Made-up words. */
class SkippedLinesTest {
    private val c = Candidate(5, "Song", "Artist", "Album", 120.0, false, "one\ntwo\nthree",
        "[00:10.00] one\n[00:20.00] two\n[00:30.00] three")

    @Test fun skippedLinesAreHidden() {
        assertEquals(listOf("one", "three"), LyricsRepo.linesOf(c, listOf(20_000)).map { it.text })
        assertEquals(3, LyricsRepo.linesOf(c, emptyList()).size)
    }

    @Test fun theyAreSavedAndCountAsYours() {
        val e = LyricsStore.Entry(c, pinned = false, offsetMs = 0, fetchedAt = 1, skips = listOf(20_000))
        assertEquals(listOf(20_000L), LyricsStore.decode(LyricsStore.encode(e)).skips)
        assertTrue(LyricsStore.isYours(e))
        assertEquals(listOf(20_000L), LyricsStore.fixOf("k", e, null)!!.skips)
    }

    @Test fun theyGoIntoTheBackupAndComeBack() {
        val key = "a".repeat(64)
        val snap = LibrarySnapshot(fixes = listOf(TimingFix(key, 5, false, 0, emptyList(), skips = listOf(20_000))))
        val back = LibrarySnapshot.fromJson(snap.toJson())
        assertEquals(listOf(20_000L), back.fixes.single().skips)
        val restored = LyricsStore.merged(null, back.fixes.single())!!
        assertEquals(listOf(20_000L), restored.skips)
    }

    @Test fun clearingBringsThemBack() {
        val f = LyricsStore.Entry(c, pinned = false, offsetMs = 0, fetchedAt = 1, skips = listOf(20_000))
        val shown = Shown.Found(c, LyricsRepo.linesOf(c, f.skips), true, false, 0, skips = f.skips)
        assertEquals(3, shown.withSkips(emptyList()).lines.size)
    }

    @Test fun theyAreLeftOutOfAShare() {
        val timed = LyricsRepo.linesOf(c, listOf(20_000))
        val body = ShareFix.body(c, timed, skipped = true)
        assertEquals("one\nthree", body.getString("plainLyrics"))
        assertFalse(body.getString("syncedLyrics").contains("two"))
    }
}
