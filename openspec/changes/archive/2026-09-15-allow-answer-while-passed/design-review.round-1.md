# design.md 적대적 리뷰

검증 통과 지적 없음 — `[치명]`·`[높음]`으로 세울 수 있는 결함을 찾지 못했다.

설계의 코드 주장(게이트 위치, 유일 호출자, 방송-판정 순서, lazy reset 판별식, 프론트 무관여)을 전부 실제 파일을 열어 대조했고 모두 현재 코드와 일치했다. 문서 간(design / proposal / tasks / delta spec / 메인 spec) 대조에서도 서로 어긋나는 지점이 없었다. 아래는 의심했으나 **반증된** 후보들이다.

## 기각한 후보

### 후보 1 — 델타의 `MODIFIED` 요구사항이 본문을 유실했을 것

OpenSpec은 `MODIFIED` 블록으로 요구사항 **전체**를 교체하므로, 델타가 긴 본문(표기 정규화 표·폴백 규칙·시나리오 9개)을 옮겨 적다가 빠뜨렸으면 스펙이 조용히 손실된다고 의심했다.

**반증**: 메인 스펙(`openspec/specs/room-game-session/spec.md:131-195`)과 델타 블록을 기계 diff한 결과, 차이는 의도한 한 줄(`- **AND** 정답 채팅 원문은 일반 채팅으로 방송되지 않아야 한다` → 새 문장)뿐이었다. 나머지는 바이트 동일. `npx openspec validate allow-answer-while-passed --strict`도 통과.

### 후보 2 — Decision 4대로 포기 표를 안 지우면 다음 라운드로 이월될 것

"전이와 함께 자연 소멸한다"는 낙관적 단정으로 보여 반례를 세우려 했다.

**반증**: 경로를 끝까지 추적하면 성립한다. 포기자 A가 라운드 `seq=S`에서 정답 → `ADVANCE_ON_CORRECT_SCRIPT`(`RoundStateStoreImpl.kt:361-385`)가 `roundSeq`를 `S+1`로 올리고 `passes`·`passSeq`는 건드리지 않는다(`passSeq`는 `S` 유지). REVEAL 마감 후 다음 `OPEN`은 `S+2`. 이 시점의 모든 읽기/쓰기가 판별식으로 잔재를 거른다 — `snapshot`(`:133`)은 `passSeq != roundSeq`라 `passedCount=0`·`passing=false`, `TOGGLE_PASS`/`ON_PLAYER_LEFT`는 `RESET_STALE_PASSES`(`:263-271`)로 `DEL passes` 후 `passSeq`를 갱신한다. 표시 측도 `roundReducer.ts:104-107`이 `ROUND_STARTED`에서 세 필드를 0/false로 초기화하고, `PassControl`은 `RoomView.tsx:176`의 `phase === "OPEN"` 가드로 REVEAL 구간에 아예 렌더되지 않는다.

### 후보 3 — 프론트가 게이트를 알고 있어 "프론트 변경 없음"이 거짓일 것

포기 중 입력창 비활성화나 "판정에서 제외됩니다" 류 문구가 하나라도 있으면 Non-Goals가 무너진다.

**반증**: `ChatInput.tsx`는 props로 `passed`를 아예 받지 않고(`placeholder`·`onSend`·`passRoundSeq`·`onPass`뿐), `PassControl.tsx`는 토글·카운터만 그린다. 채점 관련 문구 없음. `room-round-ui` 스펙에도 게이트를 규정하는 문장이 없다(25·29행은 "정답 판정은 서버가 수행"만 서술). 프론트 델타 누락이 아니다.

### 후보 4 — `tasks.md 3.6`("`RoomRoundPassStompIntegrationTest` 변경 없음")이 실제로는 깨질 것

그 테스트가 포기자로 정답 문자열을 보내면 이번 변경으로 라운드가 전이돼 단언이 흔들린다고 의심했다.

**반증**: `RoomRoundPassStompIntegrationTest.kt:110-128`이 보내는 내용은 `"이건 모르겠다"`로 트랙 제목이 아니며, 단언도 채팅 수신 여부뿐이고 phase를 보지 않는다. 3.6의 지시는 정확하다.

### 후보 5 — Decision 3의 `snapshot(...).passing` 이관이 다른 판별식을 보게 될 것

이관 대상 테스트가 실제로는 다른 성질을 검증하게 되면 회귀 커버리지가 사라진다.

**반증**: `snapshot`(`RoundStateStoreImpl.kt:133`)의 `passesValid`는 `IS_PASSING_SCRIPT`(`:453-463`)와 동일한 `passSeq == roundSeq` 판별식이다. 차이는 원자성뿐(`snapshot`은 HGETALL/SCARD/SISMEMBER 3회 왕복)인데, 이관 대상 테스트(`RoundStateStoreIntegrationTest.kt:349-361`)는 동시성 없이 순차 실행하므로 원자성에 의존하지 않는다. 설계가 이 손실을 명시적으로 인정하고 근거를 댄 것과도 일치한다.

### 후보 6 — `tasks.md 3.5`가 지목한 "마지막 줄"이 실제 코드와 다를 것

**반증**: `RoundStateStoreIntegrationTest.kt:375`가 그 테스트의 마지막 단언이고 내용도 `assertThat(roundStateStore.isPassing(roomId, 1L)).isFalse()`로 정확히 일치한다. 같은 테스트의 `snapshot.passedCount == 0`·`snapshot.passing == false` 단언(373-374행)이 남아 성질을 계속 덮는다는 주장도 맞다.

판정: 진입 가능 — 설계의 코드 주장 전부가 현재 코드와 일치하고, 문서 간 불일치·부수 효과 전파 누락·수용한 위험의 부당성을 세울 근거가 없다.
