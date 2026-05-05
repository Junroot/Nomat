# playlist-search-sync Specification

## Purpose
TBD - created by archiving change 2026-05-03-replace-debezium-cdc-with-modulith-outbox. Update Purpose after archive.
## Requirements
### Requirement: 플레이리스트 생성·수정 시 ES 인덱싱
시스템은 플레이리스트가 생성되거나 수정될 때 Elasticsearch 인덱스에 동일한 내용으로 동기화해야(MUST) 한다. 동기화는 비동기로 수행되며, 비즈니스 트랜잭션 커밋 후에 실행된다.

#### Scenario: 플레이리스트 생성 시 ES upsert
- **WHEN** 사용자가 플레이리스트를 생성하는 API를 호출하고 `Playlist`가 MySQL에 저장
- **AND** 비즈니스 트랜잭션이 커밋
- **THEN** `EsPlaylistSyncHandler`가 비동기로 `ElasticsearchOperations.save(PlaylistDocument.from(event))`를 호출해야 한다
- **AND** ES 인덱스에는 새 플레이리스트 도큐먼트가 존재해야 한다 (Awaitility 기준 수 초 안에)

#### Scenario: 플레이리스트 수정 시 ES upsert
- **WHEN** 사용자가 플레이리스트를 수정 (제목·트랙 변경 등)
- **AND** 비즈니스 트랜잭션이 커밋
- **THEN** `EsPlaylistSyncHandler`가 동일 id의 ES 도큐먼트를 새 상태로 덮어쓰기 해야 한다

### Requirement: 플레이리스트 삭제 시 ES 인덱스 삭제
시스템은 플레이리스트가 삭제될 때 Elasticsearch 인덱스에서도 해당 도큐먼트를 삭제해야(MUST) 한다.

#### Scenario: 플레이리스트 삭제 시 ES 도큐먼트 삭제
- **WHEN** 사용자가 플레이리스트를 삭제하는 API를 호출하고 `Playlist`가 MySQL에서 DELETE
- **AND** 비즈니스 트랜잭션이 커밋
- **THEN** `EsPlaylistSyncHandler`가 비동기로 `ElasticsearchOperations.delete(event.id.toString(), PlaylistDocument::class)`를 호출해야 한다
- **AND** ES 인덱스에서 해당 id의 도큐먼트가 사라져야 한다

### Requirement: 플레이리스트 삭제 시 고아 favorite 정리
시스템은 플레이리스트가 삭제될 때 해당 플레이리스트를 참조하는 `favorite_playlist` row를 모두 정리해야(MUST) 한다. 정리 책임은 `favoriteplaylist` 모듈의 핸들러가 담당하며, ES 동기화 어댑터에 묶이지 않아야 한다.

#### Scenario: 플레이리스트 삭제 시 favorite 자동 정리
- **WHEN** 임의 플레이리스트를 즐겨찾기한 사용자가 있는 상태에서 플레이리스트가 삭제
- **THEN** `PlaylistDeletedHandler`(favoriteplaylist 모듈)가 비동기로 `favoritePlaylistRepository.deleteByPlaylistId(event.playlistId)`를 호출해야 한다
- **AND** `favorite_playlist` 테이블에서 해당 playlist_id를 참조하는 row가 모두 삭제되어야 한다

#### Scenario: favorite 정리는 CDC 어댑터에 묶이지 않음
- **WHEN** 코드베이스를 검사
- **THEN** `infrastructure/cdc/` 패키지의 어떤 코드도 `FavoritePlaylistService` 또는 `FavoritePlaylistRepository`에 의존하지 않아야 한다 (헥사고날 경계 회복)

### Requirement: 도메인 이벤트 기반 동기화 메커니즘
시스템은 ES 동기화와 favorite 정리를 `Playlist` 애그리거트가 발행하는 도메인 이벤트(`PlaylistUpserted`, `PlaylistDeleted`)에 기반해 수행해야(MUST) 한다. 이벤트는 `AbstractAggregateRoot.registerEvent()`로 등록되고 Spring Modulith Event Publication Registry로 신뢰성 있게 전달된다.

#### Scenario: 이벤트 발행 시점
- **WHEN** `Playlist`가 생성·수정·삭제됨
- **THEN** 해당 도메인 동작 안에서 `registerEvent(PlaylistUpserted(...))` 또는 `registerEvent(PlaylistDeleted(...))`가 호출되어야 한다
- **AND** `JpaRepository.save()` 호출 시 Spring Data 표준 메커니즘으로 이벤트가 `ApplicationEventPublisher`에 발행되어야 한다

#### Scenario: 이벤트 페이로드 자기충족성
- **WHEN** `PlaylistUpserted` 페이로드가 직렬화
- **THEN** 페이로드는 ES 인덱싱에 필요한 모든 필드(id, ownerId, title, tracks 등)를 자기충족적으로 포함해야 한다
- **AND** 핸들러는 ES 인덱싱을 위해 추가 DB 조회를 수행하지 않아야 한다

### Requirement: 본 PR(Phase A) 동안 Debezium 경로 유지
시스템은 본 변경의 Phase A 단계 동안 기존 Debezium 기반 ES 동기화를 그대로 유지하면서 Modulith 기반 동기화를 함께 수행해야(SHALL) 한다. dual-write 단계는 운영 검증을 위한 안전망이다.

#### Scenario: dual-write 멱등성
- **WHEN** Phase A 운영 중 Debezium이 binlog로 ES에 쓰는 경로와 `EsPlaylistSyncHandler`가 도메인 이벤트로 ES에 쓰는 경로가 동일 도큐먼트에 동시에 쓰기 발생
- **THEN** 두 경로 모두 `ElasticsearchOperations.save()` 멱등 호출이므로 데이터 손실이 없어야 한다
- **AND** 마지막 write가 최종 상태로 반영되어야 한다

#### Scenario: Phase A 단계의 Debezium 책임 축소
- **WHEN** 코드베이스를 검사
- **THEN** `DebeziumSourceEventListener.delete()`는 ES 도큐먼트 삭제만 수행하고 favorite 정리는 호출하지 않아야 한다 (favorite 정리는 `PlaylistDeletedHandler`로 완전 이관)

### Requirement: Testcontainers 기반 통합 테스트
시스템은 ES 동기화 흐름을 Testcontainers 기반 통합 테스트로 검증해야(MUST) 한다. Mock 사용은 허용되지 않는다.

#### Scenario: 생성/수정/삭제 흐름 end-to-end 검증
- **WHEN** `@IntegrationTest` 환경에서 `WebTestClient`로 플레이리스트 생성·수정·삭제 API를 호출
- **THEN** Awaitility로 ES 인덱스에 반영됨이 검증되어야 한다
- **AND** 기존 `PlaylistControllerTest.searchByTitle` 등 ES 의존 테스트가 회귀 없이 통과해야 한다

#### Scenario: favorite 정리 흐름 검증
- **WHEN** favorite을 등록한 뒤 플레이리스트를 삭제
- **THEN** Awaitility로 `favorite_playlist` row 삭제가 확인되어야 한다

