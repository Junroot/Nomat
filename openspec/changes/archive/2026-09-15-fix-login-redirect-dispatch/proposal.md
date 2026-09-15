## Why

**Discord 로그인을 마쳐도 원래 화면으로 돌아오지 않고 로그인 화면에 그대로 머무는 일이 종종 있다.** 팝업은 정상적으로 닫히고 쿠키도 발급되어 있어서, 사용자가 새로고침하면 그제야 들어가진다.

원인은 하나가 아니라 **둘**이며, 둘 다 같은 증상으로 수렴한다.

### 원인 1 — 로그인 화면으로 보내는 주체가 둘이고, 순서가 비결정적

`LoginView`의 복귀 로직은 `redirectUrl` 쿼리 파라미터가 있을 때만 동작한다. 팝업이 닫혔는지 감시하는 `setInterval` 블록 전체가 `if (redirectUrl)` 안에 들어 있기 때문이다(`LoginView.tsx:74-81`). 즉 **`redirectUrl` 없이 `/login`에 도착하면 로그인에 성공해도 복귀 경로가 아예 존재하지 않는다.**

그런데 `/login`으로 보내는 코드가 두 곳이고, 한쪽만 `redirectUrl`을 붙인다.

```
                   로그아웃 상태로 메인(/) 진입
                             │
             ┌───────────────┴────────────────┐
             ▼                                ▼
    RoomsView: fetchRooms()          NavigationBar → Me: fetchMe()
             │                                │
          403 ▼                            403 ▼
   ┌──────────────────────┐        ┌──────────────────────┐
   │ api.ts:24 인터셉터   │        │ api.ts:24 인터셉터   │  ← 여기도 먼저 발화
   │ href = "/login"      │        │ href = "/login"      │
   │    ❌ redirectUrl 없음│        └──────────┬───────────┘
   └──────────────────────┘                   ▼
                                   ┌──────────────────────┐
                                   │ Me.tsx:18 catch      │
                                   │ href = "/login?      │
                                   │    redirectUrl=…"    │  ✅
                                   └──────────────────────┘

   같은 태스크 안의 두 번 할당은 뒤가 이긴다 → redirectUrl 버전이 남는다.
   그러나 fetchRooms 응답이 "나중에" 도착하면 그 bare "/login" 이 마지막 할당이 된다.
```

**어느 응답이 마지막으로 도착하느냐가 최종 URL을 결정한다.** 네트워크 상황에 좌우되므로 재현이 간헐적이다. bare `/login`에 도착한 사용자는 로그인에 성공해도 화면이 그대로다.

여기에 `Me`가 **렌더 본문에서** `fetchMe()`를 호출하는 것(`Me.tsx:14-19`, `useEffect` 밖)이 기름을 붓는다. `me`가 `null`인 매 렌더마다 재호출되므로 403이 여러 번 터지고, 그만큼 경쟁에 참여하는 할당이 늘어난다.

### 원인 2 — 403이 "미인증"과 "권한 없음"을 겸직한다 (100% 재현)

`SecurityConfiguration.kt:45`가 `Http403ForbiddenEntryPoint`를 쓰므로 **미인증도 403**이다. 그런데 `ForbiddenException`(→403)은 **로그인된 사용자의 비즈니스 규칙 위반**에도 쓰인다. 프론트는 두 경우를 상태 코드로 구분할 수 없고, 인터셉터는 403이면 무조건 로그인 화면으로 보낸다.

