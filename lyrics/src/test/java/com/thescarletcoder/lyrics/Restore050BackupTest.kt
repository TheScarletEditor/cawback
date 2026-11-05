package com.thescarletcoder.lyrics

import com.thescarletcoder.lyrics.library.LibraryBackup
import com.thescarletcoder.lyrics.library.LibraryMerge
import com.thescarletcoder.lyrics.library.LibrarySnapshot
import com.thescarletcoder.lyrics.library.Moment
import com.thescarletcoder.lyrics.library.NoteHistory
import com.thescarletcoder.lyrics.library.Play
import com.thescarletcoder.lyrics.library.Song
import com.thescarletcoder.lyrics.library.SongKey
import com.thescarletcoder.lyrics.library.SongTrack
import com.thescarletcoder.lyrics.library.TimingFix
import com.thescarletcoder.lyrics.library.Tombstone
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.security.MessageDigest

/**
 * A real Cawback 0.5.0 backup, restored by this version (independent review of 0.5.1). The file was written by 0.5.0's
 * own backup code (tag v0.5.0) with its real passphrase settings, so this reads exactly what the released app wrote;
 * how it was made is in src/test/fixtures/README.md. Invented songs and timings only.
 */
class Restore050BackupTest {
    private val pass = "invented passphrase for the 0.5.0 fixture"
    private val file by lazy { javaClass.getResourceAsStream("/backups/cawback-0.5.0.lyrbak")!!.use { it.readBytes() } }
    private val restored by lazy { LibraryBackup.read(ByteArrayInputStream(file), pass.toCharArray()) }

    private fun sha256(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }
    private fun hex(s: String) = sha256(s.toByteArray())

    // What 0.5.0 put in the file (Gen050.kt in src/test/fixtures).
    private val glass = Song("song-glass", SongKey.of("Glass Harbor", "The Lanterns"), "Glass Harbor", "The Lanterns", "Low Tide",
        "Night drive song. Better loud.", true, 1_760_000_000_000L, 1_760_100_000_000L)
    private val saints = Song("song-saints", SongKey.of("Paper Saints", "Mara Vell feat. Odo Pine"), "Paper Saints", "Mara Vell feat. Odo Pine",
        "Kindling", "", false, 1_760_050_000_000L, 1_760_050_000_000L)
    private val copper = Song("song-copper", SongKey.of("Copper Rain", "Juniper Hollow"), "Copper Rain", "Juniper Hollow", null,
        "", false, 1_760_060_000_000L, 1_760_060_000_000L)
    private val glassFix = TimingFix(hex("cache glass harbor"), 9001, true, 250, listOf(Anchor(30_000, -400), Anchor(90_000, -1_200)),
        glass.matchKey, tapped = true)
    private val saintsFix = TimingFix(hex("cache paper saints"), 9002, false, -1_500, emptyList(), saints.matchKey)
    private val copperFix = TimingFix(hex("cache copper rain"), 9003, false, 5_000, listOf(Anchor(20_000, 0), Anchor(120_000, 7_000)),
        copper.matchKey, tapped = true)
    private val expected = LibrarySnapshot(
        songs = listOf(glass, saints, copper),
        plays = listOf(
            Play("play-1", "song-glass", 1_760_200_000_000L, 201_000), Play("play-2", "song-glass", 1_760_210_000_000L, 180_500),
            Play("play-3", "song-saints", 1_760_220_000_000L, 95_000), Play("play-4", "song-copper", 1_760_230_000_000L, 240_000),
        ),
        moments = listOf(Moment("moment-1", "song-glass", 102_000, "the key change", 1_760_150_000_000L, 1_760_150_000_000L)),
        noteHistory = listOf(NoteHistory("note-1", "song-glass", "First note.", 1_760_100_000_000L)),
        tombstones = listOf(Tombstone("song-forgotten", "song", 1_760_090_000_000L)),
        tracks = listOf(
            SongTrack("song-glass", hex("track glass harbor")), SongTrack("song-saints", hex("track paper saints")),
            SongTrack("song-copper", hex("track copper rain")),
        ),
        fixes = listOf(glassFix, saintsFix, copperFix),
    )

