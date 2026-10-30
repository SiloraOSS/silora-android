#!/usr/bin/env bash
# Build signed Silora release APKs locally, with the same key and version code
# base as the fork's GitHub releases, so they install over each other in place.
#
# Usage: silora-release-apk [phone|tv|both] [--tests] [--lint] [--install] [--version-code N]
# Run with --help for details.

set -euo pipefail

REPO_SLUG="${SILORA_RELEASE_REPO:-SiloraOSS/silora-android}"
DEFAULT_REPO_DIR="$HOME/silora-android"
# Public SHA-256 fingerprint of the shared Silora release signing certificate.
EXPECTED_CERT_SHA256="a0f114bd672d91e69bdb8bf7bbcfd6c84aa2dba8d3549aae922a2e8e29496155"

usage() {
  cat <<'EOF'
silora-release-apk — build signed Silora release APKs on this Mac

Usage:
  silora-release-apk [phone|tv|both] [--tests] [--lint] [--install] [--version-code N]

Targets (default: both):
  phone   build androidApp only
  tv      build androidTvApp only
  both    build both apps

Options:
  --tests            run project-wide Gradle unit tests before building.
  --lint             run full release lint for the selected apps before building.
  --install          after building, install onto the connected device with adb.
                     Picks the TV APK for an Android TV device and the phone APK
                     otherwise. With several devices, set ANDROID_SERIAL first.
  --version-code N   use N as the version code base instead of the one from the
                     latest GitHub release (phone = N*2, TV = N*2+1).
  -h, --help         show this help.

Tests and full lint are skipped unless their flags are supplied. Android's
release-critical lint checks still run as part of APK assembly.

Verified APKs and their matching R8 mapping files are saved together in
dist/local-YYYY-MM-DD.HHMMSS/ with SHA256SUMS. Keep each APK and mapping pair
to decode crash reports from that build. Missing mappings stop packaging.

It builds whatever is checked out in the repo (default ~/silora-android, or the
silora-android checkout you run it from; override with SILORA_REPO=path).
Signing files are read from ../silora-signing beside the checkout:
  release.jks   shared Silora keystore
  signing.json  password and alias strings; optional key_password string
Override the folder with SILORA_SIGNING_DIR=path. Passwords are read directly
into the build environment and are never printed or passed as command arguments.

The default version code comes from the latest SiloraOSS/silora-android release
(override with SILORA_RELEASE_REPO=owner/repo or --version-code N).
To install over an existing build, use the same app ID and signing key and an
equal or higher version code. A build signed with Kyle's old key requires a
one-time uninstall before switching to the shared key.
EOF
}

die() { echo "error: $*" >&2; exit 1; }

target="both"
run_tests=false
run_lint=false
install=false
version_code=""
while (($#)); do
  case "$1" in
    phone|tv|both) target="$1" ;;
    --tests) run_tests=true ;;
    --lint) run_lint=true ;;
    --install) install=true ;;
    --version-code)
      shift
      [[ "${1:-}" =~ ^[0-9]+$ ]] || die "--version-code needs a number"
      version_code="$1"
      ;;
    -h|--help) usage; exit 0 ;;
    *) die "unknown argument: $1 (see --help)" ;;
  esac
  shift
done

# Find the repo: the current checkout if it is silora-android, else the default.
repo_dir="${SILORA_REPO:-}"
if [[ -z "$repo_dir" ]]; then
  top="$(git rev-parse --show-toplevel 2>/dev/null || true)"
  if [[ -n "$top" && -f "$top/androidApp/build.gradle.kts" && -f "$top/androidTvApp/build.gradle.kts" ]]; then
    repo_dir="$top"
  else
    repo_dir="$DEFAULT_REPO_DIR"
  fi
fi
[[ -x "$repo_dir/gradlew" ]] || die "no silora-android checkout at $repo_dir (set SILORA_REPO)"
cd "$repo_dir"
repo_dir="$PWD"

