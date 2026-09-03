## 1. 백엔드 — 라운드 상태 포트 확장 (`room/application/domain`)

- [x] 1.1 `RoundStateStore`에 `togglePass(roomId, expectedSeq, playerId): PassOutcome` 포트 추가. KDoc에 "포기 토글 → 임계 도달 시 `OPEN→REVEAL` CAS"와 `expectedSeq` 게이트의 목적(라운드 경계에서 늦게 도착한 신호 차단)을 명시한다
- [x] 1.2 `RoundStateStore.removeScore(roomId, playerId)`를 `onPlayerLeft(roomId, playerId): PassOutcome`으로 확장. 점수판 제거·포기 집합 제거·임계 재판정을 하나의 원자 연산으로 수행한다는 계약을 KDoc에 남긴다. 호출 지점이 라운드를 모르므로 `expectedSeq`를 받지 않고 스크립트가 현재 `roundSeq`를 직접 읽는다는 비대칭(design Decision 6)을 함께 적는다
- [x] 1.3 `PassOutcome(transition: RoundTransition, passedCount: Int, requiredCount: Int, passing: Boolean)` 데이터 클래스 추가. `transition.result == TRANSITIONED`이면 임계 도달로 라운드가 전이된 경우이고, 그때는 포기 현황을 별도 전파하지 않는다는 규칙을 KDoc에 명시한다
- [x] 1.4 `RoundSnapshot`에 `passedCount`·`requiredCount`·`passing` 추가 (`passing`은 조회자 기준)
- [x] 1.5 `RoundPassUpdatedEvent(roomId, roundSeq, passedCount, requiredCount)`를 `room/application/domain/`에 추가. 이벤트 클래스를 이 패키지에 두는 것은 Modulith 직렬화 안정성 관례를 따르는 것이며, 본 이벤트는 `@TransactionalEventListener` ephemeral broadcast 경로라 outbox 적재 대상이 아니다
- [x] 1.6 `RoundStateStore`에 포기 여부 조회 포트 추가 — 정답 판정 게이트(3.2)가 쓰는 읽기 경로다. `passSeq == roundSeq` 유효성과 `SISMEMBER`를 **하나의 원자 연산**으로 판정한다는 계약을 KDoc에 명시한다 (design Decision 5의 유효성 계약)

## 2. 백엔드 — Redis 어댑터 (`room/out`)

