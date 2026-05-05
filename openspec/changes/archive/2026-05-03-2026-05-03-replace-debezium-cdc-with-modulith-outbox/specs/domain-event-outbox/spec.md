## ADDED Requirements

### Requirement: 도메인 이벤트의 트랜잭션 원자적 기록
시스템은 도메인 이벤트 발행을 비즈니스 트랜잭션과 원자적으로 묶어 outbox에 기록해야(MUST) 한다. 비즈니스 데이터와 publication entry는 같은 트랜잭션 안에서 INSERT되며, 트랜잭션이 롤백되면 publication entry도 롤백되어야 한다.

#### Scenario: 정상 커밋 시 publication entry 기록
- **WHEN** 도메인 서비스가 비즈니스 데이터를 변경하고 `AbstractAggregateRoot.registerEvent()`로 도메인 이벤트를 등록한 뒤 트랜잭션이 정상 커밋
- **THEN** MySQL `event_publication` 테이블에 해당 이벤트에 대해 *수신 리스너 수만큼*의 row가 INSERT되어야 한다
- **AND** 각 row의 `listener_id`는 해당 `@ApplicationModuleListener`의 명시 id 또는 메서드 풀시그니처여야 한다
- **AND** 각 row의 `serialized_event`는 이벤트 객체의 Jackson JSON 직렬화 본체여야 한다

#### Scenario: 비즈니스 트랜잭션 롤백 시 publication entry도 롤백
- **WHEN** 도메인 서비스가 이벤트를 등록한 뒤 동일 트랜잭션 안에서 예외가 발생해 롤백
- **THEN** `event_publication` 테이블에 해당 이벤트에 대한 row가 존재하지 않아야 한다
- **AND** 비즈니스 데이터 변경도 롤백되어야 한다 (atomic 보장)

### Requirement: 핸들러별 격리 추적
시스템은 한 도메인 이벤트가 여러 핸들러를 트리거할 때, 각 핸들러의 진행 상태를 독립적으로 추적해야(MUST) 한다.

#### Scenario: 한 이벤트당 리스너 수만큼 publication 생성
- **WHEN** `PlaylistDeleted` 이벤트가 발행되고 두 개의 `@ApplicationModuleListener`(예: ES sync, favorite cleanup)가 해당 이벤트를 수신
- **THEN** `event_publication` 테이블에 두 개의 row가 INSERT되어야 한다 (각 리스너마다 하나)
- **AND** 두 row의 `listener_id`는 서로 달라야 한다

#### Scenario: 한 핸들러 실패가 다른 핸들러를 막지 않음
- **WHEN** 한 이벤트의 두 리스너 중 한쪽(`es-sync-playlist-deleted`)이 예외를 던지고 다른 쪽(`favorite-cleanup-on-playlist-deleted`)은 정상 실행
- **THEN** 실패한 리스너의 publication row는 `completion_date`가 NULL로 남아야 한다
- **AND** 정상 실행된 리스너의 publication row는 `completion-mode=DELETE` 정책에 따라 즉시 DELETE되어야 한다
- **AND** 실패한 리스너의 재시도가 정상 실행된 리스너의 부수 효과를 다시 일으키지 않아야 한다

### Requirement: 비동기 + 격리된 트랜잭션 실행
시스템은 핸들러 실행을 별도 스레드와 별도 트랜잭션에서 수행해야(MUST) 한다. 핸들러 실패가 원본 비즈니스 트랜잭션에 영향을 주지 않아야 한다.

#### Scenario: 핸들러는 별도 스레드에서 실행
- **WHEN** 도메인 서비스가 `@Transactional` 메서드 안에서 이벤트를 발행하고 응답을 반환
- **THEN** 응답 반환 시점에는 핸들러 실행이 완료되어 있지 않을 수 있다(비동기)
- **AND** 핸들러는 호출자 스레드가 아닌 `spring.task.execution.pool`에서 가져온 별도 스레드에서 실행되어야 한다

#### Scenario: 핸들러 트랜잭션은 원본과 분리
- **WHEN** 핸들러가 `@ApplicationModuleListener` 안에서 예외를 던짐
- **THEN** 원본 비즈니스 트랜잭션은 영향받지 않고 이미 커밋된 상태여야 한다
- **AND** 핸들러의 publication row는 `completion_date=NULL`로 남아 재시도 대상이 되어야 한다

### Requirement: 미완료 이벤트의 주기적 재시도
시스템은 핸들러 실행에 실패해 미완료 상태로 남은 publication을 주기적으로 재시도해야(SHALL) 한다.

#### Scenario: 미완료 publication 재시도
- **WHEN** 시스템에 30초 이상 미완료(`completion_date IS NULL`)인 publication row가 존재
- **AND** 해당 publication의 `publication_date`가 5분 이상 경과
- **THEN** `EventPublicationRetryScheduler`가 `IncompleteEventPublications.resubmitIncompletePublications(Duration.ofMinutes(5))`를 호출해 핸들러를 재실행해야 한다
- **AND** 재실행이 성공하면 `completion-mode=DELETE` 정책에 따라 row가 DELETE되어야 한다

