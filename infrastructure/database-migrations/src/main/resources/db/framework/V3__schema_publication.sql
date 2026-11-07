CREATE TABLE red_entity_publication (
    entity_id UUID PRIMARY KEY REFERENCES red_entity(id),
    published_revision BIGINT NOT NULL,
    table_name VARCHAR(63) NOT NULL UNIQUE,
    published_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT red_entity_publication_revision_fk
        FOREIGN KEY (entity_id, published_revision)
        REFERENCES red_entity_revision(entity_id, revision)
);
