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
atomically. Record the `operation`
(`merge`, `release_only`, or `release_unpublished_main`), `origin_mode`, current `mode` (`live` or `dry_run`),
`release_route` (`cloud` or `local`), the execution ID, chat
association when available, worktree, stage, pinned inputs, recovery ref,
tested-tree evidence/build archive/log paths,
validation results, local merge SHA, reviewed remote baseline, verified pushed
SHA, review, approval, and accepted workflow run as they become known. For a
live `--unattended` request, save the actual invocation or a reliable chat-message
reference in the new receipt as authorization for that execution. Bind it to
the prepared result only after validation. For an interactive run, record the
actual reply or a reliable reference after presenting the review.

Before any push or publication, record the exact source, upstream, push
destination, workflow, mode, route and approved actions. Local approvals also
bind the reviewed archive hashes, planned tag and APK versions. Keep
invocation approval with its execution. Other receipts, boolean fields and
scheduled automation settings cannot supply approval. Keep credentials out of
reports. Record local draft, dispatch and verification evidence as described in
[local APK releases](local-release.md).

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

## Coordinate with other local runs

The manual skill and the scheduled merge share one coordinator on this machine.
Use the script from the normal checkout, with its default private state directory.
Use one execution receipt ID for the whole run, including approval pauses and
resumptions. Each new claim gives that execution a new reservation token.

```sh
coordinator=/home/albinoman887/Workspace/SigmaStreams/Silo/source/silo-android/scripts/silora-release-lock.py
python3 "$coordinator" claim --execution-id "$execution_id"
python3 "$coordinator" run --token "$reservation_token" -- git fetch --no-tags upstream refs/heads/main:refs/remotes/upstream/main
python3 "$coordinator" release --token "$reservation_token"
```

Read the token from the successful claim's JSON. Keep it in private execution
state for the active session. Use `run --token ... -- COMMAND` from the selected
working directory for every local mutation in this workflow, including shared
ref updates, worktree changes, merge operations, builds, pushes, release
handoffs, and local progress sends. Pass the token on each tool call. Wrap the
actual foreground command, including any shell redirections that write shared
files, so the child process holds the lock until the command exits. Read-only
status checks and monitoring an accepted GitHub run can run outside the lock.

Claim before start notifications and reconcile existing execution and handoff
receipts before doing work. A claim that exits 75 means another execution owns
the reservation or a guarded command is still active. In a manual run, report
the active execution and stop or monitor read-only. On the schedule, skip this
occurrence quietly. A coordination error stops the mutating steps.

The reservation bridges tool calls and expires after 15 minutes without a
guarded command or `renew --token ...`. An active command holds the kernel lock
even when it runs longer than that window, and a completed command refreshes
the reservation. Renew during active preparation if extended read-only work
needs more time. Release before an approval or product-decision wait, after a
verified GitHub handoff, or when local work ends. On resumption, obtain a fresh
token and recheck the checkout, source pins, remote baseline and existing
handoffs before continuing. Preserve previous receipts across crashes and
reboots. For an isolated dry run, use the reservation when changing shared
refs or worktrees, then release it while the preview awaits a choice.

## Resume an interrupted execution

- Match the request to one receipt and recover the recorded route, pins, and
  approval scope. A new invocation, a read-only inspection, and an explicit
  stop are distinct from resuming that execution. An explicit stop keeps the
  run paused. An already accepted GitHub job can continue independently, so
  check its live state and report whether publication has already occurred.
- Before a local commit, match the recorded worktree, `HEAD`, `MERGE_HEAD`,
  recovery ref, and staged/unstaged changes. If the commit completed before the
  receipt was saved, inspect its parents, diff and matching changelog entry
  before adopting it. Preserve unexplained changes and leave other Git
  operations alone. Reuse checks only if their completed evidence matches the
  tested tree. With a missing or incomplete local build receipt, recover an
  archive only if its identity and all validation evidence can be established.
  Otherwise rerun the affected checks and build before binding approval.
- Record `push_pending` before attempting the push. On recovery, a remote at
  the approved SHA establishes acceptance. A remote still at the reviewed
  baseline allows the same fast-forward attempt after rechecking eligibility.
  A third SHA requires inspection. Reconcile a dispatch, tag, draft or public
  result before attempting publication again. An approval for one archive or
  workflow never carries over to a replacement.
- If the workflow or notification result is uncertain, record it separately
  from the push and public release status. A lost status response can follow
  successful publication. Match the exact tag, source, assets and run before
  sending a completion or an announcement. Reuse the original message and
  delivery receipts when recovering a Discord send.

## Notifications

This section is live-only. A dry run sends no milestones, including start,
unchanged or failure messages. Keep planned messages explicitly unsent in its
local report; do not create delivery intents as if they had been attempted.
Keep for later records a saved preview without approval, notifications,
automatic cleanup or future scheduled work. If Matt selects Release this
result, do not replay start/validating messages for the completed local preview.
Use the same execution ID. The `validating` and `handoff` events and GitHub
result notifications below apply to cloud. Use the checkout notifier for
[local APK milestones](local-release.md#staff-milestones) on `--local` and
`--release-only`. For upstream-merge start and pre-candidate events, and
cloud merge milestones, use the installed sender:

```sh
python3 /home/albinoman887/Workspace/SigmaStreams/Staging/silora-upstream-automation/local-notifier/notify-local-automation.py EVENT \
  --execution-id EXECUTION_ID
```

Append `--report-file PRIVATE_REPORT_JSON` for events that require a report.
Use the existing SiloraOSS webhook in #📋・mod-log. Never print its URL/token,
create a replacement webhook, or use `setup` for a normal run. Keep staff
milestones distinct from the
[release announcement](release-announcement.md) sent through Discord tools.

| Event | When to send | Report fields |
| --- | --- | --- |
| `started` | Once per new execution, before workspace/fetch/merge work | None |
| `validating` | Cloud merge, audit and resolutions complete and local checks about to start | `summary`, `upstream_sha`, `recovery_ref` |
| `unchanged` | Fresh upstream is already included and the selected route concludes without building or releasing | `upstream_sha` |
| `handoff` | Cloud merge, approval received, push verified and exact publishing run accepted | `summary`, `upstream_sha`, `merge_sha`, `validation`, `recovery_ref`, `changelog_line`, `workflow_run_id` |
| `failed` | Work stopped before cloud handoff or before a local APK candidate was pinned | `stage`, factual `summary`, optional `recovery_ref` |

An approval wait sends no terminal event. A decline or deferral is recorded in
the execution receipt and reported in chat; do not falsely describe it as a
failed build or successful GitHub handoff. If deferred work later resumes,
retain the same execution ID and review, refreshing approval if its inputs
change. A continuation after the approved push can proceed to its approved
release without another routine confirmation. Do not send another
start/validating post on continuation.

For an [unpublished-main release](unpublished-main.md), use the staff
milestones for the selected route. On the GitHub route, report validation and
handoff in chat and let the workflow report its cloud build and result.

Handoff summaries for actual merges must match the chat review and measured
validation results.
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
continuing the same run. After a cloud handoff, GitHub owns result messages.

A missing logger or configuration/network error does not bypass any merge,
validation, signing, push, or approval gate. Preserve its receipt, report the
notification problem, and continue otherwise authorized work. Keep webhook
configuration and delivery receipts outside Git.
