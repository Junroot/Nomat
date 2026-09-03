# Design — add-round-pass-signal

## Context

`add-game-round-engine`이 깔아둔 라운드 엔진은 `OPEN`을 끝내는 트리거를 둘만 갖고 있다.

```
                     OPEN 종료 조건 (현재)

  누군가 안다   ──▶  첫 정답 채팅  ──▶  즉시 REVEAL      반응 지연 ≈ 0초
  아무도 모른다 ──▶  ( 비어 있음 ) ──▶  마감까지 대기     반응 지연 = 클립 전체
                          ▲
                          └── 본 변경이 채우는 칸
```

`RoundService.openDurationMillis`가 `(endTimeSec - startTimeSec) × repeatCount + 2s`라, 15초 클립 × 3회면 47초다. 아무도 모르는 곡이면 그 47초가 통째로 死시간이 된다.

본 설계는 탐색 대화에서 사용자가 직접 확정한 결정들을 반영한다. 재사용하는 기존 인프라는 다음과 같다.

- **전이 게이트**: `round:{shard}:<roomId>` Hash의 `(roundSeq, phase)` 단일 원자 Lua CAS (`RoundStateStoreImpl`). 멱등성은 분산 락이 아니라 여기에 있다.
- **단일 시계**: 모든 시각 앵커·비교는 `redis.call('TIME')`.
- **키 배치**: `RoundRedisKeys`의 동일 hash tag `{shard}` 규약 — 클러스터 `CROSSSLOT` 회피.
- **실시간 fan-out**: 도메인 이벤트 → `room:{id}:events` Redis pub/sub → 각 replica `RoomEventRedisSubscriber` → `/topic/rooms/{id}` STOMP.
- **라이브 로스터**: `scores:{shard}:<roomId>` ZSET. 게임 시작 시 전원 0점으로 초기화되고(`START_SCRIPT`) 퇴장 시 제거된다(`removeScore`). 게임 중 신규 입장은 `Room.join`이 막으므로 **단조 감소만** 한다.

## Goals / Non-Goals

**Goals**

- `OPEN`의 세 번째 종료 경로 — 남은 인원의 2/3 이상이 포기하면 즉시 `REVEAL`
- 기존 두 경로(첫 정답·클립 소진)의 동작·타이밍 **무변경**
- 다중 인스턴스에서 정확히 한 번 전이 — 기존 CAS 게이트 재사용, 새 동기화 원시 0
- 퇴장으로 로스터가 줄었을 때의 임계 재평가
- 채팅 입력 리듬을 깨지 않는 조작 — 데스크톱 키보드 주 경로
- 신규 외부 의존성·DB 마이그레이션·인프라 변경 0

**Non-Goals**

- **라운드 제한시간을 클립 길이에서 분리하는 것** — `openDurationMillis` 공식은 그대로 둔다. 상수 튜닝은 상호작용이 아니라 게임 밸런스 변경이고, 기존 플레이리스트의 체감을 소리 없이 바꾼다. 별도 change로 판단한다.
- **힌트 진행** — 死시간을 없애는 대신 채우는 방향. 마감 인덱스가 "방당 마감 1개"를 전제하므로(`ZADD deadlines <deadlineAt> <roomId>`) 라운드 중간 이벤트를 넣으려면 인덱스 구조 자체를 손봐야 한다.
- **다중 정답 인정·차등 점수** — "첫 정답자만 1점"은 그대로.
- **포기 통계·메트릭** — 커스텀 비즈니스 메트릭은 `application-metrics`가 명시적으로 범위 밖에 두고 있다.
- **방장 강제 스킵** — 파티 게임에서 한 명이 남의 기회를 끊는 권한 모델은 채택하지 않는다.

## 상태 머신

포기는 새 phase를 만들지 않는다. `OPEN` 안의 부수 상태이고, 임계를 넘는 순간에만 기존 `OPEN→REVEAL` 전이를 발화시킨다.

```
                     ┌──────── 첫 정답 (마감 이내) ────────┐
                     │                                    │
                     │  ┌──── 포기 2/3 도달 ─────────────┐ │
                     │  │      (신규)                    │ │
   start() ───▶  ┌───┴──┴─┐                        ┌────▼─▼───┐
                 │  OPEN  │ ───── 마감 도달 ──────▶ │  REVEAL  │
                 └────────┘                        └────┬─────┘
                    │  ▲                                │ 5s
       포기 토글 ────┘  └────────── 다음 라운드 ──────────┘
    (전이 없이 카운트만 갱신)
```

전이 트리거는 이제 넷이고 전부 `RoundService`로 수렴한다.

```
                      ┌───────────────────────────────┐
  /app/rooms/chat ────┤                               │
  RoundDeadlineSweeper┤        RoundService           │──▶ (roundSeq, phase) CAS
  /app/rooms/pass ────┤   전이 트리거의 단일 수렴점       │       단일 원자 Lua
  onPlayerLeft ───────┤                               │
      (신규 2개)       └───────────────────────────────┘
```

