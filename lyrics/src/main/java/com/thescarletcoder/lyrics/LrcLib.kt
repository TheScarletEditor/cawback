package com.thescarletcoder.lyrics

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.abs

/** A lyrics record from LRCLIB. */
data class Candidate(
    val id: Long,
    val track: String,
    val artist: String,
    val album: String,
    val durationSec: Double,
    val instrumental: Boolean,
    val plain: String?,
    val synced: String?,
)

/** The only network client in Scarlet Lyrics. It can only reach https://lrclib.net. */
object LrcLib {
    private const val HOST = "lrclib.net"
    private val AGENT = "ScarletLyrics/${BuildConfig.VERSION_NAME.substringBefore('+')} (+https://github.com/TheScarletEditor/scarlet-lyrics)"

    class NotFound : IOException()

    /** Exact lookup (LRCLIB matches duration within about 2 seconds). Throws [NotFound] on 404. */
    fun get(track: String, artist: String, album: String?, durationSec: Double?): Candidate {
        val q = buildString {
            append("track_name=").append(enc(track)).append("&artist_name=").append(enc(artist))
            if (!album.isNullOrBlank()) append("&album_name=").append(enc(album))
            if (durationSec != null && durationSec > 0) append("&duration=").append(Math.round(durationSec))
        }
        return parse(JSONObject(fetch("/api/get?$q")))
    }

    fun byId(id: Long): Candidate = parse(JSONObject(fetch("/api/get/$id")))

    fun search(query: String): List<Candidate> = list("/api/search?q=${enc(query)}")

    /** Title only: for songs LRCLIB files under a different artist (a label, a composer) than the player shows. */
    fun searchTitle(track: String): List<Candidate> = list("/api/search?track_name=${enc(track)}")

    fun search(track: String, artist: String): List<Candidate> =
        list("/api/search?track_name=${enc(track)}&artist_name=${enc(artist)}")

    private fun list(pathAndQuery: String): List<Candidate> {
        val arr = JSONArray(fetch(pathAndQuery))
        return (0 until arr.length()).map { parse(arr.getJSONObject(it)) }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun fetch(pathAndQuery: String): String {
        val url = URL("https://$HOST$pathAndQuery")
        check(url.protocol == "https" && url.host == HOST)
        val c = url.openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 8000
            c.readTimeout = 12000
            c.instanceFollowRedirects = false
            c.setRequestProperty("User-Agent", AGENT)
            c.setRequestProperty("Accept", "application/json")
            when (c.responseCode) {
                200 -> return c.inputStream.bufferedReader().use { it.readText() }
                404 -> throw NotFound()
                else -> throw IOException("LRCLIB answered ${c.responseCode}")
            }
        } finally {
            c.disconnect()
        }
    }

    private fun parse(o: JSONObject) = Candidate(
        id = o.getLong("id"),
        track = o.optString("trackName"),
        artist = o.optString("artistName"),
        album = o.optString("albumName"),
        durationSec = o.optDouble("duration", 0.0),
        instrumental = o.optBoolean("instrumental"),
        plain = o.optString("plainLyrics").takeIf { !o.isNull("plainLyrics") && it.isNotBlank() },
        synced = o.optString("syncedLyrics").takeIf { !o.isNull("syncedLyrics") && it.isNotBlank() },
    )
}

/** Picking a match when the exact lookup misses. Pure, so it is unit tested. */
object Matching {
    private val dashSuffix = Regex("""\s+-\s+.*(remaster|remastered|version|edit|mix|mono|stereo|deluxe|single|bonus).*$""", RegexOption.IGNORE_CASE)
    private val feat = Regex("""\s*[(\[](feat\.?|ft\.?|with)\s[^)\]]*[)\]]""", RegexOption.IGNORE_CASE)

    /** "Song - Remastered 2011" -> "Song". Keeps "(Live)" etc., which change the lyrics. */
    fun cleanTitle(t: String) = t.replace(dashSuffix, "").replace(feat, "").trim()

    /**
     * How a version fits the song, for "Other versions" (0.5.0): [fits] false when a synced version's lyrics run past the
     * end; [agreeing] how many other versions on the list share its timing; [otherArtist] when the artist isn't the song's.
     */
    data class Fit(val fits: Boolean, val agreeing: Int, val otherArtist: Boolean)

    private val creditSplit = Regex("""\s*(?:,|&|/|;|\+|×|、|＆|\bfeat\.?|\bft\.?|\bfeaturing\b|\bwith\b|\bvs\.?|\bx\b)\s*""", RegexOption.IGNORE_CASE)
    private val unknownArtists = setOf("", "unknown", "unknownartist", "variousartists", "various")

    /** One name as compared: lower case, "$" as "s" (Ke$ha), no leading "the", letters and digits only (any script). */
    private fun norm(s: String) = s.lowercase().replace('$', 's').trim().removePrefix("the ").filter { it.isLetterOrDigit() }

    /** The names in a credit ("Pitbull feat. Kesha", "Mike Shinoda/X-Ecutioners"), each as compared. */
    fun names(credit: String): Set<String> = credit.split(creditSplit).map { norm(it) }.filter { it.isNotEmpty() }.toSet()

    /** False when the credit says nothing about who it is (blank, "<unknown>", "Various Artists"). */
    fun knownArtist(credit: String) = norm(credit) !in unknownArtists && names(credit).isNotEmpty()

    /** True when the two credits share a whole name ("X-Ecutioners, Mike Shinoda" and "The X-Ecutioners" do). */
    fun sameArtist(songArtist: String, candidate: String): Boolean = names(songArtist).intersect(names(candidate)).isNotEmpty()

