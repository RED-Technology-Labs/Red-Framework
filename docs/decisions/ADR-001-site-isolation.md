# ADR-001: Site database isolation

Status: proposed; implementation pending.

## Context
RED must host multiple isolated sites with dynamic schemas and independent application lifecycles.

## Requirements
Prevent cross-site reads and writes, support separate backup and restoration, and keep site selection server controlled.

## Alternatives
Shared tables with a site column reduce connection overhead but make every query and migration responsible for isolation. Schema per site separates namespaces but shares one database lifecycle. Database per site provides a strong operational boundary at the cost of more databases and pools.

## Trade-offs
Database per site simplifies data isolation and restores, but increases provisioning, migration, and connection-pool costs.

## Decision
Use database per site as the initial target. A trusted site registry supplies the database mapping. A single local site is supported first. Do not infer a connection from a client-supplied identifier.

## Consequences
Connections and migrations are scoped to one site. Operational tooling must eventually handle fleet-wide provisioning and updates. This ADR does not claim implementation.

## Reconsideration conditions
Reassess if site count or connection overhead makes database per site impractical, while preserving strict isolation tests.
