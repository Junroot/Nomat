## Why

현재 백엔드는 MySQL → Elasticsearch 플레이리스트 동기화를 Debezium 임베디드 + Kafka로 구현하고 있다. 코드를 들여다보면 핵심 사실이 드러난다: **Kafka는 메시지 브로커로 쓰이지 않는다**. `back/src/main/kotlin/ilpak/nomat/infrastructure/cdc/CdcConfiguration.kt`에서 `KafkaOffsetBackingStore`와 `schema.history.internal.kafka.*`로만 쓰이며, 실제 변경 이벤트는 같은 JVM 안의 `DebeziumSourceEventListener.handleChangeEvent`가 직접 받아 ES에 쓴다. 즉 메시지 큐로서의 가치를 활용하지 않으면서 Kafka 클러스터 운영비를 지불하는 상태이며, Debezium 임베디드 동작이 블랙박스라 디버깅 부담까지 함께 발생한다.

추가로 `DebeziumSourceEventListener.delete()`는 `favoritePlaylistService.deleteByPlaylistId(id)`를 호출한다 — 도메인 정합성(고아 favorite 정리)이 CDC 어댑터에 묻혀 있어 헥사고날 경계가 침범된 상태다. 기존 코드를 그대로 따르지 말고 더 나은 구조로 개선하는 본 프로젝트 원칙(`CLAUDE.md`)에도 어긋난다.

운영 환경 측면에서 백엔드는 Docker Swarm `replicas: 2`로 운영 중이고, 다른 도메인의 비동기 사이드 이펙트와 다른 엔티티의 ES 동기화가 향후 늘어날 가능성이 명시적으로 있다. 따라서 본 변경은 단순히 Kafka 한 컴포넌트를 빼는 게 아니라, **모든 비동기 도메인 이벤트가 신뢰성 있게 흐르도록 하는 일반화된 outbox 인프라**를 도입하는 일이다. ES 동기화 lag은 준 실시간(수 초) 허용이라 polling 기반 worker도 충분하다.

## What Changes

- `Spring Modulith Event Publication Registry`(JPA 기반)를 outbox 인프라로 도입 — 비즈니스 트랜잭션과 원자적으로 publication entry를 INSERT, 별도 트랜잭션의 비동기 핸들러로 실행, 핸들러별 격리 추적
- `Playlist` 도메인 모델을 `AbstractAggregateRoot<Playlist>` 상속으로 변경하고 `PlaylistUpserted`·`PlaylistDeleted` 이벤트를 발행
- `EsPlaylistSyncHandler` 신설 (`playlist/in/`) — `@ApplicationModuleListener`로 ES upsert/delete 처리. `DebeziumSourceEventListener`의 ES 동기화 책임을 이관
- `PlaylistDeletedHandler` 신설 (`favoriteplaylist/in/`) — `@ApplicationModuleListener`로 고아 favorite 정리. 기존 `DebeziumSourceEventListener.delete()`에서 호출하던 `favoritePlaylistService.deleteByPlaylistId(id)` 책임을 이관
- 미완료 publication 재시도 스케줄러 신설 (`infrastructure/events/EventPublicationRetryScheduler`) + ShedLock(Redis)으로 멀티 인스턴스 환경에서 단일 인스턴스만 실행
- Flyway `V10__create_event_publication.sql` 추가 — Modulith JPA 표준 스키마를 직접 관리 (`schema-initialization.enabled=false`)
- application.yml에 `spring.modulith.events.completion-mode=DELETE`, `republish-outstanding-events-on-restart=false`, `spring.task.execution.pool.*` 추가
- 의존성 추가: `spring-modulith-starter-jpa`, `shedlock-spring`, `shedlock-provider-redis-spring`
- **본 PR 범위 내에서는 Debezium·Kafka 의존성과 인프라 stack을 제거하지 않는다** — dual-write 단계로 운영 검증 후 별도 후속 PR(Phase B)에서 제거
- `back/CLAUDE.md`에 두 가지 도메인 이벤트 패턴(`@TransactionalEventListener` ephemeral용, `@ApplicationModuleListener` 정합성 사이드 이펙트용)의 사용 기준을 명시

