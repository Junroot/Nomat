## Context

로그인 후 복귀가 간헐적으로 실패한다. 추적해 보면 **"인증 실패를 감지해 로그인 화면으로 보낸다"는 하나의 관심사가 세 곳에 흩어져 있고, 셋이 서로를 모른다**는 구조 문제로 수렴한다.

```
  감지·판정·이동이 분산된 현재                    한 곳으로 모은 뒤

  api.ts 인터셉터 ─┐                              api.ts redirectToLogin()
   (403이면 무조건) │                                  ├ 401에서만
                   ├──▶ /login                        ├ 중복 가드
  Me.tsx catch  ───┤    (서로 덮어씀,                  └ redirectUrl 항상
   (redirectUrl 붙임)│     순서 비결정적)                      │
                   │                                          ▼
  LoginView       ─┘                              LoginView (복귀만 담당)
   (redirectUrl 있을 때만 복귀)                     redirectUrl 없으면 "/"
```

여기에 서버 쪽 전제가 하나 더 깔려 있다. `Http403ForbiddenEntryPoint`(`SecurityConfiguration.kt:45`) 때문에 **미인증과 권한 없음이 같은 403**이라, 프론트가 아무리 잘 정리해도 "로그인하면 해결되는 실패"를 골라낼 수 없다. 프론트만 고치면 경쟁 상태는 사라지지만, 로그인된 사용자가 상한을 초과했을 때 로그인 화면으로 튕기는 오동작은 그대로 남는다.

관련 사실:

- 인증은 쿠키 기반 JWT(`TokenAuthenticationFilter`)이고 세션은 `STATELESS`다
- `ForbiddenException`(→403)은 HTTP 경로 6곳에서 **로그인된 사용자**에게 발생한다. 방 비밀번호·방장 권한은 `ForbiddenException`이지만 STOMP 경로라 axios를 타지 않는다
- 403을 받는 호출부는 대부분 이미 `catch` + 토스트를 갖추고 있으나, 인터셉터가 먼저 페이지를 날려 실행되지 않는다
- `front/`에는 테스트 프레임워크가 없다. 자동 게이트는 `typecheck`/`build`뿐이다

## Goals / Non-Goals

**Goals**

- 로그인 후 복귀 실패를 제거한다 — 어떤 경로로 `/login`에 도착하든 로그인 완료 시 화면이 이동한다
- 여러 요청이 동시에 인증 실패해도 로그인 이동이 정확히 한 번 일어나게 한다
- "로그인하면 해결되는 실패"와 "로그인해도 소용없는 실패"를 상태 코드로 구분한다
- 이미 작성되어 있는 403 에러 UX가 실제로 사용자에게 보이게 한다

**Non-Goals**

- 팝업 로그인 방식을 바꾸지 않는다. 팝업 차단·인증 취소·`window.close()` 실패는 이번 범위 밖이며 후속 change로 다룬다
- `AuthenticationFailureHandler`를 신설하지 않는다
- `ForbiddenException`이 쓰이는 자리(상한·소유권 판정)를 재검토하지 않는다. 일부는 422/409가 더 적절할 수 있으나 별건이다
- 인터셉터의 전체 페이지 네비게이션을 SPA 라우팅으로 바꾸지 않는다
- 프론트에 테스트 프레임워크를 도입하지 않는다

## Decision 1 — 미인증은 401, 권한 없음은 403. 엔트리포인트는 **교체**하지 제거하지 않는다

```
  401 Unauthorized   "네가 누군지 모르겠다"   → 로그인하면 해결됨   → 로그인 화면으로
  403 Forbidden      "너는 알지만 안 된다"    → 로그인해도 무의미   → 호출부가 처리
```

프론트 인터셉터가 원래 하려던 일이 정확히 전자에 대한 대응이다. 401 도입은 새 규약을 만드는 게 아니라 **코드가 이미 전제하던 구분을 상태 코드로 드러내는 것**이다.

```kotlin
// SecurityConfiguration.kt:45, TestConfiguration.kt:54
.exceptionHandling { it.authenticationEntryPoint(HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)) }
```

