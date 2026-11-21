# Dry run: release, keep, or discard

Prepare the selected release route and preview the result without
changing remote state. Use a separate local checkout internally so discarding
the preview does not undo Matt's normal working directory or move local main.
Do not require Matt to understand or manage Git worktrees.

## Prepare the preview

1. Persist `mode: dry_run`, the selected `release_route`, a fresh execution ID,
   and the original checkout's branch, HEAD and status in the private receipt.
   Inspect existing executions read-only. Do not resume or modify live work, reuse its
   approval, send notifications, or write to its handoff/delivery receipts.
2. For release-only, pin the current branch commit and review baseline from
   [current-branch release](release-only.md#pin-and-build). For a merge,
   fetch Silora main as the preview base and official upstream main as the
   merge input. These fetches update local refs and caches only. For either
   route, check whether the live `main` candidate already includes upstream
   without moving the original checkout. If so, use the
   [unpublished-main decision](unpublished-main.md#select-the-candidate).
   An eligible candidate becomes the preview base. Apply the selected
   operation's source checks before preparing the preview. Report
   unexpected commits, tracked edits, divergence or an in-progress operation
   that would block a live run.
3. Create a worktree in the private execution directory at
   `manual-runs/EXECUTION_ID/checkout` and a uniquely named branch such as
   `codex/silora-release-dry-run-EXECUTION_ID` from the pinned Silora base
   or the pinned commit for release-only or an unpublished-main release.
   Neither may already exist. Keep the checkout outside the original source
   directory so it does not introduce untracked files there.
   Record the exact path, branch, repository, initial SHA and ownership in the
   receipt. Do not switch, fast-forward or merge into the original checkout.
4. For release-only or an unpublished-main release, build and verify the
   pinned commit in the preview checkout on the selected route. For a merge,
   follow the merge skill, including its fork audit,
   recovery ref, signed phone/TV tests/lint/builds, changelog, validation and
   local merge commit. The dry-run request explicitly authorizes this preview
   branch as the target instead of main. With `--local`, follow the
   [exact-commit build order](local-release.md#prepare-the-exact-archive).
   Record completed checks and any refs or commits created by this execution
   in the receipt. Add a changelog entry when a merge was prepared.
5. Record final branch HEAD, tracked/index status and diff evidence, merge
   state when incomplete, and known generated output paths. A normal successful
   preview should have a clean tracked tree. Keep enough evidence to recognize
   later edits or commits before discarding. Check the original checkout's
   branch, HEAD and working state stayed unchanged; report concurrent changes
   accurately rather than trying to restore another person's edits.
6. Return the dry-run report and three choices defined in `SKILL.md` when a
   candidate was built and validated. Show real failures and unrun checks
   accurately. If the upstream and publication checks find no changes to
   release, report that outcome without creating a build or presenting a
   release choice. A completed build preview is `dry_run_complete` with its
   decision pending.

Keep the release helpers idle. The cloud dispatch helper starts a real
validation workflow even without `--publish`. Do not invoke the notification
sender, including `--preview` for handoff (which requires a real accepted run). Describe the
planned notification stages as unsent. No GitHub compilation/publication or
Discord delivery is tested by the local dry run.

## Release this result

After the concrete review, offer **Release this result**, **Keep for later**,
and **Discard**. Explain that Release this result pushes the reviewed commit
to `SiloraOSS/silora-android` main if needed and publishes through the reviewed
workflow. For a local publication, the approval covers the exact archived APKs.
Selecting that action or replying "Continue with release" is the single approval
for the actions in the review. `--approved` applies to live invocations only.
A dry-run preview still needs the explicit release choice. Keep, Discard and
silence leave publication unapproved. Keep a pending preview without
scheduling, publishing, or auto-deleting it.

Before acting, identify the exact preview and verify the recorded checkout
ownership, branch HEAD, clean tracked/index state, absence of unexpected build
inputs, pinned SHAs, actual validation evidence, remote URL and workflow/helper
identity. For local, recheck the reviewed archive against its recorded hashes
and eligibility. For a merge, the result must include the reviewed fork base
and upstream input. For release-only or an unpublished-main release, verify
the exact pinned commit and its eligibility to fast-forward remote main.
For unpublished-main releases, recheck Latest before publication.
Fetch/recheck remote main: it must match the reviewed baseline, or the approved
result when reconciling a previous push. Reconcile existing exact-source release
handoffs first. Do not combine another preview, silently add new upstream code,
or treat failed/missing validation as passed.

If those reviewed inputs still match, reuse the existing local preparation and
validation; do not merge or build locally again just to change modes. Persist
the selection's approval evidence and exact action scope, preserve
`origin_mode: dry_run`, and set `mode: live` and stage `approved`. Keep the same
execution ID. If source or publication inputs must change, repair/revalidate,
show the changed review and refresh the choice before pushing. An unknown
preview, discarded checkout, unexpected edits or failed checks cannot be
published through a bare release request.

When a push is required, push the exact approved commit from the prepared
checkout with a normal fast-forward-only push, substituting its full lowercase SHA:

```sh
git push silora FULL_APPROVED_RESULT_SHA:refs/heads/main
```

Use no force flag or `+` refspec. This is the continuation route's equivalent
of the normal main push; it preserves the local main checkout while updating
the same verified remote main. Do not push the preview branch name as a new
remote branch. Verify GitHub main equals the approved SHA. For cloud, use the
existing exact-commit release helper with `--publish`. For local, use
[publish and verify](local-release.md#publish-and-verify) with the reviewed
archive. Continue through reconciliation, handoff and publication checks under
the same approval.

Do not backfill `started` or `validating` Discord messages for work already
completed silently in dry-run mode. For a cloud merge, send `handoff` after
the run is accepted, with the same execution ID. For a direct release-only
invocation, use its [publishing milestones](release-only.md#review-and-publish).
For a local merge or an unpublished-main release, report handoff in chat
and follow the selected workflow through publication.
Use the chosen operation's failure handling for a conclusive stop after
approval.

## Keep for later

Preserve the prepared commit, checkout, build/test evidence and recovery refs.
Record `decision: keep` and stage `dry_run_kept`; leave `mode: dry_run` and
approval unset. End the run with a brief confirmation and the preview ID when
useful. Do not push, dispatch, send notifications, schedule a reminder/release,
repeat completed builds, or delete the preview. Keeping it does not alter
Matt's normal checkout or block unrelated work.

On a later request to resume, identify the saved preview and recheck its
source, evidence and remote baseline before presenting the saved review and
choices. Reuse unchanged preparation. A resume request alone does not approve
publication. An explicit request to release the previously reviewed exact
result can serve as the one approval when its reviewed inputs still match;
changed inputs require an updated review and choice. Report a missing or stale
preview accurately rather than silently recreating or publishing it.

## Discard the preview

"Discard the dry run" authorizes removing only that execution's preview
checkout, task-created branch/refs and generated files inside that checkout.
It does not authorize resetting main or deleting unrelated work. If several
previews exist and the target is unclear, identify the intended one first.

Before removal, verify the receipt's mode and ownership against the actual
Git root, registered worktree, exact path, branch and recorded HEAD. Check
tracked/index changes, untracked files and commits against the saved state;
preserve unexpected edits, commits or files and report the difference instead
of force-removing them. Do not run broad cleanup commands or reset Matt's
normal checkout.

For an interrupted uncommitted merge, abort it only in this owned checkout
after confirming it is the recorded dry-run operation and no later edits need
preservation. If task-created tracked changes remain without an active merge,
restore only their recorded paths to the recorded preview HEAD after checking
the diffs still match this execution. Remove only recorded task-created
untracked output that blocks cleanup; preserve anything unexpected.
Remove the worktree with `git worktree remove` without `--force`. If removal
refuses unexpected files or dirty state, inspect and preserve them; do not
force past the refusal. Ignored build output inside this task's checkout may
be removed with the checkout as part of the requested discard.

After worktree removal, delete only this task's recorded branch/ref values,
using an expected old SHA so a concurrent change cannot be erased:

```sh
git update-ref -d refs/heads/RECORDED_PREVIEW_BRANCH RECORDED_PREVIEW_HEAD_SHA
git update-ref -d RECORDED_TASK_RECOVERY_REF RECORDED_RECOVERY_REF_SHA
```

Substitute verified exact refs; never delete shared branches, other recovery
refs, or remote refs. Leave any ref whose value changed for inspection. Keep
the private execution receipt, mark it `discarded`, and report any retained
files/refs. Recheck that the original checkout is unchanged. Do not rewind
remote-tracking refs or delete shared caches to imitate a byte-for-byte undo.

Discard before Release this result removes local preview work only. Once a
push or release has been attempted, first reconcile its actual state; never describe local
cleanup as undoing a remote push or public release. Do not delete remote refs,
releases or handoff receipts. Keep the publication record and report it clearly.