## Capabilities

### New Capabilities

- `domain-event-outbox`: 도메인 이벤트를 비즈니스 트랜잭션과 원자적으로 publication 레지스트리에 기록하고, 별도 트랜잭션의 비동기 핸들러로 실행하며, 멀티 인스턴스 환경에서 미완료 이벤트를 단일 인스턴스의 스케줄러로 재시도하는 능력
- `playlist-search-sync`: 플레이리스트 생성·수정·삭제를 Elasticsearch 인덱스에 비동기로 동기화하는 능력 (기존 Debezium 기반 흐름을 대체하는 신규 메커니즘이 본 변경에서 처음 명문화됨)

### Modified Capabilities

기존 `openspec/specs/`에는 `pr-auto-review`, `redis-pubsub-health` 두 capability만 존재하며 본 변경과 무관하다. Debezium 기반 동기화는 별도 spec으로 정의된 적이 없으므로 본 변경에서 처음 capability로 명문화된다.

## Impact

- **서브프로젝트**: `back/` 코드·의존성·application.yml에 영향. `infra/` 변경은 본 PR 범위 외(Phase B에서 Kafka 컨테이너 제거). `front/` 영향 없음
- **도메인 모듈**: `playlist`(이벤트 발행, ES 핸들러), `favoriteplaylist`(고아 정리 핸들러). `room`, `player`, `auth`는 영향 없음. `room`의 `RoomEventListener`는 ephemeral broadcast 용도이므로 본 변경에서 outbox로 이관하지 않음
- **헥사고날 계층**:
  - `application/domain/`: `Playlist`가 `AbstractAggregateRoot` 상속, 이벤트 클래스 추가
  - `in/`: `EsPlaylistSyncHandler`(playlist 모듈), `PlaylistDeletedHandler`(favoriteplaylist 모듈) 신설 — 모두 `private class`
  - `infrastructure/`: `cdc/` 패키지는 본 PR에서 유지(dual-write), `events/` 패키지 신설(재시도 스케줄러 + ShedLock 구성)
  - `application/PlaylistService.kt`: 명시적 `OutboxRepository.save()` 호출 없음. `AbstractAggregateRoot`의 표준 메커니즘으로 자동 발행
- **DB 스키마**: `event_publication` 테이블 신설(V10 Flyway). 무중단 — 빈 테이블 추가만 발생. 롤백은 단순 `DROP TABLE`. 기존 도메인 테이블 변경 없음
- **ES 매핑**: 변경 없음. `PlaylistDocument` 스키마 그대로
- **Kafka 토픽**: 본 PR에서 제거 안 함. Phase B에서 `mysql_playlist*`, `nomat_mysql_offset`, `nomat_mysql_schema_history` 모두 제거 예정
- **Redis 키**: ShedLock 락 키 `shedlock:event-publication-retry` 신규 사용 (TTL 1분, 운영 가시성 영향 미미)
- **의존성**:
  - 추가: `org.springframework.modulith:spring-modulith-starter-jpa`, `net.javacrumbs.shedlock:shedlock-spring`, `net.javacrumbs.shedlock:shedlock-provider-redis-spring`
  - 제거(본 PR 안 함): Debezium·Kafka 관련 의존성은 Phase B에서
- **운영 동작**:
  - dual-write 기간 동안 동일 ES 문서가 두 경로(Debezium, Modulith)에서 멱등하게 쓰임 — race 발생 시 마지막 write가 이김. `ElasticsearchOperations.save()`는 멱등하므로 데이터 손실 없음
  - 컨테이너 메모리·CPU 사용량 소폭 증가(추가 풀 + 핸들러). dev 운영 후 정량 측정해 Phase B 진입 판단
  - sync lag은 `@ApplicationModuleListener`가 AFTER_COMMIT 즉시 디스패치이므로 정상 경로에서는 Debezium과 비슷하거나 더 빠름. 폴링은 *실패한* 이벤트만 영향
