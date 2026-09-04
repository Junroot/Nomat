# room-reconnect-grace Specification

## Purpose

방 멤버의 WebSocket 연결이 끊긴 뒤 유예 시간 내 재접속을 허용하고, 재접속이 없으면 퇴장으로 전이하는 끊김 → 유예 → 퇴장 흐름을 **백엔드 인스턴스와 프로세스 수명에 무관하게** 내구성 있게 만드는 역량을 정의한다. 유예 예약은 인-프로세스 타이머 대신 Redis ZSET(`rooms:pending-leaves`)에 Redis 서버 시계 기준 만료 시각으로 기록되고, 재접속에 의한 예약 취소(`ZREM`)는 어느 인스턴스에서든 성립한다. 만료 처리는 ShedLock으로 단일 실행되는 주기 sweeper가 **먼저 claim(`ZREM`)한 뒤 실행(`RoomService.leave`)** 하는 방식으로 재접속과의 경합에서 한쪽만 이기도록 보장하며, 퇴장 실패는 5초 뒤 재시도로 이어진다. 무응답 연결은 STOMP 하트비트(서버 송신 10초·클라이언트 수신 10초)로 감지해 일반 끊김과 같은 유예 경로로 흘려보내고, 인스턴스 graceful 종료 시에는 Redis 연결이 살아 있는 동안 열린 세션의 끊김을 예약으로 기록한다. 유예 sweep은 라운드 마감 sweeper와 다른 스케줄러 스레드에서 실행되어 라운드 전이를 지연시키지 않는다.

## Requirements

### Requirement: 연결이 끊긴 멤버의 퇴장 유예 예약은 프로세스 밖에 기록된다

시스템은 방 멤버의 WebSocket 연결이 끊기면(`SessionDisconnectEvent`) 그 멤버의 퇴장을 유예 시간(`app.room.reconnect-grace-period-seconds`, 기본 60초) 뒤로 예약하되, 예약을 **백엔드 인스턴스 공유 저장소(Redis ZSET `rooms:pending-leaves`)** 에 기록해야(MUST) 한다. 예약의 만료 시각은 Redis 서버 시계(`TIME`)를 기준으로 계산한다. 인-프로세스 타이머나 메모리 맵에만 의존하는 예약은 허용되지 않는다.

끊김 이벤트가 그 세션이 현재 활성 세션이 아닐 때(다른 탭·재접속으로 이미 교체된 세션) 도착하면, 기존과 같이 예약하지 않는다.

#### Scenario: 끊김 시 Redis에 유예 예약이 기록된다
- **WHEN** 방에 STOMP로 접속 중인 멤버의 연결이 끊김
- **THEN** Redis `rooms:pending-leaves` ZSET에 `{roomId}:{playerId}` 멤버가 만료 시각(현재 Redis 시각 + 유예 시간)을 score로 추가되어야 한다

#### Scenario: 예약을 기록한 인스턴스가 종료되어도 예약은 남는다
- **WHEN** 멤버의 끊김을 처리해 유예 예약을 기록한 백엔드 인스턴스가 유예 만료 전에 종료됨
- **THEN** 예약은 Redis에 그대로 남아 다른 인스턴스가 만료 시각에 처리해야 한다

#### Scenario: 이미 교체된 세션의 끊김은 예약하지 않는다
- **WHEN** 한 플레이어가 새 세션으로 접속해 활성 세션이 교체된 뒤, 옛 세션의 끊김 이벤트가 도착
- **THEN** 옛 세션의 끊김은 활성 세션 불일치로 무시되어 `rooms:pending-leaves`에 아무것도 추가되지 않아야 한다

### Requirement: 유예 예약 취소는 어느 인스턴스에서든 성립한다

시스템은 유예 시간 내 재접속(STOMP CONNECT)이 어느 백엔드 인스턴스에 도착하든 Redis의 유예 예약을 원자적으로 제거(`ZREM`)하고, **제거 결과(예약이 있었는지)** 로 재접속과 신규 입장을 구분해야(MUST) 한다. 활성 세션이 없는 CONNECT에서 예약 제거가 성공하면 재접속으로 처리해 `Room.join()`을 호출하지 않고, 실패하면 신규 입장으로 `Room.join()`을 호출한다. 활성 세션이 같은 방을 가리키면 예약 제거 결과와 무관하게 재접속으로 처리한다. 다른 방을 가리키면 옛 방의 예약을 제거하고 옛 방에서 퇴장한 뒤 새 방에 입장한다.

#### Scenario: 다른 인스턴스에서 예약을 취소한다
- **WHEN** 인스턴스 A가 기록한 유예 예약이 남아 있는 상태에서, 유예 시간 내에 인스턴스 B로 같은 방에 CONNECT
- **THEN** B의 `ZREM`이 예약을 제거해 재접속으로 처리되고 멤버십이 유지되어야 한다
- **AND** 이후 유예 만료 시각이 지나도 그 멤버는 퇴장되지 않아야 한다

