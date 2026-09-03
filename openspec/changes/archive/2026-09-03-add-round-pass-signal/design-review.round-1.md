# design.md 적대적 리뷰 — add-round-pass-signal

검증 통과 지적 2건(치명 2). 근거는 전부 현재 코드를 직접 열어 확인했다.

---

### [치명] 정답 판정 게이트가 lazy reset을 통과하지 않아, 한 번 포기한 참가자가 이후 라운드 내내 정답 판정에서 제외된다

- **위치**: `design.md:122-130` (Decision 4), `design.md:132-149` (Decision 5), `design.md:309-311` (Redis 키 요약)
- **설계 주장**:
  - Decision 4 — "`RoundService.submitAnswer`가 `AnswerMatcher.matches` 앞에서 `SISMEMBER passes <playerId>`를 확인해 포기 상태면 판정하지 않는다."
  - Decision 5 — 포기 집합의 라운드 경계 청소는 lazy reset이며, `passSeq` 비교 후 `DEL passes`를 수행하는 주체는 **"포기·퇴장 스크립트"** 둘뿐이다. "기존 전이 스크립트를 한 줄도 수정하지 않는다"가 이 결정의 채택 근거다.
  - 키 요약 — `passes:{shard}:<roomId>` = "현재 라운드에 포기 중인 playerId들".
- **무엇이 깨지나**: lazy reset 채택으로 `passes` 키에는 **"`passSeq == roundSeq`일 때만 유효하다"는 숨은 불변식**이 붙는데, Decision 4가 정의한 읽기 경로에는 그 조건이 없다. 설계 문장 그대로 구현하면 재현 시나리오는 다음과 같다.

  ```
  라운드 3(OPEN, roundSeq=5)  A가 포기 → SADD passes A, HSET passSeq 5
                              임계 미달로 라운드 계속
  마감 도달                    ADVANCE_ON_DEADLINE_SCRIPT → REVEAL(seq=6) → OPEN(seq=7)
                              (이 스크립트는 passes를 건드리지 않는다 — 무수정이 Decision 5의 전제)
  라운드 4(OPEN, roundSeq=7)  passes = {A}, passSeq = 5  ← 아무도 토글하지 않으면 영원히 이 상태
  A가 정답 입력                submitAnswer: SISMEMBER passes A = 1 → 판정 스킵
                              A는 게임이 끝날 때까지(또는 누군가 포기를 토글할 때까지) 단 한 라운드도 이길 수 없다
  ```

  게다가 이 상태는 **본인에게도 다른 참가자에게도 보이지 않는다.** 프론트는 `ROUND_STARTED`에서 포기 표시를 초기화하므로(design Decision 11 / tasks 6.5) A의 화면은 "포기 안 함"이고, 서버만 A를 배제한다. 침묵 실패라 운영 중 원인 규명도 어렵다.

  이는 delta spec의 명시 요구사항을 직접 위반한다 — `specs/room-game-session/spec.md:156` "포기 상태는 현재 라운드에만 유효해야(SHALL) 하며, 라운드가 바뀌면 별도 조작 없이 해제되어야(SHALL) 한다", 같은 파일 `:181` "포기를 취소하면 판정 자격이 즉시 복원되어야(SHALL) 한다".

  이건 구현자가 코드를 보고 국소적으로 정할 사항이 아니다. lazy reset을 쓰는 이상 **모든 읽기 경로가 `passSeq` 유효성을 함께 판정해야 한다**는 것은 Decision 5가 만들어낸 계약이고, 설계가 그 계약을 명시하지 않은 채 읽기 경로를 `SISMEMBER` 단독으로 못박아 두었다. 실제로 `tasks.md:17`(2.7 스냅샷)만 "`passSeq` 유효성을 확인해"를 담고 있고 `tasks.md:22`(3.2 정답 게이트)에는 그 문구가 없어, 산출물 안에서도 이 불변식이 한쪽에만 적용돼 있다 — 불변식이 설계에 문장으로 존재하지 않기 때문에 생긴 누락이다.
- **검증 근거**:
  - `RoundStateStoreImpl.kt:180-221` — `ADVANCE_ON_DEADLINE_SCRIPT`가 `OPEN→REVEAL`, `REVEAL→OPEN/ENDED`를 모두 처리하며 포기 집합 성격의 키를 전혀 건드리지 않는다. `ADVANCE_ON_CORRECT_SCRIPT`(223-247)·`START_SCRIPT`(155-178)도 동일. 즉 라운드 경계에서 `passes`를 비우는 코드 경로는 설계상 존재하지 않는다.
  - `RoundStateStoreImpl.kt:258-266` — `TEARDOWN_SCRIPT`는 게임 종료·방 삭제 시점에만 호출된다(`RoundService.kt:90-92`, `RoomService.kt:138-140`, `RoomService.kt:162`). 라운드 경계가 아니다.
  - `RoundService.kt:66-80` — 현재 `submitAnswer`는 `snapshot(roomId)`로 phase만 확인한 뒤 곧바로 `AnswerMatcher.matches`로 간다. 여기에 Decision 4의 `SISMEMBER`를 그대로 끼워 넣으면 위 시나리오가 성립한다.
  - `design.md:136-143` — 인용된 lua 조각의 주석이 "포기·퇴장 스크립트가 진입 시" 수행한다고 적어 적용 범위를 두 쓰기 경로로 한정하고 있다.

