# Design — expose-application-metrics

## Context

직전 변경 `replace-elk-with-grafana-cloud`는 메트릭 경로를 "Alloy가 node-exporter scrape → Mimir remote_write"로 확정하면서, 앱 레벨 메트릭(JVM/Spring)은 명시적으로 후속으로 미뤘다. 본 변경은 그 후속이다. 탐색 과정에서 단순 endpoint 노출이 아니라 **네 개의 인프라 경계 결정**이 필요함이 드러났고, 아래 Decision으로 확정한다.

현재 토폴로지 (앱 노드, 단일 Swarm 노드로 운영):

```
backend overlay                    observability overlay
┌──────────────────────┐          ┌──────────────────────────┐
│ spring-app (replicas:2)│   격벽   │ alloy (global) +sock+creds │
│   :8080  nginx :80host │  ←───→  │ node-exporter :9100        │
└──────────────────────┘          └──────────────────────────┘
```

- `spring-app`은 `backend`에만, `alloy`/`node-exporter`는 `observability`에만 → 현재 Alloy는 앱에 도달할 경로가 없다
- actuator `base-path: /`, nginx `location /`가 전부 `spring-app:8080`으로 프록시 → `prometheus`를 켜면 공개 도메인으로 노출
- Spring Security(`SecurityConfiguration.kt`): `permittedUrls = /login/**, /html/**, /health/**, /info/**, /ws/**`, 나머지 `authenticated()`
- 버전 확인 통로: CI `GIT_COMMIT=github.sha` → Dockerfile build-arg → `buildInfo()` → 공개 `GET /info`의 `build.commit` (실동작, 상시 운영 통로)

## Goals / Non-Goals

**Goals**
- JVM·Spring Boot 메트릭을 Actuator `prometheus` endpoint로 노출하고 Alloy가 replica별 scrape
- `prometheus` endpoint를 공개 ingress 표면에서 격리
- Grafana Cloud Free 10k active series 한도 안에서 안전한 카디널리티 유지

**Non-Goals**
- 커스텀 비즈니스 메트릭(`@Timed`, `MeterRegistry` 직접 사용) 추가 — 본 변경은 기본 Micrometer 메트릭만 노출
- 대시보드·알림 룰 정의 — Grafana Cloud에서 운영 작업으로 별도 작성
- `front/` 메트릭, `data/` 노드 앱 메트릭(앱이 없음)

## Decision 1 — Alloy가 `backend` 네트워크에 합류해 pull scrape (격벽 부분 개방)

`infra/CLAUDE.md:34`의 "`observability` overlay로 alloy ↔ node-exporter scrape 격리"는 의도된 설계다. 그러나 **pull이든 push든 메트릭은 어딘가에서 네트워크를 공유**해야 하므로, 격리를 유지한 채 scrape할 방법은 없다. 선택지는 "누가 격벽을 넘느냐":

| 방향 | 격벽을 넘는 주체 | 평가 |
|---|---|---|
| **A. alloy → backend (pull)** | alloy에 `backend` 추가 | 표준 Prometheus 토폴로지. alloy는 이미 `docker.sock`+Cloud creds 보유(최고 권한) → 한계 노출 최소. **채택** |
| B. app → observability (pull) | spring-app(×2)에 `observability` 추가 | 방향이 거꾸로(앱이 관측망을 앎), replica마다 추가 오버레이 |
| C. app → alloy (push, OTLP) | app→alloy 경로 필요 | push도 공유망 불가피(B와 동일 방향) + 앱 OTLP 설정 부담만 증가 |

**채택: A.** `alloy.networks = [observability, backend]`. `spring-app`은 `observability`를 알지 못하므로 역방향 격리는 유지된다(앱 컨테이너가 침해돼도 관측망 도달 불가).

## Decision 2 — `discovery.docker` 재사용으로 replica별 타깃 + per-replica `instance` 라벨

`spring-app`은 `replicas: 2`다. scrape 타깃 선정 방식:

| 방식 | 결과 |
|---|---|
| `spring-app:8081` (VIP static) | 매 scrape마다 replica 라운드로빈 → 두 replica 메트릭이 한 시계열에 섞임 ✗ |
| `tasks.spring-app` (DNS A) | 전 클러스터 task IP 반환 → 단일노드 OK, 멀티노드면 global alloy가 전부 중복 scrape △ |
| **`discovery.docker`** | **로컬 노드 컨테이너만** 열거 → replica별 타깃, `container_name`→`instance`. 멀티노드여도 노드별 alloy가 자기 replica만 scrape ✓ |

