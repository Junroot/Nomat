## Context

현재 백엔드는 MySQL → Elasticsearch 플레이리스트 동기화를 다음과 같이 구현하고 있다:

- `back/src/main/kotlin/ilpak/nomat/infrastructure/cdc/CdcConfiguration.kt`: Debezium MySqlConnector를 임베디드로 부팅. `offset.storage = KafkaOffsetBackingStore`, `schema.history.internal.kafka.*`로 Kafka에 상태 저장
- `back/src/main/kotlin/ilpak/nomat/infrastructure/cdc/DebeziumSourceEventListener.kt`: `notifying(::handleChangeEvent)`로 같은 JVM 안에서 binlog 변경을 수신, `ElasticsearchOperations.save/delete` 호출
- `DebeziumSourceEventListener.delete()`는 ES 삭제와 함께 `favoritePlaylistService.deleteByPlaylistId(id)`도 호출 — 도메인 정합성 정리가 CDC 어댑터에 묻혀 있음

운영 측면:
- 백엔드는 `infra/app/compose.yml` 기반 Docker Swarm에서 `replicas: 2`로 운영
- Kafka는 `infra/data` stack에서 별도 컨테이너로 운영 — 메시지 브로커로는 활용되지 않으면서 운영비 발생
- Redis와 분산 락(`RedisDistributedLockExecutor`)은 이미 사용 중
- `room` 도메인은 이미 `AbstractAggregateRoot` + `@TransactionalEventListener(AFTER_COMMIT)` 패턴으로 in-memory 도메인 이벤트를 사용 중 (`RoomEventListener.kt`) — ephemeral broadcast 용도

이해관계자/제약:
- 답변자(개발자): Kafka 운영비 + Debezium 디버깅 부담 동시 보고
- 향후 다른 도메인 비동기 이펙트와 다른 엔티티의 ES 동기화 증가 예상
- ES sync는 준 실시간(수 초 lag) 허용
- 테스트 정책: no mocking, Testcontainers 기반
- Spring Boot 3.4 / Kotlin 1.9 / MySQL 8 + Flyway / Java 17

## Goals / Non-Goals

**Goals:**
- 모든 비동기 도메인 이벤트가 신뢰성 있게 흐르도록 일반화된 outbox 인프라 도입
- Debezium 임베디드 엔진과 Kafka 클러스터를 제거 가능한 상태로 만들기 — 본 PR은 dual-write 단계, Phase B PR에서 실제 제거
- 한 도메인 이벤트가 여러 핸들러를 트리거하는 fan-out + 핸들러별 격리 추적
- 멀티 인스턴스 환경에서 미완료 이벤트 재시도가 중복 실행되지 않음
- favorite 정리 책임을 `favoriteplaylist` 모듈로 이관해 CDC 어댑터의 책임 침범 제거
- ES 인덱스 데이터 손실 없는 무중단 컷오버

**Non-Goals:**
- `RoomEventListener`의 `@TransactionalEventListener(AFTER_COMMIT)` → `@ApplicationModuleListener` 마이그레이션은 본 변경 범위 외. 채팅·입퇴장 broadcast는 ephemeral 신호로 in-memory 충분
- 외부 시스템으로의 이벤트 publish (Kafka externalization 등). 필요해지면 별도 변경
- Debezium → 다른 CDC 도구(Maxwell, Canal 등) 교체. CDC 자체를 제거하므로 무관
- playlist 외 도메인의 일거 outbox 이벤트화. 점진적으로 별도 변경에서
- ES 인덱스 매핑 변경 또는 `PlaylistDocument` 스키마 변경
- 본 PR에서 Debezium·Kafka 의존성·infra stack 제거 — Phase B로 분리

## Decisions

### Decision 1: Spring Modulith Event Publication Registry를 outbox 인프라로 채택

직접 outbox 테이블·워커·디스패처를 구현하는 대신 Spring Modulith의 Event Publication Registry(`spring-modulith-starter-jpa`)를 사용한다.

