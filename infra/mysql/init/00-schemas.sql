-- 서비스별 스키마 생성 (concert 스키마는 01-schema.sql 이 만든다)
-- SET NAMES utf8mb4: 엔트리포인트 클라이언트 기본 charset이 latin1이라 넣어둔다.
SET NAMES utf8mb4;

CREATE DATABASE IF NOT EXISTS auth         CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS payment      CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS notification CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
