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
