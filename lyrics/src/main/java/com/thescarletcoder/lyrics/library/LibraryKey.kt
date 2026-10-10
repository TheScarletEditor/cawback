package com.thescarletcoder.lyrics.library

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
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

    fun passphrase(context: Context): ByteArray {
        val file = File(context.noBackupFilesDir, FILE)
        val key = keystoreKey()
        if (file.exists()) {
            val blob = file.readBytes()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, blob, 0, IV_BYTES))
            return cipher.doFinal(blob, IV_BYTES, blob.size - IV_BYTES)
        }
        val raw = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val tmp = File(context.noBackupFilesDir, "$FILE.tmp")
        tmp.writeBytes(cipher.iv + cipher.doFinal(raw))
        check(tmp.renameTo(file)) { "could not store wrapped library key" }
        return raw
    }

    private fun keystoreKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
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
