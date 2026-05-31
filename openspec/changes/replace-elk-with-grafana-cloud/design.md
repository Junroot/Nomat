## Context

### 현재 observability 스택

```
[APP NODE — Swarm]                          [DATA NODE — compose]
  spring-app x2  ──app log TCP─┐              MySQL
   logback-spring.xml         │              Elasticsearch (Nori, 검색 + 로그)
   logback-access-dev.xml     │              ├──────────── ▲ Kibana 쿼리
   → LogstashTcpSocketAppender│              │
   → LogstashAccessTcp...     │              │
                              │              ▼
  Kibana ◀── ES 쿼리 ─────────┼──────── Logstash :5044/:50000
  Grafana OSS                 │           pipeline.conf (beats/tcp → ES)
  Prometheus (커스텀 이미지)   │           logstash-config.yml
   prometheus.yml             │
   ${DATA_NODE_IP} sed 치환    │           Redis
  node-exporter ◀── scrape ───┘           node-exporter
```

### 환경 의존
- `back/build.gradle.kts:59-61`: `logstash-logback-encoder:7.4`, `logback-access-common:2.0.6`, `logback-access-tomcat:2.0.6`
- `back/src/main/resources/logback-spring.xml`: dev 프로파일이 `LOGSTASH_URL` 환경변수로 TCP 송신
- `back/src/main/resources/logback-access-dev.xml`: Tomcat access log를 별도 TCP로 송신
- `infra/app/compose.yml`: `LOGSTASH_URL` 환경변수 주입, `kibana`/`grafana`/`prometheus`/`node-exporter` 서비스
- `infra/data/compose.yml`: `logstash` 서비스, `elk` 브리지 네트워크
- `infra/prometheus/Dockerfile` + `entrypoint.sh`: `${DATA_NODE_IP}` sed 치환을 위한 커스텀 이미지

### 외부 환경 제약
- Grafana Cloud는 outbound HTTPS push만 지원 — EC2 안으로 scrape하지 않음. 에이전트가 EC2 안에서 push 필수
- Grafana Cloud Free tier: 50GB logs, 10k metrics active series, 14일 보관, 3 active users
- Loki는 라벨 카디널리티가 폭발하면 즉시 성능 저하 — high-cardinality(requestId·playerId) 필드는 label 금지
- Promtail은 2026-03-02 EOL — 공식 후속은 Grafana Alloy
- Grafana Cloud Loki HTTPS endpoint의 batch 한도 65536 bytes (self-hosted는 4MB) — Alloy 기본값으로 충족

## Goals / Non-Goals

**Goals:**
- ELK 로그 경로 완전 제거 (Logstash·Kibana 컨테이너 및 ES의 로그 인덱스)
- self-hosted Prometheus·Grafana 컨테이너 제거 + 커스텀 Prometheus 이미지/`infra/prometheus/` 디렉토리 정리
- Grafana Alloy 단일 에이전트로 로그·메트릭 수집·송신 통합 (Promtail은 사용하지 않음 — 후속 마이그레이션 부담 회피)
- ES는 플레이리스트 검색용으로 남기되 로그 인덱스(`logstash-*`)는 제거하여 디스크/메모리 회수
- 로그 송신 경로 변경에도 기존 MDC(`requestId`, `requestPlayerId`)와 stack trace JSON 구조가 그대로 보존
- 운영자가 인시던트 시 단일 Grafana Cloud UI에서 로그·메트릭·트레이스(추후)를 함께 조회

**Non-Goals:**
- Elasticsearch 자체 제거 — 플레이리스트 검색 책임을 옮기는 별도 변경
- Tracing 도입 (Tempo) — 후속 변경 후보
- 알림(Alertmanager·Grafana OnCall) 신규 설계 — 본 변경은 채널/규칙 마이그레이션만 다룸
- 로그 보관 정책 튜닝 (Free tier 14일 기본값 사용)
- 신규 도메인 모듈 추가, 헥사고날 구조 변경
- local/test 프로파일 변경 — 로컬은 콘솔 출력 그대로
- JVM/Spring Boot 메트릭 노출 (Actuator Prometheus endpoint) — 현재 노출하지 않으며 본 변경에서도 노출하지 않음. 후속 변경 후보

