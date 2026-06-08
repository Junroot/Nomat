# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

루트 `CLAUDE.md`도 함께 참조하세요.

## 구조

인프라 코드는 두 개의 Docker Compose 스택으로 분리되어 별도 노드에서 운영된다.

```
app/          — 앱 노드 (Docker Swarm 스택: spring-app, nginx, alloy, node-exporter)
  compose.yml
  nginx.conf
  alloy-config.alloy
data/         — 데이터 노드 (단독 Docker Compose: MySQL, ES, Redis, alloy, node-exporter)
  compose.yml
  elasticsearch.yml
  alloy-config.alloy
grafana/      — Grafana Cloud 관측 자산 export 스냅샷 (UI가 원천, git은 스냅샷)
  dashboards/   spring-app·node-exporter·로그 대시보드 JSON
  alerting/     알림 규칙 그룹 export
  README.md     관측 자산 컨벤션 + 알림 카탈로그(규칙↔패널 미러링)
```

로그·메트릭은 Grafana Cloud로 보내고(아래 참조), 운영자는 Grafana UI에서 조회한다. 알림은 대시보드 패널을 미러링해 구성하며, 대시보드·알림 규칙은 `grafana/`에 export 스냅샷으로 버전 관리한다. 자세한 컨벤션·알림 카탈로그는 `grafana/README.md` 참조.

로그·메트릭은 self-hosted 백엔드(Kibana/Logstash/Prometheus/Grafana) 없이 **Grafana Cloud**로 전송한다. 각 노드의 **Grafana Alloy** 에이전트가 Docker socket으로 컨테이너 stdout을 수집해 Loki로, node-exporter를 scrape해 Mimir로 push한다. app 노드에서는 Alloy가 추가로 `spring-app`을 backend 네트워크에서 replica별로 scrape해 앱(JVM/Spring) 메트릭도 Mimir로 push한다. 운영자는 Grafana Cloud의 Grafana UI에서 조회한다.

## 배포

- **앱 노드**: `infra/app/` 변경이 develop에 push되면 CI가 EC2에 SSH 접속하여 `docker stack deploy`로 배포
- **데이터 노드**: CI 자동 배포 없음 — 수동으로 `docker compose up -d` 실행
- CI 트리거 경로: `infra/app/**` 만 해당 (`infra/data/` 변경은 CI를 트리거하지 않음)

## 핵심 설계

### 네트워크

- **app 스택**: `backend` overlay 네트워크로 spring-app ↔ nginx 연결. `observability` overlay로 alloy ↔ node-exporter scrape. 앱 메트릭 scrape를 위해 **alloy를 `backend`에도 합류**시켜 격벽을 **Alloy 한 방향으로 부분 개방**한다(`alloy.networks = [observability, backend]`). `spring-app`은 `observability`를 알지 못하므로 역방향 격리는 유지된다(앱 컨테이너가 침해돼도 관측망 도달 불가)
- **data 스택**: `observability` bridge 네트워크로 alloy ↔ node-exporter 연결. Elasticsearch 등 나머지 서비스는 포트 매핑으로 외부 노출

### Nginx

- `nomat-back_spring-app:8080`으로 리버스 프록시 (Swarm 서비스 디스커버리 사용)
- `/ws` 경로는 WebSocket 업그레이드 헤더 추가
- **actuator 관리 포트(8081) allow-list**: dev에서 actuator는 전용 관리 포트(8081)로 분리된다. nginx는 `upstream backend_mgmt`(`:8081`)로 `location = /info`만 공개 reverse proxy한다(버전 확인 통로). `/health`는 컨테이너 내부 healthcheck(`localhost:8081`) 전용, `/prometheus`는 Alloy scrape 전용 — 둘 다 nginx 미프록시이므로 공개 도달 불가(default-closed)

### Swarm config 업데이트 (Nginx / Alloy)

Docker Swarm config는 수정 시 키 값을 변경해야 적용된다. app 스택은 nginx와 alloy 설정을 Swarm config로 둔다 — `nginx.conf`를 고치면 `compose.yml`의 `nginx_conf-N` 숫자를, `alloy-config.alloy`를 고치면 `alloy_config-N` 숫자를 각각 증가시켜야 적용된다. (data 스택의 alloy는 단독 compose라 bind mount로 직접 반영되므로 키 증가 불필요.)

### Grafana Cloud + Alloy

메트릭·로그는 self-hosted 백엔드 없이 Grafana Cloud로 push한다. 커스텀 Prometheus 이미지 빌드 단계는 제거됐다.

- 각 노드의 **Alloy** 에이전트가 Docker socket(`/var/run/docker.sock:ro`)으로 컨테이너 stdout을 tail → Grafana Cloud **Loki**로 전송. `discovery.docker`로 컨테이너 메타를 라벨링하고, spring-app JSON 로그의 `logType`/`level`만 라벨로 승격한다 (`requestId` 등 high-cardinality는 line content로 보존).
- Alloy가 같은 노드의 `node-exporter:9100`을 scrape → Grafana Cloud **Mimir**로 remote_write. 메트릭에는 `node=app|data` external label이 붙는다.
- **app 노드 앱 메트릭**: Alloy가 `backend` 네트워크에서 `discovery.docker`로 로컬 컨테이너를 열거하고 swarm service 라벨(`nomat-back_spring-app`)로 keep → replica별 타깃(컨테이너 이름을 `instance` 라벨로)으로 `<ip>:8081/prometheus`를 scrape. `prometheus.relabel "app_allowlist"`가 `__name__` 화이트리스트(`jvm_*`·`process_*`·`system_*`·`http_server_requests*`·`hikaricp_*`·`logback_events*`·`tomcat_*`)로 송신을 제한해 Mimir 10k active series 예산을 하드캡한다. replica별 `instance`로 두 replica를 개별 식별한다(`jvm_memory_used_bytes{node="app"}`).
- app 노드 Alloy는 `deploy.mode: global`(Swarm 노드당 1개), data 노드 Alloy는 단독 compose 컨테이너.

### 환경변수

- **app 스택**: `.env` 파일을 EC2 홈 디렉토리에서 `infra/` 디렉토리로 복사하여 사용. DB, OAuth2, ES, JWT, Redis 등 모든 시크릿을 환경변수로 주입
- **data 스택**: `DB_*`, `ELASTIC_PASSWORD` 등 필요
- **Grafana Cloud (양쪽 스택 alloy)**: `GRAFANA_CLOUD_LOKI_URL`(예: `https://logs-prod-***.grafana.net/loki/api/v1/push`), `GRAFANA_CLOUD_LOKI_USER`(인스턴스 ID), `GRAFANA_CLOUD_MIMIR_URL`(예: `https://prometheus-prod-**.grafana.net/api/prom/push`), `GRAFANA_CLOUD_MIMIR_USER`(인스턴스 ID), `GRAFANA_CLOUD_API_TOKEN`(Loki/Mimir write scope 공용 토큰). 값은 저장소에 commit하지 않고 GitHub Secrets·EC2 `.env`로만 주입

### Spring App 배포 전략

- 2 replicas, rolling update (parallelism: 1, start-first) — 무중단 배포
- 헬스체크: `http://localhost:8081/health` (관리 포트, 컨테이너 내부 직접 호출 — nginx 우회. 30초 간격, 60초 시작 대기)