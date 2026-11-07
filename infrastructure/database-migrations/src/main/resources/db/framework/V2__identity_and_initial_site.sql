CREATE TABLE red_site (
    id UUID PRIMARY KEY,
    canonical_name VARCHAR(63) NOT NULL UNIQUE,
    status VARCHAR(16) NOT NULL CHECK (status IN ('ACTIVE', 'SUSPENDED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE red_user (
    id UUID PRIMARY KEY,
    full_name VARCHAR(120) NOT NULL,
    email VARCHAR(320) NOT NULL UNIQUE,
    password_hash VARCHAR(300) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE red_role (
    id UUID PRIMARY KEY,
    canonical_name VARCHAR(63) NOT NULL UNIQUE
);

CREATE TABLE red_user_role (
    user_id UUID NOT NULL REFERENCES red_user(id),
    role_id UUID NOT NULL REFERENCES red_role(id),
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE red_session (
    token_hash CHAR(64) PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES red_user(id),
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX red_session_expiry_idx ON red_session(expires_at);

CREATE TABLE red_setup_state (
    singleton BOOLEAN PRIMARY KEY DEFAULT TRUE CHECK (singleton),
    site_id UUID NOT NULL REFERENCES red_site(id),
    administrator_id UUID NOT NULL REFERENCES red_user(id),
    completed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
