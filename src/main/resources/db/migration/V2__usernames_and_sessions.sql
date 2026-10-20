-- Username + password sign-in, and sign-in sessions.

-- Usernames are stored in lower case so "Zach" and "zach" can't both exist.
ALTER TABLE app_user ADD COLUMN username VARCHAR(20);
ALTER TABLE app_user ADD CONSTRAINT uq_app_user_username UNIQUE (username);

-- Username accounts don't have an email address; Google accounts do.
ALTER TABLE app_user ALTER COLUMN email DROP NOT NULL;

-- One row per signed-in browser. Only a SHA-256 hash of the token is stored,
-- so a copy of the database can't be used to sign in as anyone.
CREATE TABLE user_session (
    token_hash  CHAR(64)  PRIMARY KEY,
    user_id     BIGINT    NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at  TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_user_session_user ON user_session (user_id);
