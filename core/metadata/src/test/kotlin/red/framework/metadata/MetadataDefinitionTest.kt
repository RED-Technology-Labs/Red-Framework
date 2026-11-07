package red.framework.metadata

import red.framework.kernel.identity.ApplicationId
import red.framework.kernel.identity.AttributeId
import red.framework.kernel.identity.EntityId
import red.framework.kernel.versioning.Revision
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MetadataDefinitionTest {
    @Test
    fun `rejects duplicate attribute names and preserves immutable input snapshot`() {
        val attribute = AttributeDefinition(AttributeId.new(), "customer_name", "Customer name", "TEXT", true)
        val attributes = mutableListOf(attribute)
        val definition = EntityDefinition(EntityId.new(), ApplicationId.new(), "customer", "Customer", Revision.INITIAL, attributes)
        attributes.clear()
        assertEquals(listOf(attribute), definition.attributes)
        assertFailsWith<UnsupportedOperationException> { (definition.attributes as MutableList).clear() }
        assertFailsWith<IllegalArgumentException> {
            EntityDefinition(EntityId.new(), ApplicationId.new(), "customer", "Customer", Revision.INITIAL,
                listOf(attribute, attribute.copy(id = AttributeId.new())))
        }
    }

    @Test
    fun `rejects invalid names and type IDs before persistence`() {
        assertFailsWith<IllegalArgumentException> {
            AttributeDefinition(AttributeId.new(), "bad-name", "Bad", "TEXT")
        }
        assertFailsWith<IllegalArgumentException> {
            AttributeDefinition(AttributeId.new(), "valid_name", "Bad", "text")
        }
        assertFailsWith<IllegalArgumentException> {
            EntityDefinition(EntityId.new(), ApplicationId.new(), "Bad Name", "Bad", Revision.INITIAL, emptyList())
        }
    }

    @Test
    fun `validator rejects unregistered types and accepts built ins`() {
        val id = EntityId.new()
        val appId = ApplicationId.new()
        val validator = MetadataValidator.withBuiltIns()
        val supported = EntityDefinition(id, appId, "customer", "Customer", Revision.INITIAL,
            listOf(AttributeDefinition(AttributeId.new(), "email", "Email", "EMAIL")))
        validator.validate(supported)
        val unsupported = EntityDefinition(id, appId, "customer", "Customer", Revision.INITIAL,
            listOf(AttributeDefinition(AttributeId.new(), "future_field", "Future", "FUTURE_TYPE")))
        assertFailsWith<IllegalArgumentException> { validator.validate(unsupported) }
    }
}
