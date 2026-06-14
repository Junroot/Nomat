# Design — add-room-game-start

## Context

`room` 모듈의 실시간 흐름은 이미 정교하게 완성돼 있다. 탐색 과정에서 "게임 시작"을 한 번에 게임 전체로 만들지 않고, 상태 전이만 끝-to-끝으로 관통시키는 워킹 스켈레톤으로 잘라내기로 했다. 그 결정의 근거와 비자명한 경계들을 아래 Decision으로 확정한다.

현재 상태 머신:

```
PENDING ──join(첫 입장)──▶ ACTIVE
  (생성 직후)               (멤버 ≥1, get() 목록 노출)
```

본 변경 후:

```
                  start(방장)              end(방장)
PENDING ─join─▶ ACTIVE ───────────▶ PLAYING ───────────▶ ACTIVE
                 │ ▲                  │  ▲                  │
        신규입장 허용 └─목록 노출       신규입장 거부 ★     목록 재노출
                                      재접속은 허용         입장 재개
                                      목록 자동 비노출
```

관련 기존 코드:
- `Room`(`AbstractAggregateRoot`): `status`(`PENDING`/`ACTIVE`), `master`(=`sortedEntries.firstOrNull()`), `join`/`leave` 도메인 메서드 + `registerEvent`
- `RoomService.join`/`leave`: `distributedLockExecutor.withLock("room:$roomId:lock")` + `REQUIRES_NEW` 트랜잭션
- `RoomEventListener`: `@TransactionalEventListener(AFTER_COMMIT)` → Redis `room:{id}:events` 발행
- `RoomEventRedisSubscriber`: Redis 구독 → `/topic/rooms/{id}` STOMP fan-out
- `RoomJoinChannelInterceptor.preSend`(CONNECT): 세션/유예 상태에 따라 `join()` 또는 `cancelGracePeriod()` 분기

## Goals / Non-Goals

**Goals**
- `RoomStatus.PLAYING` 도입과 방장 트리거 `ACTIVE↔PLAYING` 전이(시작/종료)
- 게임 중 신규 입장 거부 + 기존 멤버 재접속 보존
- 전이의 실시간 전파(기존 pub/sub·STOMP 레일 재사용)
- 재접속/새로고침 시 게임 화면 복원을 위한 상태 노출

**Non-Goals**
- 라운드 진행 로직, 트랙 재생/오디오 동기화, 정답 입력·매칭(ES Nori 재사용 후보), 점수 계산, 서버 권위 타이머 — 전부 후속 "라운드 엔진" 변경
- 게임 상태(라운드/점수)의 저장소 선택(MySQL vs Redis) — 본 슬라이스는 방 수준 상태 전이만 다루므로 결정 불필요
- 관전자/대기열 — 중간 입장을 허용하지 않기로 했으므로 불필요

## Decision 1 — 전이 권한·상태 검증은 도메인(`Room`)에, 동시성은 서비스에

`start`/`end`를 `Room`의 도메인 메서드로 두고, 권한(방장만)과 상태 가드(ACTIVE/PLAYING에서만)를 엔티티 안에서 강제한다. `RoomService`는 기존 `join`/`leave`와 동일하게 분산 락 + `REQUIRES_NEW`로 감싸 동시성만 책임진다.

```kotlin
// Room.kt
fun start(playerId: Long) {
    if (master?.playerId != playerId) throw ForbiddenException("방장만 게임을 시작할 수 있습니다.")
    if (status != RoomStatus.ACTIVE) throw ConflictException("시작할 수 없는 방 상태입니다.")
    status = RoomStatus.PLAYING
    registerEvent(GameStartedEvent(id, playerId))
}

fun end(playerId: Long) {
    if (master?.playerId != playerId) throw ForbiddenException("방장만 게임을 종료할 수 있습니다.")
    if (status != RoomStatus.PLAYING) throw ConflictException("종료할 수 없는 방 상태입니다.")
    status = RoomStatus.ACTIVE
    registerEvent(GameEndedEvent(id, playerId))
}
```
```kotlin
// RoomService.kt — join/leave와 동형
fun start(roomId: Long, playerId: Long) {
    distributedLockExecutor.withLock("room:$roomId:lock") {
        writeTransactionTemplate.executeWithoutResult {
            val room = roomRepository.findById(roomId) ?: throw NotFoundException(NotFoundResource.ROOM)
            room.start(playerId)
            roomRepository.save(room)
        }
    }
}
```

