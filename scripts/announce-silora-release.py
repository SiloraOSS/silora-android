#!/usr/bin/env python3
"""Render or announce an exact verified Silora release through discord-tools."""
import argparse
import datetime as dt
import hashlib
import importlib.util
import json
from pathlib import Path
import re
import subprocess
import sys
from zoneinfo import ZoneInfo


def load_script(name, filename):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).with_name(filename))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


release = load_script("silora_release", "automation-release.py")
receipts = load_script("local_notifications", "notify-local-automation.py")
TOOLS = receipts.PRIVATE.parents[1] / "Tools/discord-tools"
STATE = receipts.PRIVATE / "release-announcements"
UPDATES_READY = receipts.PRIVATE / "config/silora-updates.ready"
CHANNEL = "1568502343631114260"
MESSAGE = re.compile(
    rf"^Posted and verified: (https://discord\.com/channels/{receipts.GUILD}/{CHANNEL}/[0-9]+)$",
    re.MULTILINE,
)
LIMIT = 4000
HIGHLIGHT = re.compile(r"- .+ \*\((?:Silora|Silo upstream)\)\*")


def verified_release(tag, source):
    if not release.TAG.fullmatch(tag):
        raise ValueError("Expected the exact published Silora build tag.")
    source = release.sha(source)
    result = release.api(f"releases/tags/{tag}")
    expected_url = f"https://github.com/{release.REPO}/releases/tag/{tag}"
    if (result.get("tag_name") != tag or result.get("html_url") != expected_url
            or not release.complete(result)
            or release.sha(release.api(f"commits/{tag}")["sha"]) != source):
        raise ValueError("GitHub does not show a complete public release of this source.")
    return result


def length(text):
    return len(text.encode("utf-16-le")) // 2


def embed_body(result, highlights):
    title = " ".join(result["name"].split())
    if not title or not (result.get("body") or "").strip():
        raise ValueError("The release needs a title and published notes.")
    bullets = [line.strip() for line in highlights.splitlines() if line.strip()]
    if not bullets:
        raise ValueError("Add at least one verified release highlight.")
    if not all(HIGHLIGHT.fullmatch(bullet) for bullet in bullets):
        raise ValueError("Use one bullet per line ending in *(Silora)* or *(Silo upstream)*.")
    notes = "\n".join(bullets)
    published = dt.datetime.fromisoformat(result["published_at"].replace("Z", "+00:00"))
    published = published.astimezone(ZoneInfo("America/Los_Angeles"))
    date = f"{published:%b} {published.day}, {published.year}"
    url = result["html_url"]
    downloads = url.replace("/releases/tag/", "/releases/download/", 1)
    footer = (f"[Phone and tablet APK]({downloads}/silora-phone.apk) · "
              f"[Android TV APK]({downloads}/silora-tv.apk) · "
              f"[Full release notes]({url})")
    body = (f"## 📢 {title}\n{date} · Android phones, tablets, and TV\n\n"
            f"### What changed\n\n{notes}\n\n{footer}")
    if length(body) > LIMIT:
        raise ValueError("Shorten the release highlights to fit one Discord embed.")
    return body + "\n"


def announcement_args(body_file, nonce, *, updates_enabled=False):
    audience = "--silora-updates" if updates_enabled else "--no-roles"
    return ["post", CHANNEL, str(body_file), "--embed", audience,
            "--input-format", "body", "--nonce", nonce]


def render(folder, args):
    renderer = TOOLS / "skills/discord-announcement/scripts/render_preview.py"
    output = folder / "preview.html"
    subprocess.run([sys.executable, "-B", str(renderer), "--output", str(output), "--", *args],
                   check=True, stdout=subprocess.DEVNULL)
    review = json.loads(output.with_suffix(".review.json").read_text())
    if (review["channel"]["id"], review["channel"]["guild_id"]) != (CHANNEL, receipts.GUILD):
        raise ValueError("Discord announcement destination did not match SigmaStreams.")
    html = output.read_text()
    output.write_text(html.replace("LOCAL PREVIEW · AWAITING APPROVAL", "ONE-TIME DESIGN PREVIEW")
                      .replace("Announcement preview — awaiting approval", "Silora announcement design preview"))
    return output, review


def body_file(folder, body):
    path = folder / "announcement.md"
    with path.open("w", encoding="utf-8") as stream:
        stream.write(body)
    path.chmod(0o600)
    return path


def publish(tag, source, result, body, nonce):
    with receipts.locked_run(STATE, f"silora-release-{result['id']}") as (folder, _):
        path = folder / "announcement.json"
        if path.exists():
            record = json.loads(path.read_text())
            if (record["tag"], record["source_sha"]) != (tag, source):
                raise ValueError("Release announcement receipt has different provenance.")
            if record["status"] == "delivered":
                return record["message_url"]
            raise ValueError(f"Announcement delivery is uncertain. Inspect {path} before retrying.")

        # Freeze the exact body and destination before the only possible send.
        updates_enabled = UPDATES_READY.is_file()
        args = announcement_args(body_file(folder, body), nonce, updates_enabled=updates_enabled)
        command = [str(TOOLS / "discord-tools"), "announce", *args]
        check = subprocess.run([*command, "--dry-run"], check=True, text=True, capture_output=True)
        if any(line.startswith("Audit channel: ") for line in check.stdout.splitlines()):
            raise ValueError("Discord announce is configured to post an extra audit message.")
        record = dict(tag=tag, source_sha=source, channel_id=CHANNEL, status="pending",
                      updates_enabled=updates_enabled,
                      nonce=nonce, body_sha256=hashlib.sha256(body.encode()).hexdigest(),
                      attempted_at=receipts.now())
        receipts.save(path, record)
        sent = subprocess.run(command, text=True, capture_output=True)
        link = MESSAGE.search(sent.stdout)
        if not link or sent.returncode:
            # The CLI can report a created message ID even when read-back fails.
            created = re.search(r"created as message ([0-9]+)", sent.stderr)
            if created:
                record["message_id"] = created[1]
                receipts.save(path, record)
            raise ValueError(f"Announcement delivery is uncertain. Inspect {path} before retrying.")
        record.update(status="delivered", message_url=link[1], verified_at=receipts.now())
        receipts.save(path, record)
        if sent.stderr.strip():
            print("Announcement verified. The configured audit needs separate verification.", file=sys.stderr)
        return link[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("tag")
    parser.add_argument("source_sha")
    parser.add_argument("--highlights-file", type=Path, required=True)
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument("--preview-dir", type=Path)
    mode.add_argument("--publish", action="store_true")
    args = parser.parse_args()
    try:
        result = verified_release(args.tag, args.source_sha)
        body = embed_body(result, args.highlights_file.read_text())
        nonce = hashlib.sha256(f"silora:{CHANNEL}:{result['id']}".encode()).hexdigest()[:24]
        if args.publish:
            print(publish(args.tag, args.source_sha, result, body, nonce))
        else:
            folder = args.preview_dir.resolve()
            folder.mkdir(mode=0o700)
            output, review = render(folder, announcement_args(
                body_file(folder, body), nonce, updates_enabled=True,
            ))
            print(json.dumps({"preview": str(output), "channel": review["channel"], "audit": review["audit"]}))
    except (ValueError, KeyError, OSError, subprocess.CalledProcessError, receipts.NotificationError) as error:
        parser.exit(1, f"{error}\n")


if __name__ == "__main__":
    main()
