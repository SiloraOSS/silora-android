#!/usr/bin/env python3
"""Publish verified PC builds through a shared, compilation-free release job."""
import argparse
import datetime as dt
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile
from zoneinfo import ZoneInfo

ROOT = Path(__file__).resolve().parent.parent
REPO = "SiloraOSS/silora-android"
WORKFLOW = "fork-local-release.yml"
PAYLOAD = {"silora-phone.apk", "silora-tv.apk", "silora-phone-mapping.txt",
           "silora-tv-mapping.txt", "build-info.json"}
FILES = PAYLOAD | {"SHA256SUMS", "RELEASE_NOTES.md"}
HEX40 = re.compile(r"[0-9a-f]{40}")
HEX64 = re.compile(r"[0-9a-f]{64}")
spec = importlib.util.spec_from_file_location("local_build", ROOT / "scripts/build-local-release.py")
build = importlib.util.module_from_spec(spec)
spec.loader.exec_module(build)


def gh(*args):
    return subprocess.run(["gh", *map(str, args)], cwd=ROOT, check=True,
                          capture_output=True, text=True).stdout


def api(path):
    return json.loads(gh("api", f"repos/{REPO}/{path}"))


def digest(path):
    value = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            value.update(chunk)
    return value.hexdigest()


def validate_archive(directory, *, manifest_sha256=None, tag=None, source=None):
    """Validate every byte and provenance field before any release mutation."""
    directory = Path(directory)
    if not directory.is_dir() or {p.name for p in directory.iterdir()} != FILES:
        raise ValueError("Release archive must contain both APKs, both mappings, metadata, notes and SHA256SUMS")
    for name in FILES:
        path = directory / name
        if path.is_symlink() or not path.is_file() or path.stat().st_size == 0:
            raise ValueError(f"Missing, empty or linked release file: {name}")
    if manifest_sha256 is not None:
        if not HEX64.fullmatch(manifest_sha256) or digest(directory / "SHA256SUMS") != manifest_sha256:
            raise ValueError("Uploaded checksum manifest differs from the locally verified release")
    entries = {}
    for line in (directory / "SHA256SUMS").read_text().splitlines():
        match = re.fullmatch(r"([0-9a-f]{64})  ([A-Za-z0-9_.-]+)", line)
        if not match or match[2] in entries or match[2] not in FILES - {"SHA256SUMS"}:
            raise ValueError("Invalid or duplicate checksum entry")
        entries[match[2]] = match[1]
    if set(entries) not in (PAYLOAD, PAYLOAD | {"RELEASE_NOTES.md"}):
        raise ValueError("Checksums must cover both APKs, both mappings and build metadata")
    for name, expected in entries.items():
        if digest(directory / name) != expected:
            raise ValueError(f"Checksum mismatch: {name}")
    info = json.loads((directory / "build-info.json").read_text())
    if not isinstance(info, dict) or info.get("local_build") is not True:
        raise ValueError("Expected metadata from ./build.sh")
    if info.get("tracked_tree_dirty") is not False or info.get("untracked_build_inputs") is not False:
        raise ValueError("Build must come from committed source without untracked build inputs; commit, then rebuild")
    if set(info.get("targets", [])) != {"phone", "tv"}:
        raise ValueError("Latest releases require both phone and TV APKs")
    validation = info.get("validation", {})
    if not isinstance(validation, dict) or validation.get("release_lint") is not True:
        raise ValueError("Build lacks full release-lint evidence; rebuild with ./build.sh --lint")
    if not isinstance(validation.get("signing_certificate_sha256"), str) or not HEX64.fullmatch(validation["signing_certificate_sha256"]):
        raise ValueError("Missing release certificate evidence")
    if not all(isinstance(info.get(k), str) and HEX40.fullmatch(info[k]) for k in ("source_sha", "upstream_checked_sha")):
        raise ValueError("Invalid source provenance")
    if type(info.get("version_code_base")) is not int or not 0 < info["version_code_base"] <= 1_049_999_999:
        raise ValueError("Invalid Android version code")
    if not isinstance(info.get("title"), str) or not info["title"].startswith("Silora "):
        raise ValueError("Invalid release title")
    if not isinstance(info.get("version_name"), str) or not info["version_name"]:
        raise ValueError("Missing APK version")
    if source is not None and (not HEX40.fullmatch(source) or info["source_sha"] != source):
        raise ValueError("Release source differs from the locally verified commit")
    if tag is not None and (not build.fork_release.BUILD_TAG.fullmatch(tag) or info.get("tag") != tag):
        raise ValueError("Release tag differs from the locally verified build")
    return info


def verify_local(directory, info, signing_dir):
    env, fingerprint = build.signing_environment(signing_dir)
    if fingerprint != info["validation"]["signing_certificate_sha256"]:
        raise ValueError("Build certificate differs from the configured persistent signing key")
    env.setdefault("ANDROID_HOME", os.environ.get("ANDROID_SDK_ROOT", str(Path.home() / "Android")))
    build.verify_apks(env, info, fingerprint, apk_directory=directory)


