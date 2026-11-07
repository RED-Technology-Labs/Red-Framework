package red.framework.infrastructure.postgres

/** Validated connection parameters. Secrets are supplied by the composition root and never logged. */
class PostgresConfig(
    val jdbcUrl: String,
    val username: String,
    val password: String,
    val maximumPoolSize: Int = 10,
) {
    init {
        require(jdbcUrl.startsWith("jdbc:postgresql://")) { "A PostgreSQL JDBC URL is required" }
        require(!jdbcUrl.contains('@') && !jdbcUrl.contains("password=", ignoreCase = true)) {
            "Credentials must be provided separately from the JDBC URL"
        }
        require(username.isNotBlank()) { "Database username is required" }
        require(maximumPoolSize in 1..100) { "Pool size must be between 1 and 100" }
    }

    override fun toString(): String = "PostgresConfig(jdbcUrl=[redacted], username=[redacted], password=[redacted], maximumPoolSize=$maximumPoolSize)"
}
