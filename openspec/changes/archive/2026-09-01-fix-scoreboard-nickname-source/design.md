## Context

라운드 엔진(`add-game-round-engine`)은 점수판을 Redis ZSET `room:{id}:scores`에 **id → 점수**로만 두고, 이벤트·스냅샷에도 그대로 id만 실어 보낸다. 닉네임은 프론트가 방 멤버 목록과 조인해 붙이도록 `add-room-round-frontend`가 규정했다(`room-round-ui/spec.md:37`).

이 분업은 **점수판과 멤버 목록의 수명이 같다**는 암묵적 전제 위에 서 있는데, 실제로는 다르다. 멤버 목록은 `LEFT` 즉시 줄어들고, 점수판은 `ENDED` 이후에도 프론트가 sticky로 붙들고 있다(`ENDED` 메시지에 점수판이 실리지 않으므로 그래야만 한다). 이슈 #235는 그 간극이 화면에 드러난 것이다.

관련 사실 두 가지:

- 서버는 퇴장 시 점수판에서 그 플레이어를 지운다(`RoundService.onPlayerLeft` → `ZREM`). 즉 **살아있는 점수판**에는 퇴장자가 없다. 문제가 되는 것은 프론트가 얼려둔 **마지막 점수판 스냅샷**뿐이다.
- `Player` 행은 삭제되지 않는다(`PlayerRepository`에 삭제 경로 없음). 방을 떠나도 닉네임은 조회 가능하다.

## Goals / Non-Goals

**Goals**

- 결과 화면·정답 공개 화면이 **참가자의 입·퇴장과 무관하게** 실제 닉네임을 표시한다
- 점수판 스냅샷을 자기완결적으로 만든다 — 표시에 필요한 정보를 스냅샷 바깥에서 찾지 않는다
- 라운드 전이 핫패스(Lua CAS·sweeper·도메인 이벤트)를 건드리지 않는다

**Non-Goals**

- 퇴장자를 점수판에 되살리는 것 — 게임 중 퇴장 시 제거 정책(`room-game-session/spec.md:198`)은 그대로 둔다
- 퇴장 여부를 결과 화면에 표시하는 것 — 이름만 그대로 보인다
- 점수판 저장 구조 변경(ZSET에 닉네임을 함께 저장하는 등)

## Decision 1: 닉네임 해석 주체를 서버로 옮긴다

세 갈래를 놓고 비교했다.

| | A. 프론트 닉네임 캐시 | **B. 서버가 닉네임 전송(채택)** | C. Redis 로스터 |
|---|---|---|---|
| 방식 | id→닉네임 맵을 누적하고 `LEFT`에도 지우지 않음 | `ScoreEntryResponse`에 닉네임 포함 | `room:{id}:roster` 해시에 보관 후 조인 |
| 변경 범위 | 프론트 2파일 | 백 5파일 + 프론트 타입 | 백 다수 + Lua |
| 재접속·하이드레이션 | 캐시가 없는 새 클라이언트는 여전히 취약 | 항상 정확 | 항상 정확 |
| 비용 | 없음 | 공개 전이당 조회 1회(배치) | Redis 상태·동기화 부담 |
| 구조 | 클라이언트 메모에 의존 | 조인 자체가 사라짐 | 조인 지점만 서버로 이동 |

**B를 택한다.** A는 30분이면 끝나지만 "표시에 필요한 정보를 화면 바깥에서 찾는다"는 결함 구조를 그대로 두고 조인 대상만 바꾼다 — 같은 결함이 다른 경로(재접속 하이드레이션, 향후 관전·리플레이)로 재발할 여지가 남는다. C는 B와 정합성이 같으면서 Redis 상태와 닉네임 변경 동기화 부담을 새로 만든다. 조회 비용(공개 전이당 `findByIdIn` 1회, 방당 최대 참가자 수)은 라운드 간격(최소 5초 REVEAL) 대비 무시할 수준이라 C가 최적화할 대상이 아니다.

B의 부수 효과가 채택 근거를 보강한다: **프론트에서 `players`를 쓰던 자리가 전부 사라진다.** `RoundPanel`·`RoundRevealOverlay`·`RoundResultOverlay` 셋 다 `players`를 오직 이름 붙이기에만 쓰고 있었으므로, prop 자체가 제거된다.

## Decision 2: 해석은 DTO를 만드는 지점에서 하고, 조립기 하나로 모은다

점수판 DTO가 만들어지는 곳은 두 군데다.

```
 도메인 이벤트                 어댑터/서비스                     전송 DTO
 RoundRevealedEvent  ─────▶  RoomEventListener (in)      ─▶  RoundRevealedEventMessage
 (scores: id·점수)            handleRoundRevealed              (scores: id·닉네임·점수)
                                     │
                                     └─▶ RoundScoreboardAssembler ◀─┐
                                          playerService.findByIdIn   │
                                                                     │
 RoundStateStore.snapshot ─▶  RoundService.getSnapshot (application) ┘
                                                              ─▶  RoundSnapshotResponse
```

**도메인 이벤트 `RoundRevealedEvent`는 그대로 둔다.** 닉네임은 `room` 애그리거트가 알지 못하는 표현 관심사이고, 인바운드 어댑터가 `playerService`로 닉네임을 붙이는 것은 `handleRoomJoined`·`handleRoomLeft`·`handleGameStarted`가 이미 쓰는 기존 패턴이다. 이 선택으로 `back/CLAUDE.md`의 이벤트 직렬화 규칙(미완료 publication 호환)을 신경 쓸 일도 없어진다 — 도메인 이벤트 스키마가 그대로이기 때문이다.

