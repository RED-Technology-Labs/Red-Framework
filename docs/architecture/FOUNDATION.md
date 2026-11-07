# RED foundation assessment

Status: Phase A build foundation verified; Phase B Kernel foundation implemented and unit tested; Phase C PostgreSQL adapter and Flyway baseline tested. No business functionality exists.

## Repository assessment

The IntelliJ starter had a Gradle 9.8 wrapper, Kotlin catalog, a `buildSrc` convention plugin, and empty `app` and `utils` projects. The wrapper, catalog, and convention plugin remain. The starter projects are excluded from settings but preserved. The requested folder layout exists; `:core:kernel`, `:core:site`, `:core:application`, `:core:metadata`, `:infrastructure:database-postgres`, and `:infrastructure:database-migrations` are registered modules.

## Build and version decisions

Kotlin 2.4.21 stays. The wrapper changes to Gradle 9.7.0 because [Kotlin's compatibility table](https://kotlinlang.org/docs/gradle-configure-project.html) lists 9.7.0 as the maximum fully supported Gradle version for KGP 2.4.21. [Gradle 9.7's compatibility table](https://docs.gradle.org/9.7.0/userguide/compatibility.html) supports JDK 25 for running Gradle and toolchains. The shared convention plugin specifies both JDK 25 and JVM target 25. The Foojay resolver 1.0.0 in settings matches [Gradle's documented example](https://docs.gradle.org/current/userguide/toolchains.html). CI is configured with Temurin 25. The JDK on PATH is 17, but a JDK 25 is installed at `C:\Users\m7ame\.jdks\ms-25.0.5.0.2`; build verification must confirm the selected toolchain.

The generated catalog's [datetime 0.8.0](https://github.com/Kotlin/kotlinx-datetime/releases), [serialization 1.11.0](https://github.com/Kotlin/kotlinx.serialization/releases), and [coroutines 1.10.2](https://github.com/Kotlin/kotlinx.coroutines/releases) are stable releases. Coroutines 1.11.0 is also stable and its release notes cite Kotlin 2.2.20; it is a candidate update, but no active module uses coroutines yet. Keep 1.10.2 until an actual use case and compilation test justify a change. The existing catalog aliases are retained. Phase C adds HikariCP 7.1.0 and pgJDBC 42.7.14; see `docs/research/POSTGRES_DEPENDENCIES.md`. Artifact compatibility beyond release status remains unverified until a module consumes each library.

## Proposed dependency graph

See [ARCHITECTURE.md](ARCHITECTURE.md). Core modules depend on `kernel` and other core contracts as needed. `infrastructure/database-postgres` depends on core ports; core never depends on PostgreSQL. Server and worker are composition roots. The proposed graph has no cycle; the active Gradle graph has site and application depending on kernel; PostgreSQL infrastructure has no project dependency yet.

## Decisions and plans

Seven ADRs in `docs/decisions` cover site isolation, identifiers, metadata versioning, site trust, migration safety, transaction boundaries, and data ownership. Detailed specifications are in `DATABASE_DESIGN.md`, `METADATA_MODEL.md`, and `MIGRATION_STRATEGY.md`. The ADRs are proposals for architectural review before dependent implementation, not claims of working features.

Build verification on 2026-11-06: `gradlew.bat --version` reported Gradle 9.7.0 and Microsoft JDK 25.0.5 for launcher and daemon. `gradlew.bat -q javaToolchains` detected JDK 25 and JDK 17; the convention plugin selects 25. `gradlew.bat clean build --console=plain` passed in 6m 27s (12 actionable tasks: 11 executed, 1 up-to-date). Kernel compile and test tasks reported `NO-SOURCE`; no application code or tests were executed. Gradle emitted a native-access warning from its own library under JDK 25; it did not fail the build. The previous Gradle 9.8 daemon was stopped cleanly before this run.

Phase A still needs formatting/static-analysis policy and implementation review. Phase B Kernel foundation is implemented and unit tested. Subsequent phases are PostgreSQL infrastructure; site, application, and metadata foundations; attribute registry; schema and record engines; and a thin developer API. The first vertical slice will create and retrieve a Customer entity and record from PostgreSQL through explicit tests, without production seed data.

## Current implementation

Kernel domain contracts compile and six unit tests pass. PostgreSQL pooling and JDBC transaction code compile; five configuration and URL tests pass. The external Neon transaction test passed against a dedicated test database, while the Testcontainers test remains skipped locally because Docker is unavailable. See execution status for exact commands. Site and Application domain contracts are implemented and unit tested, without registry or installer adapters. Flyway V1 system tables are implemented and tested on Neon. A PostgreSQL metadata repository now persists and restores active and historical revisions. No server, dynamic schema engine, or record engine is implemented yet.
