---
name: "OPSX: Onboard"
description: 가이드 온보딩 - 내레이션과 함께 완전한 OpenSpec 워크플로 사이클을 한 바퀴 돌아본다
category: Workflow
tags: [workflow, onboarding, tutorial, learning]
---

사용자가 첫 OpenSpec 워크플로 사이클을 처음부터 끝까지 완주하도록 안내하라. 이건 가르치는 경험이다—각 단계를 설명하면서 사용자의 코드베이스에서 실제 작업을 수행한다.

---

## Preflight

시작하기 전에 OpenSpec CLI가 설치되어 있는지 확인하라:

```bash
# Unix/macOS
openspec --version 2>&1 || echo "CLI_NOT_INSTALLED"
# Windows (PowerShell)
# if (Get-Command openspec -ErrorAction SilentlyContinue) { openspec --version } else { echo "CLI_NOT_INSTALLED" }
```

**CLI가 설치되어 있지 않으면:**
> OpenSpec CLI가 설치되어 있지 않습니다. 먼저 설치한 다음 `/opsx:onboard`로 다시 돌아오세요.

설치되어 있지 않으면 여기서 중단하라.

---

## Phase 1: 환영

다음을 표시하라:

```
## OpenSpec에 오신 것을 환영합니다!

코드베이스의 실제 작업을 사용해 아이디어부터 구현까지 완전한 변경 사이클을 함께 진행하겠습니다. 직접 해보면서 워크플로를 익히게 됩니다.

**우리가 할 일:**
1. 코드베이스에서 작고 실제적인 작업 하나 고르기
2. 문제를 잠깐 탐색하기
3. 변경 만들기 (작업을 담는 컨테이너)
4. 아티팩트 구축: proposal → specs → design → tasks
5. 작업 구현하기
6. 완료된 변경 아카이브하기

**소요 시간:** 약 15-20분

먼저 작업할 거리를 찾는 것부터 시작합시다.
```

---

## Phase 2: 작업 선택

### 코드베이스 분석

코드베이스에서 작은 개선 기회를 훑어보라. 다음을 찾아라:

1. **TODO/FIXME 주석** - 코드 파일에서 `TODO`, `FIXME`, `HACK`, `XXX` 검색
2. **누락된 에러 처리** - 에러를 삼키는 `catch` 블록, try-catch 없는 위험한 작업
3. **테스트 없는 함수** - `src/`와 테스트 디렉터리 교차 대조
4. **타입 문제** - TypeScript 파일의 `any` 타입 (`: any`, `as any`)
5. **디버그 잔재** - 디버그 목적이 아닌 코드의 `console.log`, `console.debug`, `debugger` 구문
6. **누락된 검증** - 검증이 없는 사용자 입력 핸들러

최근 git 활동도 확인하라:
```bash
# Unix/macOS
git log --oneline -10 2>/dev/null || echo "No git history"
# Windows (PowerShell)
# git log --oneline -10 2>$null; if ($LASTEXITCODE -ne 0) { echo "No git history" }
```

### 제안 제시

분석 결과를 바탕으로 구체적인 제안 3-4개를 제시하라:

```
## 작업 제안

코드베이스를 훑어본 결과, 시작하기 좋은 작업 몇 가지입니다:

**1. [가장 유망한 작업]**
   위치: `src/path/to/file.ts:42`
   범위: 약 1-2개 파일, 약 20-30줄
   좋은 이유: [간단한 이유]

**2. [두 번째 작업]**
   위치: `src/another/file.ts`
   범위: 약 1개 파일, 약 15줄
   좋은 이유: [간단한 이유]

**3. [세 번째 작업]**
   위치: [위치]
   범위: [추정치]
   좋은 이유: [간단한 이유]

**4. 다른 걸 하시겠어요?**
   작업하고 싶은 것을 알려주세요.

어떤 작업이 끌리나요? (번호를 고르거나 직접 설명하세요)
```

**아무것도 찾지 못하면:** 사용자가 무엇을 만들고 싶은지 묻는 것으로 대체하라:
> 코드베이스에서 눈에 띄는 빠른 개선거리를 찾지 못했습니다. 추가하거나 고치려고 마음먹어 둔 작은 게 있나요?

### 범위 가드레일

사용자가 너무 큰 것(대형 기능, 여러 날 걸리는 작업)을 고르거나 설명하면:

