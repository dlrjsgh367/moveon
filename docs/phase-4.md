# Phase 4. 배포/운영 기본 (자가 회복)

## 목표
컨테이너가 죽으면 스스로 다시 뜬다. 각 컨테이너 상태를 헬스체크로 관찰 가능하게.

## 만든 것 (docker-compose.yml만 수정)
- 7개 컨테이너 전부 `restart: unless-stopped` (명시적 중지/데몬 재시작이 아니면 죽어도 자동 재시작).
- 헬스체크 추가:
  - 자바 4개(concert/auth-member/payment/notification): `wget -qO- http://localhost:8080/<health경로>`. 이미지(temurin 21-jre, Ubuntu)에 wget/curl 내장이라 설치 불필요. 자바 부팅이 느려 `start_period: 40s`.
  - traefik: `--ping=true` 켜고 `traefik healthcheck --ping`.
  - redis: `redis-cli --no-auth-warning -a $REDIS_PASSWORD ping`. 헬스체크에서 쓰려고 `REDIS_PASSWORD`를 컨테이너 env로도 전달.
  - mysql: 기존 `mysqladmin ping` 유지.
- arm64 빌드: 호스트가 arm64라 멀티스테이지 빌드가 자동으로 네이티브 arm64 이미지 생성(이미 충족).

## 완료 검증
- 전 컨테이너 `health=healthy`, `restart=unless-stopped` 확인.
- 자가 회복: concert의 자바 프로세스를 호스트에서 강제 종료(크래시 모사) -> 데몬이 자동 재시작(RestartCount 0->1) -> 약 12초 뒤 `GET /concerts` 200 복구.

## 겪은 함정: 이 데몬은 `docker kill`을 재시작하지 않는다 (근본 원인 규명)
- 증상: `docker kill <컨테이너>` 하면 `exited(137)`로 남고 restart 정책이 안 먹는다(RestartCount=0).
- 통제 실험(정책 x 종료방식 매트릭스, redis 이미지):
  - `always` / `on-failure` / `unless-stopped` 무관하게, `docker kill`(SIGKILL)도 `docker kill --signal=SIGTERM`도 `docker stop`도 전부 재시작 안 됨(RC=0).
  - 반대로 프로세스가 스스로 죽으면(컨테이너 안 `exit 1`) `always`가 RC 1,2,3... 증가. 호스트에서 `sudo kill -9 <pid>` 한 경우도 재시작됨(RC 0->1).
- `docker events`로 분류 차이 확인:
  - `docker kill`: `create, start, kill, die` -> 이후 없음(재시작 X).
  - 호스트 프로세스 kill: `create, start, die, start` -> 자동 재기동(재시작 O).
- **근본 원인**: 데몬이 자기가 개시한 종료(`docker kill`/`docker stop`은 `kill` 이벤트를 남긴다)를 "의도된 종료"로 표시해 restart 정책을 건너뛴다. 데몬이 개시하지 않은 예상 밖 종료(크래시/OOM/외부 시그널)만 `die`로 보고 재시작한다. 이 Docker(29.5.2) 고유 동작(고전 Docker는 `docker kill` 시 always/unless-stopped를 재시작함).
- **고칠 것 없음**: compose/Dockerfile 옵션으로 바꿀 수 있는 게 아니다(데몬 동작). 실제 장애 시나리오(앱 크래시로 프로세스 사망)는 정상 회복되므로 설정은 표준적으로 올바르다. `docker kill`로 직접 확인하려면 다른 Docker 데몬/버전이 필요하다.
- 결론: 자가 회복 목표(앱이 죽으면 다시 뜬다)는 달성. GUIDE 검증 문구가 지목한 `docker kill` 명령 자체만 이 환경에서 특수하게 동작한다.

## 로그 확인
- 전체: `docker compose logs -f`
- 서비스별: `docker compose logs -f concert` (auth-member/payment/notification/traefik/mysql/redis 동일)
- 상태 한눈에: `docker compose ps` (STATUS 열에 healthy 표시)
