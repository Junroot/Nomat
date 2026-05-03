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
- **도메인 이벤트**: `AbstractAggregateRoot` 상속 + `registerEvent()`로 도메인 이벤트 등록, `repository.save()`/`delete()` 호출 시 발행. 두 가지 리스너 패턴을 용도에 맞게 사용한다:
    - `@TransactionalEventListener(AFTER_COMMIT)` — **ephemeral broadcast**. 실패가 도메인 일관성에 영향이 없고 한 번 놓쳐도 무방한 신호용. 대표 사례: Redis Pub/Sub 브로드캐스트(`RoomEventListener`의 채팅·입퇴장 알림). outbox 영속화·재시도 없음, 같은 스레드/in-memory 처리.
    - `@ApplicationModuleListener(id = "<명시적-식별자>")` — **정합성 사이드 이펙트**. 핸들러가 실패해도 결국 처리되어야 하는 작업용. 대표 사례: ES 인덱스 동기화(`EsPlaylistSyncHandler`), 고아 데이터 정리(`PlaylistDeletedHandler`). Spring Modulith Event Publication Registry가 `event_publication` 테이블에 비즈니스 트랜잭션과 원자적으로 publication entry를 INSERT, AFTER_COMMIT 직후 별도 스레드(`spring.task.execution.pool`)·별도 트랜잭션(`REQUIRES_NEW`)에서 디스패치. 실패 시 `completion_date` NULL로 남고 `EventPublicationRetryScheduler`가 30초 주기로 5분 이상 미완료 항목을 재제출(ShedLock으로 단일 인스턴스만 실행). 핸들러는 멱등하게 작성한다.
- **이벤트 클래스 직렬화 안정성**: Modulith는 `event_publication.event_type`에 FQCN, `serialized_event`에 Jackson JSON을 저장한다. 누적된 미완료 이벤트의 deserialization 실패가 부팅을 깨뜨릴 수 있어 다음 규칙을 따른다:
    - 이벤트 클래스는 `<domain>/application/domain/` 패키지에 둔다 (안정된 위치).
    - `@ApplicationModuleListener(id = "...")`로 listener id를 명시한다 (메서드 위치 이동에 강건).
    - 필드 추가는 nullable 또는 default 값으로 (옛 미완료 이벤트의 deserialization 호환).
    - 필드 삭제·이름 변경·타입 변경은 `event_publication WHERE completion_date IS NULL` 0건인 시점에서만 수행한다.
    - 핸들러는 `SecurityContext`/`MDC`/`RequestContext` 등 호출자 스레드 컨텍스트를 참조하지 않고 동작 가능하도록 페이로드에 필요한 정보를 모두 담는다 (`@Async + REQUIRES_NEW` 환경에서 컨텍스트가 자동 전파되지 않음).

횡단 관심사는 `infrastructure/`에 위치:
- `security/` — OAuth2 설정, JWT 토큰 필터 (`@Profile("!test")`)
- `web/` — CORS 설정, 전역 예외 처리, MDC 로깅 필터, WebSocket/STOMP 설정 (`/ws` 엔드포인트, `/topic` 브로커, STOMP CONNECT 시 JWT 인증 + 방 입장 인터셉터)
- `redis/` — 분산 락 (`RedisDistributedLockExecutor`), 공용 `RedisMessageListenerContainer` 빈, pub/sub round-trip 헬스 컴포넌트 (`/health` 응답에 `components.redisPubSub`로 노출됨)
- `cdc/` — Phase A dual-write 단계의 Debezium 임베디드 엔진 (Phase B에서 제거 예정)
- `events/` — Spring Modulith outbox 인프라: 미완료 publication 재시도 스케줄러(`EventPublicationRetryScheduler`)와 ShedLock Redis lock provider 구성(`ShedLockConfiguration`)
- `container/` — local/test용 Testcontainers 빈 (MySQL, ES, Kafka, Redis)
- `jpa/` — JPA auditing 설정 (`AuditorAwareImpl`이 SecurityContext에서 `createdBy` 설정)

## 테스트 패턴

### 핵심 원칙: No Mocking

MockK, Mockito 등 모킹 라이브러리를 사용하지 않는다. 모든 외부 의존성(MySQL, Redis, Elasticsearch, Kafka)은 Testcontainers로 실제 인스턴스를 제공하여 통합 테스트한다. 서비스 계층의 단위 테스트는 작성하지 않고, Step 클래스를 통한 통합 테스트로 간접 검증한다.

### 인프라 설정

- 커스텀 `@IntegrationTest` 어노테이션으로 전체 Spring 컨텍스트 부트 (`RANDOM_PORT`, `test` 프로파일)
- `ContainerConfiguration`을 통해 Testcontainers (MySQL, Elasticsearch, Kafka, Redis) 사용
- 각 테스트 인스턴스마다 Flyway `clean()` + `migrate()` + Redis flush 실행 (`IntegrationTestExecutionListener`)
- OAuth2는 제외하고, `TestAuthenticationFilter`가 `playerId` 헤더를 읽어 인증 시뮬레이션

