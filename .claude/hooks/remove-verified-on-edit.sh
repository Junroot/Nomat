#!/bin/bash
# PostToolUse hook: gh issue edit로 이슈 본문/제목이 변경되면 verified 라벨을 자동 제거한다.
# --body 또는 --title 플래그가 있을 때만 동작한다.
# --add-label / --remove-label만 있는 경우는 무시한다.

INPUT=$(cat)
COMMAND=$(echo "$INPUT" | jq -r '.tool_input.command // empty')

# gh issue edit 명령이 아니면 즉시 통과
if ! echo "$COMMAND" | grep -qE '^\s*(gh|GH)\s+issue\s+edit\b'; then
  exit 0
fi

# --body 또는 --title 플래그가 있는지 확인
if ! echo "$COMMAND" | grep -qE '\-\-body\b|\-\-title\b'; then
  exit 0
fi

# 이슈 번호 추출
ISSUE_NUMBER=$(echo "$COMMAND" | grep -oE 'gh\s+issue\s+edit\s+([0-9]+)' | grep -oE '[0-9]+')

if [ -z "$ISSUE_NUMBER" ]; then
  exit 0
fi

# 이미 --remove-label "verified"가 포함된 경우 중복 실행 방지
if echo "$COMMAND" | grep -qE '\-\-remove-label.*verified'; then
  exit 0
fi

# verified 라벨 제거 (라벨이 없으면 gh가 조용히 무시함)
gh issue edit "$ISSUE_NUMBER" --remove-label "verified" 2>/dev/null || true

exit 0
