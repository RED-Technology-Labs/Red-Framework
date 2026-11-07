package red.framework.infrastructure.migrations

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import red.framework.infrastructure.postgres.PostgresConnectionUrl
import red.framework.infrastructure.postgres.PostgresDatabase
import red.framework.infrastructure.postgres.metadata.PostgresMetadataRepository
import red.framework.kernel.context.SiteContext
import red.framework.kernel.identity.ApplicationId
import red.framework.kernel.identity.AttributeId
import red.framework.kernel.identity.EntityId
import red.framework.kernel.identity.SiteId
import red.framework.kernel.versioning.Revision
import red.framework.metadata.AttributeDefinition
import red.framework.metadata.EntityDefinition
import java.sql.SQLException
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MetadataPersistenceIntegrationTest {
    private fun definition(entityId: EntityId, appId: ApplicationId, revision: Revision, label: String): EntityDefinition =
        EntityDefinition(entityId, appId, "customer", label, revision, listOf(
            AttributeDefinition(AttributeId.parse("00000000-0000-0000-0000-000000000001"), "customer_name", "Customer name", "TEXT", true),
            AttributeDefinition(AttributeId.parse("00000000-0000-0000-0000-000000000002"), "email", "Email", "EMAIL"),
        ))

    private fun withSchema(block: (String, red.framework.infrastructure.postgres.PostgresConfig, SiteContext, ApplicationId) -> Unit) {
        val url = System.getenv("TEST_DATABASE_URL").orEmpty()
        assumeTrue(url.isNotBlank(), "TEST_DATABASE_URL is not configured")
        val config = PostgresConnectionUrl.parse(url, maximumPoolSize = 4)
        val schema = "red_test_${UUID.randomUUID().toString().replace("-", "")}" 
        val site = SiteContext(SiteId.new())
        val appId = ApplicationId.new()
        PostgresDatabase(config).use { db ->
            db.transaction { connection -> connection.createStatement().use { it.execute("CREATE SCHEMA $schema") } }
            try {
                assertEquals(7, FrameworkMigrator(db.dataSource, schema).migrate())
                db.transaction { connection ->
                    connection.prepareStatement("INSERT INTO $schema.red_application (id, canonical_name, version_major, version_minor, version_patch) VALUES (?, 'test-app', 1, 0, 0)").use {
                        it.setObject(1, appId.value)
                        it.executeUpdate()
                    }
                }
                block(schema, config, site, appId)
            } finally {
                db.transaction { connection -> connection.createStatement().use { it.execute("DROP SCHEMA $schema CASCADE") } }
            }
        }
    }

    @Test
    fun `active metadata restores through new database connection and history remains available`() = withSchema { schema, config, site, appId ->
        val entityId = EntityId.new()
        val first = definition(entityId, appId, Revision.INITIAL, "Customer")
        PostgresDatabase(config).use { db ->
            PostgresMetadataRepository(db, site, schema).create(site, first)
        }
        PostgresDatabase(config).use { db ->
            val repository = PostgresMetadataRepository(db, site, schema)
            assertEquals(first, repository.findActive(site, entityId))
            val second = definition(entityId, appId, Revision.of(2), "Client")
            assertTrue(repository.saveRevision(site, second, Revision.INITIAL))
            assertEquals(second, repository.findActive(site, entityId))
            assertEquals(first, repository.findRevision(site, entityId, Revision.INITIAL))
            assertFailsWith<IllegalArgumentException> {
                repository.findActive(SiteContext(SiteId.new()), entityId)
            }
        }
    }

    @Test
    fun `concurrent revision updates allow one winner`() = withSchema { schema, config, site, appId ->
        val entityId = EntityId.new()
        PostgresDatabase(config).use { db ->
            val repository = PostgresMetadataRepository(db, site, schema)
            repository.create(site, definition(entityId, appId, Revision.INITIAL, "Customer"))
            val second = definition(entityId, appId, Revision.of(2), "Client")
            val start = CountDownLatch(1)
            val executor = Executors.newFixedThreadPool(2)
            try {
                val results = (1..2).map {
                    executor.submit<Boolean> {
                        start.await()
                        repository.saveRevision(site, second, Revision.INITIAL)
                    }
                }
                start.countDown()
                assertEquals(listOf(false, true), results.map { it.get(30, TimeUnit.SECONDS) }.sorted())
                assertEquals(second, repository.findActive(site, entityId))
            } finally {
                executor.shutdownNow()
            }
        }
    }

    @Test
    fun `foreign key failure rolls back entity insertion`() = withSchema { schema, config, site, _ ->
        val missingApp = ApplicationId.new()
        val entityId = EntityId.new()
        PostgresDatabase(config).use { db ->
            val repository = PostgresMetadataRepository(db, site, schema)
            assertFailsWith<SQLException> {
                repository.create(site, definition(entityId, missingApp, Revision.INITIAL, "Customer"))
            }
            assertNull(repository.findActive(site, entityId))
        }
    }

    @Test
    fun `checksum detects unexpected JSONB changes`() = withSchema { schema, config, site, appId ->
        val entityId = EntityId.new()
        PostgresDatabase(config).use { db ->
            val repository = PostgresMetadataRepository(db, site, schema)
            repository.create(site, definition(entityId, appId, Revision.INITIAL, "Customer"))
            db.transaction { connection ->
                connection.prepareStatement(
                    "UPDATE $schema.red_entity_revision SET snapshot = snapshot || '{\"unexpected\":true}'::jsonb WHERE entity_id = ?"
                ).use {
                    it.setObject(1, entityId.value)
                    it.executeUpdate()
                }
            }
            assertFailsWith<IllegalStateException> { repository.findActive(site, entityId) }
        }
    }

    @Test
    fun `unregistered attribute type is rejected before database write`() = withSchema { schema, config, site, appId ->
        val entityId = EntityId.new()
        val invalid = EntityDefinition(entityId, appId, "customer", "Customer", Revision.INITIAL,
            listOf(AttributeDefinition(AttributeId.new(), "future_field", "Future", "FUTURE_TYPE")))
        PostgresDatabase(config).use { db ->
            val repository = PostgresMetadataRepository(db, site, schema)
            assertFailsWith<IllegalArgumentException> { repository.create(site, invalid) }
            assertNull(repository.findActive(site, entityId))
        }
    }
}
