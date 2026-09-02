## 1. 프론트엔드 — 전체화면 오버레이 폐기 (`front/`)

- [x] 1.1 `app/components/ui/RoundRevealOverlay.tsx` 삭제
- [x] 1.2 `app/routes/RoomView.tsx` — `RoundRevealOverlay` import·렌더와 `showReveal` 파생 상태 제거. `showGate`는 `AudioGateOverlay`와 태스크 3.3에서 계속 쓰이므로 **삭제하지 않는다**

## 2. 프론트엔드 — 정답 표시를 `RoundPanel` 인라인으로 (`front/`)

- [x] 2.1 `app/components/ui/RoundPanel.tsx` — `REVEAL`에서 정답(`round.title`)·승자(`round.winnerNickname`)를 인라인 표시. 승자가 없으면 시간 초과로 표시한다. **새 prop을 추가하지 않는다** — `round` 하나로 충분하다(design.md Decision 1)
- [x] 2.2 라운드 번호는 `round.roundNumber`를 쓴다. `roundSeq`는 라운드당 2씩 증가하는 CAS epoch라 표기에 쓰면 총 라운드 수를 넘는다(`roundReducer.ts`의 경고 참조) — 삭제되는 오버레이에서 옮겨 붙일 때 이 값을 잘못 가져오지 않도록 주의한다
- [x] 2.3 `REVEAL` 동안 패널을 시각적으로 강조한다. `app/app.css`에 이미 있는 `shadow-glow-cyan` 유틸리티를 쓰고 **새 키프레임·유틸리티를 만들지 않는다**
- [x] 2.4 `OPEN`의 기존 표시(카운트다운, 점수판, 재생 실패·자동재생 차단 안내)가 그대로 동작하는지 확인

## 3. 프론트엔드 — 영상을 흐름 안 축소 형태로 (`front/`)

- [x] 3.1 `app/components/ui/ClipPlayer.tsx` — 노출 시 스타일에서 `fixed`·`z-50`·`left-1/2 top-[8vh] -translate-x-1/2`·`w-[min(92vw,720px,80vh)]`를 제거하고 흐름 안 축소 박스로 변경. **`RoundRevealOverlay`와의 좌표 맞물림을 설명하던 주석도 함께 제거**한다(대상이 사라졌다). 16:9 비율, `rounded-2xl`·`ring-1 ring-neon-cyan/30`·`shadow-2xl`·`pointer-events-none`, iframe 절대 배치용 자손 선택자는 유지한다
- [x] 3.2 `ClipPlayer.tsx` — 숨김은 지금과 같이 `hidden`(`display:none`)을 유지한다. `display:none` 요소는 flex 항목이 아니므로 `Column2`의 `gap`에도 잡히지 않는다. `videoId`·`opts` props 동결(`EMPTY_VIDEO_ID`, `useMemo(..., [])`)은 **절대 건드리지 않는다** — 바꾸면 react-youtube가 플레이어를 파괴·재생성한다(front/CLAUDE.md "알려진 함정")
- [x] 3.3 `app/components/ui/RoundAudioPlayer.tsx` — 영상 노출 억제 플래그(예: `videoSuppressed`)를 받아 `revealing` 판정에 함께 반영한다. 플레이어 인스턴스·선버퍼링·재생 제어에는 영향을 주지 않고 **표시 여부만** 바꾼다(design.md Decision 5)
- [x] 3.4 `app/routes/RoomView.tsx` — `RoundAudioPlayer`의 렌더 위치를 `ColumnsContainer`의 형제에서 **`Column2` 안쪽, `RoundPanel`과 메시지 목록 사이**로 옮기고 `showGate`를 억제 플래그로 넘긴다
- [x] 3.5 **배치 안전성 확인(회귀 위험 지점)** — 옮긴 위치가 (a) `isPlaying &&` 조건 **밖**이고 (b) `RoundPanel` **바깥**이며 (c) `Column2`가 조건 없이 렌더되는지 확인한다. 셋 중 하나라도 어긋나면 게임 시작·종료마다 iframe이 재로드되어 부트스트랩 선불(`reduce-round-playback-latency`)이 무의미해진다(design.md Decision 2의 표)
- [x] 3.6 영상 크기는 모바일보다 데스크톱을 크게 잡되 "곡을 확인할 수 있는 정도"에 맞춘다. 겹치는 요소가 없으므로 어떤 여백 상수와도 맞물리지 않아야 한다 — 다른 컴포넌트의 치수를 참조하는 계산식을 도입하지 않는다
- [x] 3.7 `app/components/layout/AppShell.tsx` — `isMobile` 삼항 분기가 엘리먼트 트리를 갈아끼워 breakpoint를 넘을 때마다 `children`이 리마운트되고 플레이어 iframe이 재생성된다. 골격을 고정하고 모바일 전용 요소의 유무와 className만 바꾼다(design.md Decision 7). `MainShell`·`SubShell` 둘 다 해당된다

