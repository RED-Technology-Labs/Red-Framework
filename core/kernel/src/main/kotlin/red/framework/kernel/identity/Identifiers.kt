package red.framework.kernel.identity

import java.util.UUID

private fun parseCanonicalUuid(raw: String): UUID {
    val parsed = UUID.fromString(raw)
    require(parsed.toString() == raw) { "Identifier must use canonical lowercase UUID format" }
    return parsed
}

/** Stable site identity. Canonical UUID formatting prevents alias values. */
@JvmInline
value class SiteId private constructor(val value: UUID) {
    companion object {
        fun new(): SiteId = SiteId(UUID.randomUUID())
        fun parse(raw: String): SiteId = SiteId(parseCanonicalUuid(raw))
    }
    override fun toString(): String = value.toString()
}

/** Stable application identity, independent of its name. */
@JvmInline
value class ApplicationId private constructor(val value: UUID) {
    companion object {
        fun new(): ApplicationId = ApplicationId(UUID.randomUUID())
        fun parse(raw: String): ApplicationId = ApplicationId(parseCanonicalUuid(raw))
    }
    override fun toString(): String = value.toString()
}

/** Stable entity identity, independent of metadata revisions. */
@JvmInline
value class EntityId private constructor(val value: UUID) {
    companion object {
        fun new(): EntityId = EntityId(UUID.randomUUID())
        fun parse(raw: String): EntityId = EntityId(parseCanonicalUuid(raw))
    }
    override fun toString(): String = value.toString()
}

/** Stable attribute identity, independent of its display or canonical name. */
@JvmInline
value class AttributeId private constructor(val value: UUID) {
    companion object {
        fun new(): AttributeId = AttributeId(UUID.randomUUID())
        fun parse(raw: String): AttributeId = AttributeId(parseCanonicalUuid(raw))
    }
    override fun toString(): String = value.toString()
}

/** Stable record identity for dynamic entities. */
@JvmInline
value class RecordId private constructor(val value: UUID) {
    companion object {
        fun new(): RecordId = RecordId(UUID.randomUUID())
        fun parse(raw: String): RecordId = RecordId(parseCanonicalUuid(raw))
    }
    override fun toString(): String = value.toString()
}
