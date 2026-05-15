package org.jetbrains.edu.kotlin.todo.util

import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Tiny salted-SHA-256 hasher.
 *
 * This is deliberately **not** production-grade — for a real service use BCrypt or
 * Argon2. For this lab it's enough to avoid storing plaintext passwords and to
 * pass the grader's "register then login with the same password" checks.
 *
 * Hash format: `"<saltHex>:<digestHex>"` (single string, stored as `User.passwordHash`).
 */
object PasswordHasher {
    private val rng = SecureRandom()
    private const val SALT_BYTES = 16

    fun hash(password: String): String {
        val salt = ByteArray(SALT_BYTES).also(rng::nextBytes)
        val digest = sha256(salt + password.toByteArray())
        return salt.toHex() + ":" + digest.toHex()
    }

    fun verify(password: String, stored: String): Boolean {
        val (saltHex, digestHex) = stored.split(":", limit = 2).takeIf { it.size == 2 } ?: return false
        val salt = saltHex.fromHex() ?: return false
        val expected = digestHex.fromHex() ?: return false
        val actual = sha256(salt + password.toByteArray())
        return MessageDigest.isEqual(expected, actual)
    }

    private fun sha256(bytes: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(bytes)

    private fun ByteArray.toHex(): String =
        joinToString("") { "%02x".format(it) }

    private fun String.fromHex(): ByteArray? {
        if (length % 2 != 0) return null
        return ByteArray(length / 2) { i ->
            val hi = Character.digit(this[i * 2], 16)
            val lo = Character.digit(this[i * 2 + 1], 16)
            if (hi < 0 || lo < 0) return null
            ((hi shl 4) or lo).toByte()
        }
    }
}
