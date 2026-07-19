## Why

`add-room-round-frontend`로 라운드 UI를 구현하는 중, 볼륨 이상 현상을 조사하다 **별개의 실측 문제**가 드러났다. 라운드가 시작되고 **실제 소리가 나기까지 1.8초**가 걸린다.

프로덕션 빌드 + localhost 백엔드에서 `RoundAudioPlayer` 마운트 시점을 0으로 잡고 계측한 값이다:

```
+1ms      MOUNT
+458ms    onReady            ← 아이프레임·YouTube 플레이어 부트스트랩
+579ms    UNSTARTED
+616ms    BUFFERING
+856ms    UNSTARTED          ← 상태가 뒤로 감 (원인 미규명)
+926ms    BUFFERING
+1821ms   PLAYING            ← 소리 시작
+26791ms  ENDED              ← 클립 25초(start=30/end=55) 정상 재생
```

이것이 문제인 이유는 **라운드 시계의 주인이 서버**이기 때문이다. `add-room-round-frontend`가 확립한 대로 프론트는 서버가 준 `deadlineAt`까지 표시용 카운트다운만 돌리는 종속 단말이다. 그래서 시계는 `ROUND_STARTED` 발행 즉시 흐르기 시작하는데, 소리는 각 클라이언트가 아이프레임을 부트스트랩한 뒤에야 난다.

```
 서버:  ROUND_STARTED 발행 ─▶ deadlineAt 시계 시작 ──────────────────▶ 마감
 클라A: (빠른 회선)      수신 ─▶ 부트스트랩 ─▶ ♪ ────────────────────▶ 마감
 클라B: (느린 회선)      수신 ─▶ 부트스트랩 ───────▶ ♪ ─────────────▶ 마감
                                    ◀── B가 덜 듣는 시간 ──▶
```

결과는 두 가지다:

- **들을 수 있는 시간의 손실** — 위 실측에서 25초 클립 중 1.8초, 약 7%가 시작 지연으로 사라진다
- **공정성 불균등** — 손실량이 각자의 네트워크·기기 성능에 비례한다. 노래 맞히기는 **먼저 맞히는 사람이 이기는** 게임이라 시작 시점 차이가 곧 승패 차이다

localhost 실측이 1.8초라는 점이 특히 중요하다. 네트워크 왕복이 사실상 0인 조건에서 나온 값이므로, 실제 사용자 환경에서는 이보다 나쁘다.

## What Changes

지연은 두 덩어리로 나뉘고, 각각 해결 난이도가 다르다.

```
 458ms   플레이어 부트스트랩   ← 플레이어 재사용으로 회수 가능 (2라운드부터)
 1363ms  영상 로드·버퍼링      ← 선지연 로딩 없이는 회수 불가. 그런데 정답이 샌다
 ─────
 1821ms
```

### 1. 플레이어 재사용 (~458ms 회수, 저위험)

현재 `RoundPanel.tsx`는 `key={round.roundSeq}`로 라운드마다 `RoundAudioPlayer`를 통째로 교체한다. 그래서 아이프레임과 YouTube 플레이어가 **매 라운드 처음부터** 만들어진다.

플레이어를 방 세션 동안 유지하고 라운드 전환 시 `loadVideoById({videoId, startSeconds, endSeconds})`로 곡만 갈아끼우면 이 비용이 첫 라운드에만 든다.

대가: 지금 `key` 리마운트가 공짜로 주던 초기화를 직접 관리해야 한다 — `playsDoneRef` 반복 카운터, `useClipPlayback`의 재생 불가 판정. 현재 `RoundAudioPlayer`의 문서 주석이 이 리마운트 의존을 명시하고 있으므로 함께 갱신해야 한다.

### 2. 버퍼링 선행 (~1363ms 회수, B안 확정)

이쪽이 지연의 대부분인데, **정답 노출과 정면으로 충돌한다.** 다음 라운드 트랙을 미리 버퍼링하려면 클라이언트가 `embedId`를 미리 받아야 하고, `embedId`가 곧 정답이다. 브라우저에서 들여다보면 그대로 보인다.

```
 지연 최소화 ◀──────────────────────────────▶ 부정행위 방지
 다음 곡 선행 로드                        라운드 시작 시점에 전달
 = 정답 사전 노출                          = 매 라운드 1.4초 손실
```

**결정: B안 — REVEAL 구간에 다음 트랙을 선전달한다.** `ROUND_REVEALED`에 다음 라운드의 재생 참조를 실어 보내고, 클라이언트는 REVEAL 동안 미리 버퍼링한다.

