#!/bin/sh
# Fast gatekeeper for coding-agent hooks.
#
# Starting a JVM takes a few hundred milliseconds, and an agent runs this for EVERY shell command.
# So this tiny script looks at the hook input first and only starts Java when the command could be
# a push or a pull-request creation. Everything else exits at once.
#
# Usage (what the agent's hook runs):   jarbase-hook.sh [agent-id]      (default: claude-code)

agent="${1:-claude-code}"
payload=$(cat)

case "$payload" in
  *"git push"*|*"git "*" push"*|*"gh pr create"*|*"glab mr create"*|*"hub pull-request"*) ;;
  *) exit 0 ;;
esac

dir=$(cd "$(dirname "$0")" && pwd)
printf '%s' "$payload" | sh "$dir/jarbase" hook pre-push --agent "$agent"
status=$?

# 127 means the jarbase jar is not on this machine yet (the launcher already explained why).
# That must never stop a push.
[ "$status" -eq 127 ] && exit 0
exit "$status"