## Decisions

### Decision 1: 통합 에이전트로 Grafana Alloy 채택 (앱 직접 push 안 함)

로그·메트릭 모두 **Grafana Alloy** 단일 에이전트가 수집·송신한다. 백엔드 앱은 logback에서 Loki HTTP API를 직접 호출하지 않는다.

근거:
- Grafana Cloud Mimir는 push 방식(remote_write)만 지원 — scrape 에이전트 필요. 어차피 에이전트를 도입할 거면 로그도 같은 에이전트로 통합하는 게 단순
- Alloy는 Promtail의 공식 후속(2025-02 LTS, 2026-03-02 Promtail EOL) — 신규 도입에는 Alloy가 유일한 선택지
- Docker socket 기반 `loki.source.docker`는 컨테이너 stdout을 자동 수집 + 컨테이너 메타(이미지, 서비스명) 자동 라벨링
- 앱 ↔ 로깅 backend 결합도 낮음 — Loki/Mimir 장애가 앱 메모리 압력으로 직결되지 않음 (Docker가 stdout buffer 유지)
- 미래에 traces(Tempo)·profiles(Pyroscope) 추가 시 동일 Alloy 인스턴스에서 처리 가능

**대안 검토:**
- *loki4j 직접 push*: 로그는 처리 가능하지만 메트릭은 별도 에이전트 필요 → 결국 두 시스템 운영. 통합 이득 사라짐. 기각
- *Docker Loki driver plugin*: EC2 노드에 `docker plugin install` 필요. Swarm rolling update와 plugin 상태가 충돌하는 사례 보고. 멀티라인(스택트레이스) 처리 까다로움. 라벨 커스터마이징 제한적. 기각
- *Promtail*: EOL 임박. 신규 도입 부적절. 기각

### Decision 2: 로그 송신은 spring-app stdout → Alloy → Loki

logback dev 프로파일은 `LogstashTcpSocketAppender` 대신 `ConsoleAppender`로 stdout에 JSON 라인을 출력한다. Alloy의 `loki.source.docker` 컴포넌트가 Docker socket을 통해 컨테이너 stdout을 tail 후 Loki에 push한다.

근거:
- TCP appender는 logging backend 장애 시 connection backlog로 앱에 압력. stdout은 Docker가 buffer 흡수 (logging driver 기본 `json-file` + rotation)
- Alloy 재시작·일시 정지에도 Docker 컨테이너의 stdout 로그가 유실되지 않음 (file 기반 tail이므로 재시작 시 이어 읽기 가능)
- 컨테이너 메타(이미지명, swarm service명, replica index)를 Alloy가 Docker socket으로부터 자동 수집해 라벨로 부여

**대안 검토:**
- *직접 push (loki4j)*: Decision 1에서 기각
- *파일 → file tail*: Spring Boot 표준 logback의 RollingFileAppender + Alloy `loki.source.file`. ConsoleAppender + Docker socket 대비 컨테이너 파일 시스템 마운트가 추가됨. 컨테이너 친화적이지 않음. 기각

### Decision 3: JSON encoder는 `logstash-logback-encoder` 재사용

logback의 JSON 출력은 기존 `net.logstash.logback:logstash-logback-encoder:7.4`의 `LogstashEncoder`·`LogstashAccessEncoder`를 그대로 사용한다. `logstash-logback-encoder`는 이름과 달리 generic JSON encoder이며, MDC·stack trace·`customFields`(`{"logType":"app-log"}`) 등 기존 운영에서 의존하던 필드 구조를 그대로 보존한다.

