## Context

`add-game-round-engine`가 완성한 **서버 주도 라운드 엔진**을 방 화면에 반영한다. 서버는 라운드 시작·마감·공개 시각을 소유하고, 프론트는 그 이벤트를 받아 화면을 그리는 **종속 단말**이다. 소비하는 프로토콜은 이미 확정돼 있다:

```
클라이언트 → 서버 (기존 destination, 신규 없음)          서버 → /topic/rooms/{id} (STOMP)
──────────────────────────────────────────            ──────────────────────────────────────
/app/rooms/start   (방장)                              JOINED · LEFT · SESSION_REPLACED  (기존)
/app/rooms/end     (방장)                              CHAT · STARTED · ENDED            (기존)
/app/rooms/chat  ← 정답 추측도 이 채널              ★  ROUND_STARTED   answer-stripped 재생 참조
/app/rooms/leave                                    ★  ROUND_REVEALED  정답·승자·점수판

재접속 복원:  GET /rooms/{roomId} → RoomDetailResponse.round (phase 게이팅 스냅샷)
```

메시지 스키마(소비 대상, 서버가 `@JsonTypeInfo(property="type")`로 직렬화):

```ts
ROUND_STARTED  { type, roomId, roundSeq, totalRounds, deadlineAt /*epoch ms*/,
                 embedId, startTimeSec, endTimeSec, repeatCount, playerId:null, nickname:null }
ROUND_REVEALED { type, roomId, roundSeq, winnerId:number|null, title,
                 scores:[{playerId, score}], playerId:null, nickname:null }
round(snapshot){ phase:"OPEN"|"REVEAL"|"ENDED", roundSeq, totalRounds, deadlineAt,
                 currentTrack:{embedId,startTimeSec,endTimeSec,repeatCount},
                 title:string|null /*OPEN이면 null*/, winnerId:number|null, scores:[{playerId,score}] }
```

## Goals / Non-Goals

**Goals**
- `PLAYING` 화면을 실제 게임 화면으로 재구성(클립 재생·카운트다운·점수판·추측 채팅·정답 공개·결과)
- 라운드 상태를 테스트 가능한 순수 리듀서 상태 머신으로 분리
- 재접속/새로고침 시 스냅샷으로 화면 복원
- 오디오 자동재생을 제스처 게이트로 안정화

**Non-Goals**
- 백엔드·프로토콜 변경(소비만)
- 클라이언트 간 재생 시각 동기화(버퍼 핸드셰이크)
- 정답 근접 피드백·타이핑 표시 등 게임성 강화 UX

## 상태 머신

`RoomStatus`(PENDING/ACTIVE/PLAYING)는 멤버십 경계로 그대로 두고, 라운드는 `PLAYING` 안의 휘발성 하위 상태로 새로 그린다.

```
status: ACTIVE ──STARTED──► PLAYING ─────────────────────────► GAME_ENDED ──► ACTIVE(로비)
                              │                                     ▲   (결과 오버레이)
                              ▼                                     │
   idle ─ROUND_STARTED─► ● OPEN ─ROUND_REVEALED─► ○ REVEAL ─ROUND_STARTED(다음)─┐
                           │  clip 자동재생          │  clip 정지               │
                           │  카운트다운→deadlineAt   │  정답·승자·점수 공개      └─► ● OPEN
                           └───────────────────── GAME_ENDED(마지막 REVEAL 후) ──────┘

  ● OPEN   = 추측 중: 클립 재생, 채팅=추측, 카운트다운 진행
  ○ REVEAL = 공개: 정규 title·승자·점수판, 클립 정지, deadlineAt 없음 → 다음 이벤트까지 대기
```

- **REVEAL엔 `deadlineAt`가 없다** → 라이브 클라이언트는 다음 `ROUND_STARTED`까지 REVEAL을 유지(리듀서에 REVEAL 타이머 없음). 재접속 스냅샷만 REVEAL의 deadline을 안다
- **CHAT은 REVEAL보다 먼저 온다**(`RoomStompController.chat`: 채팅 방송 → 그다음 판정) → 우승 추측이 피드에 뜬 뒤 REVEAL이 확정

## Decision 1 — 라운드 상태는 순수 useReducer 슬라이스, 구독은 useRoomSubscription이 소유

**결정**: `roundReducer`(순수 함수) + `RoundState`를 별도 모듈로 두고, 기존 단일 STOMP 구독(`useRoomSubscription`)이 `handleEvent`에서 라운드 이벤트를 그 리듀서로 `dispatch`한다.

