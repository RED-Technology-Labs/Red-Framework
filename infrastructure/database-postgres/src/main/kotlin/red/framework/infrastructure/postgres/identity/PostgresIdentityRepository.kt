package red.framework.infrastructure.postgres.identity

import red.framework.identity.Administrator
import red.framework.identity.IdentityRepository
import red.framework.identity.InitialSetup
import red.framework.identity.ManagedUser
import red.framework.identity.EntityAction
import red.framework.identity.EntityGrant
import red.framework.infrastructure.postgres.PostgresDatabase
import java.security.MessageDigest
import java.security.SecureRandom
import java.sql.Connection
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.Base64
import java.util.Locale
import java.util.UUID

/** Database-backed initial administrator and revocable opaque sessions. */
class PostgresIdentityRepository(private val database: PostgresDatabase, schema: String) : IdentityRepository {
    private val namespace: String
    private val lockName: String
    private val random = SecureRandom()

    init {
        require(Regex("[a-z][a-z0-9_]{0,62}").matches(schema)) { "Invalid framework schema name" }
        namespace = "\"$schema\""
        lockName = "red_setup:$schema"
    }

    override fun isInitialized(): Boolean = database.transaction { connection -> initialized(connection) }

    override fun initialize(setup: InitialSetup): Administrator {
        val passwordHash = PasswordHasher.hash(setup.password)
        return database.transaction { connection ->
            connection.prepareStatement("SELECT pg_advisory_xact_lock(hashtext(?))").use {
                it.setString(1, lockName)
                it.executeQuery().close()
            }
            check(!initialized(connection)) { "Setup already completed" }
            val siteId = UUID.randomUUID()
            val userId = UUID.randomUUID()
            val roleId = UUID.randomUUID()
            val email = setup.email.lowercase(Locale.ROOT)
            connection.prepareStatement("INSERT INTO $namespace.red_site (id, canonical_name, status) VALUES (?, ?, 'ACTIVE')").use {
                it.setObject(1, siteId); it.setString(2, setup.siteName); it.executeUpdate()
            }
            connection.prepareStatement("INSERT INTO $namespace.red_user (id, full_name, email, password_hash) VALUES (?, ?, ?, ?)").use {
                it.setObject(1, userId); it.setString(2, setup.fullName.trim()); it.setString(3, email)
                it.setString(4, passwordHash); it.executeUpdate()
            }
            connection.prepareStatement("INSERT INTO $namespace.red_role (id, canonical_name) VALUES (?, 'system-administrator')").use {
                it.setObject(1, roleId); it.executeUpdate()
            }
            connection.prepareStatement("INSERT INTO $namespace.red_user_role (user_id, role_id) VALUES (?, ?)").use {
                it.setObject(1, userId); it.setObject(2, roleId); it.executeUpdate()
            }
            connection.prepareStatement("INSERT INTO $namespace.red_setup_state (site_id, administrator_id) VALUES (?, ?)").use {
                it.setObject(1, siteId); it.setObject(2, userId); it.executeUpdate()
            }
            Administrator(userId, setup.fullName.trim(), email)
        }
    }

