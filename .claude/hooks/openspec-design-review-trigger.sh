#!/bin/bash
# PostToolUse 훅: 활성 change의 design.md가 편집·생성되면 "검증 대기" 마커에 change 이름을 기록한다.
# 실제 검증 루프는 Stop 훅(openspec-review-pending-check.sh)이 응답 종료 시점에 1회로 합쳐 넛지한다(디바운스).
# 순수 부작용 훅 — 항상 exit 0, 모델로 차단·피드백하지 않는다.
#
# 주의: Bash heredoc/`>` 로 design.md를 만들면 Edit|Write|MultiEdit 매처에 걸리지 않는다(우회 클래스).

INPUT=$(cat)

# 편집 대상 파일 경로 추출 (Edit/Write/MultiEdit 모두 tool_input.file_path 단일 필드)
FILE=$(echo "$INPUT" | jq -r '.tool_input.file_path // empty')
[ -z "$FILE" ] && exit 0

# 아카이브 경로는 방어적으로 즉시 통과 (정규식에서도 배제되지만 이중 방어)
case "$FILE" in
  */archive/*) exit 0 ;;
esac

# 활성 change의 design.md만 매칭: openspec/changes/<slug>/design.md
# [^/]+ 단일 세그먼트라 openspec/changes/archive/<date-name>/design.md 는 자연히 불일치.
# (^|/) 접미사 매칭이라 절대경로(/Users/.../openspec/...)도 잡힌다.
REGEX='(^|/)openspec/changes/([^/]+)/design\.md$'
if [[ ! "$FILE" =~ $REGEX ]]; then
  exit 0
fi
CHANGE="${BASH_REMATCH[2]}"

# 프로젝트 루트 결정 (훅 환경변수 → stdin cwd → 현재 디렉토리 순)
PROJECT_DIR="${CLAUDE_PROJECT_DIR:-}"
if [ -z "$PROJECT_DIR" ]; then
  PROJECT_DIR=$(echo "$INPUT" | jq -r '.cwd // empty')
fi
PROJECT_DIR="${PROJECT_DIR:-$(pwd)}"

MARKER="$PROJECT_DIR/.claude/.openspec-review-pending"

# change 이름을 append-unique로 기록 (한 턴에 여러 change의 design.md를 만졌을 수 있음)
if [ -f "$MARKER" ] && grep -qxF "$CHANGE" "$MARKER" 2>/dev/null; then
  exit 0
fi
echo "$CHANGE" >> "$MARKER"
exit 0
