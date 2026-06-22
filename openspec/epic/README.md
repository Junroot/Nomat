# OpenSpec Epic

큰 작업을 여러 OpenSpec change로 분리할 때, 그 change 그룹을 총괄하는 문서를 여기에 둔다. 전체 목표·분리 이유·구성 change 목록·진행 상태를 한곳에 모아, 맥락이 각 proposal에 흩어지지 않게 한다.

OpenSpec CLI는 epic을 모른다 — `openspec validate`·`openspec status`는 epic을 추적하지 않는다. epic은 **순수 마크다운 컨벤션**이며, 정합성은 `openspec-epic` 스킬(`/opsx:epic`)과 `openspec-change-reviewer` 에이전트가 지킨다.

## 언제 만드나

`openspec/config.yaml`의 proposal 범위 가드가 분리를 권고하고 사용자가 동의했을 때, 또는 처음부터 큰 작업을 여러 change로 나눌 때. `/opsx:epic`으로 시작한다.

## 구조와 생명주기

- **활성 epic**: `openspec/epic/<epic-name>.md` (단일 파일)
- **완료된 epic**: 구성 change가 모두 archive되면 `openspec/epic/archive/<epic-name>.md`로 이동한다 (`openspec/changes/archive/`와 대칭).

## 명명

- epic 이름은 kebab-case (예: `replace-debezium-cdc`).
- 구성 change 이름은 `<epic-name>-<순번>-<요약>` 접두사를 **권장**한다 (예: `replace-debezium-cdc-1-add-outbox`). 강제는 아니다 — 소속의 source of truth는 아래 양방향 링크다.

## 양방향 링크

CLI가 추적하지 않으므로 링크는 사람과 에이전트가 유지한다.

- **epic → change**: epic 문서의 "구성 change" 표.
- **change → epic**: 각 change `proposal.md` 첫머리의 `**Epic**: [<name>](../../epic/<name>.md)` 라인.

## 표준 템플릿

새 epic은 아래 구조를 따른다. `openspec-epic` 스킬과 검토 에이전트가 이 템플릿을 기준으로 삼는다.

```markdown
# Epic: <제목>

## 목표

<이 epic이 달성하려는 전체 결과와, 왜 지금 필요한가. 개별 change가 아니라 묶음 전체의 "왜".>

## 분리 이유

<왜 하나의 change로 하지 않고 나눴는가 — 범위 가드 신호 중 무엇이 걸렸는지 명시.>

## 구성 change

순서·의존 관계와 현재 상태를 함께 적는다. 상태가 바뀌면 이 표를 갱신한다.

| # | change | 범위 (한 줄) | 의존 | 상태 |
|---|--------|------------|------|------|
| 1 | `<epic-name>-1-...` | ... | - | ⬜ draft |
| 2 | `<epic-name>-2-...` | ... | 1 | ⬜ draft |

상태 표기: ⬜ draft(스캐폴드만) · 📝 설계됨(아티팩트 작성, 구현 전) · 🔨 구현 중 · ✅ archived

## 전체 완료 기준 (Definition of Done)

- [ ] <구성 change가 모두 끝났을 때 epic 전체가 충족해야 할 조건>

## 영향 범위

- **서브프로젝트**: back / front / infra 중 해당하는 것
- **도메인 모듈**: playlist / room / player / favoriteplaylist / auth 중 해당하는 것
```
