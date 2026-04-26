## ADDED Requirements

### Requirement: Pub/Sub Self Round-trip Ping 검증
시스템은 Redis pub/sub subscription의 살아있음을 self round-trip ping으로 검증하는 헬스 컴포넌트를 제공해야(SHALL) 한다. 컴포넌트는 자기 자신만 구독하는 인스턴스 고유 채널에 ping 메시지를 발행하고, 동일 인스턴스가 그 메시지를 시간 안에 수신해야 살아있음으로 판정한다.

#### Scenario: Round-trip 성공 시 UP 판정
- **WHEN** 헬스 컴포넌트가 Redis pub/sub 채널 `health:pubsub:<instanceId>`에 ping payload를 발행
- **AND** 동일 인스턴스가 동일 채널 구독을 통해 `app.health.pubsub.timeout-ms`(기본 2000ms) 안에 그 payload를 수신
- **THEN** 헬스 컴포넌트는 `Health.up()` 결과를 반환해야 한다
- **AND** 결과 details에 발행 채널명과 round-trip 소요 시간을 포함해야 한다

#### Scenario: 타임아웃 시 DOWN 판정
- **WHEN** 헬스 컴포넌트가 ping을 발행한 뒤 `app.health.pubsub.timeout-ms`(기본 2000ms) 안에 자기 자신이 발행한 payload를 수신하지 못함
- **THEN** 헬스 컴포넌트는 `Health.down()` 결과를 반환해야 한다
- **AND** 결과 details에 timeout 값과 "expected ping not received" 사유를 포함해야 한다
- **AND** WARN 레벨 진단 로그를 한 줄 출력해야 한다 (반복 실패 시에도 매 호출마다 로그 폭증 없이 한 줄)

#### Scenario: Redis 연결 실패 시 DOWN 판정
- **WHEN** 헬스 컴포넌트가 ping 발행을 시도했으나 Redis 클라이언트가 예외를 던짐 (예: 커넥션 거부)
- **THEN** 헬스 컴포넌트는 `Health.down(ex)` 결과를 반환해야 한다
- **AND** 결과 details에 예외 타입과 메시지를 포함해야 한다

### Requirement: 인스턴스 고유 Pub/Sub 채널 사용
시스템은 각 백엔드 인스턴스가 다른 인스턴스의 ping과 자신의 ping을 혼동하지 않도록 인스턴스 고유 채널을 사용해야(MUST) 한다.

#### Scenario: 인스턴스마다 다른 채널 사용
- **WHEN** 동일 Redis를 공유하는 두 백엔드 인스턴스가 동시에 헬스 검증을 수행
- **THEN** 각 인스턴스는 자신의 `instanceId`가 포함된 서로 다른 채널 (`health:pubsub:<instanceId-A>`, `health:pubsub:<instanceId-B>`)에 ping을 발행해야 한다
- **AND** 인스턴스 A가 인스턴스 B의 ping을 수신하여 자신의 살아있음으로 오판하는 일이 없어야 한다

#### Scenario: 인스턴스 재시작 시 채널 재생성
- **WHEN** 백엔드 인스턴스가 재시작
- **THEN** 새 `instanceId`로 새 채널을 만들어 P-SUBSCRIBE 해야 한다
- **AND** 이전 인스턴스가 사용하던 채널에 대한 구독은 자동으로 해제되어야 한다 (Redis pub/sub의 connection-bound 특성에 의해 자연스럽게 발생)

### Requirement: 동일 Pub/Sub Connection 공유
시스템은 도메인 이벤트(`room:*:events` 등)를 처리하는 기존 `RedisMessageListenerContainer`와 동일한 dedicated pub/sub connection을 헬스 검증에서 공유해야(SHALL) 한다. 별도의 `RedisMessageListenerContainer`를 새로 만들지 않는다.

#### Scenario: 도메인 이벤트와 헬스 ping이 같은 connection을 통해 흐름
- **WHEN** 시스템이 부팅되어 헬스 검증 컴포넌트와 도메인 이벤트 subscriber가 모두 활성화
- **THEN** 두 listener는 동일한 `RedisMessageListenerContainer` 인스턴스에 등록되어야 한다
- **AND** Redis 측에서 보면 한 인스턴스당 dedicated pub/sub connection이 하나만 존재해야 한다

#### Scenario: 헬스 ping의 dedicated connection이 끊겼을 때 도메인 이벤트도 같이 끊김을 감지
- **WHEN** 인스턴스의 dedicated pub/sub connection이 (네트워크 blip 등으로) 죽어 도메인 이벤트가 더 이상 흐르지 않는 상태
- **THEN** 동일 connection을 공유하는 헬스 ping도 round-trip에 실패하여 `Health.down()`을 반환해야 한다

