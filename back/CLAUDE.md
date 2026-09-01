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
- local/test 프로파일에서 Testcontainers를 통한 Redis
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
- **컨트롤러, 저장소 구현체, 이벤트 핸들러/리스너는 `private class`** — 다른 패키지에서 직접 참조하지 않음 (이벤트 리스너는 `@Component` + `@ApplicationModuleListener`/`@TransactionalEventListener`/`@EventListener` 메서드를 가진 클래스)
- **저장소 인터페이스**는 `application/domain/` (포트)에, 구현체는 `out/` (어댑터)에 위치
- 저장소 구현체는 Spring Data `CrudRepository` 인터페이스 (역시 `private`)와 선택적 Elasticsearch document repository를 조합
- 컨트롤러에서 인증된 사용자 ID를 가져올 때 `@AuthenticationPrincipal playerId: Long` 사용
- 도메인 엔티티는 JPA auditing을 통해 `createdBy`/`createdDate`를 위한 `@Embedded AuditMetadata` 사용
- 커스텀 예외는 `AbstractNomatException(message, HttpStatus)`을 상속 — `GlobalControllerAdvice`에서 처리
- `NotFoundException`은 `NotFoundResource` enum 값을 인자로 받음
- **도메인 이벤트**: `AbstractAggregateRoot` 상속 + `registerEvent()`로 도메인 이벤트 등록, `repository.save()`/`delete()` 호출 시 발행. 두 가지 리스너 패턴을 용도에 맞게 사용한다:
    - `@TransactionalEventListener(AFTER_COMMIT)` — **ephemeral broadcast**. 실패가 도메인 일관성에 영향이 없고 한 번 놓쳐도 무방한 신호용. 대표 사례: Redis Pub/Sub 브로드캐스트(`RoomEventListener`의 채팅·입퇴장·게임 시작/종료 알림). outbox 영속화·재시도 없음, 같은 스레드/in-memory 처리.
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
- `events/` — Spring Modulith outbox 인프라: 미완료 publication 재시도 스케줄러(`EventPublicationRetryScheduler`)와 ShedLock Redis lock provider 구성(`ShedLockConfiguration`)
- `container/` — local/test용 Testcontainers 빈 (MySQL, ES, Redis)
- `jpa/` — JPA auditing 설정 (`AuditorAwareImpl`이 SecurityContext에서 `createdBy` 설정)

## 라운드 엔진 (`room` 모듈)

`RoomStatus.PLAYING`은 우산이고, 그 안에서 휘발성 `RoundPhase`(`OPEN`/`REVEAL`/`ENDED`)가 라운드를 구동한다. 라운드 상태·점수판은 전부 Redis(휘발성)에 두고 MySQL에 영속화하지 않는다.

