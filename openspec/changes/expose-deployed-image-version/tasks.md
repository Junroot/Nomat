## 1. 백엔드 — 빌드 메타 주입

- [x] 1.1 `back/build.gradle.kts`에 `springBoot { buildInfo { properties { additional.set(...) } } }` 블록 추가. `commit`은 `(project.findProperty("gitCommit") as String?) ?: "unknown"`, `branch`는 `(project.findProperty("gitBranch") as String?) ?: "unknown"`로 fallback 처리
- [x] 1.2 `./gradlew build -PgitCommit=test -PgitBranch=test -x test` 로컬 실행 후 `back/build/resources/main/META-INF/build-info.properties` 파일에 `additional.commit=test`, `additional.branch=test`이 들어가는지 확인 — 실제로는 Spring Boot가 `additional` 맵 키를 `build.<key>`로 평탄화하여 `build.commit=test`, `build.branch=test`로 기록함 (spec이 의도한 형태)
- [x] 1.3 `./gradlew build -x test` (프로퍼티 없이) 실행 후 동일 파일에 `additional.commit=unknown`이 들어가는지 확인 (fallback 검증) — `build.commit=unknown`, `build.branch=unknown`으로 기록 확인

## 2. 백엔드 — 런타임 endpoint 노출

- [x] 2.1 `back/src/main/resources/application.yml`의 `management.endpoints.web.exposure.include`를 `health` → `health, info`로 변경
- [x] 2.2 `application.yml`의 `management.endpoints.web.path-mapping`에 `info: info` 추가 (현재 health만 명시되어 있음 — info도 명시하여 일관성 유지)
- [x] 2.3 `back/src/main/kotlin/ilpak/nomat/infrastructure/security/SecurityConfiguration.kt:52`의 `permittedUrls` 집합에 `"/info/**"` 추가

## 3. CI/CD — git 메타 build-arg 주입

- [x] 3.1 `back/Dockerfile`의 build stage 상단에 `ARG GIT_COMMIT=unknown`, `ARG GIT_BRANCH=unknown` 추가
- [x] 3.2 `back/Dockerfile`의 `RUN ./gradlew build -x test` 라인을 `RUN ./gradlew build -x test -PgitCommit=$GIT_COMMIT -PgitBranch=$GIT_BRANCH`로 변경
- [x] 3.3 `.github/workflows/back-push-develop.yml`의 `docker/build-push-action@v6` 단계에 `build-args: GIT_COMMIT=${{ github.sha }}\n  GIT_BRANCH=${{ github.ref_name }}` 추가
- [x] 3.4 `.github/workflows/back-pull-request.yml`에서도 docker 빌드를 수행한다면 동일 build-args 추가하여 PR 빌드도 동일 메타 포함 (수행하지 않으면 skip) — PR workflow는 `./gradlew clean test`만 실행하고 docker 빌드 수행하지 않음. skip.
- [x] 3.5 로컬에서 `docker build --build-arg GIT_COMMIT=abc123 --build-arg GIT_BRANCH=feature/x ./back` 수행 후 컨테이너 안의 `/app/nomat.jar` 안에 build-info.properties가 의도된 값으로 포함되는지 확인 (`unzip -p ... META-INF/build-info.properties`) — `build.commit=abc123`, `build.branch=feature/x` 정상 확인

## 4. 빌드·정적분석

- [x] 4.1 `./gradlew test` 실행하여 전체 테스트 통과 확인 (사전부터 존재하던 flake가 있다면 본 변경 무관함을 별도 확인) — BUILD SUCCESSFUL 2m 35s
- [x] 4.2 `./gradlew detekt` 실행하여 신규 위반 없음 확인 — 로컬 JDK 21 / detekt 1.23.3 비호환으로 develop 기준에서도 동일하게 실패하는 환경 이슈. 본 변경과 무관 (CI는 Java 17).
- [x] 4.3 `./gradlew build` 최종 통과 확인 — BUILD SUCCESSFUL

## 5. 운영 검증 (배포 후 수동)

> 5.1~5.4는 develop 머지·dev 배포 완료 이후 수동 검증 항목. 구현 시점에 체크 불가, 머지 후 운영자가 PR 코멘트 또는 별도 점검에서 확인할 것.

- [ ] 5.1 develop 머지 → dev 자동 배포 후 `curl https://api.dev.nomat.live/info | jq` 실행. `build.commit`이 머지된 commit SHA와 정확히 일치, `build.branch`가 `develop`임을 확인
- [ ] 5.2 `replicas: 2`인 점을 고려하여 여러 번 호출(또는 nginx upstream 우회)하여 두 인스턴스 모두 같은 SHA를 반환하는지 확인. 롤링 배포 직후 일시적 분기는 정상 — 30초~1분 이내에 동일해져야 함
- [ ] 5.3 인증 없이 외부 네트워크에서 호출 가능한지 확인 (브라우저 시크릿 모드 또는 다른 머신에서 curl)
- [ ] 5.4 응답에 의도하지 않은 민감 정보(스택 트레이스, 내부 IP, DB 접속 문자열 등)가 노출되지 않는지 육안 검토

## 6. 문서

- [x] 6.1 `back/CLAUDE.md`에 새 `/info` 엔드포인트 한 줄 언급 — 응답 스키마(`build.commit`, `build.branch` 등), 인증 정책(permitAll), 빌드 시점 고정임을 명시
- [x] 6.2 (선택) PR 설명에 `curl /info` 응답 예시 첨부하여 리뷰어가 즉시 확인 가능하게 — PR 생성 시점 활동. 본 구현 단계 외. 응답 예시는 design.md Decision 1에 이미 기록되어 PR 본문에서 인용 가능.
