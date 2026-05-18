#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LOG_DIR="$ROOT_DIR/app/build/reports"
LOG_FILE="$LOG_DIR/lintDebug-console.log"
UNAME="$(uname -s 2>/dev/null || echo unknown)"

mkdir -p "$LOG_DIR"

if [[ "$UNAME" == MINGW* || "$UNAME" == MSYS* || "$UNAME" == CYGWIN* ]]; then
  if ! command -v cygpath >/dev/null 2>&1; then
    echo "::error::cygpath is required to call gradlew.bat from this Windows bash environment."
    exit 1
  fi
  GRADLE_CMD=(cmd.exe /c "$(cygpath -w "$ROOT_DIR/gradlew.bat")")
elif command -v wslpath >/dev/null 2>&1 && command -v cmd.exe >/dev/null 2>&1 && [[ -f "$ROOT_DIR/gradlew.bat" ]]; then
  GRADLE_CMD=(cmd.exe /c "$(wslpath -w "$ROOT_DIR/gradlew.bat")")
else
  GRADLE_CMD=("$ROOT_DIR/gradlew")
fi

set +e
"${GRADLE_CMD[@]}" :app:lintDebug 2>&1 | tee "$LOG_FILE"
gradle_exit=${PIPESTATUS[0]}
set -e

if [ "$gradle_exit" -ne 0 ]; then
  exit "$gradle_exit"
fi

if grep -Eiq "errors/warnings were listed in the baseline file|were listed in the baseline file .* but not found" "$LOG_FILE"; then
  echo "::error::Android Lint reported stale baseline entries. Regenerate or remove the stale baseline in the same change that fixes the warning."
  exit 1
fi

if [ ! -f "$ROOT_DIR/app/build/reports/lint-results-debug.xml" ]; then
  echo "::error::Expected lint XML report was not produced."
  exit 1
fi