## Decisions

### Decision 1 — 임계 도달 시 즉시 `REVEAL`로 전이한다 (마감 당기기가 아니라)

**결정**: 포기 임계를 넘으면 `OPEN→REVEAL` CAS를 즉시 수행한다. `winnerId = null`, `REVEAL` 지속은 기존과 동일한 5초.

**대안 — 마감을 `min(현재 마감, now + 5초)`로 당기기**: 새 전이 스크립트 없이 `HSET deadlineAt` + `ZADD`만으로 끝나고 이후는 기존 sweeper가 처리한다. "막 치려던 참" 억울함도 없앤다. 그러나 **`roundSeq`를 올리지 않는 상태 변경**이라는 새 범주를 프로토콜에 들인다. 이 시스템의 모든 순서 보증이 `roundSeq` 단조 증가에 걸려 있는데 거기에 예외가 생긴다.

**근거**: 전이 = `roundSeq` 증가라는 기존 모델을 깨지 않는 쪽을 택했다. 새 Lua 전이 스크립트 하나를 더 쓰는 비용이, 프론트 단조 가드 모델에 예외를 만드는 비용보다 싸다. "막 치려던 참" 문제는 Decision 4(포기의 대가)가 대신 흡수한다 — 아직 고민 중인 사람은 애초에 누르지 않기 때문이다.

### Decision 2 — 임계는 남은 인원의 2/3, 판정은 정수 곱셈으로

**결정**: `passed * 3 >= n * 2`. `n = ZCARD scores`(라이브 로스터).

나눗셈·`ceil`·부동소수를 쓰지 않는다. Lua에서 정수 비교 한 줄이고 반올림 경계 논쟁이 없다.

| 남은 인원 | 임계 | 버틸 수 있는 AFK | (참고) 엄격 과반 |
|---:|---:|---:|---:|
| 1 | 1 | 0 | 1 |
| 2 | 2 | 0 | 2 |
| 3 | 2 | 1 | 2 |
| 4 | 3 | 1 | 3 |
| 5 | 4 | 1 | 3 |
| 8 | 6 | 2 | 5 |
| 20 | 14 | 6 | 11 |

**대안 — 전원 만장일치**: 규칙이 가장 명확하고 "내 기회를 뺏겼다"가 원천적으로 없다. 그러나 `maxEntriesCount`가 최대 20인데, **연결은 살아 있고 자리만 비운 참가자를 서버는 구분할 수 없다.** 20인 방에서 AFK 한 명이 방 전체를 인질로 잡는다.

**대안 — 엄격 과반 `⌊n/2⌋+1`**: AFK 내성이 가장 크지만 5인 방에서 3명이면 넘어가 소수의 기회를 자주 뺏는다.

**근거**: 2/3는 작은 방(2~4인)에서 사실상 만장일치로 수렴해 기회 박탈을 막고, 큰 방에서만 AFK 인질 상황을 푼다. 이 게임의 주 무대가 3~8인 방이라는 전제에서 균형점이다.

**경계 — `n = 1`**: `1*3 >= 1*2`가 참이라 혼자 남은 참가자는 즉시 넘어간다. 의도한 동작이다(혼자면 혼자 결정한다). 테스트로 못 박는다.

### Decision 3 — 포기는 토글이다

**결정**: 같은 목적지가 켜고 끄기를 겸한다. Lua 안에서 `SISMEMBER`로 방향을 판단해 `SADD`/`SREM`.

**대안 — 취소 불가(라운드당 1회 일방향)**: 규칙이 단순하고 카운트가 단조 증가라 다른 참가자 화면이 흔들리지 않는다.

**근거**: 취소 불가를 선호하게 만들던 근거가 두 번 뒤집혔다.

1. Decision 1에서 즉시 `REVEAL`을 택해 **되돌릴 마감이 없다.** 취소는 `SREM` + 카운트 감소가 전부다.
2. 조작이 키보드 단축키로 옮겨가 **오발 확률이 올라갔다.** 동시에 Decision 4가 포기에 실질적 대가를 붙였으므로 오발 비용도 커졌다.

즉 취소 비용은 낮아지고 오발 위험은 높아져 균형이 이동했다. 정답 권리도 `SISMEMBER` 체크뿐이라 취소 시 자동 복원된다.

**동시성**: 각 토글은 원자 Lua 1회 실행이고 임계 판정을 매 연산마다 수행하므로, A의 취소와 B의 추가가 겹쳐도 최종 상태는 일관된다. 임계를 넘어 이미 `REVEAL`로 간 뒤의 토글은 `phase` 게이트가 막는다.

### Decision 4 — 포기 중인 참가자는 그 라운드의 정답 판정에서 제외된다

