# Decisions

ADRs 001–007 in `docs/decisions` cover site isolation, SQL identifiers, metadata versioning, trust, migrations, transactions, and data ownership.

Phase B uses dependency-free Kotlin domain contracts. UUID-backed IDs reject noncanonical formats. Contexts carry site identity after delivery-boundary verification; the context object itself does not authenticate a request.

Phase C uses HikariCP 7.1.0 and pgJDBC 42.7.14 for the first PostgreSQL adapter, with Testcontainers 2.0.5 for integration verification. Flyway and Exposed are deferred until their respective use cases are implemented. See `docs/research/POSTGRES_DEPENDENCIES.md`.
