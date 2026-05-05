## Context

백엔드는 GitHub Actions(`.github/workflows/back-push-develop.yml`)에서 `docker/build-push-action@v6`이 두 태그(`junroot0909/nomat:latest`, `junroot0909/nomat:${{ github.sha }}`)로 푸시하고, 이후 EC2에 SSH 접속하여 `NOMAT_TAG=${{ github.sha }}`를 환경변수로 주입한 채 `docker stack deploy`를 수행한다 (`back-push-develop.yml:24, 56`). 즉, "어떤 SHA가 떠있는지"는 Docker Hub 태그와 Swarm 서비스 정의에는 기록되지만 런타임 HTTP 응답에는 어디에도 드러나지 않는다.

런타임 측 현황:
- `back/src/main/resources/application.yml:1-14`의 management exposure는 `health`만 활성화
- `back/src/main/kotlin/ilpak/nomat/infrastructure/security/SecurityConfiguration.kt:52`의 `permittedUrls`는 `/login/**`, `/html/**`, `/health/**`, `/ws/**`만 허용
- Actuator 기본 `BuildInfoContributor`/`InfoEndpoint`는 비활성

빌드 측 현황:
- `back/build.gradle.kts:5-13`에 `org.springframework.boot:3.4.8` Gradle 플러그인이 적용되어 있어 `buildInfo()` 태스크는 별도 의존성 없이 활성화 가능
- `org.springframework.boot:spring-boot-starter-actuator`는 이미 의존성에 포함 (`back/build.gradle.kts:41`)
- `back/Dockerfile`은 `COPY src ./src`만 수행하며 `.git/`을 컨텍스트에 포함하지 않음. CI 환경(`actions/checkout@v4`)에서는 `.git`이 존재함

즉, 빌드/런타임 양쪽에 필요한 인프라는 이미 갖춰져 있고, 빠진 건 (1) 빌드 시점에 git 정보를 JAR에 넣는 단계, (2) 런타임에 그 정보를 HTTP로 노출하는 설정뿐.

제약·이해관계자:
- **신뢰할 수 없는 third-party Gradle 플러그인 도입 금지**. 빌드 시 third-party 코드 실행은 GitHub Actions secret(Docker Hub 토큰, EC2 SSH 키)에 접근 가능하고 빌드 산출물을 변조할 수 있어, 신뢰 비용을 치를 가치가 없음. 본 변경의 직접적 동기 중 하나
- 헥사고날·코드 품질 정책: 횡단 관심사는 `infrastructure/`에 두지만, 본 변경은 신규 Kotlin 코드 사실상 0 — 공식 Actuator contributor만 활성화
- 호출자: 운영자 수동 curl(주된 사용처), 향후 모니터링 대시보드, 잠재적으로 프론트엔드 푸터(별도 변경)
- Nomat GitHub 저장소가 public이므로 commit hash 노출은 정보 누설이 아님

## Goals / Non-Goals

**Goals:**
- 운영 중인 백엔드 인스턴스가 빌드된 git commit SHA·branch·빌드 시각·artifact 버전을 HTTP로 노출
- 인증 없이 호출 가능 — 운영 도구·외부 모니터링이 토큰 관리 부담 없이 사용
- 빌드 시점 고정 — 런타임 ENV로 변조 불가 (디버깅 안정성)
- third-party Gradle 플러그인 무사용
- Spring Boot 공식 메커니즘만 사용 (`buildInfo()` 태스크 + Actuator `BuildInfoContributor`)

**Non-Goals:**
- 프론트엔드 푸터·관리 콘솔에 버전 표시는 별도 작업 (본 변경은 endpoint 노출까지만)
- Docker 이미지 OCI 라벨(`org.opencontainers.image.revision`) 추가는 본 변경 범위 아님
- CI에서 Slack·Discord·PR 코멘트로 deploy 알림 보내기는 별도 작업
- 데이터 노드 스택(`infra/data/compose.yml`)이나 다른 컨테이너의 버전 노출은 본 변경 범위 아님
- Spring Boot 표준 `git.*` 네임스페이스 분리 구조(별도 `git.properties` 파일 생성 + `GitInfoContributor`)는 본 변경 범위 아님 — 단일 `build.*` 네임스페이스가 본 컨텍스트에서 충분