    override fun login(email: String, password: String): String? {
        val row = database.transaction { connection ->
            connection.prepareStatement("SELECT id, password_hash FROM $namespace.red_user WHERE email = ? AND active = TRUE").use {
                it.setString(1, email.lowercase(Locale.ROOT))
                it.executeQuery().use { rows -> if (rows.next()) rows.getObject(1, UUID::class.java) to rows.getString(2) else null }
            }
        } ?: return null
        if (!PasswordHasher.verify(password, row.second)) return null
        val token = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also(random::nextBytes))
        database.transaction { connection ->
            connection.prepareStatement("INSERT INTO $namespace.red_session (token_hash, user_id, expires_at) VALUES (?, ?, ?)").use {
                it.setString(1, digest(token)); it.setObject(2, row.first)
                it.setObject(3, OffsetDateTime.now(ZoneOffset.UTC).plusHours(12)); it.executeUpdate()
            }
        }
        return token
    }

    override fun findSession(token: String): Administrator? {
        if (token.length !in 40..100) return null
        return database.transaction { connection ->
            connection.prepareStatement("SELECT u.id, u.full_name, u.email FROM $namespace.red_session s JOIN $namespace.red_user u ON u.id = s.user_id WHERE s.token_hash = ? AND s.expires_at > CURRENT_TIMESTAMP AND u.active = TRUE").use {
                it.setString(1, digest(token))
                it.executeQuery().use { rows -> if (rows.next()) Administrator(rows.getObject(1, UUID::class.java), rows.getString(2), rows.getString(3)) else null }
            }
        }
    }

    override fun logout(token: String) {
        database.transaction { connection ->
            connection.prepareStatement("DELETE FROM $namespace.red_session WHERE token_hash = ?").use {
                it.setString(1, digest(token)); it.executeUpdate()
            }
        }
    }

    override fun hasRole(userId: UUID, role: String): Boolean = database.transaction { connection ->
        connection.prepareStatement("SELECT EXISTS(SELECT 1 FROM $namespace.red_user_role ur JOIN $namespace.red_role r ON r.id = ur.role_id JOIN $namespace.red_user u ON u.id = ur.user_id WHERE ur.user_id = ? AND r.canonical_name = ? AND u.active = TRUE)").use {
            it.setObject(1, userId)
            it.setString(2, role)
            it.executeQuery().use { rows -> rows.next(); rows.getBoolean(1) }
        }
    }

    override fun listUsers(): List<ManagedUser> = database.transaction { connection ->
        connection.prepareStatement("SELECT id, full_name, email, active FROM $namespace.red_user ORDER BY email").use {
            it.executeQuery().use { rows -> buildList {
                while (rows.next()) add(ManagedUser(rows.getObject(1, UUID::class.java), rows.getString(2), rows.getString(3), rows.getBoolean(4)))
            } }
        }
    }

    override fun createUser(fullName: String, email: String, password: String): ManagedUser {
        require(fullName.trim().length in 2..120)
        require(Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$").matches(email))
        require(password.length >= 12 && password.any(Char::isUpperCase) && password.any(Char::isLowerCase) &&
            password.any(Char::isDigit) && password.any { !it.isLetterOrDigit() })
        val user = ManagedUser(UUID.randomUUID(), fullName.trim(), email.lowercase(Locale.ROOT), true)
        val hash = PasswordHasher.hash(password)
        database.transaction { connection ->
            connection.prepareStatement("INSERT INTO $namespace.red_user (id, full_name, email, password_hash) VALUES (?, ?, ?, ?)").use {
                it.setObject(1, user.id); it.setString(2, user.fullName); it.setString(3, user.email); it.setString(4, hash)
                it.executeUpdate()
            }
        }
        return user
    }

    override fun hasEntityPermission(userId: UUID, entityId: UUID, action: EntityAction): Boolean = database.transaction { connection ->
        connection.prepareStatement("SELECT EXISTS(SELECT 1 FROM $namespace.red_entity_permission p JOIN $namespace.red_user u ON u.id = p.user_id WHERE p.user_id = ? AND p.entity_id = ? AND p.action = ? AND u.active = TRUE)").use {
            it.setObject(1, userId); it.setObject(2, entityId); it.setString(3, action.name)
            it.executeQuery().use { rows -> rows.next(); rows.getBoolean(1) }
        }
    }

    override fun grantEntityPermission(grant: EntityGrant) {
        database.transaction { connection ->
            connection.prepareStatement("INSERT INTO $namespace.red_entity_permission (user_id, entity_id, action) VALUES (?, ?, ?) ON CONFLICT DO NOTHING").use {
                it.setObject(1, grant.userId); it.setObject(2, grant.entityId); it.setString(3, grant.action.name)
                it.executeUpdate()
            }
        }
    }

    override fun revokeEntityPermission(grant: EntityGrant) {
        database.transaction { connection ->
            connection.prepareStatement("DELETE FROM $namespace.red_entity_permission WHERE user_id = ? AND entity_id = ? AND action = ?").use {
                it.setObject(1, grant.userId); it.setObject(2, grant.entityId); it.setString(3, grant.action.name)
                it.executeUpdate()
            }
        }
    }

    override fun listEntityGrants(userId: UUID): List<EntityGrant> = database.transaction { connection ->
        connection.prepareStatement("SELECT entity_id, action FROM $namespace.red_entity_permission WHERE user_id = ? ORDER BY entity_id, action").use {
            it.setObject(1, userId)
            it.executeQuery().use { rows -> buildList {
                while (rows.next()) add(EntityGrant(userId, rows.getObject(1, UUID::class.java), EntityAction.valueOf(rows.getString(2))))
            } }
        }
    }

    private fun initialized(connection: Connection): Boolean = connection.prepareStatement("SELECT EXISTS(SELECT 1 FROM $namespace.red_setup_state)").use {
        it.executeQuery().use { rows -> rows.next(); rows.getBoolean(1) }
    }

    private fun digest(token: String): String = MessageDigest.getInstance("SHA-256")
        .digest(token.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
