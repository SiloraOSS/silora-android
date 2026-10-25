#!/usr/bin/env python3
"""Report daily workflow status without affecting synchronization or releases."""
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import urllib.error
import urllib.request


class NotificationError(Exception):
    """An error safe to show in public Actions logs."""


class NoRedirects(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def user_ids(raw):
    ids = list(dict.fromkeys(value.strip() for value in raw.split(',')))
    if not ids or any(not re.fullmatch(r'[1-9][0-9]{16,19}', value) for value in ids):
        raise NotificationError('Configure SILORA_DAILY_FAILURE_USER_IDS as comma-separated Discord user IDs.')
    return ids


def notification(event, jobs, recipients):
    run = event['workflow_run']
    conclusion = run.get('conclusion')
    ping = False
    if event['action'] == 'in_progress':
        title, color = '🔄 Silora Daily started', 0x5865F2
        description = 'Checking upstream changes and whether a new build is needed.'
    elif conclusion == 'success':
        results = {job['name']: job.get('conclusion') for job in jobs}
        if results.get('sync') == 'success' and results.get('build') == 'skipped' and results.get('publish') == 'skipped':
            title, color = '✅ Silora Daily — no changes', 0x2ECC71
            description = 'Upstream sync completed. Source files match the latest release; no build was needed.'
        else:
            title, color = '✅ Silora Daily succeeded', 0x2ECC71
            description = 'Workflow completed successfully.'
            if all(results.get(name) == 'success' for name in ('sync', 'build', 'publish')):
                description = 'Upstream sync, build, and publication succeeded. Phone and TV APKs are available on GitHub Releases.'
    elif conclusion in ('cancelled', 'skipped', 'neutral'):
        title, color = f'⚪ Silora Daily {conclusion}', 0x95A5A6
        description = 'Check the workflow run for details.'
    else:
        title, color, ping = '🚨 Silora Daily failed', 0xE74C3C, True
        description = f'Workflow result: {conclusion or "unknown"}. Check the run logs.'
        failed = next((job for job in jobs if job.get('conclusion') in ('failure', 'timed_out', 'action_required')), None)
        if failed:
            step = next((step for step in failed.get('steps', []) if step.get('conclusion') == 'failure'), None)
            location = failed['name'] + (f' → {step["name"]}' if step else '')
            description += f'\nFailed at: **{location[:200]}**.'
            if failed['name'] == 'sync' and step and step['name'] == 'Merge upstream into main':
                description += '\nUpstream could not be merged. Check for a merge conflict or GitHub API error; building and publishing were skipped.'
    run_url = run['html_url']
    description += f'\n[View workflow run]({run_url})'
    return {
        'content': ' '.join(f'<@{value}>' for value in recipients) if ping else '',
        'allowed_mentions': {'parse': [], 'users': recipients if ping else [], 'roles': [], 'replied_user': False},
        'embeds': [{
            'title': title, 'description': description, 'color': color, 'url': run_url,
            'footer': {'text': f'Run #{run["run_number"]} · Attempt {run.get("run_attempt", 1)}'},
        }],
    }


def workflow_jobs(run):
    jobs = []
    for page in range(1, 11):
        endpoint = (f'repos/SiloraOSS/silora-android/actions/runs/{int(run["id"])}/'
                    f'attempts/{int(run.get("run_attempt", 1))}/jobs?per_page=100&page={page}')
        result = subprocess.run(['gh', 'api', endpoint], capture_output=True, text=True, timeout=30)
        if result.returncode:
            raise NotificationError('Could not read workflow job details.')
        batch = json.loads(result.stdout)['jobs']
        jobs.extend(batch)
        if len(batch) < 100:
            return jobs
    raise NotificationError('Workflow job listing exceeded the pagination limit.')


def discord_request(url, payload=None):
    request = urllib.request.Request(
        url, data=json.dumps(payload).encode() if payload is not None else None,
        headers={'Content-Type': 'application/json', 'User-Agent': 'SiloraDailyNotifications/1.0'},
    )
    try:
        with urllib.request.build_opener(NoRedirects()).open(request, timeout=20) as response:
            return json.load(response)
    except urllib.error.HTTPError as error:
        raise NotificationError(f'Discord returned HTTP {error.code}; inspect the channel before retrying a send.') from None
    except (urllib.error.URLError, OSError, ValueError):
        raise NotificationError('Discord request could not be confirmed; inspect the channel before retrying a send.') from None


def deliver(url, channel_id, payload):
    if not re.fullmatch(r'https://discord\.com/api/webhooks/[0-9]+/[A-Za-z0-9_-]+', url):
        raise NotificationError('Configure SILORA_DAILY_DISCORD_WEBHOOK_URL with a Discord webhook URL.')
    if not re.fullmatch(r'[1-9][0-9]{16,19}', channel_id):
        raise NotificationError('Configure SILORA_DAILY_DISCORD_CHANNEL_ID with the destination channel ID.')
    webhook = discord_request(url)
    if webhook.get('channel_id') != channel_id or webhook.get('type') != 1:
        raise NotificationError('Webhook destination does not match the configured channel; nothing was sent.')
    # Do not retry POST automatically: a timeout can follow a successful send.
    message = discord_request(url + '?wait=true', payload)
    message_id = str(message.get('id', ''))
    if not re.fullmatch(r'[1-9][0-9]{16,19}', message_id):
        raise NotificationError('Discord did not confirm a message ID; inspect the channel before retrying.')
    readback = discord_request(url + '/messages/' + message_id)
    embeds = readback.get('embeds', [])
    expected = payload['embeds'][0]
    if (readback.get('channel_id') != channel_id or readback.get('webhook_id') != webhook['id']
            or readback.get('content', '') != payload['content']
            or readback.get('mention_everyone') or readback.get('mention_roles')
            or {user['id'] for user in readback.get('mentions', [])} != set(payload['allowed_mentions']['users'])
            or len(embeds) != 1
            or any(embeds[0].get(key) != expected[key] for key in ('title', 'description', 'color', 'url', 'footer'))):
        raise NotificationError('Discord read-back did not match the notification; inspect the channel before retrying.')
    return message_id


def main():
    event = json.loads(Path(os.environ['GITHUB_EVENT_PATH']).read_text())
    run = event['workflow_run']
    if (event.get('action') not in ('in_progress', 'completed')
            or run.get('name') != 'Silora Daily' or run.get('head_branch') != 'main'
            or run.get('head_repository', {}).get('full_name') != 'SiloraOSS/silora-android'
            or run.get('event') not in ('schedule', 'workflow_dispatch')):
        raise NotificationError('Unexpected workflow event; nothing was sent.')
    recipients = user_ids(os.environ.get('DISCORD_FAILURE_USER_IDS', ''))
    jobs = []
    if event['action'] == 'completed':
        try:
            jobs = workflow_jobs(run)
        except (NotificationError, subprocess.TimeoutExpired, ValueError, KeyError):
            # Send the known conclusion even when job detail lookup fails.
            print('::warning::Job details unavailable; sending the workflow result with its run link.')
    payload = notification(event, jobs, recipients)
    message_id = deliver(os.environ.get('DISCORD_WEBHOOK_URL', ''), os.environ.get('DISCORD_CHANNEL_ID', ''), payload)
    print(f'Discord notification delivered and verified (message {message_id}).')


if __name__ == '__main__':
    try:
        main()
    except NotificationError as error:
        print(f'::error::{error}', file=sys.stderr)
        sys.exit(1)
    except (KeyError, ValueError, OSError):
        # Avoid tracebacks that might expose a URL containing the webhook token.
        print('::error::Could not confirm the Discord notification. Check configuration and inspect the channel before retrying.', file=sys.stderr)
        sys.exit(1)
