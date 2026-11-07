-- Framework-owned metadata storage. Dynamic business tables use RED migration plans, not Flyway.
CREATE TABLE red_application (
    id UUID PRIMARY KEY,
    canonical_name VARCHAR(63) NOT NULL UNIQUE,
    version_major INTEGER NOT NULL CHECK (version_major >= 0),
    version_minor INTEGER NOT NULL CHECK (version_minor >= 0),
    version_patch INTEGER NOT NULL CHECK (version_patch >= 0),
    registered_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT red_application_name_format CHECK (canonical_name ~ '^[a-z][a-z0-9-]{1,61}[a-z0-9]$')
);

CREATE TABLE red_entity (
    id UUID PRIMARY KEY,
    application_id UUID NOT NULL REFERENCES red_application(id),
    canonical_name VARCHAR(63) NOT NULL,
    active_revision BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT red_entity_name_format CHECK (canonical_name ~ '^[a-z][a-z0-9_]{1,61}[a-z0-9]$'),
    CONSTRAINT red_entity_active_revision_positive CHECK (active_revision IS NULL OR active_revision > 0),
    CONSTRAINT red_entity_name_unique UNIQUE (application_id, canonical_name)
);

CREATE TABLE red_entity_revision (
    entity_id UUID NOT NULL REFERENCES red_entity(id),
    revision BIGINT NOT NULL CHECK (revision > 0),
    format_version INTEGER NOT NULL CHECK (format_version > 0),
    snapshot JSONB NOT NULL,
    checksum CHAR(64) NOT NULL CHECK (checksum ~ '^[0-9a-f]{64}$'),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (entity_id, revision)
);

ALTER TABLE red_entity
    ADD CONSTRAINT red_entity_active_revision_fk
    FOREIGN KEY (id, active_revision)
    REFERENCES red_entity_revision(entity_id, revision)
    DEFERRABLE INITIALLY DEFERRED;
