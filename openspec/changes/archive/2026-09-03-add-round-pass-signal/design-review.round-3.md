# design.md 적대적 리뷰 — add-round-pass-signal

검증 통과 지적 없음. `[치명]`·`[높음]`으로 세울 수 있는 결함을 찾지 못했다.

검토 범위: `design.md`, `proposal.md`, `tasks.md`, `specs/room-game-session/spec.md`, `specs/room-round-ui/spec.md`, 그리고 이 문서들이 주장하는 코드 지점(`RoundService`·`RoundStateStoreImpl`의 Lua 5종·`Room`·`RoomService`·`RoomEventListener`·`RoomStompController`·`RoomView.tsx`·`roundReducer.ts`).

## 기각한 후보

아래는 지적으로 세우려다 **코드·문서로 반증되어** 폐기한 의심들이다.

**`onPlayerLeft`가 임계 도달로 발행하는 이벤트가 유실된다는 의심** — `RoomEventListener.handleRoomLeft`는 `@TransactionalEventListener(AFTER_COMMIT)`이고 `fallbackExecution`이 없다(`RoomEventListener.kt:49-62`). 그 안에서 새 이벤트를 발행하면 이미 커밋된 트랜잭션의 동기화가 남아 있어 리스너가 안 돌 것이라고 의심했으나 반증됐다. `AFTER_COMMIT` 리스너는 `triggerAfterCompletion` 경로에서 `clearSynchronization()` **이후**에 실행되므로 본문 실행 중 `isSynchronizationActive()`가 false이고(spring-tx 6.2.9 `AbstractPlatformTransactionManager` / `TransactionalApplicationListenerSynchronization$PlatformSynchronization`), 수신 측인 `handleRoundRevealed`·신규 `handleRoundPassUpdated`가 `fallbackExecution = true`(`RoomEventListener.kt:92-131`, tasks 4.2)라 즉시 실행된다. design Decision 5-1 인접 서술과 tasks 3.3이 이 근거를 이미 명시하고 있다.

**`getSnapshot` 시그니처 확장이 다른 호출자를 깨뜨린다는 의심** — `back/src` 전체 grep 결과 `getSnapshot` 선언은 `RoundService.kt:100`, 호출은 `RoomService.kt:67` 한 곳뿐이고 `RoundSnapshotResponse` 생성 지점도 `RoundService.kt:115` 하나다. design 223행·proposal 44행·tasks 3.4/3.5의 "인증 배관 추가 없음" 주장이 코드와 일치한다.

**게임 재시작 시 `scores` 잔재로 분모(`n`)가 부풀려진다는 의심** — `START_SCRIPT`가 `scores`를 `DEL` 없이 `ZADD 0`만 한다는 점(`RoundStateStoreImpl.kt:155-178`)에서 이전 게임 멤버가 남아 `ZCARD`가 실제 로스터보다 클 수 있다고 의심했으나 반증됐다. 방 엔트리를 제거하는 메서드는 `Room.leave` 하나뿐이고(`Room.kt:133-140`, main 소스 전체 grep) 제거 성공 시 반드시 `RoomLeftEvent`를 등록해 `removeScore`(→ 신규 `onPlayerLeft` 스크립트)의 `ZREM`으로 이어진다. 방장 종료·방 삭제 경로는 `teardown`이 `scores`를 `DEL`한다(`RoomService.kt:139,162`). 따라서 Decision 7의 "`scores`는 라이브 로스터이고 단조 감소한다"는 전제가 성립한다.

**`n = 0`에서 `passed * 3 >= n * 2`가 `0 >= 0`으로 참이 되어 오전이한다는 의심** — 성립하려면 `scores`가 비었는데 누군가 포기를 보낼 수 있어야 한다. 게임 중 `Room.join`이 막히고(`Room.kt:83-97`) 모든 퇴장이 `ZREM`으로 이어지므로, 라운드가 `OPEN`인 동안 포기 발신자는 항상 `scores`의 멤버다(`n >= 1`). 마지막 1인이 퇴장하는 경우는 tasks 2.4가 `n > 0` 가드를 두고 있고, 그 직후 방이 비어 `teardown`이 돈다(`RoomService.kt:124-141`).