## Decisions

### Decision 1: Spring Boot 공식 `buildInfo()` 태스크 + `additionalProperties` 방식 채택

`springBoot { buildInfo { properties { additional.set(...) } } }`로 `back/build/resources/main/META-INF/build-info.properties`를 생성하고, Spring Boot Actuator의 `BuildInfoContributor`가 자동으로 `/info` 응답에 노출한다. 추가 의존성·플러그인 없음.

응답 예시:
```json
{
  "build": {
    "artifact": "nomat",
    "name": "nomat",
    "version": "0.0.1-SNAPSHOT",
    "time": "2026-05-05T10:23:14.123Z",
    "commit": "38f8bed4a1c2...",
    "branch": "develop"
  }
}
```

**대안 검토:**
- *gradle-git-properties 플러그인*: 본 변경의 직접적 동기인 "third-party 플러그인 신뢰 비용 회피" 정책에 정면으로 반함. 기각.
- *.git 디렉토리를 Docker 빌드 컨텍스트에 포함 + 어떤 git-aware 플러그인 사용*: third-party 플러그인 사용 시 위와 동일 문제. 빌드 컨텍스트 비대화·매 커밋마다 캐시 무효화도 부수 단점. 기각.
- *커스텀 InfoContributor 빈 + 런타임 ENV 주입*: 빌드 시점 고정이 깨짐 (`docker run -e GIT_COMMIT=거짓값`으로 응답을 변조 가능). 디버깅 시 신뢰성↓. 또한 신규 Kotlin 코드를 인프라 영역에 추가해야 함. 기각.
- *순수 Gradle 태스크로 git.properties 직접 작성*: Spring Boot 표준 `git.*` 네임스페이스가 분리되는 장점이 있지만, build-info와 git-info를 두 군데서 관리해야 하고 Gradle 태스크 코드(~15줄)도 추가됨. 본 변경 컨텍스트에서 단일 `build.*` 네임스페이스가 더 단순. 기각.

### Decision 2: git 정보는 build-arg → Gradle property 경로로 주입

GitHub Actions에서 `build-args: GIT_COMMIT=${{ github.sha }}, GIT_BRANCH=${{ github.ref_name }}`로 전달 → `back/Dockerfile`의 `ARG GIT_COMMIT, GIT_BRANCH`로 수신 → `RUN ./gradlew build -x test -PgitCommit=$GIT_COMMIT -PgitBranch=$GIT_BRANCH`로 Gradle 프로퍼티로 변환 → `back/build.gradle.kts`의 `springBoot.buildInfo`가 `project.findProperty("gitCommit")`로 읽어 `additional`에 주입.

**`.git`을 Docker 컨텍스트에 안 넣는 이유:**
- 컨텍스트 비대화 (전체 .git 히스토리 전송)
- 매 커밋마다 .git이 변하므로 빌드 캐시 무효화
- 명시적 build-arg가 가독성·디버깅 측면에서도 더 좋음 (CI 워크플로우만 보면 정확히 어떤 메타가 박히는지 파악 가능)

**로컬 빌드(`./gradlew build` 또는 `./gradlew bootRun`)에서 프로퍼티가 없는 경우:**
- `project.findProperty("gitCommit") as String? ?: "unknown"`으로 fallback
- 로컬에서 `/info` 응답을 보면 `commit: "unknown"`이 나옴 — 의도된 동작
- 통합 테스트도 `"unknown"`을 받게 되며, 테스트는 commit이 아니라 키 존재만 검증

### Decision 3: `/info` 엔드포인트는 인증 없이 노출

`SecurityConfiguration.kt:52`의 `permittedUrls`에 `/info/**`를 추가하여 `/health/**`와 동일 정책으로 운영한다.

**근거:**
- Nomat GitHub 저장소는 public — git SHA는 이미 외부에서 보임. 인증을 걸어도 실질적 비밀 보호 효과 없음
- 모니터링·자동화 도구(Slack 봇, 외부 uptime checker, 운영 스크립트)가 토큰 관리 부담 없이 사용해야 운영 가치가 큼
- 응답 details에는 commit hash, branch, build time, artifact 이름·버전만 포함. 스택 트레이스·내부 IP·DB 접속 정보 등 민감 데이터 없음
- 기존 `/health/**` permitAll과 일관된 운영 endpoint 정책