근거:
- 비즈니스 트랜잭션 안에서 publication entry를 자동 INSERT (BEFORE_COMMIT, atomic 보장)
- 리스너별로 별도 entry 생성 → 핸들러별 격리 + 핸들러별 재시도 자연 지원
- `@ApplicationModuleListener`가 `@Async + @Transactional(REQUIRES_NEW) + @TransactionalEventListener` 표준 셋업을 한 줄로 제공
- 외부 broker 없이 내부 비동기 보장만으로 동작 (`spring-modulith-starter-jpa`만 필요)
- 현재 `Room`이 사용 중인 `AbstractAggregateRoot.registerEvent()` 컨벤션과 호환 — 학습 비용 낮음

**대안 검토:**
- *자체 outbox 테이블 + 워커 + 디스패처*: 핸들러 라우팅·재시도 스케줄링·핸들러별 격리를 모두 직접 구현. 코드량 ↑, 검증된 인프라 미활용. 장기 유지보수 부담. 기각.
- *Spring Modulith의 외부 event externalization (Kafka/AMQP 어댑터)*: Kafka 제거가 목표인데 다시 Kafka 의존. 기각.
- *Debezium offset storage만 JDBC로 교체 (`io.debezium.contrib.jdbc.JDBCOffsetBackingStore`)*: Kafka는 빠지지만 Debezium 디버깅 부담 그대로. 답변자의 두 부담 중 하나만 해결. 향후 도메인 이벤트 일반화도 못 함. 기각.

### Decision 2: 핸들러별 격리를 핵심 설계 가정으로 받아들임

한 도메인 이벤트(`PlaylistDeleted`)가 여러 `@ApplicationModuleListener`를 트리거할 수 있다. 각 리스너는 `event_publication` 테이블에 별도 row로 추적되며, 한 핸들러의 실패가 다른 핸들러의 실행을 막지 않는다.

본 변경의 핸들러:
- `EsPlaylistSyncHandler` (playlist 모듈): ES 인덱스 upsert/delete
- `PlaylistDeletedHandler` (favoriteplaylist 모듈): playlist 삭제 시 고아 favorite 정리

향후 다른 핸들러 추가는 별도 변경에서 처리하되 동일 패턴을 적용한다. `@ApplicationModuleListener(id = ...)`로 명시적 listener id를 지정해 메서드 위치 이동 시에도 publication 추적이 유지되게 한다.

### Decision 3: 재시도 스케줄러를 자체 구현하고 ShedLock으로 단일 인스턴스 보장

Spring Modulith는 기본적으로 `republish-outstanding-events-on-restart`(재시작 시 일괄 재발행)만 자동 지원하며, 이는 단일 인스턴스 배포에만 권장된다 — 멀티 인스턴스에서 모든 인스턴스가 동시에 재발행하면 중복 처리 위험.

따라서 운영 중 재시도는 직접 작성한다:
- `back/src/main/kotlin/ilpak/nomat/infrastructure/events/EventPublicationRetryScheduler.kt` 신설 (`private class`)
- `@Scheduled(fixedDelay = 30_000)` + `@SchedulerLock(name = "event-publication-retry", lockAtMostFor = "PT1M")` 메서드가 `IncompleteEventPublications.resubmitIncompletePublications(Duration.ofMinutes(5))` 호출
- 5분 이상 미완료된 항목만 재시도 — 정상 처리 중인 이벤트와의 race 최소화
- `spring.modulith.events.republish-outstanding-events-on-restart=false`로 부팅 시 일괄 재발행 비활성화

ShedLock 락 저장소: 이미 사용 중인 Redis. `shedlock-provider-redis-spring`의 `RedisLockProvider` 빈을 `infrastructure/events/ShedLockConfiguration.kt`에 구성 — 기존 `RedisConnectionFactory` 빈 재사용.