- [x] 2.1 `RoundRedisKeys`에 `passes(roomId): String = "passes:{shard}:<roomId>"` 추가. 방의 다른 키와 **동일 hash tag `{shard}`**를 쓰는 이유(클러스터 `CROSSSLOT` 회피)를 KDoc 키 목록에 함께 기재한다
- [x] 2.2 `RoundStateStoreImpl`에 `TOGGLE_PASS_SCRIPT` 추가 — `RoundStateStoreImpl`은 기존대로 `private class` 가시성을 유지한다. 스크립트 순서: ① `EXISTS round` / `roundSeq` CAS / `phase == OPEN` 검사 → ② `now > deadline`이면 `-1`(NOT_DUE) 반환 → ③ `passSeq != roundSeq`면 `DEL passes` + `HSET passSeq roundSeq`(lazy reset) → ④ `SISMEMBER`로 방향 판단해 `SADD`/`SREM` → ⑤ `n = ZCARD scores`, `passed = SCARD passes` → ⑥ `passed * 3 >= n * 2`이고 방금 `SADD`한 경우면 `OPEN→REVEAL` 전이(`roundSeq+1`, `winnerId=''`, `deadlineAt = now + REVEAL_MILLIS`, `ZADD deadlines`) → ⑦ 아니면 진행 상황만 반환. `now`는 기존 `NOW_MS` 조각(`redis.call('TIME')`)을 재사용한다
- [x] 2.3 임계 판정 상수를 `PASS_NUMERATOR = 2` / `PASS_DENOMINATOR = 3`으로 분리해 Lua 인자로 전달한다 (배포 후 3/4 등으로 조정 가능하게)
- [x] 2.4 `ON_PLAYER_LEFT_SCRIPT` 추가 — `ZREM scores` + `SREM passes`를 먼저 수행하고, 라운드가 존재하고 `phase == OPEN`이며 `n > 0`일 때만 임계를 재판정해 필요 시 `OPEN→REVEAL` 전이. 여기서도 `passSeq != roundSeq`면 포기 수를 0으로 취급한다(stale 집합을 세지 않는다). 라운드가 없으면 점수판 제거만 하고 종료한다
- [x] 2.5 `parse()` 확장 — 포기 응답 포맷 `"2|<passed>|<required>|<passing>"`(진행만 갱신)을 기존 `"1|..."`(전이)·`"0"`(IGNORED)·`"-1"`(NOT_DUE)과 함께 해석해 `PassOutcome`을 만든다
- [x] 2.6 `TEARDOWN_SCRIPT`에 `DEL passes` 추가 (KEYS에 passes 키 추가). `**ADVANCE_ON_DEADLINE_SCRIPT`·`ADVANCE_ON_CORRECT_SCRIPT`는 수정하지 않는다** — lazy reset을 채택한 이유가 매 라운드 전이가 지나가는 이 두 경로를 건드리지 않는 것이다(design Decision 5)
- [x] 2.6a `START_SCRIPT`에 `DEL <passes>` + `HDEL <round> passSeq` 추가 (KEYS에 passes 키 추가) — 기존 `HSET roundSeq 1 …`과 같은 스크립트 안에서 수행한다. **전이 스크립트 무수정 원칙의 유일한 예외다**(design Decision 5-1). 게임이 자연 종료(`ENDED`)될 때는 teardown이 호출되지 않아 `passes`/`passSeq`가 24h TTL로 남는데, 재시작 시 `roundSeq`가 1로 되돌아가므로 lazy reset의 `passSeq != roundSeq` 판별식이 이월된 잔재를 감지하지 못한다. KDoc/주석에 이 근거를 남긴다
- [x] 2.7 `snapshot(roomId, playerId)` 구현 — `passSeq` 유효성을 확인해 `passedCount`를 읽고, `requiredCount`는 `ZCARD scores`로 계산하며, `passing`은 `SISMEMBER`로 판정한다

## 3. 백엔드 — 서비스 (`room/application`)

- [x] 3.1 `RoundService.pass(roomId, playerId, expectedSeq)` 추가 — `roundStateStore.togglePass` 호출 후 결과 분기: `TRANSITIONED`면 `publishRoundRevealed`(승자 없음, 다음 트랙 동봉), 진행만 갱신이면 `RoundPassUpdatedEvent` 발행, `IGNORED`/`NOT_DUE`면 아무것도 하지 않는다
- [x] 3.2 `RoundService.submitAnswer`에 포기 게이트 추가 — `AnswerMatcher.matches` **앞에서** 포기 상태를 확인해 포기 중이면 판정하지 않고 반환한다. 판정은 `SISMEMBER passes` 단독이 아니라 `**passSeq == roundSeq` 유효성을 함께** 확인해야 한다(design Decision 5의 계약). 불일치면 이전 라운드 잔재이므로 포기 상태가 아닌 것으로 본다. 둘을 별도 왕복으로 나누지 않고 하나의 원자 연산으로 읽는다. 채팅 원문 방송은 `RoomStompController`가 별도로 수행하므로 영향이 없다는 점을 주석으로 남긴다
- [x] 3.3 `RoundService.onPlayerLeft` 확장 — 새 포트를 호출하고, `TRANSITIONED`면 `publishRoundRevealed`, 진행 변경이면 `RoundPassUpdatedEvent` 발행. 이 호출은 이미 `AFTER_COMMIT` 이후 지점이므로 라운드 이벤트 리스너의 `fallbackExecution = true`가 발행을 성립시킨다는 점을 주석으로 남긴다
- [x] 3.4 `RoundService.getSnapshot(roomId)` → `getSnapshot(roomId, playerId)`로 시그니처 확장, `RoundSnapshotResponse`에 `passedCount`·`requiredCount`·`passed` 추가. **포기자 식별자 목록은 싣지 않는다**
- [x] 3.5 `RoomService.getDetail`의 `roundService.getSnapshot(roomId)` 호출을 `getSnapshot(roomId, playerId)`로 변경 — 이 메서드는 이미 `playerId`를 파라미터로 받고 있어 인증 배관 추가가 없다
- [x] 3.6 `RoundPassUpdatedEventMessage` DTO 추가 (`roomId`·`roundSeq`·`passedCount`·`requiredCount`, `playerId`/`nickname`은 null). `RoomEventMessage`의 `@JsonSubTypes`에 `name = "ROUND_PASS_UPDATED"` 등록

