# Flyway dependency decision

Status: selected and tested 2026-11-06.

Flyway Open Source Core and its PostgreSQL module are pinned to 13.12.1. [Redgate's release notes](https://documentation.red-gate.com/fd/release-notes-for-flyway-engine-179732572.html) date that stable release to 2026-11-05. [Official PostgreSQL documentation](https://documentation.red-gate.com/fd/postgresql-database-277579325.html) identifies `org.flywaydb:flyway-database-postgresql` as the Open Source artifact. The [source license](https://github.com/flyway/flyway/blob/main/LICENSE.txt) is Apache-2.0. Flyway 13 requires Java 21 or newer; RED uses JDK 25.

The migration module takes a `DataSource`, validates the server-selected schema name, disables `clean` and `baselineOnMigrate`, validates migrations on rerun, and uses `red_flyway_schema_history` in the selected schema. V1 creates only framework-owned application and entity metadata tables. RED dynamic business tables will have their own migration planner and history; Flyway never manages them.

A dedicated Neon test schema verified first application, rerun idempotence, schema history, table presence, FK enforcement, and cleanup. No production schema or business data was changed. The implementation does not yet persist or retrieve metadata through a repository.
