## Why

라운드에서 정답이 나오면 **채팅이 5초 동안 사라진다.** 정답 영상과 정답 텍스트가 뜨는 동안 메시지 피드와 입력창이 함께 가려지고, 입력창을 눌러도 반응하지 않는다.

원인은 영상이 아니라 그 밑에 깔린 오버레이다. `RoundRevealOverlay`가 `fixed inset-0`으로 뷰포트 전체를 덮고 `backdrop-blur`로 흐린다(`RoundRevealOverlay.tsx:41`). `pointer-events`를 열어두지 않아 클릭도 통과하지 못한다.

```
   z-50  ClipPlayer         ┌──────────────────────┐  fixed, top-8vh, 가운데
                            │   정답 영상 (iframe)  │  min(92vw,720px,80vh)
                            └──────────────────────┘
   z-40  RoundRevealOverlay ╔══════════════════════╗  fixed inset-0
                            ║ 🎉 승자 정답! / 곡 제목 ║  bg-zinc-950/85
                            ╚══════════════════════╝  backdrop-blur-sm
   z-0   실제 레이아웃        nav │ 방 정보 │ RoundPanel + 채팅 + 입력창
                                            ▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲ 전부 가려짐
```

**이 차단은 서버 계약이 요구하는 것이 아니다.** 서버는 라운드 단계와 무관하게 채팅을 방송하고 정답 판정만 `OPEN`에서 수행한다(`RoundService.kt:63`). 즉 `REVEAL` 5초(`RoundStateStoreImpl.REVEAL_MILLIS`) 동안 채팅은 계약상 멀쩡히 흐르고 있는데 화면만 그것을 가린다 — 순전히 클라이언트가 만든 손실이고, 하필 정답이 공개된 직후 반응이 오가는 구간이다.

**기존 명세와도 어긋난다.** `room-round-ui`는 "시스템은 `PLAYING` 동안 채팅 입력·피드를 화면에 노출해야(SHALL) 한다"고 규정하지만, `REVEAL`도 `PLAYING`의 일부이므로 현재 구현은 라운드마다 5초씩 이 요구사항을 위반한다. 명세가 `OPEN`만을 전제로 쓰여 공개 구간을 명시하지 않은 탓에 위반이 드러나지 않았다.

**같은 뿌리에서 나온 부수 문제가 둘 더 있다.**

1. **좌표 수식이 두 파일에 복제돼 있다.** 영상은 iframe이라 DOM으로 옮기면 재로드되므로 오버레이의 자식이 될 수 없고, 그래서 두 요소가 서로를 모른 채 각자 뷰포트 기준으로 배치되며 같은 식을 손으로 맞춰 뒀다. 양쪽 파일에 "한쪽만 바꾸면 겹치거나 벌어진다"는 경고 주석이 달려 있다.

   ```
   ClipPlayer.tsx          top: 8vh            높이: min(92vw,720px,80vh) × 9/16
   RoundRevealOverlay.tsx  pt : 8vh + 그 높이 + 1.5rem      ← 손으로 베낀 값
   ```

2. **정답 시점의 점수판이 제대로 안 보인다.** 점수는 `ROUND_REVEALED`에 실려 오는데, 그것을 그리는 `RoundPanel`은 오버레이의 블러 뒤에 있다. 점수가 갱신되는 바로 그 순간에 점수판이 가장 안 보인다.

이 오버레이의 존재 근거는 "iframe을 재부모화할 수 없다"였다. 그러나 진짜 제약은 **DOM 부모가 바뀌면 안 된다**는 것이지 `fixed`로 띄워야 한다는 것이 아니다. 언마운트되지 않는 자리에 처음부터 마운트해 두면 영상은 흐름 안에서 살 수 있고, 그러면 오버레이도 좌표 복제도 필요 없어진다.

## What Changes

### front/ — 전체화면 정답 오버레이 폐기

- `app/components/ui/RoundRevealOverlay.tsx` **삭제**. `RoomView`의 `showReveal` 상태도 함께 제거
- 화면을 덮는 레이어가 사라지므로 `REVEAL` 동안 채팅 피드·입력창이 그대로 보이고 계속 입력·전송된다

### front/ — 정답 표시를 `RoundPanel` 인라인으로

- `RoundPanel`이 `REVEAL`에서 정답(`title`)·승자(`winnerNickname`)·타임아웃을 인라인으로 표시. `round` prop을 이미 받고 있어 **새 prop이 필요 없다**
- 점수판이 블러 뒤에서 나오므로 `ROUND_REVEALED` 시점의 점수 갱신이 처음으로 제대로 보인다
- 오버레이의 dim이 사라진 만큼의 시각적 강조는 패널 자체의 강조(기존 `shadow-glow-cyan` 유틸리티)로 대신한다

