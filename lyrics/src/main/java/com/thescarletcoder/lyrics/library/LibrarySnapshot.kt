package com.thescarletcoder.lyrics.library

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * The whole Library as plain data: what a backup holds, and what the Command link will carry between devices.
 * Never any lyric text (there is none in the Library to begin with).
 */
data class LibrarySnapshot(
    val songs: List<Song> = emptyList(),
    val plays: List<Play> = emptyList(),
    val moments: List<Moment> = emptyList(),
    val noteHistory: List<NoteHistory> = emptyList(),
    val tombstones: List<Tombstone> = emptyList(),
    val tracks: List<SongTrack> = emptyList(),
) {
    fun toJson(): JSONObject = JSONObject()
        .put("schema", SCHEMA)
        .put("songs", JSONArray(songs.map {
            JSONObject().put("uid", it.uid).put("matchKey", it.matchKey).put("title", it.title).put("artist", it.artist)
                .put("album", it.album ?: JSONObject.NULL).put("note", it.note).put("favorite", it.favorite)
                .put("createdAt", it.createdAt).put("updatedAt", it.updatedAt)
        }))
        .put("plays", JSONArray(plays.map {
            JSONObject().put("uid", it.uid).put("songUid", it.songUid).put("startedAt", it.startedAt).put("listenedMs", it.listenedMs)
        }))
        .put("moments", JSONArray(moments.map {
            JSONObject().put("uid", it.uid).put("songUid", it.songUid).put("atMs", it.atMs).put("label", it.label)
                .put("createdAt", it.createdAt).put("updatedAt", it.updatedAt)
        }))
        .put("noteHistory", JSONArray(noteHistory.map {
            JSONObject().put("uid", it.uid).put("songUid", it.songUid).put("text", it.text).put("replacedAt", it.replacedAt)
        }))
        .put("tombstones", JSONArray(tombstones.map { JSONObject().put("uid", it.uid).put("kind", it.kind).put("deletedAt", it.deletedAt) }))
        .put("tracks", JSONArray(tracks.map { JSONObject().put("songUid", it.songUid).put("trackKey", it.trackKey) }))

    companion object {
        const val SCHEMA = 1

        private fun JSONObject.list(name: String) = optJSONArray(name)?.let { a -> (0 until a.length()).map { a.getJSONObject(it) } }.orEmpty()

        fun fromJson(o: JSONObject): LibrarySnapshot {
            require(o.optInt("schema", 0) in 1..SCHEMA) { "This backup is from a newer Scarlet Lyrics. Update Lyrics, then restore." }
            return LibrarySnapshot(
                songs = o.list("songs").map {
                    Song(it.getString("uid"), it.getString("matchKey"), it.getString("title"), it.getString("artist"),
                        if (it.isNull("album")) null else it.getString("album"), it.optString("note", ""), it.optBoolean("favorite", false),
                        it.getLong("createdAt"), it.getLong("updatedAt"))
                },
                plays = o.list("plays").map { Play(it.getString("uid"), it.getString("songUid"), it.getLong("startedAt"), it.getLong("listenedMs")) },
                moments = o.list("moments").map {
                    Moment(it.getString("uid"), it.getString("songUid"), it.getLong("atMs"), it.optString("label", ""), it.getLong("createdAt"), it.getLong("updatedAt"))
                },
                noteHistory = o.list("noteHistory").map { NoteHistory(it.getString("uid"), it.getString("songUid"), it.getString("text"), it.getLong("replacedAt")) },
                tombstones = o.list("tombstones").map { Tombstone(it.getString("uid"), it.getString("kind"), it.getLong("deletedAt")) },
                tracks = o.list("tracks").map { SongTrack(it.getString("songUid"), it.getString("trackKey")) },
            )
        }
    }
}

/**
 * Combines two Libraries: restoring a backup into this one now, syncing two devices through the Command link later.
 * Sam's rules (scope section 11): plays from both always add up; a note or favorite changed on both sides goes to the
 * newest edit, and the older note is kept in note history; anything deleted on either side stays deleted.
 * Deterministic, so two devices merging the same data end up identical. Pure, so it is unit tested.
 */
object LibraryMerge {
    fun merge(a: LibrarySnapshot, b: LibrarySnapshot): LibrarySnapshot {
        val tombstones = (a.tombstones + b.tombstones).groupBy { it.uid }.map { (_, ts) -> ts.maxBy { it.deletedAt } }
        val dead = tombstones.map { it.uid }.toHashSet()
        val history = (a.noteHistory + b.noteHistory).associateBy { it.uid }.toMutableMap()

        // Every version of every song, the same uid from both sides first.
        val byUid = LinkedHashMap<String, Song>()
        for (s in a.songs + b.songs) {
            if (s.uid in dead) continue
            val other = byUid[s.uid]
            byUid[s.uid] = if (other == null) s else combine(other, s, history)
        }
        // The same song made separately on two devices (two uids, one match key): keep the oldest uid.
        val remap = HashMap<String, String>()
        val songs = byUid.values.groupBy { it.matchKey }.map { (_, versions) ->
            val sorted = versions.sortedWith(compareBy<Song> { it.createdAt }.thenBy { it.uid })
            val keep = sorted.first()
            var merged = keep
            sorted.drop(1).forEach { v -> remap[v.uid] = keep.uid; merged = combine(merged, v.copy(uid = keep.uid), history) }
            merged.copy(createdAt = sorted.minOf { it.createdAt }, title = keep.title, artist = keep.artist, album = keep.album ?: merged.album)
        }
        val alive = songs.map { it.uid }.toHashSet()
        fun song(uid: String) = remap[uid] ?: uid

        val plays = (a.plays + b.plays).distinctBy { it.uid }
            .filter { it.uid !in dead }.map { it.copy(songUid = song(it.songUid)) }.filter { it.songUid in alive }
        val moments = (a.moments + b.moments).groupBy { it.uid }.map { (_, ms) -> ms.maxWith(compareBy<Moment> { it.updatedAt }.thenBy { it.label }) }
            .filter { it.uid !in dead }.map { it.copy(songUid = song(it.songUid)) }.filter { it.songUid in alive }
        val notes = history.values.map { it.copy(songUid = song(it.songUid)) }.filter { it.songUid in alive }.distinctBy { it.uid }
        val tracks = (a.tracks + b.tracks).map { it.copy(songUid = song(it.songUid)) }.filter { it.songUid in alive }.distinct()

        return LibrarySnapshot(
            songs.sortedBy { it.uid }, plays.sortedBy { it.uid }, moments.sortedBy { it.uid },
            notes.sortedBy { it.uid }, tombstones.sortedBy { it.uid }, tracks.sortedWith(compareBy({ it.songUid }, { it.trackKey })),
        )
    }

    /** Two versions of one song: the newer edit's note and favorite; the other note goes to [history]. */
    private fun combine(x: Song, y: Song, history: MutableMap<String, NoteHistory>): Song {
        val (newer, older) = if (y.updatedAt > x.updatedAt || (y.updatedAt == x.updatedAt && y.note > x.note)) y to x else x to y
        if (older.note.isNotBlank() && older.note != newer.note) {
            // Named from its content, so both devices give the kept note the same uid.
            val uid = UUID.nameUUIDFromBytes("${newer.uid}\u0001${older.updatedAt}\u0001${older.note}".toByteArray()).toString()
            history.putIfAbsent(uid, NoteHistory(uid, newer.uid, older.note, newer.updatedAt))
        }
        return newer.copy(createdAt = minOf(x.createdAt, y.createdAt), album = newer.album ?: older.album)
    }
}
