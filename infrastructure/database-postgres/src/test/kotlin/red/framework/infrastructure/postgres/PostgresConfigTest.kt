package red.framework.infrastructure.postgres

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class PostgresConfigTest {
    @Test
    fun `requires PostgreSQL URL and rejects embedded credentials`() {
        assertFailsWith<IllegalArgumentException> { PostgresConfig("jdbc:h2:mem:test", "red", "secret") }
        assertFailsWith<IllegalArgumentException> { PostgresConfig("jdbc:postgresql://u:p@host/db", "red", "secret") }
        assertFailsWith<IllegalArgumentException> { PostgresConfig("jdbc:postgresql://host/db?password=secret", "red", "secret") }
    }

    @Test
    fun `requires bounded pool and nonblank user`() {
        assertFailsWith<IllegalArgumentException> { PostgresConfig("jdbc:postgresql://host/db", " ", "secret") }
        assertFailsWith<IllegalArgumentException> { PostgresConfig("jdbc:postgresql://host/db", "red", "secret", 0) }
    }

    @Test
    fun `configuration string does not reveal credentials or database host`() {
        val config = PostgresConfig("jdbc:postgresql://private-host/red", "private-user", "private-password")
        val description = config.toString()
        assertFalse(description.contains("private-host"))
        assertFalse(description.contains("private-user"))
        assertFalse(description.contains("private-password"))
    }
}
