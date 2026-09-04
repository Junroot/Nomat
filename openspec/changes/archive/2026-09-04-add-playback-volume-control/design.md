# Design — add-playback-volume-control

## Context

앱에서 소리를 내는 곳은 둘이고, 각자 볼륨 `50`을 따로 박아두고 있다.

```
  useRoundAudioOrchestrator          MusicPlayer (플레이리스트 미리듣기)
  ┌──────────────────────────┐       ┌──────────────────────┐
  │ PLAYBACK_VOLUME = 50     │       │ onReady:             │
  │  ├─ makeOnReady          │       │   setVolume(50)      │
  │  ├─ startActivePlayback  │       └──────────────────────┘
  │  ├─ loadCurrentTrack     │
  │  └─ REVEAL 재재생        │
  └──────────────────────────┘
        │              │
   ┌────▼────┐    ┌────▼────┐
   │Player A │🔊  │Player B │🔇 mute()  ← 선버퍼링 정답 유출 방지
   └─────────┘    └─────────┘
        라운드마다 역할 교대
```

라운드 플레이어에는 본 설계가 지켜야 할 기존 제약이 있다.

- **`setVolume` → `playVideo` 순서.** YouTube IFrame API에는 볼륨 playerVar가 없어 `onReady` 이후에만 볼륨을 정할 수 있다. 그래서 `autoplay` 대신 `onReady`에서 볼륨을 정하고 재생을 시작한다(`front/CLAUDE.md` "알려진 함정").
- **`mute()`/`unMute()`는 오케스트레이터의 것이다.** 선버퍼링 플레이어를 음소거하고 담당이 될 때 해제한다. 이 제어와 겹치는 수단으로 사용자 음소거를 만들면 라운드 교대가 사용자 의도를 덮는다.
- **플레이어는 두 개이고 재사용된다.** 볼륨은 둘 다에 밀어야 하며, 리마운트가 초기화를 공짜로 주지 않는다.
- **StrictMode 이중 마운트.** 개발 모드에서 플레이어가 두 번 만들어지고 첫 번째는 버려진다. 볼륨을 `onReady`에서 읽는 구조면 두 번째 플레이어도 같은 값을 받으므로 문제가 없다.

데스크톱 크롬은 좌측 72px 네비게이션 레일(`NavigationBar`, `hidden md:flex`)뿐이다. 상단 헤더는 모바일에만 있다. `MainShell`·`SubShell`이 각자 레일을 그리고, 둘 다 하단에 `<Me />`를 같은 방식으로 배치한다.

탐색에서 사용자가 확정한 결정: **모바일에는 컨트롤을 두지 않는다**(기기 볼륨에 위임), **앞으로 방 밖에서도 소리가 날 수 있음을 전제한다.**

## Goals / Non-Goals

**Goals**

- 하나의 값이 모든 소리 출처를 지배한다. 출처가 늘어도 규칙이 유지된다
- 기기에 영속. 새로고침·방 재입장·페이지 이동에 무관
- 라운드 플레이어의 기존 명령형 제어(교대·선버퍼링·재재생·`mute`/`unMute`)를 **구조적으로 건드리지 않는다**
- 데스크톱 전용 컨트롤을 모든 페이지의 같은 자리에 둔다
- 신규 외부 의존성 0

**Non-Goals**

- **전역 단축키(↑↓·M)** — 팝오버의 포커스 모델과 채팅 입력창(게임 중 포커스가 상주하는 곳)과의 키 충돌을 함께 정해야 해서 별도 change
- **모바일 컨트롤** — 결정 사항. iOS YouTube 임베드가 `setVolume`을 무시한다는 사실이 이 결정을 뒷받침한다
- **탭 간 실시간 동기화**(`storage` 이벤트) — 같은 기기에서 두 탭이 동시에 게임을 하는 경우는 지원 대상이 아니다. 각 탭은 자기 시작 시점의 값을 읽는다
- **트랙별 라우드니스 정규화** — 데이터 문제
- **볼륨 값의 서버 저장** — 계정이 아니라 기기의 속성

## Decisions

### Decision 1 — 스토어가 단일 출처이고, 각 소리 출처가 스토어를 구독한다 (레지스트리 아님)

