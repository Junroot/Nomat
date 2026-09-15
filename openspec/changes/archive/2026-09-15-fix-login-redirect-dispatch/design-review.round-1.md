# design.md 적대적 리뷰

검증 통과한 `[치명]`·`[높음]` 지적 없음.

설계의 핵심 코드 주장(엔트리포인트 교체 지점, `oauth2Login` 존재, `httpBasic disable`, `ForbiddenException` 발생 지점과 그 HTTP/STOMP 구분, 프론트의 `/login` 디스패처가 둘이라는 사실, `catch` 없는 403 수신 호출부가 정확히 둘이라는 사실)을 모두 실제 파일을 열어 대조했고, 어긋나는 지점에서 설계의 결정을 무너뜨리는 것은 찾지 못했다.

## 기각한 후보

의심했으나 코드 확인으로 반증된 항목이다. 지적이 아니다.

### 방 비밀번호·방장 권한 `ForbiddenException` 이 HTTP 경로로도 샌다?

design.md Context 가 "방 비밀번호·방장 권한은 `ForbiddenException` 이지만 STOMP 경로라 axios 를 타지 않는다"고 단정한다. 이게 틀리면 Decision 6 의 "`catch` 없는 곳은 두 곳" 이 무너진다.

**반증.** `Room.kt:77-81`(`verifyPassword`), `:95-105`(`start`), `:107-117`(`end`) 의 호출부를 `back/src/main` 전수 grep 으로 확인했다. `verifyPassword`/`join` 은 `RoomService.kt:123-124` 를 거쳐 `RoomJoinChannelInterceptor.kt:52,66` 에서만 호출되고, `start`/`end` 는 `RoomStompController.kt:37,43`(`@MessageMapping`)에서만 호출된다. `RoomController.kt:20-47` 의 HTTP 엔드포인트는 `GET /rooms`, `GET /rooms/{roomId}`, `POST /rooms` 세 개뿐이고 join/enter 성격의 엔드포인트가 없다. 설계 주장이 맞다.

### `catch` 없는 호출부가 설계가 말한 둘보다 많다?

`PlaylistsView.tsx:57-58`(`fetchPlaylist`), `:79-81`(목록), `:114`(`fetchFavoritePlaylists`), `RoomCreate.tsx:76-84,92-100,133` 모두 `.catch` 가 없다. Decision 6 은 이들을 언급하지 않는다.

**반증.** 이들은 403 을 받을 수 있는 경로가 아니다. `PlaylistService.kt:84-92` 에서 소유권 검사는 `getWithTracks` 에만 있고 트랙 없는 단건 조회·목록·즐겨찾기 조회에는 없다. 403 을 실제로 받는 6개 서버 지점(`PlaylistService.kt:48,68,79,87`, `RoomService.kt:68`, `FavoritePlaylistService.kt:25`)을 프론트 호출부에 매핑하면 `catch` 가 없는 곳은 `PlaylistWriteView.tsx:46`(`fetchPlaylistWithTracks`, 전수 grep 상 유일 호출부)와 `useRoomSubscription.ts:270`(`fetchRoomDetail`, 유일 호출부) 정확히 둘이다. 위 호출부들이 401 에서 남기는 미처리 rejection 은 인터셉터가 `Promise.reject(error)` 로 끝나는 현재(`api.ts:24-27`)와 동일하며 이번 변경이 새로 만드는 것이 아니다.

### `/login` 으로 보내는 세 번째 주체가 있다?

`PlaylistsView.tsx:259` 가 `window.location.href` 를 쓴다. Context 다이어그램이 디스패처를 둘로만 세고 있어 누락을 의심했다.

**반증.** `front/app` 전수 grep `"/login"` 의 hit 는 `api.ts:25` 와 `Me.tsx:18` 두 곳뿐이다. `PlaylistsView.tsx:259` 는 `/playlists/{id}/modify` 로 가는 수정 버튼이다. 전수 grep `"401|403"` 의 hit 도 `api.ts:24` 하나뿐이라, 상태 코드를 보고 분기하는 다른 지점도 없다.

### 엔트리포인트 교체가 허용 경로·actuator 까지 401 로 만든다?

**반증.** `SecurityConfiguration.kt:27-29` 와 `TestConfiguration.kt:47-49` 모두 `permittedUrls`(`/login/**`, `/html/**`, `/ws/**`)를 `permitAll()` 로 먼저 매칭한 뒤 `anyRequest().authenticated()` 다. 엔트리포인트는 `ExceptionTranslationFilter` 가 인가 거부를 받은 뒤에만 호출되므로 permitAll 경로는 도달하지 않는다. actuator 는 운영(`ManagementSecurityConfiguration.kt:26-33`)·테스트(`TestConfiguration.kt:35-43`) 양쪽에 `@Order(1)` + `EndpointRequest.toAnyEndpoint()` + `anyRequest().permitAll()` 체인이 따로 있어 메인 체인을 타지 않는다. 또한 `anonymous` 를 disable 하는 호출이 체인 어디에도 없어 미인증 요청은 익명 토큰을 달고 엔트리포인트로 가는 기존 경로를 그대로 탄다 — 403 이 401 로 바뀔 뿐 경로가 달라지지 않는다. 스펙의 "인증이 필요 없는 경로는 영향을 받지 않는다" 시나리오가 성립한다.

