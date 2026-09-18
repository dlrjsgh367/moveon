-- payment 스키마: 결제 기록 (Phase 3, mock)
SET NAMES utf8mb4;

USE payment;

CREATE TABLE IF NOT EXISTS payment (
  id             BIGINT AUTO_INCREMENT PRIMARY KEY,
  reservation_id BIGINT NOT NULL,
  amount         INT NOT NULL,
  status         VARCHAR(10) NOT NULL,  -- SUCCESS / FAIL
  created_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);