이 트레이드오프는 애초에 보이던 것보다 작았다. **`ROUND_STARTED`는 이미 `embedId`를 싣고 있다** — 서버가 감추는 것은 `title` 문자열뿐이고 `embedId`는 조회하면 곡명이 나온다. 즉 정답은 현행에서도 라운드 시작부터 클라이언트에 있으며, B안은 그 기지 구간을 REVEAL 길이만큼 앞당길 뿐 새로운 노출을 만들지 않는다.

전달 시점을 REVEAL로 잡은 이유는 **라운드 사이에 이미 5초 REVEAL 구간이 있어서**다(`REVEAL_MILLIS = 5_000L`). 필요한 선버퍼링은 1.2초이므로 새 타이머·스케줄러 없이 기존 전이에 얹는 것으로 족하다. 근거와 미해결 기술 리스크는 `design.md` Decision 2에 있다.

### 3. 미규명 현상 규명 (선행 조사 — 완료, 회수 0ms)

계측에 `BUFFERING(3) → UNSTARTED(-1) → BUFFERING(3)`으로 **상태가 뒤로 가는** 전이가 찍혀, 우리가 제어 가능한 원인인지 조사했다. **결론: 아니다.** 네 변형을 비교 측정한 결과 `playVideo()` 호출 시점(가설 1)과 `start`/`end` playerVar(가설 2) 모두 반증됐다 — `playVideo()`를 호출하지 않은 변형에서도 역행이 그대로 발생했다. 상세는 `design.md` Decision 3의 "조사 결과"에 있다.

따라서 이 구간은 회수 불가이며, 1번과 2번이 유일한 개선 경로다.

<details>
<summary>조사 착수 시점의 서술 (기록 보존)</summary>

계측에 `BUFFERING(3) → UNSTARTED(-1) → BUFFERING(3)`으로 **상태가 뒤로 가는** 전이가 찍혔다. 개발·프로덕션 빌드 양쪽에서 재현되므로 StrictMode 이중 마운트와는 무관하다. 이 구간이 약 1.2초로 지연의 큰 몫이다.

`RoundAudioPlayer`가 react-youtube에 넘기는 props는 이 사이에 바뀌지 않는 것으로 확인됐다(props 스냅샷 계측으로 검증). 따라서 우리 코드가 영상을 다시 로드시킨 것은 아니다. 원인이 규명되면 2번보다 **훨씬 싸게** 상당량을 회수할 가능성이 있으므로, 구현 순서상 이 조사가 먼저다.

</details>

## Impact

- **영향 스펙**
  - `room-round-ui` (`add-room-round-frontend`가 도입 중인 capability) — 클라이언트 측 재생 시작 지연·선버퍼링 요구사항 추가
  - `room-game-session` (MODIFIED) — `ROUND_REVEALED`·재접속 스냅샷이 다음 라운드 재생 참조를 싣는다는 **서버 계약 변경**. 정답 노출 요구사항의 열거가 달라지므로 해당 요구사항도 함께 재진술한다
- **영향 코드 (`front/`)**: `app/components/ui/RoundAudioPlayer.tsx`, `app/components/ui/RoundPanel.tsx`, `app/hooks/useClipPlayback.ts`, `app/hooks/roundReducer.ts`, `app/utils/RoundEvent.ts`
- **영향 코드 (`back/`)**: `room` 모듈
  - `application/domain/RoundRevealedEvent.kt` — 다음 트랙 필드 추가 (도메인 이벤트)
  - `application/dto/RoundRevealedEventMessage.kt` — STOMP 전파 DTO에 같은 필드 반영
  - `application/RoundService.kt` — REVEAL 발행 시 다음 트랙 동봉, `getSnapshot`이 REVEAL 단계에서 다음 트랙 포함
  - `application/dto/RoundSnapshotResponse.kt` — 재접속 스냅샷에 같은 필드 추가
  - `in/RoomEventListener.kt` — **인바운드 어댑터**. `handleRoundRevealed`가 `RoundRevealedEventMessage`를 생성해 Redis로 발행하는 유일 지점이므로 필드 추가 시 함께 변경된다
  - 라운드 전이 로직(`RoundStateStore`의 Lua CAS)과 sweeper는 **변경하지 않는다** — 기존 REVEAL 구간에 얹기만 한다
- **이벤트 직렬화**: `RoundRevealedEvent`에 필드를 추가하므로 `back/CLAUDE.md`의 규칙대로 **nullable로 추가**한다(마지막 라운드에는 다음 트랙이 없어 의미상으로도 nullable이다). 필드 삭제·이름 변경이 아니므로 미완료 publication 호환이 유지된다
- **선행 조건**: `add-room-round-frontend`가 먼저 완료·아카이브되어야 한다. 본 변경은 그 산출물 위에서 동작한다
- **내부 순서 의존**: 선버퍼링(그룹 3)은 플레이어가 라운드를 넘어 살아있어야 성립하므로 **플레이어 재사용(그룹 2)이 선행**이다