```
  VolumeStore ──subscribe──▶ RoundAudioPlayer ──prop──▶ useRoundAudioOrchestrator
              ──subscribe──▶ MusicPlayer
              ──subscribe──▶ VolumeControl (UI)
```

**대안: 레지스트리.** 출처가 마운트 시 플레이어 핸들을 스토어에 등록하고 스토어가 `setVolume`을 밀어주는 방식. "출처가 있을 때만 컨트롤 표시"도 덤으로 얻는다.

**기각 이유.** 출처가 둘인 지금 등록·해제 생명주기 관리는 순비용이다. 라운드 플레이어는 핸들이 `onReady` 뒤에야 생기고 두 개가 교대하므로 등록 시점과 대상이 단순하지 않다. 구독 방식은 출처가 자기 플레이어의 생명주기를 이미 알고 있는 자리에서 값만 가져다 쓰면 된다. 출처가 셋 이상으로 늘고 "컨트롤 표시 조건"이 필요해지면 그때 레지스트리로 옮긴다.

**오케스트레이터는 스토어를 직접 구독하지 않는다.** 지금처럼 파라미터(`volume: number`)로 받는다. 이 훅은 명령형 제어만 담당하는 순수 훅이고, 그 순수성이 훅 주석의 설계 서사다. 스토어 구독은 렌더링만 하는 `RoundAudioPlayer`가 맡는다.

### Decision 2 — zustand `persist` 미들웨어 + localStorage

zustand 5에 내장된 `persist`를 쓴다. 신규 의존성이 없고 `MeStore`와 같은 `create()` 패턴을 유지한다.

- 키: `nomat.volume`
- 저장 형태: `{ volume: number, lastAudible: number }`
- **읽기 시 정화**: `merge` 옵션에서 범위(0~100)·정수·타입을 검사해 벗어나면 기본값으로 대체한다. localStorage는 사용자가 편집할 수 있는 저장소라 신뢰하지 않는다
- **저장소 접근 실패**(사생활 보호 모드 등): `persist`는 storage 예외를 삼키고 메모리 상태로 동작한다. 앱이 깨지지 않는다는 요구는 이것으로 충족되지만, 태스크에서 실제로 확인한다

이 코드베이스에서 **첫 localStorage 사용**이다. 키 네임스페이스(`nomat.` 접두)를 이번에 정해 두고 `front/CLAUDE.md`에 남긴다.

**대안: 직접 `localStorage.getItem/setItem`.** 코드 몇 줄이지만 hydration 시점·JSON 파싱·예외 처리를 손으로 쓰게 된다. 미들웨어가 그 일을 이미 한다.

### Decision 3 — 음소거는 `volume === 0`, 복원값은 `lastAudible`

```
  상태: { volume: 0..100, lastAudible: 1..100 }

  setVolume(v):   volume = v;  if (v > 0) lastAudible = v
  toggleMute():   volume = volume > 0 ? 0 : lastAudible
  기본값:         volume 50, lastAudible 50
```

**왜 플레이어 `mute()`가 아닌가.** 오케스트레이터가 `unMute()`를 세 군데(`startActivePlayback`·`loadCurrentTrack`·REVEAL 재재생)에서 호출한다. 사용자 음소거를 `mute()`로 구현하면 이 호출들이 조건부가 되어야 하고, 그 조건은 오케스트레이터가 "사용자 음소거"라는 새 개념을 알아야 성립한다. `volume 0`은 YouTube API에서 mute 상태와 독립이라 — `unMute()`를 해도 볼륨 0이면 무음 — 오케스트레이터가 아무것도 몰라도 된다.

**대안: `userMuted` 플래그.** 의미가 더 명시적이지만 위 세 호출을 전부 `if (!userMuted) unMute()`로 바꿔야 하고, 슬라이더 0과 음소거 버튼이 다른 상태가 되어 UI가 둘을 구분해 그려야 한다. 얻는 것보다 잃는 게 많다.

`lastAudible`도 영속한다. 음소거한 채 새로고침하고 해제했을 때 50으로 튀는 것은 사용자에게 "내 설정이 사라졌다"로 읽힌다.

