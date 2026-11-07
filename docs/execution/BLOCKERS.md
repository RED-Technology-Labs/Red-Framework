# Blockers

No blocker for metadata persistence. Docker is absent locally, so Testcontainers remains skipped; the dedicated Neon test database is available for real PostgreSQL tests. CI behavior is not yet observed. The shared Neon credential should be rotated after this session and never committed.
