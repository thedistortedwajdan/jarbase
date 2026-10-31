#!/bin/sh
# Fast gatekeeper for coding-agent hooks.
#
# Starting a JVM takes a few hundred milliseconds, and an agent runs this for EVERY shell command.
# So this tiny script looks at the hook input first and only starts Java when the command could be
# a push or a pull-request creation. Everything else exits at once.
#
# Usage (what the agent's hook runs):   jarbase-hook.sh [agent-id]      (default: claude-code)
# The jar is found via $JARBASE_JAR, or "jarbase.jar" next to this script.

agent="${1:-claude-code}"
payload=$(cat)

case "$payload" in
  *"git push"*|*"git "*" push"*|*"gh pr create"*|*"glab mr create"*|*"hub pull-request"*) ;;
  *) exit 0 ;;
esac

jar="${JARBASE_JAR:-$(dirname "$0")/jarbase.jar}"
[ -f "$jar" ] || exit 0

printf '%s' "$payload" | java -jar "$jar" hook pre-push --agent "$agent"