근거:
- 라이브러리 교체(예: logback 1.5 내장 `JsonEncoder`)는 JSON 키 이름/구조가 바뀌어 운영 쿼리(LogQL `{...} | json`) 작성 시 호환 비용 발생
- 기존 `customFields`(`{"logType":"app-log"}`·`{"logType":"access-log"}`) 패턴을 그대로 유지하면 app log/access log를 LogQL에서 동일한 방식으로 구분 가능
- 의존성 1개 유지로 발생하는 추가 무게 미미

**대안 검토:**
- *logback 1.5 내장 `ch.qos.logback.classic.encoder.JsonEncoder`*: 의존성 1개 줄이지만 JSON 구조 차이로 추가 작업 발생. 기각

### Decision 4: Access log는 logback-access ConsoleAppender로 출력 (Tomcat 내장 access log 안 씀)

Tomcat 내장 access log(`server.tomcat.accesslog.*`)로 stdout 강제 출력하는 방식 대신, 기존 `logback-access-tomcat` 의존성을 유지하고 `LogstashAccessEncoder` + `ConsoleAppender` 조합으로 stdout에 JSON 출력한다.

근거:
- 앱 로그와 access 로그가 **동일한 JSON 인코더 패밀리**(`logstash-logback-encoder`)를 사용 → `logType` 필드 외에는 일관된 키 구조 유지
- Tomcat 내장 access log의 pattern 문자열은 JSON 직렬화/이스케이핑 책임이 운영자에게 있음 (특수문자 처리 누락 시 LogQL parser fail). logback-access encoder는 검증된 라이브러리
- 기존 `<fieldNames><requestHeaders>request_headers</requestHeaders></fieldNames>` 같은 세부 필드 매핑이 그대로 동작

**대안 검토:**
- *Tomcat 내장 access log → stdout (`server.tomcat.accesslog.directory=/dev`, `prefix=stdout`)*: `logback-access-*` 의존성 2개 제거 가능. 단 JSON 직렬화·이스케이핑 직접 관리 필요. 기각
- *Access log 자체 제거 후 OpenTelemetry servlet filter*: 별도 OTLP 송신 경로 도입. 본 변경 범위 외. 기각

### Decision 5: self-hosted Grafana·Prometheus·커스텀 Prometheus 이미지 모두 제거

self-hosted Grafana OSS 컨테이너 + Prometheus 컨테이너 + `infra/prometheus/` 디렉토리(커스텀 이미지 Dockerfile, `entrypoint.sh`) 모두 제거한다. 메트릭은 Alloy가 node-exporter를 직접 scrape하여 Grafana Cloud Mimir에 remote_write한다.

근거:
- 사용자 요구: "self-hosted Grafana/Prometheus도 옮길 수 있으면 좋겠다"
- Mimir에 push할 거면 local Prometheus는 중복 컴포넌트
- 커스텀 Prometheus 이미지(`${DATA_NODE_IP}` sed 치환)는 Alloy 설정에서 환경변수로 직접 표현 가능 → 별도 이미지 빌드/CI/레지스트리 항목 정리

**대안 검토:**
- *local Prometheus 유지 + remote_write*: 단기 안전 이중 저장. 운영 컴포넌트 1개 늘어남 그대로. 사용자가 이미 통합을 명시했으므로 기각
- *Prometheus는 두고 Grafana만 Cloud로*: 데이터 소스 분기·인증 추가 부담. 통합 효과 적음. 기각

### Decision 6: Elasticsearch는 그대로 유지 (검색 책임만 남김)

Elasticsearch 자체는 **그대로 유지**한다. 본 변경은 ES의 **로그 인덱스(`logstash-*`)만 제거**하고, `PlaylistDocument` 검색 인덱스는 손대지 않는다.

