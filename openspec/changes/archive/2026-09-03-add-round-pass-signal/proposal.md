## Why

라운드 종료 조건이 비대칭이다. `OPEN`을 끝내는 트리거는 (a) 첫 정답 (b) 클립 소진 마감 둘뿐이라, **"안다"는 신호는 라운드를 즉시 끝내지만 "모른다"는 신호를 표현할 방법이 없다.** 그래서 방 전체가 이미 포기한 뒤에도 서버는 그것을 알 수 없어 클립이 다 재생될 때까지 전원이 대기한다.

`RoundService.openDurationMillis`가 `(endTimeSec - startTimeSec) × repeatCount + 2s`라, 흔한 15초 클립 × 3회 설정이면 47초다. `repeatCount` 상한은 5이고 클립 길이에는 상한이 없어 더 길어질 수도 있다. 아무도 모르는 곡일수록 이 시간이 통째로 死시간이 된다 — 실제 사용자 피드백이 **"다들 아무 말 없이 기다리기만 한다"** 였다.

## What Changes

- 참가자가 `OPEN` 구간에 **"모르겠어요"(포기) 신호**를 보낼 수 있다. 남은 인원의 **2/3 이상**이 포기하면 라운드가 즉시 `REVEAL`로 전이되고 승자는 없다(`winnerId = null`).
- 포기는 **토글**이다 — 같은 조작으로 켜고 끌 수 있다. 라운드가 바뀌면 자동으로 해제된다.
- **포기 중인 참가자는 그 라운드의 정답 판정에서 제외된다.** 취소하면 즉시 복원된다. 이 대가가 없으면 "일단 누르고 보기"가 손해 없는 지배 전략이 되어 매 라운드가 조기 종료되고, 원래 불만을 반대편 불만("곡이 나오다 말고 끊긴다")으로 바꿔놓는다. 채팅 원문 방송은 그대로라 잡담·반응은 살아 있다.
- 포기 현황은 **인원수만** 실시간 전파된다(누가 눌렀는지는 공개하지 않는다). 재접속·새로고침 시 라운드 스냅샷으로 복원된다.
- 게임 중 퇴장으로 남은 인원이 줄면 **임계를 재평가**한다. 아무도 새로 누르지 않아도 임계가 충족될 수 있기 때문이다.
- 프론트는 데스크톱에서 **`Shift+Enter`**를 주 입력 경로로 삼는다. `OPEN` 동안 포커스는 이미 채팅 입력창에 있으므로(추측을 거기 치므로) 마우스로 손을 옮기지 않고 조작할 수 있어야 한다. 모바일에는 조합키가 없으므로 버튼을 병행한다.
- 포기 컨트롤은 **아무도 누르지 않았을 때도 상시 표시**된다. 현황 표시에만 의존하면 "아무도 안 누름 → 카운트가 안 뜸 → 기능을 발견 못 함"의 닭과 달걀에 빠진다.

BREAKING 없음 — 기존 두 종료 경로(첫 정답·클립 소진)의 동작은 그대로다.

## Capabilities

### New Capabilities

없음. 기존 두 역량의 요구사항 확장으로 충족된다.

### Modified Capabilities

- `room-game-session`: 라운드 종료 경로에 **세 번째 트리거(포기 임계)**가 추가된다. 더불어 포기한 참가자의 정답 판정 제외, 포기 현황의 실시간 전파, 퇴장 시 임계 재평가, 재접속 스냅샷의 포기 현황 포함이 서버 계약으로 추가된다.
- `room-round-ui`: `OPEN` 구간의 포기 컨트롤·단축키·현황 표시가 추가되고, 오디오 제스처 게이트의 안내 문구와 게임 중 채팅 입력 placeholder가 변경된다.

## Impact

### 영향 받는 서브프로젝트

`back/`(room 모듈), `front/`. `infra/`는 영향 없음.

### 백엔드 — `room` 모듈 (헥사고날 계층별)

| 계층 | 변경 |
|---|---|
| `in/` | `RoomStompController`에 `/rooms/pass` 매핑 추가. `RoomEventListener`에 포기 현황 브로드캐스트 핸들러 추가 |
| `application/` | `RoundService`에 포기 처리·정답 판정 게이트·퇴장 재평가 추가. `RoundStateStore` 포트 확장. 포기 현황 도메인 이벤트·DTO 신설, `RoundSnapshotResponse` 확장 |
| `out/` | `RoundStateStoreImpl`에 Lua 스크립트 추가, `RoundRedisKeys`에 키 추가 |

`RoundService.getSnapshot`의 시그니처가 `(roomId)` → `(roomId, playerId)`로 넓어진다. 호출자인 `RoomService.getDetail(roomId, playerId)`가 이미 `playerId`를 갖고 있어 인증 정보 배관은 추가되지 않는다.

### Redis 키 영향

- **신규**: `passes:{shard}:<roomId>` (SET) — 현재 라운드의 포기자 집합. `RoundRedisKeys` 규약대로 방의 다른 키와 **동일 hash tag `{shard}`**를 써야 한다(아니면 클러스터에서 `CROSSSLOT` 거부).
- **기존 `round:{shard}:<roomId>` Hash에 필드 추가**: `passSeq`. 포기 집합의 lazy reset 마커로, 라운드 전이 스크립트(`ADVANCE_ON_DEADLINE`·`ADVANCE_ON_CORRECT`)를 수정하지 않고 라운드 경계에서 집합을 비우기 위한 것이다. 게임 경계는 `roundSeq`가 1로 되돌아가 lazy reset이 감지할 수 없으므로 `START_SCRIPT`에 `DEL passes` + `HDEL passSeq` 두 줄을 추가해 닫는다(design Decision 5-1).
- DB 스키마·ES 매핑·Kafka 토픽 영향 없음. 라운드 상태는 원래 휘발성이라 마이그레이션이 필요 없다.

### 실시간 프로토콜

- **신규 STOMP 목적지**: `/app/rooms/pass`
- **신규 브로드캐스트 타입**: `ROUND_PASS_UPDATED` (`RoomEventMessage`의 `@JsonSubTypes`에 추가). 기존 `room:{id}:events` pub/sub → STOMP 경로를 그대로 탄다.
- **응답 확장**: `GET /rooms/{roomId}`의 `round` 스냅샷에 포기 현황 필드 추가

### 프론트엔드

`useRoomSubscription`(발행 함수), `roundReducer`(상태·액션), `RoundEvent.ts`(타입), `RoundPanel`/`RoomView`(컨트롤·키 핸들러·placeholder), `AudioGateOverlay`(안내 문구).

### 범위 밖

탐색 중 발견한 기존 스펙 모순 — `room-game-session`은 "정답 채팅 원문은 일반 채팅으로 방송되지 않아야 한다"고 규정하는데 `room-round-ui`는 "정답 여부와 무관하게 표시된다"고 하고, `RoomStompController.chat`은 방송을 먼저 하고 `submitAnswer`를 나중에 불러 후자를 따른다. 본 change는 이 divergence를 건드리지 않으며, 별도 change로 분리한다.
