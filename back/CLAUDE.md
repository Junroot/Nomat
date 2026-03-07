# CLAUDE.md

이 파일은 Claude Code (claude.ai/code)가 이 저장소에서 작업할 때 참고하는 가이드입니다.

프론트엔드, 인프라, 통합 플로우 등 전체 프로젝트 컨텍스트는 상위 `../CLAUDE.md`를 참조하세요.

## 빌드 및 테스트 명령어

```bash
./gradlew build                # 전체 빌드 (컴파일 + 테스트)
./gradlew test                 # 전체 테스트 실행
./gradlew test --tests "ilpak.nomat.playlist.in.PlaylistControllerTest"  # 단일 테스트 클래스 실행
./gradlew test --tests "ilpak.nomat.playlist.in.PlaylistControllerTest.save"  # 단일 테스트 메서드 실행
./gradlew detekt               # 정적 분석 (ignoreFailures=true, 빌드 실패하지 않음)
./gradlew bootRun --args='--spring.profiles.active=local'  # Testcontainers를 사용한 로컬 실행
```

## 기술 스택

- Kotlin 1.9 / Spring Boot 3.4 / Java 17
- Spring Data JPA + Flyway 마이그레이션
- Elasticsearch (한국어 검색을 위한 Nori 분석기)
- Debezium 임베디드 CDC (MySQL → Kafka → Elasticsearch)
- local/test 프로파일에서 Testcontainers를 통한 Redis, Kafka
- Kotest assertions, WebTestClient를 사용한 통합 테스트

## 아키텍처 (헥사고날)

기본 패키지: `ilpak.nomat`

각 도메인 모듈 (`playlist`, `room`, `player`, `favoriteplaylist`)은 다음 구조를 따름:

```
module/
├── in/              # 인바운드 어댑터 (REST 컨트롤러, 이벤트 리스너, Redis 구독자 등) - `private` 클래스
├── out/             # 아웃바운드 어댑터 (저장소 구현체) - `private` 클래스
└── application/
    ├── domain/      # JPA 엔티티 + 저장소 인터페이스 (포트) + 도메인 이벤트
    ├── dto/         # Request/Response DTO
    └── *Service.kt  # 비즈니스 로직
```

주요 컨벤션:
- **컨트롤러와 저장소 구현체는 `private class`** — 다른 패키지에서 직접 참조하지 않음
- **저장소 인터페이스**는 `application/domain/` (포트)에, 구현체는 `out/` (어댑터)에 위치
- 저장소 구현체는 Spring Data `CrudRepository` 인터페이스 (역시 `private`)와 선택적 Elasticsearch document repository를 조합
- 컨트롤러에서 인증된 사용자 ID를 가져올 때 `@AuthenticationPrincipal playerId: Long` 사용
- 도메인 엔티티는 JPA auditing을 통해 `createdBy`/`createdDate`를 위한 `@Embedded AuditMetadata` 사용
- 커스텀 예외는 `AbstractNomatException(message, HttpStatus)`을 상속 — `GlobalControllerAdvice`에서 처리
- `NotFoundException`은 `NotFoundResource` enum 값을 인자로 받음
- **도메인 이벤트**: `AbstractAggregateRoot` 상속 + `registerEvent()`로 도메인 이벤트 등록, `repository.save()` 호출 시 발행. `in/`의 `@TransactionalEventListener(AFTER_COMMIT)` 리스너가 후처리 (예: Redis Pub/Sub 브로드캐스트)

횡단 관심사는 `infrastructure/`에 위치:
- `security/` — OAuth2 설정, JWT 토큰 필터 (`@Profile("!test")`)
- `web/` — CORS 설정, 전역 예외 처리, MDC 로깅 필터, WebSocket/STOMP 설정 (`/ws` 엔드포인트, `/topic` 브로커, STOMP CONNECT 시 JWT 인증 + 방 입장 인터셉터)
- `redis/` — 분산 락 (`RedisDistributedLockExecutor`)
- `cdc/` — MySQL → Elasticsearch 동기화를 위한 Debezium 임베디드 엔진
- `container/` — local/test용 Testcontainers 빈 (MySQL, ES, Kafka, Redis)
- `jpa/` — JPA auditing 설정 (`AuditorAwareImpl`이 SecurityContext에서 `createdBy` 설정)

## 테스트 패턴

테스트는 커스텀 어노테이션 `@IntegrationTest`를 사용한 **통합 테스트**:
- `RANDOM_PORT`와 `test` 프로파일로 전체 Spring 컨텍스트 부트
- `ContainerConfiguration`을 통해 Testcontainers (MySQL, Elasticsearch, Kafka, Redis) 사용
- 각 테스트 인스턴스마다 Flyway `clean()` + `migrate()` 실행 (`IntegrationTestExecutionListener`)
- OAuth2는 제외하고, `TestAuthenticationFilter`가 `playerId` 헤더를 읽어 인증을 시뮬레이션
- HTTP 검증에 `WebTestClient` 사용
- `auth(playerResponse)` 확장 함수로 인증 요청의 `playerId` 헤더 설정
- **Step 클래스** (`PlayerStep`, `PlaylistStep`, `RoomStep`, `FavoritePlaylistStep`)가 재사용 가능한 테스트 셋업 헬퍼 제공
- Elasticsearch 인덱싱에 의존하는 테스트는 `Awaitility`를 사용해 최종 일관성 처리

## 데이터베이스 마이그레이션

Flyway 마이그레이션 파일은 `src/main/resources/db/migration/`에 위치 (현재 V1~V9). 시작 시 스키마 검증 (`ddl-auto: validate`).

## 프로파일

- `local` / `test`: 모든 외부 의존성에 Testcontainers 사용, 실제 자격 증명 불필요
- `dev`: 환경 변수를 통해 모든 외부 서비스를 사용하는 프로덕션 유사 환경

## Detekt 설정

설정 파일: `detekt/config.yml`. 컨트롤러/저장소가 `private class`이므로 `UnusedPrivateClass` 규칙에서 `@RestController`와 `@Repository` 어노테이션을 무시하도록 설정.
