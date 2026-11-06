#!/usr/bin/env python3
"""Send local Silora milestones through the existing private Discord webhook."""
import argparse
from contextlib import contextmanager
import datetime as dt
import fcntl
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile

spec = importlib.util.spec_from_file_location("notifications", Path(__file__).with_name("notify-fork-daily.py"))
common = importlib.util.module_from_spec(spec)
spec.loader.exec_module(common)

REPO = "SiloraOSS/silora-android"
CHANNEL = "1457811245146181806"
GUILD = "1457559352717086917"
PRIVATE = Path("/home/albinoman887/Workspace/SigmaStreams/Staging/silora-upstream-automation")
CONFIG = PRIVATE / "config/discord.json"
STATE = PRIVATE / "local-notifications"
EVENTS = ("started", "validating", "handoff", "unchanged", "failed", "setup")
TERMINAL = {"handoff", "unchanged", "failed", "setup"}


class NotificationError(Exception):
    """A safe error containing no credentials or raw request objects."""


def now():
    return dt.datetime.now(dt.timezone.utc).isoformat()


def save(path, value):
    with tempfile.NamedTemporaryFile(mode="w", encoding="utf-8", dir=path.parent, delete=False) as stream:
        temporary = Path(stream.name)
        json.dump(value, stream, indent=2)
        stream.write("\n")
        stream.flush()
        os.fsync(stream.fileno())
    temporary.replace(path)
    descriptor = os.open(path.parent, os.O_RDONLY | os.O_DIRECTORY)
    try:
        os.fsync(descriptor)
    finally:
        os.close(descriptor)


def identity(execution_id):
    if not execution_id or not re.fullmatch(r"[A-Za-z0-9_-]{1,160}", execution_id):
        raise NotificationError("Use CODEX_THREAD_ID or --execution-id with a stable ID for this local run.")
    return hashlib.sha256(execution_id.encode()).hexdigest()[:24]


@contextmanager
def locked_run(state, execution_id):
    run_id = identity(execution_id)
    folder = state / run_id
    folder.mkdir(parents=True, mode=0o700, exist_ok=True)
    state.chmod(0o700)
    folder.chmod(0o700)
    descriptor = os.open(folder / ".lock", os.O_WRONLY | os.O_CREAT, 0o600)
    with os.fdopen(descriptor, "w") as lock:
        try:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            raise NotificationError("Another notification for this local run is being handled.") from None
        path = folder / "run.json"
        if path.exists():
            record = json.loads(path.read_text())
            if record["execution_id"] != execution_id:
                raise NotificationError("Local run receipt does not match the execution ID.")
        else:
            record = dict(schema_version=1, execution_id=execution_id, run_id=run_id,
                          created_at=now(), terminal_event=None)
            save(path, record)
        yield folder, record


def load_config(path):
    if path.stat().st_mode & 0o077:
        raise NotificationError("The private webhook configuration must have mode 600 or stricter.")
    config = json.loads(path.read_text())
    url = re.fullmatch(r"https://discord\.com/api/webhooks/([1-9][0-9]{16,19})/[A-Za-z0-9_-]+", config["webhook_url"])
    if (not url or config["channel_id"] != CHANNEL or config["guild_id"] != GUILD
            or config["webhook_id"] != url[1]):
        raise NotificationError("Webhook configuration does not match the authorized mod-log destination.")
    return config


def check_webhook(config):
    hook = common.discord_request(config["webhook_url"])
    if (str(hook.get("id")) != config["webhook_id"] or str(hook.get("channel_id")) != CHANNEL
            or str(hook.get("guild_id")) != GUILD or hook.get("type") != 1):
        raise NotificationError("Webhook identity or destination changed; nothing was sent.")


def text(report, name):
    value = report.get(name)
    if not isinstance(value, str) or not value.strip():
        raise NotificationError(f"The notification report needs a nonempty {name}.")
    if (re.search(r"https?://(?:[^/]+\.)?discord(?:app)?\.com/api/webhooks/|/home/|file://|codex://|vscode://", value)
            or any(ord(char) < 32 and char not in "\n\t" for char in value)):
        raise NotificationError("Notification text contains private URLs, local paths, or control characters.")
    return value.strip()


def sha(report, name):
    value = report.get(name)
    if not isinstance(value, str) or not re.fullmatch(r"[0-9a-f]{40}", value):
        raise NotificationError(f"The notification report needs a full {name}.")
    return value


def recovery(report):
    value = report.get("recovery_ref")
    if (not isinstance(value, str) or not re.fullmatch(r"refs/codex/[A-Za-z0-9._/-]{1,180}", value)
            or ".." in value or "//" in value or value.endswith(("/", ".", ".lock"))):
        raise NotificationError("The report needs the actual recovery ref under refs/codex/.")
    return value


