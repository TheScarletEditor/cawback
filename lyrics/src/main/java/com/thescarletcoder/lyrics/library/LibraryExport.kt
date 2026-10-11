package com.thescarletcoder.lyrics.library

import java.time.Instant
import java.time.ZoneId

/**
 * Library > Export (scope section 6): plain text (built for reading or pasting into an AI) or CSV. Only ever made
 * when the user taps Export; it goes only where they send it. Reads only the Library, which holds no lyrics, so no
 * export can contain any. Pure, so it is unit tested.
 */
object LibraryExport {
    enum class Format(val label: String, val ext: String, val mime: String) {
        TEXT("Plain text", "txt", "text/plain"), CSV("CSV (spreadsheets)", "csv", "text/csv")
    }

    enum class Which(val label: String) {
        ALL("Everything"), RANGE("Date range"), FAVORITES("Favorites"), WITH_NOTES("Songs with notes"), ARTIST("One artist")
    }

    data class Options(
        val which: Which = Which.ALL,
        /** RANGE: plays from [from] (inclusive) to [to] (exclusive), epoch ms. */
        val from: Long? = null,
        val to: Long? = null,
        /** ARTIST: the primary artist, any case. */
        val artist: String? = null,
        val counts: Boolean = true,
        val notes: Boolean = true,
        val moments: Boolean = true,
        /** The AI-ready paragraph at the top (decision 4: on by default). */
        val aiHeader: Boolean = true,
        val format: Format = Format.TEXT,
    )

    data class Row(val song: Song, val plays: Int, val first: Long, val last: Long?, val moments: List<Moment>)

    /** The songs an export covers, most played first. */
    fun rows(snap: LibrarySnapshot, o: Options): List<Row> {
        val inRange = { t: Long -> (o.from == null || t >= o.from) && (o.to == null || t < o.to) }
        val plays = (if (o.which == Which.RANGE) snap.plays.filter { inRange(it.startedAt) } else snap.plays).groupBy { it.songUid }
        val moments = snap.moments.groupBy { it.songUid }
        return snap.songs
            .filter {
                when (o.which) {
                    Which.ALL -> true
                    Which.RANGE -> plays.containsKey(it.uid)
                    Which.FAVORITES -> it.favorite
                    Which.WITH_NOTES -> it.note.isNotBlank()
                    Which.ARTIST -> SongKey.primaryArtist(it.artist).equals(o.artist?.trim(), ignoreCase = true)
                }
            }
            .map { s ->
                val ps = plays[s.uid].orEmpty()
                Row(s, ps.size, s.createdAt, ps.maxOfOrNull { it.startedAt }, moments[s.uid].orEmpty().sortedBy { it.atMs })
            }
            .sortedWith(compareByDescending<Row> { it.plays }.thenBy { it.song.title.lowercase() }.thenBy { it.song.artist.lowercase() })
    }

    fun build(snap: LibrarySnapshot, o: Options, now: Long, zone: ZoneId): String {
        val rows = rows(snap, o)
        return if (o.format == Format.CSV) csv(rows, o, zone) else text(rows, o, now, zone)
    }

    private fun day(ms: Long, zone: ZoneId) = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate().toString()
    private fun clock(ms: Long) = "%d:%02d".format(ms / 60_000, (ms / 1000) % 60)
    private fun count(n: Int) = "%,d".format(n)

