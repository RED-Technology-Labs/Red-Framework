# PostgreSQL infrastructure dependency review

Status: selected for the first connection and transaction slice; reviewed 2026-11-06.

| Dependency | Selected version | Role | Source and reason |
| --- | --- | --- | --- |
| HikariCP | 7.1.0 | JDBC pooling | [Upstream README](https://github.com/brettwooldridge/HikariCP/blob/dev/README.md) lists this Java 11+ version; JDK 25 meets it. Apache-2.0 per [POM](https://github.com/brettwooldridge/HikariCP/blob/dev/pom.xml). |
| pgJDBC | 42.7.14 | PostgreSQL JDBC driver | [Official download page](https://jdbc.postgresql.org/download/) lists current version; [license](https://github.com/pgjdbc/pgjdbc/blob/master/LICENSE) permits redistribution. Runtime-only so core has no driver API dependency. |
| Testcontainers PostgreSQL/JUnit | 2.0.5 | Real PostgreSQL integration test | [Official PostgreSQL module docs](https://java.testcontainers.org/modules/databases/postgres/) list 2.0.5. Requires [Docker API-compatible runtime](https://java.testcontainers.org/supported_docker_environment/). |

Flyway Open Source 13.12.1 is now selected in the separate migration module; see `FLYWAY_DEPENDENCIES.md`. Exposed is also deferred until a fixed-table persistence use case exists. The Testcontainers test is skipped locally because no Docker runtime is installed. The external Neon test database now verifies the transaction slice; CI should still exercise Testcontainers on a Docker-capable runner.
