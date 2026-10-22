-- Apply once to an existing database after a backup; all rows are preserved.
-- New code validates UUID conversation IDs; legacy history stays separate.
ALTER TABLE chat_history
  ADD COLUMN conversation_id varchar(64) NOT NULL DEFAULT 'legacy',
  ADD COLUMN turn_id varchar(64) DEFAULT NULL,
  ADD INDEX idx_user_app_conversation (user_id, app_id, conversation_id, create_time);

CREATE TABLE IF NOT EXISTS voice_feedback (
  id bigint NOT NULL AUTO_INCREMENT PRIMARY KEY,
  user_id bigint NOT NULL,
  app_id bigint NOT NULL,
  conversation_id varchar(64) NOT NULL,
  turn_id varchar(64) NOT NULL,
  category varchar(32) NOT NULL,
  elapsed_ms bigint DEFAULT NULL,
  created_at timestamp DEFAULT CURRENT_TIMESTAMP,
  KEY idx_feedback_user_conversation (user_id, conversation_id, turn_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
