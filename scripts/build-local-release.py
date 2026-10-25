#!/usr/bin/env python3
"""Build locally with Silora Daily / Silora Manual Release signing and versioning."""
import argparse
import datetime as dt
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import re
import subprocess
import sys


ROOT = Path(__file__).resolve().parent.parent
spec = importlib.util.spec_from_file_location("fork_release", ROOT / "scripts/fork-release.py")
fork_release = importlib.util.module_from_spec(spec)
spec.loader.exec_module(fork_release)


def capture(command, *, env=None, binary=False):
    return subprocess.run(command, cwd=ROOT, env=env, check=True,
                          capture_output=True, text=not binary).stdout


def signing_environment(directory):
    directory = directory.expanduser().resolve()
    keystore = directory / "release.jks"
    if not keystore.is_file():
        raise ValueError(f"Missing release keystore: {keystore}")
    try:
        settings = json.loads((directory / "signing.json").read_text())
    except (OSError, ValueError):
        raise ValueError(f"Cannot read signing configuration: {directory / 'signing.json'}") from None
    if not isinstance(settings, dict) or any(
        not isinstance(settings.get(name), str) or not settings[name]
        for name in ("password", "alias")
    ):
        raise ValueError("signing.json must contain nonempty password and alias strings")
    key_password = settings.get("key_password", settings["password"])
    if not isinstance(key_password, str) or not key_password:
        raise ValueError("signing.json key_password must be a nonempty string when supplied")
    env = os.environ.copy()
    env.update({
        "SILO_RELEASE_KEYSTORE": str(keystore),
        "SILO_RELEASE_KEYSTORE_PASSWORD": settings["password"],
        "SILO_RELEASE_KEY_ALIAS": settings["alias"],
        "SILO_RELEASE_KEY_PASSWORD": key_password,
        "ALLOW_DEBUG_RELEASE_SIGNING": "false",
    })
    # Passwords stay in the child environment, never command arguments or logs.
    try:
        certificate = capture([
            "keytool", "-exportcert", "-keystore", str(keystore),
            "-storepass:env", "SILO_RELEASE_KEYSTORE_PASSWORD", "-alias", settings["alias"],
        ], env=env, binary=True)
    except subprocess.CalledProcessError:
        raise ValueError("Cannot open the release signing certificate with the saved credentials") from None
    return env, hashlib.sha256(certificate).hexdigest()


def build_metadata():
    # Fetch only into the same private namespace used by CI. Never merge,
    # switch branches, or change the working files being built.
    subprocess.run([
        "git", "fetch", "--no-tags", "https://github.com/Silo-Server/silo-android.git",
        "refs/heads/main:refs/silo-upstream/heads/main",
        "refs/tags/v*:refs/silo-upstream/tags/v*",
    ], cwd=ROOT, check=True)
    source = capture(["git", "rev-parse", "HEAD"]).strip()
    upstream = capture(["git", "merge-base", source, "refs/silo-upstream/heads/main"]).strip()
    now = dt.datetime.now(dt.timezone.utc)
    info = fork_release.metadata(now, 1, source, upstream,
                                 upstream=fork_release.upstream_version(ROOT, upstream))
    info.update({
        "tag": f"local-{now.strftime('%Y-%m-%d.%H%M%S')}",
        "run_number": None,
        "local_build": True,
        "working_tree_dirty": bool(capture(["git", "status", "--porcelain"]).strip()),
    })
    return info


def verify_apks(env, info, certificate_sha256):
    sdk = Path(env["ANDROID_HOME"])
    apksigner = sdk / "build-tools/35.0.0/apksigner"
    analyzer = sdk / "cmdline-tools/latest/bin/apkanalyzer"
    for module, offset in (("androidApp", 0), ("androidTvApp", 1)):
        apk = ROOT / module / f"build/outputs/apk/release/{module}-universal-release.apk"
        signed = capture([str(apksigner), "verify", "--verbose", "--print-certs", str(apk)], env=env)
        fingerprints = re.findall(r"^Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]+)$",
                                  signed, re.MULTILINE)
        if [digest.lower() for digest in fingerprints] != [certificate_sha256]:
            raise ValueError(f"{module}: APK signing certificate does not match the release keystore")
        for field, expected in (
            ("application-id", "org.silora.app"),
            ("debuggable", "false"),
            ("version-code", str(info["version_code_base"] * 2 + offset)),
            ("version-name", info["version_name"]),
        ):
            actual = capture([str(analyzer), "manifest", field, str(apk)], env=env).strip()
            if actual != expected:
                raise ValueError(f"{module}: unexpected APK {field}: {actual!r}")
        print(f"Verified release signature, application ID, and version: {module}", flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--signing-dir", type=Path,
                        default=Path(os.environ.get("SILORA_SIGNING_DIR", ROOT.parent / "silora-signing")),
                        help="directory containing release.jks and signing.json (default: ../silora-signing)")
    parser.add_argument("--check", action="store_true",
                        help="check signing credentials and calculate the version without compiling")
    parser.add_argument("--tests", action="store_true",
                        help="run release-script tests and Gradle unit tests before building")
    parser.add_argument("--lint", action="store_true",
                        help="run full release lint for phone and TV before building")
    args = parser.parse_args()
    env, fingerprint = signing_environment(args.signing_dir)
    info = build_metadata()
    env.update({
        "SILO_VERSION_NAME": info["version_name"],
        "SILO_VERSION_CODE": str(info["version_code_base"]),
        "SILO_BUILD_NUMBER": str(info["build_number"]),
        "SILO_RELEASE_CHANNEL": "sideload",
    })
    print(f"Version: {info['version_name']}", flush=True)
    print(f"Version codes: phone={info['version_code_base'] * 2}, TV={info['version_code_base'] * 2 + 1}", flush=True)
    print(f"Signing key: {env['SILO_RELEASE_KEYSTORE']}", flush=True)
    print(f"Signing certificate SHA-256: {fingerprint}", flush=True)
    if args.check:
        print("Signing and version checks passed; no APKs built.", flush=True)
        return
    tasks = []
    if args.tests:
        for test in ("test-fork-release.py", "test-build-local-release.py"):
            subprocess.run([sys.executable, str(ROOT / "scripts" / test)], cwd=ROOT, env=env, check=True)
        tasks.append("test")
    if args.lint:
        tasks.extend([":androidApp:lintRelease", ":androidTvApp:lintRelease"])
    tasks.extend([":androidApp:assembleRelease", ":androidTvApp:assembleRelease"])
    subprocess.run([str(ROOT / "scripts/check-build-supply-chain.sh")], cwd=ROOT, env=env, check=True)
    subprocess.run([
        str(ROOT / "gradlew"), *tasks,
        "-PallowDebugReleaseSigning=false", "--parallel", "--console=plain",
    ], cwd=ROOT, env=env, check=True)
    verify_apks(env, info, fingerprint)
    output = ROOT / "dist" / info["tag"]
    fork_release.package(ROOT, info, output)
    print(f"Verified APKs and checksums: {output}", flush=True)


if __name__ == "__main__":
    try:
        main()
    except (OSError, ValueError, subprocess.CalledProcessError) as error:
        print(f"Local release failed: {error}", file=sys.stderr)
        sys.exit(1)