- **전이 게이트는 락이 아니라 Lua CAS** — 이중 전이 방지의 단위는 분산 락(`RedisDistributedLockExecutor`는 TTL·펜싱 한계로 상호배제 미보장)이 아니라 `room:{id}:round` Hash의 `(roundSeq, phase)` 단일 원자 Lua CAS다(`RoundStateStoreImpl`). 라운드 전이 핫패스에서는 `withLock`을 쓰지 않는다(멤버십 임계구역 `join`/`leave`만 락 유지). 종료 시 DB `room.status` 플립만 멤버십 락을 재사용한다.
- **단일 시계** — 모든 시각 앵커·비교·sweeper 선택은 `redis.call('TIME')`으로 통일한다(앱 시계 스큐를 correctness가 아닌 latency 문제로 강등). 클러스터에서는 한 방의 모든 키가 같은 노드에 있어 **per-shard 단일 시계**로 성립한다(방은 샤드를 넘나들지 않음).
- **sweeper 단독 구동 타이머** — 타임아웃·`REVEAL` 전이의 유일 구동기는 `RoundDeadlineSweeper`(`@Scheduled` ~1초 + `@SchedulerLock`, 단일 replica). 별도 로컬 타이머가 없어 replica마다 타이머가 중복되지 않는다. sweeper가 주 구동기이므로 `lockAtMostFor`는 `PT1M`이 아니라 폴링의 2~4배(`PT4S`)로 짧게 잡는다. 정밀이 필요한 첫 정답은 sweeper가 아니라 들어온 채팅 메시지가 즉시 처리한다(이벤트 구동). sweeper는 `findDueRoomIds()`에서 `SHARD_COUNT`개 마감 인덱스 샤드를 전부 순회해 마감 방을 모은다.
- **Redis 키 (클러스터 안전)** — 키 스킴은 `RoundRedisKeys`가 관리한다. 라운드 전이는 방의 round Hash·scores ZSET·마감 인덱스 ZSET을 하나의 원자 Lua로 함께 조작하므로, Redis 클러스터의 멀티 키 `CROSSSLOT` 제약을 피하려면 세 키가 같은 slot에 있어야 한다. 이를 위해 한 방의 모든 키와 그 방이 속한 마감 인덱스 샤드를 **동일 hash tag `{shard}`**로 묶는다(`shard = roomId mod SHARD_COUNT`, 현재 64). `round:{shard}:{roomId}`(Hash: roundSeq·phase·deadlineAt·trackIndex·winnerId·totalRounds·trackOrder·trackDurations) / `scores:{shard}:{roomId}`(ZSET, 멤버 조건부 가점·퇴장 제거) / `rounds:deadlines:{shard}`(샤드 ZSET, score=deadlineAt·member=roomId). 모두 GC 백스톱 TTL(24h) + 명시적 teardown(게임 종료·방 삭제). `SHARD_COUNT`는 노드 간 분산도 ↔ sweeper 팬아웃을 맞바꾸며, 휘발성 배치를 결정하므로 활성 게임이 없을 때만 변경한다. 단일 인스턴스/마스터-레플리카에서는 hash tag가 리터럴이라 동작에 영향이 없다.
- **정답 비노출** — `OPEN` 동안 정답(`title`·`additionalTitles`)은 클라이언트로 내려가지 않는다. `ROUND_STARTED`·재접속 스냅샷은 answer-stripped 재생 참조만, `ROUND_REVEALED`·`ENDED`에서만 정답을 포함한다. 채팅 정답 판정(`AnswerMatcher` → `common/normalize/TitleNormalizer`: 표기 정규화 후 비교. 전각/반각·히라가나/가타카나·큰 가나/작은 가나·대소문자·공백/구두점/기호는 접고, 탁점·장음 부호·괄호 안의 내용은 접지 않는다)은 서버 전용.
- **이벤트** — `RoundStartedEvent`·`RoundRevealedEvent`는 `application/domain`에 두고 `@TransactionalEventListener(AFTER_COMMIT, fallbackExecution = true)`로 broadcast(트랜잭션 밖 전이라 fallback 필요). 게임 자연 종료는 기존 `GameEndedEvent`(행위자 옵셔널) 재사용. 모든 전파는 기존 `room:{id}:events` pub/sub → STOMP 경로를 그대로 쓴다.
- 모든 전이 트리거(sweeper·첫 정답·방장 종료)는 단일 진입점 `RoundService`로 수렴한다.

## 테스트 패턴

### 핵심 원칙: No Mocking

MockK, Mockito 등 모킹 라이브러리를 사용하지 않는다. 모든 외부 의존성(MySQL, Redis, Elasticsearch)은 Testcontainers로 실제 인스턴스를 제공하여 통합 테스트한다. 서비스 계층의 단위 테스트는 작성하지 않고, Step 클래스를 통한 통합 테스트로 간접 검증한다.

### 인프라 설정

- 커스텀 `@IntegrationTest` 어노테이션으로 전체 Spring 컨텍스트 부트 (`RANDOM_PORT`, `test` 프로파일)
- `ContainerConfiguration`을 통해 Testcontainers (MySQL, Elasticsearch, Redis) 사용
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

## 운영 엔드포인트

### 관리 포트 분리 (dev 프로파일)

dev 프로파일에서 actuator 전체는 `management.server.port: 8081` 전용 관리 포트로 분리된다(`local`/`test`는 메인 8080 포트 유지 — 기존 테스트·로컬 동작 보존). 관리 포트는 **내부 전용**이며 인증을 요구하지 않는다 — 메인 보안 체인(OAuth2/STATELESS/`Http403ForbiddenEntryPoint`)이 적용되면 Alloy가 403을 맞으므로, `EndpointRequest.toAnyEndpoint()`를 매칭하는 별도 `permitAll` 체인(`ManagementSecurityConfiguration`, `@Order(1)`, `@Profile("!test")`)을 둔다. 메인 `SecurityConfiguration.permittedUrls`에서는 actuator가 더 이상 메인 포트에 없으므로 `/health/**`·`/info/**`를 제거했다.

공개 노출은 nginx가 `/info` 하나만 관리 포트로 reverse proxy한다(allow-list, default-closed). `/health`는 컨테이너 내부 healthcheck(`localhost:8081/health`) 전용, `/prometheus`는 내부 Alloy scrape 전용 — 둘 다 공개 ingress로 도달 불가.

### 엔드포인트