**채택: `discovery.docker`.** 이미 로그 수집(`loki.source.docker`)에 쓰는 메커니즘을 그대로 재사용한다. `discovery.relabel`로 `__meta_docker_container_label_com_docker_swarm_service_name == "nomat-back_spring-app"`만 keep, `__meta_docker_container_name`을 `instance`로, `__address__`를 `<ip>:8081`로 합성. 이로써 걸림돌(network)과 걸림돌(replica discovery)이 한 번에 해결되고, 미래 멀티노드 확장에도 노드-로컬 scrape로 자동 정합한다.

## Decision 3 — `management.server.port` 분리(2-B) + 관리 전용 `SecurityFilterChain`

`prometheus`를 같은 8080 포트에 켜면 두 요구가 충돌한다: ① Alloy(무인증 내부 에이전트)가 scrape 가능해야 함, ② 공개 인터넷은 못 읽어야 함. 같은 공개 포트에서는 permitAll(→ 공개 유출) 또는 authenticated(→ Alloy 403) 중 하나가 된다.

| 옵션 | 평가 |
|---|---|
| 2-A. 같은 8080 + permit + nginx에서 `/prometheus` deny | 국소적이나 **부정 보안 모델**(blocklist 의존). 한 경로라도 빠뜨리면 유출 |
| **2-B. `management.server.port: 8081`로 actuator 전체 이동** | actuator를 공개 표면에서 **구조적 분리**(default-closed). **채택** |

**채택: 2-B.** 단, 두 가지 핵심 보완:

1. **관리 전용 `SecurityFilterChain` 필수.** 관리 포트를 분리해도 보안을 안 주면 메인 체인(OAuth2 login·STATELESS·`Http403ForbiddenEntryPoint`)이 적용돼 **Alloy가 403**을 맞는다. `http.securityMatcher(EndpointRequest.toAnyEndpoint())` → `permitAll`인 별도 빈을 둔다(관리 포트는 내부 전용이므로 안전). 메인 `filterChain`은 무변경, `permittedUrls`에서 `/health/**`·`/info/**`만 제거.

2. **분리는 dev 프로파일 한정.** `management.server.port: 8081`을 dev 프로파일에만 둔다. 이유: `redis-pubsub-health`의 기존 Testcontainers `WebTestClient /health` 통합 테스트는 메인 포트 기준이라, 전역으로 포트를 분리하면 테스트가 깨진다. 포트 분리는 운영(dev/EC2) 관심사이고, `local`/`test`는 메인 포트에 actuator를 유지해 기존 동작·테스트를 보존한다.

`base-path`는 `/` 그대로 둔다(관리 포트의 루트 = `/health`, `/info`, `/prometheus`). 공개 경로를 안정적으로 유지하기 위해 내부 경로를 바꾸지 않는다.

## Decision 4 — 공개 표면에는 `/info`만 allow-list, `/health`·`/prometheus`는 비공개

2-B는 actuator **전체**(`/info`·`/health`·`/prometheus`)를 8081로 옮긴다. 이 중 **공개 ingress로 노출할 것은 `/info` 하나뿐**이다:

- **`/info`**: 개발자 배포 버전 확인(`build.commit`)의 상시 외부 통로 → 공개 필요
- **`/health`**: Swarm 컨테이너 헬스체크가 **컨테이너 내부에서 `localhost:8081`로 직접** 호출하며 nginx를 거치지 않는다 → **공개 노출 불필요**. 공개하지 않으면 `components.redisPubSub.status`·503 타이밍 등 내부 상태가 인터넷에 새지 않아 표면이 줄어든다
- **`/prometheus`**: Alloy만 내부에서 scrape → 비공개

**해법:** nginx가 `/info`만 관리 포트로 명시적 reverse proxy한다. `/health`는 컨테이너 내부 healthcheck 전용이라 nginx 미프록시.

```
nginx:80
  location /         → backend(8080, 앱)         # actuator 없음
  location = /info   → backend_mgmt(8081)/info    # 버전 확인 통로 (유일한 공개 actuator)
  (그 외 actuator      미프록시)                   # /health·/prometheus 공개 도달 불가

컨테이너 내부:  healthcheck curl → localhost:8081/health   # nginx 우회, 내부 전용
Alloy(backend): scrape → spring-app:8081/prometheus        # nginx 우회, 내부 전용
```

- 개발자는 `curl https://api.dev.nomat.live/info`를 **그대로** 사용 — 공개 URL·동작 불변
- **2-A의 deny와 방향이 반대**: 2-A는 blocklist(`/prometheus` deny, 빠뜨리면 유출), 2-B는 **allow-list**(명시한 `/info`만 공개, 나머지는 기본 차단). default-closed라 `/health`·`/prometheus`가 구조적으로 안전
- nginx가 공개 경로와 내부 경로/포트를 디커플링 → 향후 내부 path가 바뀌어도 공개 URL 안정

## Decision 5 — 카디널리티 가드: 히스토그램 기본 OFF + Alloy metric allow-list (하드캡)

