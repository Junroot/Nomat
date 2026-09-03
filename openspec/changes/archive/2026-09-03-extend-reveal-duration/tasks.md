## 1. back — 길이 상수 변경

- [x] 1.1 `RoundStateStoreImpl.kt`의 `REVEAL_MILLIS`를 `5_000L` → `10_000L`로 변경한다. 이 상수는 `tryAdvanceOnDeadline`(타임아웃)·`tryAdvanceOnCorrect`(첫 정답) 두 경로가 모두 Lua 스크립트 인자로 넘기므로 **한 곳만 바꾸면 양쪽이 함께 반영된다** — 경로별로 다른 값을 두지 않는다
- [x] 1.2 `RoundStateStoreImpl`은 `out` 계층의 `private class`다. 가시성을 그대로 유지하고 상수를 설정값(`@Value`·프로퍼티)으로 승격하지 않는다 — 방마다 다른 길이를 주는 것은 스펙이 금지하는 성질이며(정답자 유무·방 설정과 무관하게 고정) 이번 범위도 아니다

## 2. back — 테스트

> 새 mock 인프라를 도입하지 않는다. 기존 `@IntegrationTest` + Testcontainers 패턴을 따른다.

- [x] 2.1 `RoundStateStoreIntegrationTest`에 REVEAL 진입 시 기록되는 마감이 **진입 시각 + 10초**임을 검증하는 단언을 추가한다. 상수를 되돌리면 실패해야 한다 — 이 change의 유일한 회귀 판별 지점이다
- [x] 2.2 `RoomRoundEngineIntegrationTest`의 `마지막 라운드가 끝나면 ENDED로 방이 ACTIVE로 복귀한다`가 REVEAL 만료를 **실제로 기다리는 유일한 테스트**다. 현재 `atMost(15초)`는 5초+지터(≈6초) 기준이라 여유가 9초였지만 10초+지터(≈11초)에서는 4초로 줄어 CI에서 불안정해진다. `atMost`를 **20초로 올린다**
- [x] 2.3 나머지 `await`들은 REVEAL **진입**까지만 기다리므로 상한을 건드리지 않는다. 실제로 그러한지 `back/src/test/kotlin/ilpak/nomat/room/` 전체를 훑어 REVEAL **만료**를 기다리는 다른 지점이 없는지 확인한다 (있으면 2.2와 같은 기준으로 조정)
- [x] 2.4 `./gradlew test` 통과
- [x] 2.5 `./gradlew detekt` 통과

## 3. 문서·주석 동기화 — 숫자를 복창하는 자리를 없앤다

> 목표는 "5초를 10초로 고치기"가 아니라 **상수를 복창하는 자리를 지워 다시 드리프트하지 않게 하기**다. 길이를 아는 자리는 `REVEAL_MILLIS` 하나와 `room-game-session` 스펙뿐이어야 한다.

- [x] 3.1 `RoomRoundEngineIntegrationTest.kt:118` 주석 `REVEAL(5초) 후` → 숫자를 빼고 "REVEAL 만료 후"로
- [x] 3.2 `RoomRoundScoreboardNicknameIntegrationTest.kt:123` 주석 `REVEAL 구간(5초)을` → 숫자를 빼고 "REVEAL 구간을"로
- [x] 3.3 `front/app/hooks/useRoundAudioOrchestrator.ts:272` 주석 `클립이 REVEAL(5초)보다 짧을 때` → "클립이 REVEAL 구간보다 짧을 때". **주석 문구만 바꾸고 재생 로직은 건드리지 않는다** — 클립 루프는 그대로 유지한다
- [x] 3.4 `front/app/components/ui/RoundPanel.tsx:26` 주석 `그 5초 동안` → "그 구간 동안"
- [x] 3.5 위 3.3·3.4는 **동작 변경이 없는 주석 수정**이지만 `front/`를 건드리므로 `npm run typecheck`와 `npm run build`를 실행해 통과를 확인한다

## 4. 확인

- [ ] 4.1 로컬(`--spring.profiles.active=local`)에서 2트랙 이상 플레이리스트로 한 게임을 끝까지 돌려, 정답 공개가 약 10초 유지되고 다음 라운드로 자동 진행되는지 육안 확인한다
- [ ] 4.2 같은 실행에서 **클립이 10초보다 짧은 트랙**으로 공개 구간이 무음이 되지 않고 클립이 계속 반복되는지 확인한다 (`proposal.md`의 "알면서 감수하는 것" — 반복 횟수가 늘어난 체감이 실제로 얼마나 거슬리는지 여기서 판단하고, 거슬리면 후속 change 후보로 남긴다)
- [ ] 4.3 공개 구간이 길어진 만큼 다음 곡 선버퍼링 창도 10초로 늘어난다. 라운드 시작 시 재생 지연이 나빠지지 않았는지(악화될 이유는 없으나 회귀가 없는지) 확인한다

