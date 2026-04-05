#!/bin/bash
# PreToolUse hook: git commit 시 커밋 메시지 형식을 검증한다.
# - Conventional Commit 접두사 필수 (feat:, fix:, refactor:, test:, chore:, docs:, style:, perf:, ci:, build:, revert:)
# - Co-Authored-By 트레일러 필수

INPUT=$(cat)
COMMAND=$(echo "$INPUT" | jq -r '.tool_input.command // empty')

# git commit 명령이 아니면 즉시 통과
if ! echo "$COMMAND" | grep -qE '^\s*git\s+commit\b'; then
  exit 0
fi

# --amend만 하는 경우 (메시지 변경 없이)는 통과
if echo "$COMMAND" | grep -qE '\-\-amend' && ! echo "$COMMAND" | grep -qE '\-m\s'; then
  exit 0
fi

# -m 플래그에서 커밋 메시지 추출
# HEREDOC 형식과 일반 형식 모두 지원
COMMIT_MSG=$(echo "$COMMAND" | grep -oP '(?<=-m\s["\x27])([\s\S]*?)(?=["\x27]\s*$)' || true)
if [ -z "$COMMIT_MSG" ]; then
  # HEREDOC 형식: -m "$(cat <<'EOF' ... EOF )"
  COMMIT_MSG=$(echo "$COMMAND" | sed -n "s/.*-m.*<<['\"]\\{0,1\\}EOF['\"]\\{0,1\\}//p" | sed '/^EOF/,$d' || true)
fi

if [ -z "$COMMIT_MSG" ]; then
  # 메시지를 파싱할 수 없으면 통과 (false negative 허용)
  exit 0
fi

# Conventional Commit 접두사 확인
FIRST_LINE=$(echo "$COMMIT_MSG" | head -1 | sed 's/^[[:space:]]*//')
if ! echo "$FIRST_LINE" | grep -qE '^(feat|fix|refactor|test|chore|docs|style|perf|ci|build|revert)(\(.+\))?!?:'; then
  echo '커밋 메시지에 Conventional Commit 접두사가 필요합니다 (예: feat:, fix:, refactor:)' >&2
  exit 2
fi

# Co-Authored-By 트레일러 확인
if ! echo "$COMMIT_MSG" | grep -q 'Co-Authored-By:'; then
  echo '커밋 메시지에 Co-Authored-By 트레일러가 필요합니다' >&2
  exit 2
fi

exit 0