측정: 라우트 템플릿 ≈ 20개(모두 `@PathVariable`로 바운드), 히스토그램 설정 없음(기본값 `_bucket` 미생성). 추정 시계열:

```
node-exporter(app) ~700 + node-exporter(data) ~700
+ spring micrometer ~300-400/replica × 2 ≈ ~700-800
= 총 ≈ 2,100 / 10,000   (헤드룸 ~8k, 현재 형태는 안전)
```

위험은 "당장 초과"가 아니라 **풋건**이다:
- ① `percentiles-histogram.http.server.requests=true` 한 줄 → `_bucket` 폭증(≈ +2,400 시계열, 사용량 2배). p99 대시보드 만들려다 흔히 켬
- ② 새 엔드포인트가 path에 UUID/이메일을 직접 박으면 URI 카디널리티 무한 증식
- ③ replica 선형 증가(대당 ≈ +350)

**가드(채택):**
1. `management.metrics.distribution.percentiles-histogram.http.server.requests: false` 명시 — ①을 설정으로 고정. p99가 필요하면 client-side `percentiles=[0.95,0.99]`(조합당 ~2 시계열)로 저렴하게
2. **Alloy `prometheus.relabel` allow-list = 비용 하드캡.** `__name__` keep 화이트리스트(`jvm_*`, `process_*`, `system_*`, `http_server_requests*`, `hikaricp_*`, `logback_events*`, `tomcat_*` 등)로 송신 메트릭을 제한. 앱에서 누가 히스토그램을 켜거나 URI가 새도 **Mimir 입구에서 차단**되어 10k 예산이 구조적으로 방어됨. 이 relabel은 Decision 2의 scrape 블록에 1단계로 부착(위치가 이미 정해짐)
3. URI 템플릿화 규율을 `CLAUDE.md`에 부기(unbounded path 금지)
4. 앱 메트릭 시계열 예산 ≈ 1k 명문화

## Spec 영향 (기존 capability 수정)

2-B가 actuator 노출 포트·보안 규칙을 바꾸므로 두 기존 spec을 수정한다. 두 capability의 **공개 동작 계약은 보존**하고, 내부 구현(포트·체인) 기술만 갱신한다.

- `deployed-version-info`: 공개 `GET /info` 무인증 200 + `build.*` 계약 **보존**. 시나리오의 "`/info/**` permit 규칙" → "관리 `SecurityFilterChain` + nginx reverse proxy(dev)"로 갱신
- `redis-pubsub-health`: `redisPubSub` 컴포넌트 노출·DOWN 시 503·Testcontainers 테스트(메인 포트) 계약 **보존**. compose healthcheck `localhost:8080/health` → `localhost:8081/health`, 인증 면제 `/health/**` → 관리 체인으로 갱신

## DB / ES / Kafka / Redis

- DB 스키마: 변경 없음 (Flyway 마이그레이션 없음)
- Elasticsearch 매핑·Debezium CDC: 변경 없음
- Kafka 토픽: 해당 없음
- Redis Pub/Sub 채널: 변경 없음 (`health:pubsub:*` 동작 동일, `/health` 노출 포트만 8081로 이동)

## Decision 8 — 롤백

단일 PR로 배포하고, 문제 시 `git revert` 후 `infra/app` 재배포(`docker stack deploy`)로 원복한다. 세부:
1. `application.yml`에서 `management.server.port`·`prometheus` exposure 원복 시 actuator가 8080으로 복귀 → `redis-pubsub-health`/`deployed-version-info` 기존 동작 자동 복원
2. `infra/app/compose.yml`·`nginx.conf`·`alloy-config.alloy` revert + Swarm config 키 원복(`alloy_config`·`nginx_conf`)
3. Mimir에 쌓인 앱 시계열은 14일 보관 후 자연 만료 — 별도 정리 불필요
4. Alloy의 `backend` 합류만 부분 revert해도 scrape는 즉시 중단됨(빠른 안전장치)

## Risks

- **관리 포트 바인드 주소**: `management.server.port` 분리 시 관리 connector가 모든 인터페이스에 바인드되어야 Alloy가 오버레이로 도달 가능. `management.server.address`를 localhost로 제한하지 않도록 주의 (검증 태스크 포함)
- **관리 체인 우선순위**: 관리 `SecurityFilterChain`이 메인 체인보다 먼저 매칭되도록 `EndpointRequest` matcher + 적절한 `@Order` 필요. 잘못되면 Alloy 403 또는 앱 보안 약화
- **healthcheck 전환 타이밍**: compose healthcheck를 8081로 바꾸는 시점과 앱이 8081을 listen하기 시작하는 배포가 정합해야 함. `start_period: 60s`가 흡수하나, 롤링 업데이트 중 한 replica가 구버전(8080 health)일 수 있음 → start-first 업데이트 순서로 흡수