근거:
- ES는 플레이리스트 검색의 핵심 인프라(Nori 한국어 분석기, `EsPlaylistSyncHandler` 의존). 검색을 다른 시스템으로 옮기는 것은 별도 큰 변경
- ELK 제거의 진짜 가치는 **Logstash·Kibana 운영 부담 제거 + ES 자원 회수**이며, ES 자체 제거가 아님
- 본 변경 범위 안에서 ES 매핑·`PlaylistDocument` 변경 없음 — 회귀 위험 0

**관련 작업:**
- 운영 작업으로 logstash-* 인덱스 일괄 삭제 (`DELETE /logstash-*`) — ES 디스크/메모리 즉시 회수
- 본 변경 머지 후 ES `elastic.password` 등 자격증명은 백엔드 검색 경로에서만 사용 (Kibana 의존 사라짐)

### Decision 7: Alloy 배포 형태 — app 노드 Swarm `mode: global`, data 노드 단일 compose 컨테이너

- **App 노드**: `infra/app/compose.yml`에 `alloy` 서비스 추가, `deploy.mode: global` 지정 (Swarm 노드당 1개 인스턴스). spring-app/nginx 컨테이너 stdout 수집 + 자신/형제 노드 node-exporter scrape
- **Data 노드**: `infra/data/compose.yml`에 `alloy` 서비스 추가 (단일 compose 컨테이너). MySQL/ES/Redis 컨테이너 stdout 수집 + 자체 node-exporter scrape
- 두 노드 모두 Docker socket(`/var/run/docker.sock`)을 read-only로 마운트해 컨테이너 메타 자동 수집

근거:
- App 노드는 향후 multi-node Swarm 확장 가능성 → `global`이 표준
- Data 노드는 단일 노드 운영이고 CI 자동 배포 없음(기존 정책 유지). 단일 컨테이너로 충분
- 각 노드의 Alloy는 자신의 Docker socket에만 접근 → 노드 간 docker 권한 공유 불필요
- node-exporter scrape는 각자 자기 노드만 담당 → cross-node 네트워크 없음

**대안 검토:**
- *App 노드 Alloy 하나가 데이터 노드 node-exporter도 scrape*: 단일 Alloy 운영 단순화. 그러나 데이터 노드 컨테이너 로그는 여전히 수집 불가(Docker socket 원격 접근 안 함). 결국 데이터 노드에도 에이전트 필요. 기각
- *Alloy를 Swarm의 다른 노드 추가로 운영*: Swarm 확장 책임 본 변경 범위 외. 기각

### Decision 8: 라벨은 low-cardinality만, MDC/요청 단위 식별자는 line content로

Loki 라벨에는 `app`, `env`, `log_type`(app/access), `level`, `container_name`(Alloy 자동) 같은 **수십 단위 카디널리티** 필드만 사용한다. `requestId`, `requestPlayerId`, URL path 같은 high-cardinality 필드는 JSON line 안에 보존하고 LogQL `| json | requestId="X"`로 사후 필터링한다.

근거:
- Loki는 label 조합마다 별도 stream을 만들고 인덱싱. high-cardinality 라벨은 stream 수 폭발 → 인덱스 메모리 폭발 → 성능 저하
- ES는 모든 필드 인덱싱이 표준이었으나 Loki는 메타데이터만 인덱싱하고 본문은 압축 저장 → 패러다임 차이 명시 필요
- `logstash-encoder`는 MDC를 자동으로 JSON 키로 평탄화 → `| json` parser로 그대로 추출 가능

**구체 라벨 매핑:**
- `app=nomat-back` (정적)
- `env=dev` (정적, 프로파일에 종속)
- `log_type=app-log|access-log` (logback `customFields`로 line에 포함 → Alloy가 stage로 라벨 승격)
- `level=INFO|WARN|ERROR|DEBUG` (app log만; access log는 status code로 대체)
- `container_name`, `swarm_service`, `image` (Alloy 자동)

### Decision 9: Grafana Cloud 시크릿은 GitHub Secrets + EC2 `.env`로 주입

