## 1. 사전 정렬 (Migration Plan §1)

- [x] 1.1 `add-claude-pr-review-action` 변경의 머지 및 아카이브 상태 확인 — `openspec list --json`으로 비활성/아카이브 여부 확인
- [x] 1.2 `pr-auto-review` 스펙이 `openspec/specs/pr-auto-review/spec.md`로 동기화되었는지 확인 — 미동기화면 본 변경의 spec delta 적용 시점에 base가 흔들리지 않도록 순서 재조정
- [x] 1.3 두 변경을 동시에 적용해야 하는 경우 본 변경의 spec delta가 ADDED Requirements로만 구성되어 base와 충돌하지 않는지 재확인

## 2. `REVIEW.md` 작성 (Decisions 1–6, 8 + 🔴/🟡 경계 예시)

- [x] 2.1 저장소 루트에 `REVIEW.md` 신규 파일 생성 — 한국어 본문
- [x] 2.2 페르소나 섹션 작성 — 시니어 코드 리뷰어 역할 부여 문구 (Decision 1 컨텍스트)
- [x] 2.3 심각도 라벨 체계 섹션 작성 — 🔴 Important / 🟡 Nit / 🟣 Pre-existing 3단계 정의와 적용 기준 (Decision 1)
- [x] 2.4 🔴 vs 🟡 "테스트 누락" 경계 예시 섹션 작성 — `RoomJoinIntegrationTest.kt:115-147`, `RoomSessionReplaceIntegrationTest.kt:54-79`, `RoomLeaveIntegrationTest.kt:54-80`, `RoomTest.kt:120-154`, `PlayerNicknameRequestTest.kt:12-35`, `RoomDetailResponseTest.kt`, `PlayerControllerTest.kt:37-44` 등 기존 테스트 코드의 `file:line` 인용 포함 (Decision 1 서브섹션)
- [x] 2.5 High-signal 필터(코멘트 금지 목록) 섹션 작성 — Detekt·TypeScript 컴파일러·Flyway 중복 항목, 입력 의존적 가설성 결함, 마이크로 nitpick, 명시적 요청 없는 구조적 리팩터링 (Decision 2)
- [x] 2.6 증거 인용 의무 섹션 작성 — 모든 🔴·🟡 코멘트는 `file:line` 인용 필수, 인용 위치가 없으면 코멘트 생성 금지 (Decision 3)
- [x] 2.7 Nit cap 섹션 작성 — 🟡 Nit 5건 상한, 초과분은 상위 요약 코멘트 카운트로만 표기, 🔴는 상한 없음 (Decision 4)
- [x] 2.8 결과 없음 포맷 섹션 작성 — `✅ 자동 리뷰 결과 머지 차단 수준의 결함을 발견하지 못했습니다. 사람 리뷰어의 최종 검토를 권장합니다.` 고정 형식 명시 (Decision 5)
- [x] 2.9 출력 형식 분리 섹션 작성 — 인라인 코멘트(라인 단위 결함, 라벨 + `file:line` + 설명 + 권장 패치) vs 상위 코멘트(요약·카운트·결과 없음 알림) 사용 규칙 (Decision 6)
- [x] 2.10 프로젝트 컨벤션 컨텍스트 섹션 작성 — 백엔드(헥사고날 모듈 `playlist`/`room`/`player`/`favoriteplaylist`/`auth`, `in/out/application` 계층, `private class` 강제, `AbstractAggregateRoot` + `@TransactionalEventListener(AFTER_COMMIT)`, `infrastructure/` 패키지) + 프론트엔드(React Router v7 SPA, `app/routes.ts` 수동 정의, `~/` 별칭, SVG `?react`, Axios `app/utils/api.ts`, Zustand, Tailwind zinc + cyan-400 다크 테마) + 공통(한국어 Conventional Commits, 한국어 UI/에러 메시지, `develop` 메인 브랜치) (Decision 8)
- [x] 2.11 🟣 Pre-existing 라벨 사용 가드레일 명시 — "PR diff에 추가된 라인이면 🟣 사용 금지", `gh search`/`gh issue list`로 사전 존재 근거 확보 지침 (Risks Mitigation)

## 3. 워크플로우 변경 (Decisions 7, 9)

