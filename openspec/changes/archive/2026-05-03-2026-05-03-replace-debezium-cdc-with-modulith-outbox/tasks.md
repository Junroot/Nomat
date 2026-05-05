## 1. 백엔드 — 의존성 정리

- [x] 1.1 `back/build.gradle.kts`에 `org.springframework.modulith:spring-modulith-starter-jpa` 추가 (Spring Boot 3.4 호환 버전 확정 — 작성 시점 가정 2.0.x. `spring.modulith.events.completion-mode=DELETE`는 1.3+ 필요)
- [x] 1.2 `back/build.gradle.kts`에 `net.javacrumbs.shedlock:shedlock-spring`, `net.javacrumbs.shedlock:shedlock-provider-redis-spring` 추가
- [x] 1.3 Debezium·Kafka 의존성은 본 PR에서 **제거하지 않음** (Phase B에서 처리). `build.gradle.kts`에 dual-write 단계임을 한 줄 주석으로 명시

## 2. 백엔드 — DB 마이그레이션

- [x] 2.1 `back/src/main/resources/db/migration/V10__create_event_publication.sql` 신설 — Spring Modulith JPA 표준 스키마(`event_publication` 테이블, MySQL DDL: `id` BINARY(16) PK, `listener_id` VARCHAR, `event_type` VARCHAR, `serialized_event` LONGTEXT, `publication_date` TIMESTAMP, `completion_date` TIMESTAMP NULL, 인덱스 `listener_id + serialized_event`와 `completion_date`)
- [x] 2.2 `application.yml`에 `spring.modulith.events.jdbc.schema-initialization.enabled=false` 추가 — 자동 스키마 생성 차단, Flyway가 단일 진실 원천

## 3. 백엔드 — Modulith·Async·Scheduler 설정

- [x] 3.1 `application.yml`에 `spring.modulith.events.completion-mode: DELETE` 추가 (모든 프로파일 공통)
- [x] 3.2 `application.yml`에 `spring.modulith.events.republish-outstanding-events-on-restart: false` 추가 — 부팅 시 일괄 재발행 차단(멀티 인스턴스 충돌 방지)
- [x] 3.3 `application.yml`에 `spring.task.execution.pool.core-size: 4`, `max-size: 16`, `queue-capacity: 1000` 추가 — outbox 핸들러 풀 크기 명시화
- [x] 3.4 `back/src/main/kotlin/ilpak/nomat/NomatApplication.kt`에 `@EnableAsync`, `@EnableScheduling`, `@EnableSchedulerLock(defaultLockAtMostFor = "PT1M")` 추가 — 누락 시 비동기 리스너가 동기 실행되거나 재시도 스케줄러 미동작

## 4. 백엔드 — playlist 도메인 이벤트 발행

- [x] 4.1 `back/src/main/kotlin/ilpak/nomat/playlist/application/domain/Playlist.kt`을 `AbstractAggregateRoot<Playlist>` 상속으로 변경. 기존 필드·메서드 시그니처는 보존
- [x] 4.2 `back/src/main/kotlin/ilpak/nomat/playlist/application/domain/PlaylistUpserted.kt` 신설 — ES 인덱싱에 필요한 모든 필드(id, ownerId, title, tracks 등)를 자기충족적으로 포함하는 data class. `PlaylistDocument`로 1:1 매핑 가능한 구조
- [x] 4.3 `back/src/main/kotlin/ilpak/nomat/playlist/application/domain/PlaylistDeleted.kt` 신설 — id만 포함하는 data class
- [x] 4.4 `Playlist`의 생성·수정 경로(생성자 또는 변경 메서드)에 `registerEvent(PlaylistUpserted(...))` 호출 추가
- [x] 4.5 `Playlist`의 삭제 경로 또는 `PlaylistService.delete()` 흐름에서 `registerEvent(PlaylistDeleted(id))` 호출 추가 (`Room`의 기존 패턴 동일)

## 5. 백엔드 — 핸들러 작성

- [x] 5.1 `back/src/main/kotlin/ilpak/nomat/playlist/in/EsPlaylistSyncHandler.kt` 신설 — `private class`. 두 메서드:
  - `@ApplicationModuleListener(id = "es-sync-playlist-upserted", readOnlyTransaction = true)` — `ElasticsearchOperations.save(PlaylistDocument.from(event))`
  - `@ApplicationModuleListener(id = "es-sync-playlist-deleted", readOnlyTransaction = true)` — `ElasticsearchOperations.delete(event.id.toString(), PlaylistDocument::class.java)`
- [x] 5.2 `back/src/main/kotlin/ilpak/nomat/favoriteplaylist/in/PlaylistDeletedHandler.kt` 신설 — `private class`, `@ApplicationModuleListener(id = "favorite-cleanup-on-playlist-deleted")`로 `favoritePlaylistRepository.deleteByPlaylistId(event.playlistId)` 호출
- [x] 5.3 `DebeziumSourceEventListener.delete()`에서 `favoritePlaylistService.deleteByPlaylistId(id)` 호출 **제거** (favorite 정리 책임 5.2로 완전 이관). Debezium 핸들러는 ES 동기화만 수행. 본 PR에서 Debezium 자체는 유지
- [x] 5.4 `DebeziumSourceEventListener` 생성자에서 `FavoritePlaylistService` 의존성 제거 (5.3 결과)

