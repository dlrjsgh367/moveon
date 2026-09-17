# 무브온(MoveOn) 예매 MSA + MySQL HA 학습 프로젝트 가이드

> 이 문서는 Claude Code가 열어두고 단계별로 구현하기 위한 작업 가이드입니다.
> 사람(학습자)이 개념을 이해하며 직접 만드는 것이 목적이므로, 각 단계의 "완료 검증"을 반드시 통과한 뒤 다음 단계로 넘어갑니다.

---

## 0. Claude Code에게 (작업 규칙)

- **한 번에 다 만들지 않는다.** Phase 0부터 순서대로, 한 Phase가 끝나면 그 Phase의 "완료 검증"을 실제로 돌려 통과하는지 확인하고 멈춘다.
- **한 Phase = 한 브랜치 + 한 PR**(또는 최소한 한 커밋)로 남긴다.
- 설명이 필요한 결정(왜 이렇게 했는지)은 코드가 아니라 커밋 메시지나 PR 본문에 한두 줄로 남긴다.
- 막히면 임의로 우회하지 말고, 무엇이 안 되는지 기록하고 사람에게 확인을 받는다.
- 시크릿(DB 비밀번호, JWT 키 등)은 절대 코드나 커밋에 넣지 않는다. `.env`로 빼고 `.env`는 커밋하지 않는다.

### 환경 제약

- 서버: OCI 무료 인스턴스 1대, ARM64(aarch64), 4코어, 램 24GB
- 실행: Docker + Docker Compose
- **모든 컨테이너 이미지는 arm64를 지원해야 한다.** 자바 앱 이미지도 arm64로 빌드한다.
- 서버가 1대이므로 이중화(HA)는 "진짜 고가용성"이 아니라 **동작 원리를 배우는 실습**이다. 한 서버가 죽으면 전부 죽는다. 이 한계를 전제로 만든다.

---

## 1. 프로젝트 개요

소규모 공연 예매 시스템 **무브온(MoveOn)**을 마이크로서비스로 만든다. 손님이 로그인해서 공연을 고르고, 좌석을 선착순으로 예매하고, 결제하면 확정되고, 확정 알림을 받는다.

### 학습 목표

- 서비스를 데이터 주인 기준으로 나누기 (마이크로서비스 경계)
- 서비스 간 통신(동기 호출 + 비동기 이벤트)
- 초과 판매 방지(동시성, 로컬 트랜잭션)
- 여러 서비스에 걸친 작업의 인지(분산 트랜잭션, Saga)
- 무상태 이중화(로드밸런싱)와 상태 이중화(MySQL InnoDB Cluster, Redis Sentinel)

---

## 2. 아키텍처

### 서비스와 데이터 주인

| 서비스 | 소유 데이터(스키마) | 주요 기능 |
|---|---|---|
| auth-member | 회원, 인증 | 회원가입, 로그인(JWT 발급), 로그아웃 |
| concert | 공연, 좌석, 예매 | 공연 목록/상세, 공연 등록, 예매(좌석 선점 + 초과판매 방지) |
| payment | 결제 | 결제 요청 처리(초기엔 mock), 결제 상태 관리 |
| notification | 알림 발송 이력 | 예매 확정 이벤트 소비, 알림 발송(멱등) |

- **원칙: 각 서비스는 자기 스키마만 접근한다. 남의 DB를 직접 조회하지 않는다.** 필요하면 그 서비스의 API를 호출한다.
- 좌석 수와 예매는 같은 서비스(concert), 같은 트랜잭션 안에서 다뤄 초과 판매를 로컬 트랜잭션으로 막는다.

### 인프라 구성요소

- **API Gateway: Traefik** — 클라이언트 요청을 알맞은 서비스로 라우팅. 나중에 replica 자동 로드밸런싱에도 사용.
- **메시지 브로커: Redis Streams** — 예매 확정 같은 이벤트를 비동기로 전달. "반드시 한 번은 전달"과 소비자 그룹(XACK) 사용.
- **DB: MySQL 8** — 서비스별 스키마 분리. HA 단계에서 InnoDB Cluster로 확장.