### 미인증 응답 본문이 바뀌어 소비자가 깨진다?

design.md Decision 1 의 기각 문단은 "`Http403ForbiddenEntryPoint` 는 **본문 없이** 403만 보내고" 라고 쓰는데, 실제로는 그렇지 않다 — spring-security-web 6.4.8 의 `Http403ForbiddenEntryPoint.commence` 는 `response.sendError(SC_FORBIDDEN, "Access Denied")` 를 호출해 컨테이너 ERROR 디스패치를 타므로 Boot 기본 오류 JSON 본문이 붙는다. 반면 교체 대상인 `HttpStatusEntryPoint.commence` 는 `response.setStatus(...)` 한 줄뿐이라 본문이 진짜로 비게 된다. 즉 미인증 응답의 본문은 "그대로"가 아니라 "있던 것이 없어지는" 변화다.

**반증(무엇도 깨지지 않음).** 이 본문을 읽는 소비자가 없다. `front/app` 전수 grep 상 상태 코드 분기는 `api.ts:24` 하나이고 미인증 응답 본문을 파싱하는 코드는 없으며, `back/src/test` 에 미인증 403 을 단언하는 테스트도 없다(단언은 `PlaylistControllerTest.kt:300`, `RoomControllerTest.kt:122` 의 비즈니스 403 둘뿐). `infra/app/nginx.conf` 에도 `proxy_intercept_errors`/`error_page` 가 없어 상태 코드나 본문을 가로채지 않는다. 또 이 문장은 **기각된 대안**의 상태 묘사일 뿐이고, 사실을 바로잡으면 기각 논거("두 경로가 우연히 다른 모양을 낸다는 사실에 기대는 것")는 오히려 강해진다 — 실제로는 양쪽 다 JSON 본문을 갖고 필드 구성만 다르다. 어떤 결정도 뒤집히지 않으므로 지적으로 세우지 않는다.

### `LoginView` 의 `new URL()` 파싱 실패가 이번 변경으로 드러난다?

`LoginView.tsx:7` 은 렌더 본문에서 `new URL(redirectUrlString)` 을 try 없이 호출하므로 `/login?redirectUrl=foo` 같은 상대 주소·깨진 값이 오면 렌더가 던진다. Decision 4 의 분기도 `null` 과 "URL 파싱 성공" 만 그리고 파싱 실패 가지를 정의하지 않는다.

**반증(이번 변경의 결함이 아님).** 현재 코드가 이미 같은 형태이고(`LoginView.tsx:7`), 이번 변경은 앱이 만드는 `redirectUrl` 을 `encodeURIComponent(window.location.href)` 로 **항상 절대 주소**로 고정하므로 파싱 실패 입력의 발생 경로를 넓히지 않는다. 손으로 주소를 조작한 경우에만 재현되는 기존 동작이며, 설계의 어떤 결정도 이 가지의 선택에 좌우되지 않는다.

### Decision 1 의 "호출을 지우면 302" 경고가 근거 없는 겁주기다?

**반증.** `SecurityConfiguration.kt:30-39` 에 `oauth2Login` 블록이 실제로 존재하고 `httpBasic`/`formLogin` 은 `:40-41` 에서 disable 되어 있다. 이 상태에서 `.authenticationEntryPoint(...)` 를 제거하면 남는 기본 엔트리포인트는 리다이렉트 계열이 되며, `TestConfiguration.kt:46-57` 체인에는 `oauth2Login` 이 없어 다른 기본값이 잡히지만 역시 리다이렉트다. "두 파일 모두 명시적으로 교체한다" 는 결론과 tasks 1.1/1.2 가 이와 일치한다. 또한 `HttpStatusEntryPoint.commence` 가 헤더를 전혀 세팅하지 않는 것도 확인했으므로 "`WWW-Authenticate` 가 없어 브라우저 Basic 팝업이 뜨지 않는다" 는 설계 주장도 맞다.

판정: 진입 가능 — 설계의 코드 주장을 `SecurityConfiguration.kt`/`TestConfiguration.kt`/`Room.kt`/`RoomController.kt`/Spring Security 6.4.8 엔트리포인트 구현과 `front/app` 전수 grep 으로 대조했고, 설계·proposal·tasks·spec 사이에서 결정을 무너뜨리는 불일치를 찾지 못했다.
