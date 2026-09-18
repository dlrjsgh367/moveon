-- concert 스키마: 좌석 재고 겸 선점(예매) 행 (Phase 3)
-- 좌석 하나가 곧 예매 대상이다. status 로 FREE/HELD/CONFIRMED 를 표현하고,
-- HELD 는 expires_at 까지만 유효(지나면 다른 사람이 재선점 가능 = lazy 만료).
SET NAMES utf8mb4;

USE concert;

CREATE TABLE IF NOT EXISTS seat (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  concert_id BIGINT NOT NULL,
  seat_no    VARCHAR(10) NOT NULL,
  status     VARCHAR(10) NOT NULL DEFAULT 'FREE',  -- FREE / HELD / CONFIRMED
  member_id  BIGINT NULL,
  expires_at DATETIME NULL,
  UNIQUE KEY uq_seat (concert_id, seat_no)
);

-- 공연 3건(01-schema.sql seed)에 좌석 A1~A5 씩
INSERT INTO seat (concert_id, seat_no) VALUES
  (1,'A1'),(1,'A2'),(1,'A3'),(1,'A4'),(1,'A5'),
  (2,'A1'),(2,'A2'),(2,'A3'),(2,'A4'),(2,'A5'),
  (3,'A1'),(3,'A2'),(3,'A3'),(3,'A4'),(3,'A5');