### Decision 4 — 오케스트레이터 변경은 "상수를 ref로, 이펙트 하나 추가"에 그친다

```
  before                              after
  ─────────────────────────           ─────────────────────────────────────
  const PLAYBACK_VOLUME = 50          params.volume  →  volumeRef.current
  player.setVolume(PLAYBACK_VOLUME)   player.setVolume(volumeRef.current)   × 4곳
                                      useEffect(() => {
                                          playersRef.current.forEach(p => p?.setVolume(volume));
                                      }, [volume]);
```

- 네 호출 지점은 그대로 둔다. 지점마다 `setVolume`이 필요한 이유(준비 완료·재생 개시·적재·재재생 각각에서 `playVideo` 앞에 볼륨이 서야 함)는 변하지 않았다
- 값은 **ref로 읽는다.** 네 지점은 이벤트 콜백·이펙트 안이라 클로저에 갇힌 옛 값을 읽을 위험이 있다. `activeIndexRef`·`phaseRef`와 같은 패턴이다
- 볼륨 변경 이펙트는 **두 플레이어 모두**에 적용한다. 선버퍼링 쪽은 `mute()` 상태라 `setVolume`이 소리를 내지 않는다(YouTube API에서 `setVolume`은 mute를 풀지 않는다). 담당이 되어 `unMute()`될 때 이미 올바른 볼륨이다
- `mute()`/`unMute()` 호출은 **한 줄도 바꾸지 않는다.** 이 결정의 검증은 태스크의 diff 확인이 맡는다

`useClipPlayback`의 재생 불가 판정은 `PLAYING`/`BUFFERING` 상태 전이만 보므로 볼륨 0과 무관하다. 변경 없음.

### Decision 5 — 컨트롤은 `NavigationBar`가 소유하는 하단 슬롯에, 팝오버 슬라이더로

```
  NavigationBar (72px, hidden md:flex)
  ┌──────┐
  │  🏠  │  ← children (셸이 채움: 탭 또는 뒤로가기·액션)
  │  📋  │
  │      │
  │      │
  │  🔊  │  ← VolumeControl   ┐ 하단 슬롯 — NavigationBar가 직접 그림
  │  👤  │  ← Me              ┘
  └──────┘
       └─▶ 클릭 시 오른쪽으로 팝오버 ┌──────────────┐
                                    │ 🔊 ────●──── │  <input type="range">
                                    └──────────────┘
```

