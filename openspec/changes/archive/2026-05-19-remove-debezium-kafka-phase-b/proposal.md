## Why

Phase A(#218)에서 Spring Modulith Event Publication Registry 기반 outbox 경로를 신설하여 MySQL → Elasticsearch 플레이리스트 동기화를 dual-write 상태로 운영하고 있다. dev 환경 검증 기준(미완료 publication 가장 오래된 항목 age < 1분, ES 문서 카운트 ≈ MySQL playlist row 카운트, 멀티 인스턴스 ShedLock 단일 실행)이 충족되면 Debezium 임베디드 + Kafka 인프라는 더 이상 ES sync 경로로 필요하지 않다. 본 변경은 검증 완료 후 dual-write를 단일 write로 전환하고 Debezium·Kafka 의존성과 인프라 stack을 제거하여 Kafka 운영비와 Debezium 임베디드 디버깅 부담을 동시에 해소한다.

## What Changes

- **BREAKING**: `infrastructure/cdc/` 패키지 전체 삭제 (`CdcConfiguration`, `DebeziumSourceEventListener`) — ES 동기화는 `EsPlaylistSyncHandler` 단일 경로로 전환
- `back/build.gradle.kts`에서 의존성 제거: `io.debezium:debezium-api`, `io.debezium:debezium-embedded`, `io.debezium:debezium-connector-mysql`, `org.springframework.kafka:spring-kafka`, `org.testcontainers:kafka`. Phase A dual-write 단계 표시 주석도 제거
- `back/src/main/resources/application.yml`에서 `spring.kafka.*` 블록 제거
- `back/src/main/.../infrastructure/container/ContainerConfiguration.kt`에서 `KafkaContainer` 빈 + 관련 `DynamicPropertySource` 항목 제거
- `infra/data/compose.yml`에서 `kafka-broker` 서비스 + `kafka-data` 볼륨 제거. dev EC2 데이터 stack에서 동일 변경 동기화
- `infra/app/compose.yml`에서 `KAFKA_BOOTSTRAP_SERVERS` 환경변수 주입 제거. dev `.env`에서도 정리
- `back/CLAUDE.md`: 기술 스택 섹션의 "Debezium 임베디드 CDC (MySQL → Kafka → Elasticsearch)" 항목 제거, `infrastructure/` 설명에서 `cdc/` 항목 제거, Testcontainers 목록에서 Kafka 제거
- `infra/CLAUDE.md`: data stack 설명·환경변수 가이드에서 Kafka 항목 제거
- `playlist-search-sync` capability에서 "본 PR(Phase A) 동안 Debezium 경로 유지" Requirement REMOVE — Phase B 머지로 Phase A scoping requirement는 종료
- **Phase A 잠재 버그 수정**: `Playlist`의 `@PostPersist registerUpsertedOnPersist()`로는 `PlaylistUpserted`가 publish되지 않는 문제 해결. `@PostPersist`는 `repository.save()` 반환 후 flush 시점에 fire하지만 Spring Data의 `EventPublishingMethodInterceptor`는 `save()` 직후 이벤트를 추출하므로 등록된 이벤트가 영원히 publish되지 않음. Phase A 동안 Debezium이 binlog INSERT를 캡처해 ES에 동기화한 덕에 가려져 있었음. **`@PostPersist` → `@PrePersist`로 콜백 변경**: `@PrePersist`는 `entityManager.persist()` 동기 흐름에서 fire하고 `GenerationType.TABLE` 사용 시 그 시점에 ID가 이미 할당된 상태이므로 Spring Data interceptor가 `save()` 반환 시 이벤트를 정상 추출함. 도메인 객체가 자신의 라이프사이클로 이벤트를 발행하는 `AbstractAggregateRoot` 패턴을 그대로 유지 — 서비스는 변경 없음

## Capabilities

### New Capabilities

본 변경은 신규 capability를 도입하지 않는다. 기존 outbox 경로(`domain-event-outbox`, `playlist-search-sync`)의 단일 활성 경로화만 수행한다.

### Modified Capabilities

- `playlist-search-sync`: dual-write 단계의 Debezium 유지 Requirement를 REMOVED 처리. ES 동기화 책임은 `EsPlaylistSyncHandler` 단일 경로로 명문화. (Phase A 시점의 "본 PR(Phase A) 동안 Debezium 경로 유지" Requirement는 의도적으로 한시적 scoping requirement였으며 Phase B 머지로 종료됨)

## Impact

- **서브프로젝트**: `back/`(코드·의존성·application.yml), `infra/data`·`infra/app`(compose 정리). `front/` 영향 없음
- **도메인 모듈**: 직접 변경되는 도메인 모듈 없음. `playlist`/`favoriteplaylist`의 ES sync·favorite 정리 동작은 Phase A 핸들러로 이미 이관 완료되어 본 변경에서 무관
- **헥사고날 계층**:
  - `infrastructure/cdc/` 패키지 삭제 (전체 제거)
  - `infrastructure/container/`: `KafkaContainer` 빈 제거
  - `application/`, `in/`, `out/` 변경 없음
- **DB 스키마**: 변경 없음. `event_publication` 테이블은 그대로 사용
- **ES 매핑**: 변경 없음. `PlaylistDocument` 그대로
- **Kafka 토픽**: 모두 제거. 제거 대상 토픽 — `nomat_mysql_offset`, `nomat_mysql_schema_history`, `mysql_playlist*`(Debezium binlog 토픽 — 본 환경에서는 schema history만 활용되었으나 Debezium 부팅 시 자동 생성될 수 있음). Kafka 클러스터 자체가 사라지므로 토픽 수동 정리 불필요
- **Redis 키**: 변경 없음 (ShedLock `shedlock:event-publication-retry` 그대로 유지)
- **의존성**: Debezium 3개 + Kafka 2개(spring-kafka, testcontainers kafka) 제거. 추가 의존성 없음
- **운영 동작**:
  - Phase B 배포 후 ES sync는 단일 경로(Modulith)로만 수행 — race 조건 자체가 사라짐
  - Kafka 컨테이너 제거로 dev/staging EC2 데이터 노드의 메모리·CPU·디스크 절감
  - 백엔드 부팅 시간 단축 (Debezium 임베디드 부팅 대기 제거)
  - 롤백 시 본 PR `git revert` 후 Kafka 컨테이너 재기동 + Debezium 재부팅이 필요 — Phase A 검증 기준 충족 후에만 진행
- **Phase A 검증 게이트(본 PR 머지 전제 조건)**:
  - dev 24시간 운영 후 `event_publication WHERE completion_date IS NULL`의 가장 오래된 항목 age < 1분
  - ES 문서 카운트 ≈ MySQL `playlist` row 카운트 (오차 0)
  - dev replica 2개 환경에서 한 인스턴스만 재시도 스케줄러를 실행함을 로그로 확인
  - favorite_playlist 고아 row 0건
