package red.framework.site

import red.framework.kernel.identity.SiteId

/** Registered site descriptor. Connection details are held only by infrastructure. */
data class Site(val id: SiteId, val canonicalName: String, val status: SiteStatus) {
    init {
        require(SITE_NAME.matches(canonicalName)) { "Site name must be 3–63 lowercase ASCII letters, digits, or hyphens" }
    }
    companion object {
        private val SITE_NAME = Regex("[a-z][a-z0-9-]{1,61}[a-z0-9]")
    }
}

enum class SiteStatus { ACTIVE, SUSPENDED }

/** Registry port for server-controlled site discovery. Implementations must not expose connection secrets. */
interface SiteRegistry {
    fun findById(id: SiteId): Site?
    fun findByCanonicalName(name: String): Site?
    fun list(): List<Site>
}