### MVP 범위 (Must)

로그인, 회원가입, 공연 목록 조회, 공연 상세 조회, 공연 등록, 공연 예매, 결제(mock), 예매 확정 알림.
(내 예매 목록/상세, 예매 취소, 공연 수정/삭제, 실시간 판매 현황, 실제 결제사 연동은 이후로 미룬다.)

---

## 3. 기술 스택

- 언어/프레임워크: Java 21 + Spring Boot 3.x (서비스별 독립 애플리케이션)
- DB: MySQL 8.4 (arm64 이미지)
- 메시지 브로커/캐시: Redis 7 (Streams)
- Gateway/LB: Traefik v3
- 컨테이너: Docker + Docker Compose (개발/학습). 이후 k3s 확장 여지 있음.
- HA: MySQL InnoDB Cluster(Group Replication) + MySQL Router, Redis Sentinel
- 빌드: 각 서비스 멀티스테이지 Dockerfile, `--platform=linux/arm64`

> 사용 이미지 태그와 arm64 지원 여부는 각 단계에서 실제로 확인하고 고정한다.

---

## 4. GitHub 레포 생성 및 저장소 구성

### 4.1 레포 만들기 (gh CLI)

```bash
# gh CLI 로그인 (최초 1회)
gh auth login

# 작업 폴더 생성 후 그 안에서
mkdir moveon && cd moveon
git init

# 이 가이드 파일과 기본 파일들을 먼저 만든 뒤(4.2, 4.3) 첫 커밋
git add .
git commit -m "chore: 프로젝트 초기 구조와 가이드 추가"

# GitHub에 private 레포 생성 + 원격 연결 + 푸시
gh repo create moveon --private --source=. --remote=origin --push \
  --description "무브온(MoveOn) 예매 MSA + MySQL HA 학습 프로젝트"
```

> gh가 없으면 GitHub 웹에서 빈 레포를 만든 뒤 `git remote add origin <url>` 후 `git push -u origin main`로 대체한다.

### 4.2 폴더 구조 (모노레포)

```
moveon/
├── README.md
├── GUIDE.md                  # 이 문서
├── CLAUDE.md                 # Claude Code용 짧은 규칙 요약(4.4)
├── .gitignore
├── .env.example              # 환경변수 견본(실제 .env는 커밋 금지)
├── docker-compose.yml        # 개발용 전체 스택(단일 DB/Redis)
├── docker-compose.ha.yml     # 이중화 실험용(MySQL Cluster, Redis Sentinel)
├── gateway/
│   └── traefik/              # Traefik 설정
├── services/
│   ├── auth-member/
│   ├── concert/
│   ├── payment/
│   └── notification/
├── infra/
│   ├── mysql/                # 단일 MySQL 초기 스키마
│   ├── mysql-cluster/        # InnoDB Cluster 구성 스크립트
│   └── redis-sentinel/       # Redis + Sentinel 설정
└── docs/                     # 설계 메모, 다이어그램
```

각 `services/<name>/`에는 독립 Spring Boot 프로젝트(자체 `Dockerfile`, `build.gradle` 또는 `pom.xml`)를 둔다.

### 4.3 기본 파일

**.gitignore** (핵심 항목)

```
# secrets & env
.env
*.env.local

# java build
target/
build/
*.jar
!gradle/wrapper/*.jar

# ide
.idea/
*.iml
.vscode/

# logs & data
*.log
data/
volumes/
mysql-data/
redis-data/
```

**.env.example** (실제 값 없이 키만)

```
MYSQL_ROOT_PASSWORD=change-me
JWT_SECRET=change-me-long-random
REDIS_PASSWORD=change-me
```

