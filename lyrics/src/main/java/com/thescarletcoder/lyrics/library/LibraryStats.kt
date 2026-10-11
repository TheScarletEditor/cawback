package com.thescarletcoder.lyrics.library

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * Library > Stats (scope section 6): listening time, top songs, artists and albums, new to you, favorite hour.
 * Stats describe; they never push (no streaks, goals or nudges, scope section 3). Pure, so it is unit tested.
 */
object LibraryStats {
    enum class Period(val label: String) { WEEK("This week"), MONTH("This month"), YEAR("This year"), ALL("All time") }

    data class Ranked(val uid: String?, val label: String, val sub: String?, val plays: Int)

    data class Result(
        val plays: Int,
        val listenedMs: Long,
        val topSongs: List<Ranked>,
        val topArtists: List<Ranked>,
        val topAlbums: List<Ranked>,
        /** Songs first heard in the period. */
        val newToYou: List<Ranked>,
        val newCount: Int,
        /** 0..23, the hour most plays started in; null with no plays. */
        val favoriteHour: Int?,
    )

    /** When [p] starts, in the device's time zone. Weeks start on Monday. */
    fun start(p: Period, now: Long, zone: ZoneId): Long {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val day: LocalDate = when (p) {
            Period.WEEK -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            Period.MONTH -> today.withDayOfMonth(1)
            Period.YEAR -> today.withDayOfYear(1)
            Period.ALL -> return 0
        }
        return day.atStartOfDay(zone).toInstant().toEpochMilli()
    }

    /**
     * [plays] are the period's plays; [songs] every song by uid. "First heard" is when the song first entered the
     * Library (its createdAt), so it survives plays being pruned.
     */
    fun compute(plays: List<Play>, songs: Map<String, Song>, from: Long, zone: ZoneId, top: Int = 5): Result {
        val bySong = plays.groupingBy { it.songUid }.eachCount()
        fun song(uid: String) = songs[uid]

        val topSongs = bySong.entries.mapNotNull { (uid, n) -> song(uid)?.let { Ranked(uid, it.title, it.artist, n) } }
            .sortedWith(compareByDescending<Ranked> { it.plays }.thenBy { it.label.lowercase() }).take(top)

        val artistPlays = HashMap<String, Pair<String, Int>>()   // key -> (display name, plays)
        val albumPlays = HashMap<String, Triple<String, String, Int>>()   // key -> (album, artist, plays)
        bySong.forEach { (uid, n) ->
            val s = song(uid) ?: return@forEach
            val artist = SongKey.primaryArtist(s.artist)
            val ak = artist.lowercase()
            artistPlays[ak] = (artistPlays[ak]?.first ?: artist) to ((artistPlays[ak]?.second ?: 0) + n)
            val album = s.album?.takeIf { it.isNotBlank() } ?: return@forEach
            val bk = album.lowercase() + "\u0001" + ak
            albumPlays[bk] = Triple(albumPlays[bk]?.first ?: album, artist, (albumPlays[bk]?.third ?: 0) + n)
        }
        val topArtists = artistPlays.values.map { Ranked(null, it.first, null, it.second) }
            .sortedWith(compareByDescending<Ranked> { it.plays }.thenBy { it.label.lowercase() }).take(top)
        val topAlbums = albumPlays.values.map { Ranked(null, it.first, it.second, it.third) }
            .sortedWith(compareByDescending<Ranked> { it.plays }.thenBy { it.label.lowercase() }).take(top)

        val fresh = bySong.keys.mapNotNull { song(it) }.filter { it.createdAt >= from }
        val newToYou = fresh.sortedByDescending { it.createdAt }.take(top).map { Ranked(it.uid, it.title, it.artist, bySong[it.uid] ?: 0) }

        val hour = plays.groupingBy { Instant.ofEpochMilli(it.startedAt).atZone(zone).hour }.eachCount()
            .entries.sortedWith(compareByDescending<Map.Entry<Int, Int>> { it.value }.thenBy { it.key }).firstOrNull()?.key

        return Result(plays.size, plays.sumOf { it.listenedMs }, topSongs, topArtists, topAlbums, newToYou, fresh.size, hour)
    }

    /** "3 h 20 min", "45 min", "under a minute". */
    fun duration(ms: Long): String {
        val min = ms / 60_000
        return when {
            min < 1 -> "under a minute"
            min < 60 -> "$min min"
            else -> "${min / 60} h" + if (min % 60 != 0L) " ${min % 60} min" else ""
        }
    }

    /** "9 PM", "12 AM". */
    fun hourLabel(h: Int) = when (h) { 0 -> "12 AM"; 12 -> "12 PM"; in 1..11 -> "$h AM"; else -> "${h - 12} PM" }
}
