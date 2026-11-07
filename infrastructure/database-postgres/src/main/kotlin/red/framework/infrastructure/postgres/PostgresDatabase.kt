package red.framework.infrastructure.postgres

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.sql.Connection
import javax.sql.DataSource

/** Owns a site-scoped PostgreSQL connection pool and closes it during shutdown. */
class PostgresDatabase(config: PostgresConfig) : AutoCloseable {
    private val pool: HikariDataSource = HikariDataSource(HikariConfig().apply {
        jdbcUrl = config.jdbcUrl
        username = config.username
        password = config.password
        maximumPoolSize = config.maximumPoolSize
        minimumIdle = 0
        isAutoCommit = false
        addDataSourceProperty("tcpKeepAlive", "true")
    })

    val dataSource: DataSource get() = pool

    /** Runs one unit of work atomically; a failure rolls back and is rethrown. */
    fun <T> transaction(block: (Connection) -> T): T = pool.connection.use { connection ->
        try {
            val result = block(connection)
            connection.commit()
            result
        } catch (failure: Throwable) {
            try {
                connection.rollback()
            } catch (rollbackFailure: Throwable) {
                failure.addSuppressed(rollbackFailure)
            }
            throw failure
        }
    }

    fun isHealthy(): Boolean = runCatching {
        pool.connection.use { it.isValid(2) }
    }.getOrDefault(false)

    override fun close() = pool.close()
}