---

### [치명] 포기한 참가자의 정답 채팅이 라운드가 열린 채로 전원에게 방송되어, 정답이 공개 힌트가 된다

- **위치**: `design.md:122-130` (Decision 4), `proposal.md:62-64` (범위 밖 — 채팅 방송 divergence)
- **설계 주장**: Decision 4 — "채팅 원문 방송(`RoomStompController`가 별도로 수행)은 영향을 받지 않는다. … **채팅 방송을 유지하는 것이 핵심이다** — 포기한 사람도 잡담과 반응은 계속하므로, 이 대가는 게임에서 빠지는 것이 아니라 그 라운드의 채점에서만 빠지는 것이다." 그리고 proposal은 "정답 채팅 원문을 방송하는" 기존 동작을 의도적으로 범위 밖에 두고, `tasks.md:77`(8.2)이 방송 순서를 바꾸지 말 것을 검증 항목으로 못박는다.
- **무엇이 깨지나**: 현재 코드에서 **정답 원문 방송이 무해한 이유는 정답이 도착하는 즉시 라운드가 `REVEAL`로 전이되기 때문**이다. 방송된 정답을 남이 읽을 시점엔 이미 정답이 공개돼 있다. Decision 4는 이 인과를 끊는다 — 포기 중인 참가자의 정답은 **방송은 되지만 라운드는 `OPEN`으로 유지**된다.

  ```
  t=0    A가 포기 (판정 제외)
  t=10s  A가 채팅에 "밤을 달리다" 입력 (정답)
         RoomStompController.chat  → convertAndSend 로 방 전원에게 원문 방송   (RoomStompController.kt:60)
                                   → submitAnswer 는 포기 게이트에서 즉시 return (Decision 4)
         라운드는 계속 OPEN. 정답 문자열만 채팅 피드에 떠 있다.
  t=11s  B가 그대로 복사해 입력 → 승자·1점 획득
  ```

  결과는 두 가지다. (1) **정답 유출** — "OPEN 동안 정답은 클라이언트로 내려가지 않는다"는 라운드 엔진의 기둥 규칙(`back/CLAUDE.md` "정답 비노출")이 참가자 채팅을 우회로로 뚫린다. (2) **그리핑** — 포기해서 어차피 못 이기는 참가자가 정답을 뿌려 남의 라운드를 망칠 수 있고, Decision 4가 붙인 "포기의 대가"가 오히려 그 행동의 비용을 0으로 만든다. 악의가 없어도, 포기한 뒤 곡이 떠올라 반사적으로 타이핑하는 흔한 행동만으로 재현된다.

  설계가 이 위험을 수용했다고 볼 수 없다. proposal이 범위 밖으로 둔 것은 **"정답 채팅을 방송하느냐"에 대한 기존 스펙-코드 divergence**이고, 그 divergence가 지금까지 무해했던 근거(정답=즉시 전이)는 본 change가 처음으로 깨뜨린다. Decision 4의 "채팅 방송 유지" 논거도 **잡담·반응**만 검토했을 뿐 **포기자의 정답 문자열**이라는 경우를 다루지 않았다. 즉 이건 이미 다뤄진 리스크의 재제기가 아니라, 새 결정이 만들어낸 미검토 경로다.

  그리고 이 갈림길은 구현자가 국소적으로 못 정한다 — 해결안이 다른 결정과 정면으로 충돌하기 때문이다.

  | 대응 | 충돌 |
  |---|---|
  | 포기자의 정답 일치 채팅만 방송 억제 | 그 사람의 메시지만 사라지는 것 자체가 "저 사람은 포기했고 방금 정답을 쳤다"를 노출 → Decision 8(익명성) 훼손 |
  | 포기자의 정답도 라운드를 전이시킴 | Decision 4(대가) 자체가 무의미해짐 |
  | 방송을 판정 뒤로 옮겨 정답이면 억제 | 범위 밖으로 분리한 divergence를 이번 change가 건드리게 됨 (`tasks.md:77`과 정면 충돌) |
  | 현행 유지(수용) | 수용한다면 그 근거가 설계에 있어야 하는데 없다 |

  어느 쪽을 고르든 Decision 4·8 또는 change 경계가 함께 움직이므로 설계 시점 결정이다.
