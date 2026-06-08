# application-metrics Specification

## Purpose

백엔드 애플리케이션의 JVM·Spring Boot 런타임 메트릭을 Spring Boot Actuator의 `prometheus` endpoint(pull)로 노출하고, 내부 네트워크의 Grafana Alloy가 spring-app을 replica별로 scrape하여 Grafana Cloud Mimir로 송신하는 역량을 정의한다. 메트릭 endpoint는 전용 관리 포트로 분리해 공개 ingress에 노출하지 않으며, 히스토그램 버킷을 끄고 Alloy의 메트릭 이름 allow-list로 시계열 카디널리티와 노출 범위를 제한한다. 앱 코드는 메트릭을 외부로 직접 push하지 않고 오직 scrape(pull) 경로로만 수집한다.

## Requirements

### Requirement: JVM·Spring Boot 메트릭을 Actuator prometheus endpoint로 노출

시스템은 백엔드 애플리케이션의 JVM·Spring Boot 런타임 메트릭(heap/non-heap 메모리, GC, 스레드, 클래스 로딩, HTTP 요청, HikariCP 커넥션 풀, logback 이벤트 등)을 Spring Boot Actuator의 `prometheus` endpoint(Prometheus text exposition format)로 노출해야(SHALL) 한다. 메트릭 수집은 `micrometer-registry-prometheus`와 Actuator 기본 binder로 구성하며, 커스텀 비즈니스 메트릭은 본 능력의 범위가 아니다.

#### Scenario: prometheus endpoint가 표준 포맷으로 메트릭 노출
- **WHEN** 내부 클라이언트가 관리 포트의 `GET /prometheus`를 호출
- **THEN** HTTP 200 + `text/plain; version=0.0.4` 계열 Prometheus exposition 포맷 본문을 반환해야 한다
- **AND** 본문에 `jvm_memory_used_bytes`, `jvm_gc_*`, `http_server_requests_seconds_count`, `hikaricp_connections` 계열 메트릭이 포함되어야 한다

#### Scenario: management exposure에 prometheus 포함
- **WHEN** `application.yml`의 `management.endpoints.web.exposure.include`를 검사
- **THEN** `prometheus`가 포함되어 있어야 한다

#### Scenario: micrometer-registry-prometheus 의존성 존재
- **WHEN** `back/build.gradle.kts`의 `dependencies` 블록을 검사
- **THEN** `io.micrometer:micrometer-registry-prometheus`가 선언되어 있어야 한다 (버전은 Spring Boot BOM 관리 — 명시 버전 없음)

### Requirement: 메트릭 endpoint는 전용 관리 포트에서만 제공되고 공개 ingress로 노출되지 않음

시스템은 운영(dev) 환경에서 actuator를 전용 관리 포트(`management.server.port`)로 분리하고, `prometheus` endpoint를 **공개 ingress(nginx)로 프록시하지 않아야**(MUST NOT) 한다. `prometheus`는 내부 네트워크의 Alloy만 접근 가능해야 한다.

#### Scenario: dev 프로파일에서 관리 포트 분리
- **WHEN** dev 프로파일의 `application.yml` 관리 설정을 검사
- **THEN** `management.server.port`가 메인 서버 포트(8080)와 다른 전용 포트로 설정되어 있어야 한다
- **AND** `local`/`test` 프로파일에는 관리 포트 분리가 적용되지 않아야 한다 (기존 테스트·로컬 동작 보존)

#### Scenario: 공개 도메인에서 prometheus 도달 불가
- **WHEN** 외부 클라이언트가 `GET https://api.dev.nomat.live/prometheus`를 호출
- **THEN** nginx가 해당 경로를 백엔드로 프록시하지 않으므로 메트릭 본문이 반환되지 않아야 한다 (404 또는 차단)

#### Scenario: nginx가 prometheus를 reverse proxy하지 않음
- **WHEN** `infra/app/nginx.conf`를 검사
- **THEN** `prometheus` 경로를 백엔드로 프록시하는 `location` 블록이 존재하지 않아야 한다 (공개 actuator는 `/info`만 관리 포트로 allow-list, `/health`도 미프록시)

