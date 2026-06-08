## MODIFIED Requirements

### Requirement: /health 엔드포인트가 컨테이너 헬스체크와 직접 연결
시스템은 Docker Swarm이 보는 `/health` 엔드포인트가 헬스 검증 결과를 그대로 반영해야(SHALL) 한다. 더미 응답은 허용되지 않는다. 운영(dev) 환경에서 actuator는 전용 관리 포트(`management.server.port`)로 분리되며, 컨테이너 헬스체크는 **컨테이너 내부에서 `localhost`의 관리 포트 `/health`를 직접** 호출한다(nginx 우회). `/health`는 **공개 ingress로 노출하지 않는다**. `local`/`test` 프로파일은 메인 포트를 유지한다.

#### Scenario: Swarm healthcheck가 관리 포트의 /health를 호출
- **WHEN** Docker Swarm healthcheck(`infra/app/compose.yml`의 `curl -f http://localhost:8081/health`)가 컨테이너 내부에서 `/health`를 호출
- **THEN** 응답은 Spring Boot Actuator health endpoint 결과여야 한다 (즉, `redisPubSub` 컴포넌트 상태가 반영됨)
- **AND** 헬스 검증이 DOWN이면 HTTP 503을 반환하여 Swarm이 컨테이너를 unhealthy로 마킹할 수 있어야 한다

#### Scenario: /health는 공개 ingress로 노출되지 않음
- **WHEN** `infra/app/nginx.conf`를 검사
- **THEN** `/health`를 백엔드로 프록시하는 `location` 블록이 존재하지 않아야 한다 (공개 actuator는 `/info`만 allow-list)
- **AND** 외부 클라이언트가 `GET https://api.dev.nomat.live/health`를 호출하면 헬스 결과가 반환되지 않아야 한다 (도달 불가)

#### Scenario: 더미 always-ok 응답이 더 이상 존재하지 않음
- **WHEN** 코드베이스를 검사
- **THEN** 어떤 검사도 수행하지 않고 항상 `"ok"`를 반환하는 `/health` 핸들러가 존재하지 않아야 한다

#### Scenario: 인증 없이 호출 가능
- **WHEN** 인증되지 않은 클라이언트(예: Swarm healthcheck 컨테이너 내부 curl)가 `/health`를 호출
- **THEN** 관리 전용 `SecurityFilterChain`(`EndpointRequest.toAnyEndpoint()` permitAll)에 의해 401/403 없이 헬스 검증 결과를 반환해야 한다 (메인 체인의 `/health/**` permit 규칙은 더 이상 사용하지 않음)