**대안 검토:**
- *MySQL `SELECT FOR UPDATE SKIP LOCKED`로 모든 인스턴스 병렬 재시도*: 처리량 ↑. 현재 트래픽 규모와 sync lag 허용도 감안 시 과도한 복잡도. 기각.
- *`republish-outstanding-events-on-restart=true`만 사용*: 운영 중 실패한 이벤트가 다음 재배포까지 묶여 ES sync 누락 방치. 기각.
- *기존 `RedisDistributedLockExecutor`로 직접 락*: ShedLock의 `@SchedulerLock` 애노테이션 인프라가 더 표준적이고 검증됨. 자체 lock executor도 활용은 가능하지만 일관성을 위해 ShedLock 채택. 기각.

### Decision 4: completion-mode = DELETE

`spring.modulith.events.completion-mode=DELETE` (Modulith 1.3+) 사용. 완료된 publication entry를 즉시 DELETE하여 `event_publication` 테이블 무한 증가를 차단.

근거:
- 디버깅 가시성은 미완료 entry(NULL completion_date)에서 100% 확보. 완료된 항목은 디버그 가치가 낮음
- 감사 요구사항이 현재는 없음 (필요해지면 ARCHIVE로 전환)
- 기본 UPDATE 모드는 별도 청소 스케줄러를 또 만들고 또 ShedLock으로 보호해야 함 — 복잡도 ↑

**대안 검토:**
- *ARCHIVE 모드 (`event_publication_archive`로 이동)*: 감사 가능. 현재 불필요. 기각.
- *기본 UPDATE 모드 + 주기 청소 스케줄러*: 청소 스케줄러도 ShedLock 보호 필요. 인프라 부피 ↑. 기각.

### Decision 5: 이벤트 클래스 위치와 직렬화 안정성 가이드

Spring Modulith는 `event_publication.event_type`에 이벤트 클래스의 FQCN을, `serialized_event`에 Jackson JSON 직렬화 본체를 저장한다. 클래스 위치/이름 변경, 필드 타입 변경이 누적된 미완료 이벤트의 deserialization 실패(부팅 시 `JpaSystemException: Unable to locate named class`)를 일으킬 수 있다.

규칙:
- 이벤트 클래스는 `<domain>/application/domain/` 패키지에 위치 — 안정된 위치
- `@ApplicationModuleListener(id = "<명시적-식별자>")`로 listener id를 박제 — 메서드 위치 이동에 강건
- 필드 추가는 nullable 또는 default 값으로 (Kotlin nullable 또는 `@JsonProperty(required = false)`)
- 필드 삭제·이름 변경·타입 변경은 미완료 publication 0인 시점에서만 (운영 절차)
- 본 가이드를 `back/CLAUDE.md`에 추가하여 향후 기여자에게 공유

### Decision 6: 무중단 컷오버 — Phase A(본 PR) + Phase B(후속 PR)

ES 인덱스 데이터 손실 없이 Debezium → Modulith 전환을 두 단계로 나눈다:

- **Phase A(본 PR)**: Modulith 인프라 + 핸들러 + 도메인 이벤트 발행을 코드에 추가하되, **Debezium은 그대로 유지**해 dual-write 상태로 운영
  - `EsPlaylistSyncHandler`(Modulith)와 `DebeziumSourceEventListener`(Debezium)가 동시에 ES에 쓰는 race가 발생할 수 있음 → 둘 다 `ElasticsearchOperations.save()` 멱등 호출이라 데이터 손실 없음. 마지막 write가 이김
  - `DebeziumSourceEventListener.delete()`에서 `favoritePlaylistService.deleteByPlaylistId(id)` 호출은 본 PR에서 **제거**(이관 완료) — favorite 정리는 `PlaylistDeletedHandler`만 담당
  - Phase A 종료 기준: dev에서 24시간 운영 후 미완료 publication 항목의 가장 오래된 age < 1분 + ES 문서 카운트 ≈ MySQL playlist row 카운트