#### Scenario: 예약이 없는 CONNECT는 신규 입장이다
- **WHEN** 활성 세션도 유예 예약도 없는 플레이어가 방에 CONNECT
- **THEN** `Room.join()` 경로로 처리되어 방 정원·비밀번호·게임 중 여부 검증을 거쳐야 한다

#### Scenario: 다른 방으로 재접속하면 옛 방 예약이 취소되고 퇴장된다
- **WHEN** 방 X에서 끊겨 유예 예약이 있는 플레이어가 유예 시간 내에 방 Y로 CONNECT
- **THEN** 방 X의 유예 예약이 제거되고 방 X에서 즉시 퇴장 처리된 뒤 방 Y에 `Room.join()`으로 입장해야 한다

### Requirement: 유예 만료는 sweeper가 처리하고 실패는 재시도된다

시스템은 만료 시각이 지난 유예 예약을 주기적(1초) 폴링 sweeper가 처리해야(SHALL) 한다. sweeper는 ShedLock(`@SchedulerLock`)으로 한 번에 하나의 인스턴스에서만 실행되며, 만료 항목마다 **먼저 `ZREM`으로 항목을 claim하고 성공(1)한 경우에만** `RoomService.leave`를 실행한다. `leave`가 예외 없이 반환하면 완료다(방이 없거나 이미 퇴장한 경우 포함). `leave`가 예외를 던지면 경고 로그를 남기고 항목을 현재 Redis 시각 + 5초로 다시 기록해 재시도한다. 재시도 상한과 시간 기반 GC는 두지 않는다. 마지막 멤버의 퇴장으로 방이 비면 기존과 같이 방이 삭제되고 라운드 상태가 정리된다.

#### Scenario: 유예가 만료되면 퇴장 처리된다
- **WHEN** 멤버의 끊김 후 유예 시간이 지나도록 재접속이 없음
- **THEN** sweeper가 예약을 claim해 `RoomService.leave`를 실행하고, 방 멤버(`entries`)에서 제거되며 `LEFT` 이벤트가 방 토픽으로 방송되어야 한다
- **AND** `rooms:pending-leaves`에서 해당 항목이 제거되어야 한다

#### Scenario: 마지막 멤버의 유예가 만료되면 방이 삭제된다
- **WHEN** 방의 유일한 멤버가 끊긴 뒤 유예 시간이 지나도록 재접속이 없음
- **THEN** 방이 삭제되고 방 목록(`GET /rooms`)에서 사라져야 한다
- **AND** 그 방의 라운드 상태(Redis `round:*`·`scores:*`·마감 인덱스)가 정리되어야 한다

#### Scenario: 퇴장 실패는 5초 뒤 재시도된다
- **WHEN** sweeper가 claim한 항목의 `leave`가 예외(예: 방 멤버십 락 `room:{id}:lock` 획득 실패 `ConflictException`)로 실패
- **THEN** 경고 로그가 남고 항목이 현재 시각 + 5초 score로 `rooms:pending-leaves`에 다시 기록되어야 한다
- **AND** 실패 원인이 해소되면 다음 만료 틱에 퇴장이 완료되어야 한다

#### Scenario: 존재하지 않는 방의 예약은 예외 없이 완료된다
- **WHEN** `rooms:pending-leaves`에 이미 삭제된 방을 가리키는 항목이 있고 만료 시각이 지남
- **THEN** sweeper가 항목을 claim한 뒤 `leave`가 조용히 반환해 항목이 제거되고 재시도되지 않아야 한다

### Requirement: 유예 만료 처리와 재접속이 경합하면 한쪽만 이긴다

시스템은 유예 만료 시각 부근에서 sweeper의 만료 처리와 클라이언트의 재접속이 동시에 일어나도 **접속 중인 멤버를 퇴장시키지 않아야(MUST NOT)** 한다. 두 경로 모두 같은 항목을 `ZREM`으로 claim하므로 정확히 한 경로만 진행한다. 재접속이 먼저 claim했으면 sweeper는 그 항목을 건너뛰고, sweeper가 먼저 claim했으면 그 재접속은 예약이 없는 CONNECT로 취급되어 유예 만료 후 신규 입장 규칙(`Room.join()`)을 따른다. 후자의 결말은 `join`과 `leave`가 같은 방 락을 다투는 순서에 따라, 재입장 시도(방이 `PLAYING`이면 게임 중 입장 거부이며 정원·비밀번호 검증에서도 거부될 수 있다) 또는 "이미 입장한 플레이어" 거부 중 하나이며, 어느 쪽이든 **연결된 세션이 멤버십 없이 남지 않아야(MUST) 한다**.

#### Scenario: 재접속이 먼저 claim하면 sweeper가 건너뛴다
- **WHEN** sweeper가 만료 항목을 조회한 직후, sweeper의 `ZREM` 전에 같은 멤버의 재접속 CONNECT가 예약을 `ZREM`으로 제거
- **THEN** sweeper의 `ZREM`이 0을 돌려 `leave`를 실행하지 않고, 재접속한 멤버는 방에 남아 있어야 한다

