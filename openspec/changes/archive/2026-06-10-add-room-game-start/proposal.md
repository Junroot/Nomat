## Why

`room` 모듈은 방 생성·입장·퇴장·채팅까지 실시간으로 완성돼 있다. STOMP CONNECT 시 인증(`JwtHandshakeInterceptor`)·비밀번호 검증·정원 체크·분산 락 입장(`RoomJoinChannelInterceptor` → `Room.join`), 끊김 시 유예 후 자동 퇴장(`ReconnectGracePeriodManager`)·세션 교체(`ActiveSessionManager`), 그리고 도메인 이벤트 → Redis pub/sub(`room:{id}:events`) → STOMP fan-out(`/topic/rooms/{id}`)으로 이어지는 실시간 백본이 모두 갖춰져 있다.

비어 있는 단 하나는 **"게임 시작"**이다. 프론트 `RoomView`의 "시작하기" 버튼은 `onClick: () => {}` 빈 스텁이고(`front/app/routes/RoomView.tsx:82`), `RoomStatus`에는 `PENDING`·`ACTIVE`만 있어 "게임 중"을 표현할 상태가 없다. 게임 자체(라운드·정답·점수)를 한 번에 만들려 하면 게임 상태 저장소·서버 타이머·정답 매칭 같은 큰 설계 결정이 미정인 채로 진흙탕이 된다.

본 변경은 그 큰 결정들을 **의도적으로 미루고**, 가장 작은 끝-to-끝 슬라이스만 관통시킨다: **"방장이 시작을 누르면 모든 참가자 화면이 게임 중 상태로 바뀐다."** 이 슬라이스는 새 도메인 모듈 없이 기존 실시간 레일을 100% 재사용하면서, 게임을 만들 때 반드시 못 박아야 할 두 가지 — **게임 세션 상태 머신**과 **게임 중 입장 권한** — 을 강제로 확정한다. 라운드 엔진은 이 위에 얹힌다.

핵심 비자명 결정: **중간 입장 금지의 집행점은 방 목록도, STOMP CONNECT 차단도 아닌 `Room.join()` 도메인 가드 한 곳이다.** 게임 중 끊겼다 돌아오는 기존 멤버는 인터셉터의 재접속 경로(`cancelGracePeriod`)로 들어와 `join()`을 우회하므로, 가드를 `join()`에 두면 신규 입장만 막히고 재접속은 무사히 통과한다. "PLAYING이면 CONNECT 거부" 같은 순진한 구현은 게임 중 네트워크 깜빡임에 영영 튕겨나가는 최악의 UX를 만든다 (`design.md` Decision 2).

## What Changes

### back/ (`room` 모듈)

- `room/application/domain/RoomStatus.kt`: enum에 `PLAYING` 추가 (`PENDING`, `ACTIVE`, `PLAYING`)
- `room/application/domain/Room.kt`:
  - `fun start(playerId: Long)` 추가 — 방장(`master`)만, `ACTIVE`에서만 `PLAYING`으로 전이, `GameStartedEvent` 등록. 위반 시 `ForbiddenException`/`ConflictException`
  - `fun end(playerId: Long)` 추가 — 방장만, `PLAYING`에서만 `ACTIVE`로 전이, `GameEndedEvent` 등록 (슬라이스를 닫기 위한 최소 종료 전이)
  - `fun join(playerId: Long)`에 **가드 추가**: `status == PLAYING`이면 `ConflictException("게임 중에는 입장할 수 없습니다.")` — 기존 정원·중복 검사 앞에 위치. 이것이 중간 입장 금지의 단일 집행점
- `room/application/domain/GameStartedEvent.kt`, `GameEndedEvent.kt`: 신규 도메인 이벤트(`roomId`, `playerId`) — 기존 `RoomJoinedEvent` 패턴 동일, 직렬화 안정성 위해 `application/domain` 패키지에 위치
- `room/application/RoomService.kt`: `start(roomId, playerId)`, `end(roomId, playerId)` 추가 — 기존 `join`/`leave`와 동일하게 `distributedLockExecutor.withLock("room:$roomId:lock")` + `REQUIRES_NEW` 트랜잭션으로 감싼다
- `room/application/dto/RoomEventMessage.kt`: `@JsonSubTypes`에 `STARTED`, `ENDED` 추가
- `room/application/dto/GameStartedEventMessage.kt`, `GameEndedEventMessage.kt`: `RoomEventMessage` 구현(`roomId`, `playerId`, `nickname`=시작/종료한 방장). 기존 `RoomJoinedEventMessage`와 동형
- `room/in/RoomStompController.kt`(`private class` 유지): `@MessageMapping("/rooms/start")`, `@MessageMapping("/rooms/leave")`와 동일하게 `roomSession()`에서 `roomId`·`playerId`를 얻어 `roomService.start(...)` 호출. `/rooms/end`도 동일
- `room/in/RoomEventListener.kt`(`private class` 유지): `@TransactionalEventListener(AFTER_COMMIT)`로 `GameStartedEvent`/`GameEndedEvent`를 받아 `GameStartedEventMessage`/`GameEndedEventMessage`를 `room:{id}:events` Redis 채널로 발행 — `handleRoomJoined`와 동일 구조
- `room/application/dto/RoomDetailResponse.kt`: `status: RoomStatus` 필드 추가 — 새로고침/재접속 시 클라이언트가 게임 화면을 복원하도록 현재 상태를 노출

> `RoomEventRedisSubscriber`·`RoomJoinChannelInterceptor`·`RoomDisconnectListener`는 **변경 없음**. 재접속이 `join()`을 우회하는 기존 분기 덕에 입장 차단은 도메인 가드만으로 정합한다(Decision 2).