- `/info` — Spring Boot Actuator. 빌드 시점에 박힌 git 메타(`build.commit`, `build.branch`)와 artifact 정보(`build.artifact`, `build.name`, `build.version`, `build.time`)를 반환. **인증 없음(관리 체인 permitAll)**. 값은 빌드 시 `back/Dockerfile`의 `ARG GIT_COMMIT`/`ARG GIT_BRANCH` → Gradle property → `springBoot.buildInfo.additional`로 jar 안 `META-INF/build-info.properties`에 박히며, 런타임 ENV로 변조 불가. CI(`back-push-develop.yml`)에서 `${{ github.sha }}`/`${{ github.ref_name }}`로 주입. 로컬 빌드는 `unknown` fallback. dev에서는 관리 포트(8081)·공개는 nginx 경유.
- `/health` — Liveness. components 표시는 익명, 상세는 인증 필요. Redis pub/sub 헬스 컴포넌트 포함. dev에서는 관리 포트(8081)에서 제공, 공개 미노출.
- `/prometheus` — Micrometer/Actuator Prometheus exposition. `io.micrometer:micrometer-registry-prometheus`(runtimeOnly)로 활성화하고 `management.endpoints.web.exposure.include`에 `prometheus` 포함. JVM(heap/GC/스레드)·HTTP 요청·HikariCP·logback·tomcat 등 기본 Micrometer 메트릭을 노출하며, 내부 네트워크의 Alloy만 scrape한다(공개 미노출). 카디널리티 가드로 `management.metrics.distribution.percentiles-histogram.http.server.requests: false`를 명시(히스토그램 `_bucket` 미생성). **신규 엔드포인트의 URI는 반드시 템플릿화**(`@PathVariable`)하고 path에 UUID/이메일 등 unbounded 값을 직접 박지 않는다 — `http_server_requests`의 `uri` 라벨 카디널리티 무한 증식 방지.

> 테스트에서 prometheus endpoint를 검증할 때는 `@AutoConfigureObservability`를 붙여야 한다 — `@SpringBootTest`는 기본적으로 metrics export를 끈다(`management.defaults.metrics.export.enabled=false`).

## Observability (로그·메트릭)

dev 프로파일의 로그는 더 이상 Logstash로 직접 TCP 송신하지 않는다. logback은 **stdout에 JSON 라인**으로 출력하며(`logback-spring.xml`의 `ConsoleAppender` + `LogstashEncoder`, access 로그는 `logback-access-dev.xml`의 `ConsoleAppender` + `LogstashAccessEncoder`), 인프라의 **Grafana Alloy** 에이전트가 Docker socket으로 컨테이너 stdout을 수집해 Grafana Cloud Loki로 전송한다. 시스템 메트릭은 Alloy가 node-exporter를 scrape해 Grafana Cloud Mimir로 push한다. **앱 레벨 메트릭**(JVM/Spring)은 Alloy가 `spring-app` replica별 `8081/prometheus`를 scrape → allow-list relabel → Mimir로 push한다(아래 `/prometheus` 참조). 운영자는 Grafana Cloud의 Grafana UI에서 로그·메트릭을 조회한다 (self-hosted Kibana/Grafana/Prometheus 없음).

- 앱 로그/access 로그는 JSON의 `logType` 필드(`app-log`/`access-log`)로 구분되며 Loki에서 `log_type` 라벨로 승격된다.
- `requestId`·`requestPlayerId` 같은 MDC 필드는 high-cardinality이므로 라벨로 올리지 않고 JSON 본문에 남긴다 (LogQL `| json | requestId="X"`로 사후 필터).
- `net.logstash.logback:logstash-logback-encoder`는 TCP appender 대신 JSON encoder로만 재사용한다. access 로그를 logback-access encoder로 직렬화하므로 `logback-access-common`/`logback-access-tomcat` 의존성은 유지한다 (`AccessLogConfiguration`의 `LogbackValve`도 의존).
- **local/test 프로파일은 변경 없음** — 사람이 읽는 콘솔 패턴 출력 그대로.

## 데이터베이스 마이그레이션

Flyway 마이그레이션 파일은 `src/main/resources/db/migration/`에 위치 (현재 V1~V9). 시작 시 스키마 검증 (`ddl-auto: validate`).

## 프로파일

- `local` / `test`: 모든 외부 의존성에 Testcontainers 사용, 실제 자격 증명 불필요
- `dev`: 환경 변수를 통해 모든 외부 서비스를 사용하는 프로덕션 유사 환경

## Detekt 설정

설정 파일: `detekt/config.yml`. 컨트롤러/저장소가 `private class`이므로 `UnusedPrivateClass` 규칙에서 `@RestController`와 `@Repository` 어노테이션을 무시하도록 설정.