**`.authenticationEntryPoint(...)` 호출 자체를 지우면 안 된다.** 지우면 Spring Security의 기본값이 되살아난다. `oauth2Login`이 설정되어 있고 클라이언트 등록이 `discord` 하나뿐이므로, `OAuth2LoginConfigurer`가 등록하는 기본 엔트리포인트는 인가 엔드포인트로 **302 리다이렉트**한다.

```
  미인증 XHR (axios, withCredentials)
        │
        ▼  302 → https://discord.com/oauth2/authorize?…
  브라우저가 자동으로 따라감 → Discord 응답에 CORS 헤더 없음
        │
        ▼
  axios: error.response === undefined  → 상태 코드를 읽을 수 없다
        └ 401 분기가 영영 실행되지 않고 로그인 리다이렉트가 통째로 죽는다
```

`TestConfiguration`의 체인에는 `oauth2Login`이 없어 기본값이 `LoginUrlAuthenticationEntryPoint("/login")`이 되지만, 마찬가지로 302를 뱉으므로 결론은 같다. **두 파일 모두 명시적으로 교체한다.**

**대안 — 프론트만으로 구분하기(기각).** `Http403ForbiddenEntryPoint`는 본문 없이 403만 보내고 `ForbiddenException`은 `{message}` JSON을 보내므로, 본문 유무로 구분하는 것이 기술적으로는 가능하다. 그러나 이는 두 코드 경로가 우연히 다른 모양을 낸다는 사실에 기대는 것이고, 누군가 미인증 응답에 본문을 추가하거나 `ForbiddenException`의 메시지를 비우는 순간 조용히 깨진다. 계약을 상태 코드로 명시하는 쪽이 옳다.

### `WWW-Authenticate`와 브라우저 기본 로그인 창

401을 피하고 403을 쓰는 가장 흔한 이유가 브라우저의 네이티브 Basic 인증 팝업이다. 그 팝업은 **`WWW-Authenticate` 헤더가 있을 때만** 뜬다. `HttpStatusEntryPoint`는 `response.setStatus(...)`만 하고 헤더를 붙이지 않으며(헤더를 붙이는 쪽은 `BasicAuthenticationEntryPoint`다), `httpBasic`은 이미 `disable()` 상태다. **팝업은 뜨지 않는다.**

## Decision 2 — 로그인 리다이렉트의 주체는 인터셉터 하나, 중복은 모듈 스코프 플래그로 막는다

`Me.tsx:17-19`의 `catch` 리다이렉트를 제거하고 인터셉터만 남긴다. 그리고 인터셉터 안에서도 **첫 번째 호출만 실제로 이동**시킨다.

```
  요청 A 401 ─┐
  요청 B 401 ─┼─▶ redirectToLogin()  ─┬─ 첫 호출: 플래그 세우고 이동
  요청 C 401 ─┘                       └─ 이후 호출: 즉시 반환(no-op)
```

플래그는 모듈 스코프 변수 하나다. 전체 페이지 네비게이션이므로 문서가 통째로 교체되고 플래그도 함께 사라진다 — 되돌릴 필요가 없다.

이것이 경쟁 상태를 **완화**하는 것이 아니라 **소멸**시킨다는 점이 중요하다. 응답 도착 순서가 최종 URL에 영향을 줄 수 없게 된다.

**대안 — 디바운스/지연(기각).** "잠시 기다렸다가 마지막 것으로 이동"은 타이밍 상수를 하나 더 도입할 뿐 순서 의존을 없애지 못한다. 첫 승자 고정이 더 단순하고 결정적이다.

## Decision 3 — 복귀 주소는 항상, 인코딩해서 붙인다

```ts
`/login?redirectUrl=${encodeURIComponent(window.location.href)}`
```

현재 `Me.tsx:18`은 인코딩하지 않는다. 복귀 주소에 `&`가 들어가면(`/playlists?tab=mine&sort=new`) `searchParams.get("redirectUrl")`이 첫 `&`에서 잘라버려 엉뚱한 곳으로 돌아간다. 현재 라우트에는 `&`가 붙는 경로가 없지만 잠복 결함이므로 같이 고친다.

`LoginView`의 읽기 쪽은 `URLSearchParams`가 디코딩까지 해주므로 변경이 필요 없다.

