# Database design

Status: partially implemented. PostgreSQL adapters, Flyway V1–V5 framework tables, revisioned Metadata, publication, safe additive schema migrations, typed record create/list/update, and transactional audit metadata are tested against a dedicated Neon test database. Multi-Site database isolation is not implemented.

A site registry in the control plane maps a verified site identity to a database connection descriptor. The preferred isolation strategy is one PostgreSQL database per site. Credentials and connection information are server controlled. Each site database contains framework-owned system tables and application-owned dynamic record tables. Framework tables are migrated with Flyway; dynamic entity tables use RED migration plans and history. These are distinct version streams.

Core metadata and record modules define repository and transaction contracts. The PostgreSQL adapter implements them. A use case declares its transaction boundary; one transaction handles metadata activation, migration history, and related database writes where PostgreSQL permits. External side effects require an outbox after commit. Cross-site transactions are prohibited in ordinary site operations.

Dynamic identifiers come from validated canonical names, mapped deterministically to SQL identifiers, checked against PostgreSQL length and reserved namespaces, and quoted at one SQL boundary. Parameter binding handles data values. No metadata change directly executes DDL. See ADR-002, ADR-005, and ADR-006.

V1 creates `red_application`, `red_entity`, and `red_entity_revision`; V2 adds initial Site, identity, role, session, and setup tables; V3 tracks current published Entity revision; V4 records RED schema migrations. Application and entity IDs are UUIDs. Entity revisions have positive revision numbers, JSONB snapshots, and SHA-256 checksums. On startup the server applies Flyway migrations to its configured framework schema.

The RED Schema Publisher creates a physical table with UUID-based identifiers for each published Entity and Attribute. It applies label-only revisions without DDL and adds new nullable columns transactionally. It refuses removal, renaming, retyping, or adding a required published Attribute until a migration plan exists. The record adapter writes typed values to those physical columns and uses an optimistic version on updates. The current server supports exactly one Site in one configured database; do not treat the Site registry row as implemented database-per-Site isolation.