**README.md**: 프로젝트 한 줄 소개, 실행 방법(`docker compose up`), 그리고 "상세 순서는 GUIDE.md 참고" 링크.

### 4.4 CLAUDE.md (짧은 규칙 요약)

Claude Code가 매번 참고하도록 레포 루트에 둔다. 내용은 이 가이드의 "0. 작업 규칙"과 "6. 공통 컨벤션"을 몇 줄로 압축.

### 4.5 브랜치와 커밋 규칙

- 기본 브랜치: `main`
- Phase마다 브랜치: `phase-1-vertical-slice`, `phase-6-mysql-ha` 처럼. 끝나면 PR로 `main`에 병합.
- 커밋 메시지: Conventional Commits 스타일. 예: `feat(concert): 좌석 선점과 초과판매 방지 추가`, `chore: docker-compose 뼈대 추가`, `fix(notification): 중복 발송 멱등 처리`.

---

## 5. 단계별 구현 계획

각 Phase는 목표 / 작업 / 완료 검증으로 구성된다. 검증을 통과해야 다음으로.

### Phase 0. 서버 준비

- 목표: Docker가 도는 깨끗한 서버.
- 작업: OCI 인스턴스에 Docker와 Docker Compose 설치. 보안 목록에서 80/443만 외부 개방. DB/Redis 포트는 외부에 열지 않는다.
- 완료 검증: `docker run hello-world`가 정상 출력.

### Phase 1. 가장 작은 왕복 한 줄기

- 목표: 자바 서비스 하나가 MySQL 하나에 붙어 API 하나를 응답.
- 작업: `docker-compose.yml`에 MySQL 1개 + concert 서비스 1개. concert에 "공연 목록 조회" API 하나 구현. 공연 몇 건을 초기 데이터로 심는다.
- 완료 검증: `curl http://localhost:8080/concerts` 로 공연 목록 JSON이 반환된다.

### Phase 2. MSA 뼈대 (이중화 없음)

- 목표: 서비스 4개가 각자 스키마로 뜨고, Gateway 한 주소로 라우팅.
- 작업:
  - 서비스 4개(auth-member, concert, payment, notification) 각각 컨테이너.
  - MySQL 1대 안에 스키마 4개(`auth`, `concert`, `payment`, `notification`). 서비스는 자기 스키마만 사용.
  - Traefik을 Gateway로 앞에 두고 경로 라우팅(예: `/auth/**` → auth-member, `/concerts/**` → concert).
  - Redis 1개(Streams용) 컨테이너 추가.
- 완료 검증: Gateway 한 주소로 각 서비스의 헬스/기본 API가 라우팅되어 응답한다.

### Phase 3. 핵심 흐름 구현 (여기까지가 MVP)

- 목표: 예매 한 건이 처음부터 끝까지 관통.
- 작업(동기 먼저, 알림은 그다음):
  1. auth-member: 회원가입, 로그인(JWT 발급). 다른 서비스는 JWT 서명을 **각자 로컬로 검증**한다(매번 auth 호출 금지).
  2. concert: 공연 목록/상세/등록. 예매 시 좌석을 선점하고, 예매 행에 만료시각(`expires_at`) 컬럼을 둔다. 초과 판매는 같은 스키마 안 로컬 트랜잭션으로 막는다(다른 사람이 같은 좌석을 동시에 잡으면 하나만 성공).
  3. payment: 결제 요청 처리(초기엔 성공을 가정하는 mock). 성공/실패 응답.
  4. concert: 결제 성공 응답을 받으면 예매를 확정하고, "예매 확정" 이벤트를 Redis Streams에 발행.
  5. notification: Streams를 소비(소비자 그룹)해서 알림 발송. 발송 성공 후 XACK. 같은 이벤트가 두 번 와도 한 번만 발송되도록 멱등키로 처리.
  6. 좌석 만료 반납: 잡는 시점에 만료된 선점은 덮어쓸 수 있게(lazy 방식) 처리. 별도 스케줄러 없이 로컬 트랜잭션 안에서 판단.