    /** A made-up synced record, the one Gen050.kt timed: a line every 10 s from 0:10 to 2:30. */
    private val lines = (1..15).map { LyricLine(it * 10_000L, "line $it") }
    private fun timed(f: TimingFix) = YourTiming.apply(lines, f.offsetMs, f.anchors).map { it.timeMs }

    @Test fun theFileIsTheOne050Wrote() {
        // Pinned, so the fixture can't be swapped for one written by newer code without this test saying so.
        assertEquals("0b849e80b0f2c3b00da53b041a0052537dd4c0b278841823b4eee316a685de66", sha256(file))
        val din = DataInputStream(ByteArrayInputStream(file))
        assertEquals("LYRBAK01", String(ByteArray(8).also { din.readFully(it) }, Charsets.US_ASCII))
        val header = JSONObject(String(ByteArray(din.readInt()).also { din.readFully(it) }, Charsets.UTF_8))
        // The app's real passphrase settings, not the fast ones other tests use.
        assertEquals(1, header.getInt("format"))
        assertEquals(LibraryBackup.MEMORY_KIB, header.getInt("m"))
        assertEquals(LibraryBackup.PASSES, header.getInt("t"))
        assertEquals(LibraryBackup.LANES, header.getInt("p"))
    }

    @Test fun theWholeLibraryComesBack() {
        assertEquals(expected, restored)
        assertTrue("0.5.0 had no skipped lines", restored.fixes.all { it.skips.isEmpty() })
    }

    @Test fun theWrongPassphraseIsStillRefused() {
        try {
            LibraryBackup.read(ByteArrayInputStream(file), "not the passphrase".toCharArray())
            throw AssertionError("opened with the wrong passphrase")
        } catch (_: LibraryBackup.WrongPassphrase) {
        }
    }

    @Test fun restoringIntoAnEmptyLibraryGivesItBack() {
        val merged = LibraryMerge.merge(LibrarySnapshot(), restored)
        assertEquals(expected.songs.sortedBy { it.uid }, merged.songs)
        assertEquals(expected.plays.sortedBy { it.uid }, merged.plays)
        assertEquals(expected.moments, merged.moments)
        assertEquals(expected.noteHistory, merged.noteHistory)
        assertEquals(expected.tombstones, merged.tombstones)
        assertEquals(expected.tracks.sortedBy { it.songUid }, merged.tracks)
    }

    @Test fun restoringIntoALibraryThatHasTheSongAddsUp() {
        // This device made Glass Harbor on its own (another uid), with an older note and a play of its own.
        val here = LibrarySnapshot(
            songs = listOf(glass.copy(uid = "song-here", note = "Heard it first here.", favorite = false, createdAt = 1_759_000_000_000L,
                updatedAt = 1_759_500_000_000L)),
            plays = listOf(Play("play-here", "song-here", 1_759_600_000_000L, 150_000)),
        )
        val merged = LibraryMerge.merge(here, restored)
        val song = merged.songs.single { it.matchKey == glass.matchKey }
        assertEquals("song-here", song.uid)                                  // the oldest uid stays
        assertEquals(glass.note, song.note)                                  // the newer note wins
        assertTrue(song.favorite)
        assertTrue(merged.noteHistory.any { it.text == "Heard it first here." && it.songUid == "song-here" })
        assertEquals(3, merged.plays.count { it.songUid == "song-here" })   // plays from both add up
        assertEquals(1, merged.moments.count { it.songUid == "song-here" })
    }

