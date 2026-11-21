---
name: silora-merge-and-release
description: Merge upstream and release Silora, or release unpublished main commits when upstream is current. Use --github (default) for GitHub builds, --local for locally built APKs, or --release-only for the current branch. Use --approved to approve a live release in the invocation or --dry-run to preview. Use silora-upstream-merge for merge-only requests.
---

# Silora merge and release

Run Matt's manual Silora release process with the existing build and release
tooling.

## Select the mode first

- `--release-only` or an explicit request to release the current branch
  without merging selects the [current-branch release](references/release-only.md).
  It builds both signed APKs locally and reports progress to the existing
  Discord staff log. Follow that reference for source selection and execution
  and use the shared approval rules below.
- **Live** mode defaults to one confirmation. `--github` selects the
  **cloud** route, the default for a merge-and-release invocation. **Silora
  Automation Release** builds and publishes the exact validated commit.
- `--local` selects the **local** route. GitHub verifies and publishes the
  exact signed APKs from local validation. Read
  [local APK releases](references/local-release.md) before preparing the merge.
  Both routes check for [unpublished main commits](references/unpublished-main.md)
  when upstream is already included.
- `--approved` in Matt's explicit request to run a live release approves the
  required push to `SiloraOSS/silora-android` `main` and publication for that
  execution. It works with both release routes and with `--release-only`.
  For example, `$silora-merge-and-release --local --approved` validates and
  publishes both signed APKs for an eligible release without the confirmation
  pause.
- `dry-run`, `--dry-run`, or "run this as a dry run" selects **dry-run** mode.
  Prepare the selected source and signed builds with tests and lint, then show
  the review and stop. Do not push, dispatch any
  workflow, create remote tags/drafts/releases, upload assets, or send Discord
  messages. Read
  [dry-run decisions and continuation](references/dry-run.md) before starting;
  it keeps preview changes separate from Matt's normal checkout. If the
  invocation also includes `--approved`, follow the dry-run flow.
- After a successful dry-run review, offer three clear choices:
  **Release this result**, **Keep for later**, and **Discard**. Release this
  result approves pushing that exact prepared commit to Silora main and
  releasing it through the reviewed route. That choice is the single approval.
  Recheck the result and proceed under that approval when the reviewed inputs
  match. Keep for later preserves the prepared work without publishing;
  Discard removes it safely.
- "Discard the dry run" selects cleanup of the identified preview, not a new
  run. Use the same reference. Handle the temporary checkout internally; Matt
  needs only the result, preview ID when useful, and the three choices.

Record the selected operation, mode, release route and any invocation approval
before any notification or merge. Older receipts default to the merge
operation and cloud route when those fields are absent. Dry-run restrictions
apply through errors and resumptions until Matt explicitly selects Release
this result for the concrete review. An unambiguous "Continue with release"
reply to that review has the same meaning. Record the selection and its approved
inputs before switching to live mode. A request to continue local dry-run
checks, an unrelated yes, silence, or an older approval does not authorize
publication. No choice leaves the preview pending without external actions.
Keep for later explicitly saves it for resumption; do not schedule publication
or clean it up automatically.

## Scope and authorization

- Repository: `/home/albinoman887/Workspace/SigmaStreams/Silo/source/silo-android`.
  Read its current instructions. For an upstream merge, read
  `.agents/skills/silora-upstream-merge/SKILL.md` with its required references.
  Follow it for merge decisions,
  validation, changelog, recovery, and commit handling. The local release
  reference defines the commit and build order for exact-SHA APKs.
- An invocation to run this combined workflow authorizes routine upstream
  integration, signed local validation, the local merge commit, and the applicable
  staff Discord milestones in live mode. Dry-run mode authorizes only the
  isolated local preparation described above. A request to create, edit,
  explain, or review the skill does not start a run. Honor narrower user
  instructions.
