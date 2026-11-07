package red.framework.server

import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.HttpHeaders
import io.ktor.server.testing.testApplication
import red.framework.application.ApplicationManifest
import red.framework.application.ApplicationRegistry
import red.framework.kernel.identity.ApplicationId
import red.framework.identity.Administrator
import red.framework.identity.IdentityRepository
import red.framework.identity.InitialSetup
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ServerTest {
    private val administrator = Administrator(UUID.randomUUID(), "Test Administrator", "admin@example.com")
    private val identity = object : IdentityRepository {
        var initialized = false
        override fun isInitialized() = initialized
        override fun initialize(setup: InitialSetup): Administrator { initialized = true; return administrator }
        override fun login(email: String, password: String) = if (initialized && email == "admin@example.com" && password == "Strong#Pass123") "test-session-token-abcdefghijklmnopqrstuvwxyz" else null
        override fun findSession(token: String) = if (token == "test-session-token-abcdefghijklmnopqrstuvwxyz") administrator else null
        override fun logout(token: String) = Unit
        override fun hasRole(userId: UUID, role: String) = userId == administrator.id && role == "system-administrator"
    }
    @Test fun `authenticated user without administrator role cannot reach builder or user APIs`() = testApplication {
        val restricted = object : IdentityRepository {
            override fun isInitialized() = true
            override fun initialize(setup: InitialSetup) = administrator
            override fun login(email: String, password: String) = "restricted-token-abcdefghijklmnopqrstuvwxyz123"
            override fun findSession(token: String) = administrator
            override fun logout(token: String) = Unit
        }
        application { serverModule({ true }, identity = restricted) }
        val cookie = "red_session=restricted-token-abcdefghijklmnopqrstuvwxyz123"
        assertEquals(HttpStatusCode.Forbidden, client.get("/api/v1/users") { headers.append(HttpHeaders.Cookie, cookie) }.status)
        assertEquals(HttpStatusCode.Forbidden, client.post("/api/v1/users") {
            contentType(ContentType.Application.Json)
            headers.append("X-RED-CSRF", "1")
            headers.append(HttpHeaders.Cookie, cookie)
            setBody("{\"fullName\":\"Other User\",\"email\":\"other@example.com\",\"password\":\"Strong#Pass123\"}")
        }.status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/v1/applications") { headers.append(HttpHeaders.Cookie, cookie) }.status)
    }
    @Test fun `health reports ready database`() = testApplication {
        application { serverModule({ true }) }
        val response = client.get("/api/v1/health")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("{\"status\":\"ok\"}", response.bodyAsText())
    }

    @Test fun `health reports unavailable database`() = testApplication {
        application { serverModule({ false }) }
        assertEquals(HttpStatusCode.ServiceUnavailable, client.get("/api/v1/health").status)
    }

    @Test fun `application API creates and lists through registry`() = testApplication {
        val stored = mutableListOf<ApplicationManifest>()
        val registry = object : ApplicationRegistry {
            override fun register(manifest: ApplicationManifest) { stored.add(manifest) }
            override fun findById(id: ApplicationId) = stored.find { it.id == id }
            override fun list() = stored.toList()
        }
        application { serverModule({ true }, registry, identity) }
        val created = client.post("/api/v1/applications") {
            contentType(ContentType.Application.Json)
            headers.append("X-RED-CSRF", "1")
            headers.append(HttpHeaders.Cookie, "red_session=test-session-token-abcdefghijklmnopqrstuvwxyz")
            setBody("{\"canonicalName\":\"demo-app\",\"version\":\"1.0.0\"}")
        }
        assertEquals(HttpStatusCode.Created, created.status)
        assertTrue(created.bodyAsText().contains("demo-app"))
        assertTrue(client.get("/api/v1/applications") { headers.append(HttpHeaders.Cookie, "red_session=test-session-token-abcdefghijklmnopqrstuvwxyz") }.bodyAsText().contains("demo-app"))
        assertEquals(HttpStatusCode.BadRequest, client.post("/api/v1/applications") {
            contentType(ContentType.Application.Json)
            headers.append("X-RED-CSRF", "1")
            headers.append(HttpHeaders.Cookie, "red_session=test-session-token-abcdefghijklmnopqrstuvwxyz")
            setBody("{\"canonicalName\":\"INVALID\",\"version\":\"1.0.0\"}")
        }.status)
    }

    @Test fun `setup and login require CSRF header and protect application API`() = testApplication {
        val state = object : IdentityRepository {
            var initialized = false
            override fun isInitialized() = initialized
            override fun initialize(setup: InitialSetup): Administrator { initialized = true; return administrator }
            override fun login(email: String, password: String) = if (initialized && email == "admin@example.com" && password == "Strong#Pass123") "session-token-abcdefghijklmnopqrstuvwxyz123456" else null
            override fun findSession(token: String) = if (token == "session-token-abcdefghijklmnopqrstuvwxyz123456") administrator else null
            override fun logout(token: String) = Unit
            override fun hasRole(userId: UUID, role: String) = userId == administrator.id && role == "system-administrator"
        }
        application { serverModule({ true }, identity = state) }
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/v1/applications").status)
        val body = "{\"fullName\":\"Test Administrator\",\"email\":\"admin@example.com\",\"password\":\"Strong#Pass123\",\"confirmPassword\":\"Strong#Pass123\",\"siteName\":\"first-site\"}"
        assertEquals(HttpStatusCode.Forbidden, client.post("/api/v1/setup/initialize") { contentType(ContentType.Application.Json); setBody(body) }.status)
        assertEquals(HttpStatusCode.Created, client.post("/api/v1/setup/initialize") {
            contentType(ContentType.Application.Json); headers.append("X-RED-CSRF", "1"); setBody(body)
        }.status)
        assertEquals(HttpStatusCode.Conflict, client.post("/api/v1/setup/initialize") {
            contentType(ContentType.Application.Json); headers.append("X-RED-CSRF", "1"); setBody(body)
        }.status)
        val login = client.post("/api/v1/auth/login") {
            contentType(ContentType.Application.Json); headers.append("X-RED-CSRF", "1")
            setBody("{\"email\":\"admin@example.com\",\"password\":\"Strong#Pass123\"}")
        }
        assertEquals(HttpStatusCode.OK, login.status)
        assertTrue(login.headers[HttpHeaders.SetCookie].orEmpty().contains("HttpOnly"))
    }
}
