## 1. 백엔드 — 정답 판정에서 포기 게이트 제거 (`back/`)

- [x] 1.1 `application/RoundService.kt` — `submitAnswer()`에서 `if (roundStateStore.isPassing(roomId, playerId)) return` 블록을 제거한다. 나머지 판정 순서(`phase == OPEN` → 트랙 조회 → `AnswerMatcher.matches` → `tryAdvanceOnCorrect`)는 그대로 둔다
- [x] 1.2 `application/RoundService.kt` — `submitAnswer()` KDoc에서 "포기 중인 참가자는 그 라운드의 정답 판정에서 제외된다" 문단을 **삭제하지 말고 교체**한다. 새 문단은 "포기는 라운드를 넘기자는 투표이며 채점 자격과 직교한다"를 서술하고, 반전된 결정임을 알 수 있도록 근거(침묵 실패·정답 유출)를 한 줄로 남긴다. 다음 사람이 "여기서 포기를 봐야 하는 것 아닌가"로 되돌리지 않게 하는 것이 이 주석의 목적이다
- [x] 1.3 `application/domain/RoundStateStore.kt` — `isPassing` 포트 선언과 그 KDoc을 제거한다. 인터페이스에 남은 다른 포트는 손대지 않는다

## 2. 백엔드 — 사라진 포트의 어댑터 정리 (`back/`)

- [x] 2.1 `out/RoundStateStoreImpl.kt` — `override fun isPassing(...)` 구현을 제거한다. 구현체는 계속 `private class` 가시성을 유지한다
- [x] 2.2 `out/RoundStateStoreImpl.kt` — `IS_PASSING_SCRIPT` Lua 스크립트 상수와 그 주석을 제거한다. **`round:{roomId}:passes` Set·`passSeq` 해시 필드와 그것을 읽고 쓰는 나머지 경로(`TOGGLE_PASS`·`onPlayerLeft`·`START_SCRIPT`의 lazy reset·`snapshot`)는 한 줄도 건드리지 않는다** — 포기 기능 자체는 그대로다
- [x] 2.3 `RoundRedisKeys.kt`의 `passes(...)` 키 헬퍼가 여전히 쓰이는지 확인한다(토글·스냅샷 경로에서 쓰이므로 남아야 한다). 쓰이지 않는 상수가 생겼다면 그때만 제거한다

## 3. 백엔드 — 테스트 (`back/`)

기존 Testcontainers 통합 테스트 패턴(`@IntegrationTest`, 실제 Redis)을 그대로 쓴다. 새 mock 인프라를 도입하지 않는다.

- [x] 3.1 `RoomRoundPassIntegrationTest.`포기 중인 참가자의 정답은 승자로 기록되지 않는다`` — 단언을 **뒤집는다**. 테스트명도 `포기 중인 참가자의 정답도 승자로 기록된다`로 바꾸고, 라운드가 `REVEAL`로 전이되고 `winnerId`가 그 참가자이며 점수가 1점 오르는 것을 확인한다
- [x] 3.2 `RoomRoundPassIntegrationTest.`포기를 취소하면 정답 판정이 즉시 복원된다`` — **삭제한다.** 포기와 판정이 직교해지면 "복원"할 대상이 없어 이 테스트는 의미를 잃는다. 포기 취소가 인원수를 되돌리는 것은 별도 토글 테스트가 이미 검증한다
- [x] 3.3 `RoomRoundPassIntegrationTest` — **포기 여부와 무관하게 동일 판정**을 확인하는 테스트를 추가한다: 같은 `OPEN` 라운드에서 포기한 참가자가 먼저 정답을 치면 그가 승자가 되고, 포기하지 않은 참가자와 차이가 없어야 한다
- [x] 3.4 `RoundStateStoreIntegrationTest.`isPassing_라운드가 바뀌면 이전 라운드의 포기는 유효하지 않다`` — **지우지 말고 이관한다.** 이 테스트가 지키는 것은 `isPassing` 메서드가 아니라 **lazy reset 판별식**(`passSeq == roundSeq`)이다. `snapshot(roomId, playerId)!!.passing`으로 같은 성질을 검증하도록 바꾸고, 테스트명에서 `isPassing_` 접두사를 `snapshot_`으로 고친다. `passes` Set에 잔재가 남아 있다는 기존 단언(`containsExactly("1")`)은 유지한다 — 판별식이 잔재를 걸러낸다는 것이 이 테스트의 핵심이다
- [x] 3.5 `RoundStateStoreIntegrationTest.`start_이전 게임의 포기 상태는 새 게임으로 이월되지 않는다`` — 마지막 줄 `assertThat(roundStateStore.isPassing(roomId, 1L)).isFalse()`만 제거한다. 같은 테스트의 `snapshot.passing`·`snapshot.passedCount` 단언이 같은 성질을 이미 덮으므로 커버리지 손실이 없다
- [x] 3.6 `RoomRoundPassStompIntegrationTest` — 변경 없음을 확인한다. 포기 현황 전파·임계 전이·포기자 채팅 방송은 이 change의 영향을 받지 않아야 한다. 실패하면 범위를 벗어난 것을 건드린 것이다
- [x] 3.7 `isPassing` 참조가 `back/` 전체에서 0건인지 확인한다(`grep -rn "isPassing" back/src`)
- [x] 3.8 `./gradlew test` 통과
- [x] 3.9 `./gradlew detekt` 통과

## 4. 프론트엔드 — 변경 없음 (`front/`)

- [x] 4.1 `ChatInput.tsx`·`PassControl.tsx`·`roundReducer.ts`·`RoomView.tsx`에 변경이 없음을 확인한다. 프론트는 이 게이트를 몰랐으므로 고칠 것이 없다 — 여기에 손이 갔다면 범위를 벗어난 것이다
- [x] 4.2 빌드 게이트(`npm run typecheck`·`npm run build`)는 프론트 변경이 없으므로 실행하지 않는다

## 5. 수동 검증

로컬 프로파일(Testcontainers)로 2인 이상 방을 만들어 `PLAYING`까지 진행한 뒤 확인한다.

- [ ] 5.1 **주 수정** — 포기(`Shift+Enter` 또는 컨트롤 클릭)를 누른 뒤 정답을 입력하면 라운드가 즉시 `REVEAL`로 전이되고 본인이 승자로 표시되며 점수가 오르는지 확인
- [ ] 5.2 **포기 표시 초기화** — 5.1 직후 다음 라운드가 열렸을 때 포기 인원수가 0이고 본인 포기 상태가 해제되어 있는지 확인
- [ ] 5.3 **임계 회귀** — 2/3 임계에 도달하면 종전대로 승자 없이 즉시 공개되는지 확인
- [ ] 5.4 **토글 회귀** — 포기를 눌렀다 취소하면 인원수가 1 줄고, 다른 참가자 화면에도 반영되는지 확인
- [ ] 5.5 **채팅 방송 회귀** — 포기 상태에서 보낸 일반 채팅과 정답 채팅이 모두 피드에 정상 표시되는지 확인
- [ ] 5.6 **오답 회귀** — 포기 상태에서 오답을 치면 라운드가 전이되지 않는지 확인(게이트 제거가 "무조건 전이"로 잘못 구현되지 않았는지)
- [ ] 5.7 **마감 경로 회귀** — 아무도 맞히지 않고 임계에도 도달하지 않으면 종전대로 마감 시각에 승자 없이 공개되는지 확인
