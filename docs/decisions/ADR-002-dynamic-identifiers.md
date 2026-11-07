# ADR-002: Dynamic identifiers and quoting

Status: proposed; implementation pending.

## Context
Entity and attribute metadata will generate PostgreSQL tables and columns. SQL parameters cannot bind identifiers.

## Requirements
Reject invalid names and collisions, prevent SQL injection, preserve stable physical names, and fit PostgreSQL identifier limits.

## Alternatives
Raw metadata names are unsafe. Hash-only names are safe but hard to inspect. Validated canonical names plus stable ID suffixes are inspectable and collision resistant.

## Trade-offs
Stable ID suffixes reduce readable space but protect renamed entities and attributes from accidental table or column changes.

## Decision
Define a strict ASCII canonical-name grammar, reserved namespace list, normalization rule, and maximum length before code. Derive physical names from immutable IDs and a shortened canonical stem. Validate against PostgreSQL's 63-byte limit and collisions. Quote every validated identifier through one SQL builder; bind all values. Never accept prequoted input.

## Consequences
Changing display labels does not rename physical storage. Renames need explicit migration plans. The exact grammar is to be finalized with schema-engine tests.

## Reconsideration conditions
Revisit if multilingual canonical names become a requirement or PostgreSQL naming limits change.