`master`는 `sortedEntries.firstOrNull()`(가장 먼저 입장한 멤버)이다.

**락이 필요한 이유 — 정원이 아니라 `status`-읽기 경합이다.** `room:{id}:lock`은 원래 `join` vs `join`의 정원 초과(`entries.size` read-check-write)를 직렬화하려고 도입됐다. `start`/`end`는 `entries`를 건드리지 않으므로 그 *정원* 관점에서는 락이 필요 없다. 그러나 `start`는 **`join`의 중간입장 금지 가드가 읽는 바로 그 `status`를 뒤집는다.** 둘이 같은 락을 공유하지 않으면 아래 인터리빙으로 가드가 무력화된다:

```
시각   join(신규 입장)               start(방장)
─────────────────────────────────────────────────────────
t0    Room 로드 (status=ACTIVE)
t1    가드 통과 (PLAYING 아님 ✓)
t2                                  Room 로드 (status=ACTIVE)
t3                                  status=PLAYING 커밋  (room 테이블)
t4    entries 추가 커밋  (room_entry 테이블)
─────────────────────────────────────────────────────────
결과: status=PLAYING + 신규 멤버 추가 = "시작된 게임에 입장" ★ 위반
```

두 쓰기가 서로 다른 테이블(`room` vs `room_entry`)이라 DB 레벨 충돌이 없고 둘 다 커밋되며, 엔티티에 `@Version`도 없다 → 이 코드베이스의 유일한 직렬화 수단이 분산 락이다. 따라서 **`start`는 `join`과 같은 `room:{id}:lock`을 잡아야** 가드가 신선한 `status`를 보장받는다. 본 슬라이스에선 피해가 없지만(라운드 부재), **라운드 엔진이 "시작 시점 참가자 명단 스냅샷"을 잡는 순간 이 창문은 실제 버그**가 되므로 선제적으로 닫는다.

**`end`는 락이 본질적으로 불필요하지만 대칭으로 유지한다.** `PLAYING→ACTIVE`는 방을 *여는* 방향이라 racing하는 `join`은 PLAYING을 보면 거부(재시도)·ACTIVE를 보면 입장 — 어느 쪽도 무해하다. 그럼에도 "모든 방 상태 변경은 `room:{id}:lock`에서 직렬화된다"는 단일 멘탈모델을 위해 `join`/`leave`/`start`와 동형으로 락을 유지한다(비용 ≈ 0, start/end는 드물게 발생).

## Decision 2 — 중간 입장 금지의 집행점은 `Room.join()` 가드 한 곳 (★ 핵심)

가장 비자명한 결정. "게임 중 입장 금지"를 어디서 막느냐에 세 후보가 있다:

| 후보 | 문제 |
|---|---|
| 방 목록(`get()`) 필터 | 이미 `ACTIVE`만 조회해 목록엔 안 뜨지만, **방 URL을 아는 사람은 STOMP CONNECT를 직접 시도** 가능 → 입장 차단이 아님 |
| 인터셉터에서 "PLAYING이면 CONNECT 거부" | **게임 중 끊긴 기존 멤버의 재접속까지 차단** → 네트워크 깜빡임에 영영 튕김(최악의 UX) |
| **`Room.join()` 도메인 가드** | 신규 입장만 막고 재접속은 통과. **채택** |

근거는 인터셉터의 세 갈래 경로다. 재접속은 `join()`을 **타지 않는다**:

