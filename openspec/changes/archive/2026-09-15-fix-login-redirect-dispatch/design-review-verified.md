# 검증 사실 캐시

이 루프 실행 중 실제 코드를 열어 확인한 관찰 사실. 코드는 루프 중 불변이므로
같은 루프의 후속 에이전트는 이 관찰을 직접 확인한 것과 동등하게 신뢰해도 된다.
사실만 담는다 — 심각도·지적·권고·평가 금지.

## 백엔드 — security

- `SecurityConfiguration.kt:45` — `.exceptionHandling { it.authenticationEntryPoint(Http403ForbiddenEntryPoint()) }`. import 는 :12 (라운드 1)
- `SecurityConfiguration.kt:30-39` — `oauth2Login` 블록 존재. `authorizationEndpoint`, `userInfoEndpoint(nomatOAuth2UserService)`, `authorizationRequestRepository(httpCookieOAuth2AuthorizationRequestRepository)`, `successHandler(nomatAuthenticationSuccessHandler)` (라운드 1)
- `SecurityConfiguration.kt:40-44` — `formLogin { disable() }`, `httpBasic { disable() }`, `csrf { disable() }`, `cors { }`, `sessionManagement(STATELESS)` (라운드 1)
- `SecurityConfiguration.kt:52` — `permittedUrls = setOf("/login/**", "/html/**", "/ws/**")` — 세 개다 (라운드 1)
- `ManagementSecurityConfiguration.kt:17` — KDoc 본문에 `Http403ForbiddenEntryPoint` 문자열 포함. `:26-33` `@Order(1)` + `EndpointRequest.toAnyEndpoint()` + `anyRequest().permitAll()` (라운드 1)
- `TokenAuthenticationFilter.kt:22-28` — 쿠키에서 토큰을 읽고 `tokenService.getPlayerId(it)` 가 null 이 아닐 때만 SecurityContext 를 채운다. 예외를 던지지 않는다 (라운드 1)
- `TokenService.kt:25-35` — `getPlayerId` 는 `kotlin.runCatching { ... }.getOrNull()` 로 감싸 파싱·서명 검증 실패 시 null 을 반환한다 (라운드 1)
- `GlobalControllerAdvice.kt:32-37` — `AbstractNomatException` 핸들러가 `exception.httpStatus` + `application/json` + `ExceptionResponse(message)` 를 반환 (라운드 1)
- `ForbiddenException.kt:5` — `AbstractNomatException(message, HttpStatus.FORBIDDEN)` (라운드 1)
- `back/src/main` 전체 grep `"ForbiddenException"` — throw 지점은 `PlaylistService.kt:48,68,79,87`, `RoomService.kt:68`, `FavoritePlaylistService.kt:25`, `Room.kt:79,101,112` 총 8곳. Room.kt 3곳은 도메인 객체 내부 (라운드 1)

## 백엔드 — 테스트 설정·기존 테스트

- `TestConfiguration.kt:54` — `.exceptionHandling { it.authenticationEntryPoint(Http403ForbiddenEntryPoint()) }`. import 는 :15 (라운드 1)
- `TestConfiguration.kt:46-57` — 체인에 `oauth2Login` 없음. `formLogin { disable() }`, `httpBasic { disable() }`, `csrf { disable() }`, `sessionManagement(STATELESS)`, `addFilterBefore(authenticationFilter(), UsernamePasswordAuthenticationFilter)` (라운드 1)
- `TestConfiguration.kt:35-43` — 테스트 소스셋에도 `@Order(1)` actuator permitAll 체인이 별도로 존재 (라운드 1)
- `TestAuthenticationFilter.kt:18-23` — `request.getHeader("playerId")?.toLongOrNull()` 가 null 이 아닐 때만 인증. 쿠키를 읽지 않는다 (라운드 1)
- `back/src/test` 전체 grep `"isForbidden|FORBIDDEN|403"` — 단언은 `PlaylistControllerTest.kt:300`, `RoomControllerTest.kt:122` 두 곳뿐이고 나머지 hit 는 `TestConfiguration.kt:15,54` 의 `Http403ForbiddenEntryPoint` 식별자다. 미인증 403 을 단언하는 테스트는 없다 (라운드 1)