#### Scenario: sweeper가 먼저 claim하면 재접속은 신규 입장이다
- **WHEN** sweeper가 항목을 `ZREM`으로 claim해 `leave`를 진행하는 중에 같은 멤버의 CONNECT가 도착
- **THEN** CONNECT는 예약이 없는 것으로 판정되어 `Room.join()` 경로를 타야 한다
- **AND** 결과는 재입장 성공(sweeper의 `leave`가 방 락을 먼저 잡은 경우, `PLAYING`이면 게임 중 입장 거부) 또는 "이미 방에 입장한 플레이어입니다" 거부(`join`이 락을 먼저 잡은 경우) 중 하나여야 한다
- **AND** 거부된 경우 CONNECT가 실패해 세션이 열리지 않고, 직후 sweeper의 `leave`가 완료되어 멤버십과 세션이 모두 없는 일관된 상태여야 한다

### Requirement: 서버는 STOMP 하트비트로 무응답 연결을 감지한다

시스템은 STOMP 브로커의 하트비트를 서버 송신 10초·클라이언트 수신 10초로 협상해야(SHALL) 한다. 클라이언트로부터 협상된 간격의 3배 동안 하트비트나 프레임을 받지 못하면 서버가 그 WebSocket 세션을 닫고, 그 결과 발생하는 `SessionDisconnectEvent`가 일반 끊김과 동일하게 유예 예약으로 이어져야 한다. 하트비트를 지원하지 않거나 `0,0`을 요청한 클라이언트에 대해서는 협상 결과에 따라 감지가 비활성화될 수 있다.

#### Scenario: 클라이언트 하트비트가 끊기면 세션이 닫히고 유예가 예약된다
- **WHEN** 클라이언트가 CONNECT 프레임에 `heart-beat:10000,10000`을 실어 접속한 뒤 TCP 연결은 유지한 채 어떤 프레임도 보내지 않음
- **THEN** 약 30초 이내에 서버가 세션을 닫고 `rooms:pending-leaves`에 그 멤버의 유예 예약이 기록되어야 한다

#### Scenario: 유휴 연결에도 서버 하트비트가 흐른다
- **WHEN** 방에 접속한 클라이언트가 채팅이나 라운드 이벤트 없이 60초 이상 유휴 상태로 있음
- **THEN** 서버가 10초 간격으로 하트비트 프레임을 보내 연결이 유지되어야 한다

#### Scenario: 하트비트를 요청하지 않는 클라이언트는 기존과 같이 동작한다
- **WHEN** 클라이언트가 `heart-beat` 헤더 없이(또는 `0,0`으로) CONNECT
- **THEN** 협상 결과 하트비트 없이 연결되며, 접속·채팅·퇴장 동작은 하트비트 도입 전과 동일해야 한다

### Requirement: 인스턴스 graceful 종료 시 열린 연결의 끊김이 기록된다

시스템은 백엔드 인스턴스가 정상 종료 신호(SIGTERM)를 받으면 graceful 종료(`server.shutdown: graceful`, 페이즈 타임아웃 20초)를 수행하고, 그 과정에서 열린 WebSocket 세션을 닫아 각 세션의 끊김 이벤트가 **Redis 연결이 살아 있는 동안** 처리되어 유예 예약이 기록되어야(MUST) 한다. 배포 설정(`infra/app/compose.yml`)의 `stop_grace_period`는 종료 페이즈 타임아웃보다 길어야(30초) 한다.

#### Scenario: 컨텍스트 종료 시 접속 중이던 멤버의 유예가 예약된다
- **WHEN** 멤버가 STOMP로 접속 중인 상태에서 백엔드 애플리케이션 컨텍스트가 정상 종료됨
- **THEN** 종료 완료 전에 그 멤버의 `{roomId}:{playerId}` 항목이 `rooms:pending-leaves`에 기록되어 있어야 한다

#### Scenario: 롤링 배포 중 끊긴 멤버는 유예 후 정리되거나 재접속으로 유지된다
- **WHEN** 롤링 배포로 옛 인스턴스가 종료되어 멤버의 연결이 끊김
- **THEN** 유예 시간 내에 재접속하면 새 인스턴스가 예약을 취소해 멤버십이 유지되고, 재접속이 없으면 새 인스턴스의 sweeper가 유예 만료 시각에 퇴장 처리해야 한다

### Requirement: 유예 sweep은 라운드 전이를 지연시키지 않는다

시스템은 유예 만료 sweeper와 라운드 마감 sweeper(`RoundDeadlineSweeper`)를 서로 다른 스케줄러 스레드에서 실행해야(SHALL) 한다. 유예 sweep이 방 멤버십 락 대기로 수 초를 점유하더라도 라운드 마감 폴링은 그 영향을 받지 않는다.

#### Scenario: 유예 sweep이 길어져도 라운드 마감 폴링은 제때 돈다
- **WHEN** 유예 sweeper가 락 경합으로 한 항목에서 수 초를 대기하는 동안 다른 방의 `OPEN` 라운드 마감 시각이 도래
- **THEN** 라운드 마감 sweeper가 자신의 폴링 주기(1초) 안에 그 라운드를 `REVEAL`로 전이해야 한다
