package red.framework.records

import red.framework.kernel.context.SiteContext
import red.framework.kernel.identity.EntityId
import red.framework.kernel.identity.RecordId
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.Collections
import java.util.UUID

sealed interface RecordValue {
    data class Text(val value: String) : RecordValue
    data class Integer(val value: Int) : RecordValue
    data class Decimal(val value: BigDecimal) : RecordValue
    data class Bool(val value: Boolean) : RecordValue
    data class Date(val value: LocalDate) : RecordValue
    data class DateTime(val value: Instant) : RecordValue
}

class BusinessRecord(val id: RecordId, val version: Long, values: Map<String, RecordValue?>, val name: String? = null) {
    init { require(version > 0) { "Record version must be positive" } }
    val values: Map<String, RecordValue?> = Collections.unmodifiableMap(values.toMap())
}

enum class RecordAuditAction { CREATE, UPDATE }

data class RecordAuditEvent(
    val id: UUID,
    val entityId: EntityId,
    val recordId: RecordId,
    val actorId: UUID,
    val action: RecordAuditAction,
    val recordVersion: Long,
    val at: Instant,
)

interface RecordRepository {
    fun create(site: SiteContext, entityId: EntityId, actorId: UUID, values: Map<String, RecordValue?>): BusinessRecord
    fun list(site: SiteContext, entityId: EntityId, limit: Int = 100): List<BusinessRecord>
    /** Returns null if the record is missing or its version changed. */
    fun update(site: SiteContext, entityId: EntityId, recordId: RecordId, actorId: UUID, expectedVersion: Long, values: Map<String, RecordValue?>): BusinessRecord?
    fun audit(site: SiteContext, entityId: EntityId, recordId: RecordId): List<RecordAuditEvent>
}
