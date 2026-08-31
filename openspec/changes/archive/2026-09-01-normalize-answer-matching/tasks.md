## 0. 선행 조건

- [ ] 0.1 `fix-additional-title-collation`이 완료·배포되었는지 확인한다. 탁점은 접지 않으므로 `ハハ`/`ババ` 조합은 정규화로 걸러지지 않고 `utf8mb4_unicode_ci`에서 여전히 PK 충돌을 일으킨다 — 정규화는 콜레이션 수정을 대체하지 못한다

## 1. back — 정규화 규칙

- [x] 1.1 `common/normalize/TitleNormalizer.kt` 작성 — NFKC → `lowercase(Locale.ROOT)` → 히라가나→가타카나 → 작은 가나→큰 가나 → `[^\p{L}\p{N}]` 제거 → 빈 결과면 공백만 제거한 값으로 폴백. **단계 순서가 규칙의 일부**다(`design.md` 구현 순서 의존). 도메인 규칙이므로 `infrastructure/`가 아니라 `common/`에 둔다
- [x] 1.2 `lowercase()`는 반드시 `Locale.ROOT`로 — 터키어 로케일에서 `I`가 `ı`로 변환되는 문제를 피한다
- [x] 1.3 `TitleNormalizer` 단위 테스트 — `design.md` Decision 1의 축별 표를 그대로 케이스로 옮긴다. **접지 않는 축(탁점·장음·괄호 내용)의 반례를 반드시 포함**한다. 접는 케이스만 검증하면 규칙이 과하게 관용적으로 바뀌어도 통과한다
- [x] 1.4 폴백 경계 테스트 — `★`·`!!!`·`! ! !`처럼 문자·숫자가 없는 값이 빈 문자열이 되지 않고, `!!!`과 `! ! !`이 같은 키가 되는지 검증한다

## 2. back — 정답 판정과 중복 제거

- [x] 2.1 `room/application/domain/AnswerMatcher.kt`가 자체 `normalize`를 버리고 `TitleNormalizer`에 위임하도록 변경한다. 규칙을 서술한 KDoc(현재 "모든 공백을 제거하고 대소문자를 무시")도 새 규칙으로 갱신한다
- [x] 2.2 `AnswerMatcherTest`에 표기 흔들림 케이스를 추가한다 — 가나 4축·구두점·기호. 탁점이 다른 입력은 **오답**으로 판정되는지도 검증한다
- [x] 2.3 `playlist/application/dto/PlaylistCreationRequest.kt`의 `PlaylistCreationRequestTrack`이 정규화 키 기준으로 추가 정답 중복을 **조용히 접도록** 한다. 400을 던지지 않는다 — 레거시 데이터를 가진 플레이리스트가 편집 불가로 잠기기 때문이다(`design.md` Decision 6)
- [x] 2.4 접을 때 **어느 쪽 원문을 남길지** 정한다 — 먼저 등록된 값을 남기고 이후 중복을 버리는 것이 사용자 기대에 가깝다. 결정을 코드 주석에 남긴다
- [x] 2.5 통합 테스트 — 표기만 다른 두 추가 정답을 담아 요청하면 하나만 저장되고 **요청은 성공**하는지 검증한다. 기존 `PlaylistControllerTest`·`PlaylistStep` 구조와 Testcontainers 패턴을 먼저 확인하고 동일한 방식으로 작성한다. 새 mock 인프라를 도입하지 않는다
- [x] 2.6 저장된 값이 **정규화 키가 아니라 입력 원문**인지 검증한다(`design.md` Decision 3). 이 테스트가 없으면 쓰기 시점 정규화로 잘못 구현되어도 통과한다
- [x] 2.7 `./gradlew test` 통과
- [x] 2.8 `./gradlew detekt` 통과
- [x] 2.9 `PlaylistCreationRequestTrack.additionalTitles`를 `Set<String>` → `List<String>`으로 바꾼다 — Jackson이 `Set`을 `HashSet`으로 역직렬화해 입력 순서를 지우므로 2.4의 "먼저 온 값을 남긴다"가 성립하지 않는다(구현 중 발견, `design.md` Decision 8). JSON 계약은 그대로이고 호출부(`LocalDataSeeder`·테스트)는 `setOf` → `listOf`로 기계적 치환

## 3. front — 사전 차단과 안내

- [x] 3.1 `app/utils/titleNormalizer.ts` 작성 — `TitleNormalizer.kt`와 동일한 파이프라인. `[^\p{L}\p{N}]`에는 `u` 플래그가 필요하다. 백엔드 구현이 소스 오브 트루스임을 파일 상단 주석에 남긴다
- [x] 3.2 `AdditionalTitleEditor.tsx`의 중복 검사를 문자열 일치에서 정규화 키 일치로 교체한다
- [x] 3.3 차단 시 **이유를 알린다.** 문구는 "중복입니다"가 아니라 **"이미 등록된 정답으로 인정됩니다"** 계열로 — 사용자 눈에는 다른 글자이므로 차단이 아니라 이미 커버된다는 뜻으로 읽혀야 한다(`design.md` Decision 4). 현재는 아무 피드백 없이 `return`한다
- [x] 3.4 `AdditionalTitleEditor.tsx:26`의 `additionalTitle.trim.length` 괄호 누락을 고친다. 상한 상수도 `Track.MAX_TITLE_LENGTH`(100)에 맞춰 `maxTitleLength`와 일치시킨다 — 현재 50/100/100으로 세 값이 어긋나 있다
- [ ] 3.5 수동 검증 — 프론트에 테스트 프레임워크가 없다. (a) `マイウェイ` 등록 후 `マイ・ウェイ`·`まいうぇい`·`ﾏｲｳｪｲ`가 차단되고 안내가 뜨는지 (b) `ハハ` 등록 후 `ババ`는 **차단되지 않는지** (c) 100자 초과 입력이 실제로 막히는지 (d) 한글 조합 중 엔터(`isComposing`)가 여전히 무시되는지
- [x] 3.6 `npm run typecheck` 통과
- [x] 3.7 `npm run build` 통과

## 4. 통합 검증

- [ ] 4.1 실제 게임 진행으로 확인 — 일본곡 트랙 하나로 방을 만들어 가나 표기를 바꿔 입력했을 때 정답 처리되는지, 탁점을 바꿔 입력했을 때 오답 처리되는지
- [ ] 4.2 진행 중인 방이 있는 상태로 배포했을 때 판정이 넓어지는 방향으로만 바뀌고 오작동이 없는지 확인한다