- Bind approval to the validated commit, destination, workflow and, for
  local releases, the exact signed archive before publication. Use Matt's
  `--approved` invocation for that live execution or get the usual confirmation
  after validation. This covers one release, including the push if needed.
  Keep the same validation and pre-push checks for both paths.
- Leave the saved automation, its paused state/schedule/model, the merge skill,
  GitHub workflows, signing configuration, and notification handlers unchanged.
  Do not create a PR, import edrock changes, or create tags/releases outside
  the reviewed release tooling.

## Prepare the run

The preparation steps below are for an upstream merge. For `--release-only`,
start with [current-branch release](references/release-only.md).

Read [execution and notification handling](references/execution.md) and the
repository docs for the selected route: `docs/automation-releases.md` for cloud
or the PC build section of `docs/fork-releases.md` for local. Reconcile
unfinished private executions, uncertain pushes, notification intents, and
release handoffs read-only before starting competing work. A resumed execution
keeps its original execution ID, pinned inputs, and receipts. A dry run only
inspects live executions; it must not resume their mutations.

For a new execution, persist a fresh UUID and run receipt under
`/home/albinoman887/Workspace/SigmaStreams/Staging/silora-upstream-automation/manual-runs/`.
Use private directory/file permissions (700/600). In live mode, send the existing
`started` milestone once before workspace/fetch/merge work. Every live milestone
uses that UUID explicitly; the chat ID alone cannot distinguish repeated runs
in one chat.

Verify the official upstream URL is `https://github.com/Silo-Server/silo-android.git`
or its equivalent SSH URL, and `silora` pushes to `SiloraOSS/silora-android`.
Use explicit repository arguments for GitHub commands. Never publish to
`origin` merely because it is the default remote.

Fetch upstream using the merge skill's scoped command, and fetch Silora `main`:

```sh
git fetch --no-tags upstream refs/heads/main:refs/remotes/upstream/main
git fetch --no-tags silora refs/heads/main:refs/remotes/silora/main
```

In live mode, use a suitable clean checkout of `main`, inspecting
`git worktree list` first.
If the current checkout is on another development branch, preserve it and reuse
or create an isolated main worktree, normally
`.worktrees/silora-upstream-automation`. Verify existing worktree ownership and
state before use. Fast-forward a clean main that is merely behind Silora main;
create a missing local main from the fetched branch if needed. Inspect
local-only commits before publication. A clean `main` ahead of Silora main can
supply the candidate when upstream is already included. Unexpected commits,
divergence, tracked edits, or an existing Git operation require inspection
before proceeding. Do not switch someone else's branch, stash/reset their
edits, abort their operation, rebase, or force-push. Preserve unrelated
untracked files. Dry-run mode uses its separate preview checkout and keeps
local main and existing live executions intact.

Pin the full `fork_before` and `upstream_tip` SHAs. Establish the upstream
status from a successful fetch. Reconcile unfinished executions before taking
the no-upstream path, including a prepared merge awaiting approval.

If upstream is already included, follow
[release unpublished main commits](references/unpublished-main.md) before
building on either route. If the source already matches GitHub Latest, stop
and report the reason. Send `unchanged` for a live no-change result when no
build ran. Dry runs make the same decision without notifications.

## Merge, validate, and commit locally

Follow `$silora-upstream-merge` through its fork audit and conflict resolution,
including the recovery ref and root `CHANGELOG.md`. For live cloud runs, send
`validating` after the merge is prepared and before the mandatory local checks.
Ask Matt about genuine product decisions or unresolved regressions; routine
resolutions need no additional approval.