# Toolchain (normally exported by ~/.zshenv; defaults cover shells that skip it).
export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home}"
export ANDROID_HOME="${ANDROID_HOME:-/opt/homebrew/share/android-commandlinetools}"
[[ -x "$JAVA_HOME/bin/java" ]] || die "Java not found at $JAVA_HOME (brew install openjdk@21)"
[[ -d "$ANDROID_HOME/platforms" ]] || die "Android SDK not found at $ANDROID_HOME"
signing_dir="${SILORA_SIGNING_DIR:-$repo_dir/../silora-signing}"
[[ -f "$signing_dir/release.jks" ]] || die "signing keystore missing: $signing_dir/release.jks"
[[ -f "$signing_dir/signing.json" ]] || die "signing configuration missing: $signing_dir/signing.json"
command -v python3 >/dev/null || die "python3 not found"

# Version code base: match the latest GitHub release unless one was given.
if [[ -z "$version_code" ]]; then
  echo "Looking up the version code of the latest $REPO_SLUG release..."
  tag="$(gh release view --repo "$REPO_SLUG" --json tagName --jq .tagName 2>/dev/null)" \
    || die "couldn't read the latest release (gh auth status?); pass --version-code N"
  version_code="$(gh release download "$tag" --repo "$REPO_SLUG" -p build-info.json -O - 2>/dev/null \
    | python3 -I -c 'import json,sys; print(json.load(sys.stdin)["version_code_base"])')" \
    || die "release $tag has no readable build-info.json; pass --version-code N"
  echo "Using version code base $version_code (from $tag)."
fi

[[ "$version_code" =~ ^[0-9]+$ ]] || die "release version code base is not a number"

tasks=()
modules=()
case "$target" in
  phone) modules=(androidApp) ;;
  tv) modules=(androidTvApp) ;;
  both) modules=(androidApp androidTvApp) ;;
esac
if $run_tests; then tasks+=(test); fi
if $run_lint; then
  for module in "${modules[@]}"; do tasks+=(":$module:lintRelease"); done
fi
for module in "${modules[@]}"; do tasks+=(":$module:assembleRelease"); done

dirty=""
[[ -z "$(git status --porcelain)" ]] || dirty=" (with uncommitted changes)"
echo "Building ${modules[*]} from $(git rev-parse --abbrev-ref HEAD) @ $(git rev-parse --short HEAD)$dirty"

# Python reads JSON without shell evaluation and keeps passwords out of arguments.
python3 -I - "$signing_dir" "$version_code" "$EXPECTED_CERT_SHA256" "${tasks[@]}" <<'PY'
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys


def fail(message):
    print(f"error: {message}", file=sys.stderr)
    sys.exit(1)


directory = Path(sys.argv[1]).expanduser().resolve()
try:
    settings = json.loads((directory / "signing.json").read_text())
except (OSError, ValueError):
    fail("cannot read signing.json; expected a JSON object with password and alias")
if not isinstance(settings, dict) or any(
    not isinstance(settings.get(name), str) or not settings[name]
    for name in ("password", "alias")
):
    fail("signing.json must contain nonempty password and alias strings")
key_password = settings.get("key_password", settings["password"])
if not isinstance(key_password, str) or not key_password:
    fail("signing.json key_password must be a nonempty string when supplied")
if any("\0" in value for value in (settings["password"], settings["alias"], key_password)):
    fail("signing.json values cannot contain NUL characters")

env = os.environ.copy()
env.update({
    "SILO_RELEASE_KEYSTORE": str(directory / "release.jks"),
    "SILO_RELEASE_KEY_ALIAS": settings["alias"],
    "SILO_RELEASE_KEYSTORE_PASSWORD": settings["password"],
    "SILO_RELEASE_KEY_PASSWORD": key_password,
    "ALLOW_DEBUG_RELEASE_SIGNING": "false",
    "SILO_VERSION_CODE": sys.argv[2],
})
try:
    certificate = subprocess.run([
        str(Path(env["JAVA_HOME"]) / "bin/keytool"), "-exportcert",
        "-keystore", env["SILO_RELEASE_KEYSTORE"],
        "-alias", env["SILO_RELEASE_KEY_ALIAS"],
        "-storepass:env", "SILO_RELEASE_KEYSTORE_PASSWORD",
    ], env=env, capture_output=True, check=True).stdout
except (OSError, subprocess.CalledProcessError):
    fail("cannot open the shared signing certificate with the saved credentials")
