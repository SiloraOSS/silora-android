#!/usr/bin/env python3
"""Report the new handoff workflow using the existing verified Discord transport."""
import importlib.util
import json
import os
from pathlib import Path
import sys

spec = importlib.util.spec_from_file_location("notifications", Path(__file__).with_name("notify-fork-daily.py"))
common = importlib.util.module_from_spec(spec)
spec.loader.exec_module(common)
WORKFLOW = "Silora Automation Release"
WORKFLOW_PATH = ".github/workflows/fork-automation-release.yml"


def notification(event, jobs, recipients):
    run = event["workflow_run"]
    publishing = (run.get("display_title") or run.get("name", "")).startswith("Silora release ")
    mode = "release" if publishing else "validation"
    conclusion = run.get("conclusion")
    ping = False
    if event["action"] == "in_progress":
        title, color = f"🔄 Silora automated {mode} started", 0x5865F2
        description = "Checking the exact source commit, then testing and building signed phone and TV APKs."
        if not publishing:
            description += " Publication is disabled for this run."
    elif conclusion == "success":
        title, color = f"✅ Silora automated {mode} succeeded", 0x2ECC71
        results = {job["name"]: job.get("conclusion") for job in jobs}
        if results.get("prepare") == "success" and results.get("build") == "skipped" and results.get("publish") == "skipped":
            description = "This exact commit was already published; no duplicate build or release was created."
        elif all(results.get(name) == "success" for name in ("prepare", "build", "publish")):
            description = "Signed phone and TV APKs were published after checksum read-back verification."
        elif not publishing and results.get("build") == "success" and results.get("publish") == "skipped":
            description = "Tests, release lint, signed builds, and APK verification passed. No release was published."
        else:
            description = "Workflow completed successfully. Check the run receipt for the exact outcome."
    elif conclusion in ("cancelled", "skipped", "neutral"):
        title, color = f"⚪ Silora automated {mode} {conclusion}", 0x95A5A6
        description = "Check the workflow run for details."
    else:
        title, color, ping = f"🚨 Silora automated {mode} failed", 0xE74C3C, True
        description = f"Workflow result: {conclusion or 'unknown'}. Review the failure before retrying."
        failed = next((job for job in jobs if job.get("conclusion") in ("failure", "timed_out", "action_required")), None)
        if failed:
            step = next((step for step in failed.get("steps", []) if step.get("conclusion") == "failure"), None)
            location = failed["name"] + (f" → {step['name']}" if step else "")
            description += f"\nFailed at: **{location[:200]}**."
    url = run["html_url"]
    description += f"\n[View workflow run]({url})"
    return {
        "content": " ".join(f"<@{value}>" for value in recipients) if ping else "",
        "allowed_mentions": {"parse": [], "users": recipients if ping else [], "roles": [], "replied_user": False},
        "embeds": [{"title": title, "description": description, "color": color, "url": url,
                    "footer": {"text": f"Run #{run['run_number']} · Attempt {run.get('run_attempt', 1)}"}}],
    }


def trigger():
    event = json.loads(Path(os.environ["GITHUB_EVENT_PATH"]).read_text())
    run = event["workflow_run"]
    if (event.get("action") not in ("in_progress", "completed")
            or run.get("head_branch") != "main" or run.get("event") != "workflow_dispatch"
            or run.get("head_repository", {}).get("full_name") != "SiloraOSS/silora-android"):
        raise common.NotificationError("Unexpected workflow event; nothing was sent.")
    # GitHub can put the custom run-name (including SHAs) in workflow_run.name.
    # Verify the stable workflow identity instead of comparing display names.
    try:
        result = common.subprocess.run(
            ["gh", "api", f"repos/SiloraOSS/silora-android/actions/workflows/{int(run['workflow_id'])}"],
            capture_output=True, text=True, timeout=30, check=True)
        workflow = json.loads(result.stdout)
    except (common.subprocess.CalledProcessError, common.subprocess.TimeoutExpired, KeyError, ValueError):
        raise common.NotificationError("Could not verify the triggering workflow; nothing was sent.") from None
    if workflow.get("path") != WORKFLOW_PATH:
        raise common.NotificationError("Unexpected triggering workflow; nothing was sent.")
    print(f"Verified workflow event: run {run.get('id')}, attempt {run.get('run_attempt', 1)}, action {event['action']}.")
    return event


def previous_marker(name):
    try:
        result = common.subprocess.run(
            ["gh", "api", f"repos/SiloraOSS/silora-android/actions/artifacts?name={name}&per_page=100",
             "--paginate", "--slurp"], capture_output=True, text=True, timeout=30, check=True)
        return any(artifact["name"] == name for page in json.loads(result.stdout) for artifact in page["artifacts"])
    except (common.subprocess.CalledProcessError, common.subprocess.TimeoutExpired, KeyError, ValueError):
        raise common.NotificationError("Could not reconcile prior notification receipts; nothing was sent.") from None


def prepare():
    event = trigger()
    run = event["workflow_run"]
    key = f"{int(run['id'])}-{int(run.get('run_attempt', 1))}-{event['action']}"
    if previous_marker(f"silora-notification-delivered-{key}"):
        needed = False
        print("This notification was already delivered and verified; skipping the repeated event.")
    elif previous_marker(f"silora-notification-intent-{key}"):
        raise common.NotificationError("A prior send intent has no delivery confirmation. Inspect Discord before retrying; nothing was sent.")
    else:
        needed = True
        Path("notification-intent.json").write_text(json.dumps({
            "trigger_run_id": run["id"], "trigger_attempt": run.get("run_attempt", 1),
            "action": event["action"], "notification_run_id": os.environ["GITHUB_RUN_ID"],
        }) + "\n")
    with open(os.environ["GITHUB_OUTPUT"], "a") as stream:
        print(f"should_notify={str(needed).lower()}\nkey={key}", file=stream)


def main():
    event = trigger()
    run = event["workflow_run"]
    recipients = common.user_ids(os.environ.get("DISCORD_FAILURE_USER_IDS", ""))
    jobs = []
    if event["action"] == "completed":
        try:
            jobs = common.workflow_jobs(run)
        except (common.NotificationError, common.subprocess.TimeoutExpired, ValueError, KeyError):
            print("::warning::Job details unavailable; sending the known result with its run link.")
    payload = notification(event, jobs, recipients)
    message = common.deliver(os.environ.get("DISCORD_WEBHOOK_URL", ""), os.environ.get("DISCORD_CHANNEL_ID", ""), payload)
    Path("notification-delivery.json").write_text(json.dumps({
        "message_id": message, "trigger_run_id": run.get("id"),
        "trigger_attempt": run.get("run_attempt", 1), "action": event["action"],
    }) + "\n")
    print(f"Discord notification delivered and verified (message {message}).")


if __name__ == "__main__":
    try:
        if sys.argv[1:] == ["prepare"]:
            prepare()
        elif not sys.argv[1:]:
            main()
        else:
            raise common.NotificationError("usage: notify-fork-automation.py [prepare]")
    except common.NotificationError as error:
        print(f"::error::{error}", file=sys.stderr)
        sys.exit(1)
    except (KeyError, ValueError, OSError):
        # Keep webhook URLs and tokens out of tracebacks and logs.
        print("::error::Could not confirm the notification. Inspect the channel before retrying.", file=sys.stderr)
        sys.exit(1)