- **Loki**: `GRAFANA_CLOUD_LOKI_URL`(예: `https://logs-prod-XXX.grafana.net/loki/api/v1/push`), `GRAFANA_CLOUD_LOKI_USER`(인스턴스 ID 숫자)
- **Mimir**: `GRAFANA_CLOUD_MIMIR_URL`(예: `https://prometheus-prod-XX.grafana.net/api/prom/push`), `GRAFANA_CLOUD_MIMIR_USER`(인스턴스 ID 숫자)
- **공용**: `GRAFANA_CLOUD_API_TOKEN`(Loki/Mimir write scope 한 token)

근거:
- 기존 `LOGSTASH_URL`·`ELASTIC*` 시크릿 주입 패턴과 일치
- Loki/Mimir는 각자 user ID가 다르지만 token은 cloud-wide 정책으로 단일 발급 가능 → 운영 단순화
- 토큰 회전 시 EC2 `.env` 갱신 + `docker stack deploy` 한 번이면 적용

**시크릿 노출 표면:**
- EC2 컨테이너 환경변수 → `docker inspect` 시 노출. 기존 ES 자격증명과 동일 수준의 신뢰 경계
- token은 write scope only로 발급 권장 (Grafana Cloud 콘솔에서 권한 제한)

### Decision 10: 본 변경은 단일 PR로 묶음 (로그·메트릭·UI 동시 컷오버)

logback 변경, Logstash/Kibana 제거, Grafana/Prometheus 제거, Alloy 도입, CI 시크릿 교체를 **하나의 PR**로 처리한다.

근거:
- 부분 변경(예: 로그만 먼저 Alloy로) 진행 시 self-hosted Grafana에서 Loki를 datasource로 임시 등록하는 식의 중간 상태 발생. Phase 3에서 어차피 버리므로 낭비
- logback 변경(stdout 출력)과 Alloy 도입이 순서 의존적 — logback만 먼저 변경하면 stdout으로 흘러나오는 JSON이 수집되지 않아 로그 사라짐. 인프라만 먼저 들어가면 Alloy가 수집할 대상이 기존 TCP 송신 그대로
- 본 변경의 변경 면적이 ELK 제거에 비해 크지만 의존성이 강하게 묶여 있어 분할 이득 적음

**대안 검토:**
- *Phase 1 로그 → Phase 2 메트릭 → Phase 3 UI*: 사용자가 통합을 명시. 분할 시 중간 상태(self-hosted Grafana에 임시 Cloud datasource 추가 등)의 낭비. 기각
- *back/와 infra/만 먼저, CI는 후속*: 시크릿이 동시 필요. 기각

### Decision 11: 롤백 전략

본 PR `git revert` + 인프라 수동 재배포 + EC2 `.env` 원복 절차:

1. `git revert <merge sha>` 후 develop push → CI가 back 이미지 재빌드(이전 logback) + `infra/app` Swarm 재배포(이전 컨테이너 구성으로 복귀)
2. EC2 `.env`에 `LOGSTASH_URL` 등 원래 변수 복구
3. `infra/data` 노드에 `docker compose up -d` 수동 실행 → `logstash` 컨테이너 재가동 (Kibana는 app 노드 stack 재배포로 복구)
4. ES 인덱스: 본 변경 머지 후 운영 작업으로 logstash-* 인덱스를 이미 삭제했다면 **이전 로그는 복구 불가**. 새 로그는 logstash 재가동 시점부터 다시 인덱싱
5. Grafana Cloud 스택은 그대로 유지(다음 시도 시 재사용)

**롤백 한계:**
- ES logstash-* 인덱스 삭제는 비가역 — 본 변경 작업 절차에서 명시적으로 "PR 머지 즉시 인덱스 삭제 금지, 1-2주 안정 운영 확인 후 삭제"를 운영 절차로 둔다 (tasks.md 후속 항목)
- self-hosted Grafana 대시보드 volume(`grafana-data`)도 마찬가지 — 컨테이너 제거 전에 volume backup 권장