```
  ForbiddenException → 403  (로그인되어 있는데도 발생하는 HTTP 경로)
  ───────────────────────────────────────────────────────────────────────
  PlaylistService.kt:79          플레이리스트 상한 초과   POST   /playlists
  FavoritePlaylistService.kt:25  즐겨찾기 상한 초과       POST   /favorite-playlists
  PlaylistService.kt:87          남의 플레이리스트 트랙 조회 GET  /playlists/{id}?includeTracks
  PlaylistService.kt:48          남의 플레이리스트 수정   PUT    /playlists/{id}
  PlaylistService.kt:68          남의 플레이리스트 삭제   DELETE /playlists/{id}
  RoomService.kt:68              방 멤버 아닌 방 조회     GET    /rooms/{roomId}
                                 │
                                 ▼
                 api.ts:24  403 → window.location.href = "/login"
                                 │
                                 ▼
            로그인되어 있는데 로그인 화면으로 튕김 + redirectUrl 없음
                       → 로그인해도 안 돌아감 (원인 1과 같은 종착지)
```

즉 **즐겨찾기를 상한 넘게 누르면 로그인 화면에 갇힌다.** 이쪽은 경쟁이 아니라 결정적이다.

방 비밀번호 불일치·방장 권한(`Room.kt:79,101,112`)도 `ForbiddenException`이지만 **STOMP 경로**(`RoomStompController`)라 axios 인터셉터를 타지 않는다. 위 6개가 HTTP 경로의 전부다.

### 부수적으로 드러난 것 — 이미 있는 에러 UX가 죽어 있다

403을 받는 호출부 대부분은 **이미 제대로 된 `catch`를 갖고 있다.** 인터셉터가 먼저 페이지를 날려버려 그 코드가 실행되지 않을 뿐이다.

| 상황 | 이미 작성된 처리 | 사용자가 실제로 보는 것 |
|---|---|---|
| 플레이리스트 상한 초과 | `PlaylistWriteView.tsx:152` — 서버 `message`를 그대로 토스트 | 로그인 화면으로 튕김 |
| 즐겨찾기 상한 초과 | `PlaylistsView.tsx:116-118` — 낙관적 갱신 롤백 + 토스트 | 로그인 화면으로 튕김 |
| 남의 플레이리스트 삭제 | `PlaylistsView.tsx:130-132` — 토스트 | 로그인 화면으로 튕김 |

백엔드가 한국어로 써 둔 안내 문구(`"플레이어는 최대 N개의 플레이리스트를 만들 수 있습니다."`)를 **사용자가 볼 수 있는 경로가 현재 존재하지 않는다.**

### 이 변경이 하는 일

세 가지를 한 번에 처리한다.

1. **서버가 두 실패를 구분해 알린다** — 미인증은 401, 권한 없음은 403
2. **프론트에서 로그인 리다이렉트의 주체를 하나로 만든다** — 인터셉터만, 중복 가드와 함께
3. **복귀 경로가 항상 존재하게 한다** — `redirectUrl`을 항상 붙이고, 없어도 `/`로 폴백

## What Changes

### back/ — 미인증 응답을 401로 분리 (`infrastructure/security`)

- `SecurityConfiguration.kt:45`의 `Http403ForbiddenEntryPoint()`를 `HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)`로 **교체**한다. 호출 자체를 지우면 `oauth2Login`의 기본 엔트리포인트가 되살아나 Discord 인가 엔드포인트로 302를 뱉고, 브라우저가 이를 따라가면 CORS로 막혀 axios가 상태 코드조차 읽지 못한다(design.md Decision 1)
- `TestConfiguration.kt:54`(테스트용 운영 체인 복제본)도 같은 값으로 교체한다. 여기서 누락하면 통합 테스트가 운영과 다른 계약을 검증하게 된다
- 두 파일의 `Http403ForbiddenEntryPoint` import를 제거한다 — 저장소에서 이 이름은 완전히 사라진다
- `ManagementSecurityConfiguration.kt:17`의 KDoc이 이 이름을 언급하므로 문구를 갱신한다(actuator 전용 체인이 필요한 이유 자체는 그대로 유효하다 — Alloy가 403 대신 401을 맞게 될 뿐)
- **비즈니스 403은 손대지 않는다.** `ForbiddenException`과 `GlobalControllerAdvice`의 매핑은 그대로다