- **Phase B(별도 후속 PR)**: Debezium·Kafka 의존성 제거 + `infrastructure/cdc/` 패키지 삭제 + `infra/data/`의 Kafka stack 제거 + `org.testcontainers:kafka` 제거

**대안 검토:**
- *한 PR에서 Debezium 즉시 제거*: 컷오버 시점에 Modulith가 미동작이거나 부분 실패면 ES sync 끊김. 안전망 없음. 기각.
- *ES 백필 스크립트로 일거 전환*: 기존 데이터를 처음부터 재인덱싱하는 비용·시간 부담. dual-write가 더 안전. 기각.

### Decision 7: 이벤트 페이로드는 자기충족적

`@ApplicationModuleListener`는 별도 스레드(`@Async`)에서 별도 트랜잭션(`REQUIRES_NEW`)으로 실행되므로 `SecurityContext`, `MDC`, `RequestContext`가 자동 전파되지 않는다. 핸들러에서 그런 컨텍스트를 참조하면 동작하지 않거나 null이 된다.

규칙:
- 이벤트 페이로드에 핸들러가 필요로 하는 모든 정보를 담는다
- `PlaylistUpserted`는 ES indexing에 필요한 모든 필드(id, ownerId, title, tracks 등)를 포함
- `PlaylistDeleted`는 id만 포함 (favorite 정리·ES 삭제 모두 id만 필요)
- 핸들러는 추가 DB 조회를 가능한 한 피한다 — 핸들러 실행 시점에는 도메인 상태가 이미 변경된 후이므로

### Decision 8: TaskExecutor 풀 크기를 명시적으로 지정

