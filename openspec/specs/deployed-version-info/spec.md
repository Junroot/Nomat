# deployed-version-info Specification

## Purpose

운영 중인 백엔드 인스턴스가 어떤 git commit/branch에서 언제 빌드되었는지를 외부에서 확인할 수 있도록 HTTP로 빌드 메타데이터를 노출한다. Spring Boot Actuator의 `/info` 엔드포인트 규약을 따르며, 메타데이터는 빌드 시점에 이미지에 고정되어 런타임 환경변수로 변조할 수 없다.

## Requirements

### Requirement: 빌드 메타데이터 HTTP 노출
시스템은 운영 중인 백엔드 인스턴스가 빌드된 git commit, branch, 빌드 시각, artifact 이름·버전을 HTTP 응답으로 노출해야(SHALL) 한다.

#### Scenario: /info 엔드포인트 200 응답
- **WHEN** 외부 클라이언트가 `GET /info`를 호출
- **THEN** HTTP 200 응답을 반환해야 한다
- **AND** 응답 JSON `build` 객체에 `artifact`, `name`, `version`, `time`, `commit`, `branch` 키가 모두 존재해야 한다

#### Scenario: 인증 없이 접근 가능
- **WHEN** 인증되지 않은 클라이언트가 `/info`를 호출
- **THEN** SecurityConfiguration의 `/info/**` permit 규칙에 의해 401/403 없이 빌드 메타데이터를 반환해야 한다

#### Scenario: 민감 정보 미노출
- **WHEN** 응답을 검사
- **THEN** 스택 트레이스, 내부 호스트 주소, 데이터베이스 접속 문자열, 환경변수 값(예: JWT_KEY) 등 민감 정보가 포함되지 않아야 한다

### Requirement: 빌드 시점 git 메타 고정
시스템은 git commit과 branch 값을 **빌드 시점에 이미지에 박아 넣어야**(MUST) 한다. 런타임 환경변수로 변조할 수 없어야 한다.

#### Scenario: CI 빌드의 build-arg가 응답에 박힘
- **WHEN** CI가 `docker build --build-arg GIT_COMMIT=<sha> --build-arg GIT_BRANCH=<branch>`로 이미지를 빌드
- **AND** 그 이미지로 컨테이너가 기동
- **THEN** `/info` 응답의 `build.commit`은 빌드 시 전달된 `<sha>`와 정확히 일치해야 한다
- **AND** `build.branch`는 `<branch>`와 일치해야 한다

#### Scenario: 런타임 ENV 주입은 응답에 영향 없음
- **WHEN** 컨테이너 기동 시 `GIT_COMMIT=다른값` 같은 환경변수를 추가로 주입
- **THEN** `/info` 응답의 `build.commit`은 빌드 시 박힌 SHA를 그대로 유지해야 하며, 런타임 ENV로 덮어쓰여지지 않아야 한다

#### Scenario: build-arg 누락 시 기본값
- **WHEN** 로컬 개발자 또는 CI가 build-arg를 전달하지 않고 `./gradlew build` 또는 `docker build`를 수행
- **THEN** `/info` 응답의 `build.commit`과 `build.branch`는 둘 다 `"unknown"` 문자열이어야 한다 (빈 문자열이나 null 아님)

### Requirement: third-party Gradle 플러그인 미사용
시스템은 git 메타데이터를 추출/주입하기 위해 third-party Gradle 플러그인(예: `com.gorylenko.gradle-git-properties`)을 사용해선 안 된다(MUST NOT). Spring Boot 공식 Gradle 플러그인의 `buildInfo()` 태스크와 표준 Gradle 프로퍼티만 사용한다.

#### Scenario: build.gradle.kts plugins 블록에 git 메타 추출용 third-party 플러그인 없음
- **WHEN** `back/build.gradle.kts`의 `plugins {}` 블록을 검사
- **THEN** `com.gorylenko.gradle-git-properties` 등 git 메타 추출/주입을 위한 third-party 플러그인이 선언되지 않아야 한다

#### Scenario: 공식 Spring Boot buildInfo 태스크만 사용
- **WHEN** `back/build.gradle.kts`의 빌드 메타 주입 코드를 검사
- **THEN** `springBoot { buildInfo { ... } }` 블록을 통해서만 메타가 주입되어야 한다 (Spring Boot 공식 Gradle 플러그인의 1급 기능)

### Requirement: Spring Boot Actuator 통합
시스템은 빌드 메타데이터 노출을 Spring Boot Actuator의 `/info` 엔드포인트 규약에 맞춰 구현해야(MUST) 한다.

#### Scenario: management exposure에 info 포함
- **WHEN** `application.yml`의 `management.endpoints.web.exposure.include`를 검사
- **THEN** `info`가 포함되어 있어야 한다

#### Scenario: BuildInfoContributor가 자동 동작
- **WHEN** `springBoot { buildInfo() }` Gradle 태스크가 빌드 시 실행됨
- **AND** `additionalProperties`로 `commit`, `branch`가 주입됨
- **THEN** Spring Boot Actuator의 `BuildInfoContributor`가 자동으로 이 값을 `/info` 응답의 `build.*` 네임스페이스에 노출해야 한다

#### Scenario: 신규 InfoContributor 빈 추가 없음
- **WHEN** 본 변경의 코드 변경을 검사
- **THEN** `org.springframework.boot.actuate.info.InfoContributor`를 구현하는 신규 빈이 추가되지 않아야 한다 (Actuator 기본 contributor만 사용)