**대안 검토:**
- *인증 요구*: 자동화 비용 증가, 운영 가치 감소. repo가 public인 이상 보안 이득 없음. 기각.
- *내부 네트워크에서만 접근 가능하게 nginx로 차단*: nginx 변경 표면적 추가, 외부 모니터링 사용 사례 차단. 기각.

### Decision 4: 응답 구조는 `build.*` 단일 네임스페이스

`additionalProperties.commit`은 Actuator `/info` 응답에서 `build.commit`으로 노출된다. 표준 Spring Boot `git.*` 네임스페이스(별도 `git.properties` 파일 + `GitInfoContributor`)는 사용하지 않는다.

**근거:**
- 본 변경의 호출자는 운영자·자동화 스크립트뿐 — 표준 `git.*` 키를 강제로 가정하는 외부 도구 없음
- `build.*` 단일 네임스페이스가 변경 범위(추가 Gradle 태스크, 추가 Spring 빈)를 최소화
- 향후 `git.*` 분리가 필요해지면 별도 변경에서 추가 가능 (비파괴적 확장)

**대안 검토:**
- *Spring Boot 표준 `git.*` 네임스페이스로 분리*: 별도 git.properties 파일 생성 단계가 필요해지고 (Decision 1에서 기각한 대안들로 회귀). 기각.

### Decision 5: 빌드 시각은 `buildInfo()` 기본값 사용

`springBoot { buildInfo() }`는 기본적으로 `build.time`을 빌드 실행 시각(UTC)으로 채운다. 이 값은 캐시된 빌드에서도 매번 갱신되어 Gradle build cache 무효화의 원인이 될 수 있지만, CI에서 매 푸시마다 어차피 새로 빌드하는 본 환경에서는 문제 없음.

만약 향후 Docker 이미지 캐시 효율이 문제가 되면 `properties { time.set(null) }`로 끄거나 고정 가능 — 본 변경에서는 default 유지.

## Risks / Trade-offs

- **Risk: 로컬 빌드와 CI 빌드 응답 차이.** 로컬은 `commit: "unknown"`, CI 빌드만 진짜 SHA. 운영 환경에서만 의미 있는 정보이므로 OK. 통합 테스트는 `"unknown"`을 검증해도 무방 (키 존재 + 형식만 검증)
- **Risk: build-arg 누락 시 silent unknown.** CI workflow에서 build-args를 빠뜨리면 운영 환경에도 `"unknown"`이 박히고 테스트로 잡기 어려움. 1차 방지는 PR 리뷰. 2차 방지는 배포 후 `curl /info` 수동 확인(tasks 6.1)
- **Trade-off: `build.*` 평탄 vs `git.*` 분리.** `build.commit` 의미상 살짝 어색할 수 있음. 다만 third-party 플러그인 회피 정책 + 변경 최소화 가치가 더 큼 (Decision 4)
- **Risk: actuator info endpoint 보안.** `/info`는 기본적으로 cache 없음 — 매 호출마다 `BuildInfoContributor`가 properties 파일을 읽지만 비용 무시 가능. 다만 무인증 노출이므로 향후 추가하는 InfoContributor가 민감 데이터를 흘리지 않도록 주의 (현 변경에서는 build-info만 노출)

## Open Questions

- 배포 시 `/info`를 모니터링·CI 알림에 자동 연계할지 (예: 배포 후 `curl /info | jq -r .build.commit`이 머지된 SHA와 일치하는지 자동 검증)는 후속 변경에서 결정
- 데이터 노드 스택 컨테이너(MySQL·ES·Kafka·Redis 등 third-party 이미지)는 자체 버전 노출 메커니즘이 다르므로 동일 패턴 재사용 불가 — 별도 운영 가시성 변경에서 다룬다
- 프론트엔드 푸터에 백엔드 버전 표시(별도 변경)는 본 endpoint 위에서 자연스럽게 빌드 가능
