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

# Gate 1: parity pipelines (upload-trigger + phase3-sequence artifact checks)
run_step "Parity pipelines" ./gradlew :shared:verifyParityPipelines

# Gate 2: Tier 1 lightweight platform verification
run_step "Tier 1 lightweight platforms" ./gradlew :shared:verifyLightweightPlatforms


echo
 echo "Verification summary"
 echo "--------------------"
for row in "${RESULTS[@]}"; do
  echo "$row"
done

if [[ $FAILURES -ne 0 ]]; then
  echo
  echo "One or more verification gates failed."
  exit 1
fi

echo
 echo "All verification gates passed."
exit 0
