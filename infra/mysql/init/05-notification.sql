-- notification 스키마: 발송 로그 + 멱등 처리기록 (Phase 3)
-- processed_event: 이미 처리한 이벤트 id 를 PK 로 두어 같은 이벤트 두 번 와도 한 번만 발송.
SET NAMES utf8mb4;

USE notification;

CREATE TABLE IF NOT EXISTS processed_event (
  event_id     VARCHAR(100) PRIMARY KEY,
  processed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS notification (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  event_id   VARCHAR(100) NOT NULL,
  member_id  BIGINT NOT NULL,
  message    VARCHAR(500) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);
