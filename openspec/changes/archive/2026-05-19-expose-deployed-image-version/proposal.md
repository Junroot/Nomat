## Why

운영 중 백엔드 인스턴스가 어떤 커밋으로 빌드되었는지 외부에서 확인할 수 있는 수단이 없다. 현재 배포된 이미지 버전을 알려면 EC2에 SSH 접속 후 `docker service inspect`를 해야 하며, 이는 다음 운영 시나리오에서 마찰이 크다:

1. develop에 머지한 PR이 dev 환경에 실제로 반영되었는지 확인
2. 장애 발생 시 어느 커밋이 떠있는지 확인 (롤백·원인 분석)
3. `replicas: 2` 환경에서 두 인스턴스가 같은 커밋으로 떠있는지 검증 (롤링 배포 중 일시 분기 가능)

이미지 태그는 `back-push-develop.yml`에서 `${{ github.sha }}`로 전달되어 `infra/app/compose.yml:3`의 `${NOMAT_TAG:-latest}`로 박히지만, 런타임 응답에 노출되지 않는다. 더불어 git commit 메타를 빌드에 박는 과정에서 **third-party Gradle 플러그인은 사용하지 않는다** — 빌드 시 third-party 코드 실행은 시크릿 접근·이미지 변조 등 표면적이 커서 신뢰 비용을 치를 가치가 없으므로, Spring Boot 공식 메커니즘만 사용한다.

## What Changes

- `back/build.gradle.kts`에 `springBoot { buildInfo { properties { additional.set(...) } } }` 블록 추가. `additionalProperties`로 `commit`, `branch`를 주입 (기본값 `"unknown"`)
- `back/Dockerfile`에 `ARG GIT_COMMIT=unknown`, `ARG GIT_BRANCH=unknown`을 추가하고, Gradle 호출을 `./gradlew build -x test -PgitCommit=$GIT_COMMIT -PgitBranch=$GIT_BRANCH`로 변경
- `.github/workflows/back-push-develop.yml`의 `docker/build-push-action@v6` 단계에 `build-args: GIT_COMMIT=${{ github.sha }}, GIT_BRANCH=${{ github.ref_name }}` 추가
- (해당된다면) `.github/workflows/back-pull-request.yml`에도 동일 build-args 추가하여 PR 빌드도 동일 메타 포함
- `back/src/main/resources/application.yml`의 `management.endpoints.web.exposure.include`에 `info` 추가, path-mapping에 `info: info` 명시
- `back/src/main/kotlin/ilpak/nomat/infrastructure/security/SecurityConfiguration.kt`의 `permittedUrls`에 `/info/**` 추가

## Capabilities

### New Capabilities
- `deployed-version-info`: 운영 중인 백엔드 인스턴스가 빌드된 git commit·branch·시각·artifact 버전을 인증 없이 HTTP로 확인할 수 있는 능력. third-party Gradle 플러그인 의존 없음

### Modified Capabilities
<!-- 기존 spec 중 요구사항이 바뀌는 capability 없음. 본 변경은 신규 capability 추가만 수행 -->

## Impact

- **서브프로젝트**: `back/` + `.github/workflows/`. `front/`, `infra/` 코드 변경 없음 (단, `infra/app/compose.yml`의 healthcheck는 `/health` 그대로 — `/info`는 별도 endpoint이며 healthcheck 대상 아님)
- **도메인 모듈**: 기존 도메인 모듈(`playlist`, `room`, `player`, `favoriteplaylist`, `auth`) 코드 변경 없음. 횡단 관심사이지만 Spring Boot Actuator 기본 contributor를 사용하므로 신규 Kotlin 코드도 사실상 0
- **헥사고날 계층**: 신규 빈/어댑터 추가 없음. `infrastructure/security/` 1줄, `application.yml` 설정만 변경
- **APIs**: `/info` 엔드포인트 신규 노출 (인증 없음). 응답 JSON: `{"build": {"artifact", "name", "time", "version", "commit", "branch"}}`. 기존 호출자에 비파괴적 추가
- **의존성**: 신규 라이브러리 추가 없음. `org.springframework.boot:spring-boot-starter-actuator`(`back/build.gradle.kts:41`)는 이미 사용 중. third-party Gradle 플러그인 도입 없음 — 공식 Spring Boot Gradle 플러그인의 `buildInfo()` 태스크만 사용
- **Docker 이미지**: 동일 commit이라도 build-arg가 다르면 이미지 해시가 달라짐. 단, `${{ github.sha }}`는 매 커밋마다 어차피 변경되므로 캐시 효율에 의미 있는 영향 없음
- **DB·ES·Kafka·Redis**: 영향 없음
- **운영 동작**: 외부에서 `curl https://api.dev.nomat.live/info` 한 번으로 떠있는 commit 확인 가능. SSH·`docker service inspect` 절차 제거. replicas 간 버전 분기도 같은 방식으로 점검 가능