## Decision 4 — `LoginView`는 복귀 주소가 없으면 `/`로 떨어뜨린다

현재는 팝업 감시 `setInterval` 전체가 `if (redirectUrl)` 안에 있어서(`LoginView.tsx:74-81`), 복귀 주소가 없으면 **감시 자체가 시작되지 않는다.** 폴백을 두면 감시가 **항상** 돌게 되어, Decision 2·3을 우회해 bare `/login`에 도착하는 경로가 생기더라도 사용자가 갇히지 않는다.

```
  redirectUrlString ─▶ null ──────────────▶ target = "/"
                   └─▶ URL 파싱 성공
                          ├ origin 불일치 ─▶ "진입 경로가 잘못되었습니다" (기존 화면 유지)
                          └ origin 일치  ─▶ target = 그 URL
```

외부 origin 차단(`LoginView.tsx:9`)은 **폴백보다 먼저** 판정한다. 폴백이 차단을 무력화해서는 안 된다 — 외부 주소가 들어왔을 때 조용히 `/`로 보내면 잘못된 링크를 사용자에게 알리지 못한다.

이것은 Decision 2가 실패했을 때를 위한 **두 번째 방어선**이지, Decision 2의 대체가 아니다. 둘 다 둔다.

## Decision 5 — `me` 적재는 `MeStore`가 소유하고, 동시 호출을 합친다

현재 `Me`는 **렌더 본문에서** `fetchMe()`를 호출한다(`Me.tsx:14-19`). React의 렌더는 순수해야 하며, 실제로 `me`가 `null`인 매 렌더마다 재호출된다. StrictMode에서는 이중 호출까지 겹친다. 401이 그만큼 여러 번 터져 경쟁에 참여하는 할당이 늘어난다.

책임을 스토어로 옮긴다. `me`를 소유한 주체가 **어떻게 적재되는지도** 소유하는 것이 자연스럽다.

```
  MeStore
    me: MeResponse | null
    inFlight: Promise<void> | null      ← 렌더에 노출되지 않는 내부 상태
    ensureMe(): Promise<void>
      ├ me 있으면            → 즉시 반환
      ├ inFlight 있으면      → 그 프라미스 재사용        ← 동시 호출 합치기
      └ 없으면 fetchMe() 시작 → settle 시 inFlight 비움

  Me 컴포넌트
    useEffect(() => { ensureMe() }, [])   ← 렌더 본문이 아님
```

**실패를 캐시하지 않는다.** 네트워크 오류로 실패하면 `me`는 `null`로 남고 `inFlight`도 비워지므로, 다음 마운트에서 다시 시도한다. 401이면 인터셉터가 이미 페이지를 옮기고 있으므로 재시도 여부는 무의미하다. `ensureMe`는 거부를 삼켜 미처리 rejection을 만들지 않는다.

`Me`는 데스크톱(`NavigationBar`)과 모바일(`MobileHeader`) 양쪽에 있고 `useBreakpoint` 전환으로 마운트/언마운트가 반복될 수 있는데, `me`가 채워진 뒤에는 `ensureMe()`가 즉시 반환하므로 추가 요청이 없다.

**대안 — `Me` 안에서 `useEffect` + `useRef` 가드(기각).** 컴포넌트가 두 자리에 렌더되므로 ref 가드는 인스턴스별로만 작동해 마운트 전환 시 중복 요청을 막지 못한다. 상태가 전역(zustand)인데 적재만 지역에 두는 것도 일관되지 않는다.

## Decision 6 — 403은 호출부로 전파하고, `catch`가 없는 두 곳을 채운다

인터셉터가 403에서 손을 떼면 기존 `catch`들이 되살아난다(대부분 이미 올바르다). 문제는 `catch`가 **없는** 두 곳이다 — 지금은 인터셉터가 페이지를 날려서 결함이 가려져 있었다.

| 위치 | 403이 나는 상황 | 처리 |
|---|---|---|
| `PlaylistWriteView.tsx:46` `fetchPlaylistWithTracks` | 남의 플레이리스트 수정 URL 직접 진입 | 토스트 후 `/playlists`로 돌려보낸다 — 빈 편집 화면에 남겨두지 않는다 |
| `useRoomSubscription.ts:270` `fetchRoomDetail` | 방 멤버가 아닌 상태의 방 조회 | 토스트 후 `/`로 돌려보낸다. 같은 훅이 이미 쓰는 이탈 경로(`navigate("/")`)와 같은 모양 |