### back/ — 계약을 고정하는 테스트 추가

- 쿠키 없이 보호된 엔드포인트를 호출하면 **401**임을 단언하는 테스트
- 로그인한 사용자가 남의 리소스를 건드리면 **403**임을 단언하는 테스트(기존 `PlaylistControllerTest.kt:300`, `RoomControllerTest.kt:122`가 이미 후자를 덮으므로, 전자를 새로 더한다)

### front/ — 인증 실패 디스패치 일원화 (`app/utils/api.ts`)

- 인터셉터가 **401에서만** 로그인 화면으로 보낸다. 403은 그대로 `reject`되어 호출부로 흘러간다
- 리다이렉트를 `redirectToLogin()` 한 함수로 모으고, **모듈 스코프 플래그로 중복 호출을 무시**한다. 몇 개의 요청이 동시에 401을 받든 첫 번째만 이동시킨다 — 경쟁의 원천 자체가 사라진다
- 복귀 주소를 `encodeURIComponent(window.location.href)`로 **항상** 붙인다. 현재 `Me.tsx:18`은 인코딩하지 않아 `&`가 포함된 URL이 잘린다

### front/ — 로그인 화면의 복귀 보장 (`app/routes/LoginView.tsx`)

- `redirectUrl`이 없으면 **`/`로 폴백**한다. 팝업 감시 `setInterval`이 `if (redirectUrl)` 밖으로 나와 **항상** 돌게 되므로, 어떤 경로로 `/login`에 도착하든 로그인 후 화면이 이동한다
- 외부 origin 차단(`LoginView.tsx:9`)은 폴백 판정 **이전에** 그대로 유지한다

### front/ — `me` 적재를 렌더 밖으로 (`app/stores/MeStore.ts`, `app/components/ui/Me.tsx`)

- `MeStore`에 `ensureMe()`를 추가한다. 진행 중인 요청을 재사용해 **동시 호출을 합친다**
- `Me`는 렌더 본문 대신 `useEffect`에서 `ensureMe()`를 한 번 부른다. `me`를 누가 적재하는가라는 책임이 스토어로 옮겨간다

### front/ — 403 전파에 대비한 호출부 보강

인터셉터가 더는 페이지를 날리지 않으므로, `catch`가 없는 두 곳이 미처리 rejection + 빈 화면이 된다.

- `PlaylistWriteView.tsx:46` `fetchPlaylistWithTracks(...)` — 남의 플레이리스트 수정 URL 직접 진입
- `useRoomSubscription.ts:270` `fetchRoomDetail(...)` — 방 멤버가 아닌 상태의 방 조회

### infra/ — 변경 없음

## Capabilities

### New Capabilities

- `login-redirect` — 인증 실패를 어떻게 분류하고, 로그인 화면으로 어떻게 보내고, 로그인 후 어디로 돌려보내는지에 대한 계약

### Modified Capabilities

- 없음

## Impact

