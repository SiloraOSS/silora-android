# Local automation status in Discord

The local upstream-merge automation reports its milestones to the same
**SiloraOSS** webhook as the GitHub notification workflows, in **#📋・mod-log**.
GitHub continues reporting the remote build and release results.

Each actual local run sends:

1. **Started**, before checking the workspace and fetching upstream.
2. **Merge prepared — validating**, after the merge audit and conflict
   resolution, before the mandatory local tests, release lint, and signed builds.
3. **Merge complete — GitHub handoff**, after validation, commit, push, and
   acceptance of the exact-SHA GitHub workflow run.

An unchanged run sends a short closing status after the start. A run that must
stop before handoff sends the actual failure stage and reason instead. Repaired
transient errors do not create extra milestones. Local updates suppress all
mentions; the existing GitHub failure notifications retain their configuration.

## Sender and private configuration

Source: `scripts/notify-local-automation.py`. Its installed runtime lives outside
the Git checkout so the start notification works while the user is on another
branch or the merge task is preparing a worktree:

```text
/home/albinoman887/Workspace/SigmaStreams/Staging/silora-upstream-automation/
  config/discord.json
  local-notifier/notify-local-automation.py
  local-notifier/notify-fork-daily.py
  local-notifier/source.json
  local-notifications/<run-id>/
```

The runtime reuses the existing webhook request helper. GitHub notification
scripts and workflow files are unchanged. `source.json` records the installed
source commit and file hashes. Keep the runtime current when its source changes.

The private JSON configuration contains `webhook_url`, `webhook_id`, `channel_id`,
and `guild_id`; it must have mode `600` or stricter. Its directory and the local
receipt directories use mode `700`. Credentials stay outside the repository,
command arguments, reports, and logs. The sender checks the webhook identity,
server, and authorized channel before posting.

## Commands

Use the same execution ID for every milestone and every continuation of that
local run. The helper defaults to `CODEX_THREAD_ID`; `--execution-id` can select
the original ID when resuming an earlier run. The ID determines a stable local
run key and appears as that key in each message footer.

From the source checkout, examples are:

```sh
python3 scripts/notify-local-automation.py check
python3 scripts/notify-local-automation.py started
python3 scripts/notify-local-automation.py validating --report-file /private/run/validating.json
python3 scripts/notify-local-automation.py handoff --report-file /private/run/handoff.json
python3 scripts/notify-local-automation.py unchanged --report-file /private/run/unchanged.json
python3 scripts/notify-local-automation.py failed --report-file /private/run/failed.json
```

The scheduled task invokes the installed absolute runtime path instead of
depending on the user's current branch. Preserve its execution ID and reports
in the existing private execution receipt. A missing logger or delivery error
does not bypass or replace any merge, validation, or push safeguard. Report
notification problems in the task and continue otherwise authorized work.

`--preview` prints the exact payload without contacting Discord or writing a
receipt. A handoff preview still checks GitHub's actual run metadata.

## Reports

A validating report needs `summary`, `upstream_sha`, and `recovery_ref`.
An unchanged report needs the fetched `upstream_sha`. A failed report needs a
short `stage`, factual `summary`, and optional `recovery_ref`. It must describe
known state accurately, especially after an uncertain push or dispatch.

A handoff report contains:

```json
{
  "summary": "Describe the upstream changes and actual merge decisions.",
  "upstream_sha": "FULL_PINNED_UPSTREAM_SHA",
  "merge_sha": "FULL_VALIDATED_PUSHED_MERGE_SHA",
  "validation": "List the checks that passed; include test counts only when measured.",
  "recovery_ref": "refs/codex/upstream-merge/ACTUAL_RECOVERY_REF",
  "changelog_line": 7,
  "workflow_run_id": 123
}
```

Use the same facts as the task's final merge summary. No local filesystem links,
credentials, raw failure logs, or invented test counts belong in a report.
The helper constructs the changelog URL at the exact merge commit and line.
It checks that GitHub accepted a release run on `main` for both supplied SHAs,
then links that run and reports its observed state. A queued/in-progress run
does not imply publication succeeded. A completed run is described by its
actual conclusion and points to GitHub's result notification for publication
details. The local automation does not perform device testing.

## Delivery and interruption recovery

Each event gets one durable intent before its POST. The confirmed message ID
is saved before read-back. The sender verifies the channel, webhook author,
content, embed, and suppressed mentions, then records the message link.
Repeating a delivered event returns that receipt without another network call.
Changing its report does not replace or resend the message.

When read-back fails after a confirmed message ID, repeat the same command to
finish verification using GET requests. If the POST outcome is unknown, the
sender stops rather than retrying. Inspect the channel read-only, match the
webhook, full payload and local-run/event footer, then reconcile the observed ID:

```sh
python3 scripts/notify-local-automation.py handoff \
  --execution-id ORIGINAL_EXECUTION_ID --message-id ACTUAL_DISCORD_MESSAGE_ID
```

Reconciliation never posts a message. Preserve uncertain intents and report
unresolved delivery in the task. A closing status prevents further new local
milestones for that execution; GitHub owns progress after the handoff.

The `setup` event is a factual one-time activation check. It does not claim a
merge started, passed validation, or produced a release.

Tests use fake Discord responses and temporary files:

```sh
python3 -B scripts/test-notify-local-automation.py
python3 -B scripts/test-notify-fork-daily.py
python3 -B scripts/test-automation-release.py
```
