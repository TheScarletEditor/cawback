// Made lyrics/src/test/resources/backups/cawback-0.5.0.lyrbak with Cawback 0.5.0's own backup code (tag v0.5.0),
// so the restore test reads bytes the released app really wrote. Invented songs and timings only.
// Compiled against the v0.5.0 sources of LibraryBackup.kt, LibrarySnapshot.kt, Library.kt, YourTiming.kt, LyricsStore.kt,
// LrcLib.kt and Lrc.kt (see README.md in this folder). Prints what 0.5.0 showed for each fix, for the test.

import com.thescarletcoder.lyrics.Anchor
import com.thescarletcoder.lyrics.LyricLine
import com.thescarletcoder.lyrics.YourTiming
import com.thescarletcoder.lyrics.library.LibraryBackup
import com.thescarletcoder.lyrics.library.LibrarySnapshot
import com.thescarletcoder.lyrics.library.Moment
import com.thescarletcoder.lyrics.library.NoteHistory
import com.thescarletcoder.lyrics.library.Play
import com.thescarletcoder.lyrics.library.Song
import com.thescarletcoder.lyrics.library.SongKey
import com.thescarletcoder.lyrics.library.SongTrack
import com.thescarletcoder.lyrics.library.TimingFix
import com.thescarletcoder.lyrics.library.Tombstone
import java.io.File
import java.security.MessageDigest

fun hex(s: String) = MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

const val PASS = "invented passphrase for the 0.5.0 fixture"
const val CREATED = 1_760_400_000_000L   // 2025-10-14, a fixed time so the file's header is the same every run

fun main(args: Array<String>) {
    val glass = Song("song-glass", SongKey.of("Glass Harbor", "The Lanterns"), "Glass Harbor", "The Lanterns", "Low Tide",
        "Night drive song. Better loud.", true, 1_760_000_000_000L, 1_760_100_000_000L)
    val saints = Song("song-saints", SongKey.of("Paper Saints", "Mara Vell feat. Odo Pine"), "Paper Saints", "Mara Vell feat. Odo Pine",
        "Kindling", "", false, 1_760_050_000_000L, 1_760_050_000_000L)
    val copper = Song("song-copper", SongKey.of("Copper Rain", "Juniper Hollow"), "Copper Rain", "Juniper Hollow", null,
        "", false, 1_760_060_000_000L, 1_760_060_000_000L)
    val snap = LibrarySnapshot(
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
        fixes = listOf(
            // Picked by hand, nudged and lined up in Fix lines; neighbouring taps within 2 s of each other.
            TimingFix(hex("cache glass harbor"), 9001, true, 250, listOf(Anchor(30_000, -400), Anchor(90_000, -1_200)),
                glass.matchKey, tapped = true),
            // The automatic match with only Earlier and Later.
            TimingFix(hex("cache paper saints"), 9002, false, -1_500, emptyList(), saints.matchKey),
            // Taps far apart in their moves, and a nudge that took the last one past 0.5.0's 10 s limit (shown at 10 s).
            TimingFix(hex("cache copper rain"), 9003, false, 5_000, listOf(Anchor(20_000, 0), Anchor(120_000, 7_000)),
                copper.matchKey, tapped = true),
        ),
    )
    val out = File(args[0])
    out.outputStream().use { LibraryBackup.write(it, snap, PASS.toCharArray(), now = CREATED) }
    check(out.inputStream().use { LibraryBackup.read(it, PASS.toCharArray()) } == snap) { "0.5.0 can't read its own file" }
    println("wrote ${out.length()} bytes, schema ${LibrarySnapshot.SCHEMA}")

    // A made-up synced record: a line every 10 s from 0:10 to 2:30.
    val lines = (1..15).map { LyricLine(it * 10_000L, "line $it") }
    snap.fixes.forEach { f -> println("${f.versionId}: " + YourTiming.apply(lines, f.offsetMs, f.anchors).map { it.timeMs }) }
}
