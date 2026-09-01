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

시스템은 게임 시작·종료·라운드 전이를 기존 도메인 이벤트 → Redis Pub/Sub(`room:{id}:events` 채널) → STOMP(`/topic/rooms/{id}`) 경로로 방의 모든 구독자에게 브로드캐스트해야(SHALL) 한다. 메시지는 `RoomEventMessage`의 서브타입으로 직렬화되며, **새로운 채널이나 전파 인프라를 추가하지 않는다**. 시작은 `STARTED`, 라운드 전이는 `ROUND_STARTED`·`ROUND_REVEALED`, 종료는 `ENDED` 서브타입을 쓴다.

`ROUND_REVEALED` 서브타입은 다음 라운드의 재생 참조를 담는 **nullable 필드**를 포함한다. 마지막 라운드에서는 비어 있다. 이 필드 추가는 기존 채널·서브타입 체계 안에서 이뤄지며 새 이벤트 타입을 도입하지 않는다.

`ROUND_REVEALED`의 점수판 항목은 `playerId`·`score`와 함께 **닉네임**을 담고, 메시지는 승자 닉네임을 담는 **nullable 필드**를 포함한다(승자가 없는 타임아웃 공개에서는 비어 있다). 승자 닉네임을 점수판에서 역참조하지 않고 별도로 싣는 이유는, 정답 판정과 점수 반영이 원자적으로 갈라질 수 있어(가점은 아직 멤버일 때만 적용된다) **점수 항목이 없는 승자**가 성립하기 때문이다. 닉네임 해석 주체·실패 처리는 별도 요구사항을 따른다.

기존 슬라이스에서 `ENDED`는 종료를 일으킨 방장(`playerId`, `nickname`)을 실었으나, 본 능력에서 종료는 **라운드 엔진이 서버 주도로** 일으킬 수도 있다. 따라서 `ENDED`의 행위자 필드는 방장 수동 종료일 때만 채워지고 **서버 주도 종료에서는 행위자가 없을 수 있다**(수신 측은 행위자 유무와 무관하게 게임 종료로 처리). 최종 점수판은 별도로 `ENDED`에 싣지 않고 직전 `ROUND_REVEALED`가 전달한다.

#### Scenario: 게임 시작이 STARTED로 브로드캐스트된다
- **WHEN** 방 상태가 `PLAYING`으로 전이되어 트랜잭션이 커밋(AFTER_COMMIT)
- **THEN** `room:{id}:events` Redis 채널로 `type=STARTED` 메시지가 발행되어 `/topic/rooms/{id}`로 중계되어야 한다

#### Scenario: 라운드 전이가 ROUND_STARTED·ROUND_REVEALED로 브로드캐스트된다
- **WHEN** 라운드가 `OPEN`으로 열리거나 `REVEAL`로 전이됨
- **THEN** 각각 `type=ROUND_STARTED`·`ROUND_REVEALED` 메시지가 `room:{id}:events`로 발행되어 모든 참가자에게 중계되어야 한다

#### Scenario: ROUND_REVEALED가 승자 닉네임을 함께 싣는다
- **WHEN** 첫 정답으로 라운드가 `REVEAL`로 전이됨
- **THEN** 메시지에 `winnerId`와 함께 그 플레이어의 닉네임이 포함되어야 한다

#### Scenario: 타임아웃 공개에는 승자 닉네임이 없다
- **WHEN** 아무도 맞히지 못해 마감으로 `REVEAL`로 전이됨
- **THEN** `winnerId`와 승자 닉네임이 모두 비어(null) 있어야 한다

#### Scenario: 서버 주도 종료도 행위자 없이 ENDED로 브로드캐스트된다
- **WHEN** 라운드 엔진이 마지막 라운드 후 서버 주도로 게임을 종료(종료를 누른 방장 없음)
- **THEN** `type=ENDED` 메시지가 행위자 없이도 발행되어 `/topic/rooms/{id}`로 중계되어야 한다

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

### Requirement: 게임 시작 시 라운드 엔진이 첫 라운드를 자동으로 연다

시스템은 방이 `PLAYING`으로 전이되면 플레이리스트의 모든 트랙을 한 라운드씩 도는 라운드 엔진을 시작해야(SHALL) 한다. 게임 시작 시 트랙 재생 순서를 정해(기본값은 셔플) 시퀀스를 라운드 상태(Redis `room:{id}:round`)에 **고정**하고 — 재접속·장애 회수가 같은 순서를 보도록 — 첫 라운드를 `OPEN` 단계로 열어 마감 시각(`deadlineAt`)을 설정해야 한다. 총 라운드 수는 트랙 수와 같다. 라운드 진행 단계(`RoundPhase`: `OPEN`/`REVEAL`/`ENDED`)는 `RoomStatus`와 분리된 휘발성 상태로, `RoomStatus`는 `PLAYING`으로 유지된다.