    private fun text(rows: List<Row>, o: Options, now: Long, zone: ZoneId): String = buildString {
        val totalPlays = rows.sumOf { it.plays }
        appendLine("Scarlet Lyrics library")
        val span = when {
            o.which == Which.RANGE -> listOfNotNull(o.from?.let { day(it, zone) }, o.to?.let { day(it - 1, zone) }).joinToString(" to ")
            else -> rows.mapNotNull { r -> r.last }.let { lasts ->
                val firsts = rows.map { it.first }
                if (firsts.isEmpty()) "" else day(firsts.min(), zone) + " to " + day((lasts + firsts).max(), zone)
            }
        }
        appendLine(listOfNotNull(
            "Exported ${day(now, zone)}",
            if (o.which == Which.ALL || o.which == Which.RANGE) null else o.which.label + (if (o.which == Which.ARTIST) ": ${o.artist?.trim()}" else ""),
            "${count(rows.size)} songs",
            if (o.counts) "${count(totalPlays)} plays" else null,
            span.ifBlank { null },
        ).joinToString(" · "))
        if (o.aiHeader) {
            appendLine()
            appendLine(
                "About this file: a personal music library exported from Scarlet Lyrics. Each line starting with \"- \" is one song: " +
                    (if (o.counts) "title | artist | album | times played | first heard | last heard (dates are YYYY-MM-DD)" else "title | artist | album") +
                    ". A blank album means none was known." +
                    (if (o.notes || o.moments) " Lines indented under a song are the listener's own words: a Note about the song, or a Moment (a time in the song, minutes:seconds, with the listener's label)." else "") +
                    (if (o.counts && o.which == Which.RANGE) " Play counts cover the date range only." else "") +
                    " It contains no lyrics."
            )
        }
        val (favs, rest) = rows.partition { it.song.favorite }
        fun section(title: String, list: List<Row>) {
            if (list.isEmpty()) return
            appendLine(); appendLine(title)
            list.forEach { r ->
                val s = r.song
                val cols = mutableListOf(oneLine(s.title), oneLine(s.artist), oneLine(s.album.orEmpty()))
                if (o.counts) cols += listOf(r.plays.toString(), day(r.first, zone), r.last?.let { day(it, zone) } ?: "")
                appendLine("- " + cols.joinToString(" | "))
                if (o.notes && s.note.isNotBlank()) appendLine("    Note: " + s.note.trim().replace("\n", "\n          "))
                if (o.moments) r.moments.forEach { m -> appendLine("    Moment ${clock(m.atMs)}" + if (m.label.isNotBlank()) ": ${oneLine(m.label).trim()}" else "") }
            }
        }
        if (o.which == Which.FAVORITES) section("Favorites", rows)
        else { section("Favorites", favs); section(if (favs.isEmpty()) "Songs" else "Everything else", rest) }
    }

    private fun csv(rows: List<Row>, o: Options, zone: ZoneId): String = buildString {
        val head = mutableListOf("title", "artist", "album", "favorite")
        if (o.counts) head += listOf("plays", "first_heard", "last_heard")
        if (o.notes) head += "note"
        if (o.moments) head += "moments"
        append(head.joinToString(",")).append("\r\n")
        rows.forEach { r ->
            val s = r.song
            val cells = mutableListOf(s.title, s.artist, s.album.orEmpty(), if (s.favorite) "yes" else "")
            if (o.counts) cells += listOf(r.plays.toString(), day(r.first, zone), r.last?.let { day(it, zone) } ?: "")
            if (o.notes) cells += s.note.trim()
            if (o.moments) cells += r.moments.joinToString("; ") { m -> clock(m.atMs) + if (m.label.isNotBlank()) " " + m.label.trim() else "" }
            append(cells.joinToString(",") { cell(it) }).append("\r\n")
        }
    }

    /**
     * One CSV cell, quoted when needed. A cell a spreadsheet would run as a formula (=, +, -, @) gets a leading
     * apostrophe, so a song title can't become a formula in a spreadsheet.
     */
    fun cell(v: String): String {
        val safe = if (v.isNotEmpty() && v[0] in "=+-@\t\r") "'$v" else v
        return if (safe.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + safe.replace("\"", "\"\"") + "\"" else safe
    }

    /** Titles come from other apps' media sessions: a line break or control character in one can't fake extra lines. */
    private fun oneLine(s: String) = s.replace(Regex("\\p{Cntrl}"), " ")
}