## 인프라

- `infra/app/compose.yml:19-20` — healthcheck 는 `curl -f http://localhost:8081/health` (관리 포트) (라운드 1)
- `infra/` 전체 grep `"401|403"` — compose.yml healthcheck 외 hit 없음. `infra/app/nginx.conf` 에 `error_page`/`proxy_intercept_errors` 및 401·403 문자열 없음 (라운드 1)

## 프론트 — 라우팅·인터셉터

- `front/app/routes.ts` — 라우트는 `/`(RoomsView), `/rooms/:roomId`, `/playlists`, `/login`, `/playlists/create`, `/playlists/:playlistId/modify` (라운드 1)
- `front/app/root.tsx:46-48` — `App` 은 `<Outlet />` 만 렌더. 공통 셸·네비게이션 없음 (라운드 1)
- `front/app/utils/api.ts:21-29` — 인터셉터가 `error.response?.status === 403` 일 때 `window.location.href = window.origin + "/login"` 후 `Promise.reject(error)` (라운드 1)
- `front/app/utils/api.ts` — export 는 fetchMe, fetchRecentlyAddedPlaylists, fetchByMasterDisplayName, fetchFavoritePlaylists, fetchMyPlaylists, searchPlaylistsByTitle, fetchPlaylist(:61), fetchPlaylistWithTracks(:66), createPlaylist, modifyPlaylist, favoritePlaylist, unfavoritePlaylist, deletePlaylist, createRoom, fetchRoomDetail(:105), fetchRooms (라운드 1)
- `front/app` 전체 grep `"axios|fetch("` — api.ts 외 axios 사용은 `PlaylistWriteView.tsx:15,152` 의 `AxiosError` 타입 캐스팅뿐. 별도 HTTP 클라이언트·raw fetch 없음 (라운드 1)
- `front/app` 전체 grep `"location.href|location.assign|location.replace"` — `Me.tsx:18`, `api.ts:25`, `LoginView.tsx:78`, `PlaylistsView.tsx:259` 네 곳 (라운드 1)

## 프론트 — LoginView / Me / MeStore

- `LoginView.tsx:5-7` — `searchParams.get("redirectUrl")` 후 값이 truthy 면 `new URL(redirectUrlString)`, 아니면 null (라운드 1)
- `LoginView.tsx:9-22` — `redirectUrl && redirectUrl.origin !== window.location.origin` 이면 "진입 경로가 잘못되었습니다" 화면을 반환하고 로그인 버튼을 렌더하지 않는다 (라운드 1)
- `LoginView.tsx:71-82` — `goToDiscordLogin` 이 `window.open(.../oauth2/authorization/discord)` 후 `if (redirectUrl)` 안에서만 500ms `setInterval` 로 `loginPage.closed` 를 감시하고 `window.location.href = redirectUrl.href` 로 이동 (라운드 1)
- `LoginView.tsx` 전체 — API 호출 없음. `AppShell`/`NavigationBar`/`MobileHeader` 를 렌더하지 않는다 (라운드 1)
- `Me.tsx:13-21` — 렌더 본문에서 `if (!meStore.me) fetchMe().then(setMe).catch(() => window.location.href = \`${window.location.origin}/login?redirectUrl=${window.location.href}\`)`. 인코딩 없음 (라운드 1)
- `Me.tsx:28-40` — `isHover` 의존 `useEffect` 가 존재. `Me.tsx:42-48` — `if (compact)` 조기 반환 (라운드 1)
- `MeStore.ts:1-12` — zustand `create` 로 `{ me: MeResponse | null, setMe }` 만 보유. 적재 로직 없음 (라운드 1)
- `front/app` 전체 grep `"MeStore"` — 소비자는 `Me.tsx:13`, `MobileBottomNav.tsx:16`, `useRoomSubscription.ts:88`(`me?.id`), `RoomView.tsx:37`(`me?.id`), `PlaylistsView.tsx:46`. 적재(`fetchMe`) 호출은 `Me.tsx:15` 한 곳뿐 (라운드 1)
- `NavigationBar.tsx:10-19` — `hidden md:flex` 컨테이너를 항상 렌더하며 그 안에 `<Me />` 가 있다. CSS 로만 숨겨진다 (라운드 1)
- `MobileHeader.tsx:27` — `<Me compact />` (라운드 1)
- `AppShell.tsx:62-93`(MainShell), `:95-145`(SubShell) — 두 셸 모두 `<NavigationBar>` 를 무조건 렌더하고, `isMobile` 일 때 `<MobileHeader …/>` 를 **추가로** 렌더한다(엘리먼트 트리를 갈아끼우지 않는다는 주석 포함). 즉 모바일 폭에서 `Me` 인스턴스가 두 개 마운트된다 (라운드 1)
- `useBreakpoint.ts:14-18` — `useState` 초기화에서 `window.matchMedia` 를 읽고, `:20-42` 에서 change 리스너로 갱신 (라운드 1)