후자는 STOMP 연결 성공이 선행되어야 도달하므로 실전 발생 가능성이 낮지만, **미처리 rejection을 남기지 않는다**는 원칙으로 채운다.

에러 문구는 서버가 준 `message`가 있으면 그대로 쓴다 — `PlaylistWriteView.tsx:151-152`가 이미 쓰는 방식이고, 백엔드가 한국어로 작성해 둔 안내를 살리는 것이 이 change의 목적 중 하나다.

## Decision 7 — 한 PR로 배포하고 과도기 수 분을 수용한다

`back/`은 Docker Swarm(EC2), `front/`는 Netlify로 파이프라인이 갈려 있어 **한 PR로 머지해도 반영 순서가 보장되지 않는다.**

```
  백엔드 먼저 반영    미인증 → 401, 구 프론트는 401 미처리 → 로그인 화면으로 이동 안 함
  프론트 먼저 반영    미인증 → 403, 신 프론트는 403을 전파  → 로그인 화면으로 이동 안 함
```

두 경우 증상이 같고, **현재 버그(로그인해도 화면이 안 바뀜)보다 나쁘지 않다.** 새로고침이나 `/login` 직접 진입으로 사용자가 빠져나올 수 있다.

**대안 — 3단계 무중단(기각).** ① 프론트가 401·403 둘 다 로그인 처리 → ② 백엔드 401 전환 → ③ 프론트에서 403 분기 제거. 과도기 증상이 없다는 장점이 있으나, 배포를 세 번 해야 하고 **②·③을 완주하지 않으면 개선이 전혀 없다**(①만으로는 비즈니스 403 오동작이 그대로다). 안전을 사는 대신 중도 정지 위험을 산다. 트래픽이 적은 시간대 배포로 과도기를 수용하는 편이 낫다.

## 위험과 완화

| 위험 | 완화 |
|---|---|
| 엔트리포인트 교체를 `TestConfiguration.kt:54`에 누락 | 테스트 체인이 운영과 다른 계약을 검증하게 된다. 두 파일을 같은 태스크에서 고치고, 새 401 계약 테스트가 이를 강제한다 |
| `.authenticationEntryPoint(...)` 줄을 지워버림 | 기본값이 302를 뱉어 로그인 리다이렉트가 죽는다(Decision 1). 401 계약 테스트가 302를 잡아낸다 |
| 401을 로그인 화면으로 보내지 **않는** 요청이 필요해질 경우 | 현재는 없다. 생기면 인터셉터를 우회하는 별도 클라이언트가 아니라 요청 단위 opt-out으로 다룬다(후속) |
| 403 전파로 새로운 미처리 rejection이 드러남 | `catch` 없는 호출부를 전수 점검한다(tasks 7절). 현재 확인된 것은 두 곳뿐이다 |
| `ensureMe`가 실패를 캐시해 `me`가 영영 비는 경우 | 실패를 캐시하지 않는다(Decision 5). `inFlight`는 settle 시 반드시 비운다 |
| 배포 순서 과도기 | Decision 7. 트래픽 적은 시간대 배포, 롤백은 단일 revert |
| 프론트 자동 테스트 부재 | `tasks.md` 9절의 수동 시나리오가 spec의 각 시나리오에 대응한다 |

## 마이그레이션

- 데이터 마이그레이션 없음. Flyway 변경 없음. ES 매핑·Kafka 토픽·Redis 키 영향 없음
- 인증 쿠키(`TokenService.TOKEN_COOKIE_KEY`)의 이름·수명·도메인이 바뀌지 않으므로 **기존 로그인 세션은 그대로 유지**된다. 배포 시 사용자가 로그아웃되지 않는다
- OAuth 인가 요청 쿠키(`oauth2_auth_request`)도 변경 없음
- 롤백은 단일 PR `git revert`. 서버 상태가 남지 않으므로 코드 원복으로 완전 롤백