```
preSend(CONNECT):
  existingSession = activeSessionManager.getSession(playerId)
  ├─ ① 세션 있음 & 같은 방   → cancelGracePeriod()        (재접속, join() 미호출)
  ├─ ② 세션 있음 & 다른 방   → leave(old) + join(new)      (방 이동, join() 호출)
  └─ ③ 세션 없음
        ├─ cancelGracePeriod()==true  → (아무것도 안 함)   (유예 중 재접속, join() 미호출)
        └─ cancelGracePeriod()==false → join(new) ★        (진짜 신규 입장, join() 호출)
```

`③`의 `true`는 disconnect 시 `RoomDisconnectListener`가 `removeSession()`(세션 삭제) 후 `scheduleLeave()`(유예 예약)를 호출해 만들어지는 "끊김 직후 유예 시간 내" 상태다 — 세션은 없지만 멤버십은 60초(`app.room.reconnect-grace-period-seconds`) 더 유지된다. 이 구간의 재접속은 이미 멤버이므로 `join()`을 부르지 않는다.

따라서 가드를 `join()` 안에 두면 `②`·`③★`(신규 입장)만 막히고 `①`·`③`(재접속)은 자동으로 통과한다. **인터셉터는 무변경.**

```kotlin
// Room.join() — 기존 검사 앞에 한 줄
fun join(playerId: Long) {
    if (status == RoomStatus.PLAYING) throw ConflictException("게임 중에는 입장할 수 없습니다.")
    if (entries.size >= maxEntriesCount) throw ConflictException("방의 정원이 초과되었습니다.")
    if (playerIds.contains(playerId)) throw ConflictException("이미 방에 입장한 플레이어입니다.")
    ...
}
```

## Decision 3 — 트리거는 REST가 아니라 STOMP `@MessageMapping`

방장은 이미 STOMP로 방에 연결돼 있고, 시작/종료는 그 방의 실시간 상호작용이다. REST `POST`로 받으면 별도 인증·`roomId` 검증을 다시 해야 하지만, STOMP 매핑은 `leave`/`chat`와 똑같이 세션 속성에서 `playerId`·`roomId`를 얻는다.

```kotlin
// RoomStompController.kt — leave와 동형
@MessageMapping("/rooms/start")
fun start(headerAccessor: SimpMessageHeaderAccessor) {
    val session = headerAccessor.roomSession() ?: return
    roomService.start(session.roomId, session.playerId)
}
```

권한(방장 여부)은 도메인 `Room.start`가 판정하므로 컨트롤러는 세션 식별만 한다. `chat`처럼 컨트롤러가 직접 Redis에 발행하지 않고, **도메인 이벤트 경로**(Decision 4)를 쓴다 — 상태 변경 트랜잭션 커밋과 전파를 묶기 위함.

## Decision 4 — 전파는 기존 도메인 이벤트 → Redis pub/sub → STOMP 레일 재사용

입퇴장과 동일한 ephemeral broadcast 경로를 그대로 쓴다. 새 채널·새 인프라 없음.

```
Room.start() → GameStartedEvent
   └─[AFTER_COMMIT]→ RoomEventListener.handleGameStarted
        └─ Redis SEND room:{id}:events  (GameStartedEventMessage, type=STARTED)
             └─ RoomEventRedisSubscriber → STOMP /topic/rooms/{id}  (모든 참가자)
```

- 이벤트 클래스: `GameStartedEvent(roomId, playerId)`, `GameEndedEvent(roomId, playerId)` — `room/application/domain/`에 위치(`back/CLAUDE.md` 직렬화 안정성 규칙). 단, 이들은 `@TransactionalEventListener(AFTER_COMMIT)` ephemeral 경로라 Modulith `event_publication` outbox에 적재되지 않으므로 deserialization 부팅 리스크는 없다(채팅·입퇴장과 동일 등급)
- 메시지 봉투: `RoomEventMessage`의 `@JsonSubTypes`에 `STARTED`/`ENDED` 추가. `GameStartedEventMessage(roomId, playerId, nickname)`의 `playerId`·`nickname`은 **시작/종료를 누른 방장**(인터페이스 계약상 세 필드 필수 — 입퇴장과 동형)

`RoomEventListener.handleGameStarted`는 `handleRoomJoined`를 그대로 본떠 `playerService.findById`로 닉네임을 채워 발행한다.