### 테스트 유형별 가이드

**1. 컨트롤러 통합 테스트** (`in/` 디렉토리)

`@IntegrationTest` + `WebTestClient`로 HTTP 요청을 보낸다. `.auth(playerResponse)` 확장 함수로 인증 처리.

```kotlin
@IntegrationTest
class MyControllerTest(
    @Autowired private val client: WebTestClient,
    @Autowired private val playerStep: PlayerStep,
) {
    private lateinit var playerResponse: PlayerResponse

    @BeforeEach
    fun setUp() {
        playerResponse = playerStep.save(dummyPlayerRequest())
    }

    @Test
    fun save() {
        client.post().uri("/my-resource")
            .auth(playerResponse)
            .bodyValue(request)
            .exchange()
            .expectStatus().isCreated
            .expectBody<MyResponse>()
            .value {
                assertThat(it).usingRecursiveComparison()
                    .ignoringFields("id")
                    .isEqualTo(expected)
            }
    }
}
```

**2. 도메인 단위 테스트** (`application/domain/` 디렉토리)

Spring 컨텍스트 없이 순수 Kotlin 단위 테스트. 엔티티의 비즈니스 로직만 검증.

```kotlin
class MyEntityTest {
    @Test
    fun `create_초기 상태 검증`() {
        val entity = MyEntity(...)
        assertThat(entity.status).isEqualTo(MyStatus.PENDING)
    }

    @Test
    fun `doSomething_조건 미충족 시 예외 발생`() {
        val entity = MyEntity(...)
        assertThatThrownBy { entity.doSomething() }
            .isExactlyInstanceOf(ConflictException::class.java)
    }
}
```

**3. DTO 검증 테스트** (`dto/` 디렉토리)

`HibernateValidator`로 Jakarta Bean Validation 어노테이션 검증.

```kotlin
class MyRequestTest {
    @Test
    fun create() {
        val result = HibernateValidator.default.validate(getRequest())
        assertThat(result).isEmpty()
    }

    @ParameterizedTest
    @MethodSource("invalidTitle")
    fun invalidTitle(title: String) {
        val request = getRequest().copy(title = title)
        val result = HibernateValidator.default.validate(request)
        assertThat(result).hasSize(1)
    }

    companion object {
        @JvmStatic
        fun invalidTitle(): List<String> = listOf("", "a".repeat(MAX_LENGTH + 1))
    }
}
```

**4. WebSocket/STOMP 테스트** (실시간 기능)

`WebSocketStompClient`로 STOMP 연결, `LinkedBlockingQueue` + Awaitility로 비동기 메시지 검증.

### Step 클래스 & 픽스처

테스트 데이터 생성은 Step 클래스(`PlayerStep`, `PlaylistStep`, `RoomStep`, `FavoritePlaylistStep`)와 `dummy*Request()` 팩토리 함수를 활용한다. 새 도메인을 추가하면 해당 Step 클래스도 함께 생성한다.

```kotlin
val player = playerStep.save(dummyPlayerRequest())
val playlist = playlistStep.save(player, dummyPlaylistCreationRequest())
val room = roomStep.save(player, dummyRoomRequest(playlist.id))
```

### 테스트 메서드 네이밍

한국어 백틱으로 테스트 의도를 명확히 한다. 단순 정상 케이스는 영어 메서드명도 허용.

```kotlin
fun `save_플레이어는 1000개까지만 플레이리스트 생성 가능`()
fun `join_방 정원 초과 시 예외 발생`()
fun save()  // 단순 정상 케이스
```

### 비동기 처리

Elasticsearch 인덱싱, WebSocket 메시지 등 비동기 작업은 Awaitility로 대기한다:

```kotlin
await()
    .pollDelay(Duration.ofSeconds(1))
    .pollInterval(Duration.ofSeconds(1))
    .atMost(Duration.ofSeconds(5))
    .untilAsserted { /* 검증 */ }
```

## 데이터베이스 마이그레이션

Flyway 마이그레이션 파일은 `src/main/resources/db/migration/`에 위치 (현재 V1~V9). 시작 시 스키마 검증 (`ddl-auto: validate`).

## 프로파일

- `local` / `test`: 모든 외부 의존성에 Testcontainers 사용, 실제 자격 증명 불필요
- `dev`: 환경 변수를 통해 모든 외부 서비스를 사용하는 프로덕션 유사 환경

## Detekt 설정

설정 파일: `detekt/config.yml`. 컨트롤러/저장소가 `private class`이므로 `UnusedPrivateClass` 규칙에서 `@RestController`와 `@Repository` 어노테이션을 무시하도록 설정.
