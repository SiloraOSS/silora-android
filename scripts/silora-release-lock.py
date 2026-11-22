#!/usr/bin/env python3
"""Coordinate the local stages of manual and scheduled Silora releases."""
import argparse
import fcntl
import json
import os
from pathlib import Path
import re
import stat
import subprocess
import sys
import tempfile
import time
import uuid


STATE = Path("/home/albinoman887/Workspace/SigmaStreams/Staging/silora-upstream-automation/local-run-coordinator")
BOOT_ID = Path("/proc/sys/kernel/random/boot_id")
IDLE_SECONDS = 15 * 60
BUSY = 75


def now():
    return time.clock_gettime(time.CLOCK_BOOTTIME)


def read_owner(path):
    try:
        return json.loads(path.read_text())
    except FileNotFoundError:
        return None


def renew(path, owner):
    owner["idle_until"] = now() + IDLE_SECONDS
    with tempfile.NamedTemporaryFile(mode="w", encoding="utf-8", dir=path.parent, delete=False) as stream:
        temporary = Path(stream.name)
        try:
            json.dump(owner, stream)
            stream.close()
            temporary.replace(path)
        finally:
            temporary.unlink(missing_ok=True)


def coordinate(args):
    directory = args.state_dir.expanduser()
    directory.mkdir(parents=True, mode=0o700, exist_ok=True)
    if directory.stat().st_mode & (stat.S_IRWXG | stat.S_IRWXO):
        raise ValueError("The release coordination directory must be private")
    ownership = directory / "owner.json"
    boot = BOOT_ID.read_text().strip()
    fd = os.open(directory / "commands.lock", os.O_CREAT | os.O_RDWR | os.O_NOFOLLOW, 0o600)
    try:
        try:
            fcntl.flock(fd, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            owner = read_owner(ownership)
            detail = f" for execution {owner['execution_id']}" if owner else ""
            print(f"A Silora release command is already running{detail}.", file=sys.stderr)
            return BUSY

        owner = read_owner(ownership)
        active = owner is not None and owner["boot_id"] == boot and owner["idle_until"] > now()
        if args.action == "claim":
            if active:
                print(f"Silora release preparation is reserved by execution {owner['execution_id']}.", file=sys.stderr)
                return BUSY
            owner = {"execution_id": args.execution_id, "token": uuid.uuid4().hex, "boot_id": boot}
            renew(ownership, owner)
            print(json.dumps({"execution_id": owner["execution_id"], "token": owner["token"]}))
            return 0

        if owner is None or owner["token"] != args.token or owner["boot_id"] != boot:
            raise ValueError("The release reservation belongs to another execution or has ended")
        if args.action == "release":
            ownership.unlink()
            return 0
        if not active:
            raise ValueError("The release reservation expired. Reclaim it and reconcile the saved execution first")

        renew(ownership, owner)
        if args.action == "renew":
            return 0
        try:
            # Inherit the lock so a surviving command still excludes a replacement run.
            result = subprocess.run(args.command, pass_fds=(fd,)).returncode
        finally:
            renew(ownership, owner)
        return result if result >= 0 else 128 - result
    finally:
        # Closing our descriptor leaves the lock held if the command still owns it.
        os.close(fd)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--state-dir", type=Path, default=STATE)
    actions = parser.add_subparsers(dest="action", required=True)
    claim = actions.add_parser("claim", help="reserve a local release execution")
    claim.add_argument("--execution-id", required=True)
    for action in ("run", "renew", "release"):
        action_parser = actions.add_parser(action)
        action_parser.add_argument("--token", required=True)
        if action == "run":
            action_parser.add_argument("command", nargs=argparse.REMAINDER)
    args = parser.parse_args()
    if args.action == "claim" and not re.fullmatch(r"[A-Za-z0-9_-]{1,160}", args.execution_id):
        claim.error("expected a stable receipt ID containing letters, digits, hyphens or underscores")
    if args.action == "run":
        if args.command[:1] == ["--"]:
            args.command = args.command[1:]
        if not args.command:
            parser.error("run requires a command after --")

    try:
        return coordinate(args)
    except (KeyError, OSError, TypeError, ValueError) as error:
        print(f"Silora release coordination stopped: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
