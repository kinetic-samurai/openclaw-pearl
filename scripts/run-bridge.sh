#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/../apps/litert-bridge"
exec ./gradlew run
