package red.framework.server

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.response.respondText
import io.ktor.server.response.header
import io.ktor.server.request.receiveText
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.ktor.server.routing.get
import io.ktor.server.routing.delete
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.int
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.long
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import red.framework.application.ApplicationManifest
import red.framework.application.ApplicationRegistry
import red.framework.application.ApplicationVersion
import red.framework.infrastructure.postgres.PostgresConnectionUrl
import red.framework.infrastructure.postgres.PostgresDatabase
import red.framework.infrastructure.postgres.application.PostgresApplicationRegistry
import red.framework.infrastructure.migrations.FrameworkMigrator
import red.framework.kernel.identity.ApplicationId
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import red.framework.identity.IdentityRepository
import red.framework.identity.Administrator
import red.framework.identity.InitialSetup
import red.framework.identity.EntityAction
import red.framework.identity.EntityGrant
import red.framework.infrastructure.postgres.identity.PostgresIdentityRepository
import red.framework.infrastructure.postgres.site.PostgresSiteRegistry
import red.framework.site.SiteRegistry
import red.framework.site.SiteStatus
import red.framework.attributes.BuiltInAttributeTypes
import red.framework.kernel.context.SiteContext
import red.framework.kernel.identity.AttributeId
import red.framework.kernel.identity.EntityId
import red.framework.kernel.identity.RecordId
import red.framework.kernel.versioning.Revision
import red.framework.metadata.AttributeDefinition
import red.framework.metadata.EntityDefinition
import red.framework.metadata.FormTab
import red.framework.metadata.NamingSeries
import red.framework.metadata.MetadataRepository
import red.framework.infrastructure.postgres.metadata.PostgresMetadataRepository
import red.framework.infrastructure.postgres.schema.PostgresSchemaPublisher
import red.framework.schema.SchemaPublisher
import red.framework.schema.SchemaPublication
import red.framework.records.RecordRepository
import red.framework.records.BusinessRecord
import red.framework.records.RecordValue
import red.framework.records.RecordAuditEvent
import red.framework.infrastructure.postgres.records.PostgresRecordRepository
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