**결정**: `RoundService.submitAnswer`가 `AnswerMatcher.matches` 앞에서 포기 상태를 확인해 포기 중이면 판정하지 않는다. 판정은 `SISMEMBER passes <playerId>` 단독이 아니라 **Decision 5의 유효성 계약을 함께 만족해야 한다** — `passSeq == roundSeq`이면서 `SISMEMBER`가 참일 때만 포기 상태다. 둘을 하나의 원자 연산으로 읽는다. 채팅 원문 방송(`RoomStompController`가 별도로 수행)은 영향을 받지 않는다.

**대안 — 대가 없음**: 규칙이 가장 단순하고 실수로 누른 것이 치명적이지 않다.

**근거**: 누르는 데 대가가 없으면 **"라운드 시작하자마자 일단 누르고 계속 추측"이 손해 없는 지배 전략**이 된다. 2/3만 그렇게 행동하면 매 라운드가 조기 종료돼, 원래 불만("다들 아무 말 없이 기다린다")을 정확히 반대편 불만("곡이 나오다 말고 끊긴다")으로 바꿔놓는다. 대가가 붙으면 "정말 모르겠는 사람만 누른다"가 성립하고 임계가 의미를 되찾는다.

채팅 방송을 유지하는 것이 핵심이다 — 포기한 사람도 잡담과 반응은 계속하므로, 이 대가는 게임에서 빠지는 것이 아니라 그 라운드의 채점에서만 빠지는 것이다.

**이 결정이 새로 여는 경로 하나를 명시적으로 수용한다** — 포기자가 정답 문자열을 치면 라운드가 `OPEN`인 채로 원문이 전원에게 방송된다. 아래 Risks의 「포기자의 정답 채팅이 `OPEN` 중 방송되어 정답이 유출될 수 있다」에서 수용 근거와 기각한 대안을 함께 적었다.

### Decision 5 — 포기 집합은 `passSeq` lazy reset으로 정리한다

**결정**: `round` Hash에 `passSeq` 필드를 추가하고, 포기·퇴장 스크립트가 진입 시 다음을 수행한다.

```lua
-- 유효 포기 수를 읽는 공통 전제
local passSeq = redis.call('HGET', roundKey, 'passSeq')
if passSeq ~= tostring(seq) then
    redis.call('DEL', passesKey)                       -- 이전 라운드 잔재를 지연 폐기
    redis.call('HSET', roundKey, 'passSeq', seq)
end
```

**계약 — `passes`는 `passSeq == roundSeq`일 때만 유효하다.** lazy reset은 라운드 경계에서 키를 비우지 않으므로, `passSeq != roundSeq`인 `passes`는 이전 라운드의 잔재이며 **포기자 0명과 동일하게 취급해야 한다.** 이 판정은 쓰기 경로뿐 아니라 **모든 읽기 경로**에 예외 없이 적용된다.

**이 계약의 적용 범위는 한 게임 안의 라운드 경계뿐이다.** `roundSeq`는 방 안에서 단조가 아니다 — 게임이 끝난 뒤 같은 방이 다시 시작하면 `START_SCRIPT`가 라운드 Hash를 `DEL` 없이 덮어써 `roundSeq`를 **다시 1로** 되돌리고, 그 8개 필드 밖의 `passSeq`와 `passes` SET은 손대지 않는다. 따라서 게임 경계를 넘으면 "`passSeq != roundSeq` = 잔재"라는 판별식 자체가 성립하지 않는다(이전 게임 1라운드의 `passSeq = 1`이 새 게임 1라운드에서 "유효"로 읽힌다). 정리 책임을 2단으로 나눈다.

| 경계 | 담당 | 근거 |
|---|---|---|
| **라운드 경계** (같은 게임 안, `roundSeq` 단조 증가) | lazy reset (`passSeq != roundSeq` → `DEL passes`) | 전이 스크립트를 건드리지 않고 진입 시점에 스스로 감지 가능 |
| **게임 경계** (`roundSeq`가 1로 되돌아감) | `START_SCRIPT`의 명시적 삭제 (Decision 5-1) | lazy reset이 **원리적으로 감지할 수 없는** 지점 |

| 읽기 경로 | 유효성 판정 |
|---|---|
| 포기 토글 스크립트 | 진입 시 위 조각으로 reset 후 읽는다 |
| 퇴장 재평가 스크립트 | 진입 시 위 조각으로 reset 후 읽는다 |
| **정답 판정 게이트(Decision 4)** | `passSeq != roundSeq`면 포기 상태가 아닌 것으로 본다 |
| 스냅샷 조회 | `passSeq != roundSeq`면 `passedCount = 0`, `passing = false` |

