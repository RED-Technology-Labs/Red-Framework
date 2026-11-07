package red.framework.infrastructure.postgres

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Opt-in test against a dedicated external PostgreSQL test database. */
class ExternalPostgresIntegrationTest {
    @Test
    fun `commit and rollback are isolated in a unique test schema`() {
        val url = System.getenv("TEST_DATABASE_URL").orEmpty()
        assumeTrue(url.isNotBlank(), "TEST_DATABASE_URL is not configured")

        val schema = "red_test_${UUID.randomUUID().toString().replace("-", "")}" // generated ASCII only
        PostgresDatabase(PostgresConnectionUrl.parse(url, maximumPoolSize = 2)).use { db ->
            var schemaCreated = false
            try {
                db.transaction { connection ->
                    connection.createStatement().use { it.execute("CREATE SCHEMA $schema") }
                }
                schemaCreated = true
                db.transaction { connection ->
                    connection.createStatement().use { it.execute("CREATE TABLE $schema.tx_probe (id INTEGER PRIMARY KEY)") }
                }
                db.transaction { connection ->
                    connection.prepareStatement("INSERT INTO $schema.tx_probe (id) VALUES (?)").use {
                        it.setInt(1, 1)
                        it.executeUpdate()
                    }
                }
                assertFailsWith<IllegalStateException> {
                    db.transaction { connection ->
                        connection.prepareStatement("INSERT INTO $schema.tx_probe (id) VALUES (?)").use {
                            it.setInt(1, 2)
                            it.executeUpdate()
                        }
                        error("rollback")
                    }
                }
                val count = db.transaction { connection ->
                    connection.createStatement().use { statement ->
                        statement.executeQuery("SELECT COUNT(*) FROM $schema.tx_probe").use { result ->
                            result.next()
                            result.getInt(1)
                        }
                    }
                }
                assertEquals(1, count)
            } finally {
                if (schemaCreated) {
                    db.transaction { connection ->
                        connection.createStatement().use { it.execute("DROP SCHEMA $schema CASCADE") }
                    }
                }
            }
        }
    }
}