### Requirement: Alloy가 spring-app을 replica별로 scrape하여 Mimir로 송신

시스템은 Grafana Alloy가 `spring-app`의 각 replica를 개별 타깃으로 scrape하여 Grafana Cloud Mimir로 remote_write해야(SHALL) 한다. 두 replica의 메트릭은 서로 다른 `instance` 라벨로 식별 가능해야 하며, scrape는 VIP 라운드로빈으로 섞이지 않아야 한다.

#### Scenario: Alloy가 backend 네트워크에서 replica를 열거
- **WHEN** `infra/app/compose.yml`의 `alloy` 서비스 `networks`를 검사
- **THEN** `backend` 네트워크가 포함되어 있어야 한다 (scrape 도달을 위한 격벽 부분 개방)
- **AND** `alloy-config.alloy`에 `discovery.docker` 기반으로 `nomat-back_spring-app` 컨테이너만 선택하는 relabel이 정의되어 있어야 한다

#### Scenario: replica별 instance 라벨
- **WHEN** 운영자가 Grafana Cloud에서 `jvm_memory_used_bytes{node="app"}`를 조회
- **THEN** 두 replica가 서로 다른 `instance` 라벨 값으로 각각 조회되어야 한다 (단일 시계열로 합쳐지지 않음)

#### Scenario: 앱 메트릭이 Mimir로 송신됨
- **WHEN** `alloy-config.alloy`를 검사
- **THEN** `prometheus.scrape`가 `spring-app` replica를 대상으로 정의되고, `prometheus.remote_write`(`${GRAFANA_CLOUD_MIMIR_URL}`)로 forward되어야 한다

### Requirement: 시계열 카디널리티는 히스토그램 OFF와 Alloy allow-list로 제한

시스템은 Grafana Cloud Free tier(10k active series) 한도를 보호하기 위해 HTTP 요청 히스토그램 버킷을 기본 생성하지 않아야(MUST NOT) 하며, Alloy가 Mimir로 송신하는 메트릭을 이름 화이트리스트로 제한해야(MUST) 한다.

#### Scenario: http_server_requests 히스토그램 버킷 미생성
- **WHEN** `application.yml`의 metrics distribution 설정을 검사
- **THEN** `management.metrics.distribution.percentiles-histogram.http.server.requests`가 `false`로 명시되어 있어야 한다
- **AND** `prometheus` endpoint 응답에 `http_server_requests_seconds_bucket` 시계열이 존재하지 않아야 한다

#### Scenario: Alloy metric allow-list 하드캡
- **WHEN** `alloy-config.alloy`를 검사
- **THEN** `spring-app` scrape 파이프라인에 `__name__` keep 화이트리스트(`jvm_*`, `process_*`, `system_*`, `http_server_requests*`, `hikaricp_*`, `logback_events*`, `tomcat_*` 등)로 송신 메트릭을 제한하는 `prometheus.relabel` 단계가 존재해야 한다
- **AND** 화이트리스트에 없는 메트릭은 Mimir로 송신되지 않아야 한다 (앱에서 의도치 않은 메트릭이 켜져도 예산 방어)

### Requirement: 앱 코드는 메트릭을 외부로 직접 push하지 않음

시스템은 메트릭을 **Alloy scrape(pull)** 경로로만 수집해야(MUST) 한다. 백엔드 애플리케이션 코드가 외부 monitoring backend(Mimir/Prometheus push gateway/OTLP collector 등)로 직접 push하지 않아야 한다. 이는 `observability-pipeline`의 "백엔드는 backend에 직접 push하지 않는다" 원칙과 일관된다.

#### Scenario: 백엔드에 메트릭 직접 송신 코드 없음
- **WHEN** `back/` 소스와 `application.yml`을 검사
- **THEN** OTLP/Prometheus remote-write/push-gateway 등 외부 metrics backend로 직접 push하는 설정·코드가 존재하지 않아야 한다 (Actuator `prometheus` endpoint 노출만 존재)
