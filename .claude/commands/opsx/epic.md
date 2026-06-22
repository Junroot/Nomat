---
name: "OPSX: Epic"
description: 큰 작업을 여러 change로 분리하고 총괄 epic 문서를 만들거나 갱신한다
category: Workflow
tags: [workflow, epic, split, experimental]
---

큰 작업을 응집된 여러 OpenSpec change로 분리하고, 그 묶음을 총괄하는 **epic 문서**를 관리한다.

**입력**: `/opsx:epic` 뒤의 인자는 분리하려는 큰 작업 설명이거나 기존 epic 이름이다. 없으면 무엇을 할지 묻는다.

OpenSpec CLI는 epic을 모른다 — epic은 `openspec/epic/`에 두는 마크다운 컨벤션이다. 규약 전체(표준 템플릿·명명·양방향 링크·생명주기)는 `openspec/epic/README.md`에 있으니 작업 전에 **반드시 읽어라**.

이 커맨드는 분해와 등록·상태 추적만 한다. **각 change의 실제 아티팩트(proposal/specs/design/tasks) 작성은 `/opsx:propose`·`/opsx:ff`·`/opsx:continue`에 위임**한다.

---

## 모드 판별

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

3. **epic 이름(kebab-case)을 정하고 `openspec/epic/<epic-name>.md`를 README 템플릿대로 작성하라.** 구성 change 표는 합의한 슬라이스로 채우고(이름은 `<epic-name>-<순번>-<요약>` 접두사 권장), 상태는 모두 `⬜ draft`.

4. **각 change 디렉터리를 스캐폴드하라.**
   ```bash
   openspec new change "<epic-name>-1-..."
   ```
   합의한 수만큼 만든다. 아티팩트는 아직 만들지 않는다.

5. **양방향 링크를 건다.** epic 표(완료) + 각 proposal 첫머리 `**Epic**: [<epic-name>](../../epic/<epic-name>.md)` 라인(proposal 작성 시 들어가도록 안내 — `config.yaml` proposal 규칙이 요구).

6. **출력**: epic 문서 경로·구성 change 목록·순서를 보이고, "첫 change부터 `/opsx:ff` 또는 `/opsx:propose`로 작성하세요. 각 change가 archive되면 `/opsx:epic`으로 와 상태를 갱신하세요."로 안내한다.

---

## B. epic 갱신

1. 대상 `openspec/epic/<epic-name>.md`를 읽는다(없으면 `archive/`도 본다).
2. 요청에 맞춰 갱신한다.
   - **상태 변경**: 표의 상태를 `⬜ draft → 🔨 구현 중 → ✅ archived`로. archive 여부는 `openspec/changes/archive/`에 디렉터리가 있는지로 확인.
   - **change 추가**: 표에 추가하고 4번처럼 스캐폴드.
   - **범위 변경**: 목표·분리 이유·완료 기준 갱신.
3. **완료 처리**: 구성 change가 모두 `✅ archived`이고 완료 기준이 충족되면 epic 문서를 `openspec/epic/archive/<epic-name>.md`로 옮긴다(`git mv`). 이동 사실을 알린다.
4. 진행 상태 요약(완료 N / 전체 M).

---

## 가드레일

- **README가 source of truth** — 템플릿·명명·생명주기는 `openspec/epic/README.md`를 따른다.
- **분해안은 동의 후 진행** — 합의 없이 change 디렉터리를 만들지 마라.
- **아티팩트는 위임** — epic 문서와 change 스캐폴드·상태만 다룬다.
- **양방향 링크 유지** — epic 표와 각 proposal의 `Epic:` 라인이 어긋나지 않게 한다.
- **완료 시 archive 이동** — 모든 구성 change가 archived면 epic도 `openspec/epic/archive/`로 옮긴다.