```
가치 있는 작업이지만, 첫 OpenSpec 완주용으로는 이상적인 것보다 좀 큰 편입니다.

워크플로를 배우는 데는 작을수록 좋습니다—구현 세부에 발목 잡히지 않고 전체 사이클을 볼 수 있으니까요.

**선택지:**
1. **더 작게 쪼개기** - [사용자 작업]에서 가장 작으면서 쓸모 있는 조각이 뭘까요? 어쩌면 [특정 조각]만으로도?
2. **다른 걸 고르기** - 다른 제안 중 하나, 아니면 다른 작은 작업?
3. **그래도 진행하기** - 정말 이걸 다루고 싶다면 해도 됩니다. 다만 시간이 더 걸린다는 점만 알아두세요.

어느 쪽이 좋으세요?
```

사용자가 고집하면 그대로 진행하게 두라—이건 강제가 아닌 가벼운 가드레일이다.

---

## Phase 3: 탐색 시연

작업이 선택되면, 탐색 모드를 잠깐 시연하라:

```
변경을 만들기 전에 **탐색 모드**를 잠깐 보여드릴게요—방향을 정하기 전에 문제를 깊이 생각하는 방식입니다.
```

관련 코드를 1-2분 조사하라:
- 관련된 파일을 읽는다
- 도움이 되면 간단한 ASCII 다이어그램을 그린다
- 고려할 점을 적어둔다

```
## 빠른 탐색

[간단한 분석—무엇을 찾았는지, 고려할 점]

┌─────────────────────────────────────────┐
│   [선택: 도움이 되면 ASCII 다이어그램]  │
└─────────────────────────────────────────┘

탐색 모드(`/opsx:explore`)는 바로 이런 사고를 위한 것입니다—구현 전에 조사하기. 문제를 깊이 생각해야 할 때 언제든 쓸 수 있습니다.

이제 작업을 담을 변경을 만들어 봅시다.
```

**PAUSE** - 진행하기 전에 사용자의 확인을 기다려라.

---

## Phase 4: 변경 만들기

**EXPLAIN:**
```
## 변경 만들기

OpenSpec에서 "변경"은 하나의 작업을 둘러싼 모든 생각과 계획을 담는 컨테이너입니다. `openspec/changes/<name>/`에 있으며 아티팩트—proposal, specs, design, tasks—를 담습니다.

우리 작업을 위해 하나 만들어 보겠습니다.
```

**DO:** kebab-case로 파생한 이름으로 변경을 만들어라:
```bash
openspec new change "<derived-name>"
```

**SHOW:**
```
생성됨: `openspec/changes/<name>/`

폴더 구조:
```
openspec/changes/<name>/
├── proposal.md    ← 왜 하는지 (비어 있음, 채울 예정)
├── design.md      ← 어떻게 만들지 (비어 있음)
├── specs/         ← 상세 요구사항 (비어 있음)
└── tasks.md       ← 구현 체크리스트 (비어 있음)
```

이제 첫 아티팩트인 proposal부터 채워 봅시다.
```

---

## Phase 5: Proposal

**EXPLAIN:**
```
## Proposal

proposal은 이 변경을 **왜** 만드는지, 그리고 큰 틀에서 **무엇**을 다루는지 담습니다. 작업의 "엘리베이터 피치"입니다.

우리 작업을 바탕으로 하나 초안 잡겠습니다.
```

**DO:** proposal 내용을 초안으로 작성하라 (아직 저장하지 말 것):

```
proposal 초안입니다:

---

## Why

[문제/기회를 설명하는 1-2문장]

## What Changes

[무엇이 달라지는지 불릿]

## Capabilities

### New Capabilities
- `<capability-name>`: [간단한 설명]

### Modified Capabilities
<!-- If modifying existing behavior -->

## Impact

- `src/path/to/file.ts`: [무엇이 바뀌는지]
- [해당하면 다른 파일들]

---

이게 의도를 잘 담았나요? 저장하기 전에 조정할 수 있습니다.
```

**PAUSE** - 사용자의 승인/피드백을 기다려라.

승인 후 proposal을 저장하라:
```bash
openspec instructions proposal --change "<name>" --json
```
그런 다음 내용을 `openspec/changes/<name>/proposal.md`에 작성하라.

```
proposal 저장됨. 이건 "왜" 문서입니다—이해가 깊어지면 언제든 돌아와 다듬을 수 있습니다.

다음은 specs입니다.
```

---

## Phase 6: Specs

**EXPLAIN:**
```
## Specs

specs는 우리가 **무엇**을 만드는지를 정밀하고 검증 가능한 용어로 정의합니다. 기대 동작을 명확히 드러내는 requirement/scenario 형식을 씁니다.

이번처럼 작은 작업은 spec 파일 하나면 충분할 수 있습니다.
```

