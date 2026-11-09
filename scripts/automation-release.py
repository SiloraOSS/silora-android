#!/usr/bin/env python3
"""Exact-commit release handoff. The existing manual/daily tooling stays unchanged."""
import datetime as dt
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile
from zoneinfo import ZoneInfo

REPO = "SiloraOSS/silora-android"
WORKFLOW = "fork-automation-release.yml"
REQUIRED = {"silora-phone.apk", "silora-tv.apk", "build-info.json", "SHA256SUMS"}
PAYLOAD = REQUIRED - {"SHA256SUMS"} | {"release-receipt.json"}
SHA = re.compile(r"[0-9a-f]{40}")
TAG = re.compile(r"(?:build-\d{4}-\d{2}-\d{2}\.\d{6}\.[1-9]\d*|(?:daily|manual)-\d{4}-\d{2}-\d{2}\.[1-9]\d*)")


order_spec = importlib.util.spec_from_file_location("release_order", Path(__file__).with_name("release-order.py"))
order = importlib.util.module_from_spec(order_spec)
order_spec.loader.exec_module(order)


def run(*args):
    return subprocess.run(args, check=True, capture_output=True, text=True).stdout.strip()


def api(path, *, pages=False):
    args = ["gh", "api", f"repos/{REPO}/{path}"]
    if pages:
        args += ["--paginate", "--slurp"]
    result = json.loads(run(*args))
    return [item for page in result for item in page] if pages else result


def sha(value):
    if not isinstance(value, str) or not SHA.fullmatch(value):
        raise ValueError("Expected a full lowercase commit SHA")
    return value


def output(**values):
    with open(os.environ["GITHUB_OUTPUT"], "a") as stream:
        for key, value in values.items():
            print(f"{key}={value}", file=stream)


def summary(message):
    print(message)
    with open(os.environ["GITHUB_STEP_SUMMARY"], "a") as stream:
        print(message, file=stream)


def releases():
    return api("releases?per_page=100", pages=True)


def complete(release):
    return (release.get("draft") is False and release.get("prerelease") is False
            and release.get("published_at") and TAG.fullmatch(release.get("tag_name", ""))
            and REQUIRED <= {a["name"] for a in release.get("assets", []) if a.get("size", 0) > 0})


def published_source(source, entries):
    for entry in entries:
        if complete(entry) and sha(api(f"commits/{entry['tag_name']}")["sha"]) == source:
            return entry
    return None


def prepare():
    source, upstream = sha(os.environ["SOURCE_SHA"]), sha(os.environ["UPSTREAM_SHA"])
    if (os.environ["GITHUB_REPOSITORY"] != REPO or os.environ["GITHUB_REF"] != "refs/heads/main"
            or os.environ["GITHUB_EVENT_NAME"] != "workflow_dispatch"):
        raise ValueError("Release handoff requires workflow_dispatch on Silora main")
    if source != sha(os.environ["GITHUB_SHA"]) or source != run("git", "rev-parse", "HEAD"):
        raise ValueError("Requested source differs from the dispatch commit; nothing will be released")
    run("git", "merge-base", "--is-ancestor", upstream, source)
    # Confirm the supplied snapshot belongs to official upstream main, without merging it.
    comparison = api(f"compare/{upstream}...Silo-Server:main?per_page=1")
    if comparison["merge_base_commit"]["sha"] != upstream:
        raise ValueError("Supplied upstream snapshot is not included in official upstream main")
    publishing = os.environ.get("PUBLISH") == "true"
    entries = releases() if publishing else []
    previous = published_source(source, entries)
    output(source_sha=source, upstream_sha=upstream, should_build=str(previous is None).lower())
    if previous:
        summary(f"Already published this exact commit: {previous['html_url']}. Build and publication skipped.")
        return
    if publishing and sha(api("git/ref/heads/main")["object"]["sha"]) != source:
        raise ValueError("Main advanced while this run waited; dispatch the newly validated merge instead")
    # Re-runs of this same workflow use the original tag and its original Pacific date.
    run_id = int(os.environ["GITHUB_RUN_ID"])
    suffix = f".{run_id}"
    tags = [r["ref"].removeprefix("refs/tags/") for r in api("git/matching-refs/tags/build-", pages=True)
            if r["ref"].endswith(suffix)] if publishing else []
    if len(tags) > 1:
        raise ValueError("More than one reserved tag exists for this run")
    tag = tags[0] if tags else f"build-{dt.datetime.now(ZoneInfo('America/Los_Angeles')):%Y-%m-%d.%H%M%S}.{run_id}"
    if not TAG.fullmatch(tag):
        raise ValueError("Invalid reserved release tag")
    if publishing:
        if tags:
            if api(f"git/ref/tags/{tag}")["object"]["sha"] != source:
                raise ValueError("Reserved tag points to a different source")
        else:
            # Creation errors remain failures, including an uncertain response.
            run("gh", "api", "--method", "POST", f"repos/{REPO}/git/refs",
                "-f", f"ref=refs/tags/{tag}", "-f", f"sha={source}")
    output(tag=tag)
    summary(f"{'Release' if publishing else 'Validation only'} of `{source}`; included upstream `{upstream}`.")


