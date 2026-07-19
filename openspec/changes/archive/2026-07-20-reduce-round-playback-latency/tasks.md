## 0. 선행 조건

- [x] 0.1 `add-room-round-frontend`가 완료·아카이브되었는지 확인 — 본 변경은 그 산출물(`RoundAudioPlayer`·`RoundPanel`·`useClipPlayback`) 위에서 동작한다
- [x] 0.2 `design.md` Decision 2(버퍼링 선행)에 대한 사용자 결정을 받는다. **단, 1번 조사 결과가 선택지를 바꿀 수 있으므로 1번 이후로 미룬다** → **B안 확정**(REVEAL 구간 선전달). 결정 근거는 design.md Decision 2

## 1. 상태 역행 조사 (최우선 — 지연의 3분의 2)

- [x] 1.1 `BUFFERING(3) → UNSTARTED(-1) → BUFFERING(3)` 역행의 원인 규명. 확인된 사실: 개발·프로덕션 양쪽 재현, 그 구간 동안 react-youtube에 넘기는 props 불변(계측 검증 완료). 따라서 우리 코드의 재로드는 아님
- [x] 1.2 가설 검증 — `onReady`에서 `playVideo()`를 호출하는 시점이 YouTube 내부 로드 절차와 경합하는지. `playVideo()` 호출을 `CUED`(state 5) 관측 이후로 미뤄 비교 측정
- [x] 1.3 가설 검증 — `start`/`end` playerVar가 로드 후 재탐색을 유발하는지. playerVar 없이 `seekTo(startTimeSec)`로 대체해 비교 측정
- [x] 1.4 조사 결과를 `design.md` Decision 3에 반영. 제어 가능한 원인이면 그것만으로 얼마나 회수되는지 기록하고, 회수량에 따라 Decision 1·2의 필요성을 재평가
- [x] 1.5 **스코프 재판정** → **분리하지 않고 본 change에 유지.** 조사 회수가 0ms라 Decision 2 없이는 지연의 3분의 2가 그대로 남고, `REVEAL_MILLIS = 5_000L` 덕에 `back/` 변경이 기존 전이에 필드를 얹는 수준(신규 타이머·스케줄러 없음)으로 작아져 분리할 실익이 적다. 미결정 지적은 0.2에서 B안이 확정되며 해소됐다. (원문) — 1.4 결과를 보고 그룹 3(Decision 2)을 본 change에 남길지 후속 change로 분리할지 정한다. 산출물 검증에서 "미결정 Decision이 tasks·Impact를 미확정으로 만든다"는 지적을 받았고, 조사 결과에 따라 Decision 2 자체가 불필요해질 수 있어 분리 판단을 이 시점으로 미뤘다. 분리하기로 하면 그룹 3을 제거하고 `proposal.md`의 조건부 `back/` 영향도 함께 걷어낸 뒤, **본 change는 그룹 1·2·4만으로 완료 처리하고 Decision 2는 신규 change로 이관한다**(원문의 그룹 번호는 재번호 이전 기준이다 — 현행에서 측정 그룹은 6이다)

## 2. 플레이어 재사용 (Decision 1)

> 1번 조사가 0ms를 회수해 **확보 가능한 유일한 부트스트랩 절감(~480ms)이 됐다.** 또한 그룹 3의 선버퍼링이 성립하려면 플레이어가 라운드를 넘어 살아있어야 하므로, 이 그룹이 그룹 3의 선행 조건이기도 하다.

