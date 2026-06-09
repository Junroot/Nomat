# room-game-session Specification

## Purpose

방의 방장이 감상 방(`Room`)을 게임 세션으로 전이(`ACTIVE` ↔ `PLAYING`)하고 다시 되돌리는 역량을 정의한다. 게임 중에는 신규 입장을 도메인(`Room.join()`)에서 차단하되, 끊긴 기존 멤버의 유예 시간 내 재접속은 허용한다. 시작·종료 전이는 기존 도메인 이벤트 → Redis Pub/Sub(`room:{id}:events`) → STOMP(`/topic/rooms/{id}`) 경로로 모든 참가자에게 실시간 전파되며, 공개 방 목록은 `PLAYING` 방을 제외하고 방 상세 조회는 현재 게임 상태(`status`)를 포함해 새로고침·재접속 시 화면 복원을 지원한다.

## Requirements

### Requirement: 방장은 ACTIVE 상태의 방에서 게임을 시작할 수 있다

시스템은 방의 방장(가장 먼저 입장한 멤버, `master`)이 STOMP 메시지 `/app/rooms/start`를 보내면 방 상태를 `ACTIVE`에서 `PLAYING`으로 전이해야(SHALL) 한다. 방장이 아닌 참가자의 시작 요청과, `ACTIVE`가 아닌 상태에서의 시작 요청은 거부하고 상태를 변경하지 않아야(MUST NOT) 한다. 권한·상태 검증은 도메인(`Room`)에서 수행하며, 전이는 `room:{id}:lock` 분산 락(Redis) 안에서 직렬화된다.

#### Scenario: 방장이 게임을 시작하면 PLAYING으로 전이된다
- **WHEN** 방장이 `ACTIVE` 상태의 방에서 `/app/rooms/start`를 전송
- **THEN** 방 상태가 `PLAYING`으로 전이되어야 한다
- **AND** `GameStartedEvent`가 등록되어 전파(STARTED 이벤트)로 이어져야 한다

#### Scenario: 방장이 아닌 참가자의 시작은 거부된다
- **WHEN** 방장이 아닌 멤버가 `/app/rooms/start`를 전송
- **THEN** `ForbiddenException`으로 거부되어야 한다
- **AND** 방 상태는 `ACTIVE`로 유지되어야 한다

#### Scenario: 이미 게임 중인 방의 시작 재요청은 거부된다
- **WHEN** 방장이 `PLAYING` 상태의 방에서 `/app/rooms/start`를 다시 전송
- **THEN** `ConflictException`으로 거부되어야 한다
- **AND** 방 상태는 `PLAYING`으로 유지되어야 한다

### Requirement: 방장은 게임을 종료해 다시 입장 가능한 상태로 되돌릴 수 있다

시스템은 방장이 STOMP 메시지 `/app/rooms/end`를 보내면 방 상태를 `PLAYING`에서 `ACTIVE`로 전이해야(SHALL) 한다. 방장이 아닌 참가자의 종료 요청과 `PLAYING`이 아닌 상태에서의 종료 요청은 거부해야(MUST NOT 변경) 한다. (본 종료 전이는 슬라이스를 닫기 위한 최소 수동 전이이며, 후속 라운드 엔진에서 서버 주도 종료로 확장될 수 있다.)

#### Scenario: 방장이 게임을 종료하면 ACTIVE로 복귀한다
- **WHEN** 방장이 `PLAYING` 상태의 방에서 `/app/rooms/end`를 전송
- **THEN** 방 상태가 `ACTIVE`로 전이되어야 한다
- **AND** `GameEndedEvent`가 등록되어 전파(ENDED 이벤트)로 이어져야 한다
- **AND** 이후 신규 입장이 다시 허용되어야 한다

#### Scenario: 방장이 아닌 참가자의 종료는 거부된다
- **WHEN** 방장이 아닌 멤버가 `/app/rooms/end`를 전송
- **THEN** `ForbiddenException`으로 거부되어야 한다
- **AND** 방 상태는 `PLAYING`으로 유지되어야 한다

### Requirement: 게임 중에는 신규 입장이 거부된다

시스템은 방이 `PLAYING` 상태인 동안 새로운 플레이어의 입장을 거부해야(MUST) 한다. 입장 차단의 집행점은 도메인 `Room.join()`이며(`status == PLAYING`이면 `ConflictException`), 방 목록 필터나 STOMP CONNECT 차단이 아니다. 입장 시도는 STOMP CONNECT(`RoomJoinChannelInterceptor`)를 통해 `Room.join()`에 도달한다.

#### Scenario: 게임 중인 방에 새 플레이어 입장이 거부된다
- **WHEN** `PLAYING` 상태의 방에 멤버가 아닌 플레이어가 STOMP CONNECT(roomId 헤더)로 입장을 시도
- **THEN** `Room.join()`이 `ConflictException("게임 중에는 입장할 수 없습니다.")`으로 거부해야 한다
- **AND** 해당 플레이어는 방 멤버(`entries`)에 추가되지 않아야 한다

