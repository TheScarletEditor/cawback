package com.thescarletcoder.lyrics

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.thescarletcoder.lyrics.library.SongKey
import com.thescarletcoder.lyrics.library.TimingFix
import org.json.JSONException
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Small on-device cache: fetched lyrics, "Other versions" picks and timing nudges, one file per track.
 * File names are hashes, so the folder doesn't read as a listening history. Excluded from every backup.
 *
 * Encrypted at rest (AES-GCM with a non-exportable Android Keystore key). 0.2.0 wrote plain JSON: [sealOldFiles]
 * encrypts those when Lyrics starts, and [load] reads either. A file that can't be opened any more (the key is gone)
 * is only a cache: it is deleted and the lyrics are fetched again.
 */
class LyricsStore(context: Context) {
    private val dir = File(context.filesDir, "lyrics").apply { mkdirs() }

    /**
     * [matchVersion]: the matching rules this automatic match was made with (LyricsRepo.MATCHING; 0 before 0.5.0 or
     * when the search failed), so older matches are looked up again. [unsure]: the records disagreed or none fitted.
     * [anchors]: the user's tapped lines (0.5.0); with [offsetMs] they are the user's timing, which wins.
     */
    data class Entry(
        val candidate: Candidate?, val pinned: Boolean, val offsetMs: Long, val fetchedAt: Long,
        val matchVersion: Int = 0, val unsure: Boolean = false, val anchors: List<Anchor> = emptyList(),
        /** A restored fix (0.5.0): the version to fetch by id the next time the song plays; no lyrics yet. */
        val pendingId: Long? = null,
        /** The timing was lined up by ear (Line up now or Fix lines), so it can be shared (owner, 0.5.0). */
        val tapped: Boolean = false,
        /** When this timing was shared with LRCLIB (0: not shared, or changed since). */
        val sentAt: Long = 0,
    )

    private fun file(key: String) = File(dir, "${hash(key)}.json")

    /** Saved but can't be read just now (a Keystore hiccup): nothing may be written over it. */
    class Busy : java.io.IOException()

    /** What reading a file gave: its contents, never readable (may be deleted), or still failing after [GIVE_UP_MS]. */
    private sealed class Read {
        class Ok(val o: JSONObject) : Read()
        object Never : Read()
        object GaveUp : Read()
    }

    /**
     * Reads [f] (under [LOCK]). Throws [Busy] on an error that may pass. A file still failing [GIVE_UP_MS] after its
     * first failure (in this run) counts as unreadable, so one bad file can't block its song or every backup for good;
     * it is never deleted for that, only written over by the next lookup, as before 0.5.0.
     */
    private fun readOrBusy(f: File): Read {
        val o = try {
            read(f)
        } catch (_: Exception) {
            val now = System.currentTimeMillis()
            val first = failingSince.putIfAbsent(f.name, now) ?: now
            if (now - first < GIVE_UP_MS) throw Busy()
            return Read.GaveUp
        }
        failingSince.remove(f.name)
        return if (o == null) Read.Never else Read.Ok(o)
    }

    /** null when nothing is saved. Throws [Busy] on a passing error, so a caller never mistakes it for nothing saved. */
    fun load(key: String): Entry? {
        val f = file(key)
        val o = synchronized(LOCK) {
            if (!f.exists()) return null
            when (val r = readOrBusy(f)) {
                is Read.Ok -> r.o
                Read.Never -> { f.delete(); return null }   // never readable: deleted
                Read.GaveUp -> return null
            }
        }
        return runCatching { decode(o) }.getOrNull()
    }

    fun save(key: String, e: Entry) {
        // The song this belongs to, so "Forget this song" finds every cached version of it (Track.key: title, artist, ...).
        val song = key.split('\u0001').takeIf { it.size >= 2 }?.let { SongKey.of(it[0], it[1]) }
        val o = encode(e, song)
        runCatching { synchronized(LOCK) { write(file(key), o) } }   // only a cache: a failed write means a fresh fetch next time
    }