두 지점이 같은 3줄 매핑을 복제하지 않도록 `room/application/RoundScoreboardAssembler.kt`(`@Component`)로 모은다. 계약:

```kotlin
fun assemble(scores: List<ScoreEntry>, winnerId: Long?): Scoreboard
// Scoreboard(entries: List<ScoreEntryResponse>, winnerNickname: String?)
```

`player` 모듈의 `PlayerService`에 의존하는데, 이는 `RoomService`(`application`)가 멤버 닉네임 해석에 이미 쓰고 있는 기존 의존 방향이라 새로운 모듈 결합을 만들지 않는다.

## Decision 3: `winnerNickname`을 별도 필드로 싣는다

승자 이름은 점수판에서 역참조하면 될 것처럼 보이지만, **점수판에 행이 없는 승자**가 성립한다.

```lua
-- RoundStateStoreImpl.kt:235-241 (ADVANCE_ON_CORRECT_SCRIPT)
if redis.call('ZSCORE', KEYS[3], ARGV[3]) then   -- 아직 멤버일 때만
    redis.call('ZINCRBY', KEYS[3], 1, ARGV[3])   -- 가점
end
redis.call('HSET', KEYS[1], ..., 'winnerId', ARGV[3])  -- winnerId는 무조건 기록
```

정답 제출과 CAS 사이에 그 플레이어의 퇴장이 처리되면 `winnerId`는 있는데 점수 행은 없다. 드문 경합이지만, 역참조 방식은 정확히 이 경우에 다시 이름 없는 승자를 만든다 — 고치려는 결함과 같은 종류다. 서버가 `winnerId`를 해석해 함께 실으면 이 구멍이 닫힌다. 조립기가 `winnerId`를 조회 id 집합에 포함하므로 **추가 조회는 발생하지 않는다.**

`ROUND_REVEALED`와 라운드 스냅샷 양쪽에 넣는다. 스냅샷에 빠뜨리면 `REVEAL` 중 재접속한 멤버만 승자 이름을 못 보는 불균등이 생긴다.

## Decision 4: 해석 실패는 예외가 아니라 중립 라벨로 degrade 한다

`PlayerService.findById`는 없으면 `NotFoundException`을 던진다. 이 경로에서 예외가 나면 **그 라운드의 공개 방송 자체가 죽는다** — 이름 하나 때문에 게임이 멈추는 것은 균형이 맞지 않는다. 따라서 조립기는 `findByIdIn`으로 배치 조회한 뒤 맵에 없는 id는 `알 수 없음`으로 채운다.

`Player` 행이 삭제되는 경로가 없어 실제로 발생하기 어렵지만, 폴백이 있으면 배포 순서 경합도 함께 흡수된다: 프론트도 `nickname`이 비어 있을 때 같은 라벨로 degrade 하므로, 신버전 프론트가 구버전 백엔드를 잠시 만나도 화면이 빈칸으로 깨지지 않는다.

`(퇴장)`이라는 라벨은 쓰지 않는다. 퇴장 여부는 이 자리에서 알 수 없는 정보이고, 그렇게 단정한 것이 이슈 #235의 원인이었다.

## Decision 5: 프론트는 조인을 제거하고 정렬만 남긴다

```
 변경 전                                    변경 후
 joinScores(scores, players)  ← 삭제        rankScores(scores)
   nicknameById 조인                          점수 내림차순 정렬만
   "(퇴장)" 폴백                              (동점 시 원래 순서 유지는 그대로)
 rankScores(scores, players)
```

`ScoreEntry`(`front/app/utils/RoundEvent.ts`)에 `nickname`을, `RoundRevealedEvent`·`RoundSnapshotResponse`에 `winnerNickname`을 추가한다. `roundReducer`는 `winnerNickname`을 `scores`와 함께 sticky로 보존해야 한다 — 결과 화면이 아니라 정답 공개 화면이 쓰는 값이지만, `winnerId`와 수명을 맞춰두지 않으면 같은 종류의 불일치가 다시 생긴다.

## Risks / Trade-offs

- **공개 전이당 DB 조회 1회 추가** — `findByIdIn`(읽기 전용 트랜잭션), 방 참가자 수 규모. 라운드 간격이 최소 5초(REVEAL)이므로 부하 관점에서 무시할 수준이다. 캐시를 붙이지 않는다(닉네임 변경이 즉시 반영되지 않는 새 문제를 만들 이유가 없다)
- **닉네임은 발행 시점 기준** — 게임 도중 닉네임을 바꾸면 그 이후 라운드부터 새 이름으로 보인다. 결과 화면은 마지막 공개 시점의 이름을 유지한다. 허용 가능한 동작으로 본다
- **페이로드 증가** — 점수판 항목당 문자열 하나. 무시할 수준
- **배포 순서** — 백엔드가 먼저 배포되는 것이 이상적이나, 양방향 모두 폴백이 있어 순서가 어긋나도 화면이 깨지지 않는다(Decision 4)
- **남는 비대칭** — 마지막 `REVEAL` 이전에 떠난 참가자는 결과에서 사라지고, 이후에 떠난 참가자는 남는다. 본 변경의 범위 밖이며(제거 정책 유지), 이름을 잃는 문제와는 별개다
