-- auth 스키마: 회원 (Phase 3)
-- SET NAMES utf8mb4: 엔트리포인트 클라이언트 기본 charset이 latin1이라 항상 넣는다.
SET NAMES utf8mb4;

USE auth;

CREATE TABLE IF NOT EXISTS member (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  email         VARCHAR(255) NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,
  created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);
