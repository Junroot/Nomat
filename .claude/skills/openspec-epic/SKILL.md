---
name: openspec-epic
description: 큰 작업을 여러 OpenSpec change로 분리하고, 그 그룹을 총괄하는 epic 문서(openspec/epic/<name>.md)를 만들거나 갱신한다. 범위 가드가 분리를 권고했을 때, 사용자가 큰 기능을 여러 change로 쪼개려 할 때, "epic 만들어줘"·"여러 change로 나눠줘"·"epic 상태 갱신" 같은 요청, 또는 구성 change가 archive되어 epic 진행 상태를 반영해야 할 때 사용한다.
license: MIT
metadata:
  author: nomat
  version: "1.0"
---

큰 작업을 응집된 여러 OpenSpec change로 분리하고, 그 묶음을 총괄하는 **epic 문서**를 관리한다.

OpenSpec CLI는 epic을 모른다 — epic은 `openspec/epic/`에 두는 마크다운 컨벤션이다. 규약 전체는 `openspec/epic/README.md`에 있으니, 작업 전에 그 파일(특히 표준 템플릿·명명·양방향 링크·생명주기)을 **반드시 읽어라**.

이 스킬은 분해와 등록·상태 추적만 한다. **각 change의 실제 아티팩트(proposal/specs/design/tasks) 작성은 기존 스킬(`/opsx:propose`·`/opsx:ff`·`/opsx:continue`)에 위임**한다.

---

## 모드 판별

먼저 무엇을 할지 가른다.

- 큰 작업 설명이 들어왔다(아직 epic 없음) → **A. 새 epic 분해**
- 기존 epic 이름이 있고 change 추가·상태 변경 요청 → **B. epic 갱신**

`openspec/epic/`와 `openspec/epic/archive/`를 확인해 기존 epic이 있는지 본다.

---

## A. 새 epic 분해

1. **`openspec/epic/README.md`를 읽어라** — 템플릿과 규약을 따른다.

2. **분해안을 제시하고 동의를 받아라.**
   - 작업을 **수직 슬라이스**(독립 PR·독립 롤백 가능한 end-to-end capability)로 나눈다. 계층별 수평 분할(백엔드 전부 / 프론트 전부)은 계층이 실제로 따로 배포될 때만.
   - 각 슬라이스의 범위 한 줄 + 의존 순서를 표로 보인다.
   - 분해안에 사용자 동의를 받는다(**AskUserQuestion**). 동의 없이 디렉터리를 만들지 마라.

3. **epic 이름(kebab-case)을 정하고 epic 문서를 만들어라.**
   - `openspec/epic/<epic-name>.md`에 README의 표준 템플릿대로 작성한다.
   - "구성 change" 표는 합의한 슬라이스로 채우되, 각 change 이름은 `<epic-name>-<순번>-<요약>` 접두사를 **권장**한다. 처음에는 모두 `⬜ draft`.

4. **각 change 디렉터리를 스캐폴드하라.**
   ```bash
   openspec new change "<epic-name>-1-..."
   ```
   합의한 change 수만큼 만든다. (아티팩트는 아직 만들지 않는다 — 위임한다.)

5. **양방향 링크를 건다.**
   - epic → change: 위 표(완료).
   - change → epic: 각 change의 proposal에 첫머리 `**Epic**: [<epic-name>](../../epic/<epic-name>.md)` 라인이 들어가야 한다. proposal을 아직 안 만들었으면, 다음 단계에서 proposal을 쓸 때 이 라인을 넣도록 안내한다(`config.yaml`의 proposal 규칙이 이를 요구한다).

6. **출력 — 다음 행동을 안내하라.**
   - 만든 epic 문서 경로와 구성 change 목록·순서.
   - 안내: "의존 순서상 첫 change부터 작성하세요. `/opsx:ff <첫 change 이름>` 또는 `/opsx:propose`로 시작합니다. 각 change가 archive되면 `/opsx:epic`으로 다시 와 상태를 갱신하세요."

---

## B. epic 갱신

1. 대상 `openspec/epic/<epic-name>.md`를 읽는다(활성에 없으면 `archive/`도 본다).
2. 요청에 맞춰 갱신한다.
   - **change 상태 변경**: 구성 change 표의 상태를 `⬜ draft → 🔨 구현 중 → ✅ archived`로 옮긴다. archive 여부는 `openspec/changes/archive/`에 해당 디렉터리가 있는지로 확인한다.
   - **change 추가**: 새 슬라이스를 표에 추가하고 4번처럼 `openspec new change`로 스캐폴드한다.
   - **범위 변경**: 목표·분리 이유·완료 기준을 갱신한다.
3. **완료 처리**: 구성 change가 **모두 `✅ archived`**이고 완료 기준이 충족되면, epic 문서를 `openspec/epic/archive/<epic-name>.md`로 옮긴다(`git mv` 권장). 이동 사실을 사용자에게 알린다.
4. 현재 진행 상태를 요약한다(완료 N / 전체 M).

---

## 가드레일

- **README가 source of truth** — 템플릿·명명·생명주기는 `openspec/epic/README.md`를 따른다. 규약을 임의로 바꾸지 마라.
- **분해안은 동의 후 진행** — 사용자 합의 없이 change 디렉터리를 만들지 마라.
- **아티팩트는 위임** — 이 스킬은 epic 문서와 change 스캐폴드·상태만 다룬다. proposal/specs/design/tasks 본문은 propose/ff/continue에 맡긴다.
- **양방향 링크 유지** — epic 표와 각 proposal의 `Epic:` 라인이 어긋나지 않게 한다.
- **완료 시 archive 이동** — 모든 구성 change가 archived면 epic도 `openspec/epic/archive/`로 옮긴다.
