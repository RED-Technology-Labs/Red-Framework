# ADR-005: Dynamic schema migration safety

Status: proposed; implementation pending.

## Context
Metadata changes may alter live business tables and risk data loss or downtime.

## Requirements
Plan before execution, classify risk, require approval for destructive changes, serialize concurrent changes, preserve data, and record outcomes.

## Alternatives
Immediate DDL is simple but unsafe. Flyway for every entity change does not naturally handle runtime metadata. A dedicated metadata-aware planner can bind each plan to the definition revision and live schema.

## Trade-offs
The planner adds complexity but makes changes reviewable and recoverable.

## Decision
Use the lifecycle in MIGRATION_STRATEGY.md. Plans are immutable and checksummed. Execution acquires a site/entity lock, rechecks schema fingerprint, records state, applies safe operations, verifies, then activates metadata. Destructive operations never run automatically. Use expand-and-contract for incompatible changes.

## Consequences
Some changes require manual approval and staged deployment. Failure must leave old active metadata intact.

## Reconsideration conditions
Revisit policy only with proven migration tests and operational evidence.