#### Scenario: 유예 시간이 만료되어 떠난 플레이어의 재입장은 신규 입장으로 거부된다
- **WHEN** 게임 중 끊긴 멤버가 재접속 유예 시간(`app.room.reconnect-grace-period-seconds`)을 넘겨 완전히 퇴장(`leave` 완료)한 뒤, 같은 `PLAYING` 방에 다시 CONNECT를 시도
- **THEN** 활성 세션도 예약된 유예도 없으므로 신규 입장 경로(`Room.join()`)를 타 `ConflictException`으로 거부되어야 한다

### Requirement: 게임 중 끊긴 기존 멤버의 재접속은 입장 차단의 영향을 받지 않는다

시스템은 방이 `PLAYING`인 동안에도, 끊겼지만 재접속 유예 시간 내에 있는 기존 멤버의 재연결을 허용해야(SHALL) 한다. 재접속은 `RoomJoinChannelInterceptor`에서 `cancelGracePeriod()`(또는 동일 방 세션 확인) 경로로 처리되어 `Room.join()`을 호출하지 않으므로, PLAYING 입장 가드의 영향을 받지 않는다. ActiveSessionManager(Redis)·ReconnectGracePeriodManager가 이 판별을 담당한다.

#### Scenario: 유예 시간 내 재접속은 게임 중에도 허용된다
- **WHEN** `PLAYING` 상태의 방에서 멤버가 연결이 끊긴 뒤 유예 시간 내에 같은 방으로 다시 CONNECT
- **THEN** 예약된 퇴장이 취소되고 멤버십이 유지되어야 한다
- **AND** `Room.join()`이 호출되지 않으므로 PLAYING 입장 거부가 적용되지 않아야 한다

### Requirement: 게임 시작·종료 전이는 모든 참가자에게 실시간 전파된다

시스템은 게임 시작·종료 전이를 기존 도메인 이벤트 → Redis Pub/Sub(`room:{id}:events` 채널) → STOMP(`/topic/rooms/{id}`) 경로로 방의 모든 구독자에게 브로드캐스트해야(SHALL) 한다. 메시지는 `RoomEventMessage`의 `STARTED`/`ENDED` 서브타입으로 직렬화되며(`roomId`, `playerId`, `nickname`=전이를 일으킨 방장), 새로운 채널이나 전파 인프라를 추가하지 않는다.

#### Scenario: 게임 시작이 STARTED 이벤트로 브로드캐스트된다
- **WHEN** 방 상태가 `PLAYING`으로 전이되어 트랜잭션이 커밋(AFTER_COMMIT)
- **THEN** `RoomEventListener`가 `room:{id}:events` Redis 채널로 `type=STARTED` 메시지를 발행해야 한다
- **AND** `RoomEventRedisSubscriber`가 이를 `/topic/rooms/{id}`로 중계해 모든 참가자가 수신해야 한다

#### Scenario: 게임 종료가 ENDED 이벤트로 브로드캐스트된다
- **WHEN** 방 상태가 `ACTIVE`로 전이되어 트랜잭션이 커밋
- **THEN** `room:{id}:events` 채널로 `type=ENDED` 메시지가 발행되어 `/topic/rooms/{id}`로 중계되어야 한다

### Requirement: 게임 중인 방은 공개 방 목록에 노출되지 않는다

시스템은 `PLAYING` 상태의 방을 공개 방 목록 조회(`GET /rooms`) 결과에서 제외해야(MUST) 한다. 기존 조회는 `ACTIVE` 상태만 반환하므로(MySQL `findByIdGreaterThanAndStatusOrderByIdDesc`), `PLAYING` 전이 시 자동으로 목록에서 사라진다.

#### Scenario: PLAYING 방은 목록에서 제외된다
- **WHEN** 클라이언트가 `GET /rooms`로 방 목록을 조회
- **THEN** `PLAYING` 상태의 방은 결과에 포함되지 않아야 한다
- **AND** `ACTIVE` 상태의 방만 반환되어야 한다

### Requirement: 방 상세 조회는 현재 게임 상태를 포함한다

시스템은 방 상세 조회(`GET /rooms/{roomId}`) 응답에 현재 방 상태(`status`)를 포함해야(SHALL) 한다. 이는 클라이언트가 새로고침하거나 유예 시간 내 재접속할 때 게임 중 화면을 올바르게 복원하기 위함이다. 라이브 전이는 STOMP `STARTED`/`ENDED`로, 초기/복원 상태는 본 응답의 `status`로 공급된다.

#### Scenario: 상세 응답에 status가 포함된다
- **WHEN** 방 멤버가 `GET /rooms/{roomId}`를 호출
- **THEN** 응답 본문에 `status`(`PENDING`/`ACTIVE`/`PLAYING`)가 포함되어야 한다

#### Scenario: 게임 중 재접속 시 게임 화면이 복원된다
- **WHEN** `PLAYING` 상태의 방에 멤버가 새로고침 또는 유예 시간 내 재접속으로 진입해 상세를 조회
- **THEN** 응답 `status`가 `PLAYING`이어야 하며, 클라이언트가 이를 근거로 게임 중 화면을 렌더할 수 있어야 한다