Spring Modulith는 `@ApplicationModuleListener`가 사용하는 `TaskExecutor`를 다른 `@Async` 메서드들과 공유한다(별도 풀 분리는 Modulith issue #641로 미해결). 풀이 작으면 outbox lag이 적체되어 디버깅이 어려워진다.

`application.yml`에 `spring.task.execution.pool.core-size: 4`, `max-size: 16`, `queue-capacity: 1000` 명시. 향후 outbox 트래픽 증가 시 dedicated executor 분리 검토(별도 변경).

## Risks / Trade-offs

- **[Risk] 멀티 인스턴스 + 재시도 스케줄러의 중복 실행** → Mitigation: ShedLock으로 단일 인스턴스만 실행 (Decision 3). Modulith 2.0의 PROCESSING 상태가 일부 race를 완화하지만 100% 보장은 어려움. 핸들러는 멱등하게 작성: `ElasticsearchOperations.save()` 멱등, `FavoritePlaylistRepository.deleteByPlaylistId()` 멱등.
- **[Risk] 이벤트 클래스 직렬화 호환성 깨짐 → 부팅 실패** → Mitigation: Decision 5 가이드 + `event_publication WHERE completion_date IS NULL` 모니터링. 미완료 0인 상태에서만 스키마 변경 PR 진행하도록 운영 룰 + `back/CLAUDE.md`에 명시.
- **[Risk] dual-write 단계에서 Debezium과 Modulith가 동일 ES 문서를 동시에 쓰는 race** → Mitigation: 두 경로 모두 `ElasticsearchOperations.save()` 멱등 호출. version_type 미사용. lost-update 발생해도 다음 변경 이벤트가 자기치유. 사용자 영향 없음(lag만).
- **[Risk] TaskExecutor 풀 공유로 outbox lag 적체** → Mitigation: Decision 8의 풀 크기 명시 + 모니터링 지표(미완료 publication 수, 가장 오래된 미완료 age).
- **[Risk] `SecurityContext`/`MDC` 비전파로 핸들러 디버깅·감사 곤란** → Mitigation: Decision 7. 페이로드 자기충족화. 향후 필요 시 `TaskDecorator`로 MDC 전파 별도 구현(본 변경 범위 외).
- **[Risk] V10 Flyway 마이그레이션이 운영 DB에 적용되는 동안 부팅 지연** → Mitigation: `event_publication` 테이블은 빈 신규 테이블이라 즉시 완료. 기존 데이터 영향 없음.
- **[Trade-off] 본 PR은 Debezium·Kafka 자체를 제거하지 않음** → Phase B(별도 PR) 분리. 운영 검증 윈도우 확보. 이중 부하·메모리 증가 일시 수용.
- **[Trade-off] `favoriteplaylist` 모듈이 `playlist` 모듈의 도메인 이벤트(`PlaylistDeleted`)에 의존** → 모듈 간 의존성이 *이벤트 타입*을 통해 발생. Spring Modulith의 모듈 검증 도구가 이를 명시적 dependency로 인식. 헥사고날 + Modulith 관점에서 자연스러운 형태이며, 직접 메서드 호출보다 디커플링은 강함.

## Migration Plan

본 변경(Phase A) 무중단 배포 절차:

1. **PR 머지** → CI가 `develop`에서 도커 이미지 빌드 → EC2에 `docker stack deploy`
2. **롤아웃**:
   - V10 Flyway 마이그레이션이 `event_publication` 테이블 생성 (빈 테이블, 즉시 완료)
   - 새 컨테이너 부팅 시 Debezium 임베디드 + Modulith 핸들러 둘 다 활성화
   - 새 playlist 변경부터 dual-write 시작 — Debezium binlog 경로 + Modulith 도메인 이벤트 경로 모두 ES 반영
   - `DebeziumSourceEventListener.delete()`의 favorite 정리 호출은 제거된 상태 — `PlaylistDeletedHandler`(Modulith)만 favorite 정리 담당
3. **Phase A 종료 기준**:
   - dev에서 24시간 운영 후 `event_publication WHERE completion_date IS NULL`의 가장 오래된 항목 age < 1분
   - ES 문서 카운트 ≈ MySQL `playlist` row 카운트
   - favorite_playlist 고아 row 0건
4. **Phase B**: 위 기준 충족 후 별도 PR로 Debezium·Kafka 제거 (코드 + 의존성 + `infra/data` Kafka stack)
5. **롤백 전략**:
   - 본 변경은 백엔드 단일 PR이므로 `git revert` 후 동일 파이프라인으로 재배포
   - V10 마이그레이션 롤백은 단순 `DROP TABLE event_publication` (별도 V11 down 마이그레이션 작성하지 않음 — Flyway 표준 관례)
   - Debezium은 본 PR에서 제거되지 않았으므로 revert 후에도 ES sync 정상 동작
   - `DebeziumSourceEventListener.delete()`에서 favorite 정리를 다시 호출하도록 revert가 자동 복원

## Open Questions

- ShedLock + Modulith의 race condition(이미 PROCESSING인 entry를 재시도 스케줄러가 다시 집을 수 있음)이 실제 운영에서 얼마나 빈번한지 — 본 변경에서는 핸들러 멱등성으로 방어. 빈도 측정은 운영 후 데이터로
- `event_publication` 테이블의 기본 인덱스가 트래픽 증가 시 성능 병목이 될 가능성 — Modulith 표준 인덱스(`listener_id, serialized_event` 복합 + `completion_date`)가 충분한지 부하 테스트는 Phase B 진입 전 결정
- `spring.modulith.events.completion-mode=DELETE` 사용은 Modulith 1.3+ 필요. Spring Boot 3.4와 호환되는 Modulith 최신 버전(현재 2.0.x로 가정) 의존성 확정은 task 1.1에서
- `@ApplicationModuleListener`의 SpEL `condition` 사용처가 본 변경에서는 없음. 핸들러가 늘어나면서 도입할 가치가 있는지는 향후 검토
- Phase A 검증 기간 동안 dev 외 staging 환경 별도 운영이 가치 있는지 — 현재 dev/prod 두 환경뿐. dev에서 충분한 트래픽이 발생하는지에 따라 결정
