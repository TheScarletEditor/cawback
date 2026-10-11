package com.thescarletcoder.lyrics.library

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import java.security.UnrecoverableKeyException
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The SQLCipher passphrase is 32 random bytes generated once, stored only in wrapped form:
 * AES-GCM encrypted by a non-exportable Android Keystore key, the same pattern as Scarlet Command.
 *
 * Not bound to screen unlock: plays are recorded while the screen is off.
 */
object LibraryKey {
    private const val ALIAS = "lyrics_library_wrap_v1"
    private const val FILE = "library.key.wrapped"
    private const val GCM_TAG_BITS = 128
    private const val IV_BYTES = 12

    /**
     * The Library exists but its key can't be unwrapped any more (the Keystore key or the wrapped file is gone).
     * The database can never be opened again on this device: LibraryDatabase.startFresh, then restore a backup.
     */
    class Gone : Exception("This device's Library key is gone.")

    fun passphrase(context: Context): ByteArray {
        val file = File(context.noBackupFilesDir, FILE)
        if (file.exists()) {
            val blob = file.readBytes()
            if (blob.size <= IV_BYTES) throw Gone()
            // Never a new key for an old file: it could never open it. Only a key that is really gone or replaced
            // counts as Gone; anything else (a passing Keystore error) is thrown as it is, and opening is tried again.
            val key = try { existingKey() } catch (_: UnrecoverableKeyException) { null } ?: throw Gone()
            try {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, blob, 0, IV_BYTES))
                return cipher.doFinal(blob, IV_BYTES, blob.size - IV_BYTES)
            } catch (_: AEADBadTagException) { throw Gone() } catch (_: KeyPermanentlyInvalidatedException) { throw Gone() }
        }
        if (LibraryDatabase.exists(context)) throw Gone()   // a database with no key: a new key can't open it
        val key = keystoreKey()
        val raw = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val tmp = File(context.noBackupFilesDir, "$FILE.tmp")
        tmp.writeBytes(cipher.iv + cipher.doFinal(raw))
        check(tmp.renameTo(file)) { "could not store wrapped library key" }
        return raw
    }

    /** Starting fresh: the wrapped key is set aside with the old database ([name]), never deleted. */
    fun setAside(context: Context, name: String) {
        val f = File(context.noBackupFilesDir, FILE)
        if (f.exists() && !f.renameTo(File(context.noBackupFilesDir, "$name.key"))) f.delete()
    }

    private fun existingKey(): SecretKey? =
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.getKey(ALIAS, null) as? SecretKey

    private fun keystoreKey(): SecretKey {
        existingKey()?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return gen.generateKey()
    }
}