- [x] 2.1 `RoundPanel.tsx`의 `key={round.roundSeq}` 제거 — 라운드 전환이 리마운트를 유발하지 않게 한다
- [x] 2.2 `RoundAudioPlayer.tsx`가 트랙 변경 시 `loadVideoById({videoId, startSeconds, endSeconds})`로 곡을 교체하도록 변경. 컴포넌트 문서 주석의 "`roundSeq`를 key로 리마운트" 서술을 갱신한다
- [x] 2.3 `playsDoneRef` 반복 카운터를 트랙 교체 시 리셋
- [x] 2.4 `useClipPlayback` 계약 변경 — 현재 "호출부가 `roundSeq`를 key로 리마운트한다"는 전제로 판정을 세우므로, `roundSeq`를 인자로 받아 내부에서 재무장하도록 바꾼다. 훅 문서 주석도 함께 갱신
- [x] 2.5 수동 검증 — 3라운드 이상 연속 진행하며 (a) 매 라운드 `repeatCount`가 온전히 지켜지는지 (b) 이전 라운드의 재생 불가 안내가 남지 않는지 (c) 2라운드부터 부트스트랩 비용이 사라지는지 확인. 프론트에 테스트 프레임워크가 없어 수동 검증에 의존한다
- [x] 2.6 `npm run typecheck` 통과 — 2.4에서 `useClipPlayback`의 시그니처를 바꾸므로 특히 필요하다
- [x] 2.7 `npm run build` 통과

## 3. 선버퍼링 방식 결정 (스파이크)

> `cueVideoById`가 실제로 버퍼를 채우는지 명세에 보장이 없다(design.md Decision 2의 미해결 리스크). **그룹 4·5의 구현 방식이 이 결과에 달려 있으므로 먼저 재고 정한다.** 그룹 1에서 추측이 두 번 다 틀렸으므로 측정 없이 단정하지 않는다.

- [x] 3.1 그룹 2 완료 상태에서 `cueVideoById` 방식과 `loadVideoById`+`pauseVideo` 방식을 각각 임시로 붙여, 선버퍼링 후 `PLAYING`까지의 지연을 **동일 트랙으로** 비교 측정한다
- [x] 3.2 실제로 버퍼를 채우는 방식을 채택하고 근거와 수치를 `design.md` Decision 2의 미해결 리스크 항목에 반영한다. 둘 다 효과가 없으면 **B안 자체를 재검토**해야 하므로 그 경우 즉시 중단하고 보고한다

## 4. 버퍼링 선행 — `back/` (Decision 2)

- [x] 4.1 `RoundRevealedEvent`(`application/domain`)에 다음 라운드 재생 참조를 **nullable 필드**로 추가한다. 마지막 라운드에는 다음 트랙이 없어 의미상으로도 nullable이며, `back/CLAUDE.md`의 이벤트 직렬화 규칙(필드 추가는 nullable)에도 부합한다
- [x] 4.2 `RoundService.publishRoundRevealed`(`application`)가 다음 트랙을 채워 발행하도록 변경. `trackOrder`는 게임 시작 시 Redis에 고정되므로 추가 조회 없이 `trackIndex + 1`로 얻는다. 마지막 라운드에서는 null
- [x] 4.3 `RoundRevealedEventMessage`(`application/dto`)에 같은 필드를 반영하고, **`RoomEventListener.handleRoundRevealed`(`in`)** 가 그 필드를 채워 Redis로 발행하도록 변경한다 — 이 메시지의 유일한 생성 지점이다
- [x] 4.4 `RoundService.getSnapshot`(`application`)이 `REVEAL` 단계에서 다음 트랙을 스냅샷에 포함하도록 변경하고 `RoundSnapshotResponse`(`application/dto`)에 필드를 추가한다. `REVEAL` 중 재접속한 멤버가 이벤트를 놓쳐 혼자만 선버퍼링을 못 하면 본 변경이 없애려는 불균등이 재현된다
- [x] 4.5 통합 테스트. `@IntegrationTest` + STOMP 클라이언트로 (a) 중간 라운드 `ROUND_REVEALED`에 다음 트랙이 실리는지 (b) **마지막 라운드에서는 null인지**(경계 조건) (c) `REVEAL` 단계 스냅샷에 다음 트랙이 포함되고 `OPEN` 단계에는 없는지 검증. **기존 라운드 엔진 테스트의 구조와 패턴을 먼저 확인하고 동일한 방식으로 작성한다**(No Mocking·Testcontainers·Awaitility)
- [x] 4.6 `./gradlew test` 통과
- [x] 4.7 `./gradlew detekt` 통과

