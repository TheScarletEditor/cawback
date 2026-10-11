package com.thescarletcoder.lyrics.library

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import com.thescarletcoder.lyrics.Matching
import java.io.File
import kotlinx.coroutines.flow.Flow
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/*
 * The Library (Lyrics 0.4; scope sections 6 and 11): songs, plays, moments and notes, in their own encrypted
 * database, separate from the lyrics cache. Never any lyric text. Every row has a uid and timestamps so the Command
 * link can sync it between the owner's devices (newest edit wins; replaced notes are kept in note_history; deletions
 * travel as tombstones).
 */

/** One song, however many versions of its title there are ("Song - Remastered 2011" and "Song" share a row). */
@Entity(tableName = "song", indices = [Index(value = ["matchKey"], unique = true)])
data class Song(
    @PrimaryKey val uid: String,
    /** SongKey.of(title, artist): cleaned title + primary artist, lowercase. */
    val matchKey: String,
    /** As the music app first gave it (the original names are what's shown). */
    val title: String,
    val artist: String,
    val album: String?,
    val note: String = "",
    val favorite: Boolean = false,
    val createdAt: Long,
    /** Last change to the note or favorite: the newer one wins when two devices sync. */
    val updatedAt: Long,
)

/** One listen that counted (PlayRule). Not stored: which app played it, location, lyrics. */
@Entity(
    tableName = "play",
    foreignKeys = [ForeignKey(entity = Song::class, parentColumns = ["uid"], childColumns = ["songUid"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("songUid"), Index("startedAt")],
)
data class Play(
    @PrimaryKey val uid: String,
    val songUid: String,
    val startedAt: Long,
    val listenedMs: Long,
)

/** A bookmarked time in a song with the user's own label. Never the line's text. */
@Entity(
    tableName = "moment",
    foreignKeys = [ForeignKey(entity = Song::class, parentColumns = ["uid"], childColumns = ["songUid"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("songUid")],
)
data class Moment(
    @PrimaryKey val uid: String,
    val songUid: String,
    val atMs: Long,
    val label: String,
    val createdAt: Long,
    val updatedAt: Long,
)

/** A note that was replaced (an edit, or the older side of a sync conflict). Nothing the user writes is lost. */
@Entity(
    tableName = "note_history",
    foreignKeys = [ForeignKey(entity = Song::class, parentColumns = ["uid"], childColumns = ["songUid"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("songUid")],
)
data class NoteHistory(
    @PrimaryKey val uid: String,
    val songUid: String,
    val text: String,
    val replacedAt: Long,
)

/** Something deleted here, so the other device deletes it too (a forgotten song, a removed moment, pruned plays). */
@Entity(tableName = "tombstone")
data class Tombstone(
    @PrimaryKey val uid: String,
    /** "song", "play" or "moment". */
    val kind: String,
    val deletedAt: Long,
)

/** The raw track keys a song's cached lyrics are filed under, so "Forget this song" can clear them too. */
@Entity(
    tableName = "song_track",
    primaryKeys = ["songUid", "trackKey"],
    foreignKeys = [ForeignKey(entity = Song::class, parentColumns = ["uid"], childColumns = ["songUid"], onDelete = ForeignKey.CASCADE)],
)
data class SongTrack(val songUid: String, val trackKey: String)

/** Settings > What's on this device: real counts of everything in the Library. */
data class LibraryCounts(val songs: Int, val plays: Int, val moments: Int, val notes: Int, val earlierNotes: Int, val favorites: Int)

/** A song with its numbers, for the Songs list. */
data class SongRow(
    val uid: String, val title: String, val artist: String, val album: String?, val favorite: Boolean, val note: String,
    val plays: Int, @ColumnInfo(name = "firstHeard") val firstHeard: Long?, @ColumnInfo(name = "lastHeard") val lastHeard: Long?,
    @ColumnInfo(name = "listenedMs") val listenedMs: Long?,
)

@Dao
interface LibraryDao {
    @Query("SELECT * FROM song WHERE matchKey = :key LIMIT 1") suspend fun songByKey(key: String): Song?
    @Query("SELECT * FROM song WHERE uid = :uid") suspend fun song(uid: String): Song?
    @Query("SELECT * FROM song WHERE uid = :uid") fun observeSong(uid: String): Flow<Song?>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertSong(s: Song)
    @Update suspend fun updateSong(s: Song)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertTrack(t: SongTrack)
    @Query("SELECT trackKey FROM song_track WHERE songUid = :uid") suspend fun trackKeys(uid: String): List<String>

    @Insert suspend fun insertPlay(p: Play)
    @Query("SELECT COUNT(*) FROM play") suspend fun playCount(): Int
    @Query("SELECT * FROM play WHERE startedAt BETWEEN :from AND :to ORDER BY startedAt") suspend fun playsBetween(from: Long, to: Long): List<Play>
    @Query("SELECT uid FROM play WHERE startedAt < :before") suspend fun playsBefore(before: Long): List<String>
    @Query("DELETE FROM play WHERE uid IN (:uids)") suspend fun deletePlays(uids: List<String>)
    @Query("SELECT * FROM play WHERE songUid = :uid ORDER BY startedAt DESC") suspend fun playsOf(uid: String): List<Play>

    @Insert suspend fun insertMoment(m: Moment)
    @Query("SELECT * FROM moment WHERE songUid = :uid ORDER BY atMs") fun observeMoments(uid: String): Flow<List<Moment>>
    @Query("SELECT * FROM moment WHERE songUid = :uid ORDER BY atMs") suspend fun momentsOf(uid: String): List<Moment>
    @Query("DELETE FROM moment WHERE uid = :uid") suspend fun deleteMoment(uid: String)

    @Insert suspend fun insertNoteHistory(h: NoteHistory)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertTombstone(t: Tombstone)
    @Query("DELETE FROM song WHERE uid = :uid") suspend fun deleteSong(uid: String)

    // First heard is when the song entered the Library, so it survives plays being pruned.
    @Query("""
        SELECT s.uid, s.title, s.artist, s.album, s.favorite, s.note,
               COUNT(p.uid) AS plays, s.createdAt AS firstHeard, MAX(p.startedAt) AS lastHeard, SUM(p.listenedMs) AS listenedMs
        FROM song s LEFT JOIN play p ON p.songUid = s.uid
        GROUP BY s.uid
    """)
    fun observeSongs(): Flow<List<SongRow>>

    @Query("""
        SELECT s.uid, s.title, s.artist, s.album, s.favorite, s.note,
               COUNT(p.uid) AS plays, s.createdAt AS firstHeard, MAX(p.startedAt) AS lastHeard, SUM(p.listenedMs) AS listenedMs
        FROM song s LEFT JOIN play p ON p.songUid = s.uid
        WHERE s.uid = :uid GROUP BY s.uid
    """)
    fun observeSongRow(uid: String): Flow<SongRow?>

    @Query("SELECT * FROM note_history WHERE songUid = :uid ORDER BY replacedAt DESC") fun observeNoteHistory(uid: String): Flow<List<NoteHistory>>
    @Query("SELECT * FROM play WHERE startedAt >= :from") suspend fun playsSince(from: Long): List<Play>
    @Query("DELETE FROM play") suspend fun deleteAllPlays()
    @Query("SELECT trackKey FROM song_track") suspend fun allTrackKeys(): List<String>
    @Query("DELETE FROM song") suspend fun deleteAllSongs()

    @Query("SELECT * FROM song") suspend fun allSongs(): List<Song>
    @Query("SELECT * FROM play") suspend fun allPlays(): List<Play>
    @Query("SELECT * FROM moment") suspend fun allMoments(): List<Moment>
    @Query("""
        SELECT (SELECT COUNT(*) FROM song) AS songs, (SELECT COUNT(*) FROM play) AS plays, (SELECT COUNT(*) FROM moment) AS moments,
               (SELECT COUNT(*) FROM song WHERE note != '') AS notes, (SELECT COUNT(*) FROM note_history) AS earlierNotes,
               (SELECT COUNT(*) FROM song WHERE favorite) AS favorites
    """)
    suspend fun counts(): LibraryCounts
    @Query("SELECT * FROM note_history") suspend fun allNoteHistory(): List<NoteHistory>
    @Query("SELECT * FROM tombstone") suspend fun allTombstones(): List<Tombstone>
    @Query("SELECT * FROM song_track") suspend fun allTracks(): List<SongTrack>

    // Restore (and later the Command link) rewrites the whole Library in one transaction.
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertSongs(l: List<Song>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertPlays(l: List<Play>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertMoments(l: List<Moment>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertNoteHistories(l: List<NoteHistory>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertTombstones(l: List<Tombstone>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertTracks(l: List<SongTrack>)
    @Query("DELETE FROM tombstone") suspend fun deleteAllTombstones()
    @Query("DELETE FROM tombstone WHERE kind IN (:kinds)") suspend fun deleteTombstones(kinds: List<String>)
}

@Database(entities = [Song::class, Play::class, Moment::class, NoteHistory::class, Tombstone::class, SongTrack::class], version = 1)
abstract class LibraryDatabase : RoomDatabase() {
    abstract fun dao(): LibraryDao

    companion object {
        private const val NAME = "library.db"

        /** Whether the Library has ever been created (so turning history on is what first creates it). */
        fun exists(context: Context) = context.getDatabasePath(NAME).exists()
        @Volatile private var instance: LibraryDatabase? = null

        /** Encrypted with a key that only exists wrapped by Android Keystore (LibraryKey), like Scarlet Command's. */
        fun get(context: Context): LibraryDatabase = instance ?: synchronized(this) {
            instance ?: run {
                System.loadLibrary("sqlcipher")
                Room.databaseBuilder(context.applicationContext, LibraryDatabase::class.java, NAME)
                    .openHelperFactory(SupportOpenHelperFactory(LibraryKey.passphrase(context)))
                    .build().also { instance = it }
            }
        }

        /**
         * After LibraryKey.Gone: sets the unreadable database aside (renamed, never deleted) and forgets its key, so the
         * next open makes a new, empty Library that a backup can be restored into.
         */
        fun startFresh(context: Context) = synchronized(this) {
            instance?.close(); instance = null
            val db = context.getDatabasePath(NAME)
            val aside = "library-unreadable-${System.currentTimeMillis()}"
            listOf("", "-wal", "-shm", "-journal").forEach { ext ->
                File(db.path + ext).takeIf { it.exists() }?.renameTo(File(db.parentFile, aside + ".db" + ext))
            }
            LibraryKey.discard(context)
        }
    }
}

/** How a song is recognised across versions of its title and across music apps. Pure, so it is unit tested. */
object SongKey {
    fun primaryArtist(artist: String) = artist.split(",", " & ", " feat", " ft.", " x ").first().trim()
    /** The same cleaning the lyrics lookup uses, so a song matches the way its lyrics do. */
    fun of(title: String, artist: String) = Matching.cleanTitle(title).lowercase() + "\u0001" + primaryArtist(artist).lowercase()
}

/**
 * What counts as a play (scope section 6, the common scrobbling rule): a track longer than 30 s, listened to for half
 * its length or 4 minutes, whichever comes first. Pure, so it is unit tested.
 */
object PlayRule {
    const val MIN_TRACK_MS = 30_000L
    const val CAP_MS = 240_000L

    fun counts(durationMs: Long?, listenedMs: Long): Boolean {
        if (durationMs != null && durationMs in 1..MIN_TRACK_MS) return false
        val need = if (durationMs == null || durationMs <= 0) CAP_MS else minOf(durationMs / 2, CAP_MS)
        return listenedMs >= need
    }
}

/**
 * Times one song at a time on the wall clock while it's actually playing: paused time doesn't count, and seeking
 * can't fake a play (only real listening time adds up). Pure: feed it the state, it says when a play finished.
 */
class PlayTracker {
    /** [earlierCounted]: this listen continues one that already counted before Lyrics closed, so it isn't a new play. */
    data class Finished(val trackKey: String, val title: String, val artist: String, val album: String?, val durationMs: Long?,
                        val startedAt: Long, val listenedMs: Long, val earlierCounted: Boolean = false)

    private var key: String? = null
    private var title = ""; private var artist = ""; private var album: String? = null; private var duration: Long? = null
    private var startedAt = 0L
    private var listened = 0L
    private var playingSince: Long? = null
    private var earlierCounted = false
    // The listen [close] ended, so coming back to the same song soon after continues it instead of starting over.
    private var closed: Finished? = null
    private var closedAt = 0L

    /**
     * The latest state: [trackKey] null when nothing is followed. Returns the previous song's listen when it ended
     * (a new song, or nothing playing any more), for the caller to check with PlayRule and record.
     */
    fun update(trackKey: String?, title: String, artist: String, album: String?, durationMs: Long?, playing: Boolean, now: Long): Finished? {
        var done: Finished? = null
        if (song(trackKey) != song(key)) {
            done = finish(now)
            key = trackKey
            if (trackKey != null) {
                this.title = title; this.artist = artist; this.album = album; duration = durationMs
                val c = closed
                if (c != null && song(c.trackKey) == song(trackKey) && now - closedAt < (c.durationMs?.takeIf { it > 0 } ?: RESUME_MS)) {
                    startedAt = c.startedAt; listened = c.listenedMs
                    earlierCounted = c.earlierCounted || PlayRule.counts(c.durationMs, c.listenedMs)
                } else {
                    startedAt = now; listened = 0; earlierCounted = false
                }
                closed = null
            }
            playingSince = null
        } else {   // the same song; the length often arrives a moment late
            key = trackKey
            if (durationMs != null) duration = durationMs
        }
        if (key != null) {
            val since = playingSince
            if (playing && since == null) playingSince = now
            if (!playing && since != null) { listened += now - since; playingSince = null }
        }
        return done
    }

    /** Track.key without its last part, the length: the same song whether or not its length is known yet. */
    private fun song(trackKey: String?) = trackKey?.substringBeforeLast('')

    /**
     * Lyrics is closing (or history was switched off): finish whatever was playing. If the same song is still playing
     * when Lyrics comes back, the listen continues: an app switch doesn't split it, and a long song isn't counted twice.
     */
    fun close(now: Long): Finished? = finish(now)?.also { closed = it; closedAt = now }

    private fun finish(now: Long): Finished? {
        val k = key ?: return null
        playingSince?.let { listened += now - it }
        val f = Finished(k, title, artist, album, duration, startedAt, listened, earlierCounted)
        key = null; playingSince = null; listened = 0; earlierCounted = false
        return f
    }

    private companion object {
        /** How long a closed listen of unknown length can still be continued. */
        const val RESUME_MS = 600_000L
    }
}
