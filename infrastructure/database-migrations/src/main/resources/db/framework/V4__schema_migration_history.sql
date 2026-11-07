CREATE TABLE red_schema_migration (
    entity_id UUID NOT NULL REFERENCES red_entity(id),
    from_revision BIGINT,
    to_revision BIGINT NOT NULL,
    operation VARCHAR(16) NOT NULL CHECK (operation IN ('CREATE', 'ADD_COLUMNS', 'NO_DDL')),
    applied_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (entity_id, to_revision),
    CONSTRAINT red_schema_migration_revision_fk
        FOREIGN KEY (entity_id, to_revision)
        REFERENCES red_entity_revision(entity_id, revision)
);
