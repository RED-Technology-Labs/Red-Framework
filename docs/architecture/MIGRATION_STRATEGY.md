# Migration strategy

Status: design only. No DDL executor exists yet.

Lifecycle: metadata change -> schema inspection and diff -> immutable migration plan -> risk classification -> validation -> approval (if required) -> lock acquisition -> execution -> verification -> history recording -> metadata activation and cache invalidation.

A plan identifies site, entity, source and target metadata revisions, expected schema fingerprint, ordered operations, risk, and checksum. Recheck the fingerprint after acquiring a site/entity scoped PostgreSQL advisory lock; stale plans fail and must be regenerated. Lock ordering is deterministic to avoid deadlocks. A unique plan key and persisted status prevent duplicate execution. Hold the lock through execution and verification. Only one migration per entity runs at once.

Additive changes may run automatically only after policy validation. Drops, narrowing, incompatible type changes, constraint changes that can reject existing rows, and data rewrites require explicit approval. Never auto-drop business data. Prefer expand, backfill, verify, switch, contract. PostgreSQL transactional DDL should be used where supported; nontransactional operations need resumable steps and explicit failure states. A failed plan does not activate new metadata. Framework-owned Flyway migrations and dynamic entity plans have separate histories.
