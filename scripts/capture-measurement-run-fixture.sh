#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
FIXTURE_OUT="${1:-$REPO_ROOT/shared/src/commonTest/resources/fixtures/measurement-run/msak-sequence-v1.json}"

cd "$REPO_ROOT"

export CELLWATCH_CAPTURE_MEASUREMENT_FIXTURE=1
export CELLWATCH_MEASUREMENT_FIXTURE_OUT="$FIXTURE_OUT"

./gradlew :shared:jvmTest --tests "*MeasurementRunFixtureCaptureJvmTest*"
./gradlew :shared:jvmTest --tests "*MeasurementRunFixtureReplayJvmTest*"

echo "Fixture captured and verified: $FIXTURE_OUT"
