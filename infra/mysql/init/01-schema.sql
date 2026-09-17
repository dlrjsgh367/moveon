-- concert 서비스 스키마 (Phase 1: 공연 목록 조회만)
-- 임포트 세션 charset을 UTF-8로 고정한다(엔트리포인트 mysql 클라이언트 기본이 latin1이라 안 하면 한글이 이중 인코딩된다).
SET NAMES utf8mb4;

CREATE DATABASE IF NOT EXISTS concert CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE concert;

CREATE TABLE IF NOT EXISTS concert (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  title        VARCHAR(200) NOT NULL,
  venue        VARCHAR(200) NOT NULL,
  perform_date DATE NOT NULL
);

INSERT INTO concert (title, venue, perform_date) VALUES
  ('무브온 단독 콘서트', '올림픽공원 체조경기장', '2026-11-01'),
  ('가을 재즈 페스티벌', 'LG아트센터', '2026-10-15'),
  ('인디밴드 합동공연', '홍대 롤링홀', '2026-09-30');