## Decision 5 — 슬라이스를 닫기 위한 최소 종료 전이 포함

`start`만 있으면 방이 `PLAYING`에 갇혀 영영 목록·입장에서 사라진다(one-way door). 슬라이스를 자체 완결·테스트 가능·되돌림 가능하게 만들기 위해 방장 트리거 `end`(PLAYING→ACTIVE)를 포함한다. 게임 콘텐츠가 없으므로 start/end는 대칭적인 상태 토글이며, 데모는 "시작 → 게임중 화면 → 종료 → 로비 복귀"로 완결된다.

후속 라운드 엔진이 들어오면 종료는 **서버 주도(게임 오버 시 자동)**로 바뀌고, 본 수동 `end`는 "방장 강제 종료"로 남길 수 있다.

## Decision 6 — (b) 방 이동 실패 시 A방 이탈은 현행 유지

`②` 경로(다른 방으로 이동)에서 `leave(A)`와 `join(B)`는 각각 독립 락 + `REQUIRES_NEW`로 **개별 커밋**된다. `join(B)`가 가드(PLAYING)·정원·비번 등으로 throw하면 `leave(A)`는 이미 커밋돼 롤백되지 않아 플레이어가 어느 방에도 없게 된다.

이 orphaning은 본 변경 이전부터 존재(B 정원 초과 등)했고, 탐색에서 **"이동 시도 = A를 떠나겠다는 약속"으로 간주해 현행 유지**하기로 확정했다. 따라서 `leave`/`join` 순서 변경이나 보상 로직을 **추가하지 않는다**(코드 변경 0).

- 프론트는 CONNECT(B) 실패를 받으면 "게임 중인 방엔 입장할 수 없습니다" 토스트 + **방 목록**으로 이동(A로 되돌리지 않음 — 이미 A에서 나갔으므로)
- 잔여 아티팩트: 이 경로는 인터셉터가 `setSession(B)`에 도달하기 전 throw하므로 Redis 세션이 잠시 A를 가리킨 채 남지만, 다음 CONNECT 때 `leave(A)`가 멱등(`removeIf` false, 이벤트 없음)하게 자기 치유된다. 본 슬라이스에서 추가 처리 불필요(구현 메모)

## Decision 7 — 게임 화면 복원을 위해 `RoomDetailResponse.status` 노출

게임 중 새로고침하거나 유예 시간 내 재접속하면 프론트는 방 초기 로드를 `fetchRoomDetail`로 한다. 현재 `RoomDetailResponse`에는 상태가 없어 "지금 게임 중인지"를 알 수 없다 → 게임 화면을 못 복원한다. `RoomDetailResponse`에 `status: RoomStatus`를 추가해 클라이언트가 초기 렌더에서 올바른 화면(로비 vs 게임중)을 고르게 한다.

라이브 전이는 STOMP `STARTED`/`ENDED` 이벤트로, 초기/복원 상태는 `GET /rooms/{id}`의 `status`로 — 두 경로가 같은 상태를 공급한다.

## Open Questions (본 슬라이스에서 확정 불필요, 라운드 엔진에서 결정)

- **최소 시작 인원**: 방장 혼자(=1명)도 시작 가능하게 둘지, `MIN_PLAYERS_TO_START` ≥ 2를 강제할지. 노래 맞히기 특성상 ≥2가 자연스럽지만 테스트·연습 편의로 본 슬라이스는 **제한 없음(≥1)** 기본값으로 두고, 라운드 엔진에서 재검토
- **게임 중 방장 이탈**: 방장이 게임 중 나가면 새 방장(`sortedEntries`의 다음)이 종료 권한을 승계 — 기존 `master` getter가 자동 반영하므로 별도 로직 불필요하나, 라운드 엔진에서 "진행 중 방장 교체" UX를 다시 본다
- **게임 상태 저장소**(MySQL vs Redis), **서버 타이머 권위**, **정답 매칭(ES Nori 재사용)** — 라운드 엔진의 핵심 결정. 본 슬라이스는 의존하지 않음
