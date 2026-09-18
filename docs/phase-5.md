# Phase 5. 무상태 이중화 (replica 로드밸런싱)

## 목표
상태 없는 concert API를 여러 개로 늘리고 Traefik이 자동 분배. replica 하나가 죽어도 무중단.

## 만든 것
- **concert replica 2개**: compose에 `deploy.replicas: 2`. `docker compose up -d`만으로 2대 기동(스케일 플래그 불필요). 상태는 전부 공유 MySQL/Redis에 있으므로 어느 replica가 받아도 동일하게 처리.
- **Traefik Docker provider + 라벨 자동발견**: concert에 라벨로 라우터/서비스/포트/헬스체크 지정. replica를 늘리거나 죽여도 Traefik이 컨테이너 이벤트로 자동 반영.
- **docker-socket-proxy(nginx)**: docker.sock 앞단에서 API 버전 접두어(`/v1.24` 등 -> `/v1.44`)를 재작성해 Traefik이 이 데몬에 붙게 함. `gateway/dockerproxy/nginx.conf`.
- file provider는 단일 서비스(auth/payment/notification) 라우팅으로 계속 사용. concert만 라벨로 옮기고 `dynamic.yml`에서 제거(중복 방지).
- Traefik `--accesslog=true`로 요청별 백엔드 확인 가능.

## 핵심: 왜 socket-proxy인가 (Phase 2 함정의 최종 해결)
- Traefik Docker provider는 이 데몬(API min 1.40)에 **1.24로 접속해 거부**당한다.
- 검증: Traefik v3.1~v3.5 전부 동일, `DOCKER_API_VERSION=1.40/1.44/1.51`도 무시(1.24 고정). Traefik엔 API 버전 지정 옵션 자체가 없음. **버전 업그레이드로는 해결 불가.**
- 해결: docker.sock 앞에 nginx를 두고 요청 경로의 버전 접두어를 데몬이 받는 `/v1.44/`로 치환. docker API는 상위 호환이라 정상 동작. Traefik은 `tcp://dockerproxy:2375`로만 접속(원시 소켓을 직접 안 만짐 = 격리 이점).

## 완료 검증 (통과)
- 두 replica가 Traefik에 등록되고 둘 다 `UP`. 요청 분배 확인(20회 -> 10/10, 30회 -> 15/15 라운드로빈).
- 무중단: 초당 ~10건 부하 중 replica 하나를 `docker kill` -> 총 150건 중 **149건 200**, kill 순간 in-flight 1건만 504. 이후 전부 생존 replica로. Traefik이 죽은 replica를 즉시 로테이션에서 제거(`serverStatus`에서 사라짐).
- 2대 LB 뒤에서 예매->결제->확정 엔드투엔드 정상.

## 결정/함정 메모
- **버전 업 무효**가 이번 핵심 발견. 프록시 경유가 이 환경의 유일한 Docker provider 경로.
- nginx 프록시는 워커를 root로 돌려 소켓(root 소유)에 접근한다. 이 프록시는 Traefik에 사실상 전체 docker API를 열어주므로, 운영이라면 읽기 전용 엔드포인트로 제한하는 게 맞다(학습용이라 단순 통과).
- replica 죽여도 자동 재기동은 Phase 4 함정과 동일(이 데몬은 `docker kill`을 재시작 안 함). 단 여기 목표는 "죽어도 다른 replica로 무중단"이라 재기동과 무관하게 충족.
- concert는 무상태라 replica 안전. auth/payment/notification도 무상태지만 이번엔 concert만 이중화(GUIDE 범위).
