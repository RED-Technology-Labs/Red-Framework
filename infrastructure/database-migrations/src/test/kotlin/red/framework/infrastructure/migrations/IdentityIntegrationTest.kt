package red.framework.infrastructure.migrations

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import red.framework.identity.InitialSetup
import red.framework.infrastructure.postgres.PostgresConnectionUrl
import red.framework.infrastructure.postgres.PostgresDatabase
import red.framework.infrastructure.postgres.identity.PostgresIdentityRepository
import java.util.UUID
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IdentityIntegrationTest {
    @Test fun `first setup persists administrator and revocable session`() {
        val url = System.getenv("TEST_DATABASE_URL").orEmpty()
        assumeTrue(url.isNotBlank(), "TEST_DATABASE_URL is not configured")
        val schema = "red_test_${UUID.randomUUID().toString().replace("-", "")}" 
        PostgresDatabase(PostgresConnectionUrl.parse(url, maximumPoolSize = 2)).use { database ->
            var created = false
            try {
                database.transaction { connection -> connection.createStatement().use { it.execute("CREATE SCHEMA $schema") } }
                created = true
                FrameworkMigrator(database.dataSource, schema).migrate()
                val identity = PostgresIdentityRepository(database, schema)
                assertFalse(identity.isInitialized())
                val setup = InitialSetup("First Administrator", "ADMIN@example.com", "Strong#Pass123", "Strong#Pass123", "first-site")
                identity.initialize(setup)
                val reloaded = PostgresIdentityRepository(database, schema)
                assertTrue(reloaded.isInitialized())
                assertFailsWith<IllegalStateException> { reloaded.initialize(setup) }
                assertNull(reloaded.login("admin@example.com", "wrong"))
                val token = assertNotNull(reloaded.login("admin@example.com", "Strong#Pass123"))
                val administrator = assertNotNull(reloaded.findSession(token))
                assertTrue(reloaded.hasRole(administrator.id, "system-administrator"))
                val restricted = reloaded.createUser("Restricted User", "restricted@example.com", "Another#Pass123")
                assertFalse(reloaded.hasRole(restricted.id, "system-administrator"))
                assertTrue(reloaded.listUsers().any { it.id == restricted.id })
                val restrictedToken = assertNotNull(reloaded.login("restricted@example.com", "Another#Pass123"))
                assertNotNull(reloaded.findSession(restrictedToken))
                reloaded.logout(restrictedToken)
                reloaded.logout(token)
                assertNull(reloaded.findSession(token))
                database.transaction { connection ->
                    connection.prepareStatement("SELECT COUNT(*) FROM $schema.red_site").use { it.executeQuery().use { rows -> rows.next(); kotlin.test.assertEquals(1, rows.getInt(1)) } }
                }
            } finally {
                if (created) database.transaction { connection -> connection.createStatement().use { it.execute("DROP SCHEMA $schema CASCADE") } }
            }
        }
    }
}
