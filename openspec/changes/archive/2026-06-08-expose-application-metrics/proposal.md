## Why

직전 변경 `replace-elk-with-grafana-cloud`는 시스템 레벨 메트릭(node-exporter)만 Grafana Cloud Mimir로 보냈고, **JVM·Spring Boot 애플리케이션 메트릭(heap, GC, 스레드, HTTP 응답시간, DB 커넥션 풀 등)은 의도적으로 Non-Goal로 남겼다** (해당 변경 `design.md`의 "JVM/Spring Boot 메트릭 노출 — 후속 변경 후보", `tasks.md` 10.6). 그 결과 현재 상태는:

1. `spring-boot-starter-actuator`는 이미 의존성에 있으나 **`micrometer-registry-prometheus`가 없어 `prometheus` endpoint 자체가 동작하지 않는다**
2. `management.endpoints.web.exposure.include`는 `health, info`만 — `prometheus` 미노출
3. Alloy는 `node-exporter`만 scrape → **앱 내부 상태가 통째로 관측 사각지대**

인시던트 시 "컨테이너 CPU·메모리는 정상인데 응답이 느리다"는 상황에서 GC 폭주·스레드 고갈·HikariCP 커넥션 풀 고갈·특정 엔드포인트 지연을 구분할 수 없다. 본 변경은 앱 레벨 메트릭을 Actuator `prometheus` endpoint로 노출하고 Alloy가 scrape하도록 하여 이 사각지대를 닫는다.

단순 "endpoint 한 줄 켜기"가 아니라 세 개의 인프라 경계 결정이 따른다: (a) Alloy와 `spring-app`이 **서로 다른 오버레이 네트워크**에 격리되어 있어 scrape 경로가 없고, (b) actuator `base-path: /` + nginx `location /`가 `prometheus`를 **공개 도메인으로 노출**하며, (c) Grafana Cloud Free tier **10k active series** 한도에서 `http_server_requests` 히스토그램·replica 배수가 시계열을 폭증시킬 수 있다. 본 제안은 이 세 결정을 설계로 확정한다 (`design.md` Decision 1~4 참조).

## What Changes

### back/
- `back/build.gradle.kts`: `runtimeOnly("io.micrometer:micrometer-registry-prometheus")` 추가 (Spring Boot BOM이 버전 관리 — 명시 버전 없음)
- `back/src/main/resources/application.yml`:
  - `management.endpoints.web.exposure.include`에 `prometheus` 추가 (`health, info, prometheus`)
  - **dev 프로파일 한정** `management.server.port: 8081` 추가 — actuator 전체를 전용 관리 포트로 분리. `local`/`test` 프로파일은 메인 포트 유지 (기존 테스트·로컬 동작 불변)
  - `management.metrics.distribution.percentiles-histogram.http.server.requests: false` 명시 — 히스토그램 버킷 미생성 의도 고정 (카디널리티 가드)
  - `management.endpoint.health`의 `show-components: always` 유지 (replica per-component status 노출 — `redis-pubsub-health` 계약 보존)
- `back/src/main/kotlin/ilpak/nomat/infrastructure/security/`: **관리 전용 `SecurityFilterChain` 빈 신규** — `EndpointRequest.toAnyEndpoint()`로 매칭, `permitAll` + csrf disable + STATELESS. 관리 포트는 내부 전용이므로 인증 없이 Alloy·nginx가 접근 가능. 기존 `SecurityConfiguration.filterChain`(앱 요청용)은 무변경, 단 `permittedUrls`에서 `/health/**`·`/info/**`는 제거(actuator가 더 이상 메인 포트에 없음)
- `back/CLAUDE.md`: observability/운영 엔드포인트 섹션에 actuator 관리 포트(8081)·`prometheus` endpoint·Micrometer 메트릭 부기

### infra/
- `infra/app/compose.yml`:
  - `alloy` 서비스의 `networks`에 `backend` 추가 (`[observability, backend]`) — scrape를 위해 앱 네트워크 격벽을 의도적으로 부분 개방 (`design.md` Decision 1)
  - `spring-app` `healthcheck`의 `curl` 대상을 `http://localhost:8081/health`로 변경 (관리 포트 분리 반영)
  - `alloy_config-2` → `alloy_config-3` (Swarm config 키 증가)
- `infra/app/alloy-config.alloy`:
  - `discovery.docker` + `discovery.relabel`로 `nomat-back_spring-app` 컨테이너만 keep, replica별 container 이름을 `instance` 라벨로, `__address__`를 `<task_ip>:8081`로 합성, `metrics_path = /prometheus`
  - `prometheus.scrape "spring_app"` 추가 → `prometheus.relabel` 경유 → `prometheus.remote_write.grafana_cloud`
  - `prometheus.relabel "app_allowlist"` 추가: `__name__` keep 화이트리스트로 송신 메트릭 제한 (카디널리티 하드캡, `design.md` Decision 4)
- `infra/app/nginx.conf`:
  - `upstream backend_mgmt { server nomat-back_spring-app:8081; }` 추가
  - `location = /info`만 `backend_mgmt`로 reverse proxy (공개 버전 확인 통로 보존). `/health`·`/prometheus`는 프록시 목록에 **포함하지 않음** → 공개 도달 불가 (allow-list/default-closed). `/health`는 컨테이너 내부 healthcheck(`localhost:8081`)가 nginx를 거치지 않고 직접 호출
  - `nginx_conf-4` → `nginx_conf-5` (Swarm config 키 증가)
