package red.framework.infrastructure.postgres.records

import red.framework.attributes.BuiltInAttributeTypes
import red.framework.infrastructure.postgres.PostgresDatabase
import red.framework.infrastructure.postgres.schema.PostgresSchemaPublisher
import red.framework.kernel.context.SiteContext
import red.framework.kernel.identity.EntityId
import red.framework.kernel.identity.RecordId
import red.framework.metadata.AttributeDefinition
import red.framework.metadata.EntityDefinition
import red.framework.metadata.MetadataRepository
import red.framework.records.BusinessRecord
import red.framework.records.RecordRepository
import red.framework.records.RecordValue
import red.framework.records.RecordAuditEvent
import red.framework.records.RecordAuditAction
import red.framework.schema.SchemaPublisher
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Types
import java.sql.Connection
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

/** Writes real typed columns in a published Entity table. No SQL identifiers originate from record input. */
class PostgresRecordRepository(
    private val database: PostgresDatabase,
    private val site: SiteContext,
    schema: String,
    private val metadata: MetadataRepository,
    private val publisher: SchemaPublisher,
) : RecordRepository {
    private val namespace: String
    private val types = BuiltInAttributeTypes.registry()

    init {
        require(Regex("[a-z][a-z0-9_]{0,62}").matches(schema)) { "Invalid framework schema name" }
        namespace = "\"$schema\""
    }

    override fun create(site: SiteContext, entityId: EntityId, actorId: UUID, values: Map<String, RecordValue?>): BusinessRecord {
        val (definition, table) = published(site, entityId)
        val normalized = normalize(definition, values)
        val id = RecordId.new()
        val columns = definition.attributes.map { "\"${PostgresSchemaPublisher.physicalColumnName(it.id)}\"" }
        val sql = "INSERT INTO $namespace.\"$table\" (id${if (columns.isEmpty()) "" else ", ${columns.joinToString(", ")}"}) VALUES (?${", ?".repeat(columns.size)})"
        val recordName = database.transaction { connection ->
            connection.prepareStatement(sql).use { statement ->
                statement.setObject(1, id.value)
                definition.attributes.forEachIndexed { index, attribute -> bind(statement, index + 2, attribute, normalized[attribute.name]) }
                statement.executeUpdate()
            }
            val generated = definition.namingSeries?.let { series ->
                val value = connection.prepareStatement("INSERT INTO $namespace.red_entity_sequence (entity_id, last_value) VALUES (?, 1) ON CONFLICT (entity_id) DO UPDATE SET last_value = red_entity_sequence.last_value + 1 RETURNING last_value").use {
                    it.setObject(1, entityId.value)
                    it.executeQuery().use { rows -> check(rows.next()); rows.getLong(1) }
                }
                check(value.toString().length <= series.digits) { "Naming series exhausted" }
                val name = series.prefix + value.toString().padStart(series.digits, '0')
                connection.prepareStatement("INSERT INTO $namespace.red_record_name (entity_id, record_id, name) VALUES (?, ?, ?)").use {
                    it.setObject(1, entityId.value); it.setObject(2, id.value); it.setString(3, name); it.executeUpdate()
                }
                name
            }
            insertAudit(connection, site, entityId, id, actorId, RecordAuditAction.CREATE, 1)
            generated
        }
        return BusinessRecord(id, 1, normalized, recordName)
    }

    override fun update(site: SiteContext, entityId: EntityId, recordId: RecordId, actorId: UUID, expectedVersion: Long, values: Map<String, RecordValue?>): BusinessRecord? {
        require(expectedVersion > 0 && expectedVersion < Long.MAX_VALUE) { "Invalid record version" }
        val (definition, table) = published(site, entityId)
        val normalized = normalize(definition, values)
        val assignments = definition.attributes.map { "\"${PostgresSchemaPublisher.physicalColumnName(it.id)}\" = ?" }
        val sql = "UPDATE $namespace.\"$table\" SET ${if (assignments.isEmpty()) "" else assignments.joinToString(", ") + ", "}version = version + 1 WHERE id = ? AND version = ? RETURNING version"
        val version = database.transaction { connection ->
            connection.prepareStatement(sql).use { statement ->
                definition.attributes.forEachIndexed { index, attribute -> bind(statement, index + 1, attribute, normalized[attribute.name]) }
                statement.setObject(definition.attributes.size + 1, recordId.value)
                statement.setLong(definition.attributes.size + 2, expectedVersion)
                statement.executeQuery().use { rows -> if (rows.next()) rows.getLong(1) else null }
            }
                .also { updatedVersion ->
                    if (updatedVersion != null) insertAudit(connection, site, entityId, recordId, actorId, RecordAuditAction.UPDATE, updatedVersion)
                }
        }
        return version?.let { BusinessRecord(recordId, it, normalized, recordName(entityId, recordId)) }
    }

    override fun audit(site: SiteContext, entityId: EntityId, recordId: RecordId): List<RecordAuditEvent> {
        require(site.siteId == this.site.siteId) { "Site context does not match record database" }
        return database.transaction { connection ->
            connection.prepareStatement("SELECT id, actor_id, action, record_version, occurred_at FROM $namespace.red_record_audit WHERE site_id = ? AND entity_id = ? AND record_id = ? ORDER BY record_version DESC").use { statement ->
                statement.setObject(1, site.siteId.value)
                statement.setObject(2, entityId.value)
                statement.setObject(3, recordId.value)
                statement.executeQuery().use { rows -> buildList {
                    while (rows.next()) add(RecordAuditEvent(
                        rows.getObject(1, UUID::class.java), entityId, recordId, rows.getObject(2, UUID::class.java),
                        RecordAuditAction.valueOf(rows.getString(3)), rows.getLong(4), rows.getObject(5, OffsetDateTime::class.java).toInstant(),
                    ))
                } }
            }
        }
    }

    private fun insertAudit(connection: Connection, site: SiteContext, entityId: EntityId, recordId: RecordId, actorId: UUID, action: RecordAuditAction, version: Long) {
        connection.prepareStatement("INSERT INTO $namespace.red_record_audit (id, site_id, entity_id, record_id, actor_id, action, record_version) VALUES (?, ?, ?, ?, ?, ?, ?)").use {
            it.setObject(1, UUID.randomUUID())
            it.setObject(2, site.siteId.value)
            it.setObject(3, entityId.value)
            it.setObject(4, recordId.value)
            it.setObject(5, actorId)
            it.setString(6, action.name)
            it.setLong(7, version)
            it.executeUpdate()
        }
    }

    override fun list(site: SiteContext, entityId: EntityId, limit: Int): List<BusinessRecord> {
        require(limit in 1..1000) { "Record limit must be between 1 and 1000" }
        val (definition, table) = published(site, entityId)
        val columns = definition.attributes.map { "r.\"${PostgresSchemaPublisher.physicalColumnName(it.id)}\"" }
        val sql = "SELECT r.id, r.version, n.name AS record_name${if (columns.isEmpty()) "" else ", ${columns.joinToString(", ")}"} FROM $namespace.\"$table\" r LEFT JOIN $namespace.red_record_name n ON n.entity_id = ? AND n.record_id = r.id ORDER BY r.created_at DESC, r.id DESC LIMIT ?"
        return database.transaction { connection ->
            connection.prepareStatement(sql).use { statement ->
                statement.setObject(1, entityId.value)
                statement.setInt(2, limit)
                statement.executeQuery().use { rows -> buildList {
                    while (rows.next()) {
                        val values = definition.attributes.associate { attribute ->
                            attribute.name to read(rows, attribute)
                        }
                        add(BusinessRecord(RecordId.parse(rows.getObject("id", java.util.UUID::class.java).toString()), rows.getLong("version"), values, rows.getString("record_name")))
                    }
                } }
            }
        }
    }

    private fun recordName(entityId: EntityId, recordId: RecordId): String? = database.transaction { connection ->
        connection.prepareStatement("SELECT name FROM $namespace.red_record_name WHERE entity_id = ? AND record_id = ?").use {
            it.setObject(1, entityId.value); it.setObject(2, recordId.value)
            it.executeQuery().use { rows -> if (rows.next()) rows.getString(1) else null }
        }
    }

    private fun normalize(definition: EntityDefinition, values: Map<String, RecordValue?>): Map<String, RecordValue?> {
        val byName = definition.attributes.associateBy { it.name }
        require(values.keys.all(byName::containsKey)) { "Unknown record field" }
        return definition.attributes.associate { attribute ->
            val value = values[attribute.name]
            require(value != null || !attribute.required) { "Required record field: ${attribute.name}" }
            if (value != null) require(accepts(attribute, value)) { "Invalid record value: ${attribute.name}" }
            attribute.name to value
        }
    }

    private fun published(requested: SiteContext, entityId: EntityId): Pair<EntityDefinition, String> {
        require(requested.siteId == site.siteId) { "Site context does not match record database" }
        val publication = publisher.findPublication(requested, entityId) ?: throw IllegalStateException("Entity is not published")
        val expectedTable = PostgresSchemaPublisher.physicalTableName(entityId)
        check(publication.tableName == expectedTable) { "Physical table identity mismatch" }
        val definition = checkNotNull(metadata.findRevision(requested, entityId, publication.publishedRevision)) { "Published definition missing" }
        return definition to expectedTable
    }

    private fun accepts(attribute: AttributeDefinition, value: RecordValue): Boolean {
        val raw: Any = when (value) {
            is RecordValue.Text -> value.value
            is RecordValue.Integer -> value.value
            is RecordValue.Decimal -> value.value
            is RecordValue.Bool -> value.value
            is RecordValue.Date -> value.value
            is RecordValue.DateTime -> value.value
        }
        return types.requireType(attribute.typeId).accepts(raw)
    }

    private fun bind(statement: PreparedStatement, index: Int, attribute: AttributeDefinition, value: RecordValue?) {
        if (value == null) {
            statement.setNull(index, when (attribute.typeId) {
                "INTEGER" -> Types.INTEGER
                "DECIMAL" -> Types.NUMERIC
                "BOOLEAN" -> Types.BOOLEAN
                "DATE" -> Types.DATE
                "DATETIME" -> Types.TIMESTAMP_WITH_TIMEZONE
                else -> Types.VARCHAR
            })
            return
        }
        when (value) {
            is RecordValue.Text -> statement.setString(index, value.value)
            is RecordValue.Integer -> statement.setInt(index, value.value)
            is RecordValue.Decimal -> statement.setBigDecimal(index, value.value)
            is RecordValue.Bool -> statement.setBoolean(index, value.value)
            is RecordValue.Date -> statement.setObject(index, value.value)
            is RecordValue.DateTime -> statement.setObject(index, OffsetDateTime.ofInstant(value.value, ZoneOffset.UTC))
        }
    }

    private fun read(rows: ResultSet, attribute: AttributeDefinition): RecordValue? {
        val column = PostgresSchemaPublisher.physicalColumnName(attribute.id)
        return when (attribute.typeId) {
            "TEXT", "EMAIL" -> rows.getString(column)?.let(RecordValue::Text)
            "INTEGER" -> rows.getInt(column).let { if (rows.wasNull()) null else RecordValue.Integer(it) }
            "DECIMAL" -> rows.getBigDecimal(column)?.let(RecordValue::Decimal)
            "BOOLEAN" -> rows.getBoolean(column).let { if (rows.wasNull()) null else RecordValue.Bool(it) }
            "DATE" -> rows.getObject(column, java.time.LocalDate::class.java)?.let(RecordValue::Date)
            "DATETIME" -> rows.getObject(column, OffsetDateTime::class.java)?.toInstant()?.let(RecordValue::DateTime)
            else -> throw IllegalStateException("Unsupported published attribute type")
        }
    }
}