## Risks / Trade-offs

- **[Risk] Alloy 자체 장애 → 로그·메트릭 일시 중단** → Mitigation: Alloy compose에 `restart: unless-stopped` + Docker logging buffer로 짧은 정전 흡수. 장기 장애 시 docker 컨테이너 stdout이 EC2 디스크에 저장되므로(json-file driver 기본) 복구 후 Alloy가 이어서 tail. 다만 file rotation 한도 초과 시 손실 가능
- **[Risk] Grafana Cloud 외부 의존 → 인터넷/Cloud 장애 시 로그·메트릭 send 불가** → Mitigation: Alloy는 in-memory buffer + 재시도. 장기 outage 시 손실 일부 발생 가능. 별도 자체 backup 저장 없음 (수용 가능한 trade-off — 본 서비스 규모상 self-hosted 백업의 운영 비용 > 로그 손실의 사업적 비용)
- **[Risk] Free tier 한도 초과(50GB logs/월, 10k active series)** → Mitigation: 본 서비스 트래픽 기준 예상 로그 ~수GB/월로 안전 마진 큼. 한도 임박 시 Grafana Cloud 콘솔에서 알람 설정. 한도 초과 시 ingestion이 거부되며 앱 자체에는 영향 없음. 비용 발생 가능 → 운영 모니터링 필요
- **[Risk] 라벨 카디널리티 폭발 → Loki 인덱스 성능 저하** → Mitigation: Decision 8의 라벨 정책을 design 단계에서 명문화. Alloy stage 설정에서 `static_labels`/`labeldrop`으로 high-cardinality 필드의 라벨 승격을 차단
- **[Risk] 본 변경 머지 직후 운영자가 익숙한 Kibana UI 사용 불가** → Mitigation: PR 본문에 Grafana Cloud UI 접속 가이드(LogQL 기본 쿼리 예시 포함) 첨부. 본 변경 작업 절차에서 핵심 운영 쿼리(에러 로그 조회, 특정 requestId 추적 등)를 사전 검증
- **[Risk] ES logstash-* 인덱스 삭제 → 과거 로그 조회 불가** → Mitigation: Decision 11에 명시. 머지 직후 삭제하지 않고 1-2주 안정 운영 확인 후 운영 작업으로 삭제. 그 이전엔 Kibana 없이 ES Dev Tools 또는 curl로 임시 조회
- **[Trade-off] logback-access-* 의존성 유지 → 라이브러리 1개 늘지만 의존성 정리보다 JSON 구조 일관성 우선** (Decision 4)
- **[Trade-off] Alloy 도입으로 컨테이너 +2 (app/data 노드)** → 그러나 net 변화는 -2 (4개 제거 + 2개 추가)이며 운영 부담은 크게 감소
- **[Trade-off] Grafana Cloud 시크릿 토큰을 EC2 환경변수로 평문 보관** → 기존 ES·DB 자격증명과 동일 수준의 신뢰 경계. 별도 secret store 도입은 본 변경 범위 외

## Migration Plan

본 변경 무중단 배포 절차:

1. **사전 준비** (운영자 수동)
   - Grafana Cloud 계정 생성·스택 생성 (region 선택: us-central1 권장)
   - Loki·Mimir 각 user ID 확인 (스택 상세에서 `instance ID`)
   - Grafana Cloud Access Policy 생성 → API token 발급 (scope: `logs:write`, `metrics:write`)
   - 발급된 token + user ID + URL을 GitHub Secrets에 등록
2. **PR 작성·머지** — 본 PR 설명에 사전 준비 결과(스택 URL, user ID 마스킹) 요약 첨부
3. **CI 롤아웃** — develop 머지 시 자동 실행:
   - `back-push-develop.yml`: 새 logback 설정 포함된 이미지 빌드·push → EC2에 `docker stack deploy` (replicas rolling update)
   - `infra-push-develop.yml`: `infra/app/compose.yml` 변경 적용 (kibana/grafana/prometheus 제거 + alloy 추가). EC2 `.env`에 Grafana Cloud 시크릿이 사전에 들어가 있어야 함
