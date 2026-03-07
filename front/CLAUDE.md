# CLAUDE.md

이 파일은 Claude Code (claude.ai/code)가 이 저장소에서 작업할 때 참고하는 가이드입니다.

## 프로젝트 개요

Nomat은 노래 맞추기 게임 애플리케이션이다. 
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

## 컨벤션

- 커밋 메시지는 한국어 Conventional Commits 형식: `feat:`, `fix:` 등
- UI 언어는 전체 한국어
- 다크 테마 — Tailwind zinc 색상 팔레트, cyan-400을 액센트 색상으로 사용
- 메인 브랜치는 `develop`