#### Scenario: 게임이 시작되면 첫 라운드가 OPEN으로 열린다
- **WHEN** 방장이 게임을 시작해 방이 `PLAYING`으로 전이
- **THEN** 라운드 엔진이 정해진 트랙 순서(기본 셔플)를 `room:{id}:round`(Redis)에 고정해야 한다
- **AND** 첫 라운드가 `OPEN` 단계로 열리고 `deadlineAt`이 `openAt + (endTimeSec − startTimeSec) × repeatCount + 버퍼`로 설정되어야 한다
- **AND** `ROUND_STARTED` 이벤트가 등록되어 모든 참가자에게 전파되어야 한다

### Requirement: 라운드는 첫 정답 또는 클립 소진으로 종료된다

시스템은 `OPEN` 라운드를 (a) 첫 정답자가 나오거나 (b) 클립이 `repeatCount`회 재생 완료되어 마감 시각에 도달하는 것 중 **먼저** 발생한 사건으로 종료하고 `REVEAL`로 전이해야(SHALL) 한다. 첫 정답 경로는 정답 메시지 수신으로 즉시, 마감 경로는 서버 타이머로 발화하며, 두 경로 모두 동일한 전이 게이트를 통과한다. 정답 일치는 입력과 정답 양쪽에서 **모든 공백을 제거하고 대소문자를 무시**해 비교하며, **마감 시각 이후 도착한 정답은 인정하지 않는다**(마감 전 추측만 유효).

#### Scenario: 첫 정답자가 나오면 라운드가 즉시 종료된다
- **WHEN** `OPEN` 라운드에서 한 참가자의 채팅이 현재 트랙의 정답(`title` 또는 `additionalTitles`)과 일치(모든 공백 제거·대소문자 무시 후, 마감 시각 이내)
- **THEN** 라운드가 `REVEAL`로 전이되고 그 참가자가 승자(`winnerId`)로 기록되어야 한다
- **AND** 정답 채팅 원문은 일반 채팅으로 방송되지 않아야 한다

#### Scenario: 아무도 못 맞히면 클립 소진 시점에 라운드가 종료된다
- **WHEN** `OPEN` 라운드에서 마감 시각까지 정답자가 없음
- **THEN** 서버 타이머가 라운드를 `REVEAL`로 전이하고 승자는 없어야(`winnerId` 공란) 한다

#### Scenario: 공백만 다른 답도 정답으로 인정된다
- **WHEN** 정답이 `밤을 달리다`인 라운드에서 참가자가 `밤을 달 리다`·`밤을     달리다`·`밤 을 달리다` 중 하나를 입력
- **THEN** 모든 공백을 제거한 비교에서 일치하므로 정답으로 인정되어야 한다

#### Scenario: 마감 시각 이후 도착한 정답은 인정되지 않는다
- **WHEN** 라운드 마감 시각이 지난 뒤(아직 sweeper가 `REVEAL`로 닫기 전) 정답 채팅이 도착
- **THEN** `now<=deadline` 게이트에서 거부되어 승자로 기록되지 않아야 한다
- **AND** 그 라운드는 승자 없이(`winnerId` 공란) 타임아웃 처리되어야 한다

### Requirement: 첫 정답자만 1점을 얻고 최고 득점자가 우승한다

시스템은 라운드의 첫 정답자에게만 1점을 부여해야(SHALL) 하며, 점수는 플레이어별로 누적되어 게임 종료 시 최고 득점자가 우승이다. 점수판은 Redis `room:{id}:scores`(ZSET)에 저장하고, 가점은 해당 플레이어가 아직 방 멤버일 때만 원자적으로 적용한다.

#### Scenario: 첫 정답자에게만 1점이 누적된다
- **WHEN** 한 라운드에서 참가자가 첫 정답으로 라운드를 종료
- **THEN** 그 참가자의 점수판(`room:{id}:scores`) 점수가 1 증가해야 한다
- **AND** 같은 라운드의 다른 추측이나 타임아웃에는 점수가 부여되지 않아야 한다

### Requirement: 정답 공개 후 다음 라운드로 자동 진행하고 마지막이면 종료된다

