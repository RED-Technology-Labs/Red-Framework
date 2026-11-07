package red.framework.infrastructure.migrations

import org.flywaydb.core.Flyway
import javax.sql.DataSource

/** Applies only RED-owned system-table migrations to a server-selected PostgreSQL schema. */
class FrameworkMigrator(dataSource: DataSource, schema: String) {
    init {
        require(Regex("[a-z][a-z0-9_]{0,62}").matches(schema)) { "Invalid framework schema name" }
    }

    private val flyway: Flyway = Flyway.configure()
        .dataSource(dataSource)
        .schemas(schema)
        .defaultSchema(schema)
        .createSchemas(false)
        .locations("classpath:db/framework")
        .table("red_flyway_schema_history")
        .cleanDisabled(true)
        .baselineOnMigrate(false)
        .validateOnMigrate(true)
        .load()

    /** Executes pending system migrations; returns the number applied in this call. */
    fun migrate(): Int = flyway.migrate().migrationsExecuted

    /** Lists successful applied migration versions in order. */
    fun appliedVersions(): List<String> = flyway.info().applied()
        .filter { it.state.isApplied }
        .mapNotNull { it.version?.version }
}
