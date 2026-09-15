## 1. 백엔드 — 미인증 응답을 401로 전환 (`back/`)

엔트리포인트를 **교체**한다. 호출을 지우면 `oauth2Login` 기본값이 302를 뱉어 로그인 리다이렉트가 통째로 죽는다(design.md Decision 1).

- [x] 1.1 `infrastructure/security/SecurityConfiguration.kt:45` — `Http403ForbiddenEntryPoint()`를 `HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)`로 바꾼다. `import org.springframework.security.web.authentication.Http403ForbiddenEntryPoint`(:12)를 제거하고 `HttpStatusEntryPoint`·`HttpStatus` import를 더한다
- [x] 1.2 `infrastructure/integration/TestConfiguration.kt:54`(테스트 소스셋의 운영 체인 복제본) — 같은 값으로 바꾸고 :15의 import를 제거한다. **누락하면 테스트가 운영과 다른 계약을 검증한다**
- [x] 1.3 `infrastructure/security/ManagementSecurityConfiguration.kt:17` — KDoc 안의 `Http403ForbiddenEntryPoint` 언급을 갱신한다. actuator 전용 permitAll 체인이 필요한 이유 자체는 그대로다(Alloy가 403 대신 401을 맞게 될 뿐)
- [x] 1.4 `grep -rn "Http403ForbiddenEntryPoint" back/src`가 아무것도 찾지 못하는지 확인한다
- [x] 1.5 `ForbiddenException`·`GlobalControllerAdvice`·`TokenAuthenticationFilter`는 **손대지 않는다**. 비즈니스 403과 그 JSON 본문은 그대로 유지된다

## 2. 백엔드 — 401/403 계약 테스트 (`back/`)

기존 Testcontainers 기반 `@IntegrationTest` 패턴을 그대로 따른다(`PlaylistControllerTest`와 같은 구조 — 생성자 `@Autowired`, `WebTestClient`, `*Step` 픽스처, `util.auth` 확장). **새 mock 인프라를 도입하지 않는다.**

- [x] 2.1 `src/test/kotlin/ilpak/nomat/infrastructure/security/AuthenticationEntryPointTest.kt`를 `@IntegrationTest`로 추가한다
- [x] 2.2 `auth(...)` 없이 `GET /players/me` → `expectStatus().isUnauthorized()`를 단언한다
- [x] 2.3 같은 응답에 `WWW-Authenticate` 헤더가 **없고** 상태가 3xx가 **아님**을 단언한다 — 이 두 단언이 design.md Decision 1의 실패 모드(브라우저 Basic 팝업, Discord로의 302)를 잡는다
- [x] 2.4 유효하지 않은 자격 증명으로 호출해도 401임을 단언한다. 테스트 하네스는 `playerId` 헤더로 인증하므로(`TestAuthenticationFilter`) 숫자가 아닌 값을 넣으면 미인증 경로를 탄다
- [x] 2.5 `permittedUrls`(`/html/**`, `/login/**`)와 actuator 엔드포인트가 무인증으로도 401이 아님을 단언한다 — 전환이 허용 경로까지 막지 않는지 확인
- [x] 2.6 기존 `PlaylistControllerTest.kt:300`(남의 플레이리스트 수정)과 `RoomControllerTest.kt:122`(방 멤버 아닌 방 조회)의 `isForbidden()` 단언이 **그대로 통과하는지** 확인한다. 둘 다 비즈니스 403이라 영향이 없어야 정상이다
- [x] 2.7 로그인 상태에서 발생하는 403이 본문 `message`를 갖는다는 것을 단언하는 테스트를 더한다. 프론트가 이 문구를 토스트로 띄우는 것이 이번 change의 산출물이므로 계약으로 고정한다. **상한(1000개)은 통합 테스트에서 채우기 비현실적이라 소유권 위반(`PUT /playlists/{id}`)으로 덮는다** — 상한 403과 완전히 같은 `ForbiddenException` → `GlobalControllerAdvice` 경로이므로 고정하려는 계약은 동일하다

## 3. 백엔드 — 게이트 (`back/`)

- [x] 3.1 `./gradlew test` 통과
- [x] 3.2 `./gradlew detekt` 통과

## 4. 프론트엔드 — 인증 실패 디스패치 일원화 (`front/`)

`app/utils/api.ts`만 고친다.

