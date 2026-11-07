package red.framework.infrastructure.postgres.metadata

import red.framework.infrastructure.postgres.PostgresDatabase
import red.framework.kernel.context.SiteContext
import red.framework.kernel.identity.EntityId
import red.framework.kernel.identity.ApplicationId
import red.framework.kernel.versioning.Revision
import red.framework.metadata.EntityDefinition
import red.framework.metadata.MetadataRepository
import red.framework.metadata.MetadataValidator
import java.sql.Connection
import java.util.UUID

/** Durable metadata repository bound to one verified site database and framework schema. */
class PostgresMetadataRepository(
    private val database: PostgresDatabase,
    private val site: SiteContext,
    schema: String,
    private val validator: MetadataValidator = MetadataValidator.withBuiltIns(),
) : MetadataRepository {
    private val namespace: String

    init {
        require(Regex("[a-z][a-z0-9_]{0,62}").matches(schema)) { "Invalid framework schema name" }
        namespace = "\"$schema\""
    }

    override fun create(site: SiteContext, definition: EntityDefinition) {
        checkSite(site)
        validator.validate(definition)
        require(definition.revision == Revision.INITIAL) { "Initial definition must have revision 1" }
        database.transaction { connection ->
            connection.prepareStatement("INSERT INTO $namespace.red_entity (id, application_id, canonical_name) VALUES (?, ?, ?)").use {
                it.setObject(1, definition.id.value)
                it.setObject(2, definition.applicationId.value)
                it.setString(3, definition.name)
                it.executeUpdate()
            }
            insertSnapshot(connection, definition)
            activate(connection, definition.id, definition.revision)
        }
    }

    override fun saveRevision(site: SiteContext, definition: EntityDefinition, expectedActive: Revision): Boolean {
        checkSite(site)
        validator.validate(definition)
        require(definition.revision == expectedActive.next()) { "New revision must follow expected active revision" }
        return database.transaction { connection ->
            val current = connection.prepareStatement(
                "SELECT application_id, canonical_name, active_revision FROM $namespace.red_entity WHERE id = ? FOR UPDATE"
            ).use { statement ->
                statement.setObject(1, definition.id.value)
                statement.executeQuery().use { rows ->
                    if (!rows.next()) return@transaction false
                    Triple(rows.getObject(1, UUID::class.java), rows.getString(2), rows.getLong(3))
                }
            }
            require(current.first == definition.applicationId.value && current.second == definition.name) {
                "Entity identity or canonical name cannot change in a revision"
            }
            if (current.third != expectedActive.value) return@transaction false
            insertSnapshot(connection, definition)
            activate(connection, definition.id, definition.revision)
            true
        }
    }

    override fun findActive(site: SiteContext, entityId: EntityId): EntityDefinition? {
        checkSite(site)
        return find(entityId, null)
    }

    override fun findRevision(site: SiteContext, entityId: EntityId, revision: Revision): EntityDefinition? {
        checkSite(site)
        return find(entityId, revision)
    }

    override fun listRevisions(site: SiteContext, entityId: EntityId): List<EntityDefinition> {
        checkSite(site)
        val revisions = database.transaction { connection ->
            connection.prepareStatement("SELECT revision FROM $namespace.red_entity_revision WHERE entity_id = ? ORDER BY revision DESC").use {
                it.setObject(1, entityId.value)
                it.executeQuery().use { rows -> buildList { while (rows.next()) add(Revision.of(rows.getLong(1))) } }
            }
        }
        return revisions.map { checkNotNull(find(entityId, it)) { "Missing metadata revision" } }
    }

    override fun listByApplication(site: SiteContext, applicationId: ApplicationId): List<EntityDefinition> {
        checkSite(site)
        val ids = database.transaction { connection ->
            connection.prepareStatement("SELECT id FROM $namespace.red_entity WHERE application_id = ? ORDER BY canonical_name").use {
                it.setObject(1, applicationId.value)
                it.executeQuery().use { rows -> buildList { while (rows.next()) add(EntityId.parse(rows.getObject(1, UUID::class.java).toString())) } }
            }
        }
        return ids.mapNotNull { find(it, null) }
    }

    private fun find(entityId: EntityId, revision: Revision?): EntityDefinition? =
        database.transaction { connection ->
            val sql = if (revision == null) {
                "SELECT r.snapshot::text, r.checksum, r.revision, e.application_id, e.canonical_name FROM $namespace.red_entity e JOIN $namespace.red_entity_revision r ON r.entity_id = e.id AND r.revision = e.active_revision WHERE e.id = ?"
            } else {
                "SELECT r.snapshot::text, r.checksum, r.revision, e.application_id, e.canonical_name FROM $namespace.red_entity e JOIN $namespace.red_entity_revision r ON r.entity_id = e.id WHERE e.id = ? AND r.revision = ?"
            }
            connection.prepareStatement(sql).use { statement ->
                statement.setObject(1, entityId.value)
                if (revision != null) statement.setLong(2, revision.value)
                statement.executeQuery().use { rows ->
                    if (!rows.next()) return@transaction null
                    val snapshot = rows.getString(1)
                    check(MetadataSnapshotCodec.checksum(snapshot) == rows.getString(2)) { "Metadata checksum mismatch" }
                    val definition = MetadataSnapshotCodec.decode(snapshot)
                    check(definition.id == entityId && definition.revision.value == rows.getLong(3)) { "Metadata identity mismatch" }
                    check(definition.applicationId.value == rows.getObject(4, UUID::class.java) && definition.name == rows.getString(5)) {
                        "Metadata row and snapshot mismatch"
                    }
                    definition
                }
            }
        }

    private fun insertSnapshot(connection: Connection, definition: EntityDefinition) {
        connection.prepareStatement(
            "INSERT INTO $namespace.red_entity_revision (entity_id, revision, format_version, snapshot, checksum) VALUES (?, ?, ?, ?::jsonb, ?)"
        ).use {
            it.setObject(1, definition.id.value)
            it.setLong(2, definition.revision.value)
            it.setInt(3, MetadataSnapshotCodec.FORMAT_VERSION)
            it.setString(4, MetadataSnapshotCodec.encode(definition))
            it.setString(5, MetadataSnapshotCodec.checksum(definition))
            it.executeUpdate()
        }
    }

    private fun activate(connection: Connection, entityId: EntityId, revision: Revision) {
        connection.prepareStatement("UPDATE $namespace.red_entity SET active_revision = ? WHERE id = ?").use {
            it.setLong(1, revision.value)
            it.setObject(2, entityId.value)
            check(it.executeUpdate() == 1) { "Entity activation failed" }
        }
    }

    private fun checkSite(requested: SiteContext) {
        require(requested.siteId == site.siteId) { "Site context does not match repository database" }
    }
}