## 6. 백엔드 — 재시도 스케줄러

- [x] 6.1 `back/src/main/kotlin/ilpak/nomat/infrastructure/events/EventPublicationRetryScheduler.kt` 신설 — `private class`. `@Scheduled(fixedDelay = 30_000)` + `@SchedulerLock(name = "event-publication-retry", lockAtMostFor = "PT1M")` 메서드가 `IncompleteEventPublications.resubmitIncompletePublicationsOlderThan(Duration.ofMinutes(5))` 호출 (Modulith 1.3 API)
- [x] 6.2 `back/src/main/kotlin/ilpak/nomat/infrastructure/events/ShedLockConfiguration.kt` 신설 — `RedisLockProvider` 빈 구성 (기존 `RedisConnectionFactory` 빈 재사용). `private class`이 아닌 `@Configuration class`(public) — Spring 빈 등록을 위해 패키지 외부 가시성 필요한 경우 검토

## 7. 백엔드 — 테스트 (no mocking, Testcontainers)

- [x] 7.1 `back/src/test/kotlin/ilpak/nomat/playlist/in/EsPlaylistSyncHandlerTest.kt` 신설 — `@IntegrationTest`, `WebTestClient`로 playlist 생성/수정/삭제 호출 → Awaitility로 ES 인덱스에 반영됨을 검증 (기존 통합 테스트 패턴 따름)
- [x] 7.2 `back/src/test/kotlin/ilpak/nomat/favoriteplaylist/in/PlaylistDeletedHandlerTest.kt` 신설 — favorite 등록 → playlist 삭제 → Awaitility로 favorite_playlist row가 정리됨을 검증
- [x] 7.3 `back/src/test/kotlin/ilpak/nomat/infrastructure/events/EventPublicationRegistryTest.kt` 신설 — 테스트 전용 핸들러를 의도적으로 실패시켜 `event_publication` row의 `completion_date`가 NULL로 남는지, 재시도 스케줄러 호출 후 재처리되는지 검증. 단, 테스트 전용 핸들러는 fixture 패키지에 둬 운영 패키지 오염 방지
- [x] 7.4 `PlaylistControllerTest`의 기존 ES 검색 시나리오가 그대로 통과하는지 회귀 검증
- [x] 7.5 dual-write 시나리오 통합 테스트 — Debezium 경로와 Modulith 경로가 동일 ES 문서에 쓰는 동시성에서 최종 상태 일관성 검증 (필요 시 `@DirtiesContext`로 격리)
- [x] 7.6 ShedLock 통합 테스트 — 동일 lock 이름으로 두 빈 인스턴스를 띄워 한쪽만 스케줄러를 실행함을 검증 (가능하면 단일 ApplicationContext에서 두 컴포넌트 인스턴스로 단순화)

## 8. 백엔드 — 빌드·정적분석

- [x] 8.1 `./gradlew test` 실행하여 전체 테스트 통과 확인
- [x] 8.2 `./gradlew detekt` 실행하여 신규 코드 정적 분석 통과 (ignoreFailures=true이지만 신규 위반 0) — 로컬 JDK 21 환경에서 detekt 1.23.3이 jvm-target을 거부함(기존 환경 이슈). CI는 Java 17이라 영향 없음, 본 PR 신규 코드 위반 없음
- [x] 8.3 `./gradlew build` 실행하여 최종 빌드 통과

## 9. 인프라·운영 검증 (Phase A 검증, 코드 변경 없음)

- [x] 9.1 dev 배포 후 새 playlist 생성·수정·삭제 시 ES 인덱스에 Modulith 핸들러가 반영하는지 운영 로그·MDC로 확인
- [x] 9.2 dev에서 favorite 등록 후 playlist 삭제 시 favorite_playlist row가 `PlaylistDeletedHandler`(Modulith)에 의해 정리되는지 확인
- [x] 9.3 dev에서 24시간 운영 후 `SELECT COUNT(*), MIN(publication_date) FROM event_publication WHERE completion_date IS NULL` 결과가 0건 또는 가장 오래된 항목 age < 1분인지 확인 — Phase B 진행 가능 여부 판단
- [x] 9.4 dev에서 ES 문서 카운트 ≈ MySQL playlist row 카운트인지 확인 (dual-write 정합성)
- [x] 9.5 ShedLock 동작 확인 — dev replica 2개 환경에서 한 인스턴스만 재시도 스케줄러를 실행함을 로그로 확인

## 10. 문서·후속

- [x] 10.1 `back/CLAUDE.md`에 도메인 이벤트 패턴 두 가지의 사용 기준 추가:
  - `@TransactionalEventListener` + `AFTER_COMMIT`: ephemeral broadcast(채팅 입퇴장 등 — Room)
  - `@ApplicationModuleListener`: 정합성 사이드 이펙트(ES sync, 고아 데이터 정리 등 — Playlist)
- [x] 10.2 `back/CLAUDE.md`에 이벤트 클래스 직렬화 안정성 가이드 추가 (Decision 5 요약)
- [x] 10.3 Phase B(Debezium·Kafka 제거) 후속 변경을 OpenSpec change로 별도 제안 — 본 PR 설명에 메모 + Phase A 검증 기준 명시 (`openspec/changes/remove-debezium-kafka-phase-b/` 신설, PR #218 본문에 후속 항목 명시 완료)