def checksums(folder):
    rows = (folder / "SHA256SUMS").read_text().splitlines()
    values = {}
    for row in rows:
        match = re.fullmatch(r"([0-9a-f]{64})  ([a-zA-Z0-9.-]+)", row)
        if not match or match[2] in values:
            raise ValueError("Invalid or duplicate checksum entry")
        values[match[2]] = match[1]
    if set(values) != PAYLOAD:
        raise ValueError("Checksums must cover exactly both APKs, build info, and release receipt")
    for name, digest in values.items():
        path = folder / name
        if not path.is_file() or path.stat().st_size == 0 or hashlib.sha256(path.read_bytes()).hexdigest() != digest:
            raise ValueError(f"Checksum mismatch or missing release file: {name}")
    return values


def receipt(folder):
    info = json.loads((folder / "build-info.json").read_text())
    source, upstream = sha(info["source_sha"]), sha(info["upstream_checked_sha"])
    if source != sha(os.environ["SOURCE_SHA"]) or upstream != sha(os.environ["UPSTREAM_SHA"]):
        raise ValueError("Packaged source does not match the validated handoff")
    verification = json.loads((Path(os.environ["RUNNER_TEMP"]) / "automation-verification.json").read_text())
    if verification["source_sha"] != source or verification["upstream_sha"] != upstream:
        raise ValueError("APK verification belongs to a different handoff")
    record = {
        "schema_version": 1, "status": "verified", "source_sha": source,
        "upstream_sha": upstream, "tag": info["tag"], "workflow": WORKFLOW,
        "run_id": int(os.environ["GITHUB_RUN_ID"]), "run_attempt": int(os.environ["GITHUB_RUN_ATTEMPT"]),
        "run_url": f"https://github.com/{REPO}/actions/runs/{os.environ['GITHUB_RUN_ID']}",
        "publication_requested": os.environ.get("PUBLISH") == "true",
        "signing_certificate_sha256": verification["signing_certificate_sha256"],
        "checks": ["unit tests", "release lint", "signed phone and TV builds", "APK identity and version verification"],
        "apk_sha256": {name: hashlib.sha256((folder / name).read_bytes()).hexdigest()
                       for name in ("silora-phone.apk", "silora-tv.apk")},
    }
    (folder / "release-receipt.json").write_text(json.dumps(record, indent=2) + "\n")
    (folder / "SHA256SUMS").write_text("".join(
        f"{hashlib.sha256((folder / name).read_bytes()).hexdigest()}  {name}\n" for name in sorted(PAYLOAD)))
    checksums(folder)


def verify():
    # Reuse the local release verifier without editing its callers or behavior.
    spec = importlib.util.spec_from_file_location("local_release", Path(__file__).with_name("build-local-release.py"))
    local = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(local)
    info = json.loads(Path(os.environ["FORK_BUILD_INFO"]).read_text())
    source, upstream = sha(os.environ["SOURCE_SHA"]), sha(os.environ["UPSTREAM_SHA"])
    if info["source_sha"] != source or info["upstream_checked_sha"] != upstream:
        raise ValueError("Build metadata differs from the handoff")
    certificate = local.capture([
        "keytool", "-exportcert", "-keystore", os.environ["SILO_RELEASE_KEYSTORE"],
        "-storepass:env", "SILO_RELEASE_KEYSTORE_PASSWORD", "-alias", os.environ["SILO_RELEASE_KEY_ALIAS"],
    ], binary=True)
    fingerprint = hashlib.sha256(certificate).hexdigest()
    local.verify_apks(os.environ.copy(), info, fingerprint)
    (Path(os.environ["RUNNER_TEMP"]) / "automation-verification.json").write_text(json.dumps({
        "source_sha": source, "upstream_sha": upstream, "signing_certificate_sha256": fingerprint,
    }) + "\n")