fun Application.serverModule(
    databaseHealthy: () -> Boolean,
    applications: ApplicationRegistry? = null,
    identity: IdentityRepository? = null,
    sites: SiteRegistry? = null,
    metadataForSite: ((SiteContext) -> MetadataRepository)? = null,
    schemaForSite: ((SiteContext) -> SchemaPublisher)? = null,
    recordsForSite: ((SiteContext) -> RecordRepository)? = null,
    secureCookies: Boolean = false,
) {
    routing {
        get("/api/v1/health") {
            if (databaseHealthy()) {
                call.respondText("{\"status\":\"ok\"}", contentType = io.ktor.http.ContentType.Application.Json)
            } else {
                call.respondText("{\"status\":\"unavailable\"}", contentType = io.ktor.http.ContentType.Application.Json, status = HttpStatusCode.ServiceUnavailable)
            }
        }
        get("/api/v1/setup/status") {
            if (identity == null) call.respondText("{\"error\":\"identity_unavailable\"}", ContentType.Application.Json, HttpStatusCode.ServiceUnavailable)
            else call.respondText("{\"initialized\":${identity.isInitialized()}}", ContentType.Application.Json)
        }
        post("/api/v1/setup/initialize") {
            if (!csrfAllowed(call.request.headers["X-RED-CSRF"])) {
                call.respondText("{\"error\":\"csrf_required\"}", ContentType.Application.Json, HttpStatusCode.Forbidden)
                return@post
            }
            if (identity == null) {
                call.respondText("{\"error\":\"identity_unavailable\"}", ContentType.Application.Json, HttpStatusCode.ServiceUnavailable)
                return@post
            }
            if (identity.isInitialized()) {
                call.respondText("{\"error\":\"already_initialized\"}", ContentType.Application.Json, HttpStatusCode.Conflict)
                return@post
            }
            val setup = runCatching {
                val raw = Json.parseToJsonElement(call.receiveText()).jsonObject
                InitialSetup(
                    raw.getValue("fullName").jsonPrimitive.content,
                    raw.getValue("email").jsonPrimitive.content,
                    raw.getValue("password").jsonPrimitive.content,
                    raw.getValue("confirmPassword").jsonPrimitive.content,
                    raw.getValue("siteName").jsonPrimitive.content,
                )
            }.getOrElse {
                call.respondText("{\"error\":\"invalid_setup\"}", ContentType.Application.Json, HttpStatusCode.BadRequest)
                return@post
            }
            try {
                identity.initialize(setup)
            } catch (_: IllegalStateException) {
                call.respondText("{\"error\":\"already_initialized\"}", ContentType.Application.Json, HttpStatusCode.Conflict)
                return@post
            }
            call.respondText("{\"initialized\":true}", ContentType.Application.Json, HttpStatusCode.Created)
        }
        post("/api/v1/auth/login") {
            if (!csrfAllowed(call.request.headers["X-RED-CSRF"])) {
                call.respondText("{\"error\":\"csrf_required\"}", ContentType.Application.Json, HttpStatusCode.Forbidden)
                return@post
            }
            if (identity == null || !identity.isInitialized()) {
                call.respondText("{\"error\":\"setup_required\"}", ContentType.Application.Json, HttpStatusCode.ServiceUnavailable)
                return@post
            }
            val credentials = runCatching {
                val raw = Json.parseToJsonElement(call.receiveText()).jsonObject
                raw.getValue("email").jsonPrimitive.content to raw.getValue("password").jsonPrimitive.content
            }.getOrElse {
                call.respondText("{\"error\":\"invalid_credentials\"}", ContentType.Application.Json, HttpStatusCode.BadRequest)
                return@post
            }
            val token = identity.login(credentials.first, credentials.second)
            if (token == null) {
                call.respondText("{\"error\":\"invalid_credentials\"}", ContentType.Application.Json, HttpStatusCode.Unauthorized)
                return@post
            }
            call.response.header(HttpHeaders.SetCookie, cookieHeader(token, secureCookies))
            val administrator = checkNotNull(identity.findSession(token))
            call.respondText(buildJsonObject {
                put("id", administrator.id.toString()); put("fullName", administrator.fullName); put("email", administrator.email)
                put("administrator", identity.hasRole(administrator.id, "system-administrator"))
            }.toString(), ContentType.Application.Json)
        }
        get("/api/v1/auth/me") {
            val administrator = identity?.findSession(call.request.cookies["red_session"].orEmpty())
            if (administrator == null) call.respondText("{\"error\":\"unauthorized\"}", ContentType.Application.Json, HttpStatusCode.Unauthorized)
            else call.respondText(buildJsonObject {
                put("id", administrator.id.toString()); put("fullName", administrator.fullName); put("email", administrator.email)
                put("administrator", identity?.hasRole(administrator.id, "system-administrator") == true)
            }.toString(), ContentType.Application.Json)
        }
        post("/api/v1/auth/logout") {
            if (!csrfAllowed(call.request.headers["X-RED-CSRF"])) {
                call.respondText("{\"error\":\"csrf_required\"}", ContentType.Application.Json, HttpStatusCode.Forbidden)
                return@post
            }
            call.request.cookies["red_session"]?.let { identity?.logout(it) }
            call.response.header(HttpHeaders.SetCookie, cookieHeader("", secureCookies, 0))
            call.respondText("{\"authenticated\":false}", ContentType.Application.Json)
        }
        get("/api/v1/users") {
            val actor = identity?.findSession(call.request.cookies["red_session"].orEmpty())
            if (actor == null || !identity.hasRole(actor.id, "system-administrator")) {
                call.respondText("{\"error\":\"forbidden\"}", ContentType.Application.Json, HttpStatusCode.Forbidden)
                return@get
            }
            val users = identity.listUsers().map { user -> buildJsonObject {
                put("id", user.id.toString()); put("fullName", user.fullName)
                put("email", user.email); put("active", user.active)
            }.toString() }
            call.respondText("[${users.joinToString(",")}]", ContentType.Application.Json)
        }
        post("/api/v1/users") {
            if (!csrfAllowed(call.request.headers["X-RED-CSRF"])) {
                call.respondText("{\"error\":\"csrf_required\"}", ContentType.Application.Json, HttpStatusCode.Forbidden)
                return@post
            }
            val actor = identity?.findSession(call.request.cookies["red_session"].orEmpty())
            if (actor == null || !identity.hasRole(actor.id, "system-administrator")) {
                call.respondText("{\"error\":\"forbidden\"}", ContentType.Application.Json, HttpStatusCode.Forbidden)
                return@post
            }
            val input = runCatching {
                val raw = Json.parseToJsonElement(call.receiveText()).jsonObject
                Triple(raw.getValue("fullName").jsonPrimitive.content,
                    raw.getValue("email").jsonPrimitive.content, raw.getValue("password").jsonPrimitive.content)
            }.getOrElse {
                call.respondText("{\"error\":\"invalid_user\"}", ContentType.Application.Json, HttpStatusCode.BadRequest)
                return@post
            }
            val user = try {
                identity.createUser(input.first, input.second, input.third)
            } catch (_: IllegalArgumentException) {
                call.respondText("{\"error\":\"invalid_user\"}", ContentType.Application.Json, HttpStatusCode.BadRequest)
                return@post
            } catch (failure: java.sql.SQLException) {
                if (failure.sqlState != "23505") throw failure
                call.respondText("{\"error\":\"duplicate_email\"}", ContentType.Application.Json, HttpStatusCode.Conflict)
                return@post
            }
            call.respondText(buildJsonObject {
                put("id", user.id.toString()); put("fullName", user.fullName)
                put("email", user.email); put("active", user.active)
            }.toString(), ContentType.Application.Json, HttpStatusCode.Created)
        }
        get("/api/v1/users/{id}/permissions") {
            val actor = identity?.findSession(call.request.cookies["red_session"].orEmpty())
            if (actor == null || !identity.hasRole(actor.id, "system-administrator")) {
                call.respondText("{\"error\":\"forbidden\"}", ContentType.Application.Json, HttpStatusCode.Forbidden)
                return@get
            }
            val userId = runCatching { UUID.fromString(call.parameters["id"]) }.getOrNull()
            if (userId == null || identity.listUsers().none { it.id == userId }) {
                call.respondText("{\"error\":\"not_found\"}", ContentType.Application.Json, HttpStatusCode.NotFound)
                return@get
            }
            val grants = identity.listEntityGrants(userId).map(::entityGrantJson)
            call.respondText("[${grants.joinToString(",")}]", ContentType.Application.Json)
        }
        put("/api/v1/users/{id}/permissions/{entityId}/{action}") {
            if (!csrfAllowed(call.request.headers["X-RED-CSRF"])) {
                call.respondText("{\"error\":\"csrf_required\"}", ContentType.Application.Json, HttpStatusCode.Forbidden)
                return@put
            }
            val actor = identity?.findSession(call.request.cookies["red_session"].orEmpty())
            if (actor == null || !identity.hasRole(actor.id, "system-administrator")) {
                call.respondText("{\"error\":\"forbidden\"}", ContentType.Application.Json, HttpStatusCode.Forbidden)
                return@put
            }
            val grant = runCatching { EntityGrant(UUID.fromString(call.parameters["id"]),
                EntityId.parse(call.parameters["entityId"] ?: "").value,
                EntityAction.valueOf(call.parameters["action"] ?: "")) }.getOrNull()
            if (grant == null) {
                call.respondText("{\"error\":\"invalid_permission\"}", ContentType.Application.Json, HttpStatusCode.BadRequest)
                return@put
            }
            try { identity.grantEntityPermission(grant) }
            catch (failure: java.sql.SQLException) {
                if (failure.sqlState != "23503") throw failure
                call.respondText("{\"error\":\"user_or_entity_not_found\"}", ContentType.Application.Json, HttpStatusCode.NotFound)
                return@put
            }
            call.respondText(entityGrantJson(grant), ContentType.Application.Json)
        }
        delete("/api/v1/users/{id}/permissions/{entityId}/{action}") {
            if (!csrfAllowed(call.request.headers["X-RED-CSRF"])) {
                call.respondText("{\"error\":\"csrf_required\"}", ContentType.Application.Json, HttpStatusCode.Forbidden)
                return@delete
            }
            val actor = identity?.findSession(call.request.cookies["red_session"].orEmpty())
            if (actor == null || !identity.hasRole(actor.id, "system-administrator")) {
                call.respondText("{\"error\":\"forbidden\"}", ContentType.Application.Json, HttpStatusCode.Forbidden)
                return@delete
            }
            val grant = runCatching { EntityGrant(UUID.fromString(call.parameters["id"]),
                EntityId.parse(call.parameters["entityId"] ?: "").value,
                EntityAction.valueOf(call.parameters["action"] ?: "")) }.getOrNull()
            if (grant == null) {
                call.respondText("{\"error\":\"invalid_permission\"}", ContentType.Application.Json, HttpStatusCode.BadRequest)
                return@delete
            }
            identity.revokeEntityPermission(grant)
            call.respondText("{\"revoked\":true}", ContentType.Application.Json)
        }
        get("/api/v1/desk/entities") {
            val actor = identity?.findSession(call.request.cookies["red_session"].orEmpty())
            if (actor == null) {
                call.respondText("{\"error\":\"unauthorized\"}", ContentType.Application.Json, HttpStatusCode.Unauthorized)
                return@get
            }
            val site = sites?.list()?.singleOrNull()?.takeIf { it.status == SiteStatus.ACTIVE }
            val context = site?.let { SiteContext(it.id) }
            val repository = context?.let { metadataForSite?.invoke(it) }
            val publisher = context?.let { schemaForSite?.invoke(it) }
            if (context == null || repository == null || publisher == null || applications == null) {
                call.respondText("{\"error\":\"site_unavailable\"}", ContentType.Application.Json, HttpStatusCode.ServiceUnavailable)
                return@get
            }
            val visible = applications.list().flatMap { repository.listByApplication(context, it.id) }
                .mapNotNull { draft -> publisher.findPublication(context, draft.id)?.let { publication ->
                    repository.findRevision(context, draft.id, publication.publishedRevision)
                } }
                .filter { canAccess(identity, actor, it.id, EntityAction.READ) }
                .map { definition -> buildJsonObject {
                    put("definition", Json.parseToJsonElement(entityJson(definition)))
                    put("canCreate", canAccess(identity, actor, definition.id, EntityAction.CREATE))
                    put("canUpdate", canAccess(identity, actor, definition.id, EntityAction.UPDATE))
                }.toString() }
            call.respondText("[${visible.joinToString(",")}]", ContentType.Application.Json)
        }
        get("/api/v1/sites") {
            if (identity?.findSession(call.request.cookies["red_session"].orEmpty())?.let { identity?.hasRole(it.id, "system-administrator") } != true) {
                call.respondText("{\"error\":\"unauthorized\"}", ContentType.Application.Json, HttpStatusCode.Unauthorized)
                return@get
            }
            if (sites == null) {
                call.respondText("{\"error\":\"site_catalog_unavailable\"}", ContentType.Application.Json, HttpStatusCode.ServiceUnavailable)
                return@get
            }
            val values = sites.list().map { site -> buildJsonObject {
                put("id", site.id.toString()); put("canonicalName", site.canonicalName); put("status", site.status.name)
            }.toString() }
            call.respondText("[${values.joinToString(",")} ]", ContentType.Application.Json)
        }
        get("/api/v1/attribute-types") {
            if (identity?.findSession(call.request.cookies["red_session"].orEmpty())?.let { identity?.hasRole(it.id, "system-administrator") } != true) {
                call.respondText("{\"error\":\"unauthorized\"}", ContentType.Application.Json, HttpStatusCode.Unauthorized)
                return@get
            }
            val types = BuiltInAttributeTypes.registry().all().map { JsonPrimitive(it.id).toString() }
            call.respondText("[${types.joinToString(",")}]", ContentType.Application.Json)
        }
        get("/api/v1/entities") {
            if (identity?.findSession(call.request.cookies["red_session"].orEmpty())?.let { identity?.hasRole(it.id, "system-administrator") } != true) {
                call.respondText("{\"error\":\"unauthorized\"}", ContentType.Application.Json, HttpStatusCode.Unauthorized)
                return@get
            }
            val site = sites?.list()?.singleOrNull()?.takeIf { it.status == SiteStatus.ACTIVE }
            val context = site?.let { SiteContext(it.id) }
            val repository = context?.let { metadataForSite?.invoke(it) }
            if (context == null || repository == null || applications == null) {
                call.respondText("{\"error\":\"site_unavailable\"}", ContentType.Application.Json, HttpStatusCode.ServiceUnavailable)
                return@get
            }
            val entities = applications.list().flatMap { repository.listByApplication(context, it.id) }
            call.respondText("[${entities.joinToString(",", transform = ::entityJson)}]", ContentType.Application.Json)
        }
        post("/api/v1/entities") {
            if (!csrfAllowed(call.request.headers["X-RED-CSRF"])) {
                call.respondText("{\"error\":\"csrf_required\"}", ContentType.Application.Json, HttpStatusCode.Forbidden)
                return@post
            }
            if (identity?.findSession(call.request.cookies["red_session"].orEmpty())?.let { identity?.hasRole(it.id, "system-administrator") } != true) {
                call.respondText("{\"error\":\"unauthorized\"}", ContentType.Application.Json, HttpStatusCode.Unauthorized)
                return@post
            }
            val site = sites?.list()?.singleOrNull()?.takeIf { it.status == SiteStatus.ACTIVE }
            val context = site?.let { SiteContext(it.id) }
            val repository = context?.let { metadataForSite?.invoke(it) }
            if (context == null || repository == null || applications == null) {
                call.respondText("{\"error\":\"site_unavailable\"}", ContentType.Application.Json, HttpStatusCode.ServiceUnavailable)
                return@post
            }
            val definition = runCatching {
                val raw = Json.parseToJsonElement(call.receiveText()).jsonObject
                val attributes = raw.getValue("attributes").jsonArray.map { element ->
                    val field = element.jsonObject
                    AttributeDefinition(
                        AttributeId.new(),
                        field.getValue("name").jsonPrimitive.content,
                        field.getValue("label").jsonPrimitive.content,
                        field.getValue("typeId").jsonPrimitive.content,
                        field["required"]?.jsonPrimitive?.boolean ?: false,
                    )
                }
                EntityDefinition(
                    EntityId.new(),
                    ApplicationId.parse(raw.getValue("applicationId").jsonPrimitive.content),
                    raw.getValue("name").jsonPrimitive.content,
                    raw.getValue("label").jsonPrimitive.content,
                    Revision.INITIAL,
                    attributes,
                    formTabs = parseFormTabs(raw),
                    namingSeries = parseNamingSeries(raw),
                    titleAttribute = raw["titleAttribute"]?.takeUnless { it == JsonNull }?.jsonPrimitive?.content,
                    listAttributes = raw["listAttributes"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList(),
                )
            }.getOrElse {
                call.respondText("{\"error\":\"invalid_entity\"}", ContentType.Application.Json, HttpStatusCode.BadRequest)
                return@post
            }
            if (applications.findById(definition.applicationId) == null) {
                call.respondText("{\"error\":\"application_not_found\"}", ContentType.Application.Json, HttpStatusCode.NotFound)
                return@post
            }
            try {
                repository.create(context, definition)
            } catch (_: IllegalArgumentException) {
                call.respondText("{\"error\":\"unsupported_attribute_type\"}", ContentType.Application.Json, HttpStatusCode.BadRequest)
                return@post
            } catch (failure: java.sql.SQLException) {
                if (failure.sqlState != "23505") throw failure
                call.respondText("{\"error\":\"duplicate_entity\"}", ContentType.Application.Json, HttpStatusCode.Conflict)
                return@post
            }
            call.respondText(entityJson(definition), ContentType.Application.Json, HttpStatusCode.Created)
        }
        get("/api/v1/entities/{id}") {
            if (identity?.findSession(call.request.cookies["red_session"].orEmpty())?.let { identity?.hasRole(it.id, "system-administrator") } != true) {
                call.respondText("{\"error\":\"unauthorized\"}", ContentType.Application.Json, HttpStatusCode.Unauthorized)
                return@get
            }
            val entityId = runCatching { EntityId.parse(call.parameters["id"] ?: "") }.getOrNull()
            if (entityId == null) {
                call.respondText("{\"error\":\"invalid_id\"}", ContentType.Application.Json, HttpStatusCode.BadRequest)
                return@get
            }
            val site = sites?.list()?.singleOrNull()?.takeIf { it.status == SiteStatus.ACTIVE }
            val context = site?.let { SiteContext(it.id) }
            val repository = context?.let { metadataForSite?.invoke(it) }
            if (context == null || repository == null) {
                call.respondText("{\"error\":\"site_unavailable\"}", ContentType.Application.Json, HttpStatusCode.ServiceUnavailable)
                return@get
            }
            val found = repository.findActive(context, entityId)
            if (found == null) call.respondText("{\"error\":\"not_found\"}", ContentType.Application.Json, HttpStatusCode.NotFound)
            else call.respondText(entityJson(found), ContentType.Application.Json)
        }
        get("/api/v1/entities/{id}/revisions") {
            if (identity?.findSession(call.request.cookies["red_session"].orEmpty())?.let { identity?.hasRole(it.id, "system-administrator") } != true) {
                call.respondText("{\"error\":\"unauthorized\"}", ContentType.Application.Json, HttpStatusCode.Unauthorized)
                return@get
            }
            val entityId = runCatching { EntityId.parse(call.parameters["id"] ?: "") }.getOrNull()
            if (entityId == null) {
                call.respondText("{\"error\":\"invalid_id\"}", ContentType.Application.Json, HttpStatusCode.BadRequest)
                return@get
            }
            val site = sites?.list()?.singleOrNull()?.takeIf { it.status == SiteStatus.ACTIVE }
            val context = site?.let { SiteContext(it.id) }
            val repository = context?.let { metadataForSite?.invoke(it) }
            if (context == null || repository == null) {
                call.respondText("{\"error\":\"site_unavailable\"}", ContentType.Application.Json, HttpStatusCode.ServiceUnavailable)
                return@get
            }
            if (repository.findActive(context, entityId) == null) {
                call.respondText("{\"error\":\"not_found\"}", ContentType.Application.Json, HttpStatusCode.NotFound)
                return@get
            }
            val revisions = repository.listRevisions(context, entityId)
            call.respondText("[${revisions.joinToString(",", transform = ::entityJson)}]", ContentType.Application.Json)
        }
        put("/api/v1/entities/{id}") {
            if (!csrfAllowed(call.request.headers["X-RED-CSRF"])) {
                call.respondText("{\"error\":\"csrf_required\"}", ContentType.Application.Json, HttpStatusCode.Forbidden)
                return@put
            }
            if (identity?.findSession(call.request.cookies["red_session"].orEmpty())?.let { identity?.hasRole(it.id, "system-administrator") } != true) {
                call.respondText("{\"error\":\"unauthorized\"}", ContentType.Application.Json, HttpStatusCode.Unauthorized)
                return@put
            }
            val entityId = runCatching { EntityId.parse(call.parameters["id"] ?: "") }.getOrNull()
            if (entityId == null) {
                call.respondText("{\"error\":\"invalid_id\"}", ContentType.Application.Json, HttpStatusCode.BadRequest)
                return@put
            }
            val site = sites?.list()?.singleOrNull()?.takeIf { it.status == SiteStatus.ACTIVE }
            val context = site?.let { SiteContext(it.id) }
            val repository = context?.let { metadataForSite?.invoke(it) }
            if (context == null || repository == null) {
                call.respondText("{\"error\":\"site_unavailable\"}", ContentType.Application.Json, HttpStatusCode.ServiceUnavailable)
                return@put
            }
            val active = repository.findActive(context, entityId)
            if (active == null) {
                call.respondText("{\"error\":\"not_found\"}", ContentType.Application.Json, HttpStatusCode.NotFound)
                return@put
            }
            val update = runCatching {
                val raw = Json.parseToJsonElement(call.receiveText()).jsonObject
                val expected = Revision.of(raw.getValue("expectedRevision").jsonPrimitive.long)
                val existingIds = active.attributes.map { it.id }.toSet()
                val attributes = raw.getValue("attributes").jsonArray.map { element ->
                    val field = element.jsonObject
                    val id = field["id"]?.jsonPrimitive?.content?.let(AttributeId::parse) ?: AttributeId.new()
                    require(field["id"] == null || id in existingIds) { "Unknown attribute ID" }
                    AttributeDefinition(
                        id,
                        field.getValue("name").jsonPrimitive.content,
                        field.getValue("label").jsonPrimitive.content,
                        field.getValue("typeId").jsonPrimitive.content,
                        field["required"]?.jsonPrimitive?.boolean ?: false,
                    )
                }
                expected to EntityDefinition(active.id, active.applicationId, active.name,
                    raw.getValue("label").jsonPrimitive.content, expected.next(), attributes,
                    formTabs = parseFormTabs(raw),
                    namingSeries = parseNamingSeries(raw),
                    titleAttribute = raw["titleAttribute"]?.takeUnless { it == JsonNull }?.jsonPrimitive?.content,
                    listAttributes = raw["listAttributes"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList())
            }.getOrElse {
                call.respondText("{\"error\":\"invalid_entity_revision\"}", ContentType.Application.Json, HttpStatusCode.BadRequest)
                return@put
            }
            if (update.first != active.revision) {
                call.respondText("{\"error\":\"revision_conflict\"}", ContentType.Application.Json, HttpStatusCode.Conflict)
                return@put
            }
            try {
                if (!repository.saveRevision(context, update.second, update.first)) {
                    call.respondText("{\"error\":\"revision_conflict\"}", ContentType.Application.Json, HttpStatusCode.Conflict)
                    return@put
                }
            } catch (_: IllegalArgumentException) {
                call.respondText("{\"error\":\"unsupported_attribute_type\"}", ContentType.Application.Json, HttpStatusCode.BadRequest)
                return@put
            }
            call.respondText(entityJson(update.second), ContentType.Application.Json)
        }
        get("/api/v1/entities/{id}/publication") {
            if (identity?.findSession(call.request.cookies["red_session"].orEmpty())?.let { identity?.hasRole(it.id, "system-administrator") } != true) {
                call.respondText("{\"error\":\"unauthorized\"}", ContentType.Application.Json, HttpStatusCode.Unauthorized)
                return@get
            }
            val entityId = runCatching { EntityId.parse(call.parameters["id"] ?: "") }.getOrNull()
            if (entityId == null) {
                call.respondText("{\"error\":\"invalid_id\"}", ContentType.Application.Json, HttpStatusCode.BadRequest)
                return@get
            }
            val site = sites?.list()?.singleOrNull()?.takeIf { it.status == SiteStatus.ACTIVE }
            val context = site?.let { SiteContext(it.id) }
            val publisher = context?.let { schemaForSite?.invoke(it) }
            if (context == null || publisher == null) {
                call.respondText("{\"error\":\"site_unavailable\"}", ContentType.Application.Json, HttpStatusCode.ServiceUnavailable)
                return@get
            }
            val found = publisher.findPublication(context, entityId)
            if (found == null) call.respondText("{\"error\":\"not_published\"}", ContentType.Application.Json, HttpStatusCode.NotFound)
            else call.respondText(publicationJson(found), ContentType.Application.Json)
        }
        post("/api/v1/entities/{id}/publish") {
            if (!csrfAllowed(call.request.headers["X-RED-CSRF"])) {
                call.respondText("{\"error\":\"csrf_required\"}", ContentType.Application.Json, HttpStatusCode.Forbidden)
                return@post
            }
            if (identity?.findSession(call.request.cookies["red_session"].orEmpty())?.let { identity?.hasRole(it.id, "system-administrator") } != true) {
                call.respondText("{\"error\":\"unauthorized\"}", ContentType.Application.Json, HttpStatusCode.Unauthorized)
                return@post
            }
            val entityId = runCatching { EntityId.parse(call.parameters["id"] ?: "") }.getOrNull()
            if (entityId == null) {
                call.respondText("{\"error\":\"invalid_id\"}", ContentType.Application.Json, HttpStatusCode.BadRequest)
                return@post
            }
            val site = sites?.list()?.singleOrNull()?.takeIf { it.status == SiteStatus.ACTIVE }
            val context = site?.let { SiteContext(it.id) }
            val repository = context?.let { metadataForSite?.invoke(it) }
            val publisher = context?.let { schemaForSite?.invoke(it) }
            if (context == null || repository == null || publisher == null) {
                call.respondText("{\"error\":\"site_unavailable\"}", ContentType.Application.Json, HttpStatusCode.ServiceUnavailable)
                return@post
            }
            val active = repository.findActive(context, entityId)
            if (active == null) {
                call.respondText("{\"error\":\"not_found\"}", ContentType.Application.Json, HttpStatusCode.NotFound)
                return@post
            }
            val publication = try { publisher.publish(context, active) }
            catch (_: IllegalStateException) {
                call.respondText("{\"error\":\"schema_migration_required\"}", ContentType.Application.Json, HttpStatusCode.Conflict)
                return@post
            }
            call.respondText(publicationJson(publication), ContentType.Application.Json)
        }
        get("/api/v1/entities/{id}/records") {
            val actor = identity?.findSession(call.request.cookies["red_session"].orEmpty())
            if (actor == null) {
                call.respondText("{\"error\":\"unauthorized\"}", ContentType.Application.Json, HttpStatusCode.Unauthorized)
                return@get
            }
            val entityId = runCatching { EntityId.parse(call.parameters["id"] ?: "") }.getOrNull()
            if (entityId == null) {
                call.respondText("{\"error\":\"invalid_id\"}", ContentType.Application.Json, HttpStatusCode.BadRequest)
                return@get
            }
            if (!canAccess(identity, actor, entityId, EntityAction.READ)) {
                call.respondText("{\"error\":\"forbidden\"}", ContentType.Application.Json, HttpStatusCode.Forbidden)
                return@get
            }
            val site = sites?.list()?.singleOrNull()?.takeIf { it.status == SiteStatus.ACTIVE }
            val context = site?.let { SiteContext(it.id) }
            val records = context?.let { recordsForSite?.invoke(it) }
            if (context == null || records == null) {
                call.respondText("{\"error\":\"site_unavailable\"}", ContentType.Application.Json, HttpStatusCode.ServiceUnavailable)
                return@get
            }
            val listed = try { records.list(context, entityId) }
            catch (_: IllegalStateException) {
                call.respondText("{\"error\":\"entity_not_published\"}", ContentType.Application.Json, HttpStatusCode.Conflict)
                return@get
            }
            call.respondText("[${listed.joinToString(",", transform = ::recordJson)}]", ContentType.Application.Json)
        }
        post("/api/v1/entities/{id}/records") {
            if (!csrfAllowed(call.request.headers["X-RED-CSRF"])) {
                call.respondText("{\"error\":\"csrf_required\"}", ContentType.Application.Json, HttpStatusCode.Forbidden)
                return@post
            }
            val actor = identity?.findSession(call.request.cookies["red_session"].orEmpty())
            if (actor == null) {
                call.respondText("{\"error\":\"unauthorized\"}", ContentType.Application.Json, HttpStatusCode.Unauthorized)
                return@post
            }
            val entityId = runCatching { EntityId.parse(call.parameters["id"] ?: "") }.getOrNull()
            if (entityId == null) {
                call.respondText("{\"error\":\"invalid_id\"}", ContentType.Application.Json, HttpStatusCode.BadRequest)
                return@post
            }
            if (!canAccess(identity, actor, entityId, EntityAction.CREATE)) {
                call.respondText("{\"error\":\"forbidden\"}", ContentType.Application.Json, HttpStatusCode.Forbidden)
                return@post
            }
            val site = sites?.list()?.singleOrNull()?.takeIf { it.status == SiteStatus.ACTIVE }
            val context = site?.let { SiteContext(it.id) }
            val records = context?.let { recordsForSite?.invoke(it) }
            val repository = context?.let { metadataForSite?.invoke(it) }
            val publisher = context?.let { schemaForSite?.invoke(it) }
            if (context == null || records == null || repository == null || publisher == null) {
                call.respondText("{\"error\":\"site_unavailable\"}", ContentType.Application.Json, HttpStatusCode.ServiceUnavailable)
                return@post
            }
            val publication = publisher.findPublication(context, entityId)
            if (publication == null) {
                call.respondText("{\"error\":\"entity_not_published\"}", ContentType.Application.Json, HttpStatusCode.Conflict)
                return@post
            }
            val definition = repository.findRevision(context, entityId, publication.publishedRevision)
            if (definition == null) {
                call.respondText("{\"error\":\"published_definition_missing\"}", ContentType.Application.Json, HttpStatusCode.ServiceUnavailable)
                return@post
            }
            val values = runCatching {
                val raw = Json.parseToJsonElement(call.receiveText()).jsonObject.getValue("values").jsonObject
                val byName = definition.attributes.associateBy { it.name }
                require(raw.keys.all(byName::containsKey)) { "Unknown field" }
                raw.mapValues { (name, element) -> parseRecordValue(byName.getValue(name).typeId, element) }
            }.getOrElse {
                call.respondText("{\"error\":\"invalid_record\"}", ContentType.Application.Json, HttpStatusCode.BadRequest)
                return@post
            }
            val created = try { records.create(context, entityId, actor.id, values) }
            catch (_: IllegalArgumentException) {
                call.respondText("{\"error\":\"invalid_record\"}", ContentType.Application.Json, HttpStatusCode.BadRequest)
                return@post
            } catch (_: IllegalStateException) {
                call.respondText("{\"error\":\"naming_series_exhausted\"}", ContentType.Application.Json, HttpStatusCode.Conflict)
                return@post
            } catch (failure: java.sql.SQLException) {
                if (failure.sqlState != "23505") throw failure
                call.respondText("{\"error\":\"record_name_conflict\"}", ContentType.Application.Json, HttpStatusCode.Conflict)
                return@post
            }
            call.respondText(recordJson(created), ContentType.Application.Json, HttpStatusCode.Created)
        }
        put("/api/v1/entities/{id}/records/{recordId}") {
            if (!csrfAllowed(call.request.headers["X-RED-CSRF"])) {
                call.respondText("{\"error\":\"csrf_required\"}", ContentType.Application.Json, HttpStatusCode.Forbidden)
                return@put
            }
            val actor = identity?.findSession(call.request.cookies["red_session"].orEmpty())
            if (actor == null) {
                call.respondText("{\"error\":\"unauthorized\"}", ContentType.Application.Json, HttpStatusCode.Unauthorized)
                return@put
            }
            val ids = runCatching {
                EntityId.parse(call.parameters["id"] ?: "") to RecordId.parse(call.parameters["recordId"] ?: "")
            }.getOrNull()
            if (ids == null) {
                call.respondText("{\"error\":\"invalid_id\"}", ContentType.Application.Json, HttpStatusCode.BadRequest)
                return@put
            }
            if (!canAccess(identity, actor, ids.first, EntityAction.UPDATE)) {
                call.respondText("{\"error\":\"forbidden\"}", ContentType.Application.Json, HttpStatusCode.Forbidden)
                return@put
            }
            val site = sites?.list()?.singleOrNull()?.takeIf { it.status == SiteStatus.ACTIVE }
            val context = site?.let { SiteContext(it.id) }
            val records = context?.let { recordsForSite?.invoke(it) }
            val repository = context?.let { metadataForSite?.invoke(it) }
            val publisher = context?.let { schemaForSite?.invoke(it) }
            if (context == null || records == null || repository == null || publisher == null) {
                call.respondText("{\"error\":\"site_unavailable\"}", ContentType.Application.Json, HttpStatusCode.ServiceUnavailable)
                return@put
            }
            val publication = publisher.findPublication(context, ids.first)
            if (publication == null) {
                call.respondText("{\"error\":\"entity_not_published\"}", ContentType.Application.Json, HttpStatusCode.Conflict)
                return@put
            }
            val definition = repository.findRevision(context, ids.first, publication.publishedRevision)
            if (definition == null) {
                call.respondText("{\"error\":\"published_definition_missing\"}", ContentType.Application.Json, HttpStatusCode.ServiceUnavailable)
                return@put
            }
            val update = runCatching {
                val raw = Json.parseToJsonElement(call.receiveText()).jsonObject
                val version = raw.getValue("expectedVersion").jsonPrimitive.long
                require(version > 0)
                val values = raw.getValue("values").jsonObject
                val byName = definition.attributes.associateBy { it.name }
                require(values.keys.all(byName::containsKey))
                version to values.mapValues { (name, element) -> parseRecordValue(byName.getValue(name).typeId, element) }
            }.getOrElse {
                call.respondText("{\"error\":\"invalid_record\"}", ContentType.Application.Json, HttpStatusCode.BadRequest)
                return@put
            }
            val revised = try { records.update(context, ids.first, ids.second, actor.id, update.first, update.second) }
            catch (_: IllegalArgumentException) {
                call.respondText("{\"error\":\"invalid_record\"}", ContentType.Application.Json, HttpStatusCode.BadRequest)
                return@put
            }
            if (revised == null) call.respondText("{\"error\":\"record_conflict\"}", ContentType.Application.Json, HttpStatusCode.Conflict)
            else call.respondText(recordJson(revised), ContentType.Application.Json)
        }
        get("/api/v1/entities/{id}/records/{recordId}/audit") {
            val actor = identity?.findSession(call.request.cookies["red_session"].orEmpty())
            if (actor == null) {
                call.respondText("{\"error\":\"unauthorized\"}", ContentType.Application.Json, HttpStatusCode.Unauthorized)
                return@get
            }
            val ids = runCatching {
                EntityId.parse(call.parameters["id"] ?: "") to RecordId.parse(call.parameters["recordId"] ?: "")
            }.getOrNull()
            if (ids == null) {
                call.respondText("{\"error\":\"invalid_id\"}", ContentType.Application.Json, HttpStatusCode.BadRequest)
                return@get
            }
            if (!canAccess(identity, actor, ids.first, EntityAction.READ)) {
                call.respondText("{\"error\":\"forbidden\"}", ContentType.Application.Json, HttpStatusCode.Forbidden)
                return@get
            }
            val site = sites?.list()?.singleOrNull()?.takeIf { it.status == SiteStatus.ACTIVE }
            val context = site?.let { SiteContext(it.id) }
            val records = context?.let { recordsForSite?.invoke(it) }
            if (context == null || records == null) {
                call.respondText("{\"error\":\"site_unavailable\"}", ContentType.Application.Json, HttpStatusCode.ServiceUnavailable)
                return@get
            }
            val events = records.audit(context, ids.first, ids.second)
            call.respondText("[${events.joinToString(",", transform = ::recordAuditJson)}]", ContentType.Application.Json)
        }
        route("/api/v1/applications") {
            get {
                if (identity?.findSession(call.request.cookies["red_session"].orEmpty())?.let { identity?.hasRole(it.id, "system-administrator") } != true) {
                    call.respondText("{\"error\":\"unauthorized\"}", ContentType.Application.Json, HttpStatusCode.Unauthorized)
                    return@get
                }
                if (applications == null) {
                    call.respondText("{\"error\":\"catalog_unavailable\"}", ContentType.Application.Json, HttpStatusCode.ServiceUnavailable)
                    return@get
                }
                val values = applications.list().map(::applicationJson)
                call.respondText("[${values.joinToString(",")} ]", ContentType.Application.Json)
            }
            get("/{id}") {
                if (identity?.findSession(call.request.cookies["red_session"].orEmpty())?.let { identity?.hasRole(it.id, "system-administrator") } != true) {
                    call.respondText("{\"error\":\"unauthorized\"}", ContentType.Application.Json, HttpStatusCode.Unauthorized)
                    return@get
                }
                if (applications == null) {
                    call.respondText("{\"error\":\"catalog_unavailable\"}", ContentType.Application.Json, HttpStatusCode.ServiceUnavailable)
                    return@get
                }
                val id = runCatching { ApplicationId.parse(call.parameters["id"] ?: "") }.getOrNull()
                if (id == null) {
                    call.respondText("{\"error\":\"invalid_id\"}", ContentType.Application.Json, HttpStatusCode.BadRequest)
                    return@get
                }
                val found = applications.findById(id)
                if (found == null) call.respondText("{\"error\":\"not_found\"}", ContentType.Application.Json, HttpStatusCode.NotFound)
                else call.respondText(applicationJson(found), ContentType.Application.Json)
            }
            post {
                if (!csrfAllowed(call.request.headers["X-RED-CSRF"])) {
                    call.respondText("{\"error\":\"csrf_required\"}", ContentType.Application.Json, HttpStatusCode.Forbidden)
                    return@post
                }
                if (identity?.findSession(call.request.cookies["red_session"].orEmpty())?.let { identity?.hasRole(it.id, "system-administrator") } != true) {
                    call.respondText("{\"error\":\"unauthorized\"}", ContentType.Application.Json, HttpStatusCode.Unauthorized)
                    return@post
                }
                if (applications == null) {
                    call.respondText("{\"error\":\"catalog_unavailable\"}", ContentType.Application.Json, HttpStatusCode.ServiceUnavailable)
                    return@post
                }
                val body = runCatching {
                    val raw = Json.parseToJsonElement(call.receiveText()).jsonObject
                    ApplicationManifest(
                        ApplicationId.new(),
                        raw.getValue("canonicalName").jsonPrimitive.content,
                        ApplicationVersion.parse(raw.getValue("version").jsonPrimitive.content),
                        emptyList(),
                    )
                }.getOrElse {
                    call.respondText("{\"error\":\"invalid_application\"}", ContentType.Application.Json, HttpStatusCode.BadRequest)
                    return@post
                }
                try {
                    applications.register(body)
                } catch (failure: java.sql.SQLException) {
                    if (failure.sqlState != "23505") throw failure
                    call.respondText("{\"error\":\"duplicate_name\"}", ContentType.Application.Json, HttpStatusCode.Conflict)
                    return@post
                }
                call.respondText(applicationJson(body), ContentType.Application.Json, HttpStatusCode.Created)
            }
        }
    }
}

private fun csrfAllowed(value: String?): Boolean = value == "1"

private fun canAccess(identity: IdentityRepository?, actor: Administrator, entityId: EntityId, action: EntityAction): Boolean =
    identity?.hasRole(actor.id, "system-administrator") == true ||
        identity?.hasEntityPermission(actor.id, entityId.value, action) == true

private fun entityGrantJson(value: EntityGrant): String = buildJsonObject {
    put("userId", value.userId.toString())
    put("entityId", value.entityId.toString())
    put("action", value.action.name)
}.toString()

private fun cookieHeader(token: String, secure: Boolean, maxAge: Int = 43_200): String =
    "red_session=$token; Path=/; HttpOnly; SameSite=Strict; Max-Age=$maxAge" + if (secure) "; Secure" else ""

private fun applicationJson(value: ApplicationManifest): String = buildJsonObject {
    put("id", value.id.toString())
    put("canonicalName", value.canonicalName)
    put("version", value.version.toString())
}.toString()

private fun entityJson(value: EntityDefinition): String = buildJsonObject {
    put("id", value.id.toString())
    put("applicationId", value.applicationId.toString())
    put("name", value.name)
    put("label", value.label)
    put("revision", value.revision.value)
    put("formTabs", buildJsonArray { value.formTabs.forEach { tab -> add(buildJsonObject {
        put("name", tab.name); put("label", tab.label)
        put("fields", buildJsonArray { tab.fields.forEach { add(JsonPrimitive(it)) } })
    }) } })
    put("namingSeries", value.namingSeries?.let { buildJsonObject {
        put("prefix", it.prefix); put("digits", it.digits)
    } } ?: JsonNull)
    put("titleAttribute", value.titleAttribute?.let { JsonPrimitive(it) } ?: JsonNull)
    put("listAttributes", buildJsonArray { value.listAttributes.forEach { add(JsonPrimitive(it)) } })
    put("attributes", buildJsonArray {
        value.attributes.forEach { attribute -> add(buildJsonObject {
            put("id", attribute.id.toString()); put("name", attribute.name); put("label", attribute.label)
            put("typeId", attribute.typeId); put("required", attribute.required)
        }) }
    })
}.toString()

private fun parseFormTabs(raw: kotlinx.serialization.json.JsonObject): List<FormTab> =
    raw["formTabs"]?.jsonArray?.map { item ->
        val tab = item.jsonObject
        FormTab(tab.getValue("name").jsonPrimitive.content, tab.getValue("label").jsonPrimitive.content,
            tab.getValue("fields").jsonArray.map { it.jsonPrimitive.content })
    } ?: emptyList()

private fun parseNamingSeries(raw: kotlinx.serialization.json.JsonObject): NamingSeries? =
    raw["namingSeries"]?.takeUnless { it == JsonNull }?.jsonObject?.let {
        NamingSeries(it.getValue("prefix").jsonPrimitive.content, it.getValue("digits").jsonPrimitive.int)
    }

private fun publicationJson(value: SchemaPublication): String = buildJsonObject {
    put("entityId", value.entityId.toString())
    put("publishedRevision", value.publishedRevision.value)
    put("tableName", value.tableName)
}.toString()

private fun parseRecordValue(typeId: String, element: JsonElement): RecordValue? {
    if (element == JsonNull) return null
    val primitive = element.jsonPrimitive
    return when (typeId) {
        "TEXT", "EMAIL" -> { require(primitive.isString); RecordValue.Text(primitive.content) }
        "INTEGER" -> { require(!primitive.isString); RecordValue.Integer(primitive.int) }
        "DECIMAL" -> { require(!primitive.isString); RecordValue.Decimal(BigDecimal(primitive.content)) }
        "BOOLEAN" -> { require(!primitive.isString); RecordValue.Bool(primitive.boolean) }
        "DATE" -> { require(primitive.isString); RecordValue.Date(LocalDate.parse(primitive.content)) }
        "DATETIME" -> { require(primitive.isString); RecordValue.DateTime(Instant.parse(primitive.content)) }
        else -> throw IllegalArgumentException("Unsupported record type")
    }
}

private fun recordJson(record: BusinessRecord): String = buildJsonObject {
    put("id", record.id.toString())
    put("name", record.name?.let { JsonPrimitive(it) } ?: JsonNull)
    put("version", record.version)
    put("values", buildJsonObject {
        record.values.forEach { (name, value) -> put(name, when (value) {
            null -> JsonNull
            is RecordValue.Text -> JsonPrimitive(value.value)
            is RecordValue.Integer -> JsonPrimitive(value.value)
            is RecordValue.Decimal -> JsonPrimitive(value.value)
            is RecordValue.Bool -> JsonPrimitive(value.value)
            is RecordValue.Date -> JsonPrimitive(value.value.toString())
            is RecordValue.DateTime -> JsonPrimitive(value.value.toString())
        }) }
    })
}.toString()

private fun recordAuditJson(event: RecordAuditEvent): String = buildJsonObject {
    put("id", event.id.toString())
    put("entityId", event.entityId.toString())
    put("recordId", event.recordId.toString())
    put("actorId", event.actorId.toString())
    put("action", event.action.name)
    put("recordVersion", event.recordVersion)
    put("at", event.at.toString())
}.toString()

fun main() {
    val url = System.getenv("DATABASE_URL")?.takeIf { it.isNotBlank() }
        ?: error("DATABASE_URL is required")
    val database = PostgresDatabase(PostgresConnectionUrl.parse(url))
    Runtime.getRuntime().addShutdownHook(Thread { database.close() })
    val schema = System.getenv("RED_SCHEMA")?.takeIf { it.isNotBlank() } ?: "red_framework"
    require(Regex("[a-z][a-z0-9_]{0,62}").matches(schema)) { "Invalid RED_SCHEMA" }
    database.transaction { connection -> connection.createStatement().use { it.execute("CREATE SCHEMA IF NOT EXISTS \"$schema\"") } }
    FrameworkMigrator(database.dataSource, schema).migrate()
    val applications = PostgresApplicationRegistry(database, schema)
    val identity = PostgresIdentityRepository(database, schema)
    val sites = PostgresSiteRegistry(database, schema)
    val metadataForSite: (SiteContext) -> MetadataRepository = { context -> PostgresMetadataRepository(database, context, schema) }
    val schemaForSite: (SiteContext) -> SchemaPublisher = { context -> PostgresSchemaPublisher(database, context, schema) }
    val recordsForSite: (SiteContext) -> RecordRepository = { context ->
        PostgresRecordRepository(database, context, schema, metadataForSite(context), schemaForSite(context))
    }
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    embeddedServer(Netty, port = port, host = "127.0.0.1") {
        serverModule(database::isHealthy, applications, identity, sites, metadataForSite, schemaForSite, recordsForSite, secureCookies = System.getenv("RED_COOKIE_SECURE") == "true")
    }.start(wait = true)
}