### Requirement: Spring Boot Actuator HealthIndicator 통합
시스템은 헬스 검증 결과를 Spring Boot Actuator의 `HealthIndicator` 인터페이스 규약에 맞춰 노출해야(MUST) 한다.

#### Scenario: /actuator/health 응답에 redisPubSub 컴포넌트 노출
- **WHEN** 외부에서 Actuator health endpoint를 호출
- **THEN** 응답 JSON `components` 항목에 `redisPubSub` 키가 존재해야 한다
- **AND** 해당 컴포넌트의 `status` 필드가 헬스 검증 결과(UP/DOWN)를 반영해야 한다

#### Scenario: 한 컴포넌트라도 DOWN이면 전체 status가 DOWN
- **WHEN** `redisPubSub` 컴포넌트가 DOWN 상태
- **AND** 다른 컴포넌트는 모두 UP 상태
- **THEN** Actuator health endpoint의 최상위 `status` 필드는 `DOWN`이 되어야 한다 (Actuator 기본 status aggregator 규약)
- **AND** HTTP 응답 상태 코드는 503이어야 한다

### Requirement: /health 엔드포인트가 컨테이너 헬스체크와 직접 연결
시스템은 Docker Swarm·nginx 등 외부 인프라가 보는 `/health` 엔드포인트가 헬스 검증 결과를 그대로 반영해야(SHALL) 한다. 더미 응답은 허용되지 않는다.

#### Scenario: /health 호출 시 Actuator 검증 결과가 반환됨
- **WHEN** Docker Swarm healthcheck(`infra/app/compose.yml`의 `curl -f http://localhost:8080/health`)가 `/health`를 호출
- **THEN** 응답은 Spring Boot Actuator health endpoint 결과여야 한다 (즉, `redisPubSub` 컴포넌트 상태가 반영됨)
- **AND** 헬스 검증이 DOWN이면 HTTP 503을 반환하여 Swarm이 컨테이너를 unhealthy로 마킹할 수 있어야 한다

#### Scenario: 더미 always-ok 응답이 더 이상 존재하지 않음
- **WHEN** 코드베이스를 검사
- **THEN** 어떤 검사도 수행하지 않고 항상 `"ok"`를 반환하는 `/health` 핸들러가 존재하지 않아야 한다

#### Scenario: 인증 없이 호출 가능
- **WHEN** 인증되지 않은 클라이언트(예: Swarm healthcheck 컨테이너 내부 curl)가 `/health`를 호출
- **THEN** SecurityConfiguration의 `/health/**` permit 규칙에 의해 401/403 없이 헬스 검증 결과를 반환해야 한다

### Requirement: 검증 동작의 외부 설정 가능성
시스템은 헬스 검증의 타임아웃과 채널 prefix를 `application.yml`에서 설정 가능하도록 노출해야(SHALL) 한다.

#### Scenario: 타임아웃 외부화
- **WHEN** 운영자가 `application.yml`에 `app.health.pubsub.timeout-ms: 5000`을 설정
- **AND** 애플리케이션을 재시작
- **THEN** 헬스 검증의 round-trip 타임아웃이 5초로 적용되어야 한다

#### Scenario: 채널 prefix 외부화
- **WHEN** 운영자가 `app.health.pubsub.channel-prefix: my-prefix`를 설정
- **THEN** 헬스 검증이 사용하는 채널이 `my-prefix:<instanceId>` 형식이 되어야 한다

#### Scenario: 설정 미지정 시 기본값
- **WHEN** `application.yml`에 관련 설정이 전혀 없음
- **THEN** 타임아웃은 2000ms, 채널 prefix는 `health:pubsub`이 적용되어야 한다

### Requirement: Testcontainers 기반 통합 테스트
시스템은 헬스 검증 컴포넌트를 Testcontainers Redis로 띄운 통합 테스트로 검증해야(MUST) 한다. Mock 사용은 허용되지 않는다.

#### Scenario: 정상 round-trip 통합 테스트
- **WHEN** Testcontainers Redis가 떠 있는 상태에서 통합 테스트가 헬스 컴포넌트의 `health()` 메서드를 호출
- **THEN** `Health.up()`이 반환되어야 한다

#### Scenario: Redis 컨테이너 정지 시 DOWN 통합 테스트
- **WHEN** 통합 테스트에서 Testcontainers Redis 컨테이너를 정지
- **AND** 헬스 컴포넌트의 `health()` 메서드를 호출
- **THEN** `Health.down()`이 반환되어야 한다

#### Scenario: Actuator endpoint를 통한 end-to-end 통합 테스트
- **WHEN** 통합 테스트가 `WebTestClient`로 `/health` GET 요청을 보냄
- **THEN** 응답 JSON에 `components.redisPubSub.status`가 존재해야 한다
- **AND** 정상 상태에서 HTTP 200을 받아야 한다
