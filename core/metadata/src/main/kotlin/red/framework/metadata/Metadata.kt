package red.framework.metadata

import red.framework.kernel.identity.ApplicationId
import red.framework.kernel.identity.AttributeId
import red.framework.kernel.identity.EntityId
import red.framework.kernel.versioning.Revision
import red.framework.kernel.context.SiteContext
import java.util.Collections

private val CANONICAL_NAME = Regex("[a-z][a-z0-9_]{1,61}[a-z0-9]")
private val TYPE_ID = Regex("[A-Z][A-Z0-9_]{0,62}")

/** Immutable attribute specification in one entity revision. */
data class AttributeDefinition(
    val id: AttributeId,
    val name: String,
    val label: String,
    val typeId: String,
    val required: Boolean = false,
) {
    init {
        require(CANONICAL_NAME.matches(name)) { "Invalid attribute name" }
        require(label.isNotBlank()) { "Attribute label is required" }
        require(TYPE_ID.matches(typeId)) { "Invalid attribute type ID" }
    }
}

data class FormTab(val name: String, val label: String, val fields: List<String>) {
    init {
        require(CANONICAL_NAME.matches(name)) { "Invalid tab name" }
        require(label.isNotBlank()) { "Tab label is required" }
        require(fields.distinct().size == fields.size) { "Duplicate field in tab" }
    }
}

data class NamingSeries(val prefix: String, val digits: Int) {
    init {
        require(Regex("[A-Z][A-Z0-9-]{0,31}").matches(prefix)) { "Invalid naming prefix" }
        require(digits in 2..12) { "Naming series digits must be 2 to 12" }
    }
}

/** Complete immutable, validated definition for one entity revision. */
class EntityDefinition(
    val id: EntityId,
    val applicationId: ApplicationId,
    val name: String,
    val label: String,
    val revision: Revision,
    attributes: Collection<AttributeDefinition>,
    formTabs: Collection<FormTab> = emptyList(),
    val namingSeries: NamingSeries? = null,
    val titleAttribute: String? = null,
    listAttributes: Collection<String> = emptyList(),
) {
    val attributes: List<AttributeDefinition> = Collections.unmodifiableList(attributes.toList())
    val formTabs: List<FormTab> = Collections.unmodifiableList(formTabs.map { it.copy(fields = Collections.unmodifiableList(it.fields.toList())) })
    val listAttributes: List<String> = Collections.unmodifiableList(listAttributes.toList())

    init {
        require(CANONICAL_NAME.matches(name)) { "Invalid entity name" }
        require(label.isNotBlank()) { "Entity label is required" }
        require(this.attributes.map { it.name }.toSet().size == this.attributes.size) { "Duplicate attribute name" }
        require(this.attributes.map { it.id }.toSet().size == this.attributes.size) { "Duplicate attribute ID" }
        val fieldNames = this.attributes.map { it.name }.toSet()
        require(this.formTabs.map { it.name }.distinct().size == this.formTabs.size) { "Duplicate tab name" }
        require(this.formTabs.flatMap { it.fields }.distinct().size == this.formTabs.sumOf { it.fields.size }) { "Field appears in more than one tab" }
        require(this.formTabs.flatMap { it.fields }.all(fieldNames::contains)) { "Tab references unknown field" }
        require(titleAttribute == null || titleAttribute in fieldNames) { "Title references unknown field" }
        require(this.listAttributes.distinct().size == this.listAttributes.size && this.listAttributes.all(fieldNames::contains)) { "Invalid list fields" }
        require(this.listAttributes.size <= 12) { "Too many list fields" }
    }

    override fun equals(other: Any?): Boolean = other is EntityDefinition &&
        id == other.id && applicationId == other.applicationId && name == other.name &&
        label == other.label && revision == other.revision && attributes == other.attributes &&
        formTabs == other.formTabs && namingSeries == other.namingSeries &&
        titleAttribute == other.titleAttribute && listAttributes == other.listAttributes

    override fun hashCode(): Int = listOf(id, applicationId, name, label, revision, attributes, formTabs, namingSeries, titleAttribute, listAttributes).hashCode()
}

/** Site-scoped persistence contract. Implementations must use an actual durable store. */
interface MetadataRepository {
    fun create(site: SiteContext, definition: EntityDefinition)
    /** Returns false if the active revision no longer matches the expected revision. */
    fun saveRevision(site: SiteContext, definition: EntityDefinition, expectedActive: Revision): Boolean
    fun findActive(site: SiteContext, entityId: EntityId): EntityDefinition?
    fun findRevision(site: SiteContext, entityId: EntityId, revision: Revision): EntityDefinition?
    fun listRevisions(site: SiteContext, entityId: EntityId): List<EntityDefinition>
    fun listByApplication(site: SiteContext, applicationId: ApplicationId): List<EntityDefinition>
}