    /**
     * Settings: "Reset all timing". Every song's nudge and fixed lines back to 0; lyrics and picks stay. False if any file
     * couldn't be read or written this time (a file that can never be read has no nudge to reset and doesn't count).
     */
    fun resetOffsets(): Boolean {
        var ok = true
        files().forEach { f ->
            synchronized(LOCK) {
                if (!f.exists()) return@forEach   // forgotten or cleared meanwhile: nothing to reset, not a failure
                val o = try { read(f) } catch (_: Exception) { ok = false; null } ?: return@forEach
                if (o.optLong("offset") == 0L && o.optJSONArray("anchors").let { it == null || it.length() == 0 } && !o.optBoolean("t")) return@forEach
                o.put("offset", 0L).remove("anchors"); o.remove("t"); o.remove("sent")
                if (!runCatching { write(f, o) }.getOrDefault(false)) ok = false   // a failed replace is a failure too
            }
        }
        return ok
    }

    /** "Forget this song" in the Library: its cached lyrics, by the hashed keys the Library kept. */
    fun forgetHashed(hashes: List<String>) {
        hashes.filter { it.matches(HASH) }.forEach { synchronized(LOCK) { File(dir, "$it.json").delete() } }
    }

    /**
     * "Forget this song", for the versions the Library never saw (another album or length, never counted): every
     * cached file of the same song. 0.2.0 files don't say which song they're for, so their lyrics' title and artist decide.
     */
    fun forgetSong(songKey: String) {
        files().forEach { f ->
            synchronized(LOCK) {
                val o = runCatching { read(f) }.getOrNull() ?: return@forEach
                if (songOf(o) == songKey) f.delete()
            }
        }
    }

    /** The song a saved file belongs to: "s", or for files saved before "s" existed, the saved version's title and artist. */
    private fun songOf(o: JSONObject): String? =
        o.optString("s").ifBlank { null } ?: o.optJSONObject("c")?.let { SongKey.of(it.optString("track"), it.optString("artist")) }

    /**
     * The user's timing and picks, for the Library backup (0.5.0, owner Q25): never lyric text. Each carries its song,
     * so Forget reaches a restored fix before it plays. Throws [Busy] when a file can't be read just now: a backup that
     * would quietly leave timing out fails instead (R6).
     */
    fun fixes(): List<TimingFix> = files().mapNotNull { f ->
        val o = (synchronized(LOCK) { readOrBusy(f) } as? Read.Ok)?.o ?: return@mapNotNull null
        fixOf(f.name.removeSuffix(".json"), runCatching { decode(o) }.getOrNull() ?: return@mapNotNull null, songOf(o))
    }

    data class FixesRestored(val now: Int, val later: Int, val failed: Int)

    /**
     * Restored fixes (owner: the device's own timing or pick wins). A fix for a version this device already has is
     * applied at once; otherwise the song fetches it by id on its next play. Counts how many were applied now and how
     * many wait for their song to play (finalize 0.5.0 Q4: counted apart), and how many couldn't be written (R6).
     */
    fun restoreFixes(fixes: List<TimingFix>): FixesRestored {
        var now = 0
        var later = 0
        var failed = 0
        fixes.forEach { fix -> when (restoreFix(fix)) { NOW -> now++; LATER -> later++; FAILED -> failed++ } }
        return FixesRestored(now, later, failed)
    }

    /** [NOW]: applied; [LATER]: waits for its song to play; [KEPT]: this device's own wins; [FAILED]: not written. */
    private fun restoreFix(fix: TimingFix): Int {
        if (!HASH.matches(fix.key)) return KEPT
        val f = File(dir, "${fix.key}.json")
        synchronized(LOCK) {
            // A file here that can't be read just now (a busy key store) or decoded is kept: it may hold the user's own
            // pick and timing, which win. Only a file that can never be read (read gives null) counts as nothing here.
            val existing = if (f.exists()) runCatching { read(f) }.getOrElse { return FAILED } else null
            val mine = existing?.let { runCatching { decode(it) }.getOrElse { return KEPT } }
            val next = merged(mine, fix) ?: return KEPT
            val o = encode(next, existing?.optString("s")?.ifBlank { null } ?: fix.song)
            return if (!runCatching { write(f, o) }.getOrDefault(false)) FAILED else if (next.candidate != null) NOW else LATER
        }
    }

