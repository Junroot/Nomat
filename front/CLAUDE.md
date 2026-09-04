# CLAUDE.md

이 파일은 Claude Code (claude.ai/code)가 이 저장소에서 작업할 때 참고하는 가이드입니다.

## 프로젝트 개요

Nomat은 노래 맞히기 게임 애플리케이션이다. 
사용자는 Discord OAuth2로 인증하고, YouTube 트랙 기반 플레이리스트를 생성/관리하며, 공유 감상 방에 참여한다. 
이 저장소는 **프론트엔드** — 백엔드 API와 통신하는 React SPA이다.

## 명령어

- `npm run dev` — 개발 서버 실행 (http://localhost:5173)
- `npm run build` — React Router를 통한 프로덕션 빌드
- `npm run typecheck` — `react-router typegen` 후 `tsc` 실행
- `npm run start` — 프로덕션 빌드 서빙
- 테스트 프레임워크는 설정되어 있지 않음

## 기술 스택

- **React 19** + **React Router v7** (SPA 모드, `react-router.config.ts`에서 SSR 비활성화)
- **Vite 5** 번들러 (플러그인: Tailwind CSS v4, vite-plugin-svgr, vite-tsconfig-paths)
- **TypeScript** (strict 모드)
- **Tailwind CSS v4** — `app/app.css`에서 `@import "tailwindcss"`로 임포트
- **Zustand** — 클라이언트 상태 관리
- **Axios** — API 호출
- **react-youtube** — YouTube 임베드 플레이어

## 아키텍처

### 라우팅

라우트는 `app/routes.ts`에서 수동 정의 (파일 기반 아님). 모든 라우트 컴포넌트는 `app/routes/`에 위치:

| 경로 | 컴포넌트 | 설명 |
|---|---|---|
| `/` (index) | `RoomsView.tsx` | 방 목록 및 생성 |
| `/rooms/:roomId` | `RoomView.tsx` | 개별 방 (채팅 포함) |
| `/playlists` | `PlaylistsView.tsx` | 플레이리스트 탐색 (탭: 즐겨찾기, 내가 만든, 전체) |
| `/playlists/create` | `PlaylistWriteView.tsx` | 새 플레이리스트 생성 |
| `/playlists/:playlistId/modify` | `PlaylistWriteView.tsx` | 기존 플레이리스트 수정 |
| `/login` | `LoginView.tsx` | Discord OAuth2 로그인 |

### API 레이어

`app/utils/api.ts` — 중앙 집중식 Axios 클라이언트:
- `VITE_SERVER_BASE_URL` 환경변수에서 Base URL 설정
- 쿠키 기반 인증 (`withCredentials: true`)
- 403 응답 시 `/login`으로 리다이렉트하는 글로벌 인터셉터

### 상태 관리

- `app/stores/MeStore.ts` — 현재 사용자 정보를 담는 Zustand 스토어 (`MeResponse`)
- `app/stores/VolumeStore.ts` — 앱 전역 볼륨(0~100, 기본 50). **앱의 모든 소리 출처는 이 스토어를 구독한다.** 새 소리 출처를 만들 때 자체 볼륨 상수를 두지 말 것. 음소거는 별도 플래그가 아니라 `volume === 0`이다(라운드 플레이어의 `mute`/`unMute`와 직교). zustand `persist`로 localStorage에 영속된다 — 이 코드베이스의 첫 localStorage 사용이며, 키는 `nomat.` 접두 네임스페이스를 따른다(`nomat.volume`)

### 타입 정의

`app/utils/`에 API 요청/응답용 TypeScript 인터페이스 존재 (예: `PlaylistResponse.ts`, `RoomResponse.ts`, `PlaylistRequest.ts`). 런타임 검증 없이 순수 인터페이스만 정의.

### 컴포넌트 구조

- `app/components/layout/` — 공통 레이아웃 프리미티브: `NavigationBar`, `NavigationItem`, `ColumnsContainer`, `Column1`, `Column2`
- `app/components/ui/` — 재사용 UI 컴포넌트: `Modal`, `MusicPlayer`, `SearchBar`, `SelectMenu`, `TrackEditLayer`, `RoomCreate` 등

### 레이아웃 패턴

모든 라우트 뷰는 동일한 구조를 따름:
```
<NavigationBar> (좌측 사이드바, 아이콘)
<ColumnsContainer>
  <Column1> (주요 콘텐츠)
  <Column2> (상세 패널)
</ColumnsContainer>
```

### SVG 아이콘

`app/assets/`의 SVG 파일은 `vite-plugin-svgr`을 통해 `?react` 쿼리 접미사로 React 컴포넌트로 임포트:
```tsx
import RoomIcon from "~/assets/room.svg?react";
```

### 경로 별칭

`~/`는 `./app/`으로 매핑 (`tsconfig.json` paths에서 설정).

## 설계 원칙

코드를 작성할 때 기존 코드를 무조건 따라하지 않는다. 아래 원칙에 따라 더 나은 구조를 적극적으로 제안하고 적용한다.

### 관심사 분리

- **비즈니스 로직은 커스텀 훅으로 분리**한다. 컴포넌트에 API 호출, 상태 변환, 유효성 검증 로직을 직접 넣지 않는다
- 예: `usePlaylistSearch()`, `useFavoriteToggle()`, `useDebounce()` 등 재사용 가능한 훅으로 추출
- 컴포넌트는 UI 렌더링과 사용자 인터랙션에 집중한다

### 상태 관리

- 관련된 상태가 3개 이상이면 `useReducer()` 사용을 고려한다 (useState 나열 지양)
- 서버 상태(API 데이터)와 클라이언트 상태(UI 상태)를 명확히 구분한다
- 서버 상태 관리가 복잡해지면 React Query 등 데이터 페칭 라이브러리 도입을 제안한다

### 컴포넌트 설계

- 단일 컴포넌트 200줄 초과 시 분리를 검토한다
- 반복되는 패턴(모달 상태, 디바운스, 무한 스크롤 등)은 커스텀 훅으로 추출한다
- 폼 유효성 검증은 인라인 if 체크 대신 스키마 기반 검증(Zod 등)을 권장한다

### 에러 처리

- 컴포넌트마다 try-catch를 반복하지 않고, 공통 에러 처리 패턴을 활용한다
- Error Boundary를 적절히 배치한다

### 성능

- 비용이 큰 계산에는 `useMemo`, 콜백에는 `useCallback`을 적절히 사용한다
- 불필요한 리렌더링을 유발하는 상태 구조를 피한다

## 알려진 함정

### StrictMode와 명령형 서드파티 플레이어

이 앱에는 `app/entry.client.tsx`가 없어 React Router의 **기본 클라이언트 엔트리**가 사용되고, 그 파일이 앱을 `<StrictMode>`로 감싼다(`node_modules/@react-router/dev/dist/config/defaults/entry.client.tsx`). 따라서 **개발 모드에서는 모든 컴포넌트의 이펙트가 두 번 실행된다.**

명령형 리소스를 생성하는 서드파티 컴포넌트(YouTube 플레이어, 지도, 캔버스 등)를 감쌀 때 이것이 눈에 보이는 오작동으로 나타날 수 있다. 실제 사례:

```
 createPlayer  ─▶ 플레이어 A ─▶ onReady ─▶ setVolume(v) / playVideo()
                                            │              └─▶ 🔊 볼륨 100으로 재생
                                            └─ 파괴 중이라 반영 실패
 destroyPlayer ─▶ A 파괴(비동기)
 createPlayer  ─▶ 플레이어 B ─▶ onReady ─▶ setVolume(v) 반영 ─▶ 🔉 v
```

라운드 오디오 플레이어에서 "재생 시작 시 볼륨이 컸다가 작아지는" 증상으로 관측됐다. 볼륨이 변한 것이 아니라 **버려질 플레이어 A가 기본 볼륨으로 잠시 울리다 파괴되고 B로 교체되는** 소리였다. react-youtube가 클래스 컴포넌트라 `componentDidMount → componentWillUnmount → componentDidMount`가 연쇄한 결과다. 위 도식의 `setVolume`/`playVideo`는 `useRoundAudioOrchestrator`(`makeOnReady`·`startActivePlayback`)에 있다 — 명령형 재생 제어는 전부 이 훅이 들고 있고, `RoundAudioPlayer`는 `ClipPlayer` 두 개를 그리기만 한다.

**판별법**: 컴포넌트에 인스턴스 번호를 붙여 마운트/언마운트를 찍어본다. 같은 번호로 `MOUNT → UNMOUNT → MOUNT`가 나오면(ref가 보존된 채 이펙트만 재실행) StrictMode다. 번호가 바뀌면 진짜 리마운트이므로 원인이 다르다.

**대응**: 프로덕션 빌드에서는 재현되지 않으므로 대개 수정 대상이 아니다. 확인은 `npx vite preview`로 한다 — `npm run start`는 SPA 모드(`ssr: false`)에서 빌드가 서버 번들을 삭제하기 때문에 **동작하지 않는다.**

### react-youtube는 `videoId`·`opts`가 바뀌면 플레이어를 파괴하고 다시 만든다

`react-youtube`(10.1.0)의 `shouldResetPlayer`는 `videoId`가 달라지거나 `opts`가 깊은 비교로 달라지면 참이 되고, `componentDidUpdate`는 `resetPlayer()` 직후 반환해 `updateVideo()`(= `loadVideoById`/`cueVideoById` 경로)에 **도달조차 하지 않는다**(`dist/YouTube.esm.js:66`, `252`).

따라서 **플레이어 인스턴스를 재사용하려면 이 props를 바꾸면 안 된다.** 부모의 `key`를 걷어내는 것만으로는 부족하다 — props를 계속 갱신하면 리마운트가 그대로 일어난다. 아이프레임·플레이어 부트스트랩은 ~460ms라 이 재생성 비용이 그대로 지연이 된다.

이 프로젝트가 쓰는 형태(`ClipPlayer.tsx`): `videoId`는 **빈 문자열로 고정**하고(`EMPTY_VIDEO_ID`), `opts`는 `useMemo(..., [])`로 참조를 붙들어 둔다. 곡을 아예 넣지 않고 만드는 것이 핵심이다 — 그래야 어떤 곡이 나올지 알기 전(방 대기 중)에 부트스트랩을 끝낼 수 있고, 그만큼이 첫 라운드 재생 지연에서 빠진다. 트랙 지정과 이후 교체는 전부 `useRoundAudioOrchestrator`의 명령형 호출(`loadVideoById`)이 맡는다.

### `cueVideoById`는 버퍼를 채우지 않는다

선(先)버퍼링 용도로 쓸 수 없다. 실측에서 `cueVideoById` 후 5초를 기다렸다가 재생해도 아무것도 안 한 기준선과 같은 지연이 나왔다(451ms vs 435ms). 실제로 버퍼를 채우려면 `mute()` → `loadVideoById()` → `PLAYING` 관측 시 `pauseVideo()`가 필요하다(같은 조건에서 113ms).

**mute가 먼저여야 한다** — 이 방식은 다음 곡을 실제로 재생해 버퍼를 채우므로, 음소거하지 않으면 정답 공개 구간에 다음 곡이 들려 그 자체로 정답이 유출된다.

### `vite preview`로 로컬 백엔드를 붙이려면 `.env.production.local`이 필요하다

프로덕션 빌드 동작을 확인할 때(StrictMode 이중 마운트 배제, 재생 지연 측정 등) `npx vite preview`를 쓰는데, 이때 Vite는 `production` 모드의 env 파일을 읽는다. 저장소에 커밋된 `.env.development`는 적용되지 않으므로 **아무 설정도 없으면 `https://api.dev.nomat.live`를 바라본다.**

로컬 백엔드로 붙이려면 `front/.env.production.local`에 `VITE_SERVER_BASE_URL=http://localhost:8080`을 둔다. 이 파일은 `.gitignore`의 `.env.*.local`에 걸려 커밋되지 않으므로 **각자 로컬에서 만들어야 한다**(없다고 해서 누가 지운 것이 아니다).

### YouTube IFrame API에는 볼륨 playerVar가 없다

볼륨은 `setVolume()` 메서드로만 제어할 수 있어 `onReady` 이후에만 설정 가능하다. 따라서 `autoplay` playerVar로 재생을 시작하면 볼륨 설정 전에 소리가 나간다. `onReady`에서 `setVolume()` → `playVideo()` 순으로 직접 재생을 시작해야 순서가 보장된다(IFrame API 공식 문서의 권장 패턴).

볼륨 값은 `VolumeStore`에서 온다. 재생 개시 지점(`onReady`·재생 시작·적재·REVEAL 재재생)은 이벤트 콜백·이펙트 안이라 클로저의 옛 값을 읽을 수 있으므로 **ref로 읽어 최신 값을 보장한다**(`useRoundAudioOrchestrator`의 `volumeRef`, `MusicPlayer`의 `volumeRef`). 재생 중 변경은 별도의 `volume` 이펙트가 플레이어에 민다 — 라운드 플레이어는 교대하므로 **두 플레이어 모두**에 민다. `setVolume`은 mute를 풀지 않으므로 선버퍼링 쪽의 음소거와 충돌하지 않는다.

localStorage 키는 `nomat.` 접두 네임스페이스를 쓴다(예: `nomat.volume`). 사용자가 편집할 수 있는 저장소이므로 읽을 때 정화한다.

## 컨벤션

- 커밋 메시지는 한국어 Conventional Commits 형식: `feat:`, `fix:` 등
- UI 언어는 전체 한국어
- 다크 테마 — Tailwind zinc 색상 팔레트, cyan-400을 액센트 색상으로 사용
- 메인 브랜치는 `develop`
