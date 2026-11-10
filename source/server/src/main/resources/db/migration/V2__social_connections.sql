CREATE TABLE social_connections (
 id VARCHAR(36) PRIMARY KEY,
 created_at TIMESTAMP(6) NOT NULL,
 first_user_id VARCHAR(36) NOT NULL,
 second_user_id VARCHAR(36) NOT NULL,
 requester_id VARCHAR(36),
 friendship_status VARCHAR(20) NOT NULL,
 conversation_id VARCHAR(36) UNIQUE,
 CONSTRAINT uq_social_pair UNIQUE(first_user_id, second_user_id),
 CONSTRAINT fk_social_first FOREIGN KEY(first_user_id) REFERENCES users(id),
 CONSTRAINT fk_social_second FOREIGN KEY(second_user_id) REFERENCES users(id),
 CONSTRAINT fk_social_requester FOREIGN KEY(requester_id) REFERENCES users(id),
 CONSTRAINT fk_social_conversation FOREIGN KEY(conversation_id) REFERENCES conversations(id),
 CONSTRAINT ck_social_pair CHECK(first_user_id < second_user_id),
 CONSTRAINT ck_social_status CHECK(friendship_status IN ('NONE','PENDING','ACCEPTED')),
 CONSTRAINT ck_social_requester CHECK(
  (friendship_status = 'NONE' AND requester_id IS NULL) OR
  (friendship_status IN ('PENDING','ACCEPTED') AND requester_id IS NOT NULL
   AND (requester_id = first_user_id OR requester_id = second_user_id))
 )
);
CREATE INDEX ix_social_second ON social_connections(second_user_id, friendship_status);
