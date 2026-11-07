# Implementation status

| Subsystem | Status | Evidence |
| --- | --- | --- |
| Repository build | Tested initial single-Site slice | Gradle 9.7/JDK 25 build and Studio production build passed 2026-11-07; final XML reports 40 passed, 1 Docker skip, 0 failures |
| Kernel | Tested foundation | IDs, contexts, errors, results, events, clock, revisions; six unit tests pass |
| PostgreSQL connection and transactions | Tested transaction slice | Five config/URL tests and one external Neon transaction test pass; Testcontainers skips locally |
| Framework-owned migrations | V1–V7 Neon tested | Flyway applied all seven migrations in disposable test schemas, including Entity permissions and naming tables |
| Metadata persistence | Initial presentation flow tested | Immutable snapshots, checksum, active and historical retrieval, optimistic concurrency, cross-site context check. Neon HTTP integration verified form tabs and naming options in Entity responses; browser rendering remains unverified |
| Site domain | Initial Site discovery tested | Setup inserts first Site transactionally; PostgreSQL registry and authenticated API list it; no provisioning or Site isolation yet |
| Application domain | Functional registration slice | Manifest invariants and PostgreSQL registry; Ktor create/list/get API and Studio application page. No installer or dependency persistence yet; external Neon registry test is opt-in |
| Attribute type registry | Tested foundation | Seven built-in descriptors, extension and duplicate rules; metadata rejects unregistered types before PostgreSQL writes; unit and Neon integration tests pass |
| Dynamic schema | Safe subset tested | Real typed table creation; label-only and optional-column migrations; history in RED-owned table. Destructive/retype/required-column changes rejected |
| Records | Initial create/list/update and naming tested | Real typed PostgreSQL columns, validation, optimistic updates, audit, and first generated naming-series value verified on Neon. No delete, server filtering, field-level diffs, or bulk operations yet |
| Query engine | Designed | No runtime implementation |
| Initial identity | Implemented slice, external verification pending | Administrator setup, PBKDF2 password hash, role assignment, PostgreSQL sessions, login/logout, and administrator-managed restricted account creation/listing. New PostgreSQL user/role assertions skipped in this run. No password recovery or rate limiting yet |
| Authorization | Entity READ/CREATE tested | Builder routes verify the administrator role. V6 stores per-user READ/CREATE/UPDATE grants; Neon HTTP test verified denial before grants and permitted read/create after grants. UPDATE grant, role management, and record/attribute permissions remain outside the tested slice |
| Jobs, workflows, files, advanced services | Planned | Master directive |
| Developer API | Initial builder/runtime API tested | Ktor exposes setup/auth, Site list, application create/list/get, Entity create/edit/history/publish, record create/list/update/audit; no full permission model or Site provisioning yet |
| Desk | API tested, browser unverified | Published Entity discovery, record list/search/local pagination, create/update, audit activity, configured form tabs, titles, and list columns are wired to APIs and PrimeVue components. No browser end-to-end test, saved views, relation UI, workflow, notifications, or attachments |
| SDK | Planned | No runtime implementation |
| Studio | Production build passed, browser unverified | Setup, login, application, Site, Entity, record, user, and Entity permission pages are wired to tested APIs. Entity builder includes form tabs, list columns, title field, and naming series; PrimeVue 4 with Aura is configured. Site creation and advanced builders are still absent |
| Production readiness | Planned | No production certification |
