# Phase 3. 핵심 흐름 구현 (MVP)

## 목표
예매 한 건이 로그인부터 알림까지 관통. 초과판매 방지 / 결제 성공 시 확정+알림 1회 / 결제 실패 시 좌석 유지 / 만료 좌석 재선점.

## 만든 것
- **auth-member**: 회원가입(`POST /auth/signup`, BCrypt 해시), 로그인(`POST /auth/login`, HS256 JWT 발급). 토큰 subject에 memberId.
- **concert**:
  - 공연 목록(`GET /concerts`), 상세(`GET /concerts/{id}`), 좌석 상태(`GET /concerts/{id}/seats`), 등록(`POST /concerts`).
  - 예매 선점(`POST /concerts/{id}/reservations`, 인증 필수): 좌석을 HELD로 잡고 `expires_at` 설정.
  - 결제+확정(`POST /concerts/reservations/{id}/pay`): payment 동기 호출 -> 성공이면 CONFIRMED + 이벤트 발행, 실패면 HELD 유지.
  - JWT를 auth와 같은 시크릿으로 로컬 검증(`JwtVerifier`). 매 요청 auth 호출 안 함.
- **payment**: 결제 mock(`POST /payment`). 기본 성공, `outcome=FAIL`이면 실패 응답. 기록 저장.
- **notification**: `reservation-confirmed` 스트림을 소비자 그룹으로 소비 -> 멱등 발송 -> XACK.
- 스키마: `infra/mysql/init/02~05.sql` (auth.member / concert.seat+시드15 / payment.payment / notification.processed_event+notification).

## 핵심 설계

### 초과판매 방지 + 만료 재선점 (한 문장으로)
좌석 선점은 조건부 UPDATE 하나로 처리(`SeatRepository.hold`):
```sql
UPDATE seat SET status='HELD', member_id=?, expires_at=?
 WHERE concert_id=? AND seat_no=?
   AND (status='FREE' OR (status='HELD' AND expires_at < NOW()))
```
- 동시에 여러 요청이 같은 좌석을 노려도 MySQL 행 잠금으로 직렬화되어 **영향행이 1인 요청만 성공**, 나머지는 0(이미 잡힘 -> 409). `uq_seat(concert_id, seat_no)` 유니크 인덱스가 정확히 한 행만 잠그게 한다.
- 만료 재선점(lazy)도 같은 WHERE의 `status='HELD' AND expires_at < NOW()` 로 함께 처리. **별도 스케줄러 없음** (잡는 시점에 판단).
- 확정도 같은 방식(`confirm`): 본인의 유효 HELD만 CONFIRMED로.

### JWT 로컬 검증
auth가 발급, 다른 서비스는 같은 `JWT_SECRET`으로 서명만 로컬 검증(`Jwts.parser().verifyWith(key)`). 서비스 간 인증 호출이 없어 결합도/지연이 낮다.

### 이벤트 + 멱등
- concert가 확정 시 `reservation-confirmed` 스트림에 발행. `eventId="confirm:<예매id>"` (예매당 고정).
- notification은 소비자 그룹(`notification`)으로 소비. `processed_event.event_id`(PK)에 insert 시도 -> 이미 있으면 발송 건너뜀. **같은 이벤트가 두 번 와도 발송 1회**.
- 처리(발송 또는 중복확인) 후 수동 XACK. 예외 시 미ack -> 재전달(at-least-once + 소비 측 멱등 = effectively-once).

## 완료 검증 (깨끗한 볼륨에서 4개 모두 통과)
1. 같은 좌석 동시 8건 요청 -> **201 x1 / 409 x7**.
2. 결제 성공 -> 좌석 CONFIRMED + 알림 1건. 같은 eventId 중복 투입해도 알림 여전히 1건, PENDING=0.
3. 결제 실패 -> 좌석 HELD 유지. 같은 예매로 재결제 성공 -> CONFIRMED.
4. 선점 TTL(테스트 5초) 만료 후 다른 회원이 같은 좌석 재선점 성공(소유자 변경). 만료 전 시도는 409.

## 설정/운영 메모
- concert 환경변수: `JWT_SECRET`, `REDIS_HOST/PASSWORD`, `HOLD_TTL_SECONDS`(기본 600, `${HOLD_TTL_SECONDS:-600}`로 오버라이드 가능), `PAYMENT_URL`(내부 DNS `http://payment:8080`).
- 만료 짧게 테스트: `HOLD_TTL_SECONDS=5 docker compose up -d --no-deps concert` 후 검증, 끝나면 `docker compose up -d --no-deps concert`로 기본 복귀.
- 스키마 바꾸면 `docker compose down -v && up -d`로 재초기화(init SQL은 빈 볼륨 최초 1회만). 새 init SQL 맨 위 `SET NAMES utf8mb4;` 필수.
- 한글은 utf8mb4로 정상 저장됨. `mysql` 클라이언트로 조회 시 `--default-character-set=utf8mb4`를 줘야 화면에 안 깨져 보인다(저장 자체는 정상).

## 남긴 단순화 (ponytail)
- 좌석 = 예매 행(별도 reservation 테이블 없음). reservationId = seat.id.
- 새로 등록한 공연은 좌석 시드가 없어 예매 대상 아님(예매 검증은 시드 공연 1~3으로).
- 결제는 mock(실제 PG 없음), 결제 실패는 `outcome=FAIL`로 유도.
- 자동화 테스트 대신 curl 통합 검증(프로젝트 컨벤션).
