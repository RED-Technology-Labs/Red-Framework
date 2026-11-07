package red.framework.infrastructure.postgres

import org.junit.jupiter.api.Test
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@Testcontainers(disabledWithoutDocker = true)
class PostgresDatabaseIntegrationTest {
    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:17-alpine")
    }

    @Test
    fun `transaction commits and rollback preserves previous rows`() {
        PostgresDatabase(PostgresConfig(postgres.jdbcUrl, postgres.username, postgres.password, 2)).use { db ->
            assertTrue(db.isHealthy())
            db.transaction { connection ->
                connection.createStatement().use { it.execute("CREATE TABLE tx_probe (id INTEGER PRIMARY KEY)") }
            }
            db.transaction { connection ->
                connection.prepareStatement("INSERT INTO tx_probe (id) VALUES (?)").use {
                    it.setInt(1, 1)
                    it.executeUpdate()
                }
            }
            assertFailsWith<IllegalStateException> {
                db.transaction { connection ->
                    connection.prepareStatement("INSERT INTO tx_probe (id) VALUES (?)").use {
                        it.setInt(1, 2)
                        it.executeUpdate()
                    }
                    error("rollback")
                }
            }
            val count = db.transaction { connection ->
                connection.createStatement().use { statement ->
                    statement.executeQuery("SELECT COUNT(*) FROM tx_probe").use { result ->
                        result.next()
                        result.getInt(1)
                    }
                }
            }
            assertEquals(1, count)
        }
    }
}