## 4. 프론트엔드 — 채팅 바닥 고정 (`front/`)

- [x] 4.1 `app/hooks/`에 바닥 고정 훅 신규. 메시지 컨테이너의 **크기 변화를 `ResizeObserver`로 관찰**해, 직전에 바닥 근처였으면 바닥으로 다시 붙인다. `phase` 전이를 트리거로 쓰지 않는다 — 원인은 단계가 아니라 높이 변화다(design.md Decision 4)
- [x] 4.2 바닥 근처 판정은 기존 임계(80px)를 그대로 쓰고, **위로 스크롤해 과거를 보던 참가자는 끌어내리지 않는다**
- [x] 4.3 `app/routes/RoomView.tsx` — 컨테이너 ref·`isNearBottomRef`·`messages` 변화 시 스크롤 로직을 훅으로 옮긴다. `RoomView`는 이미 284줄로 분리 기준(200줄)을 넘겨 있으므로 관찰 로직을 인라인으로 더하지 않는다(front/CLAUDE.md "관심사 분리")
- [x] 4.4 기존 동작 보존 확인 — 새 메시지 도착 시 바닥 추종, 위로 스크롤 중에는 추종하지 않음

## 5. 프론트엔드 — 빌드 게이트 (`front/`)

- [x] 5.1 `npm run typecheck` 통과
- [x] 5.2 `npm run build` 통과
- [x] 5.3 저장소에 `RoundRevealOverlay` 참조가 남지 않았는지 검색으로 확인

## 6. 수동 검증

프론트에 테스트 프레임워크가 없다. 2인 이상으로 실제 게임을 진행해 확인한다.

- [ ] 6.1 **주 수정** — 누군가 정답을 맞힌 뒤 공개 구간 5초 동안 채팅 피드와 입력창이 계속 보이고, 그 사이에 메시지를 입력·전송할 수 있으며 전송한 메시지가 피드에 나타나는지 확인
- [ ] 6.2 **스크롤 회귀** — 바닥을 보고 있는 상태에서 정답이 공개될 때, 정답 직전·직후에 도착한 메시지가 화면 밖으로 밀려나지 않는지 확인. 다음 라운드가 열려 영역이 다시 늘어날 때도 최신 메시지가 보이는지 확인
- [ ] 6.3 **스크롤 예외** — 위로 스크롤해 지난 메시지를 읽는 중에 정답이 공개되면 스크롤이 바닥으로 끌려가지 않는지 확인
- [ ] 6.4 **정답 유출 없음** — `OPEN` 동안 영상이 보이지 않는지, 공개 구간에 선버퍼링 중인 다음 곡의 영상이 보이지 않는지 확인
- [ ] 6.5 **게이트** — arming 전에 공개 구간에 들어간 경우 영상이 게이트 뒤에 비치지 않고, 게이트를 통과하면 남은 공개 구간에 영상이 나타나는지 확인
- [ ] 6.6 **재생 지연 회귀** — 게임 종료 후 같은 방에서 다시 시작했을 때 첫 라운드 재생 지연이 늘지 않는지 확인. 늘었다면 태스크 3.5의 배치가 어긋난 것이다
- [ ] 6.7 **타임아웃 공개** — 아무도 맞히지 못한 라운드에서 정답·시간 초과 표기와 영상·소리가 정상인지 확인
- [ ] 6.8 **재접속 복원** — 공개 구간 중 새로고침해 `REVEAL` 스냅샷으로 복원했을 때 정답·영상·채팅이 모두 정상인지 확인
- [ ] 6.9 **점수판** — 공개 시점에 갱신되는 점수판이 가려지지 않고 보이는지 확인
- [ ] 6.10 **모바일** — 좁은 화면에서 영상·정답·점수판·채팅이 겹치지 않는지, "방 정보 · 플레이어"를 펼친 상태에서도 화면이 깨지지 않는지 확인
- [ ] 6.11 **게임 종료** — `ENDED` 결과 화면이 이번 변경 전과 동일하게 동작하는지 확인(범위 밖이므로 회귀만 본다)
- [ ] 6.12 **화면 크기 전환** — 재생 중에 창 폭을 모바일 breakpoint(768px) 위아래로 오가며 **영상·소리가 끊기지 않는지** 확인. 끊기면 셸의 골격 고정이 어긋난 것이다. 함께 `AppShell`을 쓰는 다른 화면(방 목록·플레이리스트)의 배치 회귀도 확인한다
