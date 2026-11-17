USE focus_app;

CREATE TABLE IF NOT EXISTS schema_meta (
  meta_key VARCHAR(64) NOT NULL PRIMARY KEY,
  meta_value VARCHAR(255) NOT NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

DROP PROCEDURE IF EXISTS focus_add_column;
DELIMITER $$
CREATE PROCEDURE focus_add_column(IN p_table_name VARCHAR(64), IN p_column_name VARCHAR(64), IN p_definition TEXT)
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table_name AND COLUMN_NAME = p_column_name
  ) THEN
    SET @sql = CONCAT('ALTER TABLE `', p_table_name, '` ADD COLUMN `', p_column_name, '` ', p_definition);
    PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
  END IF;
END$$
DELIMITER ;

CALL focus_add_column('focus_session', 'user_id', 'BIGINT UNSIGNED NULL AFTER id');
CALL focus_add_column('focus_session', 'task_id', 'BIGINT UNSIGNED NULL AFTER client_id');
CALL focus_add_column('focus_session', 'habit_id', 'BIGINT UNSIGNED NULL AFTER task_id');
CALL focus_add_column('focus_session', 'source', "VARCHAR(16) NOT NULL DEFAULT 'timer' AFTER status");
CALL focus_add_column('focus_session', 'version', 'INT NOT NULL DEFAULT 1 AFTER session_date');
CALL focus_add_column('focus_session', 'updated_at', 'DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) AFTER created_at');
DROP PROCEDURE IF EXISTS focus_add_column;

ALTER TABLE focus_session MODIFY task_title VARCHAR(200) NOT NULL DEFAULT '';
ALTER TABLE focus_session MODIFY note TEXT NOT NULL;
ALTER TABLE focus_session MODIFY started_at DATETIME(6) NOT NULL;
ALTER TABLE focus_session MODIFY ended_at DATETIME(6) NOT NULL;
ALTER TABLE focus_session MODIFY created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6);

DROP PROCEDURE IF EXISTS focus_drop_index;
DELIMITER $$
CREATE PROCEDURE focus_drop_index(IN p_index_name VARCHAR(64))
BEGIN
  IF EXISTS (
    SELECT 1 FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'focus_session' AND INDEX_NAME = p_index_name
  ) THEN
    SET @sql = CONCAT('ALTER TABLE focus_session DROP INDEX `', p_index_name, '`');
    PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
  END IF;
END$$
DELIMITER ;
SET @sql = IF((SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'focus_session' AND INDEX_NAME = 'idx_session_user_fk') = 0,
  'ALTER TABLE focus_session ADD KEY idx_session_user_fk (user_id)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
CALL focus_drop_index('uk_session_client');
CALL focus_drop_index('uk_session_user_client');
CALL focus_drop_index('idx_session_date');
CALL focus_drop_index('idx_session_user_date');
ALTER TABLE focus_session ADD UNIQUE KEY uk_session_user_client (user_id, client_id);
ALTER TABLE focus_session ADD KEY idx_session_user_date (user_id, session_date, deleted_at);
CALL focus_drop_index('idx_session_user_fk');
DROP PROCEDURE IF EXISTS focus_drop_index;

DROP PROCEDURE IF EXISTS focus_add_fk;
DELIMITER $$
CREATE PROCEDURE focus_add_fk(IN p_fk_name VARCHAR(64), IN p_definition TEXT)
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.TABLE_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'focus_session' AND CONSTRAINT_NAME = p_fk_name
  ) THEN
    SET @sql = CONCAT('ALTER TABLE focus_session ADD CONSTRAINT `', p_fk_name, '` ', p_definition);
    PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
  END IF;
END$$
DELIMITER ;

