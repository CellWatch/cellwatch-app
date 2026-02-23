#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
FIXTURE_OUT="${1:-$REPO_ROOT/shared/src/commonTest/resources/fixtures/measurement-history/msak-history-v1.json}"

cd "$REPO_ROOT"

export CELLWATCH_CAPTURE_HISTORY_FIXTURE=1
export CELLWATCH_HISTORY_FIXTURE_OUT="$FIXTURE_OUT"

./gradlew :shared:jvmTest --tests "*MeasurementHistoryFixtureCaptureJvmTest*"
./gradlew :shared:jvmTest --tests "*MeasurementHistoryFixtureReplayJvmTest*"

echo "History fixture captured and verified: $FIXTURE_OUT"
