package red.framework.attributes

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.Collections

/** Logical value family. Physical storage is chosen by the schema engine. */
enum class ValueKind { TEXT, INTEGER, DECIMAL, BOOLEAN, DATE, DATETIME }

/** Validates one logical attribute type without depending on PostgreSQL or HTTP. */
interface AttributeTypeDescriptor {
    val id: String
    val valueKind: ValueKind
    fun accepts(value: Any): Boolean
}

/** Immutable registry assembled from built-in and trusted extension descriptors. */
class AttributeTypeRegistry(descriptors: Collection<AttributeTypeDescriptor>) {
    private val byId: Map<String, AttributeTypeDescriptor>

    init {
        val copied = descriptors.toList()
        require(copied.all { Regex("[A-Z][A-Z0-9_]{0,62}").matches(it.id) }) { "Invalid type ID" }
        require(copied.map { it.id }.toSet().size == copied.size) { "Duplicate attribute type ID" }
        byId = Collections.unmodifiableMap(copied.associateBy { it.id })
    }

    fun find(id: String): AttributeTypeDescriptor? = byId[id]
    fun requireType(id: String): AttributeTypeDescriptor = byId[id]
        ?: throw IllegalArgumentException("Unsupported attribute type: $id")
    fun all(): List<AttributeTypeDescriptor> = byId.values.sortedBy { it.id }
}

private class SimpleType(
    override val id: String,
    override val valueKind: ValueKind,
    private val predicate: (Any) -> Boolean,
) : AttributeTypeDescriptor {
    override fun accepts(value: Any): Boolean = predicate(value)
}

/** Initial supported types; further types require a real descriptor and tests. */
object BuiltInAttributeTypes {
    fun registry(): AttributeTypeRegistry = AttributeTypeRegistry(listOf(
        SimpleType("TEXT", ValueKind.TEXT) { it is String },
        SimpleType("INTEGER", ValueKind.INTEGER) { it is Int },
        SimpleType("DECIMAL", ValueKind.DECIMAL) { it is BigDecimal },
        SimpleType("BOOLEAN", ValueKind.BOOLEAN) { it is Boolean },
        SimpleType("DATE", ValueKind.DATE) { it is LocalDate },
        SimpleType("DATETIME", ValueKind.DATETIME) { it is Instant },
        SimpleType("EMAIL", ValueKind.TEXT) { it is String && EMAIL_PATTERN.matches(it) },
    ))

    private val EMAIL_PATTERN = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
}
