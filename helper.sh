#!/usr/bin/env bash
# Build, test, lint and publish the archive produced by this invocation.
set -euo pipefail

if [[ "${1:-}" == --help || "${1:-}" == -h ]]; then
  echo 'Usage: ./helper.sh'
  echo 'Build both signed APKs with lint and tests, then upload and queue publication.'
  echo 'Commit and push the intended source to Silora main before running.'
  exit 0
fi
if (( $# )); then
  echo 'Usage: ./helper.sh (no arguments)' >&2
  exit 2
fi

REPO_ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)
cd "$REPO_ROOT"
build_receipt=$(mktemp "${TMPDIR:-/tmp}/silora-build-path.XXXXXX")
trap 'rm -f -- "$build_receipt"' EXIT

./build.sh --lint --tests --output-file "$build_receipt"
if ! IFS= read -r archive < "$build_receipt" || [[ "$archive" != "$REPO_ROOT"/dist/local-* || ! -d "$archive" ]]; then
  echo 'Build did not report a valid release archive; nothing was published.' >&2
  exit 1
fi

printf '\nPublishing the verified build: %s\n' "$archive"
./release.sh local "$archive" --publish
