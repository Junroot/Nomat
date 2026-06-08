## ADDED Requirements

### Requirement: 로그·메트릭 송신은 Grafana Alloy 단일 에이전트가 담당

시스템은 백엔드 애플리케이션과 인프라 컴포넌트의 로그·메트릭을 **Grafana Alloy** 단일 에이전트가 수집·송신해야(MUST) 한다. 백엔드 애플리케이션 코드는 외부 logging/monitoring backend에 직접 push하지 않아야 한다 (HTTP·TCP 직접 송신 금지).

#### Scenario: 백엔드 컨테이너의 stdout 로그가 Alloy를 통해 Loki로 수집
- **WHEN** spring-app 컨테이너가 로그 라인을 stdout에 출력
- **AND** Alloy 에이전트가 동일 노드에서 Docker socket을 통해 컨테이너 stdout을 tail
- **THEN** 해당 로그 라인이 Grafana Cloud Loki에 전송되어 LogQL `{app="nomat-back"}` 쿼리로 조회 가능해야 한다

#### Scenario: node-exporter 메트릭이 Alloy를 통해 Mimir로 송신
- **WHEN** Alloy가 동일 노드의 `node-exporter:9100`을 주기적으로 scrape
- **THEN** 수집된 메트릭이 Grafana Cloud Mimir에 remote_write되어 `node_cpu_seconds_total{node=~"app|data"}` 쿼리로 조회 가능해야 한다

#### Scenario: 백엔드 코드에 logging backend 직접 송신 코드 없음
- **WHEN** `back/src/main/resources/logback-spring.xml`, `logback-access-dev.xml`을 검사
- **THEN** `LogstashTcpSocketAppender`, `LokiAppender`(loki4j), 기타 외부 backend에 직접 HTTP/TCP push하는 appender가 존재하지 않아야 한다 (`ConsoleAppender` 또는 동등한 stdout 출력 appender만 사용)

### Requirement: 로그 라벨은 low-cardinality만, 요청 단위 식별자는 line content로

시스템은 Loki 라벨에 **수십 단위 카디널리티**의 필드만 사용해야(MUST) 한다. `requestId`, `requestPlayerId`, URL path 등 high-cardinality 필드는 라벨로 승격하지 않고 로그 라인 JSON 안에 보존하여 LogQL의 `| json` parser로 사후 추출되도록 해야 한다.

#### Scenario: 라벨에 허용되는 필드
- **WHEN** Alloy가 송신하는 모든 로그 stream의 라벨 집합을 검사
- **THEN** 라벨 키는 `app`, `env`, `log_type`, `level`, `node`, `container_name`, `swarm_service`, `image` 외의 키를 포함하지 않아야 한다

#### Scenario: high-cardinality MDC는 line content로 보존
- **WHEN** 백엔드가 MDC에 `requestId`, `requestPlayerId`를 설정한 상태로 로그를 출력
- **THEN** 해당 값은 로그 라인의 JSON 본문에 키로 존재해야 한다 (LogQL `{app="nomat-back"} | json | requestId="<X>"` 쿼리로 필터링 가능)
- **AND** Loki 라벨에는 `requestId`·`requestPlayerId` 키가 존재하지 않아야 한다

### Requirement: 앱 로그와 access 로그는 동일 송신 경로에서 `logType` 라벨로 구분

시스템은 백엔드 애플리케이션 로그와 Tomcat access 로그를 **동일한 stdout/Alloy/Loki 경로**로 송신하되, JSON 라인의 `logType` 필드(`app-log` 또는 `access-log`)를 Loki 라벨 `log_type`으로 승격하여 LogQL에서 구분 가능하게 해야(MUST) 한다.

#### Scenario: 앱 로그 stream 분리
- **WHEN** LogQL 쿼리 `{app="nomat-back", log_type="app-log"}` 실행
- **THEN** spring-app의 일반 로그(logback-spring.xml 출력)만 반환되어야 한다 (access log 미포함)

#### Scenario: access 로그 stream 분리
- **WHEN** LogQL 쿼리 `{app="nomat-back", log_type="access-log"}` 실행
- **THEN** Tomcat access 로그(logback-access-dev.xml 출력)만 반환되어야 한다 (app log 미포함)
- **AND** access 로그 JSON 본문에는 `method`, `uri`, `status`, `request_headers` 등 access 필드가 포함되어야 한다

### Requirement: 운영 모니터링 UI는 Grafana Cloud Grafana로 단일화

시스템은 운영자가 로그·메트릭·대시보드·알림을 **Grafana Cloud의 Grafana UI**에서 일괄 조회·관리하도록 해야(MUST) 한다. self-hosted Kibana, self-hosted Grafana 컨테이너는 운영에서 사용하지 않아야 한다.

