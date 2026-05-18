## Context

Phase A(#218)는 `EsPlaylistSyncHandler`(Modulith)와 `DebeziumSourceEventListener`(Debezium)가 동시에 ES에 쓰는 dual-write 상태로 운영 중이다. `infrastructure/cdc/` 패키지는 다음 두 클래스로 구성된다:

- `CdcConfiguration.kt`: Debezium MySqlConnector 임베디드 부팅. `KafkaOffsetBackingStore`와 `schema.history.internal.kafka.*`로 Kafka에 offset/schema history 저장
- `DebeziumSourceEventListener.kt`: `notifying(::handleChangeEvent)`로 binlog 이벤트 수신 후 `ElasticsearchOperations.save/delete` 호출 (Phase A에서 favorite 정리 호출은 이미 제거됨)

운영 인프라:
- `infra/data/compose.yml`의 `kafka-broker` 서비스 (apache/kafka:3.7.2 KRaft 모드, 단일 노드)
- `infra/app/compose.yml`이 백엔드에 `KAFKA_BOOTSTRAP_SERVERS` 환경변수 주입
- `back/build.gradle.kts`의 Debezium 3개 + spring-kafka + testcontainers-kafka 의존성
- `application.yml`의 `spring.kafka.{bootstrap-servers, consumer.group-id}` 블록
- `ContainerConfiguration.kt`의 `KafkaContainer` 빈 (local/test 프로파일)

Phase B 진입 게이트(검증 완료 후 본 PR 진행):
1. dev 24h 운영 후 `event_publication WHERE completion_date IS NULL`의 가장 오래된 항목 age < 1분
2. ES 문서 카운트 ≈ MySQL `playlist` row 카운트 (오차 0)
3. dev replica 2개 환경에서 ShedLock으로 한 인스턴스만 재시도 스케줄러 실행함을 로그로 확인
4. `favorite_playlist` 고아 row 0건

## Goals / Non-Goals

**Goals:**
- ES 동기화 경로를 단일 활성 경로(Modulith)로 일원화
- Debezium 임베디드 + Kafka 클러스터 의존성·인프라 stack 완전 제거
- 백엔드 부팅 시간 단축 + EC2 데이터 노드 자원 회수
- 코드베이스에서 `infrastructure/cdc/` 패키지 흔적 완전 제거 (헥사고날 횡단 관심사 정리)
- 기존 ES 검색 시나리오(특히 `PlaylistControllerTest.searchByTitle` 등)가 Modulith 단일 경로에서 회귀 없이 동작함을 검증

**Non-Goals:**
- ES 인덱스 매핑 변경 또는 `PlaylistDocument` 스키마 변경
- `event_publication` 테이블 스키마·`spring.modulith.events.*` 설정 튜닝
- ShedLock·재시도 스케줄러 로직 변경
- `room`·`player`·`auth` 모듈에 `@ApplicationModuleListener` 도입 (별도 변경)
- 외부 시스템으로의 이벤트 publish (Kafka externalization 등)
- 새 모니터링 대시보드 추가

## Decisions

### Decision 1: Phase A 검증 게이트 충족 후에만 Phase B 머지

본 PR은 Phase A의 dev 운영 검증이 모두 통과한 시점에만 머지한다. 검증 항목 4개(앞 Context의 게이트) 중 하나라도 미충족이면 PR에 결과를 첨부해 보류한다.

근거:
- ES sync 신뢰성이 검증되지 않은 상태에서 Debezium을 제거하면 dev에서도 sync 누락이 사용자 영향으로 직결
- dual-write 단계의 운영비는 일시적이므로 검증 충실도가 더 중요

**대안 검토:**
- *사전 검증 없이 즉시 머지*: 이전 PR 본문에서 Phase A를 dual-write 안전망으로 명시했음. 검증 생략 시 안전망의 의미 상실. 기각

### Decision 2: 코드·의존성·인프라 정리를 한 PR에 묶음

Debezium 코드 제거, build.gradle.kts 의존성 제거, application.yml 정리, infra/data Kafka stack 제거, infra/app 환경변수 정리를 하나의 PR로 통합한다.

근거:
- 의존성·코드만 먼저 제거하면 dev/prod의 Kafka 컨테이너가 무용한 상태로 남아 자원 낭비
- 인프라만 먼저 제거하면 Debezium 임베디드 부팅 실패로 백엔드 컨테이너가 즉시 다운
- 두 변경의 머지 순서 의존성이 강하므로 단일 PR이 운영 안전

**대안 검토:**
- *코드 PR → 1주 후 인프라 PR*: 중간 기간 동안 Kafka 컨테이너가 유휴 상태. 운영비 낭비 + 모니터링 대상에 잔존. 기각
- *인프라 PR 선행*: 백엔드 부팅 실패 위험. 기각

### Decision 3: Kafka 토픽 수동 정리 불필요

Phase A에서 사용된 Kafka 토픽(`nomat_mysql_offset`, `nomat_mysql_schema_history`, Debezium이 자동 생성한 binlog 토픽 등)은 별도 정리하지 않는다. Kafka 클러스터 자체가 사라지므로 토픽도 함께 사라진다.

근거:
- `infra/data/compose.yml`에서 `kafka-broker` 서비스와 `kafka-data` 볼륨을 동시에 제거
- 볼륨 제거로 디스크에 저장된 토픽 데이터 함께 삭제
- 외부 consumer가 없으므로 토픽 보존 가치 없음

### Decision 4: Testcontainers Kafka 제거 시 통합 테스트 영향 점검

`ContainerConfiguration.kt`의 `KafkaContainer` 빈을 제거하면 기존 통합 테스트에서 Kafka에 의존하는 코드가 있을 경우 테스트가 깨진다. 사전 grep 결과:

- `back/src/test/`에서 `Kafka`/`kafka` 직접 참조 없음 (Phase A 시점에 이미 정리)
- `Phase A`의 `PlaylistDualWriteSyncTest`도 Kafka 채널 직접 호출 없이 `WebTestClient` + Awaitility로 ES 검증 수행

따라서 Testcontainers Kafka 제거가 테스트에 미치는 영향은 부팅 시간 단축뿐이다. 안전.

### Decision 5: 롤백 전략

본 PR `git revert` + 동일 파이프라인으로 재배포한다. 추가 절차:

- `infra/data` stack 재배포로 `kafka-broker` 컨테이너 재기동 — 토픽은 빈 상태에서 시작하므로 Debezium이 schema history snapshot부터 다시 부팅
- 백엔드 컨테이너 재기동 시 Debezium 임베디드가 `nomat_mysql_offset` 토픽을 새로 생성하고 binlog 스트리밍 재개
- ES 인덱스에는 Modulith 핸들러가 dual-write로 계속 쓰고 있었기 때문에 데이터 손실 없음
- 롤백 후 ES 동기화는 다시 dual-write 상태로 복귀

`event_publication` 테이블은 Phase A 자산이므로 본 PR로 변경되지 않으며 롤백 영향 없음.

### Decision 6: Phase A의 `@PostPersist` 이벤트 등록 버그 함께 수정

Phase A는 `Playlist`의 생성 시 `PlaylistUpserted` 이벤트를 `@PostPersist registerUpsertedOnPersist()`로 등록하지만, 이 이벤트는 실제로 publish되지 않는다. 원인:

- `@Id @GeneratedValue(strategy = GenerationType.TABLE)`을 사용하므로 Hibernate는 INSERT를 flush 시점까지 지연시킨다
- Spring Data Commons의 `EventPublishingMethodInterceptor`는 `repository.save()` 반환 직후 `entity.andEvents()`로 이벤트를 추출한다
- `@PostPersist`는 INSERT가 실제로 실행되는 flush 시점에 fire하므로 `save()` 반환 시점의 `domainEvents`는 비어 있다
- 따라서 등록된 이벤트는 entity의 `domainEvents` 리스트에 남지만 publish되는 일이 없다

**검증** (Phase B 작업 중 디버그 로그로 확인):
- `@PostPersist registerUpsertedOnPersist()`는 정상 fire (등록 자체는 성공)
- `PlaylistUpserted`를 받는 `@EventListener`/`@ApplicationModuleListener` 모두 호출되지 않음
- `event_publication` 테이블에 `PlaylistUpserted` 행이 INSERT되지 않음
- 반면 `playlist.markDeleted()`로 등록되는 `PlaylistDeleted`는 `repository.delete()` 호출 전에 등록되므로 정상 publish됨 (favorite cleanup·ES 삭제 핸들러 모두 정상 동작)

Phase A에서는 Debezium이 binlog INSERT를 캡처하여 ES에 동기화한 덕분에 사용자 영향이 가려져 있었다. Phase B에서 Debezium을 제거하면 Modulith 단독 경로로 동작해야 하지만 `PlaylistUpserted`가 publish되지 않으므로 ES sync가 깨져 `EsPlaylistSyncHandlerTest`/`PlaylistControllerTest.searchByTitle`이 회귀한다.

**Fix:** `@PostPersist` → `@PrePersist`로 콜백만 변경

JPA 라이프사이클 콜백 순서 (Hibernate, `GenerationType.TABLE`):
1. `entityManager.persist(entity)` 호출
2. ID allocator가 ID 할당 → `entity.id = N` (이미 정해짐)
3. **`@PrePersist` 콜백 fire** ← 이 시점에 `registerEvent` 호출
4. 엔티티가 persistence context에 등록 (`save()` 동기 흐름 종료, return)
5. Spring Data `EventPublishingMethodInterceptor`가 `entity.andEvents()` 호출 → 이벤트 추출 → publish ✓
6. 트랜잭션 commit 시점 flush → INSERT 실행 → `@PostPersist` callback (사용 안 함)

핵심: `@PrePersist`는 `persist()` 동기 흐름 안에서 fire하므로 `save()` 반환 전에 이벤트가 등록된다. `GenerationType.TABLE`은 allocator pre-fetch 방식이라 `@PrePersist` 시점에 이미 ID가 결정되어 있어 `PlaylistUpserted.from(this).id`가 올바른 값을 가진다.

**변경 범위:**
- `Playlist.kt`: `registerUpsertedOnPersist()` 메서드의 어노테이션을 `@PostPersist` → `@PrePersist`로 교체. import도 `PostPersist` → `PrePersist`로 교체
- `Playlist.update()`, `Playlist.markDeleted()`, `PlaylistService.save()/update()/delete()`는 변경 없음 — 도메인 객체가 자신의 라이프사이클로 자율 발행하는 `AbstractAggregateRoot` 패턴을 그대로 유지

**대안 검토:**
- *`markUpserted()` 메서드 추가 + 서비스에서 명시 호출 + 더블 save*: 작동하지만 이벤트 발행을 도메인이 아닌 서비스로 누수시킨다. 이는 `applicationEventPublisher.publishEvent()` 직접 호출과 본질적으로 동일하며 `AbstractAggregateRoot` 패턴의 의미를 잃는다. 또한 더블 save는 의도가 모호하다. 기각
- *`ApplicationEventPublisher.publishEvent` 직접 호출*: 위와 같은 이유로 기각
- *Phase A 핫픽스 별도 PR*: dev 검증이 Debezium에 가려진 상태에서 통과한 것이므로 별도 수정 PR이 Phase B 선행되어야 한다. 그러나 dev에서 Modulith 단독 동작 검증을 한 번 더 거쳐야 하므로 Phase A 검증 게이트와 사실상 동등한 작업이 두 번 발생한다. 본 PR에 포함하면 cutover가 atomic하고 검증도 한 번에 끝난다. 기각
- *`@PostUpdate`까지 함께 활용*: 마찬가지로 flush 시점 fire하므로 동일 문제. 기각

**근거:** Phase B의 검증 기준에 "단일 경로(Modulith) ES sync가 회귀 없이 동작"이 포함되어 있다 (`specs/playlist-search-sync/spec.md`). 이 fix 없이는 검증 기준 자체가 충족되지 않는다.

⚠️ **Phase A 검증 게이트의 재해석**: Phase A 검증 게이트 0.2 (ES 문서 카운트 ≈ MySQL `playlist` row 카운트)가 dev에서 OK로 보이는 것은 Debezium이 망가진 Modulith 생성 경로를 메워주고 있기 때문일 가능성이 있다. 본 PR은 Modulith가 진짜로 단독 동작하도록 만들고, dev 사후 검증(task 6.1)에서 새 playlist 생성 시 ES 인덱싱이 실제로 발생함을 다시 확인한다.

## Risks / Trade-offs

- **[Risk] Phase A 검증 기준이 미충족인데 본 PR을 머지** → Mitigation: PR 머지 전 운영 로그·MySQL/ES 카운트·MDC 로그를 PR 코멘트에 증거로 첨부. 자동화는 본 PR 범위 외(향후 모니터링 변경에서 정형화)
- **[Risk] Kafka 컨테이너 제거 후 사용자 모르게 Kafka 의존이 남은 코드** → Mitigation: `grep -rn -E "kafka\|Kafka\|debezium\|Debezium" back/src/ infra/`으로 사전 점검. 빌드·테스트·운영 부팅에서 추가 검증
- **[Risk] 백엔드 부팅이 Kafka 헬스체크에 묶여 있던 경우 부팅 순서 변경 영향** → Mitigation: `infra/app/compose.yml`의 `depends_on`/`healthcheck` 항목에서 Kafka 관련 부분 제거 확인
- **[Trade-off] dev `.env`에서 `KAFKA_BOOTSTRAP_SERVERS` 항목을 즉시 정리하지 않으면 무용 환경변수만 남음** → 본 PR에서 함께 정리

## Migration Plan

본 변경 무중단 배포 절차:

1. **사전 검증** (Phase A 게이트 4종, Context 참조)
2. **PR 작성** — 본 PR 설명에 Phase A 검증 결과(스크린샷·쿼리 결과) 첨부
3. **PR 머지** → CI가 `develop`에서 도커 이미지 빌드 → EC2에 `docker stack deploy`
4. **롤아웃**:
   - 새 백엔드 컨테이너는 Debezium·Kafka 의존성 없이 부팅 — 부팅 시간 단축
   - 기존 ES sync는 Modulith 핸들러가 단일 경로로 계속 처리
   - `infra/data` stack 재배포로 Kafka 컨테이너 정리 (`docker stack rm` 후 재배포 또는 `docker stack deploy`로 갱신)
5. **사후 검증**:
   - ES 문서 카운트 vs MySQL playlist row 카운트가 계속 일치
   - dev에서 새 playlist 생성·수정·삭제가 ES에 반영되는지 운영 로그·MDC로 확인
   - Kafka 컨테이너가 dev EC2에서 사라졌는지 확인
   - 백엔드 컨테이너 메모리·CPU·부팅 시간 모니터링
6. **롤백** (필요 시): Decision 5의 절차로 복귀

## Open Questions

- 사전 점검 스크립트(`grep -rn -E ...`)를 CI 파이프라인의 일부로 영구 등록할지 — 본 변경 범위 외. Kafka 의존이 다시 생기는 것을 방지하려면 정형화 가치가 있음
- Debezium 임베디드가 자동 생성했던 토픽들이 dev/prod Kafka 컨테이너에 남아 있는지 확인 — Kafka 클러스터 제거로 자동 정리되지만, 별도 도구로 모니터링 중이라면 알람 정리 필요
- `event_publication` 테이블의 향후 운영(파티셔닝, 별도 청소 정책 등)은 본 변경 범위 외 — 트래픽 증가 시 별도 변경에서 검토