이 조건이 빠진 읽기 경로가 하나라도 생기면 침묵 실패가 난다 — 라운드 3에서 포기한 참가자가 라운드 4 이후에도 계속 정답 판정에서 제외되는데, 프론트는 `ROUND_STARTED`에서 포기 표시를 초기화하므로 본인 화면에는 아무 흔적이 없다. 유효성 판정과 `passes` 읽기는 **같은 원자 연산 안에서** 수행한다(별도 왕복으로 나누면 그 사이에 라운드가 전이될 수 있다).

**대안 — 라운드 전이 스크립트에도 `DEL passes` 추가**: `ADVANCE_ON_DEADLINE_SCRIPT`·`ADVANCE_ON_CORRECT_SCRIPT` 각각에 한 줄. 직관적이지만 **매 라운드 전이가 지나가는, 이미 검증된 경로 두 곳을 더 건드린다.**

**대안 — 키에 seq를 넣기(`passes:{shard}:<roomId>:<seq>`)**: 청소가 아예 불필요해지지만 라운드 수만큼 키가 생기고, 클러스터에서 패턴 삭제가 곤란해 teardown이 지저분해진다.

**근거**: lazy reset은 **라운드 전이 스크립트를 한 줄도 수정하지 않고** 라운드 경계를 처리한다 — `ADVANCE_ON_DEADLINE_SCRIPT`·`ADVANCE_ON_CORRECT_SCRIPT`는 무수정으로 남는다. 키는 방당 하나로 고정되어 `TEARDOWN_SCRIPT`에 `DEL passes` 한 줄만 추가하면 정리가 끝난다. 게임 경계만은 lazy reset의 판별식이 성립하지 않으므로 `START_SCRIPT`에서 명시적으로 지운다(Decision 5-1). 라운드 상태의 GC 백스톱 TTL(24h)도 기존 키와 같은 정책을 그대로 적용한다.

### Decision 5-1 — 게임 경계의 포기 상태 정리는 `START_SCRIPT`에서 명시적으로 한다

**결정**: `START_SCRIPT`에 두 줄을 추가한다 — `DEL <passes key>`와 `HDEL <round hash> passSeq`. 기존 `HSET roundSeq 1 …`과 **같은 원자 실행 단위 안에서** 수행하므로, 게임 시작 시점에 포기 상태가 확정적으로 비어 있음이 보장된다. `passes` 키가 KEYS에 추가된다.

**무엇을 막는가**: 게임이 자연 종료(`ENDED`)되는 경로에는 teardown 호출이 없다 — 라운드 Hash와 `passes`가 24h TTL로 살아남는다. 그 상태로 같은 방이 다시 시작하면 `roundSeq`가 1로 되돌아가므로, 이전 게임 1라운드의 포기가 새 게임 1라운드로 **유효한 상태인 채** 이월된다. 그러면 (a) 그 참가자는 새 곡을 듣기도 전에 정답 판정에서 제외되고(Decision 4), (b) 포기 버튼이 취소로 동작하며(Decision 3), (c) 임계가 0이 아닌 값에서 시작한다(Decision 2). 프론트는 `ROUND_STARTED`에서 포기 표시를 초기화하므로 본인 화면에는 아무 흔적이 없다 — 정확히 Decision 5가 경계한 침묵 실패 형태다.

**대안 — 자연 종료 경로에 teardown 추가**: 근본 정리라 매력적이지만 `advanceDueRoom`의 `ENDED` 분기 의미를 바꾼다. 지금 라운드 Hash는 `ENDED` 이후에도 TTL만 걸린 채 남고 `RoundService.getSnapshot`이 그것을 그대로 `phase = ENDED` 스냅샷(최종 점수판·승자)으로 내려주므로, teardown을 넣으면 종료 직후 재접속·재조회의 응답이 이번 change의 범위 밖에서 달라진다.

**대안 — `passSeq`를 게임 단위 토큰과 합성(`<gameId>:<roundSeq>`)**: 스크립트 수정이 없지만 방마다 게임 식별자를 새로 도입해야 하고(현재 없다), 그 토큰을 어디서 발급·저장할지가 또 다른 설계 결정이 된다.

**근거**: 문제의 원인이 **`START_SCRIPT`가 라운드 Hash를 `DEL` 없이 덮어쓰면서 `roundSeq`만 되돌린다**는 것이므로, 정리도 같은 자리에 두는 것이 인과에 가장 가깝다. 편집량이 두 줄이고, 게임 시작은 라운드 전이와 달리 **드물게 실행되며 이미 상태를 초기화하는 자리**라 회귀 위험이 가장 작다.

**전이 스크립트 무수정 원칙의 정확한 범위**: `START_SCRIPT`만 예외로 수정한다. `ADVANCE_ON_DEADLINE_SCRIPT`·`ADVANCE_ON_CORRECT_SCRIPT`는 **여전히 무수정**이며, 이 구분은 `tasks.md` 2.6·2.6a·8.3의 검증 항목으로 강제한다.

