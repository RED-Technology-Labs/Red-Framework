# ADR-003: Metadata persistence and versioning

Status: proposed; implementation pending.

## Context
Runtime records must follow definitions that change over time without losing the history needed for migration or audit.

## Requirements
Stable IDs, immutable revisions, snapshots, change history, compatibility checks, migration association, and site-safe caches.

## Alternatives
Mutable rows are easy to query but lose historical truth. Event-only storage makes reads and migrations expensive. Immutable snapshots plus a change log give reliable historical reads and manageable lookup cost.

## Trade-offs
Snapshots duplicate definition data; checksums and schema versions add discipline but make changes auditable and replayable.

## Decision
Store stable entity and attribute IDs, append immutable entity snapshots, and track attribute revisions within them. Maintain a separate active revision pointer per site and entity. Record change events and migration-plan association. Cache by site, entity, and revision; invalidate the active pointer on activation. Application version and database migration version remain separate.

## Consequences
Reverts create a new revision. Activation is gated on schema verification. Persistence format and indexes require integration tests.

## Reconsideration conditions
Revisit if snapshot size or query cost becomes material under measured workloads.