시스템은 `REVEAL` 단계를 **5초**간 유지한 뒤, 남은 트랙이 있으면 별도의 카운트다운 없이 다음 라운드를 `OPEN`으로 자동 진행하고, 마지막 트랙이면 게임을 `ENDED`로 종료해야(SHALL) 한다. REVEAL 길이는 정답자 유무·방 설정과 무관하게 항상 5초로 고정한다. 전이는 서버 타이머가 구동한다.

#### Scenario: REVEAL 후 다음 라운드가 곧장 열린다
- **WHEN** `REVEAL` 단계가 5초 경과하고 남은 트랙이 있음
- **THEN** 다음 트랙으로 새 `OPEN` 라운드가 열리고 `ROUND_STARTED`가 전파되어야 한다

#### Scenario: 마지막 라운드의 REVEAL 후 게임이 종료된다
- **WHEN** `REVEAL` 단계가 5초 경과하고 남은 트랙이 없음
- **THEN** 게임이 `ENDED`로 종료되고 최종 점수판이 전파되어야 한다

### Requirement: 정답은 OPEN 동안 클라이언트에 노출되지 않는다

시스템은 라운드가 `OPEN`인 동안 정답(`title`·`additionalTitles`)을 클라이언트로 전송하지 않아야(MUST NOT) 한다. 클라이언트에 내려가는 게임용 트랙 정보는 정답을 제거한(answer-stripped) 재생 참조(`embedId`·`startTimeSec`·`endTimeSec`·`repeatCount`)만 포함하며, 정답은 `REVEAL`·`ENDED` 단계에서만 포함한다. 서버만 정답을 보유해 채팅을 대조한다.

`REVEAL` 단계에서는 **다음 라운드의 answer-stripped 재생 참조를 함께 내려보낸다**(클라이언트 선버퍼링용). 이 참조 역시 정답을 포함하지 않는다.

> 이 선전달이 정답 노출 요구사항을 약화시키지 않는 이유: 재생 참조의 `embedId`는 이미 `ROUND_STARTED`로 라운드 시작 시점에 내려가고 있어, 본 요구사항이 실제로 보호하는 것은 `title`·`additionalTitles`다. 선전달은 `embedId`가 클라이언트에 도달하는 시점을 `REVEAL` 구간만큼 앞당길 뿐 새로운 정보를 노출하지 않는다. 근거는 `design.md` Decision 2.

#### Scenario: ROUND_STARTED에 정답이 포함되지 않는다
- **WHEN** `OPEN` 라운드가 열려 `ROUND_STARTED`가 전파됨
- **THEN** 메시지에 `embedId`·재생 구간·`repeatCount`는 포함되지만 `title`·`additionalTitles`는 포함되지 않아야 한다

#### Scenario: 정답은 REVEAL에서만 공개된다
- **WHEN** 라운드가 `REVEAL`로 전이되어 `ROUND_REVEALED`가 전파됨
- **THEN** 메시지에 정답(`title`)과 승자·갱신된 점수판이 포함되어야 한다

#### Scenario: REVEAL은 다음 라운드의 재생 참조를 동봉한다
- **WHEN** 마지막이 아닌 라운드가 `REVEAL`로 전이되어 `ROUND_REVEALED`가 전파됨
- **THEN** 메시지에 다음 라운드의 answer-stripped 재생 참조가 포함되어야 한다
- **AND** 그 참조에 다음 라운드의 정답(`title`·`additionalTitles`)은 포함되지 않아야 한다

#### Scenario: 마지막 라운드의 REVEAL에는 다음 재생 참조가 없다
- **WHEN** 마지막 라운드가 `REVEAL`로 전이됨
- **THEN** `ROUND_REVEALED`의 다음 라운드 재생 참조가 비어(null) 있어야 한다

### Requirement: 라운드 전이는 다중 인스턴스·인스턴스 장애에서도 정확히 한 번 일어난다

시스템은 백엔드가 다중 인스턴스(Swarm replica)로 떠 있고 일부 인스턴스가 장애로 사라져도, 각 라운드 전이를 정확히 한 번만 수행해야(SHALL) 한다. 전이의 멱등성은 분산 락이 아니라 라운드 상태(Redis `room:{id}:round`)에 대한 단일 원자 연산(`roundSeq`·`phase` 게이트)으로 보장하며, 시각 판정은 Redis 단일 시계로 통일한다. 타임아웃·정답 공개 전이는 단일 인스턴스만 실행하는 sweeper(주기 폴링, ShedLock으로 단일 실행 보장)가 구동하고, 정밀이 필요한 첫 정답은 들어온 메시지로 즉시 전이한다. replica마다 모든 방의 로컬 타이머를 두지 않는다.

