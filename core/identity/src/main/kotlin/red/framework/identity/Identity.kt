package red.framework.identity

import java.util.UUID

data class Administrator(val id: UUID, val fullName: String, val email: String)
data class ManagedUser(val id: UUID, val fullName: String, val email: String, val active: Boolean)
enum class EntityAction { READ, CREATE, UPDATE }
data class EntityGrant(val userId: UUID, val entityId: UUID, val action: EntityAction)

data class InitialSetup(
    val fullName: String,
    val email: String,
    val password: String,
    val confirmPassword: String,
    val siteName: String,
) {
    init {
        require(fullName.trim().length in 2..120) { "Full name must have 2–120 characters" }
        require(EMAIL.matches(email)) { "Invalid email address" }
        require(password.length >= 12 && password.any(Char::isUpperCase) && password.any(Char::isLowerCase) &&
            password.any(Char::isDigit) && password.any { !it.isLetterOrDigit() }) { "Password must be at least 12 characters with upper, lower, number, and symbol" }
        require(password == confirmPassword) { "Passwords do not match" }
        require(SITE_NAME.matches(siteName)) { "Invalid site name" }
    }

    companion object {
        private val EMAIL = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
        private val SITE_NAME = Regex("[a-z][a-z0-9-]{1,61}[a-z0-9]")
    }
}

interface IdentityRepository {
    fun isInitialized(): Boolean
    fun initialize(setup: InitialSetup): Administrator
    fun login(email: String, password: String): String?
    fun findSession(token: String): Administrator?
    fun logout(token: String)
    fun hasRole(userId: UUID, role: String): Boolean = false
    fun listUsers(): List<ManagedUser> = emptyList()
    fun createUser(fullName: String, email: String, password: String): ManagedUser =
        throw UnsupportedOperationException("User management unavailable")
    fun hasEntityPermission(userId: UUID, entityId: UUID, action: EntityAction): Boolean = false
    fun grantEntityPermission(grant: EntityGrant) { throw UnsupportedOperationException("Permissions unavailable") }
    fun revokeEntityPermission(grant: EntityGrant) { throw UnsupportedOperationException("Permissions unavailable") }
    fun listEntityGrants(userId: UUID): List<EntityGrant> = emptyList()
}
