CREATE DATABASE IF NOT EXISTS focus_app
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE focus_app;

CREATE TABLE IF NOT EXISTS focus_session (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  client_id    CHAR(36)        NOT NULL COMMENT '客户端 UUID，幂等键',
  task_title   VARCHAR(100)    NOT NULL DEFAULT '' COMMENT '任务名，自由文本',
  note         VARCHAR(200)    NOT NULL DEFAULT '' COMMENT '专注事项',
  mode         VARCHAR(16)     NOT NULL COMMENT 'countup / countdown',
  planned_sec  INT             NULL COMMENT '倒计时预设秒数',
  started_at   DATETIME        NOT NULL,
  ended_at     DATETIME        NOT NULL,
  duration_sec INT             NOT NULL,
  paused_sec   INT             NOT NULL DEFAULT 0,
  status       VARCHAR(16)     NOT NULL COMMENT 'completed / aborted',
  session_date DATE            NOT NULL COMMENT '归属日，服务端按开始时间计算',
  created_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted_at   DATETIME        NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_session_client (client_id),
  KEY idx_session_date (session_date, deleted_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
