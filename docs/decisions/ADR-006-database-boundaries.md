# ADR-006: Database abstraction and transactions

Status: proposed; implementation pending.

## Context
Core use cases need persistence and atomicity but must not depend on PostgreSQL or ORM types.

## Requirements
Keep core testable, support fixed framework tables and dynamic record tables, and make transaction ownership explicit.

## Alternatives
Direct JDBC in core leaks infrastructure. A generic SQL abstraction in core risks recreating a database library. Narrow repository ports plus a transaction boundary match use-case needs.

## Trade-offs
Ports require adapter code but keep domain behavior independent of storage details.

## Decision
Define repository ports beside consuming core modules and a narrow transaction boundary for application use cases. PostgreSQL adapters implement ports. Use Exposed only where suitable for fixed system tables; use safe JDBC or dedicated SQL building for dynamic tables. One use case owns one site-scoped transaction. No ordinary cross-site transaction. Publish integration events through an outbox after commit.

## Consequences
Adapters must share the same transaction context. Transaction tests must cover rollback and concurrency.

## Reconsideration conditions
Revisit if ports become too broad or a different persistence technology materially improves correctness.
