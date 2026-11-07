package red.framework.infrastructure.postgres

import java.net.URI

/** Converts a Postgres URI supplied by the environment into a JDBC configuration. */
object PostgresConnectionUrl {
    fun parse(raw: String, maximumPoolSize: Int = 10): PostgresConfig {
        val uri = try {
            URI(raw)
        } catch (_: Exception) {
            throw IllegalArgumentException("Invalid PostgreSQL connection URL")
        }
        require(uri.scheme == "postgresql" || uri.scheme == "postgres") { "PostgreSQL URL is required" }
        require(uri.host != null && uri.port in -1..65535 && uri.port != 0) { "PostgreSQL host or port is invalid" }
        require(uri.rawPath.length > 1 && !uri.rawPath.drop(1).contains('/')) { "Database name is required" }
        require(uri.fragment == null) { "URL fragments are not supported" }

        val userInfo = uri.userInfo ?: throw IllegalArgumentException("Database credentials are required")
        val separator = userInfo.indexOf(':')
        require(separator > 0) { "Database username and password are required" }
        val username = userInfo.substring(0, separator)
        val password = userInfo.substring(separator + 1)

        val options = uri.rawQuery?.split('&')?.associate { part ->
            val equals = part.indexOf('=')
            require(equals > 0) { "Invalid PostgreSQL URL option" }
            part.substring(0, equals) to part.substring(equals + 1)
        }.orEmpty()
        require(options.keys.all { it == "sslmode" || it == "channel_binding" }) {
            "Unsupported PostgreSQL URL option"
        }
        val sslMode = options["sslmode"] ?: "verify-full"
        require(sslMode == "require" || sslMode == "verify-full") { "TLS is required" }
        val channelBinding = options["channel_binding"] ?: "require"
        require(channelBinding == "require") { "Channel binding is required" }

        val port = if (uri.port == -1) "" else ":${uri.port}"
        val jdbcUrl = "jdbc:postgresql://${uri.host}$port${uri.rawPath}?sslmode=$sslMode&channelBinding=$channelBinding"
        return PostgresConfig(jdbcUrl, username, password, maximumPoolSize)
    }
}