**왜 두 번째 구독을 만들지 않나**: `/topic/rooms/{id}` 구독은 하나뿐이고, StrictMode 이중 마운트 방어·자발 퇴장 타임아웃·세션 교체 처리 등 미묘한 생명주기 로직이 `useRoomSubscription.ts:173-211`에 응집돼 있다. 두 번째 구독은 이 로직을 복제하거나 깨뜨린다. 그래서 **전송(구독)은 한 곳, 상태(리듀서)는 분리**한다.

```
useRoomSubscription (전송 + room 슬라이스: players/messages/status — 기존 유지)
        │  handleEvent(event)
        ├─ JOINED/LEFT/CHAT/STARTED/ENDED/SESSION_REPLACED … (기존 그대로)
        └─ ROUND_STARTED / ROUND_REVEALED ──dispatch──► roundReducer(순수, 별도 모듈)
                                                          │
   HYDRATE(fetchRoomDetail().round) ──dispatch──────────►┤  RoundState
                                                          └─► RoomView가 round로 렌더
```

**리듀서 순수성**: `Date.now()`를 리듀서에 넣지 않는다(`front/CLAUDE.md` 성능·순수성 원칙, 그리고 테스트 용이성). 카운트다운은 리듀서 밖 표시 로직(`useEffect` + interval)이 `state.deadlineAt`만 읽어 계산한다. 리듀서는 서버 이벤트에만 전이한다.

**액션**: `GAME_STARTED`(scores 0으로 초기화 or 빈 상태) · `ROUND_STARTED` · `ROUND_REVEALED`(scores·title·winnerId 갱신) · `GAME_ENDED`(phase=ended, **scores는 직전 값 유지**) · `HYDRATE`(스냅샷 전체 시드) · `RESET`(방 이탈).

**대안 각하**: (A) `useRoomSubscription`에 useState 나열 → 훅 200줄 초과·관심사 혼재로 `front/CLAUDE.md` 위반. (B) 별도 구독 훅 → 위 생명주기 로직 복제 리스크.

## Decision 2 — 오디오 자동재생 제스처 게이트

**문제**: 브라우저 자동재생 정책은 사용자 제스처 없는 오디오 재생을 차단한다. 방장은 "시작하기"를 클릭(제스처)하지만 **나머지 참가자는 게임 시작 시점에 아무것도 클릭하지 않는다** → 첫 라운드가 무음이 될 수 있다.

**결정**: `PLAYING` 진입(및 재접속으로 `PLAYING` 복원) 시 `armed`가 아니면 **"게임 참여 / 소리 켜기" 1회 오버레이**를 띄운다(기존 `isDeactivated` 전면 오버레이 패턴 재사용). 클릭이 곧 제스처가 되어 (a) 자동재생 정책을 통과시키고 (b) 볼륨을 세팅하며 (c) 이후 라운드는 조용히 자동재생한다.

- **muted 자동재생은 각하**: 노래 맞히기라 음소거 재생은 무의미. 제스처가 필수
- **일관성**: 방장도 동일 게이트를 거치게 해 분기 최소화(방장의 start 클릭으로 auto-arm하는 최적화는 선택). 재접속·새 탭도 동일 게이트
- **arming 구현**: 게이트 클릭 시 숨은 YouTube 플레이어에 `playVideo()`(또는 무음 워밍업)로 제스처를 소비. 상태 `armed=true`는 라운드 리듀서 밖의 UI 상태(방 세션 동안 유지)

## Decision 3 — 정답 은닉하지 않음, REVEAL의 title이 정규 표기

**사실**: 배포 코드 `RoomStompController.chat`은 정답 여부와 무관하게 **채팅 원문을 항상 먼저 방송**한 뒤 `submitAnswer`로 판정한다(주석: "정답이면 … 원문 채팅도 그대로 방송한다"). 이는 아카이브된 백엔드 스펙의 "정답은 방송 안 함"과 어긋나며, **소스 오브 트루스는 코드**다.

**결정**: 프론트는 정답 은닉을 시도하지 않는다.
- 우승 추측은 추측 피드(채팅)에 그대로 뜬다
- 뒤이어 오는 `ROUND_REVEALED`가 **정규 정답(`title`)·승자(`winnerId`)·갱신 점수판**을 확정 공개한다
- 표시는 REVEAL의 `title`을 정규 표기로 쓴다(추측은 공백/대소문자 정규화로 맞았을 뿐 표기가 다를 수 있음)
- 타임아웃(`winnerId=null`)은 "시간 초과 · 정답은 {title}"로 표시

