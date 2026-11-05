#!/usr/bin/env python3
"""Dispatch or reconcile one exact-commit handoff; validation is the default."""
import argparse
import fcntl
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import time

spec = importlib.util.spec_from_file_location("release", Path(__file__).with_name("automation-release.py"))
release = importlib.util.module_from_spec(spec)
spec.loader.exec_module(release)


def save(path, record):
    temporary = path.with_suffix(".tmp")
    with temporary.open("w") as stream:
        stream.write(json.dumps(record, indent=2) + "\n")
        stream.flush()
        os.fsync(stream.fileno())
    temporary.replace(path)
    directory = os.open(path.parent, os.O_RDONLY | os.O_DIRECTORY)
    try:
        os.fsync(directory)
    finally:
        os.close(directory)


def find_run(source, upstream, publishing):
    title = f"Silora {'release' if publishing else 'validation'} {source} upstream {upstream}"
    pages = json.loads(release.run("gh", "api",
        f"repos/{release.REPO}/actions/workflows/{release.WORKFLOW}/runs?event=workflow_dispatch&branch=main&head_sha={source}&per_page=100",
        "--paginate", "--slurp"))
    matches = [run for page in pages for run in page["workflow_runs"]
               if run["head_sha"] == source and run["display_title"] == title]
    return max(matches, key=lambda run: run["id"]) if matches else None


def dispatch(args):
    source, upstream = release.sha(args.source_sha), release.sha(args.upstream_sha)
    args.receipt_dir.mkdir(parents=True, exist_ok=True)
    path = args.receipt_dir / f"{source}-{'release' if args.publish else 'validation'}.json"
    # Serialize local invocations and persist intent before the network mutation.
    with (args.receipt_dir / ".dispatch.lock").open("a") as lock:
        fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        previous = json.loads(path.read_text()) if path.exists() else {}
        record = {"source_sha": source, "upstream_sha": upstream, "publish": args.publish,
                  "workflow": release.WORKFLOW}
        if previous and any(previous.get(key) != value for key, value in record.items()):
            raise ValueError("Saved receipt differs from this handoff; inspect it before dispatch")
        record = previous | record
        found = find_run(source, upstream, args.publish)
        if found:
            if previous.get("status") == "dispatch_pending" and previous.get("retry_of") == found["id"]:
                raise ValueError("A reviewed retry may already have been accepted; reconcile the pending dispatch before retrying again")
            if found["status"] != "completed" or found.get("conclusion") == "success":
                record.update(status=found["status"], conclusion=found.get("conclusion"),
                              run_id=found["id"], run_url=found["html_url"])
                save(path, record)
                print(json.dumps(record, indent=2))
                return
            if args.retry_reviewed != found["id"]:
                raise ValueError(f"Run {found['id']} ended {found.get('conclusion')}; review it before --retry-reviewed {found['id']}")
        elif previous:
            raise ValueError("Prior dispatch has no discoverable run; outcome is uncertain. Inspect GitHub; do not blindly retry")
        elif args.retry_reviewed:
            raise ValueError("The reviewed run was not found for this exact commit and mode")
        if release.sha(release.api("git/ref/heads/main")["object"]["sha"]) != source:
            raise ValueError("GitHub main differs from the validated merge; nothing dispatched")
        record.update(status="dispatch_pending", dispatch_time=time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()))
        if args.retry_reviewed:
            record["retry_of"] = args.retry_reviewed
        save(path, record)
        # No automatic retry: even a failed response may follow an accepted dispatch.
        release.run("gh", "workflow", "run", release.WORKFLOW, "--repo", release.REPO, "--ref", "main",
                    "-f", f"source_sha={source}", "-f", f"upstream_sha={upstream}",
                    "-f", f"publish={str(args.publish).lower()}")
        for attempt in range(4):
            found = find_run(source, upstream, args.publish)
            if found and (not args.retry_reviewed or found["id"] > args.retry_reviewed):
                record.update(status=found["status"], conclusion=found.get("conclusion"),
                              run_id=found["id"], run_url=found["html_url"])
                save(path, record)
                print(json.dumps(record, indent=2))
                return
            if attempt < 3:
                time.sleep(2)
        raise ValueError("Dispatch accepted but run not visible yet. Run this command again to reconcile; no second dispatch will be sent")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source-sha", required=True)
    parser.add_argument("--upstream-sha", required=True)
    parser.add_argument("--receipt-dir", required=True, type=Path)
    parser.add_argument("--publish", action="store_true", help="Publish after validation; omitted means build and verify only")
    parser.add_argument("--retry-reviewed", type=int, help="Explicitly retry this failed/cancelled run after reviewing it")
    try:
        dispatch(parser.parse_args())
    except (ValueError, KeyError, OSError, subprocess.CalledProcessError) as error:
        parser.exit(1, f"{error}\n")
