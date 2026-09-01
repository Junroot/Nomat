## Why

이슈 [#235](https://github.com/Junroot/Nomat/issues/235) — 게임 종료 결과 화면에서 **1위(14점)의 닉네임이 `(퇴장)`으로 표기됐다.** 실제로 게임을 끝까지 하고 우승한 사람인데, 결과 화면이 뜬 뒤 방을 떠났다는 이유로 이름을 잃었다.

원인은 표기 버그가 아니라 **점수판 신원을 해석하는 방식**에 있다. 서버는 점수판을 `playerId`와 점수만으로 내려보내고, 닉네임은 프론트가 **살아있는 방 멤버 목록(`players`)과 조인해서** 붙인다.

```
front/app/utils/scoreboard.ts:15
    nickname: nicknameById.get(s.playerId) ?? "(퇴장)"
```

문제는 조인하는 두 데이터의 **수명이 다르다**는 것이다.

```
              게임 시작        마지막 REVEAL        ENDED        A가 방을 나감
                 │                  │                │                │
 round.scores    ├──────────────────┼────────────────┼────────────────┤  sticky 보존
 (roundReducer)  │   [A:14, B:4]    │  [A:14, B:4]   │  [A:14, B:4]   │  (ENDED엔 점수판이 없다)
                 │                  │                │                │
 players         ├──────────────────┼────────────────┼───────✂────────┤  LEFT 수신 즉시 제거
 (useRoomSub)    │     [A, B]       │     [A, B]     │      [B]       │
                 │                  │                │                ▼
                                                            조인 실패 → "(퇴장)"
```

`useRoomSubscription.ts:120`이 `LEFT`를 받아 A를 목록에서 지우는 순간, 이미 얼어붙은 결과 점수판의 A 행은 이름을 잃는다. 게임이 끝난 뒤에는 새 `ROUND_REVEALED`가 오지 않으므로 영구히 `(퇴장)`으로 남는다.

같은 뿌리에서 나오는 형제 증상이 하나 더 있다 — `RoundRevealOverlay.tsx:26`. 정답자가 공개 구간 직후 나가면 **승자 이름도 `(퇴장)`** 이 된다.

이 동작은 우발적 결함이 아니라 **명시된 스펙**이라 스펙 정정이 함께 필요하다:

```
openspec/specs/room-round-ui/spec.md:37
  "점수판은 id만 담고 있으므로 방의 players와 조인해 닉네임으로 표기하고,
   미매칭 id는 퇴장으로 폴백한다."
```

## What Changes

**누가 득점했는가는 서버가 소유한 게임 사실인데, 지금은 클라이언트가 휘발성 멤버십 목록으로 되짚어 추론하고 있다.** 신원 해석을 서버로 옮겨 점수판 스냅샷이 자기완결적이 되게 한다.

```
                        ┌──────────────── 백엔드 ────────────────┐
  Redis ZSET             RoundStateStore          닉네임 해석 지점
  room:{id}:scores  ──▶  scoreboard()  ──▶  ┌─ RoomEventListener.handleRoundRevealed (in)
  (id → 점수)            snapshot()          │    └▶ ROUND_REVEALED { scores:[{playerId,nickname,score}],
                                             │                        winnerId, winnerNickname }
                                             └─ RoundService.getSnapshot (application)
                                                  └▶ GET /rooms/{id}.round { scores:[…nickname], winnerNickname }
                        └───────────────────────────────────────┘
                                             │
                        ┌──────────────── 프론트 ────────────────┐
                        roundReducer.scores  ← 닉네임 포함, sticky 보존
                              │
                        rankScores(scores)   ← players 인자 삭제, joinScores 제거
                              │
                   RoundPanel · RevealOverlay · ResultOverlay  ← players prop 삭제
                        └───────────────────────────────────────┘
```

### 1. 점수판 항목이 닉네임을 싣는다 (`back/`)

`ScoreEntryResponse`가 `playerId`·`score`에 더해 `nickname`을 담는다. 해석은 방 멤버십이 아니라 **`Player` 저장소**를 조회해 하므로, 이미 방을 떠난 참가자도 이름이 나온다. 해석 지점은 이 DTO를 만드는 두 곳(`RoomEventListener.handleRoundRevealed`, `RoundService.getSnapshot`)이며, 중복을 막기 위해 공용 조립기 하나로 모은다(`design.md` Decision 2).

**도메인 이벤트 `RoundRevealedEvent`는 건드리지 않는다.** 닉네임은 도메인 사실이 아니라 표현 관심사이고, 인바운드 어댑터에서 붙이는 것이 `JOINED`·`LEFT`·`STARTED`가 이미 쓰는 방식이다. 덕분에 이벤트 직렬화 호환 문제도 발생하지 않는다.

### 2. 승자 닉네임을 별도로 싣는다 (`back/`)

`ROUND_REVEALED`와 라운드 스냅샷에 `winnerNickname`(nullable)을 추가한다. 승자 이름을 점수판에서 역참조할 수 없기 때문이다 — `ADVANCE_ON_CORRECT_SCRIPT`는 `ZSCORE`가 없으면 가점을 건너뛰지만 `winnerId`는 그대로 기록하므로(`RoundStateStoreImpl.kt:235-241`), **점수판에 행이 없는 승자**가 성립한다.

### 3. 프론트에서 조인을 제거한다 (`front/`)

`joinScores`를 삭제하고 `rankScores(scores)`가 정렬만 담당한다. `RoundPanel`·`RoundRevealOverlay`·`RoundResultOverlay` 세 컴포넌트에서 `players` prop이 사라진다 — 확인 결과 이 셋에서 `players`는 **오직 점수판 이름 붙이기에만** 쓰이고 있었다.

### 4. 표기 정책 — 이름 그대로

퇴장 여부와 무관하게 실제 닉네임만 표시한다. 결과 화면은 게임이 끝난 시점의 기록이므로 그 이후의 입·퇴장으로 내용이 달라지지 않는다.

## Impact

- **영향 스펙**
  - `room-round-ui` (MODIFIED ×2) — `ROUND_REVEALED` 표시 요구사항에서 조인·`(퇴장)` 폴백 규정을 제거, 최종 결과 화면이 현재 멤버 목록에 의존하지 않음을 명시
  - `room-game-session` (MODIFIED ×2, ADDED ×1) — `ROUND_REVEALED`·라운드 스냅샷의 점수판 항목 계약 변경, 닉네임 해석 주체·폴백 규칙 신규
- **영향 코드 (`back/`)** — `room` 모듈, 헥사고날 계층별
  - `application/dto/RoundSnapshotResponse.kt` — `ScoreEntryResponse`에 `nickname` 추가, `RoundSnapshotResponse`에 `winnerNickname` 추가
  - `application/dto/RoundRevealedEventMessage.kt` — `winnerNickname` 추가
  - `application/RoundService.kt` — `getSnapshot`이 조립기를 거쳐 닉네임을 채운다
  - `application/RoundScoreboardAssembler.kt` (신규) — 점수판 id → 닉네임 해석 단일 지점. `player` 모듈의 `PlayerService`에 의존(`RoomService`가 이미 쓰는 기존 방향)
  - `in/RoomEventListener.kt` — **인바운드 어댑터**. `RoundRevealedEventMessage`의 유일한 생성 지점
  - `application/domain/*`·`out/*` **무변경** — 도메인 이벤트·Redis 키·Lua CAS·점수판 저장 구조를 건드리지 않는다
- **영향 코드 (`front/`)**: `app/utils/RoundEvent.ts`, `app/utils/scoreboard.ts`, `app/hooks/roundReducer.ts`, `app/components/ui/RoundPanel.tsx`, `app/components/ui/RoundRevealOverlay.tsx`, `app/components/ui/RoundResultOverlay.tsx`, `app/routes/RoomView.tsx`
- **DB·Redis·Kafka·ES**: 영향 없음. 스키마 마이그레이션 없음, Redis 키(`room:{id}:scores`) 구조·Lua 스크립트 무변경
- **실시간 프로토콜**: `ROUND_REVEALED`·`GET /rooms/{id}.round`에 필드가 **추가**된다(제거·개명 없음). 구버전 프론트는 모르는 필드를 무시하므로 배포 순서 경합에서도 깨지지 않고, 신버전 프론트는 `nickname` 부재 시 폴백 라벨로 degrade 한다(`design.md` Decision 4)
- **범위 밖**: "게임 중 퇴장한 플레이어는 점수판에서 제거된다"(`room-game-session/spec.md:198`)는 유지한다. 따라서 **마지막 `REVEAL` 이전에** 떠난 참가자는 여전히 결과에서 사라지고, **그 이후에** 떠난 참가자는 이름과 함께 남는다. 이는 결과 화면이 "게임 종료 시점의 스냅샷"이라는 의미론에서 나오는 자연스러운 귀결이며, 본 변경은 그 스냅샷이 **자기 이름을 잃지 않게** 하는 데 한정한다