CREATE TABLE IF NOT EXISTS app_user (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  username VARCHAR(50) NOT NULL,
  password_hash VARCHAR(100) NOT NULL,
  display_name VARCHAR(50) NOT NULL,
  timezone VARCHAR(64) NOT NULL DEFAULT 'Asia/Shanghai',
  ics_token CHAR(36) NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id), UNIQUE KEY uk_user_username (username), UNIQUE KEY uk_user_ics_token (ics_token)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS task_list (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT, user_id BIGINT UNSIGNED NOT NULL,
  name VARCHAR(100) NOT NULL, color CHAR(7) NOT NULL DEFAULT '#3b6ef6',
  sort_order INT NOT NULL DEFAULT 0, archived TINYINT(1) NOT NULL DEFAULT 0,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id), KEY idx_list_user (user_id, archived, sort_order),
  CONSTRAINT fk_list_user FOREIGN KEY (user_id) REFERENCES app_user (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS task (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT, user_id BIGINT UNSIGNED NOT NULL,
  list_id BIGINT UNSIGNED NULL, title VARCHAR(200) NOT NULL, note TEXT NULL,
  completed TINYINT(1) NOT NULL DEFAULT 0, completed_at DATETIME(6) NULL,
  version INT NOT NULL DEFAULT 1, created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  deleted_at DATETIME(6) NULL, PRIMARY KEY (id),
  KEY idx_task_user_list (user_id, list_id, deleted_at),
  CONSTRAINT fk_task_user FOREIGN KEY (user_id) REFERENCES app_user (id),
  CONSTRAINT fk_task_list FOREIGN KEY (list_id) REFERENCES task_list (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS habit (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT, user_id BIGINT UNSIGNED NOT NULL,
  name VARCHAR(100) NOT NULL, color CHAR(7) NOT NULL DEFAULT '#22a06b', archived TINYINT(1) NOT NULL DEFAULT 0,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id), KEY idx_habit_user (user_id, archived),
  CONSTRAINT fk_habit_user FOREIGN KEY (user_id) REFERENCES app_user (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS calendar_subscription (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT, user_id BIGINT UNSIGNED NOT NULL,
  url TEXT NOT NULL, name VARCHAR(100) NOT NULL, color CHAR(7) NOT NULL DEFAULT '#7c6cf7',
  enabled TINYINT(1) NOT NULL DEFAULT 1, etag VARCHAR(255) NULL, last_modified VARCHAR(255) NULL,
  last_refreshed_at DATETIME(6) NULL, last_error VARCHAR(500) NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id), KEY idx_subscription_user (user_id, enabled),
  CONSTRAINT fk_subscription_user FOREIGN KEY (user_id) REFERENCES app_user (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS calendar_event (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT, user_id BIGINT UNSIGNED NOT NULL, kind VARCHAR(20) NOT NULL,
  task_id BIGINT UNSIGNED NULL, habit_id BIGINT UNSIGNED NULL, list_id BIGINT UNSIGNED NULL,
  subscription_id BIGINT UNSIGNED NULL, title VARCHAR(200) NOT NULL, note TEXT NULL,
  start_at DATETIME(6) NOT NULL, end_at DATETIME(6) NOT NULL, all_day TINYINT(1) NOT NULL DEFAULT 0,
  timezone VARCHAR(64) NOT NULL DEFAULT 'Asia/Shanghai', rrule TEXT NULL, color CHAR(7) NULL,
  external_uid VARCHAR(512) NULL, recurrence_id VARCHAR(128) NOT NULL DEFAULT '', version INT NOT NULL DEFAULT 1,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6), deleted_at DATETIME(6) NULL,
  PRIMARY KEY (id), UNIQUE KEY uk_ics_uid (subscription_id, external_uid(191), recurrence_id),
  KEY idx_event_range (user_id, start_at, end_at, deleted_at), KEY idx_event_task (task_id, deleted_at),
  CONSTRAINT fk_event_user FOREIGN KEY (user_id) REFERENCES app_user (id),
  CONSTRAINT fk_event_task FOREIGN KEY (task_id) REFERENCES task (id),
  CONSTRAINT fk_event_habit FOREIGN KEY (habit_id) REFERENCES habit (id),
  CONSTRAINT fk_event_list FOREIGN KEY (list_id) REFERENCES task_list (id),
  CONSTRAINT fk_event_subscription FOREIGN KEY (subscription_id) REFERENCES calendar_subscription (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS focus_preset (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT, user_id BIGINT UNSIGNED NOT NULL, name VARCHAR(100) NOT NULL,
  mode VARCHAR(16) NOT NULL, planned_sec INT NULL, task_id BIGINT UNSIGNED NULL, habit_id BIGINT UNSIGNED NULL,
  note_template TEXT NULL, sort_order INT NOT NULL DEFAULT 0,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id), KEY idx_preset_user (user_id, sort_order),
  CONSTRAINT fk_preset_user FOREIGN KEY (user_id) REFERENCES app_user (id),
  CONSTRAINT fk_preset_task FOREIGN KEY (task_id) REFERENCES task (id),
  CONSTRAINT fk_preset_habit FOREIGN KEY (habit_id) REFERENCES habit (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS user_setting (
  user_id BIGINT UNSIGNED NOT NULL, settings_json JSON NOT NULL,
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (user_id), CONSTRAINT fk_setting_user FOREIGN KEY (user_id) REFERENCES app_user (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CALL focus_add_fk('fk_session_user', 'FOREIGN KEY (user_id) REFERENCES app_user (id)');
CALL focus_add_fk('fk_session_task', 'FOREIGN KEY (task_id) REFERENCES task (id)');
CALL focus_add_fk('fk_session_habit', 'FOREIGN KEY (habit_id) REFERENCES habit (id)');
DROP PROCEDURE IF EXISTS focus_add_fk;

INSERT INTO schema_meta (meta_key, meta_value)
SELECT 'focus_utc_migrated', 'pending'
WHERE NOT EXISTS (SELECT 1 FROM schema_meta WHERE meta_key = 'focus_utc_migrated');

UPDATE focus_session
SET started_at = CONVERT_TZ(started_at, '+08:00', '+00:00'),
    ended_at = CONVERT_TZ(ended_at, '+08:00', '+00:00'),
    created_at = CONVERT_TZ(created_at, '+08:00', '+00:00')
WHERE (SELECT meta_value FROM schema_meta WHERE meta_key = 'focus_utc_migrated') = 'pending';

UPDATE schema_meta SET meta_value = 'migrated-to-utc' WHERE meta_key = 'focus_utc_migrated' AND meta_value = 'pending';