- 완료 검증(모두 통과해야 함):
  - 같은 좌석을 두 요청이 동시에 예매 → 하나만 성공, 다른 하나는 "이미 잡힌 좌석" 응답.
  - 결제 성공 → 예매 확정 + 알림 정확히 한 번.
  - 결제 실패 → 좌석 유지, 유저가 재시도 가능.
  - 10분(테스트에선 짧게) 지난 미결제 선점 좌석은 다른 사람이 잡을 수 있다.
- **이 지점에서 크게 커밋하고 태그(`v0.1-mvp`)를 남긴다.**

### Phase 4. 배포/운영 기본

- 목표: 컨테이너가 스스로 회복.
- 작업: 각 컨테이너에 헬스체크와 `restart: unless-stopped`. 로그를 간단히 확인 가능하게. 자바 이미지는 arm64로 빌드.
- 완료 검증: 컨테이너 하나를 `docker kill` 해도 자동으로 다시 뜬다.

### Phase 5. 무상태 이중화

- 목표: 상태 없는 API를 여러 개로 늘려 로드밸런싱.
- 작업: concert 서비스를 replica 2개로(`docker compose up --scale` 또는 compose에 replica 설정). Traefik이 두 replica로 자동 분배하고 헬스체크하게 라벨 설정.
- 완료 검증: replica 하나를 죽여도 요청이 다른 replica로 가서 서비스가 끊기지 않는다.

### Phase 6. Stateful HA: MySQL InnoDB Cluster (핵심 학습)

- 목표: DB를 이중화하고 자동 장애 넘김을 눈으로 확인. MySQL은 감시자를 밖에 두지 않고 Group Replication으로 합의를 DB 안에 품는다(etcd 불필요).
- 작업:
  - **6a (이해용, 선택):** MySQL 2개로 수동 주-복제를 한 번 걸어 복제가 흐르는 걸 확인. 원리 감 잡기용.
  - **6b (진짜 구성):** `docker-compose.ha.yml`에 MySQL 8 노드 3개(`mysql-1/2/3`)를 GTID/Group Replication 가능하게 띄운다. MySQL Shell로 클러스터 구성:

    ```bash
    mysqlsh --uri root@mysql-1:3306
    ```
    ```js
    // 각 노드 준비 (mysql-1, mysql-2, mysql-3 각각)
    dba.configureInstance('root@mysql-1:3306');
    dba.configureInstance('root@mysql-2:3306');
    dba.configureInstance('root@mysql-3:3306');

    // 클러스터 생성 후 나머지 합류
    var cluster = dba.createCluster('ticketCluster');
    cluster.addInstance('root@mysql-2:3306');
    cluster.addInstance('root@mysql-3:3306');
    cluster.status();   // 3노드, 하나가 PRIMARY 인지 확인
    ```

  - **MySQL Router**로 앱 접속을 단일화. 라우터를 클러스터에 bootstrap하면 쓰기 포트(기본 6446)와 읽기 포트(6447)가 생긴다. concert 서비스는 MySQL을 직접 보지 않고 **Router 주소(쓰기 6446)**로만 접속하게 전환.
- 완료 검증: PRIMARY인 MySQL 컨테이너를 `docker kill` → 남은 2노드가 과반으로 새 PRIMARY 선출 → Router가 자동으로 새 PRIMARY로 쓰기를 넘김 → 앱은 잠깐 끊겼다 계속된다(`cluster.status()`로 새 PRIMARY 확인).
- 주의:
  - 한 서버 안 3컨테이너라 진짜 구역 장애/네트워크 분리 실험은 반쪽이다.
  - 3노드 중 과반(2)이 살아야 쓰기가 된다. 1노드만 남으면 쓰기가 멈추는 게 정상(스플릿 브레인 방지).

