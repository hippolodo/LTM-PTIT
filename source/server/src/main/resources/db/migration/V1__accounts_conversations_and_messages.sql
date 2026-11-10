CREATE TABLE users (
 id VARCHAR(36) PRIMARY KEY, created_at TIMESTAMP(6) NOT NULL,
 username VARCHAR(32) NOT NULL UNIQUE, email VARCHAR(254) NOT NULL UNIQUE,
 password_hash VARCHAR(100) NOT NULL, display_name VARCHAR(80) NOT NULL,
 avatar_url VARCHAR(500), role VARCHAR(20) NOT NULL, status VARCHAR(20) NOT NULL,
 updated_at TIMESTAMP(6) NOT NULL, last_login_at TIMESTAMP(6)
);
CREATE TABLE guest_sessions (
 id VARCHAR(36) PRIMARY KEY, created_at TIMESTAMP(6) NOT NULL,
 guest_token_hash VARCHAR(64) NOT NULL UNIQUE, expires_at TIMESTAMP(6) NOT NULL,
 last_active_at TIMESTAMP(6) NOT NULL, status VARCHAR(20) NOT NULL
);
CREATE INDEX ix_guest_expiry ON guest_sessions(expires_at);
CREATE TABLE conversations (
 id VARCHAR(36) PRIMARY KEY, created_at TIMESTAMP(6) NOT NULL,
 conversation_type VARCHAR(20) NOT NULL, status VARCHAR(20) NOT NULL,
 persistent_history BOOLEAN NOT NULL, started_at TIMESTAMP(6) NOT NULL,
 ended_at TIMESTAMP(6), expires_at TIMESTAMP(6)
);
CREATE INDEX ix_conversation_expiry ON conversations(persistent_history,expires_at);
CREATE TABLE conversation_participants (
 id VARCHAR(36) PRIMARY KEY, created_at TIMESTAMP(6) NOT NULL,
 conversation_id VARCHAR(36) NOT NULL, user_id VARCHAR(36), guest_session_id VARCHAR(36),
 joined_at TIMESTAMP(6) NOT NULL, left_at TIMESTAMP(6), hidden_at TIMESTAMP(6),
 CONSTRAINT fk_participant_conversation FOREIGN KEY(conversation_id) REFERENCES conversations(id),
 CONSTRAINT fk_participant_user FOREIGN KEY(user_id) REFERENCES users(id),
 CONSTRAINT fk_participant_guest FOREIGN KEY(guest_session_id) REFERENCES guest_sessions(id),
 CONSTRAINT ck_participant_identity CHECK ((user_id IS NOT NULL AND guest_session_id IS NULL) OR (user_id IS NULL AND guest_session_id IS NOT NULL)),
 CONSTRAINT uq_participant_conversation UNIQUE(conversation_id,id),
 CONSTRAINT uq_conversation_user UNIQUE(conversation_id,user_id),
 CONSTRAINT uq_conversation_guest UNIQUE(conversation_id,guest_session_id)
);
CREATE INDEX ix_participant_history ON conversation_participants(user_id,hidden_at,conversation_id);
CREATE INDEX ix_participant_guest ON conversation_participants(guest_session_id);
CREATE TABLE messages (
 id VARCHAR(36) PRIMARY KEY, created_at TIMESTAMP(6) NOT NULL,
 conversation_id VARCHAR(36) NOT NULL, sender_participant_id VARCHAR(36) NOT NULL,
 client_message_id VARCHAR(64) NOT NULL, message_type VARCHAR(20) NOT NULL,
 content VARCHAR(4000), edited_at TIMESTAMP(6), deleted_at TIMESTAMP(6), status VARCHAR(20) NOT NULL,
 CONSTRAINT fk_message_conversation FOREIGN KEY(conversation_id) REFERENCES conversations(id),
 CONSTRAINT fk_message_sender FOREIGN KEY(conversation_id,sender_participant_id) REFERENCES conversation_participants(conversation_id,id),
 CONSTRAINT uq_message_retry UNIQUE(conversation_id,sender_participant_id,client_message_id)
);
CREATE INDEX ix_messages_page ON messages(conversation_id,created_at,id);
CREATE TABLE message_attachments (
 id VARCHAR(36) PRIMARY KEY, created_at TIMESTAMP(6) NOT NULL,
 message_id VARCHAR(36) NOT NULL UNIQUE, original_filename VARCHAR(180) NOT NULL,
 storage_key VARCHAR(500) NOT NULL UNIQUE, mime_type VARCHAR(100) NOT NULL,
 file_size BIGINT NOT NULL, attachment_type VARCHAR(20) NOT NULL, duration_seconds DOUBLE,
 CONSTRAINT fk_attachment_message FOREIGN KEY(message_id) REFERENCES messages(id)
);
CREATE TABLE auth_sessions (
 id VARCHAR(36) PRIMARY KEY, created_at TIMESTAMP(6) NOT NULL,
 user_id VARCHAR(36) NOT NULL, refresh_token_hash VARCHAR(64) NOT NULL,
 expires_at TIMESTAMP(6) NOT NULL, revoked_at TIMESTAMP(6), rotated_at TIMESTAMP(6) NOT NULL,
 CONSTRAINT fk_auth_user FOREIGN KEY(user_id) REFERENCES users(id)
);
CREATE INDEX ix_auth_expiry ON auth_sessions(expires_at);
CREATE INDEX ix_auth_user ON auth_sessions(user_id,revoked_at);