### Decision 6 — 두 신규 트리거의 게이트 방식은 의도적으로 다르다

| 트리거 | 게이트 | 이유 |
|---|---|---|
| 포기 (`/app/rooms/pass`) | 클라이언트가 보낸 `expectedSeq`로 CAS | 라운드 경계 경합 방어 |
| 퇴장 재평가 (`onPlayerLeft`) | 스크립트가 현재 `roundSeq`를 직접 읽음 | 호출 지점이 라운드를 모른다 |

**포기가 `expectedSeq`를 받아야 하는 이유** — 받지 않으면 이런 사고가 난다.

```
 t=44.9s  사용자가 Shift+Enter
 t=45.0s  마감 도달 → sweeper가 REVEAL로 전이 → 다음 라운드 OPEN
 t=45.1s  포기 요청 도착  ──▶  다음 라운드에 1표가 꽂힌다  ✗
                              (곡이 들리기도 전에)
```

프론트는 `roundReducer`에 `roundSeq`를 이미 들고 있으므로 페이로드에 싣기만 하면 되고, 서버는 `ADVANCE_ON_CORRECT_SCRIPT`와 동일하게 불일치 시 `IGNORED`로 흘린다.

**퇴장이 받으면 안 되는 이유** — `RoomEventListener.handleRoomLeft`는 `RoomLeftEvent`(roomId·playerId)만 보는 자리라 현재 라운드를 모른다. 알아내려고 `snapshot()`을 먼저 읽으면 읽기와 쓰기 사이에 라운드가 전이될 수 있어 오히려 경합이 생긴다. 스크립트가 현재 `roundSeq`를 직접 읽고 그 안에서 전부 끝내는 것이 원자적이고 정확하다.

### Decision 7 — 분모는 `scores` ZSET을 재사용한다

**결정**: `n = ZCARD scores:{shard}:<roomId>`.

**대안 — 별도 로스터 키**: 의미가 명시적이지만 같은 사실의 사본이 둘 생기고, 둘의 동기화가 새 버그 표면이 된다.

**근거**: `scores` ZSET은 이미 라이브 로스터다 — 게임 시작 시 전원 0점 초기화, 퇴장 시 제거, 게임 중 입장 불가. **단조 감소만** 하므로 임계 판정이 단순하다. 이미 같은 slot에 있어 원자 Lua에서 추가 제약 없이 읽을 수 있다.

**주의**: 승자 가점은 "아직 멤버일 때만" 적용되는 반면 `winnerId`는 무조건 기록되므로 "점수 항목이 없는 승자"가 성립한다(`RoundScoreboardAssembler` 참조). 분모로서의 `scores`는 이 성질과 무관하다 — 멤버십의 사실을 그대로 반영한다.

### Decision 8 — 포기 현황은 인원수만 공개한다

**결정**: `ROUND_PASS_UPDATED { roundSeq, passedCount, requiredCount }`. 누가 눌렀는지는 방송하지 않는다. 본인 여부는 클라이언트가 자기 조작으로 알고, 재접속 시에는 스냅샷의 `passed` 불리언으로 받는다.

**대안 — 채팅 피드에 "○○님이 모르겠대요"**: 파티 게임 느낌이 살고 "나도 모르겠는데"가 연쇄되어 임계가 잘 채워진다. 그러나 눈치가 보여 첫 클릭이 늦어지고, 포기를 사회적 낙인으로 만든다.

**근거**: 카운트만으로도 진행 상황의 사회적 신호는 충분하다. 익명성이 첫 클릭의 심리적 비용을 낮춘다.

**스냅샷에 `passed`만 싣고 `passedPlayerIds`를 싣지 않는 이유**: 목록을 내리면 devtools에서 보여 이 결정이 무효가 된다. `RoomService.getDetail(roomId, playerId)`가 이미 `playerId`를 갖고 있으므로 `roundService.getSnapshot(roomId, playerId)`로 시그니처만 넓히면 서버가 본인 여부를 판정해 불리언으로 내려줄 수 있다 — 인증 정보 배관이 추가되지 않는다.

### Decision 9 — 데스크톱 주 입력은 `Shift+Enter`

**결정**: 채팅 입력창의 `onKeyDown`에서 처리한다. 모바일은 버튼.

```tsx
if (e.key === "Enter" && !e.nativeEvent.isComposing) {
    e.preventDefault();
    if (e.shiftKey) pass(round.roundSeq); else handleSend();
}
```

`OPEN` 동안 포커스는 이미 채팅 입력창에 있다(추측을 거기 치므로). 버튼만 있으면 마우스로 손을 옮겼다가 돌아와야 해 리듬이 끊긴다.