    /** Encrypts the plain files 0.2.0 left. Run when Lyrics starts, off the main thread. */
    fun sealOldFiles() {
        files().forEach { f ->
            synchronized(LOCK) {   // one file at a time, so lookups and nudges carry on in between
                val bytes = runCatching { f.readBytes() }.getOrNull() ?: return@forEach
                if (CacheCipher.sealed(bytes)) return@forEach
                val o = try { JSONObject(bytes.toString(Charsets.UTF_8)) } catch (_: JSONException) { f.delete(); return@forEach }
                runCatching { write(f, o) }   // if sealing fails now, the plain file stays for the next start
            }
        }
    }

    /**
     * Wipes everything (Settings: "Clear saved lyrics"). Each file under the lock, so a nudge or a reset can't write
     * one back halfway. False if any file that was there couldn't be deleted (a song fetched meanwhile is new, not a failure).
     */
    fun clear(): Boolean {
        var ok = true
        dir.listFiles()?.forEach { f -> synchronized(LOCK) { if (!f.delete() && f.exists()) ok = false } }
        return ok
    }
    fun count() = files().size

    private fun files() = dir.listFiles()?.filter { it.name.endsWith(".json") }.orEmpty()

    /**
     * null when the file can never be read (damaged, or sealed with a key that's gone), so it may be deleted.
     * Throws on anything that may pass, like a Keystore hiccup: the file must stay (it can hold the user's picks and nudges).
     */
    private fun read(f: File): JSONObject? {
        val bytes = f.readBytes()
        val text = if (CacheCipher.sealed(bytes)) {
            try { CacheCipher.open(bytes) } catch (_: AEADBadTagException) { return null }
        } else bytes.toString(Charsets.UTF_8)
        return try { JSONObject(text) } catch (_: JSONException) { null }
    }

    /** False when the new file couldn't take the old one's place. */
    private fun write(f: File, o: JSONObject): Boolean {
        val tmp = File(dir, f.name + ".tmp")
        tmp.writeBytes(CacheCipher.seal(o.toString()))
        return tmp.renameTo(f)
    }

