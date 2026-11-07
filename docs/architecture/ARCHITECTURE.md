# Architecture

Status: partially implemented. `:core:kernel`, `:core:site`, `:core:application`, `:core:metadata`, `:infrastructure:database-postgres`, and `:infrastructure:database-migrations` are active Gradle modules. Site, application, and metadata depend on Kernel. PostgreSQL infrastructure implements metadata persistence and uses JDBC/HikariCP; the migration module uses a `DataSource` and Flyway Open Source. Database behavior has passed real tests on a dedicated Neon test database. Docker-based Testcontainers still skips locally.

Dependency direction: domain contracts at the center; application use cases depend on those contracts; delivery and infrastructure adapters depend on use cases and contracts. A runtime entry point composes the graph.

Proposed project edges:

```text
core:kernel <- core:metadata, core:attribute-types, core:site
core:metadata + core:attribute-types <- core:schema-engine, core:records
core contracts <- infrastructure:database-postgres
core use cases + infrastructure adapters <- apps:server, apps:worker
```

Here `<-` means the module on the right depends on the module on the left. `core:schema-engine` owns plans and ports; PostgreSQL SQL execution belongs in the adapter. `core:records` owns record use cases and repository ports. `core:metadata` owns definition rules and repository ports. No edge points from core to infrastructure, and the proposed graph is acyclic. The active graph has site, application, and metadata depending on kernel; PostgreSQL infrastructure depends on metadata.

Site-scoped requests must carry a trusted context resolved at the delivery boundary; repositories select a site database using server-side registry data. See the ADRs for trust, transactions, and migration safety.
