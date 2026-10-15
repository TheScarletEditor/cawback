package com.thescarletcoder.lyrics

import com.thescarletcoder.lyrics.library.LibraryBackup
import com.thescarletcoder.lyrics.library.LibraryExport
import com.thescarletcoder.lyrics.library.LibraryExport.Options
import com.thescarletcoder.lyrics.library.LibraryExport.Which
import com.thescarletcoder.lyrics.library.LibraryMerge
import com.thescarletcoder.lyrics.library.LibrarySnapshot
import com.thescarletcoder.lyrics.library.Moment
import com.thescarletcoder.lyrics.library.NoteHistory
import com.thescarletcoder.lyrics.library.Play
import com.thescarletcoder.lyrics.library.Song
import com.thescarletcoder.lyrics.library.SongKey
import com.thescarletcoder.lyrics.library.SongTrack
import com.thescarletcoder.lyrics.library.Tombstone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.ZoneId
import java.time.ZonedDateTime

/** Invented songs only. */
class LibraryBackupTest {
    private val zone = ZoneId.of("America/Chicago")
    private fun at(y: Int, m: Int, d: Int, h: Int = 12) = ZonedDateTime.of(y, m, d, h, 0, 0, 0, zone).toInstant().toEpochMilli()
    private fun song(uid: String, title: String, artist: String = "The Lanterns", album: String? = "Low Tide", created: Long = at(2026, 1, 14),
                     note: String = "", fav: Boolean = false, updated: Long = created) =
        Song(uid, SongKey.of(title, artist), title, artist, album, note, fav, created, updated)

    private val glass = song("s1", "Glass Harbor", note = "Night drive song. Better loud.", fav = true, updated = at(2026, 2, 1))
    private val saints = song("s2", "Paper Saints", "Mara Vell", "Kindling", at(2026, 9, 30))
    private val sample = LibrarySnapshot(
        songs = listOf(glass, saints),
        plays = listOf(Play("p1", "s1", at(2026, 10, 8), 200_000), Play("p2", "s1", at(2026, 10, 1), 150_000), Play("p3", "s2", at(2026, 10, 7), 90_000)),
        moments = listOf(Moment("m1", "s1", 102_000, "the key change", at(2026, 3, 1), at(2026, 3, 1))),
        noteHistory = listOf(NoteHistory("n1", "s1", "first note", at(2026, 2, 1))),
        tombstones = listOf(Tombstone("gone", "song", at(2026, 5, 5))),
        tracks = listOf(SongTrack("s1", "a".repeat(64))),
    )
    private val fast = LibraryBackup.Params(memoryKiB = 64, passes = 1, lanes = 1)

    // --- backup file ---

    @Test fun backupRoundTripIsIdentical() {
        val out = ByteArrayOutputStream()
        LibraryBackup.write(out, sample, "correct horse".toCharArray(), params = fast)
        val back = LibraryBackup.read(ByteArrayInputStream(out.toByteArray()), "correct horse".toCharArray())
        assertEquals(sample, back)
        // Restoring into an empty Library gives the backup back.
        assertEquals(LibraryMerge.merge(LibrarySnapshot(), sample), LibraryMerge.merge(LibrarySnapshot(), back))
    }

    @Test fun backupIsNotReadableWithoutThePassphrase() {
        val out = ByteArrayOutputStream()
        LibraryBackup.write(out, sample, "correct horse".toCharArray(), params = fast)
        val bytes = out.toByteArray()
        assertFalse(String(bytes, Charsets.ISO_8859_1).contains("Glass Harbor"))
        try { LibraryBackup.read(ByteArrayInputStream(bytes), "wrong horse".toCharArray()); fail() } catch (_: LibraryBackup.WrongPassphrase) {}
    }

    @Test fun aChangedFileIsRefused() {
        val out = ByteArrayOutputStream()
        LibraryBackup.write(out, sample, "correct horse".toCharArray(), params = fast)
        val bytes = out.toByteArray()
        val tampered = bytes.copyOf().also { it[bytes.size - 5] = (it[bytes.size - 5].toInt() xor 1).toByte() }
        try { LibraryBackup.read(ByteArrayInputStream(tampered), "correct horse".toCharArray()); fail() } catch (_: LibraryBackup.WrongPassphrase) {}
        // The header is authenticated too: changing its created time breaks the file.
        val s = String(bytes, Charsets.ISO_8859_1)
        val i = s.indexOf("\"created\":") + 10
        val headerTampered = bytes.copyOf().also { it[i] = if (it[i] == '1'.code.toByte()) '2'.code.toByte() else '1'.code.toByte() }
        try { LibraryBackup.read(ByteArrayInputStream(headerTampered), "correct horse".toCharArray()); fail() } catch (_: LibraryBackup.WrongPassphrase) {}
    }

    @Test fun otherFilesAreNotBackups() {
        try { LibraryBackup.read(ByteArrayInputStream("hello there, not a backup".toByteArray()), "x".toCharArray()); fail() } catch (_: LibraryBackup.NotABackup) {}
        try { LibraryBackup.read(ByteArrayInputStream(ByteArray(0)), "x".toCharArray()); fail() } catch (_: LibraryBackup.NotABackup) {}
    }

    // --- merge (restore now, the Command link later) ---

    @Test fun playsFromBothSidesAddUp() {
        val other = LibrarySnapshot(songs = listOf(glass), plays = listOf(Play("p9", "s1", at(2026, 10, 9), 120_000), Play("p1", "s1", at(2026, 10, 8), 200_000)))
        val m = LibraryMerge.merge(sample, other)
        assertEquals(setOf("p1", "p2", "p3", "p9"), m.plays.map { it.uid }.toSet())
    }