## 4. 백엔드 — 인바운드 어댑터 (`room/in`)

- [x] 4.1 `RoomStompController`에 `@MessageMapping("/rooms/pass")` 추가 — `start`/`end`와 동일한 `roomSession()` 패턴을 따르고, `RoomPassRequest(roundSeq: Long)` 페이로드를 받는다. `RoomStompController`는 기존대로 `private class` 가시성을 유지한다
- [x] 4.2 `RoomEventListener`에 `handleRoundPassUpdated` 추가 — `@TransactionalEventListener(phase = AFTER_COMMIT, fallbackExecution = true)`. 라운드 전이가 트랜잭션 밖에서 일어나므로 `fallbackExecution`이 필요하다(`handleRoundStarted`·`handleRoundRevealed`와 동일). 기존 `room:{roomId}:events` 채널로 방송한다. `RoomEventListener`는 기존대로 `private class` 가시성을 유지한다

## 5. 백엔드 — 테스트

기존 라운드 테스트(`RoundStateStoreIntegrationTest`, `RoomRoundEngineIntegrationTest`, `RoomRoundLifecycleIntegrationTest`)의 구조·네이밍·`@IntegrationTest` + Testcontainers 패턴을 먼저 읽고 동일한 방식으로 작성한다. **모킹 라이브러리를 도입하지 않는다.** `RoundRedisKeys`가 `internal`이라 화이트박스 상태 조작이 가능하다.