### front/

- `app/utils/RoomDetailResponse.ts`: `status` 필드 추가(`"PENDING" | "ACTIVE" | "PLAYING"`)
- `app/hooks/useRoomSubscription.ts`:
  - `RoomEventMessage` 유니온에 `STARTED`/`ENDED` 타입 추가, `handleEventRef`에 분기 추가 → 게임 상태(`status`) 갱신 + 시스템 메시지
  - 훅 반환에 `status`, `startGame()`, `endGame()` 추가. `startGame`/`endGame`은 `client.publish({ destination: "/app/rooms/start" | "/app/rooms/end" })`
  - 최초 `fetchRoomDetail` 응답의 `status`로 초기 게임 상태 세팅(재접속/새로고침 복원)
- `app/routes/RoomView.tsx`: `시작하기` 버튼의 빈 `onClick`(82행)을 `startGame`에 연결하고 **방장에게만** 노출. `status === "PLAYING"`이면 게임 중 화면(이번 슬라이스에선 최소 플레이스홀더 — 라운드 UI는 후속) 렌더, 방장에게 "게임 종료" 액션 제공

## Capabilities

### New Capabilities
- `room-game-session`: 방의 게임 세션 상태(`PLAYING`)와 그 진입/이탈 전이를 다루는 능력. 방장이 게임을 시작·종료할 수 있고, 게임 중에는 신규 입장이 거부되되 기존 멤버의 재접속은 허용되며, 상태 전이가 실시간으로 전파되고, 게임 중인 방은 공개 목록에서 제외되고, 방 상세 조회가 현재 상태를 포함한다. (라운드·정답·점수는 본 능력의 범위가 아니며 후속 변경에서 본 능력에 requirement를 ADD한다.)

### Modified Capabilities
- 없음 (기존 spec 중 영향받는 능력 없음 — `room`에 대한 기존 capability spec이 없어 신규로 시작)

## Impact

- **서브프로젝트**: `back/`(room 모듈), `front/`(RoomView·useRoomSubscription·RoomDetailResponse 타입). `infra/` 영향 없음
- **도메인 모듈**: `room`만 변경. `playlist`/`player`/`favoriteplaylist`/`auth` 무변경
- **헥사고날 계층**:
  - `application/domain`: `RoomStatus`(enum 값 추가), `Room`(start/end/join 가드), `GameStartedEvent`·`GameEndedEvent` 신규
  - `application`(서비스): `RoomService.start`/`end` 추가
  - `application/dto`: `RoomEventMessage` 서브타입 2종 추가, `RoomDetailResponse.status` 추가
  - `in`: `RoomStompController`(매핑 2개 추가), `RoomEventListener`(리스너 2개 추가) — 둘 다 `private class` 유지
  - `out`: 변경 없음 (저장소 인터페이스·구현 무변경, `findByIdGreaterThanAndStatusOrderByIdDesc`는 기존 그대로 `ACTIVE`만 조회)
- **DB 스키마**: **변경 없음**. `room.status`는 `CHAR(20)` 컬럼으로 이미 존재하고 `PENDING`/`ACTIVE` 문자열을 저장 중 → enum 값 `PLAYING` 추가는 DDL/Flyway 마이그레이션이 **불필요**(새 문자열 값일 뿐). 기존 행은 영향 없음
- **ES 매핑**: 해당 없음 (`PlaylistDocument`·Debezium CDC 무관)
- **Kafka 토픽**: 해당 없음
- **Redis 키/채널**: **신규 없음**. 전파는 기존 `room:{id}:events` pub/sub 채널 재사용, 동시성 제어는 기존 `room:{id}:lock` 분산 락 재사용. `player:session:*`·유예 처리 무변경
- **이벤트 직렬화**: `GameStartedEvent`/`GameEndedEvent`는 `AbstractAggregateRoot` + `@TransactionalEventListener(AFTER_COMMIT)`(ephemeral broadcast) 경로 — Modulith outbox(`event_publication`) 미사용이므로 직렬화 안정성 부담 없음(채팅·입퇴장과 동일)
- **API 계약 변화**: `GET /rooms/{roomId}` 응답에 `status` 필드 **추가**(하위호환 — 기존 필드 불변). 신규 STOMP 인바운드 `/app/rooms/start`·`/app/rooms/end`, 신규 STOMP 아웃바운드 이벤트 `STARTED`·`ENDED`
- **의존성**: 추가/제거 없음
- **동작 변화**:
  - 방장이 시작 → 방 `PLAYING` → 목록에서 자동 제외(`get()`이 `ACTIVE`만 조회) → 신규 입장 거부 → 기존 멤버 재접속은 허용 → 방장 종료 시 `ACTIVE` 복귀·입장 재개
  - (b) A방 멤버가 PLAYING인 B방으로 이동 시도 → `leave(A)` 커밋 후 `join(B)`가 가드로 거부 → A에도 B에도 없는 상태로 끝남. **현행 동작 유지(의도된 선택)** — "이동 시도 = A 떠남의 약속"으로 간주, 코드 변경 없음(`design.md` Decision 6)
- **롤백**: 단일 PR `git revert`. DB·인프라·외부 시스템 변경이 없어 코드 원복만으로 완전 롤백. 진행 중이던 `PLAYING` 방은 revert 후 `get()`에서 계속 숨겨지나(상태값 잔존) `start`/`end` 경로 소멸 — 잔존 `PLAYING` 행은 멤버 전원 퇴장 시 삭제되거나 운영상 무시 가능
