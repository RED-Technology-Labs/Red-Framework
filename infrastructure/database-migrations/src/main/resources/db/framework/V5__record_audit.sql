CREATE TABLE red_record_audit (
    id UUID PRIMARY KEY,
    site_id UUID NOT NULL REFERENCES red_site(id),
    entity_id UUID NOT NULL REFERENCES red_entity(id),
    record_id UUID NOT NULL,
    actor_id UUID NOT NULL REFERENCES red_user(id),
    action VARCHAR(16) NOT NULL CHECK (action IN ('CREATE', 'UPDATE')),
    record_version BIGINT NOT NULL CHECK (record_version > 0),
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (site_id, entity_id, record_id, record_version)
);
CREATE INDEX red_record_audit_record_idx ON red_record_audit(site_id, entity_id, record_id, occurred_at DESC);
