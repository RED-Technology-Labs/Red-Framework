package red.framework.infrastructure.postgres.application

import red.framework.application.ApplicationManifest
import red.framework.application.ApplicationRegistry
import red.framework.application.ApplicationVersion
import red.framework.infrastructure.postgres.PostgresDatabase
import red.framework.kernel.identity.ApplicationId

/** Catalog stored in the framework-owned schema. Dependency persistence is not supported yet. */
class PostgresApplicationRegistry(private val database: PostgresDatabase, schema: String) : ApplicationRegistry {
    private val table: String

    init {
        require(Regex("[a-z][a-z0-9_]{0,62}").matches(schema)) { "Invalid framework schema name" }
        table = "\"$schema\".red_application"
    }

    override fun register(manifest: ApplicationManifest) {
        require(manifest.dependencies.isEmpty()) { "Application dependencies cannot be persisted yet" }
        database.transaction { connection ->
            connection.prepareStatement(
                "INSERT INTO $table (id, canonical_name, version_major, version_minor, version_patch) VALUES (?, ?, ?, ?, ?)"
            ).use { statement ->
                statement.setObject(1, manifest.id.value)
                statement.setString(2, manifest.canonicalName)
                statement.setInt(3, manifest.version.major)
                statement.setInt(4, manifest.version.minor)
                statement.setInt(5, manifest.version.patch)
                statement.executeUpdate()
            }
        }
    }

    override fun findById(id: ApplicationId): ApplicationManifest? = database.transaction { connection ->
        connection.prepareStatement("SELECT id, canonical_name, version_major, version_minor, version_patch FROM $table WHERE id = ?").use { statement ->
            statement.setObject(1, id.value)
            statement.executeQuery().use { rows -> if (rows.next()) read(rows) else null }
        }
    }

    override fun list(): List<ApplicationManifest> = database.transaction { connection ->
        connection.prepareStatement("SELECT id, canonical_name, version_major, version_minor, version_patch FROM $table ORDER BY canonical_name").use { statement ->
            statement.executeQuery().use { rows -> buildList { while (rows.next()) add(read(rows)) } }
        }
    }

    private fun read(rows: java.sql.ResultSet): ApplicationManifest = ApplicationManifest(
        ApplicationId.parse(rows.getObject("id", java.util.UUID::class.java).toString()),
        rows.getString("canonical_name"),
        ApplicationVersion(rows.getInt("version_major"), rows.getInt("version_minor"), rows.getInt("version_patch")),
        emptyList(),
    )
}
