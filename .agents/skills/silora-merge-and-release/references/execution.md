# Private execution state and Discord milestones

Use the existing sender and selected release helper. This reference describes
manual-run state and the approval wait. Read
`docs/local-automation-notifications.md` in the validated checkout for current
report schemas and delivery reconciliation.

## Execution receipt

For a new run, generate a fresh UUID (for example `uuid.uuid4().hex`) and save
it before the first notification. The notification sender accepts this format.
Use the same ID explicitly for every continuation, even if it resumes in a
different chat. A genuinely new invocation in the same chat gets a new UUID;
do not reuse `CODEX_THREAD_ID` as the execution ID for multiple runs.

Save private state under:

```text
/home/albinoman887/Workspace/SigmaStreams/Staging/silora-upstream-automation/
  manual-runs/EXECUTION_ID/execution.json
  handoffs/
```

Keep directories mode 700 and reports/receipts mode 600. Write receipt updates
atomically. Record `origin_mode`, current `mode` (`live` or `dry_run`),
`release_route` (`cloud` or `local`), the execution ID, chat
association when available, worktree, stage, pinned inputs, recovery ref,
tested-tree evidence/build archive/log paths,
validation results, local merge SHA, reviewed remote baseline, verified pushed
SHA, review, approval, and accepted workflow run as they become known. Begin
with no approval. Store the actual approval reply or a reliable chat-message
reference and its exact source, upstream, push destination, workflow, mode and
release route. Local approvals also bind the reviewed archive hashes, planned
tag and APK versions. Record
that the single approval covers both push and release; a previously written
boolean alone does not establish user consent. Never put credentials or raw
secrets in reports. Keep local draft, dispatch and verification evidence in
this execution receipt as described in [local APK releases](local-release.md).

Before a new run, inspect unfinished manual executions and existing private
automation receipts for the same repository. Resume or reconcile relevant
work instead of racing another merge or overlooking a prepared local commit
or already-pushed result. Read-only inspection of a known release run is
allowed before approval; pushing or starting/retrying a workflow is not. If
multiple pending reviews make a reply ambiguous, identify the intended
execution before pushing or dispatching.

Dry-run receipts also record checkout/ref ownership, the final review, decision
and cleanup evidence from [dry-run decisions and continuation](dry-run.md).
Never reuse another execution's approval or write a handoff while still in
dry-run mode. The explicit Release this result choice can promote this exact
execution to live; preserve its dry-run origin and record that choice as the
single push/release approval. A completed/discarded preview does not authorize
or block an unrelated live run.

Suggested stages are preparing, merging, validating, committed,
awaiting_approval, approved, push_pending, pushed, dispatch_pending, handed_off,
published, failed, unchanged, declined, dry_run_complete, dry_run_kept, or discarded.
Distinguish local failure from a GitHub failure after handoff. A normal approval wait remains resumable and is
not a failure.

## Notifications

This section is live-only. A dry run sends no milestones, including start,
unchanged or failure messages. Keep planned messages explicitly unsent in its
local report; do not create delivery intents as if they had been attempted.
Keep for later records a saved preview without approval, notifications,
automatic cleanup or future scheduled work. If Matt selects Release this
result, do not replay start/validating messages for the completed local preview.
Use the same execution ID. The `validating` and `handoff` events and GitHub
result notifications below apply to cloud. See [local route reporting](local-release.md#report-in-chat)
for the publish-only workflow.

Matt authorized retaining the automation's existing staff milestone messages
for invocations of this combined workflow. Use only the installed sender:

```sh
python3 /home/albinoman887/Workspace/SigmaStreams/Staging/silora-upstream-automation/local-notifier/notify-local-automation.py EVENT \
  --execution-id EXECUTION_ID
```

Append `--report-file PRIVATE_REPORT_JSON` for events that require a report.
Use the existing SiloraOSS webhook in #📋・mod-log. Never print its URL/token,
create a replacement webhook, use `setup` for a normal run, or make other
Discord posts. Do not turn staff milestones into member announcements.

| Event | When to send | Report fields |
| --- | --- | --- |
| `started` | Once per new execution, before workspace/fetch/merge work | None |
| `validating` | Cloud route, merge audit and resolutions complete and local checks about to start | `summary`, `upstream_sha`, `recovery_ref` |
| `unchanged` | Fresh upstream already included and no unfinished run to resume | `upstream_sha` |
| `handoff` | Cloud route, approval received, push verified and exact publishing run accepted | `summary`, `upstream_sha`, `merge_sha`, `validation`, `recovery_ref`, `changelog_line`, `workflow_run_id` |
| `failed` | Local work has actually stopped before accepted handoff | `stage`, factual `summary`, optional `recovery_ref` |

An approval wait sends no terminal event. A decline or deferral is recorded in
the execution receipt and reported in chat; do not falsely describe it as a
failed build or successful GitHub handoff. If deferred work later resumes,
retain the same execution ID and review, refreshing approval if its inputs
change. A continuation after the approved push can proceed to its approved
release without another routine confirmation. Do not send another
start/validating post on continuation.

Handoff summaries must match the chat review and measured validation results.
Use the actual line number in the pushed `CHANGELOG.md` and the accepted run
ID. The sender independently verifies the run and builds the GitHub links.
It currently states no device testing occurred; do not use that template to
misreport an execution with separately requested device testing. Keep that
case accurate and report a notification limitation instead of changing the
sender or hiding the test.

The sender suppresses mentions and retains durable per-event delivery state.
Reconcile pending sends read-only: a known confirmed message ID can be read
again; an unknown POST outcome requires inspecting the existing channel and
using the documented `--message-id` reconciliation. Never erase intents or
blindly repeat a POST. Do not send a terminal failure while repairing and
continuing the same run. After accepted handoff, GitHub owns result messages.

A missing logger or configuration/network error does not bypass any merge,
validation, signing, push, or approval gate. Preserve its receipt, report the
notification problem, and continue otherwise authorized work. Keep webhook
configuration and delivery receipts outside Git.
