# Phase 1. 가장 작은 왕복 한 줄기

## 목표
자바 서비스 하나(concert)가 MySQL 하나에 붙어 `/concerts` API를 응답.

## 만든 것
- `docker-compose.yml`: MySQL 8.4 + concert 서비스. MySQL은 외부 포트를 열지 않고(내부 네트워크만), concert만 8080 노출.
- `infra/mysql/init/01-schema.sql`: concert 스키마 + concert 테이블 + 공연 3건 초기 데이터.
- `services/concert/`: Spring Boot 3.3.4 + Java 21. 엔티티(Concert) + JpaRepository + `GET /concerts` 컨트롤러.
- Dockerfile 멀티스테이지(gradle 이미지로 빌드 -> temurin jre로 실행). arm64 호스트라 자동 arm64 빌드.

## 완료 검증 (통과)
- `curl http://localhost:8080/concerts` -> 200, 공연 3건 JSON.
- 한글 정상: 저장 바이트 HEX가 `EBACB4...`("무"의 정상 UTF-8).

## 겪은 함정: 한글 이중 인코딩 (double encoding)
- 증상: API 응답의 한글이 `ë¬´ë¸Œì˜¨`처럼 깨짐. DB 저장 바이트가 `C3ABC2AC...`(정상값을 latin1로 오해해 UTF-8로 재인코딩한 값).
- 원인: MySQL 도커 엔트리포인트가 init `.sql`(실제 UTF-8)을 임포트할 때 클라이언트 세션 기본 charset이 `latin1`(character_set_client/connection/results). UTF-8 바이트를 latin1로 읽어 utf8mb4로 저장 -> 이중 인코딩.
- 해결: init SQL 맨 위에 `SET NAMES utf8mb4;` 추가. init 스크립트는 데이터 볼륨이 비었을 때만 실행되므로 `docker compose down -v` 후 재기동해 다시 심음.
- 교훈: DB 자체 charset(utf8mb4)이 맞아도 "임포트하는 연결"의 charset이 다르면 깨진다. 앞으로 서비스 스키마 init SQL엔 `SET NAMES utf8mb4;`를 기본으로 넣는다.

## 결정 메모
- DTO 없이 엔티티를 직접 반환(학습용 최소 구현). 필드 노출을 통제해야 할 때 DTO 분리.
- ddl-auto=none. 스키마는 앱이 아니라 init SQL이 소유(가이드의 infra/mysql 방향).
