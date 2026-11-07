package red.framework.infrastructure.postgres.identity

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PasswordHasherTest {
    @Test fun `hashes with distinct salts and verifies`() {
        val first = PasswordHasher.hash("Strong#Pass123")
        val second = PasswordHasher.hash("Strong#Pass123")
        assertTrue(first != second)
        assertTrue(PasswordHasher.verify("Strong#Pass123", first))
        assertFalse(PasswordHasher.verify("wrong", first))
        assertFalse(PasswordHasher.verify("Strong#Pass123", "malformed"))
    }
}
