package com.thescarletcoder.lyrics

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.thescarletcoder.lyrics.library.SongKey
import org.json.JSONException
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

    data class Entry(val candidate: Candidate?, val pinned: Boolean, val offsetMs: Long, val fetchedAt: Long)

    private fun file(key: String) = File(dir, "${hash(key)}.json")

    fun load(key: String): Entry? {
        val f = file(key)
        val o = synchronized(LOCK) {
            if (!f.exists()) return null
            // A passing error: try again next time. Never readable: deleted.
            runCatching { read(f) }.getOrElse { return null } ?: run { f.delete(); return null }
        }
        return runCatching {
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
    }

    fun save(key: String, e: Entry) {
        val o = JSONObject()
            .put("pinned", e.pinned).put("offset", e.offsetMs).put("at", e.fetchedAt)
        // The song this belongs to, so "Forget this song" finds every cached version of it (Track.key: title, artist, ...).
        key.split('\u0001').takeIf { it.size >= 2 }?.let { o.put("s", SongKey.of(it[0], it[1])) }
        e.candidate?.let {
            o.put("c", JSONObject()
                .put("id", it.id).put("track", it.track).put("artist", it.artist).put("album", it.album)
                .put("duration", it.durationSec).put("instrumental", it.instrumental)
                .put("plain", it.plain ?: "").put("synced", it.synced ?: ""))
        }
        runCatching { synchronized(LOCK) { write(file(key), o) } }   // only a cache: a failed write means a fresh fetch next time
    }

    /**
     * Settings: "Reset all timing nudges". Every song's offset back to 0; lyrics and picks stay. False if any file
     * couldn't be read or written this time (a file that can never be read has no nudge to reset and doesn't count).
     */
    fun resetOffsets(): Boolean {
        var ok = true
        files().forEach { f ->
            synchronized(LOCK) {
                val o = try { read(f) } catch (_: Exception) { ok = false; null } ?: return@forEach
                if (o.optLong("offset") != 0L && runCatching { write(f, o.put("offset", 0L)) }.isFailure) ok = false
            }
        }
        return ok
    }

    /** "Forget this song" in the Library: its cached lyrics, by the hashed keys the Library kept. */
    fun forgetHashed(hashes: List<String>) {
        hashes.filter { it.matches(Regex("[0-9a-f]{64}")) }.forEach { synchronized(LOCK) { File(dir, "$it.json").delete() } }
    }

    /**
     * "Forget this song", for the versions the Library never saw (another album or length, never counted): every
     * cached file of the same song. 0.2.0 files don't say which song they're for, so their lyrics' title and artist decide.
     */
    fun forgetSong(songKey: String) {
        files().forEach { f ->
            synchronized(LOCK) {
                val o = runCatching { read(f) }.getOrNull() ?: return@forEach
                val s = o.optString("s").ifBlank { null }
                    ?: o.optJSONObject("c")?.let { SongKey.of(it.optString("track"), it.optString("artist")) }
                if (s == songKey) f.delete()
            }
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

    private fun write(f: File, o: JSONObject) {
        val tmp = File(dir, f.name + ".tmp")
        tmp.writeBytes(CacheCipher.seal(o.toString()))
        tmp.renameTo(f)
    }

    companion object {
        /** Every read-modify-write of a cache file, across all instances (startup sealing runs beside lookups). */
        private val LOCK = Any()

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