def workflow_for(report):
    source, upstream = sha(report, "merge_sha"), sha(report, "upstream_sha")
    run_id = report.get("workflow_run_id")
    if isinstance(run_id, bool) or not isinstance(run_id, int) or run_id < 1:
        raise NotificationError("The handoff needs the accepted GitHub workflow run ID.")
    try:
        result = subprocess.run(["gh", "api", f"repos/{REPO}/actions/runs/{run_id}"],
                                check=True, capture_output=True, text=True, timeout=30)
        run = json.loads(result.stdout)
    except (subprocess.CalledProcessError, subprocess.TimeoutExpired, ValueError):
        raise NotificationError("Could not verify the GitHub handoff; no handoff notification was sent.") from None
    if (run.get("head_sha") != source or run.get("head_branch") != "main"
            or run.get("event") != "workflow_dispatch"
            or run.get("head_repository", {}).get("full_name") != REPO
            or run.get("path") != ".github/workflows/fork-automation-release.yml"
            or run.get("display_title") != f"Silora release {source} upstream {upstream}"
            or run.get("html_url") != f"https://github.com/{REPO}/actions/runs/{run_id}"):
        raise NotificationError("GitHub run does not match this exact source, upstream, and release handoff.")
    return run


def payload_for(event, report, run_id):
    report = report or {}
    url = None
    if event == "started":
        title, color = "🔄 Silora local automation started", 0x5865F2
        body = "The local upstream merge automation has started. Checking the workspace and official upstream before merging."
    elif event == "validating":
        title, color = "🛠️ Silora merge prepared — validating", 0xFAA61A
        body = (text(report, "summary") + "\n\nRunning full unit tests, release lint, and signed phone/TV builds before committing and pushing."
                + f"\n- Upstream: `{sha(report, 'upstream_sha')}`\n- Recovery: `{recovery(report)}`")
    elif event == "handoff":
        run = workflow_for(report)
        source, upstream = sha(report, "merge_sha"), sha(report, "upstream_sha")
        line = report.get("changelog_line")
        if isinstance(line, bool) or not isinstance(line, int) or not 1 <= line <= 1_000_000:
            raise NotificationError("The handoff needs the verified changelog line number.")
        title, color = "✅ Silora merge complete — GitHub handoff", 0x2ECC71
        url = run["html_url"]
        body = (text(report, "summary") + f"\n- Upstream: `{upstream}`\n- Merge: `{source}`"
                + f"\n- Passed: {text(report, 'validation')}\n- Recovery: `{recovery(report)}`"
                + f"\n- [Changelog (line {line})](https://github.com/{REPO}/blob/{source}/CHANGELOG.md#L{line})\n\n")
        if run.get("status") == "completed":
            conclusion = run.get("conclusion") or "unknown"
            body += f"[Silora Automation Release]({url}) finished with result **{conclusion}** on the exact validated SHA. See GitHub's result notification for publication details."
        else:
            status = "in progress" if run.get("status") == "in_progress" else "queued"
            body += f"[Silora Automation Release]({url}) is {status} on the exact validated SHA. Publication success is not yet confirmed."
        body += " No device testing occurred."
    elif event == "unchanged":
        title, color = "⚪ Silora local automation — no upstream changes", 0x95A5A6
        body = ("Official upstream is already included. No merge, local build, push, or GitHub release was needed."
                + f"\n\nUpstream: `{sha(report, 'upstream_sha')}`")
    elif event == "failed":
        title, color = "🚨 Silora local automation stopped", 0xE74C3C
        stage = text(report, "stage")
        if "\n" in stage or len(stage) > 100:
            raise NotificationError("Use a short, single-line failure stage.")
        body = f"Local automation stopped during **{stage}**.\n\n{text(report, 'summary')}"
        if report.get("recovery_ref"):
            body += f"\n\nRecovery: `{recovery(report)}`"
    elif event == "setup":
        title, color = "🔔 Silora local status notifications enabled", 0x5865F2
        body = ("Future local automation runs will report their start, merge preparation and validation, and the validated merge summary when GitHub accepts the handoff. "
                "Unchanged or stopped runs will get a closing status. GitHub will continue reporting build and release results.")
    else:
        raise NotificationError("Unknown local notification event.")
    if len(body.encode("utf-16-le")) // 2 > 4096:
        raise NotificationError("The update exceeds Discord's embed limit; shorten the report before sending.")
    embed = dict(title=title, description=body, color=color, footer={"text": f"Local run {run_id} · {event}"})
    if url:
        embed["url"] = url
    return dict(content="", embeds=[embed],
                allowed_mentions=dict(parse=[], users=[], roles=[], replied_user=False))