- [x] 4.1 모듈 스코프에 `redirectToLogin()`과 중복 가드 플래그를 둔다. 첫 호출만 이동시키고 이후 호출은 즉시 반환한다. 전체 페이지 네비게이션이라 문서가 교체되면서 플래그도 사라지므로 되돌리지 않는다(design.md Decision 2)
- [x] 4.2 `redirectToLogin()`이 `` `${window.location.origin}/login?redirectUrl=${encodeURIComponent(window.location.href)}` ``로 이동하게 한다. **복귀 주소를 항상, 인코딩해서** 붙인다(Decision 3)
- [x] 4.3 인터셉터(:21-30)의 조건을 `error.response?.status === 403`에서 **`401`**로 바꾸고 `redirectToLogin()`을 호출한다. 403은 분기 없이 그대로 `Promise.reject(error)`로 흘려보낸다
- [x] 4.4 `error.response`가 `undefined`인 경우(네트워크 오류·CORS)에는 이동하지 않고 그대로 reject하는지 확인한다 — 기존 동작과 같다
- [x] 4.5 `redirectToLogin`의 짧은 주석에 "로그인 이동의 유일한 주체", "401만 처리하는 이유(403은 로그인해도 해결되지 않음)", "가드가 경쟁 상태를 없애는 방식"을 각 한 줄로 남긴다

## 5. 프론트엔드 — 로그인 화면의 복귀 보장 (`front/`)

`app/routes/LoginView.tsx`만 고친다. 팝업 방식 자체는 바꾸지 않는다(design.md Non-Goals).

- [x] 5.1 외부 origin 차단(:9-22)을 **그대로 먼저** 판정한다. 폴백이 차단을 무력화해서는 안 된다(Decision 4)
- [x] 5.2 `redirectUrl`이 없거나 비어 있으면 이동 목표를 `/`로 정한다. 차단 판정 이후에 적용한다
- [x] 5.3 `goToDiscordLogin`(:71-83)의 `if (redirectUrl)` 래퍼를 제거해 팝업 감시 `setInterval`이 **항상** 돌게 한다. 이동 목표는 5.2에서 정해진 값을 쓴다
- [x] 5.4 `window.open`이 `null`(팝업 차단)일 때의 현재 동작 — 첫 틱에 즉시 `closed`로 판정해 로그인 없이 이동 — 이 그대로 남는다는 점을 코드 주석으로 남긴다. **이번 범위에서 고치지 않으며 후속 change(같은 탭 리다이렉트 전환)에서 사라진다**

## 6. 프론트엔드 — `me` 적재를 스토어로 (`front/`)

- [x] 6.1 `app/stores/MeStore.ts` — `ensureMe(): Promise<void>`를 추가한다. `me`가 있으면 즉시 반환, 진행 중인 요청이 있으면 그 프라미스를 재사용, 없으면 `fetchMe()`를 시작한다(design.md Decision 5)
- [x] 6.2 진행 중 프라미스는 성공·실패 무관하게 settle 시 비운다. **실패를 캐시하지 않는다** — 다음 마운트에서 재시도할 수 있어야 한다
- [x] 6.3 `ensureMe()`가 거부를 삼켜 미처리 rejection을 만들지 않게 한다. 401은 인터셉터가 이미 처리하고 있다
- [x] 6.4 `app/components/ui/Me.tsx` — 렌더 본문의 `fetchMe()` 호출과 `.catch` 리다이렉트(:14-19)를 제거하고 `useEffect(() => { ensureMe() }, [])`로 옮긴다. `compact` 조기 반환(:44)보다 이펙트가 앞서도록 훅 순서를 지킨다
- [x] 6.5 `MeStore` 상단에 `me`의 적재 책임이 스토어에 있는 이유와 동시 호출을 합치는 이유를 주석 한 단락으로 남긴다

## 7. 프론트엔드 — 403 전파에 대비한 호출부 보강 (`front/`)

인터셉터가 더는 페이지를 날리지 않으므로 `catch` 없는 호출부가 드러난다(design.md Decision 6).

- [x] 7.1 `app/routes/PlaylistWriteView.tsx:46` — `fetchPlaylistWithTracks(...)`에 `catch`를 더한다. 서버 `message`가 있으면 그대로 토스트하고 `/playlists`로 돌려보낸다. 빈 편집 화면에 남겨두지 않는다
- [x] 7.2 `app/hooks/useRoomSubscription.ts:270` — `fetchRoomDetail(...)`에 `catch`를 더한다. 토스트 후 `/`로 이동하며, 같은 훅이 이미 쓰는 이탈 경로(`navigate("/")`, :260)와 같은 모양으로 맞춘다. `finally`의 `setIsLoading(false)`는 유지한다
- [x] 7.3 `app/utils/api.ts`의 내보낸 함수들을 호출하는 곳을 전수 확인해 `catch` 없는 곳이 더 없는지 점검한다. 이미 `catch`가 있는 곳(`PlaylistsView.tsx:116,130`, `PlaylistWriteView.tsx:151`, `RoomsView.tsx:41`)은 **손대지 않는다** — 이번 변경으로 비로소 실행되기 시작할 뿐이다
- [x] 7.4 `RoomCreate.tsx`의 플레이리스트 목록 적재 경로(`favoriteError`/`myError` 상태를 쓰는 곳)가 403을 오류 문구로 처리하는지 확인한다. **점검 결과 실패를 전혀 처리하지 않았다** — `favoriteError`/`myError`에 `null` 외의 값이 쓰이는 곳이 없어 오류 UI가 죽은 코드였고, `setFavoriteLoading(false)`가 `.then()` 안에만 있어 실패 시 스피너가 영구히 돌았다. 두 경로에 `.catch`를 더해 문구를 띄우고 로딩을 해제한다. (이 경로는 403이 아니라 401을 받으므로 이번 변경이 새로 만든 문제는 아니다. `loaded`를 함께 세우지 않으면 이펙트가 재발화해 무한 재시도가 된다)