- [x] 5.1 `RoundStateStoreIntegrationTest`에 임계 판정 케이스 추가 — 남은 인원 1·2·3·4·5·8·20에 대해 필요 인원이 각각 1·2·2·3·4·6·14인지 검증 (`n = 1`이 즉시 전이되는 경계 포함)
- [x] 5.2 포기 토글 테스트 — 같은 참가자가 두 번 보내면 인원수가 0으로 돌아오고, 같은 참가자의 중복 포기가 인원수를 두 번 세지 않는지 검증
- [x] 5.3 라운드 경계 테스트 — 이전 `roundSeq`를 가리키는 포기가 무시되는지, 라운드가 바뀌면 포기 집합이 lazy reset으로 비워지는지 검증
- [x] 5.4 마감 이후 도착한 포기가 `NOT_DUE`로 무시되고 상태를 바꾸지 않는지 검증 (기존 "마감 시각 이후 도착한 정답" 테스트와 같은 방식으로 마감을 과거로 당겨 조작)
- [x] 5.5 동시성 테스트 — 여러 스레드가 동시에 포기해 임계를 넘겨도 전이가 정확히 한 번만 일어나는지 검증 (`RoundStateStoreIntegrationTest`의 기존 `CountDownLatch` + `Executors` 패턴 재사용)
- [x] 5.6 정답과 포기의 경합 — 임계 도달과 정답이 동시에 도착해도 라운드가 한 번만 전이되는지 검증
- [x] 5.7 포기 중 정답 판정 제외 테스트 — 포기한 참가자의 정답이 승자로 기록되지 않고 점수도 오르지 않는지, 취소 후에는 정답이 인정되는지 검증
- [x] 5.7a lazy reset 유효성 회귀 테스트 — 라운드 N에서 포기한 참가자가 **아무 토글 없이** 라운드 N+1로 넘어간 뒤 정답을 맞히면 정상적으로 승자·가점이 되는지 검증 (`passes`가 남아 있고 `passSeq`만 stale인 상태를 화이트박스로 만들어 재현). 이 판정이 빠지면 침묵 실패로 그 참가자가 게임 내내 이길 수 없다
- [x] 5.7b 게임 재시작 회귀 테스트 — 게임 1의 1라운드에서 포기 → 게임이 `ENDED`로 자연 종료(teardown 없음) → 같은 방이 다시 시작 → 새 게임 1라운드에서 ① 포기 인원수가 0이고 ② 그 참가자가 포기 상태가 아니며 ③ 그 참가자의 정답이 정상적으로 승자·가점이 되는지 검증. `START_SCRIPT`의 `DEL passes` + `HDEL passSeq`가 없으면 `passSeq(1) == roundSeq(1)`로 잔재가 "유효"하게 읽혀 침묵 실패한다(design Decision 5-1)
- [x] 5.8 포기한 참가자의 채팅이 그대로 방송되는지 STOMP 통합 테스트로 검증 (`RoomStompChatIntegrationTest` 패턴)
- [x] 5.9 퇴장 재평가 테스트 — ① 5명 중 3명 포기 상태에서 미포기자 1명이 나가면 4명/임계 3으로 전이되는지 ② 포기자가 나가면 포기 인원수도 줄어 남은 인원수를 넘지 않는지 ③ 퇴장 후에도 미달이면 계속 `OPEN`인지
- [x] 5.10 포기 현황 전파 테스트 — `ROUND_PASS_UPDATED`가 인원수만 담고 포기자 식별자·닉네임을 담지 않는지, 임계 도달로 전이된 경우에는 `ROUND_REVEALED`만 오고 포기 현황 메시지가 오지 않는지 STOMP 통합 테스트로 검증 (Awaitility 사용)
- [x] 5.11 스냅샷 복원 테스트 — `OPEN` 중 포기한 참가자의 스냅샷에 `passed=true`·인원수가 담기고, 응답에 포기자 목록 필드가 없는지 검증
- [x] 5.12 포기로 종료된 라운드가 다음 라운드로 정상 진행하고 마지막이면 게임이 종료되는지 검증 (`RoomRoundLifecycleIntegrationTest` 패턴)
- [x] 5.13 `./gradlew test` 실행 — 전체 통과 확인
- [x] 5.14 `./gradlew detekt` 실행 — 새 코드에 신규 위반이 없는지 확인 (특히 Lua 문자열 길이·함수 복잡도)

## 6. 프론트엔드 — 프로토콜 · 상태

- [x] 6.1 `app/utils/RoundEvent.ts`에 `RoundPassUpdatedEvent` 타입 추가 (`type: "ROUND_PASS_UPDATED"`, `roundSeq`, `passedCount`, `requiredCount`)하고 `RoundSnapshotResponse`에 `passedCount`·`requiredCount`·`passed` 추가. 파일 상단의 "소스 오브 트루스" 주석에 새 DTO 경로를 반영한다
- [x] 6.2 `app/hooks/roundReducer.ts`의 `RoundState`에 `passedCount`·`requiredCount`·`passed` 추가, `initialRoundState`에 초기값 추가
- [x] 6.3 `PASS_UPDATED` 액션 추가 — 가드는 반드시 `if (e.roundSeq < state.roundSeq) return state`(`ROUND_REVEALED` 패턴). `ROUND_STARTED`/`HYDRATE`의 `=== && phase !== "idle"` 패턴을 복사하면 **같은 `roundSeq`로 오는 포기 이벤트가 전부 씹힌다.** 이 함정을 주석으로 남긴다
- [x] 6.4 카운트를 `max()`로 누적하지 않고 마지막 값을 그대로 반영한다 — 토글이라 인원수가 줄어들 수 있고, `max()`를 쓰면 취소가 영영 반영되지 않는다. 주석으로 근거를 남긴다
- [x] 6.5 `ROUND_STARTED` 처리에서 포기 상태를 초기화한다 (`passedCount = 0`, `passed = false`) — 이전 라운드의 현황이 이월되면 안 된다. `HYDRATE`는 스냅샷 값을 그대로 반영한다
- [x] 6.6 `app/hooks/useRoomSubscription.ts`에 `pass(roundSeq: number)` 추가 — 기존 `chat`/`start`/`end`와 동일하게 `client.publish({ destination: "/app/rooms/pass", body: JSON.stringify({ roundSeq }) })`. `ROUND_PASS_UPDATED` 수신을 리듀서 액션으로 연결한다

