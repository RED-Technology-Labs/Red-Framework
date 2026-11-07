# Metadata model

Status: partially implemented and integration tested. V1 system tables, Kotlin entity/attribute definitions, and a PostgreSQL repository now persist and restore immutable revisions. Attribute registry, richer validation, caching, and schema synchronization remain pending.

An entity has a stable opaque ID, a canonical name, an owning application ID, and an immutable sequence of definition revisions. Each attribute has its own stable ID and an entity-scoped canonical name; its definition changes are captured inside entity revisions, with an attribute revision counter for audit and compatibility checks. Rename preserves IDs; name reuse requires explicit conflict checks. Published revisions are immutable.

A metadata snapshot contains the complete definition graph for one entity at a revision, a schema format version, a content checksum, author and timestamp, and the previous revision reference. The change log records the proposed change, validation result, approval, migration-plan ID, activation, and failure details. A revision is proposed before schema work and becomes active only after successful schema verification. The last active snapshot remains queryable for rollback planning; reverting active metadata is a new revision, not deletion of history.

Compatibility checks compare stable IDs, logical types, requiredness, defaults, relationships, and stored data implications. Additive changes may be eligible for automatic application after validation. Destructive or incompatible changes require explicit approval and an expand-and-contract plan. Cache keys include site ID, entity ID, and active revision; activation invalidates or changes the revision pointer atomically. A stale cache must never serve another site's metadata.

Entity revision, application manifest version, schema migration plan/checksum, and framework Flyway version are independent identifiers. Their association is recorded in migration history rather than inferred from matching numbers.
