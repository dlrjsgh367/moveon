# Phase 2. MSA 뼈대 (이중화 없음)

## 목표
서비스 4개가 각자 스키마로 뜨고, Traefik Gateway 한 주소(80)로 경로 라우팅.

## 만든 것
- 서비스 3개 추가(auth-member, payment, notification): concert와 같은 골격(Spring Boot 3.3.4 / Java 21, 멀티스테이지 Dockerfile). 각자 GET `/<도메인>/health` -> "ok".
- MySQL 스키마 4개(auth, concert, payment, notification). `infra/mysql/init/00-schemas.sql`이 auth/payment/notification 생성, 01이 concert. 각 서비스는 자기 스키마만 접속.
- Traefik v3.1 Gateway: 경로 라우팅(/concerts, /auth, /payment, /notification). 외부 개방은 80만, 대시보드 8080은 127.0.0.1만.
- Redis 7 컨테이너 추가(기동만, Streams는 Phase 3).
- concert의 8080 직접 노출 제거(Gateway 뒤로).

## 완료 검증 (통과)
- `curl http://localhost/concerts` 200 + 한글 정상, `/auth/health` `/payment/health` `/notification/health` 각각 200 "ok".
- 스키마 4개 존재 확인. 컨테이너 7개 Up.

## 겪은 함정: Traefik Docker provider가 최신 데몬에 접속 실패
- 증상: Gateway 경유 전부 404. Traefik 로그에 `client version 1.24 is too old. Minimum supported API version is 1.40`. 라우터가 하나도 등록 안 됨.
- 원인: 이 서버 Docker 데몬(API 1.54, min 1.40)이 오래된 API를 거부. Traefik v3.1의 Docker 클라이언트가 1.24로 접속하고 버전 협상을 안 함.
- 시도(실패): 컨테이너에 `DOCKER_API_VERSION=1.44` 주입. 환경변수는 들어갔으나 Traefik이 무시하고 계속 1.24로 접속.
- 해결: Docker provider(소켓 감시)를 버리고 파일 provider로 전환. `gateway/traefik/dynamic.yml`에 라우터/서비스를 정적으로 정의. docker.sock 마운트 제거(보안상 이점). 서비스 주소는 compose 서비스명(도커 내부 DNS)으로.
- 남은 과제: Phase 5(replica 자동 로드밸런싱)는 Docker provider의 자동 발견이 편하다. 그때 Traefik 버전 업그레이드로 Docker provider 재검토하거나, 파일 provider + 도커 DNS 라운드로빈으로 갈지 결정.

## 결정 메모
- 각 서비스가 자기 게이트웨이 접두어를 포함한 경로(`/auth/health` 등)를 직접 처리. StripPrefix 미들웨어를 안 써서 단순. concert의 `/concerts`와 일관.
- 스켈레톤 서비스도 data-jpa로 자기 스키마에 실제 접속(각자 스키마로 뜬다를 증명, Phase 3 준비).
