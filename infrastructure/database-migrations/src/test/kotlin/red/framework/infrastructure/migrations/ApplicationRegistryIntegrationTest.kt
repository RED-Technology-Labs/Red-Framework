package red.framework.infrastructure.migrations

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import red.framework.application.ApplicationManifest
import red.framework.application.ApplicationVersion
import red.framework.infrastructure.postgres.PostgresConnectionUrl
import red.framework.infrastructure.postgres.PostgresDatabase
import red.framework.infrastructure.postgres.application.PostgresApplicationRegistry
import red.framework.kernel.identity.ApplicationId
import java.sql.SQLException
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ApplicationRegistryIntegrationTest {
    @Test fun `application survives new repository and duplicate name is rejected`() {
        val url = System.getenv("TEST_DATABASE_URL").orEmpty()
        assumeTrue(url.isNotBlank(), "TEST_DATABASE_URL is not configured")
        val schema = "red_test_${UUID.randomUUID().toString().replace("-", "")}" 
        PostgresDatabase(PostgresConnectionUrl.parse(url, maximumPoolSize = 2)).use { database ->
            var created = false
            try {
                database.transaction { connection -> connection.createStatement().use { it.execute("CREATE SCHEMA $schema") } }
                created = true
                FrameworkMigrator(database.dataSource, schema).migrate()
                val first = ApplicationManifest(ApplicationId.new(), "sample-app", ApplicationVersion(1, 0, 0), emptyList())
                PostgresApplicationRegistry(database, schema).register(first)
                val reloaded = PostgresApplicationRegistry(database, schema)
                assertEquals(first.id, reloaded.findById(first.id)?.id)
                assertEquals("sample-app", reloaded.list().single().canonicalName)
                assertFailsWith<SQLException> {
                    reloaded.register(ApplicationManifest(ApplicationId.new(), "sample-app", ApplicationVersion(1, 1, 0), emptyList()))
                }
            } finally {
                if (created) database.transaction { connection -> connection.createStatement().use { it.execute("DROP SCHEMA $schema CASCADE") } }
            }
        }
    }
}