#### Scenario: self-hosted Kibana 컨테이너 부재
- **WHEN** app 노드에서 `docker service ls`를 실행
- **THEN** 출력에 `kibana` 서비스가 존재하지 않아야 한다

#### Scenario: self-hosted Grafana 컨테이너 부재
- **WHEN** app 노드에서 `docker service ls`를 실행
- **THEN** 출력에 `grafana` 서비스가 존재하지 않아야 한다

#### Scenario: Grafana Cloud에서 로그·메트릭 조회 가능
- **WHEN** 운영자가 Grafana Cloud 콘솔의 Explore 패널에 접속
- **AND** Loki datasource 선택 후 `{app="nomat-back"}` 쿼리 실행
- **THEN** 최근 dev 환경 로그가 표시되어야 한다
- **WHEN** Mimir(Prometheus) datasource 선택 후 `node_cpu_seconds_total` 쿼리 실행
- **THEN** 최근 dev 환경 노드 메트릭이 표시되어야 한다

### Requirement: 메트릭 송신은 self-hosted Prometheus 없이 Alloy → Mimir로 직접 push

시스템은 메트릭을 **Alloy가 직접 scrape + Mimir remote_write**하도록 해야(MUST) 한다. self-hosted Prometheus 서버와 커스텀 Prometheus 이미지 빌드는 운영에서 사용하지 않아야 한다.

#### Scenario: self-hosted Prometheus 컨테이너 부재
- **WHEN** app 노드에서 `docker service ls`를 실행
- **THEN** 출력에 `prometheus` 서비스가 존재하지 않아야 한다

#### Scenario: 커스텀 Prometheus 이미지 빌드 단계 부재
- **WHEN** 저장소 루트에서 `infra/prometheus/` 디렉토리 존재 여부를 확인
- **THEN** 해당 디렉토리가 존재하지 않아야 한다 (`Dockerfile`, `entrypoint.sh` 모두 제거)

#### Scenario: Alloy가 node-exporter를 직접 scrape
- **WHEN** `infra/app/alloy-config.alloy` 및 `infra/data/alloy-config.alloy`를 검사
- **THEN** `prometheus.scrape` 컴포넌트가 동일 노드의 `node-exporter:9100`을 대상으로 정의되어 있어야 한다
- **AND** `prometheus.remote_write` 컴포넌트가 `${GRAFANA_CLOUD_MIMIR_URL}`을 endpoint로 정의해야 한다

### Requirement: Elasticsearch 로그 인덱스 의존 제거

시스템은 더 이상 로그를 Elasticsearch에 저장하지 않아야(MUST NOT) 한다. Elasticsearch는 플레이리스트 검색 책임만 보유하며, Logstash 파이프라인 + `logstash-*` 인덱스는 운영에서 제거되어야 한다.

#### Scenario: Logstash 컨테이너 부재
- **WHEN** data 노드에서 `docker compose ps`를 실행
- **THEN** 출력에 `logstash` 서비스가 존재하지 않아야 한다

#### Scenario: Logstash 설정 파일 부재
- **WHEN** `infra/data/` 디렉토리를 검사
- **THEN** `logstash-pipeline.conf`, `logstash-config.yml`이 존재하지 않아야 한다

#### Scenario: Elasticsearch는 검색용으로 유지
- **WHEN** data 노드에서 `docker compose ps`를 실행
- **THEN** `elasticsearch` 서비스는 그대로 존재해야 한다 (Nori 한국어 분석기 포함, `PlaylistDocument` 검색 책임 유지)

### Requirement: Grafana Cloud 자격증명은 환경변수로만 주입

시스템은 Grafana Cloud로의 송신 자격증명(Loki/Mimir user ID, API token, endpoint URL)을 컨테이너 환경변수로만 주입해야(MUST) 한다. 자격증명을 저장소에 commit하거나 컨테이너 이미지에 박지 않아야 한다.

#### Scenario: 자격증명이 GitHub Secrets/EC2 .env에서 주입
- **WHEN** Alloy 컨테이너가 부팅
- **THEN** `GRAFANA_CLOUD_LOKI_URL`, `GRAFANA_CLOUD_LOKI_USER`, `GRAFANA_CLOUD_MIMIR_URL`, `GRAFANA_CLOUD_MIMIR_USER`, `GRAFANA_CLOUD_API_TOKEN` 5개 환경변수가 설정되어 있어야 한다

#### Scenario: 저장소에 자격증명 평문 없음
- **WHEN** `git grep -E "Bearer\\s+glc_|x-scope-orgid"` 또는 token 패턴을 저장소 전체에서 검색
- **THEN** 실제 token 값이 commit된 파일에 존재하지 않아야 한다 (변수 참조 `${GRAFANA_CLOUD_API_TOKEN}` 형태만 허용)
