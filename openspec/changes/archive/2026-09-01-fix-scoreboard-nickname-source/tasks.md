## 1. 백엔드 — 닉네임 해석 (`back/`, `room` 모듈)

- [x] 1.1 `application/RoundScoreboardAssembler.kt` 신규 (`@Component`). `assemble(scores: List<ScoreEntry>, winnerId: Long?)`가 점수판 id와 승자 id를 **합친 집합으로 `playerService.findByIdIn` 배치 조회 1회**를 수행해 `ScoreEntryResponse` 목록과 승자 닉네임을 만든다. 맵에 없는 id는 `알 수 없음`으로 채우고 **예외를 던지지 않는다**(design.md Decision 4). 어댑터가 아닌 애플리케이션 컴포넌트이므로 `private` 대상이 아니다 — `in/`의 리스너와 `application/`의 서비스 양쪽에서 주입받는다
- [x] 1.2 `application/dto/RoundSnapshotResponse.kt` — `ScoreEntryResponse`에 `nickname` 추가, `RoundSnapshotResponse`에 `winnerNickname`(nullable) 추가
- [x] 1.3 `application/dto/RoundRevealedEventMessage.kt` — `winnerNickname`(nullable) 추가. 점수판 필드는 1.2의 `ScoreEntryResponse` 변경을 그대로 따른다
- [x] 1.4 `in/RoomEventListener.kt`의 `handleRoundRevealed`가 조립기를 거쳐 메시지를 만들도록 변경. **`private class` 가시성을 유지**한다. 도메인 이벤트 `RoundRevealedEvent`는 변경하지 않는다(닉네임은 어댑터에서 붙는 표현 관심사 — design.md Decision 2)
- [x] 1.5 `application/RoundService.kt`의 `getSnapshot`이 같은 조립기로 점수판·승자 닉네임을 채우도록 변경
- [x] 1.6 `application/domain/`·`out/` 무변경 확인 — `RoundStateStore` 포트, Lua CAS, Redis 키 구조를 건드리지 않았는지 확인한다

## 2. 백엔드 — 테스트 (`back/`)

- [x] 2.1 기존 라운드 테스트 구조를 먼저 확인한다 — `RoomRoundLifecycleIntegrationTest`, `RoundStateStoreIntegrationTest`. **No Mocking·Testcontainers·`@IntegrationTest`·`WebSocketStompClient` + Awaitility** 패턴을 그대로 따른다
- [x] 2.2 `RoomRoundLifecycleIntegrationTest:72`의 점수판 단언이 새 응답 형태(`nickname` 포함)에서도 통과하도록 갱신
- [x] 2.3 통합 테스트 추가 — 첫 정답으로 공개된 `ROUND_REVEALED`에 (a) 점수판 각 항목의 닉네임 (b) `winnerNickname`이 실제 닉네임으로 담기는지 검증
- [x] 2.4 통합 테스트 추가 — **핵심 회귀 방어**: 게임 중 퇴장한 플레이어가 승자였던 라운드의 공개 메시지, 그리고 퇴장 이후에도 남는 점수 항목의 닉네임이 방 멤버십과 무관하게 해석되는지 검증. 이슈 #235가 재발하면 이 테스트가 잡는다
- [x] 2.5 통합 테스트 추가 — 타임아웃 공개(`winnerId=null`)에서 `winnerNickname`도 null인지 검증
- [x] 2.6 통합 테스트 추가 — `GET /rooms/{roomId}` 라운드 스냅샷의 점수판 항목에 닉네임이 포함되고, `REVEAL` 단계면 `winnerNickname`도 포함되는지 검증
- [x] 2.7 `./gradlew test` 통과
- [x] 2.8 `./gradlew detekt` 통과

## 3. 프론트엔드 — 조인 제거 (`front/`)

- [x] 3.1 `app/utils/RoundEvent.ts` — `ScoreEntry`에 `nickname` 추가, `RoundRevealedEvent`·`RoundSnapshotResponse`에 `winnerNickname: string | null` 추가. 파일 상단의 "id만 담겨 오므로 방 `players`와 조인" 주석을 갱신한다
- [x] 3.2 `app/utils/scoreboard.ts` — `joinScores` 삭제, `rankScores(scores: ScoreEntry[])`가 정렬만 담당하도록 축소(동점 시 원래 순서 유지는 그대로). `nickname`이 비어 있을 때만 중립 라벨로 degrade 한다 — 백엔드 선배포가 어긋나는 경우를 흡수한다(design.md Decision 4)
- [x] 3.3 `app/hooks/roundReducer.ts` — `winnerNickname`을 상태에 추가하고 `ROUND_REVEALED`·`HYDRATE`에서 채운다. **`scores`와 동일하게 sticky 보존**해 `winnerId`와 수명을 맞춘다. 주석의 "sticky 보존" 설명에 승자 닉네임도 포함되도록 갱신
- [x] 3.4 `app/components/ui/RoundRevealOverlay.tsx` — `players` prop 제거, `round.winnerNickname`을 그대로 표기(`players.find(...) ?? "(퇴장)"` 삭제)
- [x] 3.5 `app/components/ui/RoundResultOverlay.tsx` — `players` prop 제거, `rankScores(round.scores)` 사용
- [x] 3.6 `app/components/ui/RoundPanel.tsx` — `players` prop 제거, `rankScores(round.scores)` 사용
- [x] 3.7 `app/routes/RoomView.tsx` — 세 컴포넌트에 넘기던 `players` 전달 제거. `players` 자체는 참가자 목록·방장 판정에 계속 쓰이므로 **삭제하지 않는다**
- [x] 3.8 `npm run typecheck` 통과
- [x] 3.9 `npm run build` 통과

## 4. 검증

- [ ] 4.1 수동 검증(프론트에 테스트 프레임워크가 없다) — 2인 이상으로 게임을 끝까지 진행한 뒤, **결과 화면이 뜬 상태에서 상위 득점자가 방을 나갔을 때** 남은 사람의 결과 화면에서 이름이 유지되는지 확인한다. 이것이 이슈 #235의 정확한 재현 경로다
- [ ] 4.2 수동 검증 — 정답자가 공개 구간 직후 나갔을 때 `RoundRevealOverlay`의 승자 이름이 유지되는지 확인
- [ ] 4.3 수동 검증 — 게임 중 새로고침(재접속)했을 때 복원된 점수판·승자 이름이 실시간 화면과 동일하게 보이는지 확인
- [x] 4.4 화면 어디에도 `(퇴장)` 문구가 남지 않았는지 확인 — `front/`에서 해당 문자열 검색