    companion object {
        private const val NOW = 0
        private const val LATER = 1
        private const val KEPT = 2
        private const val FAILED = 3

        /** How long a file may keep failing to read before it counts as unreadable. */
        private const val GIVE_UP_MS = 3_600_000L
        private val failingSince = java.util.concurrent.ConcurrentHashMap<String, Long>()

        /** Every read-modify-write of a cache file, across all instances (startup sealing runs beside lookups). */
        private val LOCK = Any()

        /** An entry as saved (pure, so the format is unit tested). [song]: the song key, for "Forget this song". */
        fun encode(e: Entry, song: String? = null): JSONObject {
            val o = JSONObject()
                .put("pinned", e.pinned).put("offset", e.offsetMs).put("at", e.fetchedAt)
                .put("mv", e.matchVersion).put("u", e.unsure)
            e.pendingId?.let { o.put("pid", it) }
            if (e.tapped) o.put("t", true)
            if (e.sentAt > 0) o.put("sent", e.sentAt)
            if (e.anchors.isNotEmpty()) o.put("anchors", JSONArray().apply { e.anchors.forEach { put(JSONArray().put(it.lineMs).put(it.deltaMs)) } })
            song?.let { o.put("s", it) }
            e.candidate?.let {
                o.put("c", JSONObject()
                    .put("id", it.id).put("track", it.track).put("artist", it.artist).put("album", it.album)
                    .put("duration", it.durationSec).put("instrumental", it.instrumental)
                    .put("plain", it.plain ?: "").put("synced", it.synced ?: ""))
            }
            return o
        }

        /** A fix worth keeping: a version with a pick, a nudge or tapped lines. */
        fun fixOf(key: String, e: Entry, song: String?): TimingFix? {
            val id = e.candidate?.id ?: e.pendingId ?: return null
            if (!e.pinned && e.offsetMs == 0L && e.anchors.isEmpty()) return null
            return TimingFix(key, id, e.pinned, e.offsetMs, e.anchors, song, e.tapped)
        }

        /**
         * What a restored [fix] makes of this device's entry (owner: the device's wins): null to leave it. Same version
         * and no timing here: the timing is added. Otherwise, with nothing of the user's here: the version to fetch.
         */
        fun merged(existing: Entry?, fix: TimingFix): Entry? {
            if (existing != null && (existing.pinned || existing.offsetMs != 0L || existing.anchors.isNotEmpty() || existing.pendingId != null)) return null
            // Marked current, so the next play doesn't match again and drop the timing.
            if (existing?.candidate?.id == fix.versionId) return existing!!.copy(
                pinned = fix.pinned || existing.pinned, offsetMs = fix.offsetMs, anchors = fix.anchors, matchVersion = LyricsRepo.MATCHING,
                tapped = fix.tapped)
            return Entry(null, fix.pinned, fix.offsetMs, 0, anchors = fix.anchors, pendingId = fix.versionId, tapped = fix.tapped)
        }

        /** An entry as read; entries from before 0.5.0 have no matching version and no anchors. */
        fun decode(o: JSONObject): Entry = Entry(
            candidate = o.optJSONObject("c")?.let {
                Candidate(
                    it.getLong("id"), it.optString("track"), it.optString("artist"), it.optString("album"),
                    it.optDouble("duration", 0.0), it.optBoolean("instrumental"),
                    it.optString("plain").ifBlank { null }, it.optString("synced").ifBlank { null },
                )
            },
            pinned = o.optBoolean("pinned"),
            pendingId = if (o.has("pid")) o.optLong("pid") else null,
            tapped = o.optBoolean("t"),
            sentAt = o.optLong("sent"),
            offsetMs = o.optLong("offset"),
            fetchedAt = o.optLong("at"),
            matchVersion = o.optInt("mv"),
            unsure = o.optBoolean("u"),
            anchors = o.optJSONArray("anchors")?.let { arr ->
                // A damaged pair is skipped on its own, so it can't cost the pick, the nudge or the other anchors.
                (0 until arr.length()).mapNotNull { i ->
                    arr.optJSONArray(i)?.takeIf { it.length() == 2 && it.opt(0) is Number && it.opt(1) is Number }
                        ?.let { Anchor(it.getLong(0), it.getLong(1)) }
                }
            }.orEmpty(),
        )
        private val HASH = Regex("[0-9a-f]{64}")   // a cache file's name: a SHA-256 in hex

        /** A track key as it's stored anywhere on disk: SHA-256, hex. */
        fun hash(key: String): String =
            MessageDigest.getInstance("SHA-256").digest(key.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}

/** "LYC1" + 12-byte IV + AES-256-GCM, with a Keystore key that never leaves the device. Not bound to screen unlock. */
private object CacheCipher {
    private const val ALIAS = "lyrics_cache_v1"
    private val MAGIC = "LYC1".toByteArray(Charsets.US_ASCII)
    private const val IV_BYTES = 12

    fun sealed(bytes: ByteArray) = bytes.size > MAGIC.size + IV_BYTES && MAGIC.indices.all { bytes[it] == MAGIC[it] }

    fun seal(text: String): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        return MAGIC + cipher.iv + cipher.doFinal(text.toByteArray(Charsets.UTF_8))
    }

    fun open(bytes: ByteArray): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes, MAGIC.size, IV_BYTES))
        val start = MAGIC.size + IV_BYTES
        return cipher.doFinal(bytes, start, bytes.size - start).toString(Charsets.UTF_8)
    }

    @Synchronized private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }
}
