package com.thescarletcoder.lyrics.library

import android.content.Context
import android.content.SharedPreferences
import androidx.room.withTransaction
import com.thescarletcoder.lyrics.LyricsStore
import com.thescarletcoder.lyrics.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Listening history settings (scope section 6, ruling of 2026-10-09): off until the user turns it on. Read straight from the
 * saved preferences every time, so the screen and the background listener always agree.
 */
object History {
    enum class Mode(val id: String, val label: String) {
        OFF("off", "Off"), WHILE_OPEN("open", "Only while Lyrics is open"), ALWAYS("always", "Always");
        companion object { fun of(id: String?) = entries.firstOrNull { it.id == id } ?: OFF }
    }

    /** Where a play is being timed: the open Lyrics screen, or the background listener. */
    enum class Where { SCREEN, BACKGROUND }

    /** "Pause history" until turned back on. */
    const val PAUSED_UNTIL_ON = Long.MAX_VALUE
    /** How long plays are kept: 0 = forever. */
    val KEEP_DAYS = listOf(0, 365, 90)
    fun keepLabel(days: Int) = when (days) { 0 -> "Forever"; 365 -> "1 year"; else -> "$days days" }

    const val PREFS = "lyrics_prefs"
    const val MODE = "history_mode"
    const val PAUSED = "history_paused_until"
    const val KEEP = "history_keep_days"

    fun prefs(context: Context): SharedPreferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    fun mode(p: SharedPreferences) = Mode.of(p.getString(MODE, null))

    /**
     * Whether a play may be recorded right now. Exactly one recorder runs for each mode, so a song is never counted
     * twice: the screen's in "only while open", the background listener's in "always". Pure, so it is unit tested.
     */
    fun records(mode: Mode, where: Where, pausedUntil: Long, now: Long): Boolean = when {
        now < pausedUntil -> false
        mode == Mode.WHILE_OPEN -> where == Where.SCREEN
        mode == Mode.ALWAYS -> where == Where.BACKGROUND
        else -> false
    }

    /**
     * Songs forgotten while Lyrics runs (song key to when): a listen of one that was already being timed when it was
     * forgotten is dropped instead of bringing the song back. In memory only, so nothing about it is ever written down;
     * every recorder lives in this process.
     */
    private val forgotten = ConcurrentHashMap<String, Long>()

    fun noteForgotten(songKey: String, now: Long = System.currentTimeMillis()) { forgotten[songKey] = now }

    fun forgottenSince(songKey: String, since: Long): Boolean = forgotten[songKey]?.let { it >= since } ?: false

    fun records(context: Context, where: Where, now: Long): Boolean {
        val p = prefs(context)
        return records(mode(p), where, p.getLong(PAUSED, 0), now)
    }

    /** History is on and not paused (by either recorder). */
    fun on(context: Context, now: Long): Boolean {
        val p = prefs(context)
        return mode(p) != Mode.OFF && now >= p.getLong(PAUSED, 0)
    }
}

/**
 * Feeds what's playing to the one shared [PlayTracker] and saves the listens that count. Only the recorder for the
 * current mode feeds it, so switching between "only while open" and "always" mid-song hands the listen over instead of
 * dropping it, and a recreated screen or reconnected listener carries on with it. Off or paused drops the song being
 * timed for good, so nothing from that time is ever written or continued.
 */
class PlayRecorder(context: Context, private val where: History.Where) {
    private val app = context.applicationContext
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun onState(track: Track?, playing: Boolean) {
        val now = System.currentTimeMillis()
        val done = synchronized(tracker) {
            if (!History.records(app, where, now)) {
                if (!History.on(app, now)) tracker.drop()   // off or paused; otherwise it's the other recorder's listen
                return
            }
            tracker.update(track?.key, track?.title.orEmpty(), track?.artist.orEmpty(), track?.album, track?.durationMs, playing, now)
        }
        done?.let(::finish)
    }

    fun close() {
        val now = System.currentTimeMillis()
        if (!History.records(app, where, now)) return
        synchronized(tracker) { tracker.close(now) }?.let(::finish)
    }

    private companion object {
        val tracker = PlayTracker()
    }

    private fun finish(f: PlayTracker.Finished) {
        if (f.earlierCounted || !PlayRule.counts(f.durationMs, f.listenedMs)) return
        if (!History.records(app, where, System.currentTimeMillis())) return
        if (History.forgottenSince(SongKey.of(f.title, f.artist), f.startedAt)) return
        io.launch { runCatching { LibraryRepo(app).recordPlay(f) } }
    }
}

/** Every write to the Library goes through here, so each one leaves what sync needs (timestamps, kept notes, tombstones). */
class LibraryRepo(context: Context) {
    private val app = context.applicationContext
    private val db = LibraryDatabase.get(app)
    private val dao = db.dao()

    private fun uid() = UUID.randomUUID().toString()

    suspend fun recordPlay(f: PlayTracker.Finished) = db.withTransaction {
        val now = System.currentTimeMillis()
        val key = SongKey.of(f.title, f.artist)
        val song = dao.songByKey(key)
            ?: Song(uid(), key, f.title, f.artist, f.album, createdAt = now, updatedAt = now).also { dao.insertSong(it) }
        dao.insertTrack(SongTrack(song.uid, LyricsStore.hash(f.trackKey)))
        dao.insertPlay(Play(uid(), song.uid, f.startedAt, f.listenedMs))
    }

    /** The replaced note goes to note_history: nothing the user writes is lost. */
    suspend fun setNote(songUid: String, text: String) = db.withTransaction {
        val s = dao.song(songUid) ?: return@withTransaction
        if (s.note == text) return@withTransaction
        val now = System.currentTimeMillis()
        if (s.note.isNotBlank()) dao.insertNoteHistory(NoteHistory(uid(), s.uid, s.note, now))
        dao.updateSong(s.copy(note = text, updatedAt = now))
    }