## Decision 4 — 최종 점수는 마지막 REVEAL에서 sticky 보존, 결과 화면

**문제**: `ENDED`(`GameEndedEventMessage`)에는 점수판이 없다 — 최종 점수는 직전 `ROUND_REVEALED`가 전달했다.

**결정**: 리듀서는 마지막으로 받은 `scores`를 `GAME_ENDED`를 가로질러 유지한다. `GAME_ENDED` 수신 시 그 sticky `scores`로 **결과(순위) 오버레이**를 그린다.

```
… 마지막 라운드 OPEN → ROUND_REVEALED(scores=최종) ──┐  리듀서: scores 저장
                                                     ▼
                              (REVEAL 5초 후) GAME_ENDED(점수 없음) → 결과 오버레이(sticky scores)
```

- **순위 렌더**: `scores`(id-only)를 `players`와 조인해 닉네임 표기, 내림차순 정렬, 우승자(최고점 또는 마지막 `winnerId` 기준)는 하이라이트
- **퇴장자 폴백**: 게임 중 퇴장자는 서버가 점수판에서 제거하므로 대개 조인 실패는 없으나, 방어적으로 미매칭 id는 "(퇴장)"으로 표기
- **닫기**: `status`는 `ENDED`와 함께 이미 `ACTIVE`로 돌아온다(서버 `endByEngine`). 오버레이는 사용자가 "방으로"로 닫으면 로비 채팅으로 복귀

## Decision 5 — 재접속 하이드레이션

**결정**: 최초 `fetchRoomDetail(roomId)` 응답의 `round`가 있으면(그리고 `status==PLAYING`) `HYDRATE` 액션으로 리듀서를 시드한다.

- `phase==OPEN` → 클립 재생 + `deadlineAt` 카운트다운 재개(title은 서버가 null로 보냄 → 정답 비노출 유지)
- `phase==REVEAL` → 정답·승자·점수판 공개 상태로 복원
- 오디오는 Decision 2 게이트를 거쳐 재개(재접속도 제스처 필요)
- **주의**: 하이드레이션과 라이브 이벤트가 경합할 수 있으므로, `roundSeq`가 더 낮은 늦은 하이드레이션은 무시(리듀서에서 `roundSeq` 단조 증가 가드)

## Decision 6 — 재생 불가 트랙은 "고치지 않고" 감지해 알린다

**문제**: 일부 YouTube 트랙은 임베드에서 재생이 거부된다. 실제 사례 `VyvhvlYvRnc`(YOASOBI「優しい彗星」Official MV)는 "다음 콘텐츠에 자살 또는 자해 관련 주제가 포함되어 있을 수 있습니다" 경고로 자동재생되지 않는다.

**실측**(`youtube.com/embed/{id}` + `Referer`, `embedded_player_response.previewPlayabilityStatus`):

```
  VyvhvlYvRnc  →  CONTENT_CHECK_REQUIRED     ← 문제 트랙
  dQw4w9WgXcQ  →  OK                          ← 대조군
  9bZkp7q19f0  →  OK                          ← 대조군
```

**결정 A — 임베드 안에서 재생시키는 것은 불가능하다(설계가 아니라 사실)**. 같은 응답의 `proceedButton`은 다음과 같다:

```json
"proceedButton": { "text": { "simpleText": "YouTube에서 보기" },
  "urlEndpoint": { "url": "http://www.youtube.com/watch?v=VyvhvlYvRnc",
                   "target": "TARGET_NEW_WINDOW" } }
```

임베드에는 **인-플레이스 동의 버튼이 없다**. YouTube가 제공하는 유일한 경로는 youtube.com 새 창으로 이탈하는 링크이며, 이는 정답(제목·영상)을 통째로 노출하고 사용자를 게임에서 이탈시킨다. 따라서 오버레이·클리핑으로 동의 버튼만 노출시키는 설계는 **대상이 존재하지 않아** 성립하지 않는다.

