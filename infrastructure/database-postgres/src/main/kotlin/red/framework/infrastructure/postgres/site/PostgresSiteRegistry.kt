package red.framework.infrastructure.postgres.site

import red.framework.infrastructure.postgres.PostgresDatabase
import red.framework.kernel.identity.SiteId
import red.framework.site.Site
import red.framework.site.SiteRegistry
import red.framework.site.SiteStatus
import java.sql.ResultSet
import java.util.UUID

/** Read-only discovery of Sites created through the setup transaction. */
class PostgresSiteRegistry(private val database: PostgresDatabase, schema: String) : SiteRegistry {
    private val table: String

    init {
        require(Regex("[a-z][a-z0-9_]{0,62}").matches(schema)) { "Invalid framework schema name" }
        table = "\"$schema\".red_site"
    }

    override fun findById(id: SiteId): Site? = database.transaction { connection ->
        connection.prepareStatement("SELECT id, canonical_name, status FROM $table WHERE id = ?").use {
            it.setObject(1, id.value)
            it.executeQuery().use { rows -> if (rows.next()) read(rows) else null }
        }
    }

    override fun findByCanonicalName(name: String): Site? = database.transaction { connection ->
        connection.prepareStatement("SELECT id, canonical_name, status FROM $table WHERE canonical_name = ?").use {
            it.setString(1, name)
            it.executeQuery().use { rows -> if (rows.next()) read(rows) else null }
        }
    }

    override fun list(): List<Site> = database.transaction { connection ->
        connection.prepareStatement("SELECT id, canonical_name, status FROM $table ORDER BY canonical_name").use {
            it.executeQuery().use { rows -> buildList { while (rows.next()) add(read(rows)) } }
        }
    }

    private fun read(rows: ResultSet): Site = Site(
        SiteId.parse(rows.getObject("id", UUID::class.java).toString()),
        rows.getString("canonical_name"),
        SiteStatus.valueOf(rows.getString("status")),
    )
}
