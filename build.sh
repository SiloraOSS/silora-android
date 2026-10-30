#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
cd "$REPO_ROOT"

export JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-21-openjdk-amd64}"
export ANDROID_HOME="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android}}"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"

# Keep each APK and its matching R8 mapping together in dist/local-*/.
exec python3 scripts/build-local-release.py "$@"
