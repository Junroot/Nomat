# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

루트 `CLAUDE.md`도 함께 참조하세요.

## 구조

인프라 코드는 두 개의 Docker Compose 스택으로 분리되어 별도 노드에서 운영된다.

```
app/          — 앱 노드 (Docker Swarm 스택: spring-app, nginx, prometheus, grafana, kibana)
  compose.yml
  nginx.conf
  prometheus.yml
  kibana.yml
data/         — 데이터 노드 (단독 Docker Compose: MySQL, ES, Kafka, Redis, Logstash)
  compose.yml
  elasticsearch.yml
  logstash-config.yml
  logstash-pipeline.conf
prometheus/   — 커스텀 Prometheus 이미지 (환경변수로 타겟 IP 치환)
  Dockerfile
  entrypoint.sh
```

## 배포

- **앱 노드**: `infra/app/` 변경이 develop에 push되면 CI가 EC2에 SSH 접속하여 `docker stack deploy`로 배포
- **데이터 노드**: CI 자동 배포 없음 — 수동으로 `docker compose up -d` 실행
- CI 트리거 경로: `infra/app/**` 만 해당 (`infra/data/` 변경은 CI를 트리거하지 않음)

## 핵심 설계

### 네트워크

- **app 스택**: `backend` overlay 네트워크로 spring-app ↔ nginx 연결. `local-prometheus` overlay로 모니터링 서비스 격리
- **data 스택**: `elk` bridge 네트워크로 Elasticsearch ↔ Logstash 연결. 나머지 서비스는 포트 매핑으로 외부 노출

### Nginx

- `nomat-back_spring-app:8080`으로 리버스 프록시 (Swarm 서비스 디스커버리 사용)
- `/ws` 경로는 WebSocket 업그레이드 헤더 추가

### Nginx config 업데이트

Docker Swarm config는 수정 시 키 값을 변경해야 적용된다. `compose.yml`의 `nginx_conf-N` 숫자를 증가시켜야 함.

### Prometheus

커스텀 이미지(`prometheus/`)를 사용하여 `entrypoint.sh`에서 `${DATA_NODE_IP}` 환경변수를 sed로 치환한 후 실행. 앱 노드와 데이터 노드의 node-exporter를 모두 스크래핑.

### 환경변수

- **app 스택**: `.env` 파일을 EC2 홈 디렉토리에서 `infra/` 디렉토리로 복사하여 사용. DB, OAuth2, ES, Kafka, JWT, Redis 등 모든 시크릿을 환경변수로 주입
- **data 스택**: `HOST_IP` (Kafka advertised listener), `DB_*`, `ELASTIC_PASSWORD` 등 필요

### Spring App 배포 전략

- 2 replicas, rolling update (parallelism: 1, start-first) — 무중단 배포
- 헬스체크: `http://localhost:8080/health` (30초 간격, 60초 시작 대기)