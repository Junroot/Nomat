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

## 컨벤션

- 커밋 메시지는 한국어 Conventional Commits 형식: `feat:`, `fix:` 등
- UI 언어는 전체 한국어
- 다크 테마 — Tailwind zinc 색상 팔레트, cyan-400을 액센트 색상으로 사용
- 메인 브랜치는 `develop`