### front/ — 정답 영상을 축소해 흐름 안으로

- `ClipPlayer`의 노출 스타일을 `fixed`·화면 중앙·대형에서 **흐름 안 축소 형태**로 변경. `z-50`·`top-[8vh]`·`min(92vw,720px,80vh)` 좌표 수식이 전부 사라진다
- `RoundAudioPlayer`의 렌더 위치를 `ColumnsContainer`의 형제에서 **`Column2` 안쪽**(`RoundPanel`과 메시지 목록 사이)으로 옮긴다. `Column2`는 방 화면이 살아 있는 동안 항상 렌더되므로 런타임에 재부모화가 일어나지 않는다 — 게임 시작 전 부트스트랩 선불(`reduce-round-playback-latency`)은 그대로 유지된다
- 숨김은 지금과 같이 `display:none`이며 소리는 계속 난다. 클릭을 받지 않는 성질(`pointer-events-none`)도 유지해 실수로 정답 곡을 멈추는 사고를 막는다
- 오디오 제스처 게이트가 떠 있는 동안에는 영상도 숨긴다

### front/ — 채팅 바닥 고정 훅 신규

- `REVEAL` 진입 시 라운드 정보 영역이 커져 메시지 영역이 줄어든다. 현재 로직은 **메시지 배열이 바뀔 때만** 바닥으로 재고정하므로, 컨테이너가 줄어드는 이 경우를 잡지 못해 **정답 직후 도착한 메시지가 화면 밖으로 밀려난다**
- 메시지 컨테이너의 크기 변화를 관찰해 바닥을 보고 있던 참가자를 계속 바닥에 붙이는 훅을 추가한다. 위로 스크롤해 과거를 보던 참가자는 건드리지 않는다

### back/ · infra/ — 변경 없음

프론트 전용 변경이다. API 계약·DB·ES·Kafka·Redis 영향 없음.

## Capabilities

### New Capabilities

- 없음.

### Modified Capabilities

- `room-round-ui`: 정답 공개 구간의 표시 방식을 규정한다. ① 채팅 노출 요구사항이 `OPEN`뿐 아니라 `REVEAL`에도 적용됨을 명시하고, ② 정답 공개 UI가 화면을 점유하지 못하도록 제한하며, ③ 공개 시점에 채팅의 스크롤 위치가 어긋나지 않아야 함을 요구사항으로 추가한다.

## Impact

- **서브프로젝트**: `front/`만. `back/`·`infra/` 영향 없음
- **헥사고날 계층**: 해당 없음(프론트 전용)
- **DB 스키마 / ES 매핑 / Kafka 토픽 / Redis 키**: 영향 없음
- **API 계약 변화**: 없음. 서버 이벤트(`ROUND_REVEALED`)와 스냅샷 형태를 그대로 쓴다
- **외부 의존성**: 신규 npm 패키지 없음
- **영향 받는 화면**: `/rooms/:roomId`의 `PLAYING` 구간만. 로비·플레이리스트 화면 무관
- **파일**:
  - 삭제 — `app/components/ui/RoundRevealOverlay.tsx`
  - 수정 — `app/routes/RoomView.tsx`, `app/components/ui/RoundPanel.tsx`, `app/components/ui/ClipPlayer.tsx`, `app/components/ui/RoundAudioPlayer.tsx`, `app/components/layout/AppShell.tsx`
  - 신규 — 채팅 바닥 고정 훅(`app/hooks/`)
- **동작 변화**:
  - 정답이 공개돼도 채팅 피드·입력창이 계속 보이고, 그 5초 동안 채팅을 계속 칠 수 있다 **(주 수정)**
  - 정답 영상이 화면 중앙 대형에서 축소 형태로 바뀐다 — 곡을 확인하는 용도이며, 이 구간의 주된 페이로프인 정답 곡의 소리는 그대로다
  - 정답·승자·점수판이 오버레이가 아니라 라운드 정보 영역에 인라인으로 표시된다
  - `ROUND_REVEALED` 시점의 점수 갱신이 가려지지 않고 보인다
  - 정답 공개 전후로 채팅이 최신 메시지에 머문다(위로 스크롤 중이던 참가자는 예외)
- **범위 밖**: 게임 종료 결과 화면(`RoundResultOverlay`)은 손대지 않는다. 이유는 design.md의 Non-Goals 참조
- **자동 테스트 불가**: 프론트에 테스트 프레임워크가 없다. 검증은 `npm run typecheck`·`npm run build`와 수동 시나리오로 한다