## 5. 버퍼링 선행 — `front/` (Decision 2)

- [x] 5.1 `RoundEvent.ts`의 `RoundRevealedEvent`·`RoundSnapshotResponse`에 다음 트랙 타입을 추가한다. 백엔드 DTO가 소스 오브 트루스라는 파일 상단 주석 규약을 지킨다
- [x] 5.2 `roundReducer`가 `ROUND_REVEALED`와 재접속 스냅샷 양쪽에서 다음 트랙을 상태에 보관하도록 확장
- [x] 5.3 `RoundAudioPlayer`가 다음 트랙을 받으면 **3.2에서 채택한 방식으로** 선버퍼링한다. 선버퍼링 중 소리가 나지 않아야 한다(볼륨·mute 처리 포함)
- [x] 5.4 수동 검증 — 3라운드 이상 진행하며 (a) ✅ **확인됨(314ms)** 선버퍼링된 라운드의 `ROUND_STARTED` → 재생 개시가 **500ms 이내**인지 (b) 선버퍼링된 트랙이 REVEAL 중 **소리를 내지 않는지** (c) 마지막 라운드 종료 후 오작동이 없는지 (d) REVEAL 중 새로고침해 재접속해도 선버퍼링이 동작하는지 (e) **선버퍼링이 만드는 상태 전이가 현재 라운드의 반복 카운터·재생 불가 판정을 건드리지 않는지** 확인
- [x] 5.5 `npm run typecheck` 통과
- [x] 5.6 `npm run build` 통과

## 7. 실사용 검증에서 드러난 결함 (Decision 4·5)

> 그룹 2·5 검증 중 두 가지가 제기됐다: 라운드 표기가 `13 / 9`, REVEAL에 노래가 안 들림.
> 조사 결과 표기 버그는 `roundSeq`(전이 epoch)를 라운드 번호로 오인한 것이고, 이는 본 change가
> 트랙 교체 경계로 쓰던 값이기도 해 **선버퍼링 무효화의 원인이기도 했다.** REVEAL 무음은
> 기존 스펙("클립을 정지")대로였으나 사용자 결정으로 재생하기로 바꿨다.