- `infra/CLAUDE.md`: observability 흐름에 "Alloy가 `spring-app`을 backend 네트워크에서 replica별 scrape" 추가, 네트워크 격리 문구를 "부분 개방"으로 갱신, 관리 포트·nginx allow-list 명시

### .github/workflows/
- 변경 없음 — Mimir write 자격증명(`GRAFANA_CLOUD_MIMIR_*`, `GRAFANA_CLOUD_API_TOKEN`)은 직전 변경에서 이미 주입됨

### front/
- 영향 없음

## Capabilities

### New Capabilities
- `application-metrics`: JVM·Spring Boot 애플리케이션 메트릭을 Actuator `prometheus` endpoint(전용 관리 포트)로 노출하고, Alloy가 replica별로 scrape하여 Grafana Cloud Mimir로 송신하되, 공개 ingress 표면에서 분리하고 시계열 카디널리티를 allow-list로 제한하는 능력

### Modified Capabilities
- `deployed-version-info`: `/info`가 메인 포트에서 **dev 관리 포트(8081)로 이동**하고, 공개 `GET /info`는 nginx reverse proxy를 통해 제공된다. 인증 면제 규칙이 메인 체인의 `/info/**` permit에서 **관리 전용 `SecurityFilterChain`(permitAll)**으로 이동. 공개 동작 계약(인증 없이 빌드 메타 200)은 보존
- `redis-pubsub-health`: 컨테이너 헬스체크가 `http://localhost:8080/health` → **`http://localhost:8081/health`**(컨테이너 내부, nginx 우회)로 변경되고, `/health`가 dev 관리 포트에서 제공된다. **공개 ingress로는 노출하지 않는다**(nginx 미프록시). 인증 면제가 `/health/**` permit → 관리 체인으로 이동. `redisPubSub` 컴포넌트 노출·503 의미·Testcontainers 테스트(메인 포트 유지) 계약은 보존

## Impact

- **서브프로젝트**: `back/`(build.gradle.kts·application.yml·security·CLAUDE.md), `infra/`(app: compose·alloy-config·nginx·CLAUDE.md). `front/`·`infra/data/` 영향 없음
- **도메인 모듈**: 직접 변경 없음. `playlist`/`room`/`player`/`favoriteplaylist`/`auth` 로직 무변경 — 횡단 관심사(`infrastructure/security`)와 운영 설정만 변경
- **헥사고날 계층**: `in`/`out`/`application` 변경 없음. 신규 도메인 빈·포트·어댑터 추가 없음. `infrastructure/security`에 관리 전용 `SecurityFilterChain` 빈 1개 추가
- **DB 스키마**: 변경 없음 (Flyway 마이그레이션 없음)
- **ES 매핑**: 변경 없음 (`PlaylistDocument` 무관, Debezium CDC 무관)
- **Kafka 토픽**: 해당 없음
- **Redis 키**: 변경 없음 (`health:pubsub:*` 채널 동작 무변경 — 노출 포트만 8081로 이동)
- **의존성**: 추가 — `io.micrometer:micrometer-registry-prometheus`(runtimeOnly). 제거 없음
- **외부 시스템**: Grafana Cloud Mimir에 앱 메트릭 시계열 추가 송신. 신규 외부 의존 없음 (기존 Mimir 재사용). Free tier 10k active series 한도 내 사용 가정 — 앱 메트릭 예산 ≈ 1k (`design.md` Decision 4)
- **인프라 컨테이너 변화**: 컨테이너 수 변화 없음. `alloy`가 `backend` 네트워크에 추가 합류, `spring-app`이 8081 관리 포트를 추가 listen(내부 전용, 미발행)
- **네트워크 경계**: `observability` ↔ `backend` 격리가 **Alloy 한 방향으로 부분 개방**됨 (Alloy→`spring-app:8081` scrape). `spring-app`은 `observability`를 알지 못함(역방향 격리 유지)
- **운영 동작**:
  - 메트릭: Alloy가 backend 네트워크에서 `spring-app` replica를 `discovery.docker`로 열거→ 각 8081/prometheus scrape → allow-list relabel → Mimir push. Grafana Cloud에서 `jvm_memory_used_bytes{node="app"}`, `http_server_requests_seconds_count` 등 조회 가능
  - 공개 `/info`: nginx가 8081로 reverse proxy → 외부 동작 불변(개발자 버전 확인 통로 보존)
  - 공개 `/health`·`/prometheus`: **도달 불가** (nginx 미프록시) — 관리 포트 내부 전용. `/health`는 컨테이너 내부 healthcheck(`localhost:8081`)·`/prometheus`는 Alloy만 접근
  - replica별 `instance` 라벨로 두 replica를 개별 식별. replica 증가 시 시계열 선형 증가(대당 ≈ 350)
- **롤백**: 단일 PR `git revert` + `infra/app` 재배포(`docker stack deploy`) + (필요 시) 관리 포트 분리 전 `application.yml`로 원복. Mimir에 쌓인 앱 시계열은 14일 보관 후 자연 만료. 자세한 절차는 `design.md` Decision 8