## 8. 프론트엔드 — 게이트 (`front/`)

- [x] 8.1 `npm run typecheck` 통과
- [x] 8.2 `npm run build` 통과

## 9. 검증 — 수동

프론트에 테스트 프레임워크가 없다. `npm run dev` + 로컬 백엔드(`--spring.profiles.active=local`)로 확인한다. 각 항목은 `specs/login-redirect/spec.md`의 시나리오에 대응한다.

- [ ] 9.1 **주 증상** — 쿠키를 지우고 `/`에 진입 → `/login?redirectUrl=…`로 이동하는지(주소창에 파라미터가 **있는지**) 확인 → Discord 로그인 → `/`로 돌아오는지 확인. 10회 반복해 한 번도 bare `/login`에 머물지 않는지 확인한다(수정 전에는 간헐적으로 실패했다)
- [ ] 9.2 **복귀 대상 보존** — 쿠키를 지우고 `/playlists`, `/rooms/:id`에서 각각 진입 → 로그인 후 그 화면으로 돌아오는지 확인
- [ ] 9.3 **단일 이동** — DevTools Network에서 `/`진입 시 401이 여러 건 발생함을 확인하고, 문서 네비게이션이 한 번만 일어나는지 확인
- [ ] 9.4 **bare `/login` 폴백** — 주소창에 `/login`을 직접 입력해 로그인 → `/`로 이동하는지 확인
- [ ] 9.5 **외부 origin 차단** — `/login?redirectUrl=https://example.com/` 진입 시 "진입 경로가 잘못되었습니다"가 뜨고 로그인 버튼이 없는지, `/`로 조용히 이동하지 않는지 확인
- [ ] 9.6 **403이 로그인으로 튕기지 않음(즐겨찾기)** — 즐겨찾기를 상한까지 채운 뒤 하나 더 추가 → 화면이 유지되고 별표가 원복되며 `"즐겨찾기 변경에 실패했습니다."` 토스트가 뜨는지 확인
- [ ] 9.7 **403이 로그인으로 튕기지 않음(플레이리스트 상한)** — 플레이리스트를 상한까지 만든 뒤 하나 더 저장 → **서버 문구**(`"플레이어는 최대 N개의 플레이리스트를 만들 수 있습니다."`)가 토스트로 보이는지 확인. 이 문구가 사용자에게 보이는 것은 이번 변경이 처음이다
- [ ] 9.8 **남의 플레이리스트 수정 진입** — 다른 계정의 플레이리스트 id로 `/playlists/:id/modify`에 직접 진입 → 빈 화면이 아니라 안내 토스트 후 `/playlists`로 돌아오는지, 콘솔에 미처리 rejection이 없는지 확인
- [ ] 9.9 **브라우저 기본 인증 팝업 없음** — 401을 받는 모든 경로에서 브라우저 네이티브 로그인 대화상자가 뜨지 않는지 확인
- [ ] 9.10 **중복 요청 제거** — 로그인 상태로 `/`에 진입해 Network 탭에서 `/players/me`가 한 번만 나가는지 확인. 창 폭을 데스크톱↔모바일로 바꿔 재마운트시켜도 추가 요청이 없는지 확인
- [ ] 9.11 **기존 흐름 회귀** — 로그인 상태에서 방 생성·입장(STOMP), 채팅, 게임 시작/종료, 플레이리스트 CRUD가 종전과 같은지 확인. 방 비밀번호 오입력이 여전히 모달 안 오류로 처리되는지(로그인 화면으로 튕기지 않는지) 확인
- [ ] 9.12 **세션 유지** — 배포 전후로 기존 로그인 쿠키가 유효한지 확인한다. 쿠키 이름·수명·도메인이 바뀌지 않으므로 로그아웃되지 않아야 한다
