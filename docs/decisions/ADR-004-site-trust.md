# ADR-004: Authentication and site trust boundaries

Status: proposed; implementation pending.

## Context
A request may carry a hostname, token, or site hint, none of which alone proves authority to access a site.

## Requirements
Resolve one trusted site, authenticate separately, authorize every operation, and deny access by default.

## Alternatives
Client-supplied site IDs are convenient but spoofable. Host-only routing can be misconfigured. Registry-backed routing with verified credentials and membership checks provides an explicit trust chain.

## Trade-offs
Registry lookups and membership checks add latency and operational dependencies, which may later need safe caching.

## Decision
Delivery adapters resolve a site candidate from a configured route, look it up in a server-controlled registry, validate the authenticated principal's site membership, then construct an immutable SiteContext. Use cases require that context explicitly. Repository adapters bind to the database descriptor from the trusted registry, never from request input. Authentication and authorization are distinct; all permissions default to deny.

## Consequences
No ambient global site variable is allowed. Site context must propagate to jobs and outbox handlers with revalidation.

## Reconsideration conditions
Revisit routing methods when deploying custom domains or federated identity, without weakening the trust checks.
