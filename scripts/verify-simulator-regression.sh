#!/usr/bin/env bash
set -u

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

run_step() {
  local label="$1"
  shift
  local start end elapsed status

  echo
  echo "==> $label"
  start=$(date +%s)
  (cd "$REPO_ROOT" && "$@")
  status=$?
  end=$(date +%s)
  elapsed=$((end - start))

  if [[ $status -eq 0 ]]; then
    RESULTS+=("PASS | ${label} | ${elapsed}s")
  else
    RESULTS+=("FAIL | ${label} | ${elapsed}s")
    FAILURES=$((FAILURES + 1))
  fi

  return $status
}

RESULTS=()
FAILURES=0

run_step "Android emulator Phase3 button smoke (strict success)" \
  ./gradlew :shared:verifyAndroidUiPhase3ButtonSmoke

run_step "iOS simulator Phase3 button smoke (strict success)" \
  ./gradlew :shared:verifyIosTestAppHostedPhase3ButtonSmoke

echo
echo "Simulator regression summary"
echo "---------------------------"
for row in "${RESULTS[@]}"; do
  echo "$row"
done

if [[ $FAILURES -ne 0 ]]; then
  echo
  echo "Simulator regression sequence failed."
  exit 1
fi

echo
echo "Simulator regression sequence passed."
exit 0