def verify_message(message, payload, config, message_id):
    embeds = message.get("embeds", [])
    if (str(message.get("id")) != message_id or str(message.get("channel_id")) != CHANNEL
            or str(message.get("webhook_id")) != config["webhook_id"]
            or message.get("content", "") != payload["content"]
            or message.get("mention_everyone") or message.get("mention_roles") or message.get("mentions")
            or len(embeds) != 1
            or any(embeds[0].get(key) != value for key, value in payload["embeds"][0].items())):
        raise NotificationError("Discord read-back does not match; inspect the pending receipt before retrying.")


def notify(event, report, execution_id, config_path=CONFIG, state=STATE, message_id=None):
    with locked_run(state, execution_id) as (folder, run):
        path = folder / f"{event}.json"
        digest = hashlib.sha256(json.dumps(report or {}, sort_keys=True).encode()).hexdigest()
        config = load_config(config_path)
        if path.exists():
            receipt = json.loads(path.read_text())
            if report is not None and receipt["report_sha256"] != digest:
                raise NotificationError("This event already has different content; its existing receipt was preserved.")
            if receipt["status"] == "delivered":
                return dict(status="already_delivered", run_id=run["run_id"], message_id=receipt["message_id"], url=receipt["url"])
            # A known message can be read again; an unknown POST outcome is never retried.
            known_id = message_id or receipt.get("message_id")
            if not known_id:
                raise NotificationError("A prior send is uncertain. Inspect Discord and reconcile with --message-id; nothing resent.")
            if not re.fullmatch(r"[1-9][0-9]{16,19}", known_id):
                raise NotificationError("Use the actual Discord message ID for reconciliation.")
            check_webhook(config)
            actual = common.discord_request(config["webhook_url"] + "/messages/" + known_id)
            verify_message(actual, receipt["payload"], config, known_id)
        else:
            if message_id:
                raise NotificationError("There is no pending intent to reconcile; nothing was sent.")
            if run.get("terminal_event") or any((folder / f"{name}.json").exists() for name in TERMINAL):
                raise NotificationError("This local run already ended; no additional milestone was sent.")
            payload = payload_for(event, report, run["run_id"])
            check_webhook(config)
            receipt = dict(status="pending", event=event, report_sha256=digest,
                           payload=payload, attempted_at=now(), webhook_id=config["webhook_id"], channel_id=CHANNEL)
            save(path, receipt)
            if event in TERMINAL:
                run["terminal_event"] = event
                save(folder / "run.json", run)
            # One POST only. Persist its confirmed ID before the read-back request.
            actual = common.discord_request(config["webhook_url"] + "?wait=true", payload)
            known_id = str(actual.get("id", ""))
            if not re.fullmatch(r"[1-9][0-9]{16,19}", known_id):
                raise NotificationError("Discord did not confirm a message ID; inspect the pending receipt before retrying.")
            receipt["message_id"] = known_id
            save(path, receipt)
            actual = common.discord_request(config["webhook_url"] + "/messages/" + known_id)
            verify_message(actual, payload, config, known_id)
        receipt.update(status="delivered", message_id=known_id, verified_at=now(),
                       url=f"https://discord.com/channels/{GUILD}/{CHANNEL}/{known_id}")
        save(path, receipt)
        return dict(status="delivered", run_id=run["run_id"], message_id=known_id, url=receipt["url"])


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("event", choices=EVENTS + ("check",))
    parser.add_argument("--execution-id", default=os.environ.get("CODEX_THREAD_ID"))
    parser.add_argument("--report-file", type=Path)
    parser.add_argument("--config", type=Path, default=CONFIG)
    parser.add_argument("--state-dir", type=Path, default=STATE)
    parser.add_argument("--message-id", help="Reconcile a pending send by GET; never posts another message")
    parser.add_argument("--preview", action="store_true", help="Print the payload without writing state or contacting Discord")
    args = parser.parse_args()
    try:
        report = json.loads(args.report_file.read_text()) if args.report_file else None
        if report is not None and not isinstance(report, dict):
            raise NotificationError("The notification report must be a JSON object.")
        if args.event == "check":
            config = load_config(args.config)
            check_webhook(config)
            result = dict(status="configured", channel_id=CHANNEL, webhook_id=config["webhook_id"])
        elif args.preview:
            result = payload_for(args.event, report, identity(args.execution_id))
        else:
            result = notify(args.event, report, args.execution_id, args.config, args.state_dir, args.message_id)
        print(json.dumps(result, indent=2))
    except (NotificationError, common.NotificationError) as error:
        parser.exit(1, f"{error}\n")
    except (KeyError, ValueError, OSError):
        parser.exit(1, "Could not handle the local notification. Inspect private configuration/receipts; no blind retries.\n")
