CREATE DATABASE IF NOT EXISTS focus_app
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE focus_app;

-- 在创建表前记录是否为旧库，避免旧数据被误标为已完成 UTC 迁移。
SET @focus_session_existed = (
  SELECT COUNT(*) FROM information_schema.TABLES
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'focus_session'
);

CREATE TABLE IF NOT EXISTS schema_meta (
  meta_key VARCHAR(64) NOT NULL PRIMARY KEY,
  meta_value VARCHAR(255) NOT NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS app_user (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  username VARCHAR(50) NOT NULL,
  password_hash VARCHAR(100) NOT NULL,
  display_name VARCHAR(50) NOT NULL,
  timezone VARCHAR(64) NOT NULL DEFAULT 'Asia/Shanghai',
  ics_token CHAR(36) NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_username (username),
  UNIQUE KEY uk_user_ics_token (ics_token)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS task_list (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id BIGINT UNSIGNED NOT NULL,
  name VARCHAR(100) NOT NULL,
  color CHAR(7) NOT NULL DEFAULT '#3b6ef6',
  sort_order INT NOT NULL DEFAULT 0,
  archived TINYINT(1) NOT NULL DEFAULT 0,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  KEY idx_list_user (user_id, archived, sort_order),
  CONSTRAINT fk_list_user FOREIGN KEY (user_id) REFERENCES app_user (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS task (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id BIGINT UNSIGNED NOT NULL,
  list_id BIGINT UNSIGNED NULL,
  title VARCHAR(200) NOT NULL,
  note TEXT NULL,
  completed TINYINT(1) NOT NULL DEFAULT 0,
  completed_at DATETIME(6) NULL,
  version INT NOT NULL DEFAULT 1,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  deleted_at DATETIME(6) NULL,
  PRIMARY KEY (id),
  KEY idx_task_user_list (user_id, list_id, deleted_at),
  CONSTRAINT fk_task_user FOREIGN KEY (user_id) REFERENCES app_user (id),
  CONSTRAINT fk_task_list FOREIGN KEY (list_id) REFERENCES task_list (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS habit (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id BIGINT UNSIGNED NOT NULL,
  name VARCHAR(100) NOT NULL,
  color CHAR(7) NOT NULL DEFAULT '#22a06b',
  archived TINYINT(1) NOT NULL DEFAULT 0,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  KEY idx_habit_user (user_id, archived),
  CONSTRAINT fk_habit_user FOREIGN KEY (user_id) REFERENCES app_user (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS calendar_subscription (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id BIGINT UNSIGNED NOT NULL,
  url TEXT NOT NULL,
  name VARCHAR(100) NOT NULL,
  color CHAR(7) NOT NULL DEFAULT '#7c6cf7',
  enabled TINYINT(1) NOT NULL DEFAULT 1,
  etag VARCHAR(255) NULL,
  last_modified VARCHAR(255) NULL,
  last_refreshed_at DATETIME(6) NULL,
  last_error VARCHAR(500) NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  KEY idx_subscription_user (user_id, enabled),
  CONSTRAINT fk_subscription_user FOREIGN KEY (user_id) REFERENCES app_user (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS calendar_event (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id BIGINT UNSIGNED NOT NULL,
  kind VARCHAR(20) NOT NULL COMMENT 'task/habit/anniversary/course/ics',
  task_id BIGINT UNSIGNED NULL,
  habit_id BIGINT UNSIGNED NULL,
  list_id BIGINT UNSIGNED NULL,
  subscription_id BIGINT UNSIGNED NULL,
  title VARCHAR(200) NOT NULL,
  note TEXT NULL,
  start_at DATETIME(6) NOT NULL COMMENT 'UTC',
  end_at DATETIME(6) NOT NULL COMMENT 'UTC',
  all_day TINYINT(1) NOT NULL DEFAULT 0,
  timezone VARCHAR(64) NOT NULL DEFAULT 'Asia/Shanghai',
  rrule TEXT NULL,
  color CHAR(7) NULL,
  external_uid VARCHAR(512) NULL,
  recurrence_id VARCHAR(128) NOT NULL DEFAULT '',
  version INT NOT NULL DEFAULT 1,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  deleted_at DATETIME(6) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_ics_uid (subscription_id, external_uid(191), recurrence_id),
  KEY idx_event_range (user_id, start_at, end_at, deleted_at),
  KEY idx_event_task (task_id, deleted_at),
  CONSTRAINT fk_event_user FOREIGN KEY (user_id) REFERENCES app_user (id),
  CONSTRAINT fk_event_task FOREIGN KEY (task_id) REFERENCES task (id),
  CONSTRAINT fk_event_habit FOREIGN KEY (habit_id) REFERENCES habit (id),
  CONSTRAINT fk_event_list FOREIGN KEY (list_id) REFERENCES task_list (id),
  CONSTRAINT fk_event_subscription FOREIGN KEY (subscription_id) REFERENCES calendar_subscription (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS focus_preset (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id BIGINT UNSIGNED NOT NULL,
  name VARCHAR(100) NOT NULL,
  mode VARCHAR(16) NOT NULL,
  planned_sec INT NULL,
  task_id BIGINT UNSIGNED NULL,
  habit_id BIGINT UNSIGNED NULL,
  note_template TEXT NULL,
  sort_order INT NOT NULL DEFAULT 0,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  KEY idx_preset_user (user_id, sort_order),
  CONSTRAINT fk_preset_user FOREIGN KEY (user_id) REFERENCES app_user (id),
  CONSTRAINT fk_preset_task FOREIGN KEY (task_id) REFERENCES task (id),
  CONSTRAINT fk_preset_habit FOREIGN KEY (habit_id) REFERENCES habit (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS user_setting (
  user_id BIGINT UNSIGNED NOT NULL,
  settings_json JSON NOT NULL,
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (user_id),
  CONSTRAINT fk_setting_user FOREIGN KEY (user_id) REFERENCES app_user (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS focus_session (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id BIGINT UNSIGNED NULL COMMENT '旧数据首次初始化账号时回填',
  client_id CHAR(36) NOT NULL COMMENT '客户端 UUID，用户内幂等键',
  task_id BIGINT UNSIGNED NULL,
  habit_id BIGINT UNSIGNED NULL,
  task_title VARCHAR(200) NOT NULL DEFAULT '' COMMENT '历史任务名与统计回退',
  note TEXT NOT NULL,
  mode VARCHAR(16) NOT NULL COMMENT 'countup/countdown',
  planned_sec INT NULL,
  started_at DATETIME(6) NOT NULL COMMENT 'UTC',
  ended_at DATETIME(6) NOT NULL COMMENT 'UTC',
  duration_sec INT NOT NULL,
  paused_sec INT NOT NULL DEFAULT 0,
  status VARCHAR(16) NOT NULL COMMENT 'completed/aborted',
  source VARCHAR(16) NOT NULL DEFAULT 'timer' COMMENT 'timer/manual',
  session_date DATE NOT NULL COMMENT '用户默认时区下的归属日',
  version INT NOT NULL DEFAULT 1,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  deleted_at DATETIME(6) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_session_user_client (user_id, client_id),
  KEY idx_session_user_date (user_id, session_date, deleted_at),
  KEY idx_session_task (task_id, deleted_at),
  KEY idx_session_habit (habit_id, deleted_at),
  CONSTRAINT fk_session_user FOREIGN KEY (user_id) REFERENCES app_user (id),
  CONSTRAINT fk_session_task FOREIGN KEY (task_id) REFERENCES task (id),
  CONSTRAINT fk_session_habit FOREIGN KEY (habit_id) REFERENCES habit (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

INSERT INTO schema_meta (meta_key, meta_value)
VALUES ('focus_utc_migrated', IF(@focus_session_existed = 0, 'fresh-schema', 'pending'))
ON DUPLICATE KEY UPDATE meta_value = meta_value;
