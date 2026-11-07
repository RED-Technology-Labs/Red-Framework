# ADR-007: Framework-owned and application-owned data

Status: proposed; implementation pending.

## Context
RED must distinguish system state from installed application definitions and user business records.

## Requirements
Version system schema independently, keep business data free of automatic demo seeds, and allow safe application installation.

## Alternatives
One migration stream is simple but couples framework upgrades to runtime entity changes. Separate streams preserve ownership and lifecycle.

## Trade-offs
Separate streams need coordination at application installation and upgrade time.

## Decision
Framework-owned tables and essential system metadata use versioned Flyway migrations. Application manifests and entity definitions are versioned metadata; dynamic tables use RED migration plans. Business records are created only by explicit operations. Demo fixtures are test or opt-in development resources and never run at production startup.

## Consequences
Migration history must record ownership and association across streams. Install and uninstall flows need explicit data-retention policy.

## Reconsideration conditions
Revisit if version coordination becomes unmanageable, while retaining independent audit trails and no implicit business seeds.
