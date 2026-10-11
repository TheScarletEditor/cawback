package com.thescarletcoder.lyrics.library

import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import java.util.Base64
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * "Back up the Library" (scope section 6; ruling of 2026-10-09: protected by a passphrase, like Scarlet Command's backups).
 *
 * File = "LYRBAK01" + header length + JSON header (format, Argon2id parameters, salt, created) + 12-byte IV +
 * AES-256-GCM of the gzipped LibrarySnapshot JSON. The key comes from the user's passphrase through Argon2id (64 MiB,
 * 3 passes), and the header is authenticated too, so nothing in the file can be changed unnoticed. Standard parts
 * only: Bouncy Castle's Argon2 and Android's AES-GCM. The passphrase is never stored.
 */
object LibraryBackup {
    private const val MAGIC = "LYRBAK01"
    private const val FORMAT = 1
    const val MEMORY_KIB = 64 * 1024
    private const val MAX_MEMORY_KIB = 256 * 1024
    private const val IV_BYTES = 12
    // Far beyond any real Library (no lyrics are in it), small enough that a crafted file can't exhaust memory.
    private const val MAX_SEALED = 64 * 1024 * 1024
    private const val MAX_UNPACKED = 128 * 1024 * 1024
    const val PASSES = 3
    const val LANES = 2
    const val MIN_PASSPHRASE = 8
    const val EXTENSION = "lyrbak"

    class WrongPassphrase : Exception("That passphrase doesn't open this backup, or the file is damaged or incomplete.")
    const val NEWER = "This backup is from a newer Scarlet Lyrics. Update Lyrics, then restore."
    private const val TOO_LARGE = "This backup is too large to be a Scarlet Lyrics Library."

    /** A backup that is recognised but can't be read here; the message is meant for the user. */
    class Unreadable(message: String) : IllegalArgumentException(message)
    class NotABackup : Exception("This isn't a Scarlet Lyrics Library backup.")

    data class Params(val memoryKiB: Int = MEMORY_KIB, val passes: Int = PASSES, val lanes: Int = LANES)

    fun write(out: OutputStream, snap: LibrarySnapshot, passphrase: CharArray, now: Long = System.currentTimeMillis(), params: Params = Params()) {
        require(passphrase.size >= MIN_PASSPHRASE) { "passphrase too short" }
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val header = JSONObject().put("format", FORMAT).put("kdf", "Argon2id").put("m", params.memoryKiB).put("t", params.passes)
            .put("p", params.lanes).put("salt", Base64.getEncoder().encodeToString(salt)).put("created", now)
            .toString().toByteArray(Charsets.UTF_8)
        val plain = ByteArrayOutputStream().also { b -> GZIPOutputStream(b).use { it.write(snap.toJson().toString().toByteArray(Charsets.UTF_8)) } }.toByteArray()
        val key = argon2(passphrase, salt, params)
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val iv = ByteArray(IV_BYTES).also { SecureRandom().nextBytes(it) }
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
            cipher.updateAAD(MAGIC.toByteArray(Charsets.US_ASCII) + header)
            val sealed = cipher.doFinal(plain)
            DataOutputStream(out).apply {
                write(MAGIC.toByteArray(Charsets.US_ASCII)); writeInt(header.size); write(header); write(iv); write(sealed); flush()
            }
        } finally { key.fill(0); plain.fill(0) }
    }

    fun read(input: InputStream, passphrase: CharArray): LibrarySnapshot {
        val din = DataInputStream(input)
        val magic = ByteArray(MAGIC.length)
        runCatching { din.readFully(magic) }.onFailure { throw NotABackup() }
        if (String(magic, Charsets.US_ASCII) != MAGIC) throw NotABackup()
        val len = din.readInt()
        if (len !in 1..4096) throw NotABackup()
        val header = ByteArray(len).also { din.readFully(it) }
        val h = runCatching { JSONObject(String(header, Charsets.UTF_8)) }.getOrElse { throw NotABackup() }
        if (h.optInt("format") != FORMAT || h.optString("kdf") != "Argon2id") throw Unreadable(NEWER)
        val damaged = Unreadable("This backup's settings look damaged.")
        val params = runCatching { Params(h.getInt("m"), h.getInt("t"), h.getInt("p")) }.getOrElse { throw damaged }
        // Capped at what this app writes (with room to grow), so a damaged header can't demand a huge allocation.
        if (params.memoryKiB !in 8..MAX_MEMORY_KIB || params.passes !in 1..10 || params.lanes !in 1..8) throw damaged
        val salt = runCatching { Base64.getDecoder().decode(h.getString("salt")) }.getOrElse { throw damaged }
        val iv = ByteArray(IV_BYTES).also { din.readFully(it) }
        val sealed = din.readNBytes(MAX_SEALED + 1)
        if (sealed.size > MAX_SEALED) throw Unreadable(TOO_LARGE)
        val key = argon2(passphrase, salt, params)
        val plain = try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
            cipher.updateAAD(magic + header)
            cipher.doFinal(sealed)
        } catch (_: AEADBadTagException) { throw WrongPassphrase() } finally { key.fill(0) }
        val unpacked = GZIPInputStream(plain.inputStream()).use { it.readNBytes(MAX_UNPACKED + 1) }
        if (unpacked.size > MAX_UNPACKED) throw Unreadable(TOO_LARGE)
        val json = unpacked.toString(Charsets.UTF_8)
        plain.fill(0)
        return LibrarySnapshot.fromJson(JSONObject(json))
    }

    /** "lyrics-library-2026-10-09.lyrbak" */
    fun fileName(now: java.time.LocalDate) = "lyrics-library-$now.$EXTENSION"

    private fun argon2(p: CharArray, salt: ByteArray, params: Params): ByteArray {
        val spec = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id).withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withSalt(salt).withMemoryAsKB(params.memoryKiB).withIterations(params.passes).withParallelism(params.lanes).build()
        val pw = String(p).toByteArray(Charsets.UTF_8)
        try {
            return ByteArray(32).also { Argon2BytesGenerator().apply { init(spec) }.generateBytes(pw, it) }
        } finally { pw.fill(0) }
    }
}
