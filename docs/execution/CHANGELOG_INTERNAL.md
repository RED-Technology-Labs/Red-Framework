# Internal changelog

2026-11-06: Phase A architecture and build foundation established. Autonomous execution tracking created. Phase B Kernel identifiers, contexts, errors, outcomes, clock, events, and revisions implemented with six tests. Phase C began with Hikari pool, JDBC transaction boundary, configuration validation, and a Testcontainers PostgreSQL test; local integration verification pending Docker.

2026-11-06: Redacted PostgreSQL configuration 	oString; added regression test to protect host, username, and password.

2026-11-06: Added Site descriptor and registry port, Application manifest/version/dependency invariants, and five tests. No persistence or installer implementation yet.

2026-11-06: Added Neon-style PostgreSQL URL conversion, opt-in TEST_DATABASE_URL integration test with unique schema cleanup, and .env.example; no credentials committed.

2026-11-06: Confirmed supplied Neon database is test-only, ran the external transaction integration test successfully, then removed TEST_DATABASE_URL from the process and reran the root build.

2026-11-06: Added Flyway Open Source framework migration module and V1 application/entity/revision tables. Verified migration and idempotence on Neon. Fixed Gradle caching of externally configured database tests.

2026-11-06: Implemented durable metadata snapshots, active/historical revision retrieval, optimistic updates, site check, and full-snapshot checksum verification.