    @Test fun everyTimingFixComesBack() {
        val fixes = LibrarySnapshot.toRestore(restored.fixes, forgotten = emptySet())
        assertEquals(expected.fixes, fixes)
        fixes.forEach { fix ->
            // Nothing for this song here yet: the version is fetched by id on its next play, with the timing.
            val entry = LyricsStore.merged(null, fix)!!
            assertEquals(fix.versionId, entry.pendingId)
            assertEquals(fix.pinned, entry.pinned)
            assertEquals(fix.offsetMs, entry.offsetMs)
            assertEquals(fix.anchors, entry.anchors)
            assertEquals(fix.tapped, entry.tapped)
            // It survives this version's cache file, and the next backup carries it as it came.
            assertEquals(entry, LyricsStore.decode(LyricsStore.encode(entry)))
            assertEquals(fix.copy(song = null), LyricsStore.fixOf(fix.key, entry, null))
        }
        // A song forgotten on this device gets no timing back.
        assertEquals(listOf(glassFix, saintsFix), LibrarySnapshot.toRestore(restored.fixes, emptySet(), forgottenSongs = setOf(copper.matchKey)))
    }

    @Test fun theSameVersionHereTakesTheTimingAtOnce() {
        val here = LyricsStore.Entry(Candidate(9001, "Glass Harbor", "The Lanterns", "Low Tide", 200.0, false, "words", "[00:10.00] line 1"),
            pinned = false, offsetMs = 0, fetchedAt = 1)
        val entry = LyricsStore.merged(here, glassFix)!!
        assertEquals(here.candidate, entry.candidate)
        assertEquals(250L, entry.offsetMs)
        assertEquals(glassFix.anchors, entry.anchors)
        assertTrue(entry.pinned)
        assertTrue(LyricsRepo.isCurrent(entry))   // not matched again on the next play
    }

    @Test fun aRestoredLibraryBacksUpAgainInThisVersionsFormat() {
        val out = ByteArrayOutputStream()
        LibraryBackup.write(out, restored, "another invented passphrase".toCharArray(), params = LibraryBackup.Params(memoryKiB = 8, passes = 1, lanes = 1))
        assertEquals(3, restored.toJson().getInt("schema"))
        assertEquals(expected, LibraryBackup.read(ByteArrayInputStream(out.toByteArray()), "another invented passphrase".toCharArray()))
    }

    @Test fun a050FixPlaysAsItDidWhereTheRulesDidNotChange() {
        // What 0.5.0's YourTiming showed for these fixes over the same record (printed by Gen050.kt).
        assertEquals(listOf(10150L, 20150, 30150, 40283, 50417, 60550, 70683, 80817, 90950, 100950, 110950, 120950, 130950, 140950, 150950),
            timed(restored.fixes.single { it.versionId == 9001L }))
        assertEquals(listOf(11500L, 21500, 31500, 41500, 51500, 61500, 71500, 81500, 91500, 101500, 111500, 121500, 131500, 141500, 151500),
            timed(restored.fixes.single { it.versionId == 9002L }))
    }

    /**
     * Two 0.5.1 changes reach a fix made in 0.5.0 (on restore, and the same for a fix already on the device when it
     * upgrades): taps more than 2 s apart in their moves now jump at the longest pause instead of spreading evenly
     * (owner Q53), and a tapped line may move past 10 s (Q53, Q62). Pinned as 0.5.1 plays it, beside what 0.5.0 showed.
     */
    @Test fun a050FixWithFarApartTapsFollowsThe051Rules() {
        val fix = restored.fixes.single { it.versionId == 9003L }
        val shown050 = listOf(5000L, 15000, 24300, 33600, 42900, 52200, 61500, 70800, 80100, 90000, 100000, 110000, 120000, 130000, 140000)
        val shown051 = listOf(5000L, 15000, 18000, 28000, 38000, 48000, 58000, 68000, 78000, 88000, 98000, 108000, 118000, 128000, 138000)
        assertEquals(shown051, timed(fix))
        assertEquals(shown050.take(2), shown051.take(2))   // up to the first tap: the same
        assertNotEquals(shown050, shown051)
    }
}
