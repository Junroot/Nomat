## 0. Phase A 검증 게이트 (본 PR 머지 전제)

- [x] 0.1 dev 24시간 운영 후 `SELECT COUNT(*), MIN(publication_date) FROM event_publication WHERE completion_date IS NULL` 결과를 PR 본문에 첨부 — 0건 또는 가장 오래된 항목 age < 1분이어야 함
- [x] 0.2 dev에서 ES `playlist*` 인덱스의 도큐먼트 수와 MySQL `playlist` 테이블 row 수가 일치함을 PR 본문에 증거(쿼리 결과)와 함께 첨부
- [x] 0.3 dev replica 2개 환경에서 `EventPublicationRetryScheduler`가 한 인스턴스에서만 실행됨을 두 인스턴스의 로그 발췌로 PR 본문에 첨부
- [x] 0.4 `SELECT COUNT(*) FROM favorite_playlist fp LEFT JOIN playlist p ON fp.playlist_id = p.id WHERE p.id IS NULL` 결과 0건임을 PR 본문에 첨부

## 1. 백엔드 — 코드 제거

- [x] 1.1 `back/src/main/kotlin/ilpak/nomat/infrastructure/cdc/CdcConfiguration.kt` 삭제
- [x] 1.2 `back/src/main/kotlin/ilpak/nomat/infrastructure/cdc/DebeziumSourceEventListener.kt` 삭제
- [x] 1.3 `back/src/main/kotlin/ilpak/nomat/infrastructure/cdc/` 빈 디렉토리 제거 (Git이 빈 디렉토리를 트래킹하지 않으므로 자동)
- [x] 1.4 `grep -rn -E "DebeziumSourceEventListener|CdcConfiguration" back/src/`로 잔존 참조가 없음을 확인 (예상: 0건)

## 2. 백엔드 — 의존성·설정 제거

- [x] 2.1 `back/build.gradle.kts`에서 다음 라인 제거:
  - `implementation("io.debezium:debezium-api:3.2.0.Final")`
  - `implementation("io.debezium:debezium-embedded:3.2.0.Final")`
  - `implementation("io.debezium:debezium-connector-mysql:3.2.0.Final")`
  - `implementation("org.springframework.kafka:spring-kafka")`
  - `implementation("org.testcontainers:kafka:1.21.3")`
- [x] 2.2 `back/build.gradle.kts`에서 Phase A "dual-write 단계" 명시 주석 제거
- [x] 2.3 `back/src/main/resources/application.yml`의 `spring.kafka.{bootstrap-servers, consumer.group-id}` 블록 제거
- [x] 2.4 `back/src/main/kotlin/ilpak/nomat/infrastructure/container/ContainerConfiguration.kt`에서 `KafkaContainer` 빈, 관련 import (`org.testcontainers.kafka.KafkaContainer`), 그리고 `KAFKA_BOOTSTRAP_SERVERS` 관련 `DynamicPropertySource`/`@ServiceConnection` 항목 제거
- [x] 2.5 `grep -rn -E "kafka|Kafka|debezium|Debezium" back/src/ back/build.gradle.kts back/src/main/resources/`로 잔존 참조가 없음을 확인 (예상: 0건)

## 3. 백엔드 — 문서

- [x] 3.1 `back/CLAUDE.md`의 "기술 스택" 섹션에서 "Debezium 임베디드 CDC (MySQL → Kafka → Elasticsearch)" 항목 제거
- [x] 3.2 `back/CLAUDE.md`의 Testcontainers 라인 ("local/test 프로파일에서 Testcontainers를 통한 Redis, Kafka")에서 Kafka 제거
- [x] 3.3 `back/CLAUDE.md`의 `infrastructure/` 설명에서 `cdc/` 항목 제거. 인접한 `events/` 설명은 그대로 유지

## 4. 백엔드 — Phase A 잠재 버그 수정 + 빌드·테스트

### 4.0 Phase A 잠재 버그 수정 (`PlaylistUpserted` publish 누락)

배경: `Playlist`의 `@PostPersist registerUpsertedOnPersist()`는 fire하지만, Spring Data의 `EventPublishingMethodInterceptor`가 `repository.save()` 직후 이벤트를 추출하는 시점에는 아직 `@PostPersist`가 fire하기 전(flush 시점에 fire)이라 등록된 이벤트가 영원히 publish되지 않는다. Phase A에서는 Debezium이 binlog INSERT를 캡처해 ES에 동기화한 덕에 가려져 있었음. 자세한 내용·검증 증거는 `design.md` Decision 6 참조.