## 프론트 — 403 수신 호출부

- `PlaylistWriteView.tsx:46` — `fetchPlaylistWithTracks(playlistIdNumber).then(...)`, `.catch` 없음 (라운드 1)
- `PlaylistWriteView.tsx:151-154` — 저장 경로 `catch` 에서 `const axiosError = error as AxiosError<{message: string}>` (:152) 후 `toast.error(axiosError.response?.data?.message ?? "알 수 없는 오류가 발생했습니다.")` (:153) (라운드 1)
- `useRoomSubscription.ts:254-262` — 이펙트 진입 시 `!client || storeRoomId !== roomId` 면 `navigate("/")`(:260) 후 return. `:270-281` — `fetchRoomDetail(roomId).then(...).finally(() => setIsLoading(false))`, `.catch` 없음 (라운드 1)
- `RoomsView.tsx:36-47` — `fetchRooms().then(...).catch(() => setError("방 목록을 불러오지 못했습니다")).finally(...)` (라운드 1)
- `PlaylistsView.tsx:57-58` — `fetchPlaylist(selectedPlaylistId).then(...)`, `.catch` 없음. `:79-81` — 목록 `request.then(...).finally(...)`, `.catch` 없음. `:114` — `fetchFavoritePlaylists().then(p => setPlaylists(p))`, `.catch` 없음 (라운드 1)
- `PlaylistsView.tsx:103-120` — `toggleFavorite` 의 `try/catch` 가 `setSelectedPlaylist({...selectedPlaylist})` 롤백 + `toast.error("즐겨찾기 변경에 실패했습니다.")` (라운드 1)
- `PlaylistsView.tsx:122-137` — `handleConfirmDelete` 의 `catch` 가 `toast.error("삭제 중 문제가 발생했습니다.")` (라운드 1)
- `RoomCreate.tsx:76-84`, `:92-100` — `fetchFavoritePlaylists()`/`fetchMyPlaylists()` 를 `setTimeout` 안에서 `.then` 만 붙여 호출. `.catch` 없고 `favoriteError`/`myError` 는 `setXxxError(null)` 로만 세팅된다(`:74`, `:91`). `:133` `searchPlaylistsByTitle` 도 동일 파일 내 호출 (라운드 1)
- `PlaylistService.kt:84-92` — `getWithTracks` 만 `masterId != requestPlayerId` 에서 ForbiddenException. 트랙 없는 단건 조회 경로에는 소유권 검사가 없다 (라운드 1)
- `RoomService.kt:65-69` — `getDetail` 이 `!room.playerIds.contains(playerId)` 일 때 ForbiddenException (라운드 1)

## 백엔드 — 필터 체인 인가 규칙 (라운드 2)

