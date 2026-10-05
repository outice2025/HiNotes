package com.hiapps.hinotes.data

import android.content.Context
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Stores the optional app unlock password.
 *
 * The password itself is never written anywhere. A random 16-byte salt is generated per
 * password and only `PBKDF2WithHmacSHA1(password, salt, 120_000)` is persisted, alongside the
 * salt, so the stored value cannot be turned back into the password.
 *
 * The file is excluded from cloud backup (see `backup_rules.xml`) so a restored device does
 * not silently inherit a lock the user did not set there.
 */
class LockStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** True when an unlock password has been set. */
    val isConfigured: Boolean
        get() = prefs.contains(KEY_HASH) && prefs.contains(KEY_SALT)

    /** Sets or replaces the password. Short passwords are rejected by the caller's UI. */
    fun setPassword(password: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        prefs.edit()
            .putString(KEY_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(KEY_HASH, Base64.encodeToString(hash(password, salt), Base64.NO_WRAP))
            .apply()
    }

    /** Verifies [password] against the stored hash in constant time. */
    fun verify(password: String): Boolean {
        val salt = prefs.getString(KEY_SALT, null)?.let {
            runCatching { Base64.decode(it, Base64.NO_WRAP) }.getOrNull()
        } ?: return false
        val expected = prefs.getString(KEY_HASH, null) ?: return false
        val actual = Base64.encodeToString(hash(password, salt), Base64.NO_WRAP)
        return MessageDigest.isEqual(expected.toByteArray(), actual.toByteArray())
    }

    /** Removes the password and, with it, any biometric unlock that depended on it. */
    fun clear() {
        prefs.edit().clear().apply()
    }

    private fun hash(password: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
                .generateSecret(spec)
                .encoded
        } finally {
            spec.clearPassword()
        }
    }

    private companion object {
        const val PREFS_NAME = "hinotes_lock"
        const val KEY_HASH = "password_hash"
        const val KEY_SALT = "password_salt"
        const val ITERATIONS = 120_000
        const val KEY_LENGTH_BITS = 256
    }
}