- **참고**: 인터스티셜 화면 자체는 제목을 노출하지 않는다(응답 내 제목 문자열 0건, 썸네일은 제네릭 아이콘). 정답 유출 우려는 기우였으나 위 이유로 무의미하다
- **`showinfo: 0`은 무효**다(deprecated·무시). 공식 문서: "the channel avatar and video title **will always display before playback begins**". 향후 어떤 이유로든 플레이어를 화면에 드러내는 설계는 제목 노출을 전제해야 한다
- **우회는 각하**: 인터스티셜 억제(InnerTube `contentCheckOk`·스트림 추출)는 (a) 자살·자해 안전장치의 무력화이고, (b) API ToS 16.3("YouTube API 서비스를 통하지 않은 방식으로 시청각 콘텐츠를 이용할 권리는 부여되지 않는다")에 저촉되며, (c) 백엔드가 EC2(데이터센터 IP)라 봇 탐지로 **동작하지도 않는다**. 세 이유 중 어느 하나만으로도 각하

**결정 B — 해결은 트랙 교체이며 이는 기존 플레이리스트 편집 UI가 이미 담당한다**. 경고는 곡이 아니라 **특정 업로드**에 붙는다. 같은 곡의 대체 업로드 7건을 확인한 결과 전부 `OK`였고, 노래 맞히기 용도로는 `iykAM_McguQ`(YOASOBI - Topic, 음반사 제공 공식 오디오)가 적합하다. 따라서 본 change는 트랙 교체 기능을 새로 만들지 않고 **문제를 사용자에게 드러내는 것까지만** 책임진다.

**결정 C — 탐지는 오류 이벤트가 아니라 재생 개시 관찰로 한다**. `CONTENT_CHECK_REQUIRED`에서 IFrame API `onError`가 발생하는지는 **미검증**이다(문서화된 코드 2·5·100·101·150에 콘텐츠 체크가 없으나, 프로브 중 관측된 에러 153처럼 YouTube는 문서 외 내부 코드를 사용한다). 이 불확실성을 설계로 흡수한다:

```
  armed && playVideo()
        │
        ├─ onAutoplayBlocked ──────► 게이트 문제. 재생 불가 아님 → 소리 켜기 재유도
        ├─ onError(101/150/…) ─────► 재생 불가 확정 (즉시 판정)
        ├─ state 3 (BUFFERING) ────► ★ 플레이어 생존 신호 → 판정 해제
        ├─ state 1 (PLAYING) ──────► 정상
        └─ 위 어느 것도 없이 경과 ──► 재생 불가 판정
```

**왜 state 3이 핵심인가**: 단순 타임아웃은 느린 네트워크를 재생 불가로 오판한다. `CONTENT_CHECK_REQUIRED` 트랙은 재생 시도 자체가 거부되어 **BUFFERING에 도달하지 않는** 반면, 느린 네트워크는 BUFFERING에 빠르게 진입한 뒤 PLAYING이 늦다. 따라서 "BUFFERING을 봤으면 생존"을 게이트로 두면 판정이 타임아웃 값 튜닝에 의존하지 않는다. `onError`가 오면 즉시, 안 오면 관찰이 잡으므로 어느 쪽이든 동작한다.

**판정 시간 = 3초**. 서버가 `deadlineAt = clipLen × repeatCount + 2s`로 잡으므로 짧은 클립(15s×1회 ≈ 17s)에서도 안내가 약 14초간 보인다. state 3 게이트가 오탐을 막으므로 공격적으로 잡아도 안전하다 — 정상 재생은 보통 1초 내에 BUFFERING에 도달해 판정이 해제되고, 느린 네트워크도 BUFFERING만 찍히면 얼마가 걸리든 재생 불가로 판정되지 않는다. 즉 3초는 "재생 완료까지의 여유"가 아니라 **"플레이어가 살아있다는 신호가 오기까지의 여유"**다. 6.5에서 실측 후 조정할 수 있다.

**배치**: 탐지 로직은 `RoundAudioPlayer` 내부가 아니라 별도 훅으로 분리한다(`front/CLAUDE.md` "비즈니스 로직은 커스텀 훅으로 분리"). 훅이 재생 상태를 반환하면 `RoundPanel`이 안내를 렌더한다. 훅 경계는 **같은 조용한 실패를 겪는 `MusicPlayer`(플레이리스트 미리듣기)에서도 재사용 가능**하게 열어두되, 그 적용 자체는 본 변경의 영향 범위(`room` 화면) 밖이라 후속 과제로 둔다 — 작성자가 편집 화면에서 문제를 발견하면 결정 B의 교체로 자연히 이어진다.

**스코프 밖**: 서버 신고·라운드 스킵/재추첨·트랙 블랙리스트, 그리고 `MusicPlayer`(플레이리스트 편집 화면) 적용. 훅이 상태를 반환하는 구조라 후속 change에서 호출부만 확장하면 된다.

