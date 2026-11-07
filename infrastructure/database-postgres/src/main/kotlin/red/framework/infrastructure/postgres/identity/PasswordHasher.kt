package red.framework.infrastructure.postgres.identity

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** PBKDF2 password verifier with a per-password salt and constant-time hash comparison. */
internal object PasswordHasher {
    private const val ITERATIONS = 210_000
    private val random = SecureRandom()
    private val base64 = Base64.getUrlEncoder().withoutPadding()
    private val decoder = Base64.getUrlDecoder()

    fun hash(password: String): String {
        val salt = ByteArray(32).also(random::nextBytes)
        val derived = derive(password, salt, ITERATIONS)
        return "pbkdf2-sha256\$$ITERATIONS\$${base64.encodeToString(salt)}\$${base64.encodeToString(derived)}"
    }

    fun verify(password: String, encoded: String): Boolean {
        val parts = encoded.split('$')
        if (parts.size != 4 || parts[0] != "pbkdf2-sha256") return false
        val iterations = parts[1].toIntOrNull() ?: return false
        if (iterations !in 100_000..1_000_000) return false
        return runCatching {
            val salt = decoder.decode(parts[2])
            val expected = decoder.decode(parts[3])
            salt.size == 32 && expected.size == 32 && MessageDigest.isEqual(expected, derive(password, salt, iterations))
        }.getOrDefault(false)
    }

    private fun derive(password: String, salt: ByteArray, iterations: Int): ByteArray {
        val chars = password.toCharArray()
        val spec = PBEKeySpec(chars, salt, iterations, 256)
        try { return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded }
        finally { spec.clearPassword(); chars.fill('\u0000') }
    }
}
