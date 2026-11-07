package red.framework.server

import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import red.framework.infrastructure.migrations.FrameworkMigrator
import red.framework.infrastructure.postgres.PostgresConnectionUrl
import red.framework.infrastructure.postgres.PostgresDatabase
import red.framework.infrastructure.postgres.application.PostgresApplicationRegistry
import red.framework.infrastructure.postgres.identity.PostgresIdentityRepository
import red.framework.infrastructure.postgres.site.PostgresSiteRegistry
import red.framework.infrastructure.postgres.metadata.PostgresMetadataRepository
import red.framework.infrastructure.postgres.schema.PostgresSchemaPublisher
import red.framework.infrastructure.postgres.records.PostgresRecordRepository
import java.util.UUID
import java.sql.SQLException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ServerIntegrationTest {
    @Test fun `restricted user can access only granted Entity actions`() {
        val url = System.getenv("TEST_DATABASE_URL").orEmpty()
        assumeTrue(url.isNotBlank(), "TEST_DATABASE_URL is not configured")
        val schema = "red_test_${UUID.randomUUID().toString().replace("-", "")}" 
        PostgresDatabase(PostgresConnectionUrl.parse(url, maximumPoolSize = 2)).use { database ->
            var created = false
            try {
                database.transaction { connection -> connection.createStatement().use { it.execute("CREATE SCHEMA $schema") } }
                created = true
                FrameworkMigrator(database.dataSource, schema).migrate()
                val identity = PostgresIdentityRepository(database, schema)
                identity.initialize(red.framework.identity.InitialSetup("Test Admin", "admin@example.com", "Strong#Pass123", "Strong#Pass123", "first-site"))
                val applications = PostgresApplicationRegistry(database, schema)
                val appId = red.framework.kernel.identity.ApplicationId.new()
                applications.register(red.framework.application.ApplicationManifest(appId, "demo-app", red.framework.application.ApplicationVersion.parse("1.0.0"), emptyList()))
                val site = red.framework.kernel.context.SiteContext(PostgresSiteRegistry(database, schema).findByCanonicalName("first-site")!!.id)
                val entityId = red.framework.kernel.identity.EntityId.new()
                val metadata = PostgresMetadataRepository(database, site, schema)
                val definition = red.framework.metadata.EntityDefinition(entityId, appId, "customer", "Customer", red.framework.kernel.versioning.Revision.INITIAL,
                    listOf(red.framework.metadata.AttributeDefinition(red.framework.kernel.identity.AttributeId.new(), "email", "Email", "EMAIL", true)))
                metadata.create(site, definition)
                PostgresSchemaPublisher(database, site, schema).publish(site, definition)
                val restricted = identity.createUser("Restricted User", "restricted@example.com", "Another#Pass123")
                testApplication {
                    application { serverModule(database::isHealthy, applications, identity, PostgresSiteRegistry(database, schema),
                        { context -> PostgresMetadataRepository(database, context, schema) },
                        { context -> PostgresSchemaPublisher(database, context, schema) },
                        { context -> PostgresRecordRepository(database, context, schema, PostgresMetadataRepository(database, context, schema), PostgresSchemaPublisher(database, context, schema)) }) }
                    val adminToken = identity.login("admin@example.com", "Strong#Pass123")!!
                    val userToken = identity.login("restricted@example.com", "Another#Pass123")!!
                    val adminCookie = "red_session=$adminToken"
                    val userCookie = "red_session=$userToken"
                    assertEquals(HttpStatusCode.Forbidden, client.get("/api/v1/entities/$entityId/records") { headers.append(HttpHeaders.Cookie, userCookie) }.status)
                    assertEquals(HttpStatusCode.OK, client.put("/api/v1/users/${restricted.id}/permissions/$entityId/READ") {
                        headers.append("X-RED-CSRF", "1"); headers.append(HttpHeaders.Cookie, adminCookie)
                    }.status)
                    assertEquals(HttpStatusCode.OK, client.get("/api/v1/entities/$entityId/records") { headers.append(HttpHeaders.Cookie, userCookie) }.status)
                    assertTrue(client.get("/api/v1/desk/entities") { headers.append(HttpHeaders.Cookie, userCookie) }.bodyAsText().contains("customer"))
                    assertEquals(HttpStatusCode.Forbidden, client.post("/api/v1/entities/$entityId/records") {
                        contentType(ContentType.Application.Json); headers.append("X-RED-CSRF", "1"); headers.append(HttpHeaders.Cookie, userCookie)
                        setBody("""{"values":{"email":"second@example.com"}}""")
                    }.status)
                    assertEquals(HttpStatusCode.OK, client.put("/api/v1/users/${restricted.id}/permissions/$entityId/CREATE") {
                        headers.append("X-RED-CSRF", "1"); headers.append(HttpHeaders.Cookie, adminCookie)
                    }.status)
                    assertEquals(HttpStatusCode.Created, client.post("/api/v1/entities/$entityId/records") {
                        contentType(ContentType.Application.Json); headers.append("X-RED-CSRF", "1"); headers.append(HttpHeaders.Cookie, userCookie)
                        setBody("""{"values":{"email":"second@example.com"}}""")
                    }.status)
                }
            } finally {
                if (created) database.transaction { connection -> connection.createStatement().use { it.execute("DROP SCHEMA $schema CASCADE") } }
            }
        }
    }

    @Test fun `setup login create application and logout against PostgreSQL`() {
        val url = System.getenv("TEST_DATABASE_URL").orEmpty()
        assumeTrue(url.isNotBlank(), "TEST_DATABASE_URL is not configured")
        val schema = "red_test_${UUID.randomUUID().toString().replace("-", "")}" 
        PostgresDatabase(PostgresConnectionUrl.parse(url, maximumPoolSize = 2)).use { database ->
            var created = false
            try {
                database.transaction { connection -> connection.createStatement().use { it.execute("CREATE SCHEMA $schema") } }
                created = true
                FrameworkMigrator(database.dataSource, schema).migrate()
                val identity = PostgresIdentityRepository(database, schema)
                val applications = PostgresApplicationRegistry(database, schema)
                testApplication {
                    application { serverModule(database::isHealthy, applications, identity, PostgresSiteRegistry(database, schema),
                        { site -> PostgresMetadataRepository(database, site, schema) },
                        { site -> PostgresSchemaPublisher(database, site, schema) },
                        { site -> PostgresRecordRepository(database, site, schema, PostgresMetadataRepository(database, site, schema), PostgresSchemaPublisher(database, site, schema)) }) }
                    assertEquals("{\"initialized\":false}", client.get("/api/v1/setup/status").bodyAsText())
                    assertEquals(HttpStatusCode.Unauthorized, client.get("/api/v1/applications").status)
                    val setup = client.post("/api/v1/setup/initialize") {
                        contentType(ContentType.Application.Json); headers.append("X-RED-CSRF", "1")
                        setBody("{\"fullName\":\"Test Admin\",\"email\":\"admin@example.com\",\"password\":\"Strong#Pass123\",\"confirmPassword\":\"Strong#Pass123\",\"siteName\":\"first-site\"}")
                    }
                    assertEquals(HttpStatusCode.Created, setup.status)
                    assertEquals("{\"initialized\":true}", client.get("/api/v1/setup/status").bodyAsText())
                    val login = client.post("/api/v1/auth/login") {
                        contentType(ContentType.Application.Json); headers.append("X-RED-CSRF", "1")
                        setBody("{\"email\":\"admin@example.com\",\"password\":\"Strong#Pass123\"}")
                    }
                    assertEquals(HttpStatusCode.OK, login.status)
                    val cookie = login.headers[HttpHeaders.SetCookie]!!.substringBefore(';')
                    assertTrue(login.bodyAsText().contains("admin@example.com"))
                    val createdApp = client.post("/api/v1/applications") {
                        contentType(ContentType.Application.Json); headers.append("X-RED-CSRF", "1")
                        headers.append(HttpHeaders.Cookie, cookie)
                        setBody("{\"canonicalName\":\"demo-app\",\"version\":\"1.0.0\"}")
                    }
                    assertEquals(HttpStatusCode.Created, createdApp.status)
                    val list = client.get("/api/v1/applications") { headers.append(HttpHeaders.Cookie, cookie) }
                    assertTrue(list.bodyAsText().contains("demo-app"))
                    val sites = client.get("/api/v1/sites") { headers.append(HttpHeaders.Cookie, cookie) }
                    assertTrue(sites.bodyAsText().contains("first-site"))
                    val types = client.get("/api/v1/attribute-types") { headers.append(HttpHeaders.Cookie, cookie) }
                    assertTrue(types.bodyAsText().contains("TEXT"))
                    val appId = Regex("\"id\":\"([^\"]+)\"").find(createdApp.bodyAsText())!!.groupValues[1]
                    val createdEntity = client.post("/api/v1/entities") {
                        contentType(ContentType.Application.Json); headers.append("X-RED-CSRF", "1")
                        headers.append(HttpHeaders.Cookie, cookie)
                        setBody("""{"applicationId":"$appId","name":"customer","label":"Customer","attributes":[{"name":"email","label":"Email","typeId":"EMAIL","required":true}],"formTabs":[{"name":"details","label":"Details","fields":["email"]}],"namingSeries":{"prefix":"CUS-","digits":4},"titleAttribute":"email","listAttributes":["email"]}""")
                    }
                    assertEquals(HttpStatusCode.Created, createdEntity.status)
                    assertTrue(createdEntity.bodyAsText().contains("\"formTabs\""))
                    assertTrue(createdEntity.bodyAsText().contains("\"CUS-\""))
                    val entities = client.get("/api/v1/entities") { headers.append(HttpHeaders.Cookie, cookie) }
                    assertTrue(entities.bodyAsText().contains("customer"))
                    val entityId = Regex("\"id\":\"([^\"]+)\"").find(createdEntity.bodyAsText())!!.groupValues[1]
                    val attributeId = Regex("\"attributes\":\\[\\{\"id\":\"([^\"]+)\"").find(createdEntity.bodyAsText())!!.groupValues[1]
                    val updateBody = """{"expectedRevision":1,"label":"Customer account","attributes":[{"id":"$attributeId","name":"email","label":"Contact email","typeId":"EMAIL","required":true}],"formTabs":[{"name":"details","label":"Details","fields":["email"]}],"namingSeries":{"prefix":"CUS-","digits":4},"titleAttribute":"email","listAttributes":["email"]}"""
                    val revised = client.put("/api/v1/entities/$entityId") {
                        contentType(ContentType.Application.Json); headers.append("X-RED-CSRF", "1")
                        headers.append(HttpHeaders.Cookie, cookie); setBody(updateBody)
                    }
                    assertEquals(HttpStatusCode.OK, revised.status)
                    assertTrue(revised.bodyAsText().contains("\"revision\":2"))
                    assertEquals(HttpStatusCode.Conflict, client.put("/api/v1/entities/$entityId") {
                        contentType(ContentType.Application.Json); headers.append("X-RED-CSRF", "1")
                        headers.append(HttpHeaders.Cookie, cookie); setBody(updateBody)
                    }.status)
                    val history = client.get("/api/v1/entities/$entityId/revisions") { headers.append(HttpHeaders.Cookie, cookie) }
                    assertTrue(history.bodyAsText().contains("\"revision\":1"))
                    assertTrue(history.bodyAsText().contains("\"revision\":2"))
                    val publication = client.post("/api/v1/entities/$entityId/publish") {
                        headers.append("X-RED-CSRF", "1"); headers.append(HttpHeaders.Cookie, cookie)
                    }
                    assertEquals(HttpStatusCode.OK, publication.status)
                    assertTrue(publication.bodyAsText().contains("\"publishedRevision\":2"))
                    val physicalTable = PostgresSchemaPublisher.physicalTableName(red.framework.kernel.identity.EntityId.parse(entityId))
                    database.transaction { connection ->
                        connection.prepareStatement("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = ? AND table_name = ?").use {
                            it.setString(1, schema); it.setString(2, physicalTable)
                            it.executeQuery().use { rows -> rows.next(); assertEquals(1, rows.getInt(1)) }
                        }
                    }
                    val createdRecord = client.post("/api/v1/entities/$entityId/records") {
                        contentType(ContentType.Application.Json); headers.append("X-RED-CSRF", "1")
                        headers.append(HttpHeaders.Cookie, cookie); setBody("{\"values\":{\"email\":\"customer@example.com\"}}")
                    }
                    assertEquals(HttpStatusCode.Created, createdRecord.status)
                    assertTrue(createdRecord.bodyAsText().contains("customer@example.com"))
                    assertTrue(createdRecord.bodyAsText().contains("\"name\":\"CUS-0001\""))
                    assertTrue(createdRecord.bodyAsText().contains("\"version\":1"))
                    val recordId = Regex("\"id\":\"([^\"]+)\"").find(createdRecord.bodyAsText())!!.groupValues[1]
                    val recordUpdate = "{\"expectedVersion\":1,\"values\":{\"email\":\"changed@example.com\"}}"
                    val revisedRecord = client.put("/api/v1/entities/$entityId/records/$recordId") {
                        contentType(ContentType.Application.Json); headers.append("X-RED-CSRF", "1")
                        headers.append(HttpHeaders.Cookie, cookie); setBody(recordUpdate)
                    }
                    assertEquals(HttpStatusCode.OK, revisedRecord.status)
                    assertTrue(revisedRecord.bodyAsText().contains("\"version\":2"))
                    assertEquals(HttpStatusCode.Conflict, client.put("/api/v1/entities/$entityId/records/$recordId") {
                        contentType(ContentType.Application.Json); headers.append("X-RED-CSRF", "1")
                        headers.append(HttpHeaders.Cookie, cookie); setBody(recordUpdate)
                    }.status)
                    val audit = client.get("/api/v1/entities/$entityId/records/$recordId/audit") { headers.append(HttpHeaders.Cookie, cookie) }
                    assertEquals(HttpStatusCode.OK, audit.status)
                    assertTrue(audit.bodyAsText().contains("\"action\":\"CREATE\""))
                    assertTrue(audit.bodyAsText().contains("\"action\":\"UPDATE\""))
                    assertTrue(audit.bodyAsText().contains("\"recordVersion\":2"))
                    assertTrue(!audit.bodyAsText().contains("changed@example.com"))
                    val siteContext = red.framework.kernel.context.SiteContext(PostgresSiteRegistry(database, schema).findByCanonicalName("first-site")!!.id)
                    val recordRepository = PostgresRecordRepository(database, siteContext, schema,
                        PostgresMetadataRepository(database, siteContext, schema), PostgresSchemaPublisher(database, siteContext, schema))
                    assertFailsWith<SQLException> {
                        recordRepository.create(siteContext, red.framework.kernel.identity.EntityId.parse(entityId), UUID.randomUUID(),
                            mapOf("email" to red.framework.records.RecordValue.Text("rollback@example.com")))
                    }
                    assertEquals(1, recordRepository.list(siteContext, red.framework.kernel.identity.EntityId.parse(entityId)).size)
                    val desk = client.get("/api/v1/desk/entities") { headers.append(HttpHeaders.Cookie, cookie) }
                    assertEquals(HttpStatusCode.OK, desk.status)
                    assertTrue(desk.bodyAsText().contains("\"label\":\"Details\""))
                    val listedRecords = client.get("/api/v1/entities/$entityId/records") { headers.append(HttpHeaders.Cookie, cookie) }
                    assertTrue(listedRecords.bodyAsText().contains("changed@example.com"))
                    assertEquals(HttpStatusCode.BadRequest, client.post("/api/v1/entities/$entityId/records") {
                        contentType(ContentType.Application.Json); headers.append("X-RED-CSRF", "1")
                        headers.append(HttpHeaders.Cookie, cookie); setBody("{\"values\":{\"email\":\"invalid-email\"}}")
                    }.status)
                    val nextBody = updateBody.replace("\"expectedRevision\":1", "\"expectedRevision\":2")
                    assertEquals(HttpStatusCode.OK, client.put("/api/v1/entities/$entityId") {
                        contentType(ContentType.Application.Json); headers.append("X-RED-CSRF", "1")
                        headers.append(HttpHeaders.Cookie, cookie); setBody(nextBody)
                    }.status)
                    assertEquals(HttpStatusCode.OK, client.post("/api/v1/entities/$entityId/publish") {
                        headers.append("X-RED-CSRF", "1"); headers.append(HttpHeaders.Cookie, cookie)
                    }.status)
                    val additiveBody = "{\"expectedRevision\":3,\"label\":\"Customer account\",\"attributes\":[{\"id\":\"$attributeId\",\"name\":\"email\",\"label\":\"Contact email\",\"typeId\":\"EMAIL\",\"required\":true},{\"name\":\"notes\",\"label\":\"Notes\",\"typeId\":\"TEXT\",\"required\":false}]}"
                    assertEquals(HttpStatusCode.OK, client.put("/api/v1/entities/$entityId") {
                        contentType(ContentType.Application.Json); headers.append("X-RED-CSRF", "1")
                        headers.append(HttpHeaders.Cookie, cookie); setBody(additiveBody)
                    }.status)
                    assertEquals(HttpStatusCode.OK, client.post("/api/v1/entities/$entityId/publish") {
                        headers.append("X-RED-CSRF", "1"); headers.append(HttpHeaders.Cookie, cookie)
                    }.status)
                    val migratedRecords = client.get("/api/v1/entities/$entityId/records") { headers.append(HttpHeaders.Cookie, cookie) }
                    assertTrue(migratedRecords.bodyAsText().contains("\"notes\":null"))
                    database.transaction { connection ->
                        connection.prepareStatement("SELECT operation FROM $schema.red_schema_migration WHERE entity_id = ? ORDER BY to_revision").use {
                            it.setObject(1, UUID.fromString(entityId))
                            it.executeQuery().use { rows ->
                                val operations = buildList { while (rows.next()) add(rows.getString(1)) }
                                assertEquals(listOf("CREATE", "NO_DDL", "ADD_COLUMNS"), operations)
                            }
                        }
                    }
                    val incompatibleBody = "{\"expectedRevision\":4,\"label\":\"Customer account\",\"attributes\":[{\"id\":\"$attributeId\",\"name\":\"email\",\"label\":\"Contact email\",\"typeId\":\"TEXT\",\"required\":true}]}"
                    assertEquals(HttpStatusCode.OK, client.put("/api/v1/entities/$entityId") {
                        contentType(ContentType.Application.Json); headers.append("X-RED-CSRF", "1")
                        headers.append(HttpHeaders.Cookie, cookie); setBody(incompatibleBody)
                    }.status)
                    assertEquals(HttpStatusCode.Conflict, client.post("/api/v1/entities/$entityId/publish") {
                        headers.append("X-RED-CSRF", "1"); headers.append(HttpHeaders.Cookie, cookie)
                    }.status)
                    val typedEntity = client.post("/api/v1/entities") {
                        contentType(ContentType.Application.Json); headers.append("X-RED-CSRF", "1")
                        headers.append(HttpHeaders.Cookie, cookie)
                        setBody("{\"applicationId\":\"$appId\",\"name\":\"typed_entry\",\"label\":\"Typed Entry\",\"attributes\":[{\"name\":\"quantity\",\"label\":\"Quantity\",\"typeId\":\"INTEGER\"},{\"name\":\"amount\",\"label\":\"Amount\",\"typeId\":\"DECIMAL\"},{\"name\":\"enabled\",\"label\":\"Enabled\",\"typeId\":\"BOOLEAN\"},{\"name\":\"day\",\"label\":\"Day\",\"typeId\":\"DATE\"},{\"name\":\"occurred_at\",\"label\":\"Occurred\",\"typeId\":\"DATETIME\"},{\"name\":\"note\",\"label\":\"Note\",\"typeId\":\"TEXT\"}]}")
                    }
                    assertEquals(HttpStatusCode.Created, typedEntity.status)
                    val typedId = Regex("\"id\":\"([^\"]+)\"").find(typedEntity.bodyAsText())!!.groupValues[1]
                    assertEquals(HttpStatusCode.OK, client.post("/api/v1/entities/$typedId/publish") {
                        headers.append("X-RED-CSRF", "1"); headers.append(HttpHeaders.Cookie, cookie)
                    }.status)
                    val typedRecord = client.post("/api/v1/entities/$typedId/records") {
                        contentType(ContentType.Application.Json); headers.append("X-RED-CSRF", "1")
                        headers.append(HttpHeaders.Cookie, cookie)
                        setBody("{\"values\":{\"quantity\":7,\"amount\":12.50,\"enabled\":true,\"day\":\"2026-11-06\",\"occurred_at\":\"2026-11-06T15:30:00Z\",\"note\":\"stored\"}}")
                    }
                    assertEquals(HttpStatusCode.Created, typedRecord.status)
                    val typedList = client.get("/api/v1/entities/$typedId/records") { headers.append(HttpHeaders.Cookie, cookie) }
                    assertTrue(typedList.bodyAsText().contains("\"quantity\":7"))
                    assertTrue(typedList.bodyAsText().contains("\"enabled\":true"))
                    assertTrue(typedList.bodyAsText().contains("2026-11-06T15:30:00Z"))
                    assertEquals(HttpStatusCode.OK, client.post("/api/v1/auth/logout") {
                        headers.append("X-RED-CSRF", "1"); headers.append(HttpHeaders.Cookie, cookie)
                    }.status)
                    assertEquals(HttpStatusCode.Unauthorized, client.get("/api/v1/applications") {
                        headers.append(HttpHeaders.Cookie, cookie)
                    }.status)
                }
            } finally {
                if (created) database.transaction { connection -> connection.createStatement().use { it.execute("DROP SCHEMA $schema CASCADE") } }
            }
        }
    }
}
