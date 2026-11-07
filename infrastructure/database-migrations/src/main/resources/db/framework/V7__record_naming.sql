CREATE TABLE red_entity_sequence (
    entity_id UUID PRIMARY KEY REFERENCES red_entity(id) ON DELETE CASCADE,
    last_value BIGINT NOT NULL CHECK (last_value > 0)
);

CREATE TABLE red_record_name (
    entity_id UUID NOT NULL REFERENCES red_entity(id) ON DELETE CASCADE,
    record_id UUID NOT NULL,
    name VARCHAR(64) NOT NULL,
    PRIMARY KEY (entity_id, record_id),
    UNIQUE (entity_id, name)
);
