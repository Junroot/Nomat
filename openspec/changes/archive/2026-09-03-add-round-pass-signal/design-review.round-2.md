# Design Review — add-round-pass-signal

검증 통과 지적 1건(치명).

### [치명] `roundSeq`가 게임마다 1로 되돌아가는데 `passes`·`passSeq`는 자연 종료 후에도 남아, `passSeq == roundSeq` 유효성 계약이 다음 게임 1라운드에서 거짓 양성이 된다

- **위치**: `design.md` Decision 5(134-162행) — lazy reset 계약 및 "`TEARDOWN_SCRIPT`에 `DEL passes` 한 줄만 추가하면 정리가 끝난다". 연동: `design.md` Redis 키 요약(316-328행), `tasks.md` 2.6·8.3, `specs/room-game-session/spec.md`「포기는 토글이며 라운드 경계에서 자동 해제된다」

- **설계 주장**:
  - "`passes`는 `passSeq == roundSeq`일 때만 유효하다. … `passSeq != roundSeq`인 `passes`는 이전 라운드의 잔재이며 포기자 0명과 동일하게 취급해야 한다."
  - "lazy reset은 **기존 전이 스크립트를 한 줄도 수정하지 않는다.** 키는 방당 하나로 고정되어 `TEARDOWN_SCRIPT`에 `DEL passes` 한 줄만 추가하면 정리가 끝난다."
  - `tasks.md` 2.6·8.3이 이 주장을 강제한다 — "기존 `START_SCRIPT`·`ADVANCE_ON_DEADLINE_SCRIPT`·`ADVANCE_ON_CORRECT_SCRIPT`는 수정하지 않는다", "무수정인지 diff로 확인".

  이 계약은 **`roundSeq`가 방 안에서 전역 단조 증가한다**는 것을 암묵 전제로 한다. 그래야 "`passSeq`가 현재 `roundSeq`와 다르다 = 과거의 잔재"가 성립한다.

- **무엇이 깨지나**: `roundSeq`는 방 안에서 단조가 아니다. 게임이 자연 종료(`ENDED`)될 때 라운드 상태는 **teardown되지 않고** 24h TTL로 남으며, 같은 방이 다시 게임을 시작하면 `START_SCRIPT`가 라운드 Hash를 `DEL` 없이 덮어써 `roundSeq`를 **다시 1로** 만든다. `START_SCRIPT`가 건드리지 않는 `passSeq` 필드와 `passes` SET은 이전 게임 값 그대로 남는다.

  따라서 **이전 게임에서 마지막으로 포기 토글이 일어난 라운드가 1라운드였다면 `passSeq == 1`이 남고, 새 게임의 1라운드(`roundSeq == 1`)에서 그 값이 "유효"로 판정된다.** 유효성 판정이 통과하므로 lazy reset도 발화하지 않는다(`passSeq ~= tostring(seq)`가 거짓).

  재현 시나리오(게임 1은 자연 종료, 방장 강제 종료 아님):

  ```
   게임 1
     라운드 1(roundSeq=1)  A가 포기 → passSeq=1, passes={A}, 임계 미달
     라운드 2..N           아무도 토글하지 않음, 아무도 퇴장하지 않음
     마지막 REVEAL 만료    ADVANCE_ON_DEADLINE → phase=ENDED
                           advanceDueRoom: ENDED → flipRoomToActive 만 호출 (teardown 없음)
                           room.status = ACTIVE
   게임 2
     방장이 다시 시작 → START_SCRIPT: HSET roundSeq=1, phase=OPEN (DEL 없음)
                        → passSeq=1 그대로, passes={A} 그대로
     라운드 1(roundSeq=1)  passSeq(1) == roundSeq(1)  ⇒ passes={A} 를 "유효"로 판정
  ```

  이 상태에서 네 가지가 동시에 깨진다.

  | 깨지는 것 | 결과 |
  |---|---|
  | Decision 4 정답 게이트 | A는 새 곡을 듣기도 전에 포기 상태여서 **정답 판정에서 제외된다.** 프론트는 `ROUND_STARTED`에서 포기 표시를 초기화하므로 A 화면에는 아무 흔적이 없다 — 설계가 156행에서 "침묵 실패"라고 지목한 바로 그 형태다 |
  | Decision 3 토글 | A가 포기를 누르면 `SISMEMBER`가 참이라 **`SREM`(취소)이 나간다.** 화면과 반대로 동작한다 |
  | Decision 2 임계 | `passedCount`가 1에서 시작한다. 3인 방이면 다른 한 명만 눌러도 `2*3 >= 3*2` → 곡 시작 직후 즉시 `REVEAL` |
  | spec 「라운드가 바뀌면 포기가 해제된다」 | 위반. "참가자가 이전 라운드의 포기를 이월당해 새 곡을 듣기도 전에 포기 상태로 시작해서는 안 된다(SHALL NOT)"가 그대로 깨진다 |

  설계가 제시한 청소 경로 어느 것도 이 경우를 덮지 않는다.
  - `TEARDOWN_SCRIPT`(`DEL passes`)는 **자연 종료 경로에서 호출되지 않는다.** 호출 지점은 방 삭제·방장 강제 종료·sweeper의 고아 정리뿐이다.
  - lazy reset은 `passSeq != roundSeq`일 때만 발화하는데, 이 시나리오는 정확히 **일치**하는 경우다.
  - 24h TTL은 백스톱일 뿐 같은 세션 안의 연속 게임을 막지 못한다.

  이 갈림길은 구현자가 국소적으로 정할 수 있는 사항이 아니다. 자연스러운 해법(`START_SCRIPT`에 `DEL passes` + `HDEL passSeq` 추가, 또는 자연 종료 시 teardown, 또는 `passSeq`를 게임 단위 토큰과 합성)은 모두 **설계가 lazy reset을 택한 근거("기존 전이 스크립트를 한 줄도 수정하지 않는다")를 재검토하게 만들고**, `tasks.md` 2.6·8.3이 명시적으로 금지한 곳을 건드린다.

