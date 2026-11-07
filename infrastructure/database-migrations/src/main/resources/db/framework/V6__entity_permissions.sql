CREATE TABLE red_entity_permission (
    user_id UUID NOT NULL REFERENCES red_user(id) ON DELETE CASCADE,
    entity_id UUID NOT NULL REFERENCES red_entity(id) ON DELETE CASCADE,
    action VARCHAR(8) NOT NULL CHECK (action IN ('READ', 'CREATE', 'UPDATE')),
    granted_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, entity_id, action)
);
CREATE INDEX red_entity_permission_entity_idx ON red_entity_permission(entity_id, user_id);
