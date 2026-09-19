# Phase 6. Stateful HA: MySQL Group Replication + 자동 페일오버 (핵심 학습)

## 목표
DB를 3벌로 이중화하고, PRIMARY가 죽으면 남은 노드가 새 PRIMARY를 뽑고 앱이 계속 쓰게 한다. 감시자를 밖에 두지 않고 합의를 DB 안(Group Replication)에 품는다(etcd 불필요).

## 환경 제약과 도구 선택 (GUIDE와 다른 점)
- 이 서버는 **arm64**. GUIDE가 지정한 **MySQL Shell / MySQL Router는 Oracle이 amd64로만** 배포한다(그대로 실행 시 exec format error). qemu 에뮬레이션은 쓰기 경로에서 느리고 합의 DB라 불안정 위험.
- 그래서 arm64 네이티브로: 3노드 GR은 `mysql:8.4`(GR 플러그인 내장)로 **SQL 수동 구성**, "라우터(단일 쓰기 문 + PRIMARY 자동 추적)"는 **ProxySQL**(arm64 네이티브, GR 인식)로 대체.
- GR 핵심(3노드 합의/과반/자동 페일오버)은 GUIDE와 동일. 바뀐 건 라우터 부품뿐.

## 구성
- `docker-compose.ha.yml` (기존 단일 mysql 스택과 별개, project `moveon-ha`): mysql-1/2/3(GR) + proxysql + concert 1대.
- `infra/ha/proxysql.cnf`: 3노드를 writer 후보 그룹에 등록 + `mysql_group_replication_hostgroups`로 GR 인식. 앱 호환 위해 ProxySQL도 **3306**으로 리슨(concert의 기존 JDBC 그대로).
- `infra/ha/bootstrap.sh`: GR 부트스트랩 + concert 스키마 + ProxySQL 계정 주입. 시크릿은 `.env`에서만.

## 실행
```bash
docker compose -f docker-compose.ha.yml -p moveon-ha up -d mysql-1 mysql-2 mysql-3 proxysql
./infra/ha/bootstrap.sh
docker compose -f docker-compose.ha.yml -p moveon-ha up -d concert   # 앱은 127.0.0.1:8090
```

## 완료 검증 (통과)
- 3노드 ONLINE, mysql-1 PRIMARY / mysql-2·3 SECONDARY(super_read_only, 쓰기 거부).
- ProxySQL이 PRIMARY만 writer 그룹(HG10)으로 분류. `:3306` 경유 쓰기가 PRIMARY로 가서 3노드에 복제됨.
- 앱(concert) 등록(POST /concerts)이 ProxySQL 경유로 정상 기록.
- **페일오버**: 쓰기를 계속 쏘며 PRIMARY(mysql-1) `docker kill` -> 남은 mysql-2·3가 과반으로 **mysql-3를 새 PRIMARY 선출** -> ProxySQL이 writer를 mysql-3로 자동 전환 -> 앱 50건 중 **49건 성공, 순간 1건만 실패**(잠깐 끊겼다 계속).
- **재합류**: 죽은 mysql-1을 되살려 `START GROUP_REPLICATION` -> SECONDARY로 복귀, 3노드 데이터 일치(각 54건).

## 핵심 함정 (하나라도 놓치면 클러스터가 안 뜬다)
1. **command는 반드시 YAML 리스트**로. 폴디드 문자열(`>`)이면 mysql 엔트리포인트가 DB 초기화를 건너뛰어 `mysql.user doesn't exist`로 죽는다.
2. **GR 옵션은 `--loose-` 접두어**. `--initialize` 단계엔 GR 플러그인이 로드 안 돼 `--group-replication-*`가 unknown variable로 초기화를 중단시킨다. `loose-`면 그 단계에선 무시, 정상 기동 땐 적용.
3. **조인 노드 GTID 리셋**. 각 노드가 초기화 때 자기 UUID로 GTID를 만들어, node2/3 합류 시 "그룹에 없는 트랜잭션 보유"로 거부된다. 합류 전 `RESET BINARY LOGS AND GTIDS`로 비운다.
4. **caching_sha2 복구**: `SET GLOBAL group_replication_recovery_get_public_key=ON`(안 하면 복구 채널 인증 실패).
5. **ProxySQL admin은 로컬 전용**: `admin` 계정은 컨테이너 안(127.0.0.1:6032)에서만. 다행히 proxysql 이미지에 mysql 클라이언트가 있어 exec로 설정.
6. init SQL/수동 INSERT에 한글이면 세션 맨 위 `SET NAMES utf8mb4`(안 하면 저장부터 깨짐).

## 주의 (GUIDE)
- **과반 규칙**: 3노드 중 2대가 살아야 쓰기가 된다. 1대만 남으면 쓰기가 멈추는 게 정상(스플릿 브레인 방지). 그래서 3노드가 최소 구성.
- 한 서버 안 3컨테이너라 진짜 구역 장애/네트워크 분리 실험은 반쪽이다.
- 이 데몬은 `docker kill`한 컨테이너를 restart 정책으로 되살리지 않는다(Phase 4 함정). 페일오버 검증엔 무관(죽은 노드 재기동은 `docker compose up -d <node>` 후 `START GROUP_REPLICATION`으로 수동 재합류).