if hashlib.sha256(certificate).hexdigest() != sys.argv[3]:
    fail("release.jks does not contain the shared Silora release certificate")
print("Shared Silora signing certificate verified.", flush=True)
result = subprocess.run([
    "./gradlew", "-Dorg.gradle.jvmargs=-Xmx4g -Dfile.encoding=UTF-8",
    "-PallowDebugReleaseSigning=false", *sys.argv[4:],
], env=env)
sys.exit(result.returncode)
PY

apksigner="$(ls -d "$ANDROID_HOME"/build-tools/*/apksigner 2>/dev/null | sort -V | tail -1)"
[[ -n "$apksigner" ]] || die "apksigner not found under $ANDROID_HOME/build-tools"

phone_apk=""
tv_apk=""
echo
for module in "${modules[@]}"; do
  apk="$repo_dir/$module/build/outputs/apk/release/$module-universal-release.apk"
  [[ -s "$apk" ]] || die "expected APK missing: $apk"
  digest="$("$apksigner" verify --print-certs "$apk" | awk -F': ' '/certificate SHA-256 digest/ {print $2; exit}')"
  [[ "$digest" == "$EXPECTED_CERT_SHA256" ]] \
    || die "$apk is not signed with the Silora release key (got ${digest:-no signature})"
  if [[ "$module" == androidApp ]]; then phone_apk="$apk"; else tv_apk="$apk"; fi
  echo "✓ $apk"
done
echo "Signed with the Silora release key, version code base $version_code."

# Archive verified APKs and mappings before a later Gradle build replaces them.
output="$(python3 -I - "$repo_dir" "${modules[@]}" <<'PY'
import datetime as dt
import hashlib
from pathlib import Path
import shutil
import sys

root = Path(sys.argv[1])
sources = {}
for module in sys.argv[2:]:
    target = {"androidApp": "phone", "androidTvApp": "tv"}[module]
    sources[f"silora-{target}.apk"] = root / module / f"build/outputs/apk/release/{module}-universal-release.apk"
    sources[f"silora-{target}-mapping.txt"] = root / module / "build/outputs/mapping/release/mapping.txt"
for name, source in sources.items():
    if not source.is_file() or source.stat().st_size == 0:
        kind = "R8 mapping" if name.endswith("-mapping.txt") else "release APK"
        sys.exit(f"error: missing or empty {kind}: {source}")
stamp = dt.datetime.now(dt.timezone.utc).strftime("%Y-%m-%d.%H%M%S")
output = root / "dist" / f"local-{stamp}"
try:
    output.mkdir(parents=True)
except FileExistsError:
    sys.exit(f"error: release output already exists; keeping the previous build: {output}")
for name, source in sources.items():
    shutil.copyfile(source, output / name)
(output / "SHA256SUMS").write_text("".join(
    f"{hashlib.sha256((output / name).read_bytes()).hexdigest()}  {name}\n"
    for name in sorted(sources)
))
print(output)
PY
)"
if [[ -n "$phone_apk" ]]; then phone_apk="$output/silora-phone.apk"; fi
if [[ -n "$tv_apk" ]]; then tv_apk="$output/silora-tv.apk"; fi
echo "Verified APKs, R8 mappings, and checksums: $output"

$install || exit 0

command -v adb >/dev/null || die "adb not found (brew install --cask android-platform-tools)"
devices="$(adb devices | awk 'NR>1 && $2=="device" {print $1}')"
[[ -n "$devices" ]] || die "no device connected (adb devices); connect one or use adb connect <ip>"
if [[ -z "${ANDROID_SERIAL:-}" && "$(wc -l <<<"$devices")" -gt 1 ]]; then
  die "several devices connected; pick one with ANDROID_SERIAL=<serial> (see adb devices)"
fi

if adb shell pm has-feature android.software.leanback 2>/dev/null | grep -q true; then
  apk="$tv_apk"; kind="Android TV"; needed="tv"
else
  apk="$phone_apk"; kind="phone/tablet"; needed="phone"
fi
[[ -n "$apk" ]] || die "the connected device is a $kind, but its APK wasn't built; run with '$needed' or 'both'"
echo "Installing on $kind $(adb get-serialno)..."
adb install -r "$apk"