- **`NavigationBar`가 하단 슬롯을 소유한다.** 지금은 `MainShell`·`SubShell`이 각자 `<div className="grow-0 shrink-0"><Me /></div>`를 넣는다. 볼륨까지 넣으면 중복이 두 배가 되므로 슬롯을 `NavigationBar`로 옮긴다. `children`은 상단 영역만 채운다. CLAUDE.md의 "기존 코드를 그대로 따라하지 말 것"에 부합하는 작은 정리다
- **모바일 분기 코드가 없다.** 레일이 이미 `hidden md:flex`라 컨트롤도 함께 사라진다. `useBreakpoint`를 쓰지 않는다
- **팝오버인 이유.** 72px 레일에 가로 슬라이더가 들어가지 않는다. 세로 슬라이더는 브라우저 지원이 고르지 않다(`writing-mode` 트릭 필요). **마우스를 올리면 펼쳐지고 아이콘 클릭은 음소거 토글**이다 — YouTube·Spotify의 스피커 아이콘과 같은 모델이라 학습 비용이 없다. 키보드로는 아이콘에 포커스가 들어오면 펼쳐지고 Tab으로 슬라이더에 닿는다
- **슬라이더는 네이티브 `<input type="range">`.** 방향키 조작·접근성을 공짜로 얻는다. 스타일은 Tailwind `accent-*`와 `[&::-webkit-slider-thumb]` 정도로 다크 테마 팔레트에 맞춘다
- **아이콘은 두 상태**(가청/음소거). `assets/`에 볼륨 아이콘이 없으므로 새 SVG 두 개를 추가한다(기존 `?react` 임포트 관례). 팝오버에는 슬라이더와 현재 값만 둔다 — 음소거 토글은 레일 아이콘이 맡으므로 팝오버 안에 아이콘을 또 두면 같은 조작이 둘이 된다
- **접기**: 마우스가 컨트롤(아이콘+팝오버)을 벗어나거나, 포커스가 밖으로 나가거나, `Escape`. 아이콘과 팝오버 사이 간격은 팝오버 래퍼의 padding으로 잡아 그 틈을 지날 때 mouseleave가 나지 않게 한다. 네이티브 `<dialog>`(#240에서 모달에 채택)는 모달 백드롭과 포커스 트랩을 동반해 여기엔 과하다. `popover` 속성은 Firefox 지원이 2024년 중반부터라 아직 이르다고 보고, `Escape`만 문서 `keydown` 리스너로 처리한다

**항상 표시한다.** 출처 유무를 알려면 Decision 1에서 기각한 레지스트리가 필요하다. OS 볼륨 아이콘도 소리 안 날 때 사라지지 않는다.

### Decision 6 — `MusicPlayer`는 스토어를 구독해 `onReady`와 값 변경 양쪽에서 적용한다

`playerRef.current.setVolume(50)`을 스토어 값으로 바꾸고, `volume` 변경 이펙트를 추가한다. `onReady` 시점에 최신 값을 읽도록 여기서도 ref를 쓴다. 이 컴포넌트는 `any` 타입 플레이어와 `start`/`end` playerVar를 쓰는 등 라운드 플레이어보다 오래된 스타일이지만, **이번 change에서는 볼륨 지점만 손댄다** — 타입 정리는 별도 문제다.

### Decision 7 — 제스처 게이트에 음소거 안내 한 줄

게이트의 "소리 켜기"는 자동재생 정책을 통과시키는 것이지 볼륨을 올리는 것이 아니다. 볼륨 0인 사용자가 통과하면 무음이고, 재생 불가 판정은 정상 재생을 보므로 안내도 뜨지 않는다 — 사용자는 장비를 의심하게 된다. 게이트가 스토어를 읽어 `volume === 0`이면 "볼륨이 0이에요. 왼쪽 아래 🔊에서 올릴 수 있어요." 한 줄을 보여준다. 볼륨을 바꾸지는 않는다 — 게이트 통과가 설정을 건드리면 사용자가 방금 정한 음소거를 앱이 풀어버리는 셈이다.

## Risks / Trade-offs

- **[슬라이더 드래그마다 `persist`가 localStorage에 동기 쓰기]** → 드래그 한 번에 수십 회 쓰기가 발생하지만 값이 수십 바이트라 실측상 무시할 수준이다. 문제가 되면 `setVolume`을 rAF로 합치거나 `persist`의 `partialize`와 별도 디바운스를 조합한다. 선제 최적화는 하지 않는다
- **[`setVolume`이 postMessage 경유라 iframe이 값을 놓칠 수 있음]** → YouTube 플레이어는 마지막 값을 반영하므로 중간 값 유실은 무해하다. 재생 개시 지점에서는 어차피 ref로 다시 적용한다
- **[볼륨 0 사용자가 무음을 재생 불가로 오해]** → Decision 7의 게이트 안내와 레일의 음소거 아이콘으로 완화. 재생 불가 안내 자체는 볼륨과 무관하게 정확하다
- **[레일 하단 슬롯 이동이 두 셸의 레이아웃을 건드림]** → `Me`의 위치·크기는 그대로이고 그 위에 아이템 하나가 더해질 뿐이다. `AppShell`의 "골격을 고정한다"(iframe 재마운트 방지) 주석과 충돌하지 않는다 — 레일은 `children` 바깥이라 방 화면의 subtree에 영향이 없다
- **[localStorage를 사용자가 편집해 이상한 값을 넣음]** → Decision 2의 읽기 정화. 범위 밖이면 기본값
- **[`persist` hydration이 첫 렌더보다 늦어 기본값 50이 잠깐 적용될 가능성]** → localStorage는 동기 API라 zustand `persist`가 스토어 생성 시점에 동기 hydration한다. 첫 렌더에서 이미 영속값이다. 비동기 storage를 쓰지 않는 한 이 문제는 없다

## Open Questions

없음. 탐색에서 남긴 질문(영속 여부·모바일·위치)은 모두 결정됐다.
