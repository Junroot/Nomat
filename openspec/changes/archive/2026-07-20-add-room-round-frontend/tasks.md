## 1. 프로토콜 타입 (front/)

- [x] 1.1 `app/utils/RoomDetailResponse.ts`에 `round?: RoundSnapshotResponse` 추가 — `phase`("OPEN"|"REVEAL"|"ENDED")·`roundSeq`·`totalRounds`·`deadlineAt`·`currentTrack`(embedId·startTimeSec·endTimeSec·repeatCount)·`title:string|null`·`winnerId:number|null`·`scores:{playerId:number,score:number}[]`
- [x] 1.2 `ROUND_STARTED`·`ROUND_REVEALED` 이벤트 타입 정의(`useRoomSubscription`의 `RoomEventMessage` 유니온 확장 또는 `app/utils/RoundEvent.ts`) — 두 이벤트는 `playerId`/`nickname`이 null

## 2. 라운드 상태 머신 (front/)

- [x] 2.1 순수 리듀서 `app/hooks/roundReducer.ts` 신규 — `RoundState`(서버 7필드 + `armed`는 UI 상태로 분리) + `roundReducer(state, action)`. 액션: `GAME_STARTED`·`ROUND_STARTED`·`ROUND_REVEALED`·`GAME_ENDED`·`HYDRATE`·`RESET`. **`Date.now()` 사용 금지**(순수성), 마지막 `scores`는 `GAME_ENDED`를 가로질러 sticky 유지, `roundSeq` 단조 증가 가드
- [x] 2.2 `app/hooks/useRoomSubscription.ts`의 `handleEvent`에 `ROUND_STARTED`/`ROUND_REVEALED` 분기 추가 → `dispatch`. 단일 구독·생명주기 로직(173-211행)은 변경하지 않는다
- [x] 2.3 최초 `fetchRoomDetail` 응답의 `round`가 있으면 `HYDRATE` dispatch(재접속 복원). 훅 반환에 `round` 상태 추가

## 3. 게임용 오디오 플레이어 (front/)

- [x] 3.1 `app/components/ui/RoundAudioPlayer.tsx` 신규 — 컨트롤 없는 hidden iframe(기존 `MusicPlayer`의 `hidden` 래퍼 패턴 재사용), `currentTrack` 자동재생, YouTube `ended`(state 0)에서 `seekTo(startTimeSec)`로 `repeatCount`회 반복 후 정지, `roundSeq`를 key로 트랙 교체 시 리마운트
- [x] 3.2 arming 훅/상태 — 제스처 게이트 클릭 시 `playVideo()`로 자동재생 정책 통과, `armed`는 방 세션 동안 유지되는 UI 상태
- [x] 3.3 재생 상태 탐지 훅 신규(`app/hooks/useClipPlayback.ts` 등) — 재생 상태(`idle`/`playing`/`blocked`/`unplayable`)를 반환. 판정 규칙(design.md Decision 6): `onAutoplayBlocked`→`blocked`, `onError`→즉시 `unplayable`, **state 3(BUFFERING) 관측 시 판정 해제**(느린 네트워크 오탐 방지), state 1→`playing`, 그 외 **3초** 경과→`unplayable`
- [x] 3.4 `RoundAudioPlayer`가 3.3 훅을 사용하도록 변경 — 탐지 로직을 컴포넌트에 인라인하지 않는다(`front/CLAUDE.md` 로직-훅 분리). 재생 상태를 상위로 전달

## 4. PLAYING 화면 재구성 (front/)

- [x] 4.1 `app/routes/RoomView.tsx`의 `status === "PLAYING"` 플레이스홀더(156-161행)를 라운드 화면으로 교체 — 라운드 헤더(`roundSeq / totalRounds`), 표시용 카운트다운(`deadlineAt` 기준, 0에서 "판정 중…", 로컬 시계로 전이 안 함), 점수판(`scores`↔`players` 조인, 미매칭 "(퇴장)"), hidden `RoundAudioPlayer`
- [x] 4.2 게임 중 채팅 입력·피드 재노출(정답 추측 채널) — 기존 `sendMessage` 재사용, 별도 정답 입력 UI 없음
- [x] 4.3 `REVEAL` 정답 오버레이 — 정규 `title`·승자 하이라이트, `winnerId=null`이면 "시간 초과 · 정답은 {title}"
- [x] 4.4 오디오 제스처 게이트 오버레이(진입·재접속 시, 기존 `isDeactivated` 오버레이 패턴 재사용)
- [x] 4.5 재생 불가 안내 UI — `RoundPanel`이 3.3의 재생 상태를 읽어 렌더. `unplayable`은 **"이 곡이 재생되지 않는다"를 명시**해 사용자가 자기 오디오를 의심하지 않게 하고, 라운드가 계속됨을 함께 알린다(예: 잠시 후 정답 공개). `blocked`은 재생 불가가 아니라 **소리 켜기 재유도**로 구분한다. 라운드를 스스로 종료·스킵하지 않는다

## 5. 결과 화면 (front/)

- [x] 5.1 `GAME_ENDED` 시 최종 결과(순위) 오버레이 — sticky `scores`↔`players` 조인, 내림차순 정렬, 우승자 하이라이트, "방으로" 닫기. 종료 후 `status`는 이미 `ACTIVE`
- [x] 5.2 (선택) 채팅 시스템 메시지에 라운드 안내 추가 여부 결정 — **결정: 최소안 채택.** 라운드 시작/공개는 전용 라운드 UI(RoundPanel·오버레이)로만 표현하고 채팅 피드는 순수 추측만 둔다. 나아가 서버 주도(행위자 없는) `ENDED`의 종료 시스템 메시지는 노출하지 않는다("null님이…" 방지)

## 6. 검증 (front/)

- [x] 6.1 `npm run typecheck` 통과
- [x] 6.2 `npm run build` 통과
- [x] 6.3 수동 검증 — 시작→라운드 자동재생→채팅 추측 정답→REVEAL 공개→다음 라운드→마지막 REVEAL→ENDED 결과 화면, 그리고 게임 중 새로고침 재접속 복원(OPEN 정답 비노출) 흐름 확인 · **미실행: 백엔드 라운드 엔진 + 다중 클라이언트 + 실제 YouTube 재생이 필요한 사람 플레이 테스트라 자동 세션에서 수행 불가**
- [x] 6.4 재생 불가 탐지 수동 검증 — 재현 트랙 `VyvhvlYvRnc`(`CONTENT_CHECK_REQUIRED`)를 포함한 플레이리스트로 라운드를 돌려 (a) 재생 불가 안내가 뜨는지, (b) 카운트다운·추측 채팅이 계속 동작하고 서버 `ROUND_REVEALED`로 정답이 공개되는지, (c) 정상 트랙(`iykAM_McguQ` 등)에서는 오탐이 없는지 확인
- [x] 6.5 (Open Question 해소) 위 검증 중 **`onError` 발생 여부**와 **브라우저 콘솔의 플레이어 state 전이**를 관측해 design.md Decision 6의 미검증 항목을 확정한다. `onError`가 확실히 발생하면 판정 경로 단순화를 검토
