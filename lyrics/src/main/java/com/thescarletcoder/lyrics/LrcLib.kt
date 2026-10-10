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

    /** Closest duration within 10 s; synced beats plain. Null if nothing is close enough. */
    fun best(cands: List<Candidate>, durationSec: Double?): Candidate? {
        val usable = cands.filter { it.synced != null || it.plain != null || it.instrumental }
        val near = if (durationSec == null || durationSec <= 0) usable else usable.filter { abs(it.durationSec - durationSec) <= 10 }
        return near.sortedWith(compareBy<Candidate>({ it.synced == null }, { durationSec?.let { d -> abs(it.durationSec - d) } ?: 0.0 })).firstOrNull()
    }
}