4. **데이터 노드 수동 배포** — `infra/data/compose.yml` 변경 적용 (CI 없음, 운영자 수동 SSH):
   - `docker compose down logstash`
   - `docker compose up -d alloy`
   - logstash 컨테이너 제거 확인
5. **운영자 EC2 `.env` 정리**:
   - 제거: `LOGSTASH_URL`
   - 추가: `GRAFANA_CLOUD_LOKI_URL`, `GRAFANA_CLOUD_LOKI_USER`, `GRAFANA_CLOUD_MIMIR_URL`, `GRAFANA_CLOUD_MIMIR_USER`, `GRAFANA_CLOUD_API_TOKEN`
6. **사후 검증**:
   - Grafana Cloud UI에서 `{app="nomat-back"}` LogQL 쿼리로 spring-app 로그가 수집되는지 확인
   - `{app="nomat-back", log_type="access-log"}`로 access log가 별도 stream으로 수집되는지 확인
   - `node_cpu_seconds_total{job="node-exporter"}` Mimir 쿼리로 app/data 노드 메트릭이 수집되는지 확인
   - dev URL(`https://api.dev.nomat.live/health`, `/info`)이 정상 응답하는지 — 로그 파이프라인 변경이 앱 자체에 영향 없음 확인
   - app 노드의 `docker service ls` 출력에서 kibana/grafana/prometheus 사라짐, alloy 추가됨 확인
7. **1-2주 안정 운영 후 ES logstash-* 인덱스 정리** (별도 운영 task):
   - `curl -X DELETE 'http://elasticsearch:9200/logstash-*' -u elastic:$ELASTIC_PASSWORD`
   - ES 디스크 사용량 회수 확인
8. **롤백** (필요 시): Decision 11의 절차로 복귀

## Open Questions

- **현재 dev 환경의 로그 볼륨 추정값** — Free tier 50GB/월 안에 들어오는지 사전 추정 필요. 추정 방법: ES `_cat/indices?v`로 logstash-* 인덱스의 일평균 size 확인 후 30배. 본 PR 머지 전 PR 본문에 추정값 첨부
- **Grafana Cloud 대시보드 마이그레이션 정책** — 기존 self-hosted Grafana 대시보드는 volume 기반(provisioning 아님)이라 운영자가 수동 생성한 것들. 본 변경에서는 (a) 새로 작성, (b) JSON export → import 중 선택. Cloud Grafana는 import UI가 있으므로 (b)가 큰 부담 없음. 현재 운영 중인 대시보드 목록 확인 필요
- **알림(alert) 규칙** — self-hosted Grafana에 정의된 알림이 있다면 Cloud Grafana Alerting으로 이전 필요. 채널(Slack/email)도 재설정. 본 변경 작업 절차에서 운영자가 점검
- **JVM·Spring Boot 메트릭 노출** — 현재 Actuator의 `prometheus` endpoint를 노출하지 않음. 본 변경에서는 node-exporter 메트릭(시스템 레벨)만 수집. 앱 레벨 메트릭(JVM heap, GC, HTTP 응답시간 등)은 후속 변경 후보
- **트레이스(Tempo) 도입** — Grafana Cloud는 Tempo도 무료 tier에 포함. Alloy에 OpenTelemetry receiver 추가로 가능. 후속 변경
- **`event_publication` 테이블 메트릭** — Spring Modulith outbox의 미완료 publication 수 등을 Grafana Cloud에서 모니터링하면 가치가 크지만, 본 변경 범위 외
- **Self-hosted Grafana `grafana-data` volume 백업 시점** — Decision 11에서 volume backup 권장이라 했으나 자동화 여부는 운영자 결정 (수동 SSH로 `docker run ... tar` 한 번)
