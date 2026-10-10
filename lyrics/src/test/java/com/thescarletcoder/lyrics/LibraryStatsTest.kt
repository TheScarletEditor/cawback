package com.thescarletcoder.lyrics

import com.thescarletcoder.lyrics.library.LibraryStats
import com.thescarletcoder.lyrics.library.LibraryStats.Period
import com.thescarletcoder.lyrics.library.Play
import com.thescarletcoder.lyrics.library.Song
import com.thescarletcoder.lyrics.library.SongKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

/** Invented songs only. */
class LibraryStatsTest {
    private val zone = ZoneId.of("America/Chicago")
    private fun at(y: Int, m: Int, d: Int, h: Int = 12) = ZonedDateTime.of(y, m, d, h, 0, 0, 0, zone).toInstant().toEpochMilli()

    private fun song(uid: String, title: String, artist: String, album: String?, created: Long) =
        Song(uid, SongKey.of(title, artist), title, artist, album, createdAt = created, updatedAt = created)

    private var n = 0
    private fun play(song: String, start: Long, ms: Long = 120_000) = Play("p${n++}", song, start, ms)

    @Test fun periodsStartWhereSamExpects() {
        val thu = at(2026, 10, 8, 15)   // a Thursday
        assertEquals(at(2026, 10, 5, 0), LibraryStats.start(Period.WEEK, thu, zone))   // Monday
        assertEquals(at(2026, 10, 1, 0), LibraryStats.start(Period.MONTH, thu, zone))
        assertEquals(at(2026, 1, 1, 0), LibraryStats.start(Period.YEAR, thu, zone))
        assertEquals(0, LibraryStats.start(Period.ALL, thu, zone))
    }

    @Test fun ranksSongsArtistsAlbumsAndNewOnes() {
        val from = at(2026, 10, 5, 0)
        val songs = listOf(
            song("a", "Glass Harbor", "The Lanterns", "Low Tide", at(2026, 1, 14)),
            song("b", "Paper Saints", "Mara Vell", "Kindling", at(2026, 10, 6)),
            song("c", "Undertow", "The Lanterns, Mara Vell", "Low Tide", at(2026, 10, 7)),
        ).associateBy { it.uid }
        val plays = listOf(
            play("a", at(2026, 10, 5, 21)), play("a", at(2026, 10, 6, 21)), play("a", at(2026, 10, 7, 9)),
            play("b", at(2026, 10, 6, 21)),
            play("c", at(2026, 10, 7, 21)), play("c", at(2026, 10, 8, 10)),
        )
        val r = LibraryStats.compute(plays, songs, from, zone)
        assertEquals(6, r.plays)
        assertEquals(6 * 120_000L, r.listenedMs)
        assertEquals(listOf("Glass Harbor", "Undertow", "Paper Saints"), r.topSongs.map { it.label })
        assertEquals(listOf("The Lanterns" to 5, "Mara Vell" to 1), r.topArtists.map { it.label to it.plays })   // primary artist
        assertEquals(listOf("Low Tide" to 5, "Kindling" to 1), r.topAlbums.map { it.label to it.plays })
        assertEquals(2, r.newCount)
        assertEquals(listOf("Undertow", "Paper Saints"), r.newToYou.map { it.label })
        assertEquals(21, r.favoriteHour)
    }

    @Test fun nothingPlayedIsEmpty() {
        val r = LibraryStats.compute(emptyList(), emptyMap(), 0, zone)
        assertEquals(0, r.plays)
        assertNull(r.favoriteHour)
    }

    @Test fun labels() {
        assertEquals("under a minute", LibraryStats.duration(30_000))
        assertEquals("45 min", LibraryStats.duration(45 * 60_000L))
        assertEquals("3 h 20 min", LibraryStats.duration(200 * 60_000L))
        assertEquals("2 h", LibraryStats.duration(120 * 60_000L))
        assertEquals("12 AM", LibraryStats.hourLabel(0))
        assertEquals("9 PM", LibraryStats.hourLabel(21))
        assertEquals("12 PM", LibraryStats.hourLabel(12))
    }
}
