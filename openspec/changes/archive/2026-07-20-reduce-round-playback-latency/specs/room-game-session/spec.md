## MODIFIED Requirements

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

### Requirement: 게임 시작·종료 전이는 모든 참가자에게 실시간 전파된다

시스템은 게임 시작·종료·라운드 전이를 기존 도메인 이벤트 → Redis Pub/Sub(`room:{id}:events` 채널) → STOMP(`/topic/rooms/{id}`) 경로로 방의 모든 구독자에게 브로드캐스트해야(SHALL) 한다. 메시지는 `RoomEventMessage`의 서브타입으로 직렬화되며, **새로운 채널이나 전파 인프라를 추가하지 않는다**. 시작은 `STARTED`, 라운드 전이는 `ROUND_STARTED`·`ROUND_REVEALED`, 종료는 `ENDED` 서브타입을 쓴다.

`ROUND_REVEALED` 서브타입은 다음 라운드의 재생 참조를 담는 **nullable 필드**를 포함한다. 마지막 라운드에서는 비어 있다. 이 필드 추가는 기존 채널·서브타입 체계 안에서 이뤄지며 새 이벤트 타입을 도입하지 않는다.

기존 슬라이스에서 `ENDED`는 종료를 일으킨 방장(`playerId`, `nickname`)을 실었으나, 본 능력에서 종료는 **라운드 엔진이 서버 주도로** 일으킬 수도 있다. 따라서 `ENDED`의 행위자 필드는 방장 수동 종료일 때만 채워지고 **서버 주도 종료에서는 행위자가 없을 수 있다**(수신 측은 행위자 유무와 무관하게 게임 종료로 처리). 최종 점수판은 별도로 `ENDED`에 싣지 않고 직전 `ROUND_REVEALED`가 전달한다.

#### Scenario: 게임 시작이 STARTED로 브로드캐스트된다
- **WHEN** 방 상태가 `PLAYING`으로 전이되어 트랜잭션이 커밋(AFTER_COMMIT)
- **THEN** `room:{id}:events` Redis 채널로 `type=STARTED` 메시지가 발행되어 `/topic/rooms/{id}`로 중계되어야 한다

#### Scenario: 라운드 전이가 ROUND_STARTED·ROUND_REVEALED로 브로드캐스트된다
- **WHEN** 라운드가 `OPEN`으로 열리거나 `REVEAL`로 전이됨
- **THEN** 각각 `type=ROUND_STARTED`·`ROUND_REVEALED` 메시지가 `room:{id}:events`로 발행되어 모든 참가자에게 중계되어야 한다

#### Scenario: 서버 주도 종료도 행위자 없이 ENDED로 브로드캐스트된다
- **WHEN** 라운드 엔진이 마지막 라운드 후 서버 주도로 게임을 종료(종료를 누른 방장 없음)
- **THEN** `type=ENDED` 메시지가 행위자 없이도 발행되어 `/topic/rooms/{id}`로 중계되어야 한다

### Requirement: 재접속 시 서버가 정답 없는 라운드 스냅샷을 제공한다

시스템은 게임 중 새로고침하거나 유예 시간 내에 재접속한 멤버에게 현재 라운드 상태(`RoundPhase`·`roundSeq`·총 라운드 수·`deadlineAt`·점수판·재생 참조)를 제공해야(SHALL) 한다. 응답은 단계로 게이팅되어, `OPEN` 중에는 정답을 포함하지 않고 `REVEAL`·`ENDED`에서만 포함한다. (이 스냅샷을 받아 화면을 복원하는 프론트엔드 UI는 후속 변경의 범위다.)

스냅샷이 `REVEAL` 단계일 때는 **다음 라운드의 answer-stripped 재생 참조를 함께 포함해야(SHALL) 한다.** `REVEAL` 중 재접속한 멤버는 `ROUND_REVEALED` 이벤트를 놓쳐 선버퍼링 기회를 잃는데, 그러면 그 멤버만 다음 라운드에서 로드·버퍼링 지연을 온전히 부담해 본 변경이 줄이려는 **참가자 간 불균등이 오히려 재현된다.** 마지막 라운드에서는 비어 있다.

#### Scenario: OPEN 중 재접속은 정답 없이 라운드를 복원한다
- **WHEN** `OPEN` 라운드 진행 중 멤버가 재접속해 라운드 스냅샷을 조회
- **THEN** 응답에 `RoundPhase`·`deadlineAt`·점수판·answer-stripped 재생 참조가 포함되어야 한다
- **AND** 정답(`title`·`additionalTitles`)은 포함되지 않아야 한다

#### Scenario: REVEAL 중 재접속도 다음 트랙을 선버퍼링할 수 있다
- **WHEN** `REVEAL` 단계에서 멤버가 재접속해 라운드 스냅샷을 조회
- **THEN** 응답에 다음 라운드의 answer-stripped 재생 참조가 포함되어야 한다
- **AND** 그 멤버는 이벤트를 받은 멤버와 동일하게 선버퍼링을 시작할 수 있어야 한다
