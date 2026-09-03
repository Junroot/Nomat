## Why

`REVEAL` 구간의 길이는 5초다(`RoundStateStoreImpl.kt:145`, `REVEAL_MILLIS = 5_000L`). 이 값은 `add-game-round-engine`의 `design.md` Decision "REVEAL 길이"에서 **정답을 인지하는 데 필요한 시간**을 기준으로 골랐다.

```
  당시의 근거 (design.md:200)
  하한 ├─ 정답 인지 2~3초 (Typito)
       ├─ transient 표준 4초 (Material)
       └─ 동일 구조 binb 5초
  상한 ├─ Nielsen 10초 한계
       └─ Kahoot 10초 = "낭비"          ◄── 10초를 배제한 근거
```

**그 사이에 이 구간의 목적이 바뀌었다.** `keep-chat-visible-on-reveal`(#241)이 정답 공개를 전체화면 오버레이에서 인-플로우 영상 배치로 바꾸면서, `REVEAL`은 "정답을 확인하고 넘어가는 전환 구간"이 아니라 **정답 곡을 듣고 보는 감상 구간**이 됐다. 실제로 `room-round-ui` 스펙은 이미 이 구간을 그렇게 규정한다 — "이 구간의 주된 페이로프는 계속 재생되는 정답 곡의 소리이며 영상은 시각적 확인 수단이다."

목적이 달라졌으므로 기존 조사의 결론을 그대로 물려받을 수 없다. **"Kahoot의 10초는 낭비"는 *정답 인지* 목적의 10초를 평가한 것**이고, 감상 목적의 구간에는 적용되지 않는다. 5초는 곡을 듣기에 짧다.

## What Changes

`REVEAL` 길이를 **5초 → 10초**로 늘린다.

- `RoundStateStoreImpl.kt:145` `REVEAL_MILLIS`: `5_000L` → `10_000L`
- 두 진입 경로(`tryAdvanceOnDeadline` = 타임아웃, `tryAdvanceOnCorrect` = 첫 정답)가 모두 이 상수를 Lua 스크립트에 넘기므로 상수 하나로 양쪽이 함께 반영된다
- 길이가 정답자 유무·방 설정과 무관하게 **고정**이라는 기존 성질은 유지한다

**숫자가 박혀 있는 문서·주석을 함께 정리한다.** 지금 "5초"는 스펙 2곳과 코드 주석 4곳에 흩어져 있어 상수 하나를 바꾸면 6곳이 거짓이 된다. 길이의 소유는 `room-game-session`에 두고, 나머지는 **숫자를 빼서 다시는 드리프트하지 않게** 한다.

| 위치 | 처리 |
|---|---|
| `specs/room-game-session/spec.md` | 5초 → 10초 (길이를 소유하는 유일한 자리) |
| `specs/room-round-ui/spec.md` "5초 남짓한 공개 구간" | 숫자 제거 — UI는 길이를 소유하지 않는다 |
| 코드 주석 4곳 (`RoomRoundEngineIntegrationTest:118`, `RoomRoundScoreboardNicknameIntegrationTest:123`, `useRoundAudioOrchestrator.ts:272`, `RoundPanel.tsx:26`) | 숫자 제거 — 상수를 주석이 복창하지 않게 한다 |

### 알면서 감수하는 것

- **짧은 클립 방에서는 같은 구간의 반복 횟수만 늘어난다.** `REVEAL`에 들리는 소리는 곡의 새 부분이 아니라 OPEN에서 들었던 클립 구간의 루프다(`useRoundAudioOrchestrator.ts:271-278`이 `endSeconds` 제약 안에서 되감아 다시 튼다). 3초 클립이면 1.7바퀴가 3.3바퀴가 된다. 클립 길이엔 하한이 없다(`PlaylistCreationRequest.kt:56`은 `start > end`만 막는다). **클립 구간을 넘어 이어 듣게 하는 일은 본 변경의 범위가 아니다** — `endSeconds`를 풀려면 `loadVideoById` 재호출이 필요해 담당 플레이어의 버퍼를 버리게 되고, 재생 개시 지연을 실측해야 하는 별개의 결정이다. 10초로 먼저 살아보고 반복이 실제로 거슬리면 후속 change로 다룬다.
- **공개 구간에 진행 표시는 계속 없다.** `REVEAL`에는 `deadlineAt`이 실리지 않고(`roundReducer.ts:104`) 카운트다운도 `OPEN`에만 뜬다(`RoundPanel.tsx:62`). 감상 구간에 남은 초를 띄우면 압박이 되므로 의도적으로 그대로 둔다.
- **체감은 10~11초다.** 전이를 구동하는 sweeper가 1초 폴링이라(`RoundDeadlineSweeper.kt`) 마감과 스윕 틱 사이에 최대 1초 지터가 붙는다. `add-game-round-engine`이 "1~2초 늦어도 무감"으로 이미 허용한 성질이며 본 변경이 바꾸지 않는다.
- **총 게임 시간이 라운드당 5초 늘어난다.** 트랙 20개면 +100초.

## Impact

- **영향 스펙**:
  - `room-game-session` (MODIFIED) — "정답 공개 후 다음 라운드로 자동 진행하고 마지막이면 종료된다"의 길이 5초 → 10초
  - `room-round-ui` (MODIFIED) — "ROUND_REVEALED 수신 시 정답·승자·점수판을 표시한다"에서 길이 언급 제거
- **영향 서브프로젝트**: `back/` — 동작 변경은 여기뿐. `front/`는 **동작 변경 없이 주석 1곳 문구만** 동기화한다. `infra/` 변경 없음
- **영향 도메인 모듈**: `room` — 헥사고날 계층은 **`out`만**(`RoundStateStoreImpl`). `in`·`application`은 이 상수를 모른다
- **DB 스키마·ES 매핑·Kafka 토픽 영향**: 없음
- **Redis 키 영향**: 키·해시 필드·ZSET 구조 모두 불변. `rounds:deadlines:{shard}`에 기록되는 `deadlineAt` **값**만 5초 뒤에서 10초 뒤로 바뀐다. 마이그레이션 대상이 아니다
- **배포 중 진행 중인 게임**: 배포 시점에 이미 `REVEAL`인 방은 마감 시각이 5초 기준으로 ZSET에 **이미 기록돼 있어** 그 라운드는 5초로 끝나고, 다음 라운드부터 10초가 된다. 섞여도 게임이 깨지지 않으므로 별도 조치는 없다