    @Test fun newestNoteWinsAndTheOlderIsKept() {
        val edited = glass.copy(note = "Edited on the phone", updatedAt = at(2026, 10, 9))
        val m = LibraryMerge.merge(sample, LibrarySnapshot(songs = listOf(edited)))
        val s = m.songs.single { it.uid == "s1" }
        assertEquals("Edited on the phone", s.note)
        assertTrue(m.noteHistory.any { it.songUid == "s1" && it.text == "Night drive song. Better loud." })
        // The same merge in the other order gives the same Library (both devices converge).
        assertEquals(m, LibraryMerge.merge(LibrarySnapshot(songs = listOf(edited)), sample))
    }

    @Test fun sameSongMadeOnTwoDevicesBecomesOne() {
        val phoneCopy = song("x9", "Glass Harbor - Remastered 2011", created = at(2026, 6, 1), note = "phone note", updated = at(2026, 10, 9))
        val other = LibrarySnapshot(songs = listOf(phoneCopy), plays = listOf(Play("px", "x9", at(2026, 10, 9), 100_000)),
            moments = listOf(Moment("mx", "x9", 30_000, "intro", at(2026, 6, 2), at(2026, 6, 2))), tracks = listOf(SongTrack("x9", "b".repeat(64))))
        val m = LibraryMerge.merge(sample, other)
        val s = m.songs.single { it.matchKey == glass.matchKey }
        assertEquals("s1", s.uid)                          // the oldest uid stays
        assertEquals(glass.createdAt, s.createdAt)
        assertEquals("Glass Harbor", s.title)
        assertEquals("phone note", s.note)                 // newer edit
        assertTrue(m.noteHistory.any { it.text == "Night drive song. Better loud." })
        assertTrue(m.plays.single { it.uid == "px" }.songUid == "s1")
        assertTrue(m.moments.single { it.uid == "mx" }.songUid == "s1")
        assertEquals(2, m.tracks.count { it.songUid == "s1" })
    }

    @Test fun forgottenStaysForgotten() {
        val forgotOnPhone = LibrarySnapshot(tombstones = listOf(Tombstone("s2", "song", at(2026, 10, 9)), Tombstone("m1", "moment", at(2026, 10, 9))))
        val m = LibraryMerge.merge(sample, forgotOnPhone)
        assertTrue(m.songs.none { it.uid == "s2" })
        assertTrue(m.plays.none { it.songUid == "s2" })
        assertTrue(m.moments.none { it.uid == "m1" })
        assertTrue(m.tombstones.any { it.uid == "s2" })
    }

    // --- export ---

    @Test fun textExportMatchesTheScopeSample() {
        val t = LibraryExport.build(sample, Options(), at(2026, 10, 9), zone)
        assertTrue(t.startsWith("Cawback library\nExported 2026-10-09 · 2 songs · 3 plays · 2026-01-14 to 2026-10-08\n"))
        assertTrue(t.contains("About this file:"))
        assertTrue(t.contains("\nFavorites\n- Glass Harbor | The Lanterns | Low Tide | 2 | 2026-01-14 | 2026-10-08\n    Note: Night drive song. Better loud.\n    Moment 1:42: the key change\n"))
        assertTrue(t.contains("\nEverything else\n- Paper Saints | Mara Vell | Kindling | 1 | 2026-09-30 | 2026-10-07\n"))
        assertFalse(t.contains("first note"))   // replaced notes aren't exported
    }

    @Test fun exportOptionsLeaveThingsOut() {
        val t = LibraryExport.build(sample, Options(counts = false, notes = false, moments = false, aiHeader = false), at(2026, 10, 9), zone)
        assertFalse(t.contains("About this file"))
        assertFalse(t.contains("Note:"))
        assertFalse(t.contains("Moment"))
        assertTrue(t.contains("- Glass Harbor | The Lanterns | Low Tide\n"))
    }

    @Test fun exportSelections() {
        fun titles(o: Options) = LibraryExport.rows(sample, o).map { it.song.title }
        assertEquals(listOf("Glass Harbor"), titles(Options(which = Which.FAVORITES)))
        assertEquals(listOf("Glass Harbor"), titles(Options(which = Which.WITH_NOTES)))
        assertEquals(listOf("Paper Saints"), titles(Options(which = Which.ARTIST, artist = " mara vell ")))
        val oct = LibraryExport.rows(sample, Options(which = Which.RANGE, from = at(2026, 10, 2, 0), to = at(2026, 10, 9, 0)))
        assertEquals(listOf("Glass Harbor" to 1, "Paper Saints" to 1), oct.map { it.song.title to it.plays })
    }

    @Test fun csvQuotesAndDisarmsFormulas() {
        assertEquals("plain", LibraryExport.cell("plain"))
        assertEquals("\"a, b\"", LibraryExport.cell("a, b"))
        assertEquals("\"say \"\"hi\"\"\"", LibraryExport.cell("say \"hi\""))
        assertEquals("'=HYPERLINK(1)", LibraryExport.cell("=HYPERLINK(1)"))
        assertEquals("'-5", LibraryExport.cell("-5"))
        val csv = LibraryExport.build(sample, Options(format = LibraryExport.Format.CSV), at(2026, 10, 9), zone)
        val lines = csv.split("\r\n")
        assertEquals("title,artist,album,favorite,plays,first_heard,last_heard,note,moments", lines[0])
        assertEquals("Glass Harbor,The Lanterns,Low Tide,yes,2,2026-01-14,2026-10-08,Night drive song. Better loud.,1:42 the key change", lines[1])
    }
}
