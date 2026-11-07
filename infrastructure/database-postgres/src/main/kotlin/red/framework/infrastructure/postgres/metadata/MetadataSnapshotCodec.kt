package red.framework.infrastructure.postgres.metadata

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import red.framework.kernel.identity.ApplicationId
import red.framework.kernel.identity.AttributeId
import red.framework.kernel.identity.EntityId
import red.framework.kernel.versioning.Revision
import red.framework.metadata.AttributeDefinition
import red.framework.metadata.EntityDefinition
import red.framework.metadata.FormTab
import red.framework.metadata.NamingSeries
import java.security.MessageDigest

/** Versioned JSONB representation; encoding order is fixed for checksum stability. */
internal object MetadataSnapshotCodec {
    const val FORMAT_VERSION = 1

    fun encode(definition: EntityDefinition): String = JsonObject(linkedMapOf(
        "formatVersion" to JsonPrimitive(FORMAT_VERSION),
        "id" to JsonPrimitive(definition.id.toString()),
        "applicationId" to JsonPrimitive(definition.applicationId.toString()),
        "name" to JsonPrimitive(definition.name),
        "label" to JsonPrimitive(definition.label),
        "revision" to JsonPrimitive(definition.revision.value),
        "formTabs" to JsonArray(definition.formTabs.map { tab -> JsonObject(linkedMapOf(
            "name" to JsonPrimitive(tab.name), "label" to JsonPrimitive(tab.label),
            "fields" to JsonArray(tab.fields.map { JsonPrimitive(it) }),
        )) }),
        "namingSeries" to (definition.namingSeries?.let { JsonObject(linkedMapOf(
            "prefix" to JsonPrimitive(it.prefix), "digits" to JsonPrimitive(it.digits),
        )) } ?: JsonNull),
        "titleAttribute" to (definition.titleAttribute?.let(::JsonPrimitive) ?: JsonNull),
        "listAttributes" to JsonArray(definition.listAttributes.map { JsonPrimitive(it) }),
        "attributes" to JsonArray(definition.attributes.map { attribute ->
            JsonObject(linkedMapOf(
                "id" to JsonPrimitive(attribute.id.toString()),
                "name" to JsonPrimitive(attribute.name),
                "label" to JsonPrimitive(attribute.label),
                "typeId" to JsonPrimitive(attribute.typeId),
                "required" to JsonPrimitive(attribute.required),
            ))
        }),
    )).toString()

    fun decode(raw: String): EntityDefinition {
        val obj = Json.parseToJsonElement(raw).jsonObject
        require(obj.getValue("formatVersion").jsonPrimitive.content.toInt() == FORMAT_VERSION) {
            "Unsupported metadata format"
        }
        return EntityDefinition(
            id = EntityId.parse(obj.getValue("id").jsonPrimitive.content),
            applicationId = ApplicationId.parse(obj.getValue("applicationId").jsonPrimitive.content),
            name = obj.getValue("name").jsonPrimitive.content,
            label = obj.getValue("label").jsonPrimitive.content,
            revision = Revision.of(obj.getValue("revision").jsonPrimitive.content.toLong()),
            attributes = obj.getValue("attributes").jsonArray.map { item ->
                val attr = item.jsonObject
                AttributeDefinition(
                    id = AttributeId.parse(attr.getValue("id").jsonPrimitive.content),
                    name = attr.getValue("name").jsonPrimitive.content,
                    label = attr.getValue("label").jsonPrimitive.content,
                    typeId = attr.getValue("typeId").jsonPrimitive.content,
                    required = attr.getValue("required").jsonPrimitive.content.toBooleanStrict(),
                )
            },
            formTabs = obj["formTabs"]?.jsonArray?.map { item ->
                val tab = item.jsonObject
                FormTab(tab.getValue("name").jsonPrimitive.content, tab.getValue("label").jsonPrimitive.content,
                    tab.getValue("fields").jsonArray.map { it.jsonPrimitive.content })
            } ?: emptyList(),
            namingSeries = obj["namingSeries"]?.takeUnless { it == JsonNull }?.jsonObject?.let {
                NamingSeries(it.getValue("prefix").jsonPrimitive.content, it.getValue("digits").jsonPrimitive.content.toInt())
            },
            titleAttribute = obj["titleAttribute"]?.takeUnless { it == JsonNull }?.jsonPrimitive?.content,
            listAttributes = obj["listAttributes"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList(),
        )
    }

    fun checksum(definition: EntityDefinition): String = checksum(encode(definition))

    fun checksum(raw: String): String = MessageDigest.getInstance("SHA-256")
        .digest(canonical(Json.parseToJsonElement(raw)).toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private fun canonical(element: JsonElement): String = when (element) {
        is JsonObject -> element.entries.sortedBy { it.key }.joinToString(prefix = "{", postfix = "}") {
            JsonPrimitive(it.key).toString() + ":" + canonical(it.value)
        }
        is JsonArray -> element.joinToString(prefix = "[", postfix = "]") { canonical(it) }
        else -> element.toString()
    }
}
