package red.framework.metadata

import red.framework.attributes.AttributeTypeRegistry
import red.framework.attributes.BuiltInAttributeTypes

/** Checks registered type support before a definition enters durable storage. */
class MetadataValidator(private val types: AttributeTypeRegistry) {
    fun validate(definition: EntityDefinition) {
        definition.attributes.forEach { types.requireType(it.typeId) }
    }

    companion object {
        fun withBuiltIns(): MetadataValidator = MetadataValidator(BuiltInAttributeTypes.registry())
    }
}
