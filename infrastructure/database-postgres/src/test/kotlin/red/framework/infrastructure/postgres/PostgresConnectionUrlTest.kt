package red.framework.infrastructure.postgres

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class PostgresConnectionUrlTest {
    @Test
    fun `Neon style URL maps to JDBC with separate credentials`() {
        val config = PostgresConnectionUrl.parse(
            "postgresql://red_user:p%40ssword@localhost:5432/red_test?sslmode=require&channel_binding=require",
            maximumPoolSize = 2,
        )
        assertEquals("red_user", config.username)
        assertEquals("p@ssword", config.password)
        assertEquals(
            "jdbc:postgresql://localhost:5432/red_test?sslmode=require&channelBinding=require",
            config.jdbcUrl,
        )
        assertEquals(2, config.maximumPoolSize)
        assertFalse(config.toString().contains("p@ssword"))
    }

    @Test
    fun `rejects insecure or ambiguous URL options`() {
        for (url in listOf(
            "postgresql://u:p@localhost/db?sslmode=disable",
            "postgresql://u:p@localhost/db?channel_binding=disable",
            "postgresql://u:p@localhost/db?password=p",
            "postgresql://u:p@localhost/db/extra",
            "mysql://u:p@localhost/db",
        )) {
            assertFailsWith<IllegalArgumentException> { PostgresConnectionUrl.parse(url) }
        }
    }
}