## 7. 프론트엔드 — UI

- [x] 7.1 포기 컨트롤 컴포넌트 추가 — 채팅 입력 pill **바로 위**에 배치. 표기는 `🤔 모르겠어요  Shift+Enter` → 인원수가 있으면 `🤔 3/4 모르겠어요  Shift+Enter` → 본인 포기 상태면 `✓ 3/4 모르겠어요  Shift+Enter`. 인원수는 **덧붙는** 형태라 레이아웃이 밀리지 않아야 한다
- [x] 7.2 `phase === "OPEN"`일 때만 렌더링. 포기 인원이 0명이어도 컨트롤 자체는 표시한다 (발견성의 닭과 달걀 — design Decision 10)
- [x] 7.3 단축키 표기를 `hidden md:inline`으로 감싼다 — 조합키가 없는 화면 폭에서 `Shift+Enter`는 사실이 아니다
- [x] 7.4 시각 강도를 낮게 유지한다 (`text-zinc-500` 계열, glow·펄스 없음). 기존 다크 테마 팔레트를 벗어나는 새 색을 도입하지 않는다
- [x] 7.5 `routes/RoomView.tsx`의 채팅 `onKeyDown`을 확장 — `if (e.key === "Enter" && !e.nativeEvent.isComposing) { e.preventDefault(); if (e.shiftKey) pass(round.roundSeq); else handleSend(); }`. `**isComposing` 가드는 필수다** (한글·일본어 조합 확정 `Enter`가 포기로 해석되면 안 된다)
- [x] 7.6 컨트롤 클릭도 동일한 `pass(round.roundSeq)`를 호출하도록 연결 (모바일 유일 경로)
- [x] 7.7 `PLAYING` 중 채팅 placeholder를 `"보낼 메시지 입력"` → `"정답을 입력하세요"`로 분기. 단축키 안내는 placeholder에 넣지 않는다
- [x] 7.8 `components/ui/AudioGateOverlay.tsx`에 안내 두 줄 추가 — `"정답은 채팅으로 입력하세요"` + 데스크톱 `"모르겠는 곡은 Shift+Enter"`(`hidden md:block`) / 모바일 `"모르겠는 곡은 🤔 버튼"`(`md:hidden`). 기존 버튼·레이아웃은 건드리지 않는다
- [x] 7.9 `npm run typecheck` 실행 — 통과 확인
- [x] 7.10 `npm run build` 실행 — 통과 확인

## 8. 마무리 검증

- [x] 8.1 수동 시나리오 확인 — 2인 방(만장일치로 수렴), 5인 방(4명 필요), 포기 후 취소, 포기 상태로 새로고침, 포기자 퇴장으로 임계 도달
- [x] 8.2 `RoomStompController.chat`의 방송 순서(방송 → `submitAnswer`)를 바꾸지 않았는지 확인 — 범위 밖으로 분리한 스펙 divergence를 이번 change가 건드리지 않아야 한다. 포기자의 정답 채팅이 `OPEN` 중 방송되는 것은 design.md Risks에서 **수용한 리스크**이므로, 여기서 억제 로직을 추가하지 않는다
- [x] 8.3 라운드 전이 스크립트 2종(`ADVANCE_ON_DEADLINE`·`ADVANCE_ON_CORRECT`)이 무수정인지 diff로 확인. `START_SCRIPT`는 2.6a의 `DEL passes` + `HDEL passSeq` 두 줄과 KEYS 추가 **외에는** 변경이 없는지 diff로 확인한다 (기존 `HSET` 필드·`ZADD scores`·반환 포맷 무변경)

