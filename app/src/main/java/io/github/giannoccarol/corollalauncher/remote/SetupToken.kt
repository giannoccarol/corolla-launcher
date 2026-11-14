package io.github.giannoccarol.corollalauncher.remote

import java.security.MessageDigest
import java.security.SecureRandom

/**
 * The secret in the QR code. Anyone on the same network could reach the setup server, so
 * every request must carry this token: 128 random bits, new for every session, compared in
 * constant time. Pure, unit tested.
 */
object SetupToken {

    private const val BYTES = 16

    fun generate(random: SecureRandom = SecureRandom()): String {
        val bytes = ByteArray(BYTES).also(random::nextBytes)
        return bytes.joinToString("") { "%02x".format(it.toInt() and 0xFF) }
    }

    fun matches(expected: String, given: String?): Boolean {
        if (given == null || expected.isEmpty()) return false
        return MessageDigest.isEqual(expected.toByteArray(), given.toByteArray())
    }
}
