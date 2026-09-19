#!/usr/bin/env bash
# Phase 6 HA 스택 부트스트랩: 3노드 Group Replication 구성 + concert 스키마 + ProxySQL 설정.
# 사용법(레포 루트에서):
#   docker compose -f docker-compose.ha.yml -p moveon-ha up -d mysql-1 mysql-2 mysql-3 proxysql
#   ./infra/ha/bootstrap.sh
#   docker compose -f docker-compose.ha.yml -p moveon-ha up -d concert
# 시크릿은 .env 에서만 읽는다(스크립트/커밋에 넣지 않음).
set -euo pipefail
cd "$(dirname "$0")/../.."
set -a; . ./.env; set +a
PW="$MYSQL_ROOT_PASSWORD"
P="docker compose -f docker-compose.ha.yml -p moveon-ha"

node() { $P exec -T "$1" mysql -uroot -p"$PW" -e "$2" 2>&1 | grep -v "Using a password" || true; }
padmin() { $P exec -T proxysql mysql -h127.0.0.1 -P6032 -uadmin -padmin -e "$1" 2>&1 | grep -v "Using a password" || true; }

echo "[1/5] 노드 healthy 대기"
for i in $(seq 1 40); do
  [ "$($P ps --format '{{.Health}}' | grep -c healthy)" -ge 3 ] && break; sleep 3
done

echo "[2/5] node1 부트스트랩"
node mysql-1 "SET GLOBAL group_replication_recovery_get_public_key=ON;
              SET GLOBAL group_replication_bootstrap_group=ON;
              START GROUP_REPLICATION USER='root', PASSWORD='$PW';
              SET GLOBAL group_replication_bootstrap_group=OFF;"
sleep 4

echo "[3/5] node2/3 합류 (초기화 때 생긴 자체 GTID 리셋 후)"
for n in mysql-2 mysql-3; do
  node "$n" "STOP GROUP_REPLICATION;
             RESET BINARY LOGS AND GTIDS;
             SET GLOBAL group_replication_recovery_get_public_key=ON;
             START GROUP_REPLICATION USER='root', PASSWORD='$PW';"
done
sleep 8
node mysql-1 "SELECT MEMBER_HOST,MEMBER_STATE,MEMBER_ROLE FROM performance_schema.replication_group_members ORDER BY MEMBER_HOST;"

echo "[4/5] concert 스키마 생성(PRIMARY에 -> 복제)"
$P exec -T mysql-1 mysql -uroot -p"$PW" 2>/dev/null <<'SQL' || true
SET NAMES utf8mb4;
CREATE DATABASE IF NOT EXISTS concert CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE concert;
CREATE TABLE IF NOT EXISTS concert (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  title VARCHAR(200) NOT NULL, venue VARCHAR(200) NOT NULL, perform_date DATE NOT NULL);
CREATE TABLE IF NOT EXISTS seat (
  id BIGINT AUTO_INCREMENT PRIMARY KEY, concert_id BIGINT NOT NULL, seat_no VARCHAR(10) NOT NULL,
  status VARCHAR(10) NOT NULL DEFAULT 'FREE', member_id BIGINT NULL, expires_at DATETIME NULL,
  UNIQUE KEY uq_seat (concert_id, seat_no));
INSERT INTO concert (title, venue, perform_date) VALUES
  ('무브온 단독 콘서트','올림픽공원 체조경기장','2026-11-01'),
  ('가을 재즈 페스티벌','LG아트센터','2026-10-15'),
  ('인디밴드 합동공연','홍대 롤링홀','2026-09-30');
SQL

echo "[5/5] ProxySQL 계정 주입(모니터/앱 user = root, 비번은 .env)"
padmin "SET mysql-monitor_username='root'; SET mysql-monitor_password='$PW';
        LOAD MYSQL VARIABLES TO RUNTIME; SAVE MYSQL VARIABLES TO DISK;"
padmin "DELETE FROM mysql_users;
        INSERT INTO mysql_users(username,password,default_hostgroup,active) VALUES('root','$PW',10,1);
        LOAD MYSQL USERS TO RUNTIME; SAVE MYSQL USERS TO DISK;"
sleep 10
echo "== ProxySQL writer(HG10)=PRIMARY =="
padmin "SELECT hostgroup_id,hostname,status FROM runtime_mysql_servers WHERE hostgroup_id=10;"
echo "부트스트랩 완료."