**DO:** spec 파일을 만들어라:
```bash
# Unix/macOS
mkdir -p openspec/changes/<name>/specs/<capability-name>
# Windows (PowerShell)
# New-Item -ItemType Directory -Force -Path "openspec/changes/<name>/specs/<capability-name>"
```

spec 내용을 초안으로 작성하라:

```
spec입니다:

---

## ADDED Requirements

### Requirement: <Name>

<Description of what the system should do>

#### Scenario: <Scenario name>

- **WHEN** <trigger condition>
- **THEN** <expected outcome>
- **AND** <additional outcome if needed>

---

이 형식—WHEN/THEN/AND—은 요구사항을 검증 가능하게 만듭니다. 그대로 테스트 케이스로 읽을 수 있습니다.
```

`openspec/changes/<name>/specs/<capability>/spec.md`에 저장하라.

---

## Phase 7: Design

**EXPLAIN:**
```
## Design

design은 우리가 **어떻게** 만들지를 담습니다—기술적 결정, 트레이드오프, 접근법.

작은 변경이라면 짧아도 됩니다. 괜찮습니다—모든 변경에 깊은 설계 논의가 필요한 건 아니니까요.
```

**DO:** design.md를 초안으로 작성하라:

```
design입니다:

---

## Context

[현재 상태에 대한 간단한 맥락]

## Goals / Non-Goals

**Goals:**
- [달성하려는 것]

**Non-Goals:**
- [명시적으로 범위 밖인 것]

## Decisions

### Decision 1: [핵심 결정]

[접근법과 근거 설명]

---

작은 작업이라면, 이 정도로 과한 설계 없이 핵심 결정을 담아냅니다.
```

`openspec/changes/<name>/design.md`에 저장하라.

---

## Phase 8: Tasks

**EXPLAIN:**
```
## Tasks

마지막으로 작업을 구현 작업 단위로 쪼갭니다—apply 단계를 이끄는 체크박스입니다.

작고, 명확하고, 논리적 순서여야 합니다.
```

**DO:** specs와 design을 바탕으로 작업을 생성하라:

```
구현 작업입니다:

---

## 1. [카테고리 또는 파일]

- [ ] 1.1 [구체적 작업]
- [ ] 1.2 [구체적 작업]

## 2. Verify

- [ ] 2.1 [검증 단계]

---

각 체크박스는 apply 단계에서 하나의 작업 단위가 됩니다. 구현할 준비가 되셨나요?
```

**PAUSE** - 사용자가 구현할 준비가 됐다고 확인할 때까지 기다려라.

`openspec/changes/<name>/tasks.md`에 저장하라.

---

## Phase 9: Apply (구현)

**EXPLAIN:**
```
## 구현

이제 각 작업을 구현하면서 진행하는 대로 체크해 나갑니다. 각 작업을 알리고, specs/design이 접근법에 어떻게 반영됐는지 가끔 짚겠습니다.
```

**DO:** 각 작업마다:

1. 알림: "작업 N 진행 중: [설명]"
2. 코드베이스에 변경 구현
3. specs/design을 자연스럽게 참조: "spec에서 X라고 하니, Y를 합니다"
4. tasks.md에서 완료 표시: `- [ ]` → `- [x]`
5. 간단한 상태: "✓ 작업 N 완료"

내레이션은 가볍게 유지하라—코드 한 줄 한 줄을 과하게 설명하지 마라.

모든 작업이 끝나면:

```
## 구현 완료

모든 작업 완료:
- [x] Task 1
- [x] Task 2
- [x] ...

변경이 구현됐습니다! 한 단계 남았습니다—아카이브합시다.
```

---

## Phase 10: 아카이브

**EXPLAIN:**
```
## 아카이브

변경이 완료되면 아카이브합니다. `openspec/changes/`에서 `openspec/changes/archive/YYYY-MM-DD-<name>/`로 옮겨집니다.

아카이브된 변경은 프로젝트의 결정 이력이 됩니다—나중에 언제든 찾아 무언가가 왜 그렇게 만들어졌는지 이해할 수 있습니다.
```

**DO:**
```bash
openspec archive "<name>"
```

**SHOW:**
```
아카이브됨: `openspec/changes/archive/YYYY-MM-DD-<name>/`

이제 변경은 프로젝트 이력의 일부입니다. 코드는 코드베이스에, 결정 기록은 보존됩니다.
```

