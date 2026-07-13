#!/bin/bash
# Stop 훅: 응답 종료 시점에 "검증 대기" 마커가 있으면 openspec-review-loop 실행을 1회 넛지한다.
# design.md 편집 훅(openspec-design-review-trigger.sh)이 남긴 마커를 여기서 소비한다 → 한 턴의 여러 저장이 1회로 합쳐짐(디바운스).
#
# 재진입 제어 (무한 재귀 방지):
#  - stop_hook_active=true  → 같은 응답 사이클에서 이미 넛지로 재개된 상태. 통과.
#  - 루프 진행 락 존재       → openspec-review-loop 가 도는 중. 자기 재트리거 방지로 통과.
# ※ 중요: 서브에이전트(reviewer/fixer)의 Edit/Write 도 이 세션의 PostToolUse 훅(design 트리거)을
#   발동시켜 마커를 쌓는다(구현 중 실측 확인). 따라서 무한 재귀의 실제 방어선은 "락"이다 —
#   루프가 도는 동안 락이 있어 Stop 넛지가 억제되고, 스킬 종료 시 마커·락을 함께 지운다.
#   크래시로 락이 남아 시스템이 영구히 멈추는 것을 막기 위해 오래된(stale) 락은 무시한다.
# 마커는 여기서 지우지 않는다(change 이름을 파일로 스킬에 확실히 넘김). 삭제는 스킬 종료 단계가 담당.

INPUT=$(cat)

# 이미 Stop 훅 넛지로 재개된 사이클이면 다시 넛지하지 않는다
if [ "$(echo "$INPUT" | jq -r '.stop_hook_active // false')" = "true" ]; then
  exit 0
fi

# 프로젝트 루트 결정 (훅 환경변수 → stdin cwd → 현재 디렉토리 순)
PROJECT_DIR="${CLAUDE_PROJECT_DIR:-}"
if [ -z "$PROJECT_DIR" ]; then
  PROJECT_DIR=$(echo "$INPUT" | jq -r '.cwd // empty')
fi
PROJECT_DIR="${PROJECT_DIR:-$(pwd)}"

MARKER="$PROJECT_DIR/.claude/.openspec-review-pending"
LOCK="$PROJECT_DIR/.claude/.openspec-review-lock"

# 대기 마커가 없으면 할 일 없음
[ -f "$MARKER" ] || exit 0

# 루프가 이미 진행 중이면 넛지하지 않는다. 단 오래된(stale) 락은 크래시 잔재로 보고 무시한다.
if [ -f "$LOCK" ]; then
  if [ -z "$(find "$LOCK" -mmin +30 2>/dev/null)" ]; then
    exit 0          # 30분 이내 락 = 루프 진행 중 → skip
  fi
  rm -f "$LOCK"     # stale 락 제거하고 계속 진행
fi

# 마커에서 change 이름 목록 추출 (빈 줄 제거, 콤마로 연결)
CHANGES=$(grep -v '^[[:space:]]*$' "$MARKER" 2>/dev/null | paste -sd ',' - | sed 's/,/, /g')
[ -z "$CHANGES" ] && exit 0

REASON="OpenSpec: change '${CHANGES}' 의 design.md 가 변경되었습니다. 다른 작업을 하기 전에 지금 openspec-review-loop 스킬을 해당 change 에 대해 실행하고, 검증 verdict PASS 가 나오거나 사용자 결정이 필요한 게이트-의도 결함에 도달할 때까지 진행하세요. 검증할 활성 change 가 없으면(모두 archive) 조용히 종료하면 됩니다."

# Stop 훅에서 decision:block + reason 을 내면 메인 에이전트가 응답을 재개하며 reason 을 지시로 받는다.
jq -n --arg r "$REASON" '{decision:"block", reason:$r}'
exit 0