**게임 자연 종료 후 `passes` 잔재가 새 게임으로 이월된다는 의심** — 실제로 `advanceDueRoom`의 `ENDED` 분기에 teardown이 없어 라운드 Hash가 TTL만 걸린 채 남고(`RoundService.kt:130-160`), `START_SCRIPT`가 `roundSeq`를 1로 되돌린다(`RoundStateStoreImpl.kt:155-178`). 그러나 design Decision 5-1과 tasks 2.6a·5.7b가 정확히 이 경로를 지목해 `START_SCRIPT`의 `DEL passes` + `HDEL passSeq`로 닫고 있고, 필드가 삭제된 뒤에는 `HGET passSeq`가 nil이라 어떤 `roundSeq`와도 불일치해 모든 읽기 경로가 "포기자 0명"으로 판정한다. 이미 다뤄진 지점이다.

**정답과 포기의 동시 발화로 이중 전이가 난다는 의심** — `ADVANCE_ON_CORRECT_SCRIPT`가 `EXISTS` → `roundSeq` CAS → `phase ~= 'OPEN'` 게이트를 단일 Lua 안에서 통과시키고(`RoundStateStoreImpl.kt:223-247`), 신규 `TOGGLE_PASS_SCRIPT`·`ON_PLAYER_LEFT_SCRIPT`도 같은 게이트를 재사용하도록 tasks 2.2·2.4가 규정한다. 두 번째로 도착한 경로는 `roundSeq`가 이미 증가해 `'0'`(IGNORED)로 흐른다. 새 동기화 원시가 필요 없다는 Goals의 주장이 성립한다.

**마감 직후 도착한 포기가 상태를 오염시킨다는 의심** — tasks 2.2가 lazy reset(③)보다 **앞에** 마감 검사(②)를 두어, `NOT_DUE`로 흐르는 호출이 `passes`·`passSeq`를 건드리지 않는다. spec의 "상태를 바꾸지 않고 무시" 요구와 일치한다.

**본인 포기 표시가 서버와 영구히 어긋난다는 의심** — `ROUND_PASS_UPDATED`가 행위자를 담지 않으므로(Decision 8) 클라이언트는 본인 상태를 자기 조작으로만 안다. 서버가 토글을 거부(`IGNORED`/`NOT_DUE`/`phase != OPEN`)하면 국소 상태가 어긋날 수 있으나, 거부가 성립하는 모든 조건은 곧 라운드 전이를 뜻하고 `ROUND_STARTED`가 표시를 초기화한다(tasks 6.5, spec room-round-ui "라운드가 바뀌면 현황이 초기화된다"). 한 라운드를 넘겨 지속되는 어긋남을 만들지 못했다.

**`Shift+Enter`가 기존 채팅 전송 관습을 깬다는 의심** — 현재 `RoomView.tsx:253-257`은 `shiftKey`를 보지 않고 `Enter`면 무조건 `preventDefault()` + `handleSend()`를 하므로 `Shift+Enter`는 지금도 "전송"이다. Decision 9는 이 겸용을 인지하고 `<input>`이라 줄바꿈 관습이 없다는 근거로 재배정을 택했으며, IME 가드(`!e.nativeEvent.isComposing`, 254행)를 그대로 유지한다. 인용한 앵커가 현재 코드와 정확히 일치한다.

판정: 진입 가능 — 설계의 코드 주장이 실제 코드와 일치하고, 전이 게이트·라운드/게임 경계 정리·퇴장 재평가·전파 배제 등 핵심 경로가 모두 결정되어 있으며, 세운 반례가 모두 반증됐다.
