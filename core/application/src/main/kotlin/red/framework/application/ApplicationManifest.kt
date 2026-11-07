package red.framework.application

import red.framework.kernel.identity.ApplicationId
import java.util.Collections

/** Semantic version of an installable application. Pre-release and build metadata are deferred. */
data class ApplicationVersion(val major: Int, val minor: Int, val patch: Int) : Comparable<ApplicationVersion> {
    init {
        require(major >= 0 && minor >= 0 && patch >= 0) { "Version components must be non-negative" }
    }
    override fun compareTo(other: ApplicationVersion): Int =
        compareValuesBy(this, other, ApplicationVersion::major, ApplicationVersion::minor, ApplicationVersion::patch)

    override fun toString(): String = "$major.$minor.$patch"

    companion object {
        fun parse(raw: String): ApplicationVersion {
            require(Regex("(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)").matches(raw)) {
                "Expected major.minor.patch"
            }
            val parts = raw.split('.').map(String::toInt)
            return ApplicationVersion(parts[0], parts[1], parts[2])
        }
    }
}

/** A dependency on a stable application ID and minimum compatible version. */
data class ApplicationDependency(val applicationId: ApplicationId, val minimumVersion: ApplicationVersion)

/** Validated package identity and declared dependencies; installation is a later use case. */
class ApplicationManifest(
    val id: ApplicationId,
    val canonicalName: String,
    val version: ApplicationVersion,
    dependencies: Collection<ApplicationDependency>,
) {
    val dependencies: List<ApplicationDependency> = Collections.unmodifiableList(dependencies.toList())

    init {
        require(Regex("[a-z][a-z0-9-]{1,61}[a-z0-9]").matches(canonicalName)) {
            "Application name must be 3–63 lowercase ASCII letters, digits, or hyphens"
        }
        require(this.dependencies.none { it.applicationId == id }) { "Application cannot depend on itself" }
        require(this.dependencies.map { it.applicationId }.toSet().size == this.dependencies.size) {
            "Duplicate application dependency"
        }
    }
}

/** Durable application catalog contract. */
interface ApplicationRegistry {
    fun register(manifest: ApplicationManifest)
    fun findById(id: ApplicationId): ApplicationManifest?
    fun list(): List<ApplicationManifest>
}