#### Scenario: 동시 발화가 이중 전이를 일으키지 않는다
- **WHEN** 같은 라운드에 대해 sweeper·첫 정답 경로(또는 ShedLock 만료 중 두 sweeper)가 거의 동시에 전이를 시도
- **THEN** `roundSeq`·`phase`가 기대값과 일치하는 첫 연산만 전이하고 나머지는 무시(no-op)되어야 한다
- **AND** 라운드는 정확히 한 번 전이되고 승자는 최대 한 명이어야 한다

#### Scenario: sweep 리더가 죽어도 전이가 이어진다
- **WHEN** sweep을 돌던 replica가 사라져 그 시점의 자동 전이가 멈춤
- **THEN** 다른 replica가 ShedLock을 이어받아 `rounds:deadlines`(Redis ZSET)의 마감 지난 미전이 라운드를 전이해야 한다
- **AND** 게임이 멈추지 않고 다음 단계로 진행되어야 한다 (첫 정답은 그 사이에도 이벤트 구동으로 동작)

### Requirement: 게임 중 퇴장한 플레이어는 점수판에서 제거된다

시스템은 게임 진행 중 플레이어가 방을 떠나면 그 플레이어를 점수판(Redis `room:{id}:scores`)에서 제거해야(SHALL) 한다. 방장이 떠나도 라운드 엔진은 서버 주도이므로 게임이 멈추지 않는다. 마지막 플레이어가 떠나 방이 삭제되면 라운드 상태(`room:{id}:round`·`rounds:deadlines`·`room:{id}:scores`)도 함께 정리되어, 삭제된 방이 계속 구동되지 않아야 한다.

#### Scenario: 퇴장 시 점수판에서 빠진다
- **WHEN** 게임 중 한 플레이어가 방을 떠남
- **THEN** 그 플레이어가 `room:{id}:scores`에서 제거되어 이후 순위에 나타나지 않아야 한다

#### Scenario: 방이 비면 라운드 상태가 정리된다
- **WHEN** 게임 중 마지막 플레이어가 떠나 방이 삭제됨
- **THEN** 해당 방의 라운드 상태와 마감 등록(`rounds:deadlines` 멤버)이 제거되어야 한다
- **AND** sweeper가 삭제된 방을 더 이상 전이하지 않아야 한다

### Requirement: 점수판·승자 표기용 닉네임은 서버가 해석해 전달한다

시스템은 점수판을 클라이언트로 내보낼 때 각 항목에 **해당 플레이어의 닉네임을 함께 실어야(SHALL) 한다.** 닉네임 해석은 방 멤버십이 아니라 **플레이어 저장소(MySQL `player`)** 를 기준으로 하므로, 이미 방을 떠난 참가자도 이름이 해석된다. 승자(`winnerId`)의 닉네임도 같은 방식으로 함께 전달한다.

해석은 점수판 전송 DTO가 만들어지는 모든 지점에 적용된다 — 실시간 `ROUND_REVEALED` 전파와 재접속 라운드 스냅샷 조회. 조회는 점수판 id와 승자 id를 합친 집합에 대해 **한 번의 배치 조회**로 수행한다.

해석되지 않는 id가 있어도 **라운드 전파나 스냅샷 응답이 실패해서는 안 된다(SHALL NOT).** 그런 항목은 중립 라벨로 대체해 응답을 완성한다. 퇴장 여부는 이 지점에서 알 수 없는 정보이므로 퇴장을 단정하는 라벨을 쓰지 않는다.

라운드 상태 저장 구조(Redis `room:{id}:scores` ZSET)와 라운드 전이 로직(Lua CAS)은 변경하지 않는다. 닉네임은 저장 대상이 아니라 전송 시점에 해석하는 표현 정보다.

#### Scenario: 점수판 항목이 닉네임을 담는다
- **WHEN** 라운드가 `REVEAL`로 전이되어 점수판이 전파됨
- **THEN** 각 점수 항목에 `playerId`·`score`와 함께 그 플레이어의 닉네임이 포함되어야 한다

#### Scenario: 퇴장한 플레이어의 닉네임도 해석된다
- **WHEN** 점수판 또는 승자 id에 이미 방을 떠난 플레이어가 포함됨
- **THEN** 방 멤버십과 무관하게 플레이어 저장소에서 닉네임이 해석되어야 한다

#### Scenario: 해석 실패가 라운드 전파를 막지 않는다
- **WHEN** 점수판의 어떤 id에 대응하는 플레이어를 찾을 수 없음
- **THEN** 그 항목이 중립 라벨로 채워진 채 `ROUND_REVEALED`가 정상 전파되어야 한다
- **AND** 예외로 인해 라운드 전이·전파가 중단되지 않아야 한다

