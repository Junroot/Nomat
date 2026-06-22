---
name: "OPSX: Sync"
description: 변경의 delta spec을 메인 spec에 동기화한다
category: Workflow
tags: [workflow, specs, experimental]
---

변경의 delta spec을 메인 spec에 동기화한다.

이것은 **에이전트 주도** 작업이다 - delta spec을 읽고 메인 spec을 직접 편집해 변경 내용을 적용한다. 그 덕에 지능적 병합이 가능하다(예: 요구사항 전체를 복사하지 않고 시나리오만 추가).

**입력**: `/opsx:sync` 뒤에 변경 이름을 선택적으로 지정한다(예: `/opsx:sync add-auth`). 생략하면 대화 맥락에서 추론할 수 있는지 확인한다. 모호하거나 불분명하면 반드시 사용 가능한 변경 목록을 제시해 선택받아야 한다.

**단계**

1. **변경 이름이 없으면 선택받기**

   `openspec list --json`을 실행해 사용 가능한 변경 목록을 가져온다. **AskUserQuestion 도구**로 사용자가 고르게 한다.

   delta spec(`specs/` 디렉터리 아래)이 있는 변경을 보여준다.

   **중요**: 변경을 추측하거나 자동 선택하지 마라. 항상 사용자가 고르게 하라.

2. **delta spec 찾기**

   `openspec/changes/<name>/specs/*/spec.md`에서 delta spec 파일을 찾는다.

   각 delta spec 파일에는 다음과 같은 섹션이 들어 있다:
   - `## ADDED Requirements` - 추가할 새 요구사항
   - `## MODIFIED Requirements` - 기존 요구사항의 변경
   - `## REMOVED Requirements` - 제거할 요구사항
   - `## RENAMED Requirements` - 이름을 바꿀 요구사항 (FROM:/TO: 형식)

   delta spec을 찾지 못하면 사용자에게 알리고 멈춘다.

3. **각 delta spec의 변경을 메인 spec에 적용**

   `openspec/changes/<name>/specs/<capability>/spec.md`에 delta spec이 있는 각 capability에 대해:

   a. **delta spec을 읽어** 의도한 변경을 파악한다

   b. **메인 spec을 읽는다** `openspec/specs/<capability>/spec.md`(아직 없을 수도 있음)

   c. **변경을 지능적으로 적용한다**:

      **ADDED Requirements:**
      - 메인 spec에 요구사항이 없으면 → 추가한다
      - 이미 있으면 → 일치하도록 갱신한다(암묵적 MODIFIED로 취급)

      **MODIFIED Requirements:**
      - 메인 spec에서 해당 요구사항을 찾는다
      - 변경을 적용한다 - 다음이 될 수 있다:
        - 새 시나리오 추가(기존 시나리오를 복사할 필요 없음)
        - 기존 시나리오 수정
        - 요구사항 설명 변경
      - delta에 언급되지 않은 시나리오/내용은 보존한다

      **REMOVED Requirements:**
      - 메인 spec에서 해당 요구사항 블록 전체를 제거한다

      **RENAMED Requirements:**
      - FROM 요구사항을 찾아 TO로 이름을 바꾼다

   d. **capability가 아직 없으면 새 메인 spec을 생성한다**:
      - `openspec/specs/<capability>/spec.md`를 생성한다
      - Purpose 섹션을 추가한다(간략해도 되고, TBD로 표시 가능)
      - ADDED 요구사항을 담은 Requirements 섹션을 추가한다

4. **요약 보여주기**

   모든 변경을 적용한 뒤 다음을 요약한다:
   - 어떤 capability가 갱신되었는지
   - 어떤 변경이 이루어졌는지(요구사항 추가/수정/제거/이름 변경)

**Delta Spec 형식 참조**

```markdown
## ADDED Requirements

### Requirement: New Feature
The system SHALL do something new.

#### Scenario: Basic case
- **WHEN** user does X
- **THEN** system does Y

## MODIFIED Requirements

### Requirement: Existing Feature
#### Scenario: New scenario to add
- **WHEN** user does A
- **THEN** system does B

## REMOVED Requirements

### Requirement: Deprecated Feature

## RENAMED Requirements

- FROM: `### Requirement: Old Name`
- TO: `### Requirement: New Name`
```

**핵심 원칙: 지능적 병합**

프로그래밍 방식 병합과 달리, **부분 갱신**을 적용할 수 있다:
- 시나리오를 추가하려면 그 시나리오만 MODIFIED 아래에 넣는다 - 기존 시나리오를 복사하지 마라
- delta는 통째 교체가 아니라 *의도*를 나타낸다
- 판단력을 발휘해 변경을 합리적으로 병합하라

**성공 시 출력**

```
## 스펙 동기화 완료: <change-name>

갱신된 메인 spec:

**<capability-1>**:
- requirement 추가: "New Feature"
- requirement 수정: "Existing Feature" (scenario 1개 추가)

**<capability-2>**:
- 새 spec 파일 생성
- requirement 추가: "Another Feature"

메인 spec이 갱신되었다. change는 여전히 활성 상태다 - 구현이 끝나면 아카이브한다.
```

**가드레일**
- 변경하기 전에 delta spec과 메인 spec을 모두 읽는다
- delta에 언급되지 않은 기존 내용은 보존한다
- 불분명한 점이 있으면 설명을 요청한다
- 진행하면서 무엇을 바꾸는지 보여준다
- 작업은 멱등해야 한다 - 두 번 실행해도 같은 결과가 나와야 한다
