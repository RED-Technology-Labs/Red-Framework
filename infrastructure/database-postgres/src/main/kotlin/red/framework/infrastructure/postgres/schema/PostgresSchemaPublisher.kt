package red.framework.infrastructure.postgres.schema

import red.framework.infrastructure.postgres.PostgresDatabase
import red.framework.infrastructure.postgres.metadata.MetadataSnapshotCodec
import red.framework.kernel.context.SiteContext
import red.framework.kernel.identity.AttributeId
import red.framework.kernel.identity.EntityId
import red.framework.kernel.versioning.Revision
import red.framework.metadata.EntityDefinition
import red.framework.metadata.MetadataValidator
import red.framework.schema.SchemaPublication
import red.framework.schema.SchemaPublisher
import java.sql.Connection

/** Transactional publication limited to table creation and non-destructive column additions. */
class PostgresSchemaPublisher(
    private val database: PostgresDatabase,
    private val site: SiteContext,
    schema: String,
) : SchemaPublisher {
    private val namespace: String
    private val validator = MetadataValidator.withBuiltIns()

    init {
        require(Regex("[a-z][a-z0-9_]{0,62}").matches(schema)) { "Invalid framework schema name" }
        namespace = "\"$schema\""
    }

    override fun publish(site: SiteContext, definition: EntityDefinition): SchemaPublication {
        checkSite(site)
        validator.validate(definition)
        val tableName = physicalTableName(definition.id)
        return database.transaction { connection ->
            val active = connection.prepareStatement("SELECT active_revision FROM $namespace.red_entity WHERE id = ? FOR UPDATE").use {
                it.setObject(1, definition.id.value)
                it.executeQuery().use { rows ->
                    check(rows.next()) { "Entity does not exist" }
                    rows.getLong(1)
                }
            }
            check(active == definition.revision.value) { "Definition is not the active revision" }
            check(snapshot(connection, definition.id, definition.revision) == definition) { "Active metadata snapshot changed" }
            val existing = connection.prepareStatement("SELECT published_revision, table_name FROM $namespace.red_entity_publication WHERE entity_id = ?").use {
                it.setObject(1, definition.id.value)
                it.executeQuery().use { rows -> if (rows.next()) SchemaPublication(definition.id, Revision.of(rows.getLong(1)), rows.getString(2)) else null }
            }
            if (existing != null) {
                check(existing.tableName == tableName) { "Physical table identity mismatch" }
                if (existing.publishedRevision == definition.revision) return@transaction existing
                check(existing.publishedRevision < definition.revision) { "Cannot publish an older revision" }
                val previous = snapshot(connection, definition.id, existing.publishedRevision)
                val nextById = definition.attributes.associateBy { it.id }
                check(previous.attributes.all { old ->
                    nextById[old.id]?.let { next -> old.name == next.name && old.typeId == next.typeId && old.required == next.required } == true
                }) { "Removing or changing published attributes requires a migration plan" }
                val oldIds = previous.attributes.map { it.id }.toSet()
                val added = definition.attributes.filter { it.id !in oldIds }
                check(added.none { it.required }) { "New required columns require a backfill plan" }
                if (added.isNotEmpty()) {
                    val additions = added.joinToString(", ") { "ADD COLUMN \"${physicalColumnName(it.id)}\" ${sqlType(it.typeId)}" }
                    connection.createStatement().use { it.execute("ALTER TABLE $namespace.\"$tableName\" $additions") }
                }
                connection.prepareStatement("UPDATE $namespace.red_entity_publication SET published_revision = ?, published_at = CURRENT_TIMESTAMP WHERE entity_id = ?").use {
                    it.setLong(1, definition.revision.value)
                    it.setObject(2, definition.id.value)
                    check(it.executeUpdate() == 1) { "Publication update failed" }
                }
                recordMigration(connection, definition.id, existing.publishedRevision, definition.revision, if (added.isEmpty()) "NO_DDL" else "ADD_COLUMNS")
                return@transaction SchemaPublication(definition.id, definition.revision, tableName)
            }
            val columns = definition.attributes.joinToString(", ") { attribute ->
                "\"${physicalColumnName(attribute.id)}\" ${sqlType(attribute.typeId)}" + if (attribute.required) " NOT NULL" else ""
            }
            val columnSql = if (columns.isEmpty()) "" else ", $columns"
            connection.createStatement().use {
                it.execute("CREATE TABLE $namespace.\"$tableName\" (id UUID PRIMARY KEY, created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, version BIGINT NOT NULL DEFAULT 1$columnSql)")
            }
            connection.prepareStatement("INSERT INTO $namespace.red_entity_publication (entity_id, published_revision, table_name) VALUES (?, ?, ?)").use {
                it.setObject(1, definition.id.value)
                it.setLong(2, definition.revision.value)
                it.setString(3, tableName)
                it.executeUpdate()
            }
            recordMigration(connection, definition.id, null, definition.revision, "CREATE")
            SchemaPublication(definition.id, definition.revision, tableName)
        }
    }

    private fun snapshot(connection: Connection, entityId: EntityId, revision: Revision): EntityDefinition =
        connection.prepareStatement("SELECT snapshot::text, checksum FROM $namespace.red_entity_revision WHERE entity_id = ? AND revision = ?").use {
            it.setObject(1, entityId.value)
            it.setLong(2, revision.value)
            it.executeQuery().use { rows ->
                check(rows.next()) { "Metadata revision missing" }
                val raw = rows.getString(1)
                check(MetadataSnapshotCodec.checksum(raw) == rows.getString(2)) { "Metadata checksum mismatch" }
                MetadataSnapshotCodec.decode(raw).also { definition ->
                    check(definition.id == entityId && definition.revision == revision) { "Metadata revision identity mismatch" }
                }
            }
        }

    private fun recordMigration(connection: Connection, entityId: EntityId, from: Revision?, to: Revision, operation: String) {
        connection.prepareStatement("INSERT INTO $namespace.red_schema_migration (entity_id, from_revision, to_revision, operation) VALUES (?, ?, ?, ?)").use {
            it.setObject(1, entityId.value)
            if (from == null) it.setNull(2, java.sql.Types.BIGINT) else it.setLong(2, from.value)
            it.setLong(3, to.value)
            it.setString(4, operation)
            it.executeUpdate()
        }
    }

    override fun findPublication(site: SiteContext, entityId: EntityId): SchemaPublication? {
        checkSite(site)
        return database.transaction { connection ->
            connection.prepareStatement("SELECT published_revision, table_name FROM $namespace.red_entity_publication WHERE entity_id = ?").use {
                it.setObject(1, entityId.value)
                it.executeQuery().use { rows -> if (rows.next()) SchemaPublication(entityId, Revision.of(rows.getLong(1)), rows.getString(2)) else null }
            }
        }
    }

    private fun checkSite(requested: SiteContext) {
        require(requested.siteId == site.siteId) { "Site context does not match schema publisher" }
    }

    companion object {
        fun physicalTableName(id: EntityId): String = "red_data_${id.value.toString().replace("-", "")}" 
        fun physicalColumnName(id: AttributeId): String = "f_${id.value.toString().replace("-", "")}" 

        fun sqlType(typeId: String): String = when (typeId) {
            "TEXT", "EMAIL" -> "TEXT"
            "INTEGER" -> "INTEGER"
            "DECIMAL" -> "NUMERIC"
            "BOOLEAN" -> "BOOLEAN"
            "DATE" -> "DATE"
            "DATETIME" -> "TIMESTAMPTZ"
            else -> throw IllegalArgumentException("Unsupported physical attribute type: $typeId")
        }
    }
}