For the local route, follow the
[exact-commit build order](references/local-release.md#prepare-the-exact-archive)
before running the build below. Commit the reviewed merge and changelog first,
add `--output-file` to the build command, and bind the resulting archive to the
review.

For every new upstream merge, including documentation-only merges, require
both signed local release builds with tests and full release lint:

```sh
./build.sh --release=both --tests --lint \
  --signing-dir /home/albinoman887/Workspace/SigmaStreams/Silo/source/silora-signing
```

Confirm the current options and tooling before running. Use JDK 21, the existing
SDK, persistent release signing, dependency verification, R8 optimization, and
matching mappings. Apply any additional affected checks from the merge skill.
Inspect actual test reports; disabled/empty tasks are not passing coverage.
Never substitute debug APKs, skip checks, or weaken dependency verification.
Fix merge-introduced problems and rerun affected validation; unresolved failures
stop before push or release. Keep signing material, logs, receipts, and build
output out of commits. Do not claim device testing without actual validation.

For the cloud route, finalize and commit the merge and changelog together after
validation, as the merge skill requires. Retain the tested tree/diff evidence:
during an uncommitted merge the local build can record the old HEAD. A
changelog-only final edit does not require another local compilation. GitHub
builds the public artifacts from the final commit.

Verify both pinned inputs are ancestors of the result. Fetch/recheck Silora
main before preparing the review. If it moved, inspect the new history and
safely integrate it only within the authorized merge scope. Repeat validation
for any added code and prepare an exact-commit archive if the local release
source changed. Stop for unexpected/unreviewed work. Record the current remote
SHA as the reviewed push baseline. Persist the local result and evidence,
then record approval below before pushing.

In dry-run mode, show the same factual review below with **Dry run — nothing
pushed or released** at the top. Include actual checks, the local preview
commit and diff, and the proposed push and release steps. For cloud, label
local APK versions as validation evidence because GitHub assigns its own build
metadata. For local, show the exact signed APK versions and archive as the proposed
release artifacts. Label its computed tag as planned. Show planned notifications
as unsent and report only verified remote state. Record
`dry_run_complete` and present the three choices above. The review must identify
the exact source/upstream SHAs, push destination, release route, workflow, and enabled
publication so Release this result can approve both actions. Wait for Matt's
selection before any push, workflow dispatch, or notification. If preparation
failed or produced no release candidate, show that result and omit the release
choice. Keep the preview
pending until an explicit choice. Keep, discard and release use their sections
in the dry-run reference; only release enters the approved publication phase.

## Record the review and approval

For a live merge or release of unpublished main commits, show Matt a concise
review with the details below. If upstream was already included, show the
commits since GitHub Latest and the pinned existing source. Report the merge
changelog and recovery ref only when applicable. For a direct `--release-only`
invocation, use the [current-branch review](references/release-only.md#review-and-publish)
with the same approval rules.

- The meaningful upstream changes, conflict decisions, and retained fork fixes.
- Local checks and their actual results, with device-testing limits.
- The full local source and pinned upstream SHAs, recovery ref, reviewed remote
  baseline, and local changelog/diff so Matt can inspect the unpublished commit.
  Add GitHub links after the source is pushed.
- Destination `SiloraOSS/silora-android` `main` and a regular Latest release
  through the selected route. For cloud, identify **Silora Automation Release**
  (`fork-automation-release.yml`), its GitHub compilation, and its configured
  notifications. For local, show the archive, APK versions, hashes and
  publish-only workflow as described in [local APK releases](references/local-release.md).
- The current push and publication status, including any verified work from
  this execution on resumption.

### Apply live approval

Save the review in the private receipt with its source, upstream, push
destination, workflow, mode and route. For local publication, include the
exact archive identity.

- With `--approved` from this live invocation, post the validated review as a
  progress update. Bind the recorded invocation to these exact inputs and
  actions, record stage `approved`, then continue through push and publication
  in the same run.
- Otherwise, record stage `awaiting_approval`, ask Matt to approve the exact
  push and release, and wait for an explicit reply. Ask for publication alone
  if the source is already on GitHub main. Record the reply and its scope.
  If Matt approves only the push, stop after verifying it.

Wait for a recorded approval before pushing, dispatching workflows, creating
tags or drafts, or uploading assets. While waiting, preserve the local commit
and resume record. Deferral or decline leaves publication pending or closed
without a `failed` or `handoff` milestone.

## Push, dispatch, and verify the approved release

For a direct `--release-only` invocation or a local unpublished-main release,
follow [review and publish](references/release-only.md#review-and-publish).
The steps here also cover a GitHub release of unpublished main commits.

This section is live-only, including a prepared dry run explicitly approved
through Release this result. Success alone or an older approval cannot
promote a dry run. Use its recorded release choice instead of asking for a
second routine confirmation.

Recheck HEAD, tracked source cleanliness, remote main, and the handoff
helper/workflow identity. For local, also reconcile and recheck the approved
archive before pushing. Remote main must still equal the reviewed baseline,
or the approved result when reconciling a previously attempted push. Do not
force-push, substitute a moved branch or fresh upstream tip, or silently merge
new code after approval. If the source, push destination, route, workflow, or
approved archive must change after approval has been bound, validate the new
result, show an updated review, and obtain fresh approval. This applies to
invocation approval as well. New upstream commits alone do not replace the
pinned upstream SHA.

For normal live preparation, push with `git push silora main` if the commit
still needs to reach GitHub. For an approved dry-run result, use the
exact-commit refspec in its continuation reference if a push is needed;
this pushes the same approved source without moving Matt's normal checkout.
Then independently read GitHub main and require it to equal the approved
result. Reconcile an uncertain push by
exact SHA instead of guessing or dispatching without verified acceptance.
Persist the verified push. Recheck remote main before handoff; a moved remote
must not result in a release of different code. If either action stops, retain
the approval and known state for reconciliation; never claim the push or
release failed to happen merely because its response was uncertain.

With both actions approved and the push verified, follow
[publish and verify the local archive](references/local-release.md#publish-and-verify)
for the local route. The remaining steps in this section are for cloud.

For cloud, use the validated checkout
and substitute its full lowercase SHAs. No second release confirmation is needed:

```sh
python3 scripts/dispatch-automation-release.py \
  --source-sha FULL_VALIDATED_RESULT_SHA \
  --upstream-sha FULL_PINNED_UPSTREAM_SHA \
  --receipt-dir /home/albinoman887/Workspace/SigmaStreams/Staging/silora-upstream-automation/handoffs \
  --publish
```

Verify the helper still targets `SiloraOSS/silora-android`,
`fork-automation-release.yml`, and `main`. Keep its durable receipts and run
matching; reconcile an accepted active/successful run rather than dispatching
another. Verify the accepted run ID, `headSha`, upstream identity, workflow,
branch, and publication mode. Send `handoff` for a completed merge with
its actual changelog and recovery ref. For unpublished main commits, report
the handoff in chat and let GitHub send its configured workflow notifications.
An uncertain response is unresolved state. Reconcile it and any old Manual
Release handoffs before another dispatch.

Follow the accepted run through completion, with bounded waits and concise
updates for meaningful changes. Preserve GitHub's tests, full release lint,
signed builds, artifact verification, publication lock, and configured Discord
notifications. Do not duplicate its build/result posts locally. Claim release
success only after the run and result receipt confirm publication (or a
verified already-published source), and read back the public release's source
tag, non-draft status, and required assets. A successful validation-only or
skipped run alone is not publication evidence.

Review failures before retrying. Do not automatically rerun jobs or dispatch a
replacement. Present the failure and proposed retry to Matt for fresh release
confirmation; prefer GitHub's failed-job rerun when appropriate. A new dispatch
after a reviewed failure requires `--retry-reviewed RUN_ID` for that matching
source/upstream/mode, with the helper's existing uncertain-outcome protections.

Finish with the source/upstream SHAs, validation and limits, push result,
changelog/recovery reference, Actions run, and verified release link or exact
failure/waiting state. Keep it concise.