---

## Phase 11: 정리 및 다음 단계

```
## 축하합니다!

방금 완전한 OpenSpec 사이클을 완료했습니다:

1. **Explore** - 문제를 깊이 생각
2. **New** - 변경 컨테이너 생성
3. **Proposal** - WHY 담기
4. **Specs** - WHAT을 상세히 정의
5. **Design** - HOW 결정
6. **Tasks** - 단계로 쪼개기
7. **Apply** - 작업 구현
8. **Archive** - 기록 보존

이 같은 리듬은 어떤 규모의 변경에도 통합니다—작은 수정이든 대형 기능이든.

---

## 명령어 레퍼런스

**핵심 워크플로:**

 | Command           | 하는 일                                     |
 |-------------------|--------------------------------------------|
 | `/opsx:propose` | 변경을 만들고 모든 아티팩트 생성 |
 | `/opsx:explore` | 작업 전/중에 문제를 깊이 생각  |
 | `/opsx:apply`   | 변경의 작업 구현              |
 | `/opsx:archive` | 완료된 변경 아카이브                   |

**추가 명령어:**

 | Command            | 하는 일                                                  |
 |--------------------|----------------------------------------------------------|
 | `/opsx:new`      | 새 변경 시작, 아티팩트를 하나씩 단계별로 진행 |
 | `/opsx:continue` | 기존 변경 작업 이어가기                   |
 | `/opsx:ff`       | 패스트 포워드: 모든 아티팩트를 한 번에 생성               |
 | `/opsx:verify`   | 구현이 아티팩트와 일치하는지 검증                  |

---

## 다음은?

정말로 만들고 싶은 것에 `/opsx:propose`를 써보세요. 이제 리듬을 익혔습니다!
```

---

## 우아한 종료 처리

### 사용자가 중간에 멈추고 싶어 할 때

사용자가 멈춰야 한다거나, 잠시 쉬고 싶다거나, 흥미를 잃은 듯 보이면:

```
괜찮습니다! 변경은 `openspec/changes/<name>/`에 저장되어 있습니다.

나중에 멈춘 지점부터 이어가려면:
- `/opsx:continue <name>` - 아티팩트 생성 재개
- `/opsx:apply <name>` - 구현으로 바로 이동 (tasks가 있으면)

작업은 사라지지 않습니다. 준비되면 언제든 돌아오세요.
```

압박 없이 우아하게 종료하라.

### 사용자가 명령어 레퍼런스만 원할 때

사용자가 명령어만 보고 싶다거나 튜토리얼을 건너뛰고 싶다고 하면:

```
## OpenSpec 빠른 레퍼런스

**핵심 워크플로:**

 | Command                  | 하는 일                                     |
 |--------------------------|--------------------------------------------|
 | `/opsx:propose <name>` | 변경을 만들고 모든 아티팩트 생성 |
 | `/opsx:explore`        | 문제를 깊이 생각 (코드 변경 없음)   |
 | `/opsx:apply <name>`   | 작업 구현                            |
 | `/opsx:archive <name>` | 끝나면 아카이브                          |

**추가 명령어:**

 | Command                   | 하는 일                              |
 |---------------------------|-------------------------------------|
 | `/opsx:new <name>`      | 새 변경 시작, 단계별로 진행    |
 | `/opsx:continue <name>` | 기존 변경 이어가기         |
 | `/opsx:ff <name>`       | 패스트 포워드: 모든 아티팩트를 한 번에 |
 | `/opsx:verify <name>`   | 구현 검증               |

`/opsx:propose`로 첫 변경을 시작해 보세요.
```

우아하게 종료하라.

---

## 가드레일

- 핵심 전환점(탐색 후, proposal 초안 후, tasks 후, 아카이브 후)에서 **EXPLAIN → DO → SHOW → PAUSE 패턴을 따르라**
- 구현 중에는 **내레이션을 가볍게 유지하라**—훈계하지 말고 가르쳐라
- 변경이 작더라도 **단계를 건너뛰지 마라**—목표는 워크플로를 가르치는 것이다
- 표시된 지점에서 **확인을 위해 멈추되**, 과하게 멈추지 마라
- **종료를 우아하게 처리하라**—사용자에게 계속하라고 압박하지 마라
- **실제 코드베이스 작업을 사용하라**—가짜 예시를 흉내 내거나 쓰지 마라
- **범위는 부드럽게 조정하라**—더 작은 작업으로 유도하되 사용자의 선택을 존중하라
