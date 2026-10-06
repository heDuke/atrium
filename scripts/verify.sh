#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
bash scripts/check-no-horologist.sh
bash gradlew :app:assembleDebug :app:assembleRelease :core:data:testDebugUnitTest :app:lintDebug --no-daemon
