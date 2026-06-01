## MODIFIED Requirements

### Requirement: 빌드 메타데이터 HTTP 노출
시스템은 운영 중인 백엔드 인스턴스가 빌드된 git commit, branch, 빌드 시각, artifact 이름·버전을 HTTP 응답으로 노출해야(SHALL) 한다. 운영(dev) 환경에서 actuator는 전용 관리 포트로 분리되며, 공개 `GET /info`는 nginx reverse proxy를 통해 관리 포트로 라우팅된다. 공개 동작 계약(인증 없이 빌드 메타 200)은 포트 분리 이전과 동일하게 유지된다.

#### Scenario: /info 엔드포인트 200 응답
- **WHEN** 외부 클라이언트가 `GET https://api.dev.nomat.live/info`를 호출
- **THEN** HTTP 200 응답을 반환해야 한다
- **AND** 응답 JSON `build` 객체에 `artifact`, `name`, `version`, `time`, `commit`, `branch` 키가 모두 존재해야 한다

#### Scenario: 공개 /info가 nginx를 통해 관리 포트로 라우팅
- **WHEN** `infra/app/nginx.conf`를 검사
- **THEN** `location = /info`가 관리 포트 upstream(`nomat-back_spring-app:8081`)으로 reverse proxy하도록 정의되어 있어야 한다
- **AND** 공개 클라이언트 입장에서 요청 URL(`/info`)과 응답 동작은 포트 분리 이전과 동일해야 한다

#### Scenario: 인증 없이 접근 가능
- **WHEN** 인증되지 않은 클라이언트가 `/info`를 호출
- **THEN** 관리 전용 `SecurityFilterChain`(`EndpointRequest.toAnyEndpoint()` permitAll)에 의해 401/403 없이 빌드 메타데이터를 반환해야 한다 (메인 체인의 `/info/**` permit 규칙은 더 이상 사용하지 않음)

#### Scenario: 민감 정보 미노출
- **WHEN** 응답을 검사
- **THEN** 스택 트레이스, 내부 호스트 주소, 데이터베이스 접속 문자열, 환경변수 값(예: JWT_KEY) 등 민감 정보가 포함되지 않아야 한다

### Requirement: Spring Boot Actuator 통합
시스템은 빌드 메타데이터 노출을 Spring Boot Actuator의 `/info` 엔드포인트 규약에 맞춰 구현해야(MUST) 한다. 운영(dev) 환경에서 actuator endpoint는 `management.server.port`로 분리된 전용 포트에서 제공된다.

#### Scenario: management exposure에 info 포함
- **WHEN** `application.yml`의 `management.endpoints.web.exposure.include`를 검사
- **THEN** `info`가 포함되어 있어야 한다

#### Scenario: dev 환경에서 actuator가 관리 포트에서 서비스됨
- **WHEN** dev 프로파일의 `application.yml` 관리 설정을 검사
- **THEN** `management.server.port`가 메인 포트와 다른 전용 포트로 설정되어 있어야 한다
- **AND** `/info`를 포함한 모든 actuator endpoint가 해당 관리 포트에서 제공되어야 한다 (`local`/`test` 프로파일은 메인 포트 유지)

#### Scenario: BuildInfoContributor가 자동 동작
- **WHEN** `springBoot { buildInfo() }` Gradle 태스크가 빌드 시 실행됨
- **AND** `additionalProperties`로 `commit`, `branch`가 주입됨
- **THEN** Spring Boot Actuator의 `BuildInfoContributor`가 자동으로 이 값을 `/info` 응답의 `build.*` 네임스페이스에 노출해야 한다

#### Scenario: 신규 InfoContributor 빈 추가 없음
- **WHEN** 본 변경의 코드 변경을 검사
- **THEN** `org.springframework.boot.actuate.info.InfoContributor`를 구현하는 신규 빈이 추가되지 않아야 한다 (Actuator 기본 contributor만 사용)