## 표시용 카운트다운 & 재생

- **카운트다운**: `remaining = max(0, deadlineAt − Date.now())`. 0 도달 시 "판정 중…"으로 바꾸고 **로컬 시계로 전이하지 않는다** — 권위는 서버 sweeper의 `ROUND_REVEALED`. clock skew는 표시만 어긋나고 라운드 정확성과 무관(서버가 `serverNow`를 주면 de-skew 가능하나 범위 외)
- **repeatCount 재생**: 기존 `MusicPlayer`는 단발 재생(`end`에서 멈춤)이라 반복이 없다. `RoundAudioPlayer`는 YouTube `ended`(state 0)에서 `seekTo(startTimeSec)`로 `repeatCount`회까지 반복 후 정지. `deadlineAt`은 서버가 `clipLen × repeatCount + 2s`로 이미 예산해 둠. **재생 소진 시각과 라운드 종료는 분리** — 재생이 먼저 끝나도 라운드는 REVEAL로만 닫힌다

## 화면 구성 (RoomView PLAYING)

```
현재(RoomView.tsx:156)              변경 후
┌──────────────────────┐          ┌───────────────────────────────────┐
│   ▶ 게임 진행 중      │          │ Round 3 / 10           ⏱ 0:07      │ 헤더
│ 곧 라운드가 시작…     │   ──►    ├───────────────────────────────────┤
│  (채팅 숨김)          │          │ [hidden RoundAudioPlayer]         │
└──────────────────────┘          │ 🏆 점수판 (닉네임 · 점수)          │
                                  ├───────────────────────────────────┤
                                  │ 추측 피드 (채팅 재노출, 스크롤)     │
                                  │ > 입력창  ← 추측이 곧 채팅          │
                                  └───────────────────────────────────┘
  오버레이: 제스처 게이트(진입 시) · REVEAL 정답 · 결과(순위, 종료 시)
```

## Risks / Trade-offs

| 리스크 | 완화 |
|--------|------|
| 오디오 자동재생 차단(비-방장 무음) | Decision 2 제스처 게이트 |
| clock skew로 카운트다운 오차 | 표시용일 뿐, 서버 REVEAL이 권위. 0에서 "판정 중…" |
| repeatCount 반복 미구현(기존 플레이어) | `RoundAudioPlayer` 신규(ended→seekTo 반복) |
| ENDED에 점수 없음 | 마지막 REVEAL scores sticky 보존(Decision 4) |
| 재접속 시 라운드 재시작 오류 | 스냅샷 HYDRATE + `roundSeq` 단조 가드 |
| id-only 점수판, 퇴장 승자 | `players` 조인, 미매칭 "(퇴장)" 폴백 |
| 임베드 재생 거부 트랙(콘텐츠 경고 등)으로 무음 라운드 | Decision 6 — 감지해 안내(고치지는 못함). 해결은 트랙 교체 |
| 느린 네트워크를 재생 불가로 오판 | state 3(BUFFERING) 생존 게이트로 타임아웃 의존 제거 |

## Migration / Rollout

- DB·인프라 변경 없음. 프론트 단일 PR, `git revert`로 완전 롤백
- 서버 프로토콜은 이미 배포됨 — 본 변경 미적용 상태에선 `PLAYING`이 플레이스홀더로 남을 뿐(현행). 점진 배포 안전

## Open Questions

- 채팅 시스템 메시지에 라운드 시작/공개 안내를 추가할지, 아니면 전용 라운드 UI로만 표현하고 피드는 순수 추측만 둘지(최소안: 후자)
- 결과 오버레이의 표시 시간/닫기 UX(자동 닫힘 vs 수동) — 기본은 수동 "방으로"
- **(Decision 6, 미검증)** `CONTENT_CHECK_REQUIRED`에서 IFrame API `onError`가 실제로 발생하는가 — 발생 여부와 무관하게 동작하도록 설계했으나, 6.3 수동 검증에서 실측하면 판정 경로를 단순화할 여지가 있다
- **(Decision 6, 미검증)** 이 상태가 클라이언트마다 동일한가 — YouTube 로그인·연령 인증 상태나 지역에 따라 일부 참가자만 재생될 경우 "모두에게 공평하게 무음"이 아니라 **불공정 라운드**가 되며, 안내만으로는 덮이지 않는다. 사실이면 후속 change에서 서버 신고·라운드 무효화가 필요하다
