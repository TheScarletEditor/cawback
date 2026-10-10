package com.thescarletcoder.lyrics

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.thescarletcoder.lyrics.library.History
import com.thescarletcoder.lyrics.library.LibraryBackup
import com.thescarletcoder.lyrics.library.LibraryExport
import com.thescarletcoder.lyrics.library.LibraryMerge
import com.thescarletcoder.lyrics.library.LibrarySnapshot
import com.thescarletcoder.lyrics.library.LibraryDatabase
import com.thescarletcoder.lyrics.library.LibraryRepo
import com.thescarletcoder.lyrics.library.PlayRecorder
import com.thescarletcoder.lyrics.library.PlayTracker
import com.thescarletcoder.lyrics.library.SongKey
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The Library on a real Android: SQLCipher, the Keystore-wrapped key, Room. Emulator only (it writes to the app's
 * own Library). Invented songs only.
 */
@RunWith(AndroidJUnit4::class)
class LibraryDbTest {
    private val ctx: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val repo get() = LibraryRepo(ctx)
    private val dao get() = LibraryDatabase.get(ctx).dao()

    private fun finished(title: String, artist: String = "The Lanterns", startedAt: Long = System.currentTimeMillis(), key: String = "$title|$artist") =
        PlayTracker.Finished(key, title, artist, "Low Tide", 200_000, startedAt, 120_000)

    @Before fun clean() = runBlocking {
        repo.deleteAll()
        History.prefs(ctx).edit().clear().commit()
        Unit
    }

    @Test fun databaseIsEncrypted() = runBlocking {
        repo.recordPlay(finished("Glass Harbor"))
        LibraryDatabase.get(ctx).openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").close()
        val head = ctx.getDatabasePath("library.db").readBytes().copyOf(16)
        assertFalse("library.db has a plain SQLite header", String(head, Charsets.US_ASCII).startsWith("SQLite format 3"))
        assertTrue(File(ctx.noBackupFilesDir, "library.key.wrapped").exists())
    }

    @Test fun versionsShareOneSongAndPlaysAddUp() = runBlocking {
        repo.recordPlay(finished("Glass Harbor"))
        repo.recordPlay(finished("Glass Harbor - Remastered 2011", key = "other"))
        val song = dao.songByKey(SongKey.of("Glass Harbor", "The Lanterns"))!!
        assertEquals("Glass Harbor", song.title)   // the first-seen original name
        assertEquals(2, dao.playsOf(song.uid).size)
        assertEquals(2, dao.trackKeys(song.uid).size)
    }

    @Test fun replacedNotesAreKept() = runBlocking {
        repo.recordPlay(finished("Paper Saints"))
        val uid = dao.songByKey(SongKey.of("Paper Saints", "The Lanterns"))!!.uid
        repo.setNote(uid, "First thought")
        repo.setNote(uid, "Second thought")
        assertEquals("Second thought", dao.song(uid)!!.note)
        val kept = LibraryDatabase.get(ctx).openHelper.readableDatabase.query("SELECT text FROM note_history WHERE songUid = ?", arrayOf(uid))
        kept.use { assertTrue(it.moveToFirst()); assertEquals("First thought", it.getString(0)); assertEquals(1, it.count) }
    }

    @Test fun forgetRemovesEverythingAndTheCachedLyrics() = runBlocking {
        val f = finished("Undertow")
        repo.recordPlay(f)
        val uid = dao.songByKey(SongKey.of("Undertow", "The Lanterns"))!!.uid
        repo.setNote(uid, "note")
        repo.addMoment(uid, 102_000, "the key change")
        val cached = File(File(ctx.filesDir, "lyrics").apply { mkdirs() }, LyricsStore.hash(f.trackKey) + ".json").apply { writeText("{}") }

        repo.forget(uid)
        assertNull(dao.song(uid))
        assertTrue(dao.playsOf(uid).isEmpty())
        assertTrue(dao.momentsOf(uid).isEmpty())
        assertFalse("cached lyrics survived Forget", cached.exists())
        val tomb = LibraryDatabase.get(ctx).openHelper.readableDatabase.query("SELECT kind FROM tombstone WHERE uid = ?", arrayOf(uid))
        tomb.use { assertTrue(it.moveToFirst()); assertEquals("song", it.getString(0)) }
    }

    @Test fun pruningDropsOldPlaysOnly() = runBlocking {
        val now = System.currentTimeMillis()
        repo.recordPlay(finished("Kindling", startedAt = now - 100L * 86_400_000))
        repo.recordPlay(finished("Kindling", startedAt = now - 10L * 86_400_000))
        val uid = dao.songByKey(SongKey.of("Kindling", "The Lanterns"))!!.uid
        repo.setNote(uid, "stays")
        repo.prune(90, now)
        assertEquals(1, dao.playsOf(uid).size)
        assertEquals("stays", dao.song(uid)!!.note)
    }

    /** Scope section 9: back up, clear storage, restore, identical library. */
    @Test fun backupRestoreRoundTrip() = runBlocking {
        repo.recordPlay(finished("Glass Harbor"))
        repo.recordPlay(finished("Paper Saints", "Mara Vell"))
        val uid = dao.songByKey(SongKey.of("Glass Harbor", "The Lanterns"))!!.uid
        repo.setNote(uid, "first"); repo.setNote(uid, "Night drive song.")
        repo.setFavorite(uid, true)
        repo.addMoment(uid, 102_000, "the key change")
        val before = repo.snapshot()

        val file = java.io.ByteArrayOutputStream()
        LibraryBackup.write(file, before, "correct horse".toCharArray(), params = LibraryBackup.Params(1024, 1, 1))
        val sql = LibraryDatabase.get(ctx).openHelper.writableDatabase   // "clear storage": nothing left, not even tombstones
        listOf("moment", "note_history", "play", "song_track", "tombstone", "song").forEach { sql.execSQL("DELETE FROM $it") }
        assertEquals(0, dao.allSongs().size)

        repo.restore(LibraryBackup.read(file.toByteArray().inputStream(), "correct horse".toCharArray()))
        assertEquals(LibraryMerge.merge(LibrarySnapshot(), before), repo.snapshot().let { LibraryMerge.merge(LibrarySnapshot(), it) })
    }

    /** Scope section 2: lyric text in the cache never reaches an export or a backup. */
    @Test fun lyricsNeverReachExportsOrBackups() = runBlocking {
        val sentinel = "SENTINEL lyric line that must never leave"
        val f = finished("Undertow")
        File(File(ctx.filesDir, "lyrics").apply { mkdirs() }, LyricsStore.hash(f.trackKey) + ".json").writeText("{\"synced\":\"[00:01.00]$sentinel\"}")
        repo.recordPlay(f)
        val snap = repo.snapshot()
        for (fmt in LibraryExport.Format.entries) {
            val out = LibraryExport.build(snap, LibraryExport.Options(format = fmt), System.currentTimeMillis(), java.time.ZoneId.systemDefault())
            assertTrue(out.contains("Undertow"))
            assertFalse(out.contains("SENTINEL"))
        }
        assertFalse(snap.toJson().toString().contains("SENTINEL"))
    }

    @Test fun historyOffWritesNothing() = runBlocking {
        val before = dao.playCount()
        for (where in History.Where.entries) {
            val r = PlayRecorder(ctx, where)
            val t = Track("Glass Harbor", "The Lanterns", null, 60_000)
            r.onState(t, true)
            Thread.sleep(50)
            r.onState(null, false)
            r.close()
        }
        Thread.sleep(500)
        assertEquals(before, dao.playCount())
        assertNotNull(History.prefs(ctx))
    }
}