- **검증 근거**:
  - `RoundStateStoreImpl.kt:155-178` — `START_SCRIPT`는 라운드 Hash에 `DEL` 없이 `HSET 'roundSeq', 1, 'phase', 'OPEN', …` 8개 필드만 쓴다. `passSeq` 같은 추가 필드는 그대로 남고, `roundSeq`는 리터럴 `1`이다.
  - `RoundService.kt:130-160` — `advanceDueRoom`의 `RoundPhase.ENDED -> flipRoomToActive(roomId)`(157행). teardown 호출 없음.
  - `RoundService.kt:162-170` / `Room.kt:125-131` — `flipRoomToActive` → `room.endByEngine()` → `status = ACTIVE`.
  - `Room.kt:99-108` — `start`는 `status == ACTIVE`면 통과. 즉 자연 종료 후 같은 방이 곧바로 다시 시작할 수 있다.
  - `RoomService.kt:140-149` — `start`는 `room.start` 후 `roundService.startRound(roomId)` → `START_SCRIPT`.
  - `back/src/main/kotlin` 전체 grep `teardownRound|teardown(` — 호출 지점은 `RoomService.kt:139`(방이 비어 삭제), `RoomService.kt:162`(방장 `end`), `RoundService.kt:133`·`:137`(스냅샷/방 없음 고아 정리) 넷뿐. **자연 종료 경로 없음.**
  - `RoundStateStoreImpl.kt:180-221` — `ENDED` 진입 분기는 `HSET` + `EXPIRE ttl` + `ZREM deadlines`로, 라운드 Hash를 TTL만 걸고 남긴다.
  - `design.md:141` — `if passSeq ~= tostring(seq) then … end`. 일치하면 reset이 발화하지 않는다.
  - `tasks.md` 2.6 / 8.3 — `START_SCRIPT` 수정 금지를 명시.
  - `specs/room-game-session/spec.md`「포기는 토글이며 라운드 경계에서 자동 해제된다」 — "이전 라운드의 포기를 이월당해 … 포기 상태로 시작해서는 안 된다(SHALL NOT)".

## 기각한 후보

- **`onPlayerLeft`가 `AFTER_COMMIT` 리스너 안에서 호출되므로, 거기서 발행하는 `RoundPassUpdatedEvent`·`RoundRevealedEvent`가 새 트랜잭션 동기화로 등록되기만 하고 실행되지 않을 것이다** (`tasks.md` 3.3의 "`fallbackExecution = true`가 발행을 성립시킨다" 주장에 대한 의심).
  → **반증됨.** spring-tx 6.2.9의 `TransactionalApplicationListenerSynchronization$PlatformSynchronization`은 `beforeCommit(boolean)`·`afterCompletion(int)`만 구현하고 `afterCommit()`을 구현하지 않는다(javap). 즉 `AFTER_COMMIT` 리스너는 `triggerAfterCompletion` 경로에서 실행되며, `AbstractPlatformTransactionManager.triggerAfterCompletion`은 `getSynchronizations()` → **`clearSynchronization()`** → `invokeAfterCompletion(...)` 순서로 진행한다(javap -c). 따라서 `handleRoomLeft`(`RoomEventListener.kt:49-62`) 본문이 실행되는 시점에는 `isSynchronizationActive()`가 false이고, 중첩 발행된 이벤트는 `fallbackExecution = true` 분기로 **즉시** 처리된다. 설계·tasks의 주장이 맞다.

- **`design.md:264`의 `showGate = isPlaying && !armed`가 실제 코드(`!isDeactivated` 조건이 하나 더 있음)와 달라, "PLAYING 진입 시 전원이 반드시 통과한다"는 도달률 주장이 깨진다**는 의심.
  → **반증됨.** `isDeactivated`는 같은 계정의 다른 접속으로 세션이 대체돼 구독이 비활성화된 상태 플래그이고(`useRoomSubscription.ts:55,75,97,202,229`), 그 상태의 화면은 `RoomView.tsx:277`의 별도 안내 블록으로 대체된다. 게임에 참여 중인 참가자는 예외 없이 게이트를 통과하므로 안내 도달률 주장은 성립한다.

판정: 조건부(치명·높음 1건 선해결) — 게임 재시작 시 `roundSeq`가 1로 되돌아가는데 `passes`/`passSeq`는 자연 종료 후에도 남는다는 사실을 Decision 5의 유효성 계약에 반영하고, 그 정리 책임을 어디에 둘지(START 경로 수정 / 자연 종료 시 teardown / `passSeq`를 게임 단위로 유일화) 설계가 결정해야 한다. 현재 설계·tasks는 그 세 위치를 모두 금지하거나 다루지 않는다.
