package com.thescarletcoder.lyrics

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/**
 * Small on-device cache: fetched lyrics, "wrong lyrics" picks and timing nudges, one file per track.
 * File names are hashes, so the folder doesn't read as a listening history. Excluded from every backup.
 */
class LyricsStore(context: Context) {
    private val dir = File(context.filesDir, "lyrics").apply { mkdirs() }

    data class Entry(val candidate: Candidate?, val pinned: Boolean, val offsetMs: Long, val fetchedAt: Long)

    private fun file(key: String): File {
        val h = MessageDigest.getInstance("SHA-256").digest(key.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(dir, "$h.json")
    }

    fun load(key: String): Entry? = runCatching {
        val o = JSONObject(file(key).takeIf { it.exists() }?.readText() ?: return null)
        Entry(
            candidate = o.optJSONObject("c")?.let {
                Candidate(
                    it.getLong("id"), it.optString("track"), it.optString("artist"), it.optString("album"),
                    it.optDouble("duration", 0.0), it.optBoolean("instrumental"),
                    it.optString("plain").ifBlank { null }, it.optString("synced").ifBlank { null },
                )
            },
            pinned = o.optBoolean("pinned"),
            offsetMs = o.optLong("offset"),
            fetchedAt = o.optLong("at"),
        )
    }.getOrNull()

    fun save(key: String, e: Entry) {
        val o = JSONObject()
            .put("pinned", e.pinned).put("offset", e.offsetMs).put("at", e.fetchedAt)
        e.candidate?.let {
            o.put("c", JSONObject()
                .put("id", it.id).put("track", it.track).put("artist", it.artist).put("album", it.album)
                .put("duration", it.durationSec).put("instrumental", it.instrumental)
                .put("plain", it.plain ?: "").put("synced", it.synced ?: ""))
        }
        val f = file(key)
        val tmp = File(dir, f.name + ".tmp")
        tmp.writeText(o.toString())
        tmp.renameTo(f)
    }

    /** Settings: "Reset all timing nudges". Every song's offset back to 0; lyrics and picks stay. */
    fun resetOffsets() {
        dir.listFiles()?.filter { it.name.endsWith(".json") }?.forEach { f ->
            runCatching {
                val o = JSONObject(f.readText())
                if (o.optLong("offset") != 0L) {
                    o.put("offset", 0L)
                    val tmp = File(dir, f.name + ".tmp")
                    tmp.writeText(o.toString())
                    tmp.renameTo(f)
                }
            }
        }
    }

    /** Wipes everything (Settings: "Clear saved lyrics"). */
    fun clear() { dir.listFiles()?.forEach { it.delete() } }
    fun count() = dir.listFiles()?.count { it.name.endsWith(".json") } ?: 0
}