- `SecurityConfiguration.kt:27-29` — `authorizeHttpRequests { requestMatchers(*permittedUrls).permitAll().anyRequest().authenticated() }`. `anonymous { disable() }` 호출은 체인(`:27-48`) 어디에도 없다 (라운드 2)
- `SecurityConfiguration.kt:30-39` — `oauth2Login` 블록 안에 `authorizationEndpoint { }`가 두 번 나오고(:31, :33-37) 두 번째에 `authorizationRequestRepository`가 설정된다 (라운드 2)
- `TestConfiguration.kt:47-49` — 테스트 체인도 `SecurityConfiguration.permittedUrls` 를 재사용해 `permitAll().anyRequest().authenticated()` (라운드 2)
- `ManagementSecurityConfiguration.kt:12-21` — KDoc 전문. "메인 체인의 OAuth2/STATELESS/Http403ForbiddenEntryPoint가 적용되면 Alloy가 403을 맞으므로" 문장에 해당 이름이 있다 (라운드 2)
- `NomatAuthenticationSuccessHandler.kt:31-40` — 쿠키(`TokenService.TOKEN_COOKIE_KEY`, path `/`, httpOnly, domain=`jwt.domain`) 발급 후 `/html/close.html` 로 302 (라운드 2)

## 백엔드 — Spring Security 6.4.8 엔트리포인트 구현 (라운드 2)

- `spring-security-web-6.4.8-sources.jar` `Http403ForbiddenEntryPoint.commence` — `response.sendError(HttpServletResponse.SC_FORBIDDEN, "Access Denied")` 를 호출한다 (라운드 2)
- `spring-security-web-6.4.8-sources.jar` `HttpStatusEntryPoint.commence` — `response.setStatus(this.httpStatus.value())` 한 줄뿐이다. 헤더 설정·`sendError` 호출 없음 (라운드 2)

## 백엔드 — room 모듈 엔드포인트 (라운드 2)

- `RoomController.kt:20-47` — `@RequestMapping("/rooms")` 에 `GET ""`(:27-33, 인증 주체 파라미터 없음), `GET "/{roomId}"`(:35-41, `@AuthenticationPrincipal playerId`), `POST ""`(:43-47) 세 개뿐. join/enter 성격의 HTTP 엔드포인트 없음 (라운드 2)
- `RoomStompController.kt` — `@MessageMapping` 은 `/rooms/leave`(:31), `/rooms/start`(:37), `/rooms/end`(:43), `/rooms/pass`(:55), `/rooms/chat`(:61) 다섯 개 (라운드 2)
- `Room.kt:77-119` — `verifyPassword`(:77-81), `start`(:95-105), `end`(:107-117) 가 `ForbiddenException` 을 던진다 (라운드 2)
- `back/src/main` 전체 grep `"verifyPassword|\.join("` — 호출부는 `RoomService.kt:123-124`(verifyPassword+join)와 `RoomJoinChannelInterceptor.kt:52,66`(roomService.join) 뿐이다 (라운드 2)

## 프론트 — 전수 grep (라운드 2)

- `front/app` 전체 grep `"/login"` — hit 는 `api.ts:25`(`window.origin + "/login"`)와 `Me.tsx:18`(`/login?redirectUrl=…`) 두 곳뿐 (라운드 2)
- `front/app` 전체 grep `"401|403"` — hit 는 `api.ts:24` 한 곳뿐 (라운드 2)
- `front/app` 전체 grep `"fetchPlaylistWithTracks"` — 정의 `api.ts:66`, import `PlaylistWriteView.tsx:14`, 호출 `PlaylistWriteView.tsx:46` (라운드 2)
- `front/app` 전체 grep `"fetchRoomDetail"` — 정의 `api.ts:105`, import `useRoomSubscription.ts:5`, 호출 `useRoomSubscription.ts:270` (라운드 2)
- `PlaylistsView.tsx:259` — `window.location.href = \`/playlists/${selectedPlaylist.id}/modify\`` (수정 버튼 onClick). 로그인 경로가 아니다 (라운드 2)
- `LoginView.tsx:7` — `const redirectUrl = redirectUrlString ? new URL(redirectUrlString) : null` 이 컴포넌트 렌더 본문에 있고 try/catch 로 감싸여 있지 않다 (라운드 2)
- `LoginView.tsx:40` — `onClick={() => goToDiscordLogin(redirectUrl)}`, `goToDiscordLogin(redirectUrl: URL | null)` 은 `:71-82` (라운드 2)
- `api.ts:13-18` — `axios.create({ baseURL: import.meta.env.VITE_SERVER_BASE_URL, withCredentials: true })`. 인스턴스는 이 하나 (라운드 2)