def check_source(source):
    comparison = api(f"compare/{source}...main")
    if comparison["merge_base_commit"]["sha"] != source:
        raise ValueError("Build source must already be pushed and included in Silora main")


def ensure_newer(info):
    # Read through the API so a failed lookup cannot silently promote an older
    # build. Silora already has public releases; absence is an actionable error.
    latest = api("releases/latest")
    with tempfile.TemporaryDirectory(prefix="silora-latest-") as temp:
        gh("release", "download", latest["tag_name"], "--repo", REPO,
           "--pattern", "build-info.json", "--dir", temp)
        previous = json.loads((Path(temp) / "build-info.json").read_text())
    base = previous.get("version_code_base")
    if type(base) is not int or info["version_code_base"] <= base:
        raise ValueError("A public release is newer than or equal to this build; build again before publishing")


def public_tag(info):
    instant = dt.datetime.fromisoformat(info["built_at"])
    if instant.tzinfo is None:
        raise ValueError("Build timestamp needs a timezone")
    stamp = instant.astimezone(ZoneInfo("America/Los_Angeles")).strftime("%Y-%m-%d.%H%M%S")
    return f"build-{stamp}.{int(instant.timestamp() * 1_000_000)}"


def stage_files(directory, info, destination):
    for name in FILES:
        shutil.copyfile(Path(directory) / name, destination / name)
    if validate_archive(destination) != info:
        raise ValueError("Archive metadata changed during staging")
    info = dict(info, tag=public_tag(info), local_archive_tag=info["tag"], publication_method="local")
    (destination / "build-info.json").write_text(json.dumps(info, indent=2) + "\n")
    (destination / "SHA256SUMS").write_text("".join(
        f"{digest(destination / name)}  {name}\n" for name in sorted(FILES - {"SHA256SUMS"})))
    return info


def release_for(tag):
    # Listing errors remain errors. Do not interpret authentication/network
    # failures as permission to create or overwrite a release.
    pages = json.loads(gh("api", f"repos/{REPO}/releases?per_page=100", "--paginate", "--slurp"))
    return next((release for page in pages for release in page if release["tag_name"] == tag), None)


def check_tag(tag, source, *, create=False):
    refs = api(f"git/matching-refs/tags/{tag}")
    ref = next((item for item in refs if item["ref"] == f"refs/tags/{tag}"), None)
    if ref:
        if ref["object"]["sha"] != source:
            raise ValueError("Existing release tag points to a different source; refusing to move it")
    elif create:
        gh("api", "--method", "POST", f"repos/{REPO}/git/refs",
           "-f", f"ref=refs/tags/{tag}", "-f", f"sha={source}")
    else:
        raise ValueError("Release source tag is missing")


def verify_remote_subset(tag, expected, existing):
    names = [item["name"] for item in existing.get("assets", [])]
    if len(names) != len(set(names)) or not set(names) <= FILES:
        raise ValueError("Existing draft has unexpected or duplicate assets")
    if names:
        with tempfile.TemporaryDirectory(prefix="silora-readback-") as temp:
            gh("release", "download", tag, "--repo", REPO, "--dir", temp)
            downloaded = Path(temp)
            if {p.name for p in downloaded.iterdir()} != set(names):
                raise ValueError("Draft asset list changed during verification")
            for name in names:
                if digest(downloaded / name) != digest(expected / name):
                    raise ValueError(f"Existing release differs from this build: {name}; no assets overwritten")
    return set(names)


def upload_and_dispatch(directory, info):
    with tempfile.TemporaryDirectory(prefix="silora-stage-") as temp:
        staged = Path(temp)
        info = stage_files(directory, info, staged)
        tag, source = info["tag"], info["source_sha"]
        manifest_hash = digest(staged / "SHA256SUMS")
        existing = release_for(tag)
        if existing and not existing["draft"]:
            if verify_remote_subset(tag, staged, existing) != FILES:
                raise ValueError("Published release is incomplete; public releases are never overwritten")
            print(existing["html_url"])
            return
        ensure_newer(info)
        check_tag(tag, source, create=True)
        if existing is None:
            gh("release", "create", tag, "--repo", REPO, "--verify-tag", "--draft",
               "--title", info["title"], "--notes-file", staged / "RELEASE_NOTES.md")
            existing = release_for(tag)
            if existing is None or not existing["draft"]:
                raise ValueError("Could not verify the new draft; inspect GitHub before retrying")
        present = verify_remote_subset(tag, staged, existing)
        missing = sorted(FILES - present)
        if missing:
            gh("release", "upload", tag, *[staged / name for name in missing], "--repo", REPO)
        existing = release_for(tag)
        if existing is None or not existing["draft"] or verify_remote_subset(tag, staged, existing) != FILES:
            raise ValueError("Could not verify every uploaded draft asset")
        gh("workflow", "run", WORKFLOW, "--repo", REPO, "--ref", "main",
           "-f", f"tag={tag}", "-f", f"source_sha={source}", "-f", f"manifest_sha256={manifest_hash}")
        print(f"Verified draft uploaded: {tag}\nPublish-only job queued; no GitHub compilation.\n"
              f"Follow completion: https://github.com/{REPO}/actions/workflows/{WORKFLOW}\n"
              "If dispatch failed or was interrupted, retry the same local command; matching drafts are reused.")


