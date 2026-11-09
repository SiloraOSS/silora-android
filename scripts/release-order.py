#!/usr/bin/env python3
"""Keep Latest releases increasing across local, manual and automated builds."""
import argparse
import json
from pathlib import Path
import subprocess
import sys
import tempfile

REPO = "SiloraOSS/silora-android"


def gh(*args):
    return subprocess.run(["gh", *map(str, args)], check=True, capture_output=True, text=True).stdout


def version_base(info):
    if not isinstance(info, dict):
        raise ValueError("Missing or invalid Android version code in release metadata")
    value = info.get("version_code_base")
    if type(value) is not int or not 0 < value <= 1_049_999_999:
        raise ValueError("Missing or invalid Android version code in release metadata")
    return value


def ensure_newer(info, repo=REPO, *, transport=None):
    command = transport or gh
    candidate = version_base(info)
    # An empty repository can publish its first release. Network/authentication
    # errors remain failures, never permission to overwrite Latest.
    pages = json.loads(command("api", f"repos/{repo}/releases?per_page=100", "--paginate", "--slurp"))
    public = [r for page in pages for r in page
              if r.get("draft") is False and r.get("prerelease") is False and r.get("published_at")]
    if not public:
        return
    latest = json.loads(command("api", f"repos/{repo}/releases/latest"))
    with tempfile.TemporaryDirectory(prefix="silora-release-order-") as temp:
        command("release", "download", latest["tag_name"], "--repo", repo,
           "--pattern", "build-info.json", "--dir", temp)
        previous = json.loads((Path(temp) / "build-info.json").read_text())
    if candidate <= version_base(previous):
        raise ValueError("A public release is newer than or equal to this build; build again before publishing")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("metadata", type=Path)
    parser.add_argument("--repo", default=REPO)
    args = parser.parse_args()
    try:
        ensure_newer(json.loads(args.metadata.read_text()), args.repo)
        print("Release version is eligible for Latest.")
    except (OSError, ValueError, KeyError, TypeError, subprocess.CalledProcessError) as error:
        print(f"Release order check failed: {error}", file=sys.stderr)
        sys.exit(1)