- **검증 근거**:
  - `RoomStompController.kt:52-63` — `chat` 핸들러가 `RoomChatEventMessage`를 만들어 `redisTemplate.convertAndSend(channel, ...)`로 **먼저** 방송(60행)하고, 그 다음 `roundService.submitAnswer(...)`를 호출(63행)한다. 방송은 정답 여부·판정 결과와 무관하게 무조건 실행된다.
  - `RoundService.kt:66-80` — 정답이면 `tryAdvanceOnCorrect`가 성공하고 즉시 `publishRoundRevealed`가 나간다. 이것이 현재 "정답 원문 방송이 무해한" 유일한 이유다. 포기 게이트를 `AnswerMatcher.matches` 앞에 두면 이 전이가 발생하지 않는다.
  - `RoundStateStoreImpl.kt:223-247` — `ADVANCE_ON_CORRECT_SCRIPT`만이 `OPEN`을 정답으로 닫는다. 이 스크립트에 도달하지 못하면 라운드는 마감/포기 임계까지 `OPEN`이다.
  - `specs/room-game-session/spec.md:192-194` — delta spec의 시나리오 "포기한 참가자의 채팅은 그대로 방송된다"가 정답 문자열을 예외로 두지 않아, 스펙 수준에서도 이 경로가 열려 있다.

---

## 기각한 후보

- **"퇴장으로 분모만 줄었을 때 `requiredCount` 갱신이 전파되지 않아 화면이 stale 해진다"** — 6인/2명 포기 상태에서 미포기자가 둘 나가면 필요 인원이 4→3으로 내려가지만 포기 인원수는 그대로라 전파 트리거(`specs/room-game-session/spec.md:202` "포기 인원수가 변할 때마다")가 걸리지 않는다. 그러나 전이 판정 자체는 `ON_PLAYER_LEFT_SCRIPT`가 서버에서 원자적으로 재수행하므로(`design.md:328`, `tasks.md:14`) **동작은 정확**하고, 표시값은 다음 토글·라운드 전환에서 자기 보정된다. 진입을 막는 결함이 아니라 폐기한다.
- **"`ROUND_REVEALED`가 OPEN과 같은 `roundSeq`로 온다"는 프론트 주석 전제 위에서 Decision 11이 성립하는가"** — 실제로 `ADVANCE_ON_CORRECT_SCRIPT`(`RoundStateStoreImpl.kt:240-244`)와 `ADVANCE_ON_DEADLINE_SCRIPT`(`:193-199`)는 `newSeq = seq + 1`을 실어 보내므로 `roundReducer.ts:98`의 주석은 부정확하다. 그러나 Decision 11이 요구하는 것은 `<` 가드이고, 포기 이벤트가 현재 `OPEN`의 `roundSeq`로 발행되는 한(`tasks.md:7`) `<` 가드는 라이브 수신·지각 도착 양쪽에서 정확히 동작한다. 설계 결론은 반증되지 않아 기각한다.
- **"`Shift+Enter` 핸들러가 `phase !== "OPEN"`에서도 채팅 전송을 가로챈다"** — `design.md:196-200`의 스니펫은 phase 조건 없이 `e.shiftKey`로 분기한다. 다만 서버가 `expectedSeq`/`phase` 게이트로 흘리므로 상태는 깨지지 않고, 핸들러를 phase로 감쌀지는 어느 쪽을 골라도 다른 설계 결정이 그대로 서는 국소 선택이라 구현 시점 결정으로 폐기한다.
- **"`n = ZCARD scores`가 접속하지 않은 방 멤버까지 분모에 넣는다"** — `START_SCRIPT`(`RoundStateStoreImpl.kt:169-171`)가 `roomRepository.findPlayerIdsByRoomId`로 받은 전원을 0점 등록하는 것은 사실이나, 이는 `design.md:99`가 "연결은 살아 있고 자리만 비운 참가자를 서버는 구분할 수 없다"로 명시 수용한 리스크이며 2/3 임계의 채택 근거 자체다. 수용이 부당하다는 논거를 세우지 못해 기각한다. 접속 끊김은 `RoomDisconnectListener.kt:29`의 `scheduleLeave`로 유예 후 실제 퇴장 처리되어 분모에서 빠진다.

판정: 조건부(치명·높음 2건 선해결) — (1) lazy reset의 "`passSeq == roundSeq`일 때만 `passes`가 유효하다"는 불변식을 설계 계약으로 명시하고 정답 판정 게이트를 포함한 **모든 읽기 경로**에 적용할 것, (2) 포기한 참가자의 정답 채팅이 `OPEN` 상태에서 방송되어 정답이 유출되는 경로에 대한 결정을 설계에 세울 것(억제·전이·수용 중 무엇이든, Decision 8과의 충돌 처리를 포함해).