def finalize(tag, source, manifest_hash):
    if (os.environ.get("GITHUB_ACTIONS") != "true"
            or os.environ.get("GITHUB_WORKFLOW") != "Silora Publish Local Build"
            or os.environ.get("GITHUB_REPOSITORY") != REPO
            or os.environ.get("GITHUB_REF") != "refs/heads/main"):
        raise ValueError("Final publication must run in the locked Silora Publish Local Build workflow")
    if not build.fork_release.BUILD_TAG.fullmatch(tag) or not HEX40.fullmatch(source) or not HEX64.fullmatch(manifest_hash):
        raise ValueError("Invalid publish job inputs")
    existing = release_for(tag)
    if existing is None:
        raise ValueError("Verified local draft is missing")
    check_tag(tag, source)
    check_source(source)
    with tempfile.TemporaryDirectory(prefix="silora-finalize-") as temp:
        gh("release", "download", tag, "--repo", REPO, "--dir", temp)
        info = validate_archive(temp, manifest_sha256=manifest_hash, tag=tag, source=source)
        if existing["draft"]:
            # Called inside the shared silora-release concurrency group. A newer
            # GitHub build finishing while the PC uploads cannot become a downgrade.
            ensure_newer(info)
            gh("release", "edit", tag, "--repo", REPO, "--title", info["title"],
               "--notes-file", Path(temp) / "RELEASE_NOTES.md", "--draft=false", "--prerelease=false", "--latest")
        published = release_for(tag)
        if published is None or published["draft"] or published.get("prerelease"):
            raise ValueError("Publication is not confirmed; inspect GitHub before retrying")
        print(published["html_url"])
        if summary := os.environ.get("GITHUB_STEP_SUMMARY"):
            with open(summary, "a") as out:
                print(f"Published verified PC build: {published['html_url']}\nNo APKs were recompiled.", file=out)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="mode", required=True)
    local = commands.add_parser("local", help="verify an archived local build; add --publish to release it")
    local.add_argument("directory", type=Path)
    local.add_argument("--publish", action="store_true")
    local.add_argument("--signing-dir", type=Path, default=Path(os.environ.get("SILORA_SIGNING_DIR", ROOT.parent / "silora-signing")))
    github = commands.add_parser("github", help="start the existing GitHub compilation workflow")
    github.add_argument("--tests", action="store_true")
    github.add_argument("--dry-run", action="store_true")
    finish = commands.add_parser("finalize", help=argparse.SUPPRESS)
    for flag in ("tag", "source", "manifest-sha256"):
        finish.add_argument(f"--{flag}", required=True)
    args = parser.parse_args()
    if args.mode == "github":
        command = ["workflow", "run", "fork-manual-release.yml", "--repo", REPO, "--ref", "main",
                   "-f", f"run_tests={str(args.tests).lower()}"]
        if args.dry_run:
            print("gh " + " ".join(command))
        else:
            gh(*command)
            print(f"GitHub build requested: https://github.com/{REPO}/actions/workflows/fork-manual-release.yml")
    elif args.mode == "finalize":
        finalize(args.tag, args.source, args.manifest_sha256)
    else:
        directory = args.directory.expanduser().resolve()
        validate_archive(directory)
        # Work from a private snapshot so later local builds cannot replace a
        # verified file while it is being uploaded.
        with tempfile.TemporaryDirectory(prefix="silora-verified-") as temp:
            snapshot = Path(temp)
            for name in FILES:
                shutil.copyfile(directory / name, snapshot / name)
            info = validate_archive(snapshot)
            verify_local(snapshot, info, args.signing_dir)
            check_source(info["source_sha"])
            print(f"Verified {info['title']}\nSource: {info['source_sha']}\nPhone: {info['version_code_base'] * 2}; TV: {info['version_code_base'] * 2 + 1}")
            if args.publish:
                # Fail before creating a draft when the new workflow is not installed.
                api(f"actions/workflows/{WORKFLOW}")
                upload_and_dispatch(snapshot, info)
            else:
                ensure_newer(info)
                print("Preview only; no tag, draft or workflow created. Add --publish to release these exact APKs.")


if __name__ == "__main__":
    try:
        main()
    except (OSError, ValueError, KeyError, TypeError, subprocess.CalledProcessError) as error:
        if isinstance(error, subprocess.CalledProcessError):
            # Never print a child environment or credential-bearing arguments.
            detail = error.stderr.strip() if error.stderr else "external command failed"
            print(f"Release stopped: {detail}", file=sys.stderr)
        else:
            print(f"Release stopped: {error}", file=sys.stderr)
        sys.exit(1)