- [x] 7.1 `roundNumber`(= `trackIndex + 1`)를 `RoundStartedEvent`·`RoundStartedEventMessage`·`RoundSnapshotResponse`에 추가하고 `RoundService`·`RoomEventListener`가 채우도록 변경
- [x] 7.2 프론트가 표기·라운드 경계 판정에 `roundNumber`를 쓰도록 전환 — `RoundEvent.ts`·`roundReducer`·`RoundPanel`·`RoundAudioPlayer`·`useClipPlayback`. `roundSeq`는 단조 가드 전용으로 남긴다
- [x] 7.3 회귀 테스트 — 2트랙 게임에서 `roundNumber`가 1·2로 세어지고 `roundSeq`는 그보다 앞서 나감을 검증
- [x] 7.4 `ClipPlayer` 분리 — react-youtube props 동결만 담당하는 무상태 래퍼. 재생 판단은 전부 `RoundAudioPlayer`가 소유
- [x] 7.5 플레이어 2개 교대 구현 — REVEAL에 담당이 정답 곡을 처음부터 재생하고 상대가 다음 곡을 선버퍼링. REVEAL 재생의 `ENDED`는 반복 카운터를 올리지 않는다
- [x] 7.6 `room-round-ui` 스펙의 `ROUND_REVEALED` 요구사항을 MODIFIED로 재진술(클립 정지 → 정답 곡 재생)하고 `design.md`에 Decision 4·5 추가
- [x] 7.7 `./gradlew test` · `./gradlew detekt` 통과 (detekt 신규 위반 0건)
- [x] 7.8 `npm run typecheck` · `npm run build` 통과
- [x] 7.10 정답 공개 구간에 정답 곡 영상 표시 — 담당 플레이어만, `OPEN`·선버퍼링 플레이어는 계속 숨김. iframe은 DOM 이동 시 재로드되므로 오버레이의 자식으로 넣지 않고 제자리에서 CSS로만 드러낸다(`RoundRevealOverlay`가 레이아웃을 비켜준다)
- [x] 7.11 1라운드 부트스트랩 회수 (Decision 6) — `ClipPlayer`를 빈 `videoId`로 생성하고 마운트를 `RoundPanel` 밖 방 화면으로 올려 게임 시작 전에 준비시킨다. `start`/`end` playerVar 제거로 첫 라운드 특례도 함께 사라진다. 재생 상태 소유권을 `RoundPanel` → 방 화면으로 이동
- [x] 7.12 `room-round-ui` 스펙의 초기화 요구사항 재진술 — 종전 "2라운드가 1라운드보다 부트스트랩의 70% 이상 빠르다" 시나리오는 1라운드도 부트스트랩을 치르지 않게 되어 성립하지 않는다. `design.md`에 Decision 6 추가
- [x] 7.9 수동 검증 — 3라운드 이상 진행하며 (a) 라운드 표기가 `N / 총계` 범위 안인지 (b) REVEAL마다 정답 곡이 들리는지 (c) **타임아웃 라운드에서도 들리는지** (d) REVEAL 중 다음 곡이 새지 않는지 (e) 두 소리가 겹치지 않는지 (f) REVEAL에만 영상이 보이고 `OPEN`에는 안 보이는지 (g) 영상이 다음 곡이 아니라 **방금 공개된 정답 곡**인지 (h) ✅ **확인됨** 빈 `videoId`로도 `onReady`가 발화하는지 (i) 1라운드 재생 개시가 이전보다 빨라졌는지

## 6. 측정 및 기록

- [x] 6.1 변경 전후 지연을 동일 환경에서 측정 — 프로덕션 빌드(`vite preview`), 로컬 백엔드 기준. `ROUND_STARTED` 수신 → `PLAYING` 경과 시간. **트랙을 반드시 고정한다** — 그룹 1 조사에서 변형마다 트랙이 달라 시간 비교가 무의미해진 전례가 있다
- [x] 6.2 측정값을 `design.md`에 기록하고, 실측에 비추어 스펙의 두 임계(부트스트랩 70%·선버퍼링 500ms)가 타당한지 재검토한다. 기준선은 1821ms(내역: 부트스트랩 458ms + 로드·버퍼링 1363ms)
- [x] 6.3 임시 계측 코드 제거 (6.1 측정 완료 후 수행)
    - [x] 3.1 스파이크 산출물 제거 — `front/app/routes/YtSpikeView.tsx` 삭제 + `routes.ts`의 `ytspike` 등록 해제 (3.2로 역할 종료)
    - [x] `root.tsx`의 호출부 제거 (그룹 2에서 변형 코드를 걷어내며 이미 처리)
    - [x] `front/app/utils/ytLatencyExperiment.ts` 삭제 + `RoundAudioPlayer`의 `logYtTiming`·`ytStateName` 호출부 제거, 조사용 `console.log` 잔존 확인
- [x] 6.4 `front/.env.production.local`은 로컬 검증 전용이므로 gitignore되어 커밋되지 않았는지 확인한다. `vite preview`로 로컬 백엔드를 붙이려면 이 파일이 필요하다는 점을 `front/CLAUDE.md`의 "알려진 함정"에 기록한다
- [x] 6.5 `npm run typecheck` 통과 — 2.4에서 `useClipPlayback`의 시그니처를 바꾸므로 특히 필요하다
- [x] 6.6 `npm run build` 통과
