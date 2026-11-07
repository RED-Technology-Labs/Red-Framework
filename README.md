# RED Framework

RED is an in-progress Kotlin framework for metadata-driven business applications. The current runnable slice covers initial setup, login, application registration, Entity definitions, safe schema publication, and typed record creation and editing in PostgreSQL. It is not a complete or production-ready framework.

## Run the current slice on Windows

1. Install JDK 25 and Node.js 20.19+ or 22.12+.
2. Set `DATABASE_URL` in your terminal to a PostgreSQL URI for a dedicated development database. Keep it out of source files. Set `RED_SCHEMA=red_framework` if you want the default schema, or choose another lowercase schema name.
3. Start the API from the repository root: `.\gradlew.bat :apps:server:run`. The server creates its framework schema, runs Flyway migrations, and listens on `127.0.0.1:8080` by default.
4. In another terminal, run `cd studio`, `npm ci`, then `npm run dev`. Open the URL printed by Vite, usually `http://localhost:5173`.
5. On first launch, the Setup screen creates the administrator and first Site in PostgreSQL. Use a password of at least 12 characters with uppercase, lowercase, number, and symbol. Then sign in.
6. Check Overview for an online server status. Sites shows the first Site stored during setup. Open Applications and enter a lowercase canonical name such as `demo-app` and a version such as `1.0.0`.
7. Open Entity definitions, select the application, enter an Entity name and label, add supported Attributes, arrange fields into form tabs, choose list columns and a title field, and optionally configure a naming prefix and digit count. Save the draft; RED stores an immutable revision in PostgreSQL.
8. Publish the definition after reviewing the confirmation. RED creates a real PostgreSQL table. Label edits and new optional Attributes can be published later; removing or changing a published Attribute is rejected until a safe migration plan exists.
9. Open Desk for the published Entity. Its form tabs, list columns, and title come from the published revision. New records receive a generated name when a naming series is configured. An edit uses a record version and reports a conflict if another edit won first. Activity shows the actor, action, version, and time for each saved change.
10. An administrator can create restricted users in Users and grant READ, CREATE, or UPDATE on individual Entities. A user needs READ to see that Entity in Desk.

The API supports health, setup, login/logout, Site listing, application registration, Entity revisions, schema publication, record create/list/update, per-record audit history, and basic Entity grants. Studio uses an HTTP-only, SameSite session cookie; mutation requests require `X-RED-CSRF: 1`. The server binds to localhost. This is a development slice without rate limiting, Site isolation, or complete permissions; do not expose it to the network. The database is configured through `DATABASE_URL` before server startup; the setup screen checks connectivity but does not change credentials. Language and appearance selections currently preview the setup screen only. The initial single-Site API flow, including naming, presentation metadata, and restricted-user READ/CREATE grants, passed against a disposable schema in the dedicated Neon test database on 2026-11-07. Studio builds, but no browser end-to-end run has been completed.

## Active modules

- `:core:kernel`: typed IDs, contexts, errors, outcomes, clock, event contracts, and revisions; six unit tests pass.
- `:core:metadata`: validated entity and attribute definitions, repository port, and PostgreSQL adapter for immutable revisions.
- `:core:attribute-types`: seven built-in type descriptors and validation before metadata persistence.
- `:core:site`: validated Site descriptor and registry port. Initial setup persists one Site; a PostgreSQL adapter lists it. Site provisioning and isolation are not implemented yet.
- `:core:identity`: initial setup validation and identity port. PostgreSQL adapter persists the first administrator, role, and revocable sessions.
- `:core:application`: validated manifest, version, dependencies, and registry port. PostgreSQL registration supports manifests without dependencies; no installation flow exists.
- `:core:schema`: publication contract. PostgreSQL adapter creates typed business tables and applies label-only and optional-column migrations transactionally.
- `:core:records`: typed record values, audit events, and repository contract. PostgreSQL adapter stores records in the published table with optimistic versioned edits and transactionally records audit metadata.
- `:infrastructure:database-postgres`: Hikari pool, JDBC transactions, configuration and URL validation. Five unit tests and a real Neon transaction test pass.
- `:infrastructure:database-migrations`: Flyway Open Source V1–V7 framework tables for metadata, identity, publication, migration history, record audit, Entity grants, and naming. Dedicated Neon tests create and remove temporary schemas; V6 and V7 passed against the dedicated Neon test database.

`apps/server` is a local Ktor API and `studio` is a Vue setup, builder, and basic record interface. `apps/worker` and `sdk` remain unimplemented. Generated `app` and `utils` starter modules are excluded from the build.

## Build and database tests

Use JDK 25 and run `.\gradlew.bat clean build` on Windows or `./gradlew clean build` elsewhere. Gradle 9.7.0 and Kotlin 2.4.21 are pinned. Set `TEST_DATABASE_URL` only to a dedicated test PostgreSQL database to run external integration tests. They create and drop unique schemas. Do not commit credentials; `.env` files are ignored. Testcontainers requires Docker and skips when unavailable locally. CI checks Docker availability.

See [architecture](docs/architecture/ARCHITECTURE.md), [execution status](docs/execution/IMPLEMENTATION_STATUS.md), [current task](docs/execution/CURRENT_TASK.md), and [decisions](docs/decisions).