    suspend fun setFavorite(songUid: String, on: Boolean) {
        val s = dao.song(songUid) ?: return
        if (s.favorite != on) dao.updateSong(s.copy(favorite = on, updatedAt = System.currentTimeMillis()))
    }

    suspend fun addMoment(songUid: String, atMs: Long, label: String) {
        val now = System.currentTimeMillis()
        dao.insertMoment(Moment(uid(), songUid, atMs, label, now, now))
    }

    suspend fun removeMoment(uid: String) = db.withTransaction {
        dao.deleteMoment(uid)
        dao.insertTombstone(Tombstone(uid, "moment", System.currentTimeMillis()))
    }

    /** "Forget this song": its plays, note, moments and cached lyrics, everywhere. */
    suspend fun forget(songUid: String) {
        val (songKey, hashes) = db.withTransaction {
            val s = dao.song(songUid) ?: return@withTransaction null
            val h = dao.trackKeys(songUid)
            dao.deleteSong(songUid)   // plays, moments, note history and track keys go with it
            dao.insertTombstone(Tombstone(songUid, "song", System.currentTimeMillis()))
            s.matchKey to h
        } ?: return
        History.noteForgotten(songKey)   // a listen of it already being timed won't bring it back
        withContext(Dispatchers.IO) { LyricsStore(app).run { forgetHashed(hashes); forgetSong(songKey) } }
    }

    val songs get() = dao.observeSongs()
    fun songRow(uid: String) = dao.observeSongRow(uid)
    fun moments(uid: String) = dao.observeMoments(uid)
    fun noteHistory(uid: String) = dao.observeNoteHistory(uid)

    /** The Library row for what's playing; made when the user first saves a note, favorite or moment for it. */
    suspend fun songFor(t: Track, create: Boolean): Song? = db.withTransaction {
        val key = SongKey.of(t.title, t.artist)
        dao.songByKey(key) ?: if (!create) null else {
            val now = System.currentTimeMillis()
            Song(uid(), key, t.title, t.artist, t.album, createdAt = now, updatedAt = now).also {
                dao.insertSong(it)
                dao.insertTrack(SongTrack(it.uid, LyricsStore.hash(t.key)))
            }
        }
    }

    suspend fun stats(period: LibraryStats.Period, zone: ZoneId, now: Long = System.currentTimeMillis()): LibraryStats.Result {
        val from = LibraryStats.start(period, now, zone)
        val songs = dao.allSongs().associateBy { it.uid }
        return LibraryStats.compute(dao.playsSince(from), songs, from, zone)
    }

    /**
     * Settings > "Delete listening history": every play. Songs, notes, moments and favorites stay.
     * A full reset (ruling of 2026-10-11): no deletion markers, so restoring a backup afterwards brings the plays back.
     */
    suspend fun deleteHistory() = db.withTransaction {
        dao.deleteAllPlays()
        dao.deleteTombstones(listOf("play"))
    }

    /**
     * Settings > "Delete the Library": everything in it, and the cached lyrics of every song in it. A full reset, so a
     * later restore brings it all back; only songs forgotten one by one keep their marker and stay forgotten.
     */
    suspend fun deleteAll() {
        val hashes = db.withTransaction {
            val h = dao.allTrackKeys()
            dao.deleteAllSongs()
            dao.deleteTombstones(listOf("play", "moment"))
            h
        }
        LyricsStore(app).forgetHashed(hashes)
    }

    suspend fun counts() = dao.counts()

    /** The whole Library, for a backup or an export. */
    suspend fun snapshot(): LibrarySnapshot = db.withTransaction {
        LibrarySnapshot(dao.allSongs(), dao.allPlays(), dao.allMoments(), dao.allNoteHistory(), dao.allTombstones(), dao.allTracks())
    }

    data class Restored(val songsBefore: Int, val songsAfter: Int, val playsBefore: Int, val playsAfter: Int)

    /**
     * Restore a backup by merging it into this Library (LibraryMerge: plays add up, newest note wins and the older
     * one is kept, deletions stay deleted), so restoring never loses what's here. Then rewrites the Library.
     */
    suspend fun restore(incoming: LibrarySnapshot): Restored {
        val (before, merged) = db.withTransaction {
            val local = snapshot()
            val merged = LibraryMerge.merge(local, incoming)
            dao.deleteAllSongs()   // plays, moments, note history and track keys go with the songs
            dao.deleteAllTombstones()
            dao.insertSongs(merged.songs)
            dao.insertPlays(merged.plays)
            dao.insertMoments(merged.moments)
            dao.insertNoteHistories(merged.noteHistory)
            dao.insertTombstones(merged.tombstones)
            dao.insertTracks(merged.tracks)
            local to merged
        }
        // Songs the backup says were forgotten: their cached lyrics go too.
        val keep = merged.tracks.map { it.trackKey }.toHashSet()
        LyricsStore(app).forgetHashed(before.tracks.map { it.trackKey }.filter { it !in keep }.distinct())
        return Restored(before.songs.size, merged.songs.size, before.plays.size, merged.plays.size)
    }

    /** Drops plays older than the "keep plays for" setting. Run when Lyrics starts. */
    suspend fun prune(keepDays: Int, now: Long = System.currentTimeMillis()) {
        if (keepDays <= 0) return
        val old = dao.playsBefore(now - keepDays * 86_400_000L)
        if (old.isEmpty()) return
        db.withTransaction {
            old.chunked(500).forEach { dao.deletePlays(it) }
            old.forEach { dao.insertTombstone(Tombstone(it, "play", now)) }
        }
    }
}