### Requirement: 재접속 시 서버가 정답 없는 라운드 스냅샷을 제공한다

시스템은 게임 중 새로고침하거나 유예 시간 내에 재접속한 멤버에게 현재 라운드 상태(`RoundPhase`·`roundSeq`·총 라운드 수·`deadlineAt`·점수판·재생 참조)를 제공해야(SHALL) 한다. 응답은 단계로 게이팅되어, `OPEN` 중에는 정답을 포함하지 않고 `REVEAL`·`ENDED`에서만 포함한다.

스냅샷의 점수판 항목은 실시간 전파와 **동일한 형태**여야(SHALL) 한다 — 각 항목이 닉네임을 담고, 승자 닉네임도 함께 포함한다. 형태가 갈리면 재접속으로 복원한 화면만 이름을 잃어, 실시간으로 받은 화면과 다르게 보인다.

스냅샷이 `REVEAL` 단계일 때는 **다음 라운드의 answer-stripped 재생 참조를 함께 포함해야(SHALL) 한다.** `REVEAL` 중 재접속한 멤버는 `ROUND_REVEALED` 이벤트를 놓쳐 선버퍼링 기회를 잃는데, 그러면 그 멤버만 다음 라운드에서 로드·버퍼링 지연을 온전히 부담해 참가자 간 불균등이 재현된다. 마지막 라운드에서는 비어 있다.

#### Scenario: OPEN 중 재접속은 정답 없이 라운드를 복원한다
- **WHEN** `OPEN` 라운드 진행 중 멤버가 재접속해 라운드 스냅샷을 조회
- **THEN** 응답에 `RoundPhase`·`deadlineAt`·점수판·answer-stripped 재생 참조가 포함되어야 한다
- **AND** 정답(`title`·`additionalTitles`)은 포함되지 않아야 한다

#### Scenario: 스냅샷 점수판도 닉네임을 담는다
- **WHEN** 멤버가 재접속해 라운드 스냅샷을 조회
- **THEN** 각 점수 항목에 닉네임이 포함되고, `REVEAL` 단계라면 승자 닉네임도 포함되어야 한다

#### Scenario: REVEAL 중 재접속도 다음 트랙을 선버퍼링할 수 있다
- **WHEN** `REVEAL` 단계에서 멤버가 재접속해 라운드 스냅샷을 조회
- **THEN** 응답에 다음 라운드의 answer-stripped 재생 참조가 포함되어야 한다
- **AND** 그 멤버는 이벤트를 받은 멤버와 동일하게 선버퍼링을 시작할 수 있어야 한다

### Requirement: 게임은 마지막 라운드 후 서버 주도로 종료되어 방이 ACTIVE로 복귀한다

시스템은 마지막 라운드가 끝나면 라운드 엔진이 게임을 종료해 방 상태(MySQL `room.status`)를 `PLAYING`에서 `ACTIVE`로 되돌려야(SHALL) 한다. 이 종료는 방장 권한과 무관하게 멱등하게 일어나며, 기존 `GameEndedEvent`(`ENDED`) 전파 경로를 재사용한다. 최종 점수판은 마지막 라운드의 `ROUND_REVEALED`로 이미 전달되므로 `ENDED`는 종료 신호만 전하고, **서버 주도 종료에는 종료를 일으킨 방장 행위자가 없을 수 있다**(방장이 이미 떠난 경우). 방장의 수동 종료(`/app/rooms/end`)도 같은 전이 경로를 통과해 Redis 라운드 상태와 DB 방 상태를 함께 이동시켜, 둘이 어긋나 방이 입장·재시작 불가로 고착되지 않아야 한다.

#### Scenario: 마지막 라운드 후 게임이 자동 종료된다
- **WHEN** 마지막 라운드의 `REVEAL`이 끝남
- **THEN** 방 상태(MySQL `room.status`)가 `ACTIVE`로 전이되고 신규 입장이 다시 허용되어야 한다
- **AND** `ENDED` 이벤트가 전파되어야 한다 (최종 점수판은 직전 `ROUND_REVEALED`로 이미 전달됨)

#### Scenario: 방장 수동 종료가 라운드 상태와 방 상태를 함께 되돌린다
- **WHEN** 방장이 게임 중 `/app/rooms/end`로 강제 종료
- **THEN** 라운드 상태(Redis)와 방 상태(`PLAYING→ACTIVE`)가 함께 정리되어야 한다
- **AND** 종료 후 sweeper가 해당 방의 잔여 라운드를 전이하지 않아야 한다