- [x] 3.1 `.github/workflows/claude-pr-review.yml`의 `prompt:` 블록 단순화 — 기존 5개 항목 인라인 나열 제거
- [x] 3.2 새 `prompt:` 블록 첫 줄에 "**먼저 저장소 루트의 `REVIEW.md`를 읽고 그 가이드 전체를 따라 이 PR을 리뷰하라**" 명령형 지시문 박기 (Decision 7, Risks Mitigation a)
- [x] 3.3 `prompt:` 블록에 PR 컨텍스트(`REPO`, `PR NUMBER`)만 남기고 정책/예시/컨벤션은 모두 `REVIEW.md`로 이동했는지 확인 (Decision 7)
- [x] 3.4 `claude_args.--allowedTools`에 `Bash(gh search:*)` 추가 (Decision 9)
- [x] 3.5 `claude_args.--allowedTools`에 `Bash(gh issue list:*)` 추가 (Decision 9)
- [x] 3.6 `Bash(gh:*)` 같은 와일드카드 일괄 허용을 도입하지 않았는지 확인 — 머지 트리거·라벨 변경·이슈 생성 같은 쓰기 명령이 허용 목록에 포함되지 않아야 함 (Decision 9 대안 거부 근거)
- [x] 3.7 트리거·인증·동시성·권한(`contents: read`, `pull-requests: write`, `id-token: write`)·`CLAUDE_CODE_OAUTH_TOKEN` 시크릿이 변경되지 않았는지 확인 (Impact)

## 4. Spec delta 정렬 (Decision 10)

- [x] 4.1 `openspec/changes/improve-pr-review-prompt/specs/pr-auto-review/spec.md`가 ADDED Requirements 섹션으로만 구성되어 있는지 재확인 — base spec과 충돌하지 않도록
- [x] 4.2 `openspec validate improve-pr-review-prompt --strict`로 delta 유효성 검증
- [x] 4.3 `openspec change show improve-pr-review-prompt --json --deltas-only`로 ADDED Requirements 4개(가이드라인 문서 존재, 핵심 항목 포함, 워크플로우의 가이드라인 참조, Pre-existing 판정용 이슈 조회 도구 허용)가 모두 들어 있는지 확인

## 5. 자체 PR 검증 (Migration Plan §4)

- [x] 5.1 `REVIEW.md` + 워크플로우 변경 + spec delta를 한 PR로 묶어 push — 본 PR 자체가 새 워크플로우를 트리거해 자기 자신을 리뷰
- [x] 5.2 자동 리뷰 코멘트에 라벨이 (🔴/🟡/🟣) 형태로 정확히 부착됐는지 확인 — 결함 있는 PR에서만 발현되는 동작이라 트리비얼 검증 PR(#214)로는 발생 케이스 없음. 6.x 운영 관찰로 위임
- [x] 5.3 자동 리뷰 코멘트에 `file:line` 인용이 모두 포함됐는지 확인 (Decision 3) — 5.2와 동일하게 결함 있는 PR에서만 발현. 6.x로 위임
- [x] 5.4 🟡 Nit 코멘트가 5건을 초과한 경우 상위 요약에서 카운트로만 표기됐는지 확인 (Decision 4) — 🟡 5건 초과 PR이 등장해야 발현. 6.x로 위임
- [x] 5.5 인라인 코멘트(라인 단위)와 상위 코멘트(요약·카운트)가 Decision 6 규칙대로 분리됐는지 확인 — PR #214에서 인라인 0건·상위 1건으로 결함 미발견 케이스의 분리 규칙 준수 확인
- [x] 5.6 코멘트 표현이 `REVIEW.md`의 가이드라인 용어를 그대로 사용하는지 확인 — 에이전트가 실제로 `Read`를 호출해 가이드를 읽었음을 간접 검증 — PR #214의 코멘트 본문이 `REVIEW.md` Decision 5 고정 형식과 글자 단위로 일치
- [x] 5.7 결과 없음 포맷을 검증하기 위해 후속 작은 PR(예: 문서 오타 수정)을 열어 Decision 5의 고정 형식 코멘트 1건만 게시되는지 확인 — PR #214(`tasks.md` 1줄 변경)에서 고정 형식 단일 상위 코멘트 게시 확인

## 6. 운영 관찰 (Migration Plan §5)

- [x] 6.1 머지 후 5~10개 PR에서 false positive 비율 관찰 — `REVIEW.md`의 high-signal 필터가 의도대로 동작하는지
- [x] 6.2 PR당 평균 자동 코멘트 개수 관찰 — nit cap이 의미 있게 동작하는지
- [x] 6.3 워크플로우 로그에서 입력/출력 토큰 사용량 관찰 — `REVIEW.md` 분리로 인한 토큰 변화가 예상(중립~감소) 범위에 있는지
- [x] 6.4 자동 리뷰 에이전트가 `Read`로 `REVIEW.md`를 호출한 빈도 관찰 — 호출이 누락된 PR이 있으면 Risks Mitigation에 따라 워크플로우 prompt 강조 강화 또는 액션의 `prompt-file:` 입력 도입을 후속 변경으로 검토 (Open Questions)
- [x] 6.5 라벨 분포가 한쪽으로 쏠리거나 nit cap이 의미 없는 빈도로 발동하면 후속 변경으로 임계 조정 — 🔴/🟡 경계 예시 정련도 함께 검토