- **서브프로젝트**: `back/`, `front/`. `infra/` 영향 없음
- **헥사고날 계층**: `infrastructure/security`만(`SecurityConfiguration`). 도메인 모듈(`playlist`/`room`/`player`/`favoriteplaylist`/`auth`)의 `in`/`out`/`application` 코드는 변경 없음
- **DB 스키마 / ES 매핑 / Kafka 토픽 / Redis 키**: 영향 없음
- **API 계약 변화**: **미인증 응답이 403 → 401로 바뀐다.** 본문은 여전히 없다. 비즈니스 403(`ForbiddenException` → `{message}` JSON)은 그대로다. 이 API의 소비자는 `front/`가 유일하다
- **인증 쿠키·OAuth 흐름**: 변경 없음. `TokenAuthenticationFilter`, `NomatAuthenticationSuccessHandler`, `HttpCookieOAuth2AuthorizationRequestRepository`, `/html/close.html` 모두 손대지 않는다
- **외부 의존성**: 신규 패키지 없음
- **영향 받는 화면**: 인증이 필요한 전부(`/`, `/rooms/:roomId`, `/playlists`, `/playlists/create`, `/playlists/:id/modify`) + `/login`
- **동작 변화**:
  - 로그인 후 원래 보던 화면으로 **항상** 돌아온다 **(주 수정)**
  - 여러 요청이 동시에 인증 실패해도 로그인 이동은 한 번만 일어난다
  - 로그인되어 있는 상태에서 권한·상한 위반을 해도 **로그인 화면으로 튕기지 않는다.** 대신 해당 화면에 머물며 토스트가 뜬다:
    - 플레이리스트 상한 초과 → 서버 문구 그대로(`"플레이어는 최대 N개의 플레이리스트를 만들 수 있습니다."`)
    - 즐겨찾기 상한 초과 → 별표 원복 + `"즐겨찾기 변경에 실패했습니다."`
    - 남의 플레이리스트 삭제 → `"삭제 중 문제가 발생했습니다."`
  - 남의 플레이리스트 수정 URL로 직접 진입하면 빈 화면 대신 안내 후 목록으로 돌아간다
  - `redirectUrl`이 `&`를 포함해도 복귀 주소가 잘리지 않는다
  - 로그아웃 상태로 진입했을 때 `/players/me` 요청이 중복으로 나가지 않는다
- **테스트**: 백엔드는 기존 Testcontainers 기반 `@IntegrationTest` 패턴으로 401/403 계약 테스트를 추가한다. 프론트는 테스트 프레임워크가 없어 `npm run typecheck` + `npm run build`가 자동 게이트이고, 행위 검증은 수동 시나리오(`tasks.md` 9절)로 한다
- **배포**: `back/`(Docker Swarm·EC2)와 `front/`(Netlify) 파이프라인이 분리되어 있어 **한 PR로 머지해도 반영 순서가 보장되지 않는다.** 어느 쪽이 먼저 뜨든 과도기(수 분) 증상은 "로그아웃 사용자가 로그인 화면으로 자동 이동하지 않는다"로 동일하며, 이는 현재 버그보다 나쁘지 않다. 트래픽이 적은 시간대에 배포한다(design.md Decision 7)
- **롤백**: 단일 PR `git revert`. 스키마·마이그레이션·상태 저장이 없어 코드 원복으로 완전 롤백
- **범위 외(후속 과제)**:
  - **팝업 로그인 방식 자체의 폐기**(같은 탭 리다이렉트 전환). 팝업 차단, 사용자의 인증 취소, `window.close()` 실패가 모두 "창이 닫혔다 = 로그인 성공했다"는 추론에 기대고 있어 여전히 오동작한다. 서버가 복귀 주소를 들고 302하는 구조로 바꾸면 이 범주 전체가 사라지지만, `HttpCookieOAuth2AuthorizationRequestRepository` 변경·open redirect 방어·`AuthenticationFailureHandler` 신설·프론트 origin 설정값 추가가 함께 필요해 별도 change로 둔다
  - `AuthenticationFailureHandler` 부재 — Discord 인증이 실패하면 Spring 기본 동작으로 **API 도메인**의 `/login?error`로 302되어 빈 화면이 된다. 현재는 팝업 안에서 일어나 사용자가 창을 닫으면 그만이라 위 후속 change와 함께 다룬다
  - `SecurityConfiguration`과 `TestConfiguration`의 **필터 체인 설정 중복** — 엔트리포인트도 `permittedUrls`처럼 companion으로 빼는 것이 정석이나, 이번에 두 곳을 같이 고치는 것으로 갈음한다
  - 인터셉터가 401에서 **전체 페이지 네비게이션**을 하는 것(SPA 라우팅이 아님) — 진행 중이던 입력이 유실된다. 라우터 통합은 별건