def verified_payload(folder, source, upstream, tag):
    checksums(folder)
    info = json.loads((folder / "build-info.json").read_text())
    record = json.loads((folder / "release-receipt.json").read_text())
    if (info["source_sha"] != source or info["upstream_checked_sha"] != upstream or info["tag"] != tag
            or record["source_sha"] != source or record["upstream_sha"] != upstream
            or record["tag"] != tag or record["status"] != "verified"
            or record["publication_requested"] is not True):
        raise ValueError("Release payload does not match the authorized source, upstream, and tag")
    if (set(record["apk_sha256"]) != {"silora-phone.apk", "silora-tv.apk"}
            or not re.fullmatch(r"[0-9a-f]{64}", record["signing_certificate_sha256"])):
        raise ValueError("Release receipt is missing APK or signing verification")
    for name, digest in record["apk_sha256"].items():
        if name not in ("silora-phone.apk", "silora-tv.apk") or digest != hashlib.sha256((folder / name).read_bytes()).hexdigest():
            raise ValueError("Receipt APK checksums do not match")
    return record


def publish(folder):
    source, upstream, tag = sha(os.environ["SOURCE_SHA"]), sha(os.environ["UPSTREAM_SHA"]), os.environ["TAG"]
    if not TAG.fullmatch(tag) or os.environ.get("PUBLISH") != "true":
        raise ValueError("Explicit publication authorization and a valid tag are required")
    record = verified_payload(folder, source, upstream, tag)
    if record["run_id"] != int(os.environ["GITHUB_RUN_ID"]):
        raise ValueError("Artifacts belong to a different workflow run")
    if api(f"git/ref/tags/{tag}")["object"]["sha"] != source:
        raise ValueError("Release tag no longer points to the verified source")
    entries = releases()
    existing = next((entry for entry in entries if entry["tag_name"] == tag), None)
    if existing and existing.get("draft") is not True:
        if not complete(existing):
            raise ValueError("An incomplete public release exists; inspect it without overwriting assets")
        # An uncertain publish response is reconciled by verifying the immutable public files.
        verify_download(folder, source, upstream, tag)
        summary(f"Publication already complete and verified: {existing['html_url']}")
        return
    duplicate = published_source(source, entries)
    if duplicate:
        summary(f"Already published this exact source: {duplicate['html_url']}; nothing overwritten.")
        return
    if existing is None:
        run("gh", "release", "create", tag, "--repo", REPO, "--verify-tag", "--draft",
            "--title", os.environ["TITLE"], "--notes-file", str(folder / "RELEASE_NOTES.md"))
    run("gh", "release", "upload", tag, "--repo", REPO,
        *[str(folder / name) for name in sorted(PAYLOAD | {"SHA256SUMS"})], "--clobber")
    verify_download(folder, source, upstream, tag)
    order.ensure_newer(json.loads((folder / "build-info.json").read_text()), REPO)
    run("gh", "release", "edit", tag, "--repo", REPO, "--draft=false", "--prerelease=false", "--latest")
    # Do not call success based only on the edit command's exit status.
    public = api(f"releases/tags/{tag}")
    if not complete(public):
        raise ValueError("GitHub did not confirm a complete public release")
    summary(f"Published and verified: {public['html_url']}\nSource: `{source}`\nRun: {record['run_url']}")


def verify_download(folder, source, upstream, tag):
    with tempfile.TemporaryDirectory() as tmp:
        downloaded = Path(tmp)
        run("gh", "release", "download", tag, "--repo", REPO, "--dir", tmp)
        verified_payload(downloaded, source, upstream, tag)
        if (downloaded / "SHA256SUMS").read_bytes() != (folder / "SHA256SUMS").read_bytes():
            raise ValueError("Uploaded files do not match the verified local payload")


if __name__ == "__main__":
    try:
        if sys.argv[1:] == ["prepare"]:
            prepare()
        elif sys.argv[1:] == ["receipt"]:
            receipt(Path("dist"))
        elif sys.argv[1:] == ["verify"]:
            verify()
        elif sys.argv[1:] == ["publish"]:
            publish(Path("dist"))
        else:
            raise ValueError("usage: automation-release.py prepare|verify|receipt|publish")
    except (ValueError, KeyError, OSError, subprocess.CalledProcessError) as error:
        print(f"::error::{error}", file=sys.stderr)
        sys.exit(1)
