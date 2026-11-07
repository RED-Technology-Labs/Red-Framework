package red.framework.attributes

import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AttributeTypeRegistryTest {
    @Test
    fun `built in descriptors validate real values`() {
        val registry = BuiltInAttributeTypes.registry()
        assertTrue(registry.requireType("TEXT").accepts("hello"))
        assertFalse(registry.requireType("TEXT").accepts(42))
        assertTrue(registry.requireType("DECIMAL").accepts(BigDecimal("1.25")))
        assertFalse(registry.requireType("DECIMAL").accepts(1.25))
        assertTrue(registry.requireType("EMAIL").accepts("person@example.com"))
        assertFalse(registry.requireType("EMAIL").accepts("invalid"))
        assertEquals(7, registry.all().size)
    }

    @Test
    fun `registry rejects duplicate IDs and accepts trusted extensions`() {
        val custom = object : AttributeTypeDescriptor {
            override val id = "CUSTOM_ID"
            override val valueKind = ValueKind.TEXT
            override fun accepts(value: Any) = value is String && value.startsWith("X-")
        }
        val registry = AttributeTypeRegistry(BuiltInAttributeTypes.registry().all() + custom)
        assertTrue(registry.requireType("CUSTOM_ID").accepts("X-42"))
        assertFailsWith<IllegalArgumentException> { AttributeTypeRegistry(listOf(custom, custom)) }
        assertFailsWith<IllegalArgumentException> { registry.requireType("UNKNOWN") }
    }
}
