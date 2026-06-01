# Tasks — expose-application-metrics

## 1. back/ — Micrometer 의존성 및 actuator 노출

- [x] 1.1 `back/build.gradle.kts` `dependencies`에 `runtimeOnly("io.micrometer:micrometer-registry-prometheus")` 추가 (Spring Boot BOM이 버전 관리 — 명시 버전 없음)
- [x] 1.2 `back/src/main/resources/application.yml` 기본 문서의 `management.endpoints.web.exposure.include`를 `health, info, prometheus`로 변경
- [x] 1.3 `application.yml`에 `management.metrics.distribution.percentiles-histogram.http.server.requests: false` 명시 추가 (카디널리티 가드 — 히스토그램 버킷 미생성 의도 고정)
- [x] 1.4 `application.yml` **dev 프로파일 문서**에 `management.server.port: 8081` 추가 (`local`/`test` 프로파일에는 추가하지 않음 — 메인 포트 유지)

## 2. back/ — 관리 포트 보안

- [x] 2.1 `infrastructure/security`에 관리 전용 `SecurityFilterChain` 빈 신규 추가: `http.securityMatcher(EndpointRequest.toAnyEndpoint())` → `permitAll`, csrf disable, STATELESS. 메인 체인보다 먼저 매칭되도록 `@Order` 지정. 기존 `SecurityConfiguration`과 동일하게 `@Profile("!test")` 적용. 신규 빈은 `private class`가 아닌 `@Configuration`/`@Bean`(Spring 보안 설정 관례 유지)
- [x] 2.2 `SecurityConfiguration.permittedUrls`에서 `/health/**`, `/info/**` 제거 (actuator가 메인 포트에 더 이상 없음). `/login/**`, `/html/**`, `/ws/**`는 유지
- [x] 2.3 관리 connector가 오버레이에서 도달 가능하도록 `management.server.address`를 localhost로 제한하지 않았는지 확인 (기본값 = 모든 인터페이스 유지)

## 3. back/ — 테스트 및 정적 분석

- [x] 3.1 기존 컨트롤러/통합 테스트 패턴(Testcontainers, `local`/`test` 프로파일)에 따라, `test` 프로파일에서 actuator가 메인 포트에 유지됨을 전제로 기존 `redis-pubsub-health` `WebTestClient /health` 테스트가 그대로 통과하는지 확인 (회귀 없음)
- [x] 3.2 `prometheus` endpoint가 응답하고 본문에 `jvm_memory_used_bytes`·`http_server_requests` 계열 메트릭이 노출되는지 검증하는 테스트 추가 (기존 통합 테스트 구조 따름, 새 mock 인프라 도입 금지)
- [x] 3.3 `./gradlew test` 전체 통과
- [x] 3.4 `./gradlew detekt` 통과

## 4. infra/app — Alloy scrape 경로

- [x] 4.1 `infra/app/compose.yml` `alloy.networks`에 `backend` 추가 (`[observability, backend]`)
- [x] 4.2 `infra/app/alloy-config.alloy`에 `discovery.docker` + `discovery.relabel` 추가: `com_docker_swarm_service_name == "nomat-back_spring-app"`만 keep, `__meta_docker_container_name` → `instance`, `__address__` → `<ip>:8081`, `metrics_path` → `/prometheus`
- [x] 4.3 `prometheus.scrape "spring_app"` 추가 → `prometheus.relabel "app_allowlist"` 경유 → `prometheus.remote_write.grafana_cloud`
- [x] 4.4 `prometheus.relabel "app_allowlist"` 추가: `__name__` keep 화이트리스트(`jvm_*`·`process_*`·`system_*`·`http_server_requests*`·`hikaricp_*`·`logback_events*`·`tomcat_*` 등)로 송신 메트릭 제한
- [x] 4.5 `compose.yml`의 `alloy_config-2` → `alloy_config-3` (Swarm config 키 증가)

## 5. infra/app — 헬스체크 및 nginx

- [x] 5.1 `infra/app/compose.yml` `spring-app.healthcheck`의 `curl` 대상을 `http://localhost:8081/health`로 변경
- [x] 5.2 `infra/app/nginx.conf`에 `upstream backend_mgmt { server nomat-back_spring-app:8081; }` 추가
- [x] 5.3 `nginx.conf`에 `location = /info` → `backend_mgmt/info` reverse proxy만 추가 (`proxy_set_header` 기존 패턴 동일). `/health`·`/prometheus`는 프록시하지 않음 (`/health`는 컨테이너 내부 healthcheck 전용)
- [x] 5.4 `compose.yml`의 `nginx_conf-4` → `nginx_conf-5` (Swarm config 키 증가)

## 6. 문서

- [x] 6.1 `back/CLAUDE.md` 운영 엔드포인트/observability 섹션에 actuator 관리 포트(dev 8081)·`prometheus` endpoint·Micrometer 메트릭 부기
- [x] 6.2 `infra/CLAUDE.md`: "Alloy가 `spring-app`을 backend 네트워크에서 replica별 scrape" 추가, 네트워크 격리 문구를 "Alloy 한 방향 부분 개방"으로 갱신, 관리 포트·nginx `/info` 단독 allow-list(`/health`는 컨테이너 내부 healthcheck 전용·`/prometheus`는 Alloy 전용) 명시

## 7. 운영 검증 (배포 후 — 코드 변경 외)

- [ ] 7.1 app 노드 `docker service ls`에서 `spring-app`·`nginx`·`alloy` 정상, 컨테이너 수 변화 없음 확인
- [ ] 7.2 Alloy 컨테이너에서 `spring-app` replica 2개가 scrape 타깃으로 발견되는지 확인 (Alloy `/metrics` 또는 디버그 UI)
- [ ] 7.3 Grafana Cloud Explore에서 `jvm_memory_used_bytes{node="app"}` 쿼리 → 두 replica가 서로 다른 `instance` 라벨로 조회되는지 확인
- [ ] 7.4 공개 `curl https://api.dev.nomat.live/info` → `build.commit`/`build.branch` 정상 반환(버전 확인 통로 보존) 확인
- [ ] 7.5 공개 `curl https://api.dev.nomat.live/prometheus` 및 `.../health` → 둘 다 도달 불가(404/차단) 확인 (공개 표면에는 `/info`만 노출)
- [ ] 7.6 컨테이너 내부 healthcheck(`localhost:8081/health`)가 Actuator 헬스 결과(`components.redisPubSub`)를 반환하고 Swarm이 컨테이너를 healthy로 유지하는지 확인 (공개 노출 없이 내부 동작 정상)
- [ ] 7.7 Grafana Cloud Mimir active series 사용량이 예산(앱 ≈ 1k) 내인지, 히스토그램 `_bucket` 시계열이 생성되지 않았는지 확인