| 후보 | IME 안전 | 오발 위험 | 판정 |
|---|---|---|---|
| `Shift+Enter` | ✓ | 낮음 | **채택** |
| `Ctrl+Enter` | ✓ | 낮음 | 탈락 — Slack·Discord 관습으로 "전송"이라 습관적 오발이 최악 |
| `Esc` | ✗ | 중간 | 탈락 — 한글 IME 조합 취소 키. 네이티브 `<dialog>`·오버레이가 이미 "닫기"로 예약 |
| 빈 입력 + `Enter` | ✓ | **높음** | 탈락 — 되돌릴 수 없고 정답 권리까지 잃는 액션에 가장 흔한 오발 경로 |
| 채팅 명령어 | △ | 매우 낮음 | 탈락 — 원문이 피드에 방송되어 Decision 8과 충돌. 억제하려면 "채팅은 항상 방송" 계약을 훼손 |

**IME 가드는 필수다.** `RoomView.tsx:254`가 이미 `!e.nativeEvent.isComposing`을 쓰고 있고, 커밋 #237(`일본어 IME 사용 시 재생 시간 숫자 입력이 막히는 문제`)이 같은 계열 이슈였다. 조합 중 `Enter`는 IME의 조합 확정이므로 그때 포기가 발화하면 안 된다.

`<input>`이라 줄바꿈 개념이 없어 `Shift+Enter`가 관습과 충돌하지 않는다.

### Decision 10 — 컨트롤 하나가 버튼·카운터·단축키 안내를 겸하고 상시 표시된다

**결정**: 채팅 입력 pill **바로 위**에 단일 컨트롤을 둔다. 아무도 누르지 않았을 때도 표시한다.

```
   Round 3 / 9                                    ⏱ 0:23
   ────────────────────────────────────────────────────────
   [채팅 피드]
   ────────────────────────────────────────────────────────
                              🤔 모르겠어요  Shift+Enter      ← 0명
                              🤔 3/4 모르겠어요  Shift+Enter   ← 눌린 뒤
                              ✓  3/4 모르겠어요  Shift+Enter   ← 내가 누른 상태
   ┌──────────────────────────────────────────────┐
   │ 정답을 입력하세요                          ➤  │
   └──────────────────────────────────────────────┘
```

**상시 표시의 근거 — 닭과 달걀**: 현황 표시에만 의존하면 이렇게 된다.

```
   아무도 안 누름  →  카운트 안 뜸  →  기능 존재를 모름  →  아무도 안 누름
        ▲                                                      │
        └──────────────────────────────────────────────────────┘
```

카운트는 **누군가 이미 눌렀을 때만** 뜨는데 발견성이 필요한 순간은 **아무도 안 눌렀을 때**다. 따라서 어포던스가 상시 존재해야 하고, 그것이 단축키 안내까지 짊어진다.

**단일 요소인 근거**: 카운트가 덧붙는 형태(`모르겠어요` → `3/4 모르겠어요`)라 레이아웃이 흔들리지 않고, 토글 상태(`🤔` ↔ `✓`)가 같은 자리에서 바뀌어 "내가 눌렀나"가 항상 명확하다.

**입력 pill 안이 아닌 이유**: pill 안에 버튼 둘을 넣으면 전송 버튼과 혼동된다.

**단축키 칩은 `hidden md:inline`**: 모바일에서 "Shift+Enter"는 거짓말이다.

**시각적 강도는 낮게(`text-zinc-500` 계열)**: 피하려는 것은 크기가 아니라 *유혹*이다. cyan glow에 펄스가 들어간 「SKIP」 버튼은 습관적 클릭을 부른다. 조용한 텍스트 컨트롤이면 읽히면서도 조르지 않는다.

**보조 안내 — `AudioGateOverlay`**: `showGate = isPlaying && !armed`라 `PLAYING` 진입 시 전원이 반드시 통과한다. 전체화면 오버레이라 공간이 공짜이고 새 요소가 0이다. `<p>` 두 개라 반응형 분기가 자연스럽다.

**placeholder**: `PLAYING` 중 `"보낼 메시지 입력"` → `"정답을 입력하세요"`. 지금 이 화면의 채팅은 잡담이 아니라 정답 제출인데 placeholder가 그것을 알려주지 않고 있다. 단축키 안내는 여기 넣지 않는다 — 타이핑을 시작하면 사라지고, 문자열이라 반응형 분기가 지저분해진다.

### Decision 11 — 프론트 리듀서는 `<` 가드를 쓰고 카운트에 `max()`를 쓰지 않는다

`roundReducer`에는 두 종류 가드가 섞여 있다.

```ts
// ROUND_STARTED / HYDRATE — 같은 seq면 버린다
if (e.roundSeq === state.roundSeq && state.phase !== "idle") return state;

// ROUND_REVEALED — 더 낮은 seq만 버린다
if (e.roundSeq < state.roundSeq) return state;
```

포기 이벤트는 **현재 `OPEN`과 같은 `roundSeq`로 온다.** 위쪽 패턴을 복사하면 전부 무시된다. `ROUND_REVEALED` 패턴이어야 한다.