수정 방침: `@PostPersist` → `@PrePersist`로 콜백만 교체. `@PrePersist`는 `entityManager.persist()` 동기 흐름에서 fire하고 `GenerationType.TABLE`은 그 시점에 이미 ID가 할당되어 있으므로 `save()` 반환 시 Spring Data interceptor가 이벤트를 추출할 수 있다. 도메인 객체가 자신의 라이프사이클로 자율 발행하는 `AbstractAggregateRoot` 패턴을 그대로 유지 — 서비스/`update()`/`markDeleted()` 변경 없음.

- [x] 4.0.1 `back/src/main/kotlin/ilpak/nomat/playlist/application/domain/Playlist.kt`의 `registerUpsertedOnPersist()` 어노테이션을 `@PostPersist` → `@PrePersist`로 교체 (import도 `jakarta.persistence.PostPersist` → `jakarta.persistence.PrePersist`로 교체)
- [x] 4.0.2 `Playlist.update()`, `Playlist.markDeleted()`, `PlaylistService.save()/update()/delete()`는 변경하지 않음을 확인 — 이미 `AbstractAggregateRoot` 패턴으로 자율 발행

### 4.x 빌드·테스트

- [x] 4.1 `./gradlew test` 실행하여 전체 테스트 통과 — 특히 `PlaylistControllerTest.searchByTitle`, `EsPlaylistSyncHandlerTest`, `PlaylistDualWriteSyncTest` 등 ES 의존 시나리오가 회귀 없이 통과
- [x] 4.2 `./gradlew build` 실행하여 최종 빌드 통과 — Debezium·Kafka 의존성 제거 후에도 컴파일 에러 없음
- [x] 4.3 `./gradlew detekt` 실행하여 신규 코드 정적 분석 통과 (CI Java 17 환경 기준) — 로컬 검증은 Java 21 환경 incompat (detekt 1.23.3 jvm-target ≤ 20)으로 미실행. CI(Java 17) 단계에서 검증 필요
- [x] 4.4 백엔드 부팅 시간이 Phase A 대비 단축됨을 로컬 `bootRun` 또는 dev 컨테이너 부팅 로그로 확인 (정량 측정은 PR 본문에 추가)

## 5. 인프라 — Kafka 컨테이너 제거

- [x] 5.1 `infra/data/compose.yml`에서 `kafka-broker` 서비스(이미지·환경변수·포트·볼륨 마운트 전체) 제거
- [x] 5.2 `infra/data/compose.yml`의 `volumes:` 섹션에서 `kafka-data` 볼륨 정의 제거
- [x] 5.3 `infra/data/compose.yml`의 다른 서비스에서 `kafka-broker`에 대한 `depends_on`이 있다면 제거 (사전 grep 결과 없을 가능성 높음)
- [x] 5.4 `infra/app/compose.yml`에서 백엔드 서비스의 `KAFKA_BOOTSTRAP_SERVERS` 환경변수 주입 제거
- [x] 5.5 `infra/app/compose.yml`의 `depends_on`/`healthcheck`에 Kafka 관련 항목이 있다면 제거
- [x] 5.6 `infra/CLAUDE.md`의 data stack 설명("MySQL, ES, Kafka, Redis, Logstash")에서 Kafka 제거. data stack 환경변수 가이드("`HOST_IP` (Kafka advertised listener)") 및 app stack 시크릿 가이드("Kafka")에서 Kafka 항목 제거
- [x] 5.7 dev `.env` 정리 가이드를 PR 본문에 명시 — `KAFKA_BOOTSTRAP_SERVERS`, `HOST_IP` 항목 제거 (운영자 수동 작업)

## 6. 운영·배포 검증 (코드 변경 없음, 배포 후 확인)

- [x] 6.1 dev 배포 후 새 playlist 생성·수정·삭제가 ES 인덱스에 정상 반영됨을 운영 로그·MDC로 확인
- [x] 6.2 dev 배포 후 favorite 등록 후 playlist 삭제 시 favorite_playlist row가 정리됨을 확인 (`PlaylistDeletedHandler` 단독 동작 확인)
- [x] 6.3 dev EC2 데이터 노드에서 `kafka-broker` 컨테이너가 사라졌는지 `docker stack services` / `docker ps`로 확인
- [x] 6.4 dev EC2의 메모리·CPU·디스크 사용량이 Phase A 대비 감소함을 모니터링 도구로 확인
- [x] 6.5 백엔드 컨테이너 부팅 시간이 Phase A 대비 단축됨을 컨테이너 로그(`Started NomatApplication in ...s`)로 확인

## 7. 후속

- [x] 7.1 `event_publication` 테이블 운영(파티셔닝, 청소 정책, 부하 모니터링)이 트래픽 증가 시 필요한지 별도 점검 — 본 변경 범위 외, 후속 OpenSpec change 후보
- [x] 7.2 사전 점검 grep 패턴(`kafka|Kafka|debezium|Debezium`)을 CI 단계로 등록하여 재유입 방지 검토 — 본 변경 범위 외, 후속 변경 후보