#### Scenario: 부팅 시 일괄 재발행 비활성화
- **WHEN** 애플리케이션 인스턴스가 부팅
- **THEN** `spring.modulith.events.republish-outstanding-events-on-restart=false` 설정으로 인해 부팅 직후 일괄 재발행이 발생하지 않아야 한다
- **AND** 미완료 publication의 처리는 운영 중 재시도 스케줄러를 통해서만 일어나야 한다

### Requirement: 멀티 인스턴스 환경에서 단일 재시도 실행
시스템은 백엔드 인스턴스가 여러 개 운영 중일 때 미완료 이벤트 재시도가 정확히 한 인스턴스에서만 실행되도록 보장해야(MUST) 한다.

#### Scenario: 두 인스턴스 환경에서 한 인스턴스만 재시도 수행
- **WHEN** 동일 Redis와 동일 MySQL을 공유하는 두 백엔드 인스턴스가 동시에 재시도 스케줄러 시각에 도달
- **THEN** ShedLock(`shedlock:event-publication-retry`, `lockAtMostFor=PT1M`)으로 인해 한 인스턴스만 락을 획득해 `IncompleteEventPublications.resubmitIncompletePublications`를 호출해야 한다
- **AND** 다른 인스턴스는 락 획득에 실패해 해당 주기를 건너뛰어야 한다

#### Scenario: 락 보유 인스턴스 장애 시 다른 인스턴스가 인수
- **WHEN** 락을 보유한 인스턴스가 `lockAtMostFor` 이내에 락을 해제하지 못한 채 장애
- **THEN** TTL 만료 후 다른 인스턴스가 다음 주기에 락을 획득해 재시도를 이어가야 한다

### Requirement: 완료된 publication의 자동 삭제
시스템은 핸들러가 정상 완료한 publication을 즉시 삭제해야(MUST) 한다. 테이블 무한 증가를 막는다.

#### Scenario: 완료된 publication 즉시 삭제
- **WHEN** 핸들러가 `@ApplicationModuleListener` 안에서 정상 반환
- **AND** `spring.modulith.events.completion-mode=DELETE`가 활성화
- **THEN** 해당 publication row는 즉시 `event_publication` 테이블에서 DELETE되어야 한다
- **AND** 별도 청소 스케줄러가 필요하지 않아야 한다

### Requirement: 핸들러는 별도 컨텍스트에서 실행됨을 페이로드로 자기충족 처리
시스템은 핸들러가 `SecurityContext`, `MDC`, `RequestContext` 등 호출자 스레드 컨텍스트를 참조하지 않고도 동작 가능하도록 이벤트 페이로드 안에 필요한 정보를 모두 담아야(SHALL) 한다.

#### Scenario: 페이로드 자기충족
- **WHEN** 핸들러가 ES upsert 또는 favorite 정리에 필요한 도메인 정보를 사용
- **THEN** 그 정보는 이벤트 페이로드 필드에 직접 담겨 있어야 한다
- **AND** 핸들러는 `SecurityContextHolder.getContext()` 또는 `MDC.get(...)`에 의존하지 않아야 한다

### Requirement: 이벤트 클래스의 직렬화 안정성
시스템은 이벤트 클래스의 위치·이름·필드 변경이 누적된 미완료 이벤트의 deserialization을 깨뜨리지 않도록 운영 룰을 따라야(SHALL) 한다.

#### Scenario: 이벤트 클래스 위치 안정성
- **WHEN** 새 도메인 이벤트 클래스가 추가됨
- **THEN** 클래스는 `<domain>/application/domain/` 패키지에 위치해야 한다
- **AND** `@ApplicationModuleListener(id = "<명시적-식별자>")`로 메서드 위치 이동에 강건해야 한다

#### Scenario: 필드 변경 호환성
- **WHEN** 이벤트 클래스에 새 필드를 추가
- **THEN** 새 필드는 nullable 또는 default 값을 가져야 한다 (옛 미완료 이벤트의 deserialization 호환)
- **AND** 필드 삭제·이름 변경·타입 변경은 미완료 publication 0건인 시점에서만 수행해야 한다

### Requirement: Testcontainers 기반 통합 테스트
시스템은 outbox 인프라를 Testcontainers 기반 통합 테스트로 검증해야(MUST) 한다. Mock 사용은 허용되지 않는다.

#### Scenario: 정상 흐름 통합 테스트
- **WHEN** Testcontainers MySQL·Redis가 떠 있는 상태에서 통합 테스트가 도메인 이벤트를 발행
- **THEN** publication entry가 INSERT되고 핸들러가 실행되어 `completion-mode=DELETE` 정책에 따라 DELETE되는 흐름이 Awaitility로 검증되어야 한다

#### Scenario: 핸들러 실패 + 재시도 통합 테스트
- **WHEN** 테스트 전용 핸들러가 의도적으로 실패하도록 설정
- **THEN** publication entry가 `completion_date=NULL`로 남고
- **AND** `EventPublicationRetryScheduler` 호출 후 재처리가 일어나야 한다

#### Scenario: ShedLock 단일 실행 통합 테스트
- **WHEN** 동일 ApplicationContext에서 두 컴포넌트 인스턴스가 동시에 스케줄러 실행 시각에 도달 (또는 동등한 시뮬레이션)
- **THEN** 한 인스턴스만 락을 획득해 `IncompleteEventPublications.resubmitIncompletePublications`를 호출함이 검증되어야 한다