또한 토글이라 **카운트가 양방향으로 움직인다.** `max()`로 받으면 취소가 영영 반영되지 않는다. 같은 `roundSeq` 안에서는 마지막 값을 그대로 받는다. 순서 보증은 기존 `CHAT` 이벤트가 이미 같은 pub/sub 채널을 비단조로 쓰고 있으므로 새로 생기는 위험이 아니다.

## Redis Pub/Sub 스키마

채널은 **기존 `room:{roomId}:events`를 그대로 쓴다.** 새 채널·새 토픽은 없다.

- **Producer**: `RoomEventListener` (`@TransactionalEventListener(AFTER_COMMIT, fallbackExecution = true)`) — 라운드 전이는 트랜잭션 밖에서 일어나므로 `fallbackExecution`이 필요하다. `RoundStartedEvent`·`RoundRevealedEvent`가 이미 같은 이유로 그렇게 되어 있다.
- **Consumer**: 각 replica의 `RoomEventRedisSubscriber` (`PatternTopic("room:*:events")`) → `/topic/rooms/{roomId}` STOMP.

메시지 스키마 (`RoomEventMessage`의 `@JsonSubTypes`에 `name = "ROUND_PASS_UPDATED"` 추가):

```json
{
  "type": "ROUND_PASS_UPDATED",
  "roomId": 1,
  "roundSeq": 7,
  "passedCount": 3,
  "requiredCount": 4,
  "playerId": null,
  "nickname": null
}
```

`playerId`/`nickname`은 null이다 — Decision 8에 따라 행위자를 공개하지 않으므로, 라운드 이벤트와 같이 행위자 없는 메시지로 둔다.

## 데이터 영향

- **DB 스키마**: 변경 없음. Flyway 마이그레이션 없음. 라운드 상태와 포기 집합은 전부 휘발성 Redis라 스키마 진화·롤백 대상이 아니다.
- **Elasticsearch 매핑 / CDC 흐름**: 영향 없음. `room` 모듈은 ES 색인 대상이 아니다.
- **Kafka 토픽**: 추가·변경 없음.
- **Modulith `event_publication`**: 신규 이벤트는 `@TransactionalEventListener`(ephemeral broadcast) 경로라 outbox에 적재되지 않는다. 직렬화 호환성 규칙(필드 추가는 nullable/default)의 적용 대상이 아니지만, 이벤트 클래스는 관례대로 `room/application/domain/`에 둔다.

## Redis 키 요약

```
round:{shard}:<roomId>   (Hash, 기존)
  ├ roundSeq, phase, deadlineAt, trackIndex, winnerId,
  │ totalRounds, trackOrder, trackDurations        ← 기존, 무변경
  └ passSeq                                        ← 추가 (lazy reset 마커)
                                                     게임 시작 시 START_SCRIPT가 HDEL — Decision 5-1

passes:{shard}:<roomId>  (SET, 신규)   ← 동일 hash tag = 동일 slot = CROSSSLOT 안전
  └ 포기 중인 playerId들 — **`passSeq == roundSeq`일 때만 유효**
    (불일치면 이전 라운드 잔재이므로 0명으로 취급. 모든 읽기 경로에 적용 — Decision 5)
    라운드 경계: lazy reset이 DEL / 게임 경계: START_SCRIPT가 DEL (roundSeq가 1로
    되돌아가 lazy reset 판별식이 성립하지 않으므로) / 방 정리: TEARDOWN_SCRIPT가 DEL

scores:{shard}:<roomId>  (ZSET, 기존)  ← 분모(ZCARD)로 재사용, 무변경
rounds:deadlines:{shard} (ZSET, 기존)  ← 무변경
```

## Risks / Trade-offs

**[퇴장 재평가 누락 → 영구 대기 상태] → 퇴장 경로를 임계 재평가 트리거로 명시하고 테스트로 못 박는다**

로스터가 줄면 임계도 함께 내려가므로, 아무도 새로 누르지 않았는데 임계가 충족되는 상태가 성립한다.

```
5명 / 임계 4 / 포기 3명        → 대기
  └ 포기 안 한 1명 퇴장
4명 / 임계 3 / 포기 3명        → 도달!  그러나 아무도 버튼을 누르지 않았다
```

또한 포기자가 퇴장했는데 `passes`에서 빼지 않으면 분자가 분모를 넘는다. `onPlayerLeft`가 `ZREM scores` + `SREM passes` + 임계 재평가를 **하나의 Lua로** 수행해야 한다.

**[포기 남용으로 매 라운드가 조기 종료] → Decision 4의 정답 권리 상실이 억제한다**

배포 후 실제 조기 종료 비율이 과하면 임계를 3/4로 올리는 것이 첫 조정 수단이다(판정식이 `passed * 4 >= n * 3`으로 바뀔 뿐이다).

