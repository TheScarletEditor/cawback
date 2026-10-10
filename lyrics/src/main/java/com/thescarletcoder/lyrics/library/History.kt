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
import java.util.UUID

/**
 * Listening history settings (scope section 6, Sam 2026-10-09): off until Sam turns it on. Read straight from the
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

    fun records(context: Context, where: Where, now: Long): Boolean {
        val p = prefs(context)
        return records(mode(p), where, p.getLong(PAUSED, 0), now)
    }
}

/**
 * Feeds what's playing to a [PlayTracker] and saves the listens that count. While history is off or paused it drops
 * the song being timed, so nothing from that time is ever written.
 */
class PlayRecorder(context: Context, private val where: History.Where) {
    private val app = context.applicationContext
    private val tracker = PlayTracker()
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun onState(track: Track?, playing: Boolean) {
        val now = System.currentTimeMillis()
        if (!History.records(app, where, now)) { tracker.close(now); return }
        tracker.update(track?.key, track?.title.orEmpty(), track?.artist.orEmpty(), track?.album, track?.durationMs, playing, now)
            ?.let(::finish)
    }

    fun close() { tracker.close(System.currentTimeMillis())?.let(::finish) }

    private fun finish(f: PlayTracker.Finished) {
        if (!PlayRule.counts(f.durationMs, f.listenedMs)) return
        if (!History.records(app, where, System.currentTimeMillis())) return
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
            ?: Song(uid(), key, SongKey.cleanTitle(f.title), f.artist, f.album, createdAt = now, updatedAt = now).also { dao.insertSong(it) }
        dao.insertTrack(SongTrack(song.uid, LyricsStore.hash(f.trackKey)))
        dao.insertPlay(Play(uid(), song.uid, f.startedAt, f.listenedMs))
    }

    /** The replaced note goes to note_history: nothing Sam writes is lost. */
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
        val hashes = db.withTransaction {
            val h = dao.trackKeys(songUid)
            dao.deleteSong(songUid)   // plays, moments, note history and track keys go with it
            dao.insertTombstone(Tombstone(songUid, "song", System.currentTimeMillis()))
            h
        }
        LyricsStore(app).forgetHashed(hashes)
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