    /** How each version fits; [ordered] is the list best fit first (owner, 0.5.0). Work it out off the main thread. */
    class Fits(val ordered: List<Candidate>, val fit: Map<Long, Fit>)

    fun fitOf(cands: List<Candidate>, durationSec: Double?, songArtist: String): Map<Long, Fit> {
        val t = cands.associate { it.id to times(it) }
        val artistKnown = knownArtist(songArtist)
        return cands.associate { c ->
            val mine = t.getValue(c.id)
            c.id to Fit(
                fits = mine.isEmpty() || durationSec == null || durationSec <= 0 || mine.last() <= durationSec + FIT_MARGIN_SEC,
                agreeing = if (mine.isEmpty()) 0 else cands.count { o -> o.id != c.id && agree(mine, t.getValue(o.id)) },
                otherArtist = artistKnown && knownArtist(c.artist) && !sameArtist(songArtist, c.artist),
            )
        }
    }

    /**
     * Best fit first (owner, 0.5.0: fitting, agreeing, same artist, then length). Fitting synced versions come first,
     * then words only, then synced versions that run past the end (that middle place wasn't decided; words don't claim
     * a timing). Each group by agreement, then the song's artist, then the closest length.
     */
    fun bestFirst(cands: List<Candidate>, durationSec: Double?, songArtist: String): Fits {
        val f = fitOf(cands, durationSec, songArtist)
        val synced = cands.associate { it.id to (it.synced != null && times(it).isNotEmpty()) }
        fun kind(c: Candidate) = when { synced.getValue(c.id).not() -> 1; f.getValue(c.id).fits -> 0; else -> 2 }
        val known = durationSec != null && durationSec > 0
        val ordered = cands.sortedWith(compareBy<Candidate>({ kind(it) }, { -f.getValue(it.id).agreeing }, { f.getValue(it.id).otherArtist },
            { if (known) abs(it.durationSec - durationSec!!) else 0.0 }))
        return Fits(ordered, f)
    }

    /** What Lyrics chose for a song, and whether it's unsure of the timing (the records disagree, or none fits). */
    data class Choice(val candidate: Candidate, val unsure: Boolean)

    /** A synced record fits the song when its last line comes no later than this after the song's end. */
    const val FIT_MARGIN_SEC = 5.0

    /** Two records agree when they have the same lines and nearly all of them start within this of each other. */
    const val AGREE_WITHIN_SEC = 0.5
    private const val AGREE_SHARE = 0.9

    /** Line start times of a synced record, in seconds (empty without synced lyrics). */
    fun times(c: Candidate): List<Double> = c.synced?.let { s -> Lrc.parseSynced(s).mapNotNull { it.timeMs?.div(1000.0) } }.orEmpty()

    /** False when the record's lyrics run past the end of the song: its timing belongs to a longer cut. */
    fun fits(c: Candidate, durationSec: Double?): Boolean {
        val t = times(c)
        return durationSec == null || durationSec <= 0 || t.isEmpty() || t.last() <= durationSec + FIT_MARGIN_SEC
    }

    fun agree(a: List<Double>, b: List<Double>): Boolean =
        a.isNotEmpty() && a.size == b.size && a.indices.count { abs(a[it] - b[it]) <= AGREE_WITHIN_SEC } >= AGREE_SHARE * a.size

    /**
     * Pick the record to show (0.5.0, owner Q16, Q26): only records within 10 s of the song's length; a synced record
     * that fits the song beats words only; among those, the timing most records agree on beats a lone upload, even the
     * exact hit; inside that group the exact hit, then the closest length, then more lines (D-038). No fitting synced
     * record: words only if there are any, else the closest synced record, marked unsure. Null when nothing is close.
     * Results of a title-only search never come here: another artist's song is never shown automatically.
     */
    fun choose(exact: Candidate?, results: List<Candidate>, durationSec: Double?): Choice? {
        val known = durationSec != null && durationSec > 0
        fun gap(c: Candidate) = if (known) abs(c.durationSec - durationSec!!) else 0.0
        val near = (listOfNotNull(exact) + results).distinctBy { it.id }
            .filter { it.synced != null || it.plain != null || it.instrumental }
            .filter { !known || gap(it) <= 10 }
        if (near.isEmpty()) return null
        val synced = near.filter { times(it).isNotEmpty() }
        val fitting = synced.filter { fits(it, durationSec) }
        if (fitting.isNotEmpty()) {
            val groups = mutableListOf<MutableList<Candidate>>()
            fitting.forEach { c ->
                val t = times(c)
                groups.firstOrNull { agree(times(it.first()), t) }?.add(c) ?: groups.add(mutableListOf(c))
            }
            val ranked = groups.sortedWith(
                compareByDescending<List<Candidate>> { it.size }
                    .thenByDescending { g -> exact != null && g.any { it.id == exact.id } }
                    .thenBy { g -> g.minOf { gap(it) } }
                    .thenByDescending { g -> g.maxOf { times(it).size } },
            )
            val best = ranked.first()
            val pick = best.firstOrNull { it.id == exact?.id }
                ?: best.sortedWith(compareBy<Candidate>({ gap(it) }, { -times(it).size })).first()
            // No majority: another timing has as many records behind it.
            return Choice(pick, unsure = ranked.size > 1 && ranked[1].size >= best.size)
        }
        // Words only is honest: records without usable timed lines (no synced lyrics, or only tags) count as words.
        near.filter { times(it).isEmpty() }
            .minWithOrNull(compareBy<Candidate>({ it.plain == null && !it.instrumental }, { gap(it) }))   // real words first
            ?.let { return Choice(it, unsure = false) }
        return Choice(synced.minBy { gap(it) }, unsure = true)   // only records that run past the song's end are left
    }
}