**[2/3가 실제 방 크기에서 빡빡하거나 느슨] → 상수 하나(`PASS_NUMERATOR`/`PASS_DENOMINATOR`)로 분리해 조정 가능하게 둔다**

5인 방에서 4명을 요구하는 것이 체감상 과할 수 있다. 판정식을 상수로 분리해 두면 조정이 Lua 인자 변경으로 끝난다.

**[토글로 카운트가 비단조 → 순서 뒤바뀜에 취약] → 같은 `roundSeq` 안에서 마지막 값을 그대로 받는다**

기존 `CHAT` 이벤트가 이미 같은 채널을 비단조로 쓰고 있어 새로 도입되는 위험 계층이 아니다. 실제로 순서가 뒤집히더라도 다음 토글이 정정하고, 라운드 전환 시 리셋된다.

**[포기 토글 브로드캐스트 volume] → 무시 가능**

방당 최대 20명, 라운드당 토글 수는 한 자릿수다. 기존 채팅 방송보다 훨씬 적다.

**[임계 도달 순간의 포기 이벤트와 `ROUND_REVEALED`의 경합] → 전이 시에는 포기 이벤트를 발행하지 않는다**

임계를 넘긴 호출은 `ROUND_REVEALED`만 발행하고 `ROUND_PASS_UPDATED`는 생략한다. 두 이벤트가 같이 나가면 클라이언트가 `REVEAL`로 넘어간 뒤 포기 카운트를 다시 그리게 된다. 프론트는 `phase !== "OPEN"`이면 컨트롤과 카운트를 감춘다.

**[포기자의 정답 채팅이 `OPEN` 중 방송되어 정답이 유출될 수 있다] → 수용한 리스크 (현행 유지)**

Decision 4가 만들어내는 새 경로다. 지금까지 정답 원문 방송이 무해했던 이유는 정답이 도착하는 즉시 라운드가 `REVEAL`로 전이돼, 남이 그 채팅을 읽을 시점엔 이미 정답이 공개돼 있었기 때문이다. 포기 게이트는 이 인과를 끊는다.

```
 t=0    A가 포기 (판정 제외)
 t=10s  A가 정답 문자열을 채팅에 입력
        → RoomStompController.chat 이 원문을 방 전원에게 먼저 방송
        → submitAnswer 는 포기 게이트에서 반환 → 라운드는 OPEN 유지
 t=11s  B가 그대로 복사해 입력 → 승자·1점
```

**수용한다.** 근거는 두 가지다. (1) 이 게임의 방은 초대로 모이는 지인 기반 파티 공간이고, 설계 전반이 그 신뢰 전제 위에 서 있다(포기 익명성·방장 강제 스킵 배제도 같은 전제다). (2) 발생하려면 포기자가 정답을 알면서 굳이 채팅에 치는 자발적 행위가 필요하다 — 사고로 새는 경로가 아니라 의도적으로 남의 라운드를 망치는 행위이며, 그런 참가자는 포기 기능 없이도 정답을 흘릴 수 있다.

대안을 택하지 않은 이유는 각각 다른 결정을 무너뜨리기 때문이다.

| 대안 | 기각 이유 |
|---|---|
| 포기자의 정답 일치 채팅만 방송 억제 | 그 사람의 메시지만 사라지는 것 자체가 "저 사람은 포기했고 방금 정답을 쳤다"를 노출 → Decision 8(포기 익명성)을 훼손한다 |
| 포기자의 정답도 라운드를 전이시킴 | Decision 4의 대가가 사라져 "일단 누르고 계속 추측"이 다시 지배 전략이 된다 |
| 방송을 판정 뒤로 옮겨 정답이면 억제 | proposal이 범위 밖으로 분리한 기존 스펙-코드 divergence(방송 → 판정 순서)를 이번 change가 흡수하게 되어 tasks 8.2와 정면 충돌한다 |

**후속**: 필요하면 별도 change에서 방송/판정 순서 divergence를 정리할 때 함께 재검토한다. 그 change가 방송을 판정 뒤로 옮기면 이 유출 경로는 부수적으로 닫힌다.

**[마감 직후 도착한 포기] → 기존 `NOT_DUE` 자가 보정 패턴과 동일하게 처리한다**

sweeper 폴링 주기(1초) 안에 마감이 지난 라운드로 포기가 도착할 수 있다. `ADVANCE_ON_CORRECT_SCRIPT`가 "마감 이후 도착한 정답은 인정하지 않는다"를 `-1`(NOT_DUE)로 처리하는 것과 동일하게, 상태를 바꾸지 않고 흘린다.

## Open Questions

없음. 탐색 대화에서 임계 정책·전이 형태·토글 여부·대가 유무·공개 범위·입력 방식·표기가 모두 확정됐다.
