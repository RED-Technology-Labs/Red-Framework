package red.framework.infrastructure.migrations

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import red.framework.infrastructure.postgres.PostgresConnectionUrl
import red.framework.infrastructure.postgres.PostgresDatabase
import java.util.UUID
import java.sql.SQLException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FrameworkMigratorTest {
    @Test
    fun `rejects unsafe schema names before connecting`() {
        // DataSource is never opened; rejection happens before Flyway is created.
        val source = object : javax.sql.DataSource {
            override fun getConnection(): java.sql.Connection = error("unexpected connection")
            override fun getConnection(username: String?, password: String?): java.sql.Connection = error("unexpected connection")
            override fun getLoginTimeout(): Int = 0
            override fun setLoginTimeout(seconds: Int) = Unit
            override fun getLogWriter(): java.io.PrintWriter? = null
            override fun setLogWriter(out: java.io.PrintWriter?) = Unit
            override fun getParentLogger(): java.util.logging.Logger = java.util.logging.Logger.getGlobal()
            override fun <T : Any?> unwrap(iface: Class<T>?): T = error("unsupported")
            override fun isWrapperFor(iface: Class<*>?): Boolean = false
        }
        assertFailsWith<IllegalArgumentException> { FrameworkMigrator(source, "public;DROP SCHEMA public") }
    }

    @Test
    fun `baseline applies once and history persists on dedicated external test database`() {
        val url = System.getenv("TEST_DATABASE_URL").orEmpty()
        assumeTrue(url.isNotBlank(), "TEST_DATABASE_URL is not configured")
        val schema = "red_test_${UUID.randomUUID().toString().replace("-", "")}" 
        PostgresDatabase(PostgresConnectionUrl.parse(url, maximumPoolSize = 2)).use { db ->
            var created = false
            try {
                db.transaction { connection ->
                    connection.createStatement().use { it.execute("CREATE SCHEMA $schema") }
                }
                created = true
                val migrator = FrameworkMigrator(db.dataSource, schema)
                assertEquals(7, migrator.migrate())
                assertEquals(0, FrameworkMigrator(db.dataSource, schema).migrate())
                assertEquals(listOf("1", "2", "3", "4", "5", "6", "7"), FrameworkMigrator(db.dataSource, schema).appliedVersions())
                db.transaction { connection ->
                    val tables = setOf("red_application", "red_entity", "red_entity_revision", "red_flyway_schema_history", "red_site", "red_user", "red_role", "red_user_role", "red_session", "red_setup_state", "red_entity_publication", "red_schema_migration", "red_record_audit", "red_entity_permission", "red_entity_sequence", "red_record_name")
                    connection.prepareStatement("SELECT table_name FROM information_schema.tables WHERE table_schema = ?").use { statement ->
                        statement.setString(1, schema)
                        statement.executeQuery().use { rows ->
                            val found = mutableSetOf<String>()
                            while (rows.next()) found.add(rows.getString(1))
                            assertEquals(tables, found)
                        }
                    }
                }
                val applicationId = UUID.randomUUID()
                val entityId = UUID.randomUUID()
                db.transaction { connection ->
                    connection.prepareStatement("INSERT INTO $schema.red_application (id, canonical_name, version_major, version_minor, version_patch) VALUES (?, 'test-app', 1, 0, 0)").use {
                        it.setObject(1, applicationId)
                        it.executeUpdate()
                    }
                    connection.prepareStatement("INSERT INTO $schema.red_entity (id, application_id, canonical_name) VALUES (?, ?, 'test_entity')").use {
                        it.setObject(1, entityId)
                        it.setObject(2, applicationId)
                        it.executeUpdate()
                    }
                    connection.prepareStatement("INSERT INTO $schema.red_entity_revision (entity_id, revision, format_version, snapshot, checksum) VALUES (?, 1, 1, '{}'::jsonb, ?)").use {
                        it.setObject(1, entityId)
                        it.setString(2, "a".repeat(64))
                        it.executeUpdate()
                    }
                    connection.prepareStatement("UPDATE $schema.red_entity SET active_revision = 1 WHERE id = ?").use {
                        it.setObject(1, entityId)
                        it.executeUpdate()
                    }
                }
                assertFailsWith<SQLException> {
                    db.transaction { connection ->
                        connection.prepareStatement("UPDATE $schema.red_entity SET active_revision = 2 WHERE id = ?").use {
                            it.setObject(1, entityId)
                            it.executeUpdate()
                        }
                    }
                }
            } finally {
                if (created) db.transaction { connection ->
                    connection.createStatement().use { it.execute("DROP SCHEMA $schema CASCADE") }
                }
            }
        }
    }
}