### Phase 7. Redis 이중화 (Sentinel)

- 목표: MQ(Redis)의 자동 장애 넘김.
- 작업: `infra/redis-sentinel/`에 Redis master 1 + replica 1 + Sentinel 3. Sentinel은 `sentinel monitor mymaster <host> <port> 2`(정족수 2)로 설정. notification의 Redis 접속을 Sentinel을 통해 현재 master를 찾는 방식으로 바꾼다.
- 완료 검증: Redis master를 죽이면 Sentinel이 replica를 승격시키고, 알림 파이프라인이 계속 동작한다.

---

## 6. 공통 컨벤션

- **시크릿**: `.env`에만. 코드/커밋/이미지에 넣지 않는다. `.env.example`로 키 목록만 공유.
- **포트 정책**: 외부 개방은 Gateway(80/443)만. MySQL/Redis/Router는 컨테이너 네트워크 내부에서만 접근.
- **서비스 간 통신**: 답을 기다려야 하면 동기 호출(REST), 늦어도 되면 비동기 이벤트(Redis Streams). 남의 DB 직접 접근 금지.
- **멱등**: 이벤트 소비 쪽은 같은 메시지를 두 번 받아도 한 번만 처리하도록 메시지 id로 중복 제거.
- **커밋/브랜치**: 4.5 규칙을 따른다. Phase 단위로 PR.

---

## 7. 전체 완료 체크리스트

- [ ] Phase 0: `docker run hello-world` 정상
- [ ] Phase 1: 공연 목록 API 응답
- [ ] Phase 2: Gateway가 4개 서비스로 라우팅
- [ ] Phase 3: 초과판매 방지 / 결제 성공 시 확정+알림 1회 / 결제 실패 시 좌석 유지 / 만료 좌석 반납 (MVP)
- [ ] Phase 4: 컨테이너 kill 후 자동 재시작
- [ ] Phase 5: API replica 하나 죽어도 무중단
- [ ] Phase 6: MySQL PRIMARY kill 후 자동 페일오버, Router가 새 PRIMARY로 전환
- [ ] Phase 7: Redis master kill 후 Sentinel 승격, 알림 지속

---

## 8. 함정 모음 (미리 알아두기)

- **ARM64**: OCI 무료는 ARM이다. 자바 앱 이미지를 arm64로 빌드하고, 사용하는 공개 이미지가 arm64를 지원하는지 확인한다. 안 뜨면 대개 이 문제다.
- **한 서버 HA의 한계**: 컨테이너를 여러 개 띄워도 서버 1대가 죽으면 다 죽는다. 이건 진짜 고가용성이 아니라 메커니즘 실습이다.
- **과반 규칙**: MySQL InnoDB Cluster는 3노드 중 2노드가 살아야 쓰기가 된다. 1노드만 남으면 멈추는 게 정상이다.
- **JWT는 로컬 검증**: 다른 서비스가 매 요청마다 auth-member를 호출하지 않는다. 토큰 서명을 각자 검증한다.
- **분산 트랜잭션**: 결제 성공 후 확정 저장이 실패하면 돈은 나갔는데 예매가 없는 상태가 될 수 있다. MVP에선 인지만 하고, 이후 Saga(앞으로 밀기: 확정 재시도)로 다룬다.
- **Redis는 감시자가 아니다**: Redis는 이 프로젝트에서 MQ다. MySQL의 이중화 조율은 Group Replication이 담당한다. Redis 자신의 이중화는 Sentinel이 맡는다.

---

## 부록. 다음 확장 (MVP 이후)

- 예매 취소/환불, 내 예매 목록, 주최자 실시간 판매 현황
- 실제 결제사(PG) 연동
- Saga 패턴으로 결제-확정 정합성 보강
- Docker Compose에서 k3s로 이전, CI/CD 파이프라인
