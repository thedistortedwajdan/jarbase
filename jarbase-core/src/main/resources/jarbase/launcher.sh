#!/bin/sh
# Runs the jarbase command-line tool:   sh .jarbase/jarbase affected --uncommitted
#
# Where it looks for the jar, in order:
#   1. $JARBASE_JAR                         (an explicit override)
#   2. .jarbase/jarbase.jar                 (put there by `jarbase install` when run from a jar)
#   3. the local Maven repository, using the version in .jarbase/version
#      (put there by the Maven plugin; Maven downloads the jar when the project is loaded)

dir=$(cd "$(dirname "$0")" && pwd)
jar=""

if [ -n "$JARBASE_JAR" ]; then
  jar="$JARBASE_JAR"
elif [ -f "$dir/jarbase.jar" ]; then
  jar="$dir/jarbase.jar"
elif [ -f "$dir/version" ]; then
  version=$(tr -d ' \r\n' < "$dir/version")
  repo="${JARBASE_M2_REPO:-$HOME/.m2/repository}"
  jar="$repo/io/github/thedistortedwajdan/jarbase/jarbase-cli/$version/jarbase-cli-$version-all.jar"
fi

if [ -z "$jar" ] || [ ! -f "$jar" ]; then
  echo "jarbase: cannot find the jarbase jar${jar:+ (looked for $jar)}." >&2
  echo "jarbase: run any Maven build once, for example './mvnw -q initialize', so Maven downloads it, then try again." >&2
  exit 127
fi

exec java -jar "$jar" "$@"
