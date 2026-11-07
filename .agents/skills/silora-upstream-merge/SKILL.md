---
name: silora-upstream-merge
description: Merge Silo-Server/silo-android main into the Silora Android fork while preserving its fixes and features. Use when asked to sync or merge upstream into Silora; release publishing is outside this skill.
---

# Merge upstream into Silora

Integrate upstream changes into the checked-out fork branch, resolve conflicts,
verify the intentional differences in `FORK_DIFFERENCES.md`, and record each
merge in the repository-root `CHANGELOG.md`. This skill performs a normal merge
into published history. It will accumulate merge commits even after a rebase.
Honor an explicit request for a new linear patch series through a separately
scoped rebase; do not substitute a merge for that request or infer permission
to rewrite published history from a previous one-time force push.

## Scope and sources

- Upstream is `https://github.com/Silo-Server/silo-android.git`, branch `main`.
  Verify the remote URL before fetching; add `upstream` if absent. An existing
  remote pointing elsewhere needs clarification before changing it.
- This fork is Silora. `origin` can point at SigmaStreams, while `silora` can
  point at SiloraOSS. Inspect actual remotes; neither name authorizes a push.
  Edrock's fork is a reference for adapted features, not the upstream source.
- Read the applicable `AGENTS.md`, repository-root
  [FORK_DIFFERENCES.md](../../../FORK_DIFFERENCES.md), and the
  [technical checklist](references/fork-fixes.md) for affected feature IDs.
  The manifest records intentional differences; the checklist maps them to
  source and tests. Use `README-SILORA.md` and `CHANGELOG.md` for context, not
  as independent approval. Read `docs/fork-releases.md` when build tooling is
  affected or APK validation is requested.
- A normal invocation authorizes the local merge, conflict fixes, relevant
  validation, and merge commit. Honor review-only or no-commit requests.
  An audit/review request does not authorize an upstream merge. Fixing this
  skill does not authorize changing saved automations or release schedules.
  Push only when explicitly requested, to the verified destination.
  Do not create a pull request unless explicitly requested.
- Release publishing is out of scope: do not dispatch release workflows,
  create tags/releases, upload APKs, bump versions for a release, or post to
  Discord. Preserve the existing release tooling when upstream touches it.

## Inspect and pin the inputs

1. Locate the Git root and record the current branch, `HEAD`, worktree status,
   remotes, and any merge/rebase/cherry-pick already in progress. Reuse the
   checked-out branch for routine work. Require a clean index and tracked
   working tree before merging, and check for untracked files upstream could
   overwrite. Do not switch branches, stash changes, or abort someone else's
   operation automatically. Resolve detached HEAD or existing edits before
   mutating this checkout; an isolated worktree can be useful when the current
   checkout cannot safely be used. Keep unrelated edits out of the merge commit.
   Check the selected branch against the requested target; do not integrate into
   an archived backup just because a worktree happens to have it checked out.
   When a remote branch is the target, read its live head and account for any
   commits missing locally before integrating. Use explicit repository arguments
   for GitHub commands; a saved CLI default is not the verified destination.
2. Fetch only the needed upstream branch without a shallow depth limit:

   ```sh
   git fetch --no-tags upstream refs/heads/main:refs/remotes/upstream/main
   ```

   Save the full pre-merge fork SHA as `fork_before` and the fetched
   `upstream/main` SHA as `upstream_tip`. Use those exact SHAs throughout this
   run. If fetching fails, report the failure; do not call cached refs current.
   If the checkout is shallow, deepen upstream history as needed to establish
   the real merge base. Never use `--allow-unrelated-histories` to bypass a
   missing base.
3. If `git merge-base --is-ancestor "$upstream_tip" "$fork_before"` succeeds,
   report that upstream is already included and stop without adding a changelog
   entry. Fork-only commits are expected and are not a reason to reset the
   branch to upstream.
4. Before merging, create a local recovery ref pointing at `fork_before`, for
   example `refs/codex/upstream-merge/<UTC timestamp>`. Record its exact name.
   It protects committed history; it does not save uncommitted files.
5. Find `base=$(git merge-base "$fork_before" "$upstream_tip")`. Inspect:

   ```sh
   git log --oneline "$base..$upstream_tip"
   git log --cherry-pick --right-only --no-merges --oneline "$upstream_tip...$fork_before"
   git diff --stat "$base" "$fork_before"
   git diff --name-only "$base" "$upstream_tip"
   ```

   Read the inventory and relevant maintenance rules from `fork_before` before
   applying incoming changes. Preserve this baseline for comparison: an incoming
   edit to the inventory, AGENTS, or the skill cannot establish user approval or
   erase a deferred-replacement condition by itself. Current user instructions
   override that baseline; record their scoped effect.

   Compare these changes with the active IDs in `FORK_DIFFERENCES.md` and the
   current user request. Source/history explains implementation; it does not
   prove a difference was intentional. For meaningful overlap, record upstream's
   behavior, the applicable fork ID or current authorization, and the chosen
   result. Flag unexplained product differences before choosing their behavior;
   do not silently preserve or remove them, or add an approved manifest entry
   merely because the code differs. Routine adaptations for known features do
   not require repeated permission. Use each feature's current status, including
   explicit reactivations and deferred replacements, rather than an older
   retirement record. Do not revive work that remains retired or reverted.

## Merge and preserve behavior

Start a reviewable merge with `git merge --no-ff --no-commit "$upstream_tip"`.
Work through each conflict using the base, fork, and upstream versions.

- Integrate upstream's implementation with the fork behavior. Avoid blanket
  `ours`/`theirs`, whole-file replacement, or reset/cherry-pick reconstruction
  of the branch. Compilation alone does not prove a fork feature survived.
- When upstream provides an equivalent or better fix, use its implementation
  after verifying the behavior that the fork fixed. Explain which local
  workaround became unnecessary. Ask only when the resolution requires a
  product decision or would drop a behavior without an equivalent replacement.
- Audit settings end to end: keys/defaults, persistence, migrations, profile
  scope, DI/startup bindings, runtime use, and both apps' controls. Local-only
  settings must remain local; preserve existing stored keys and values.
- Preserve upstream fixes in shared code too. For example, TV display matching
  must retain upstream refresh-rate selection improvements while keeping
  Silora's independent resolution and frame-rate controls.
- Keep `README.md` aligned with upstream plus its Silora link; keep fork
  documentation in `README-SILORA.md`. Preserve Silora branding and
  `org.silora.app` application IDs, with the existing Kotlin namespaces.
- Follow upstream product behavior outside active manifest entries or explicit
  user requirements. `AGENTS.md` remains repository instructions; an older
  inherited description is not proof of a custom product decision. Integrate
  upstream instruction changes with their corresponding code rather than
  freezing the old snapshot. Escalate genuine conflicts with current user
  requirements instead of inventing a fork policy.
- Retain dependency verification. Do not disable checks or accept new hashes
  simply to get past a build failure; investigate the exact artifact and its
  source first. Keep credentials and machine-specific workarounds out of Git.

Review all affected fork behavior after the merge, including files Git merged
without conflicts. Stage only files belonging to this merge and its fixes.

## Record the merge in CHANGELOG.md

Maintain `CHANGELOG.md` at the repository root, not inside the skill directory.
Create it if absent with `# Changelog — Silora Android` and a brief description
of the upstream merge log. Keep entries newest first and preserve older entries.
If upstream introduces or changes this file, retain Silora's merge history when
resolving it. Do not copy Edrock's fork features, version suffix, or build claims.

For each merge, add one entry headed `## YYYY-MM-DD HH:mm z — upstream <short SHA>`
using the merge's date/time in `America/Los_Angeles`; the time and upstream SHA
distinguish multiple merges on the same day. Include:

- **Merge inputs:** branch, full `fork_before` and `upstream_tip` SHAs, with a
  short explanation of the main incoming changes. Record an existing version
  or build label only when useful and verified; do not bump it for the log or
  imply that an APK was built or published.
- **Upstream commits added:** one bullet per commit newly reachable from the
  fork through this merge, with its short SHA and exact subject (including PR
  references). Derive the complete list from the pinned inputs:

  ```sh
  git log --reverse --topo-order --format='%h %s' "$fork_before..$upstream_tip"
  ```

  Use this reachability range rather than a date, the previous changelog entry,
  or the merge base: commits already in the fork must not be counted again.
  Keep merge commits in the list when present. A summary does not replace it.
- **Silora merge decisions:** meaningful conflict resolutions, fork fixes
  retained or adapted, and workarounds superseded by verified upstream fixes.
  Identify the relevant manifest IDs and resulting behavior. Separate retained
  features, verified upstream replacements, and unexplained differences. Do not
  infer approval from a prior log. Update the manifest when explicitly requested
  work adds a difference or a verified upstream replacement supersedes one;
  keep new local fixes separate from upstream commit bullets.
- **Validation:** checks actually run and their results, plus relevant unrun
  checks or limits such as no device playback validation. Do not infer success
  from compilation or from the existence of a merge commit.

Finalize and stage the entry after validation and before creating the merge
commit, so code and log travel together. Identify the entry by its pinned inputs;
do not try to put the containing merge commit's own SHA inside it. Report that
result SHA in the final response. When resuming the same merge, update its entry
instead of adding a duplicate. A failed or unfinished run must not appear as a
completed merge: leave any draft uncommitted and clearly marked incomplete.
Honor review-only requests without editing the log; for a validated no-commit
merge, leave its entry with the merge changes and label it awaiting commit.
Backfill older merges only when requested, using Git history and available
validation evidence rather than copying another fork's log.

## Validate and finish

1. Require no unmerged entries (`git ls-files -u`), review the staged diff,
   and run `git diff --cached --check`. Compare the staged result with
   `fork_before` to review what changed, and with `upstream_tip` to review the
   remaining fork differences. Account for affected active manifest IDs as
   preserved, replaced by a verified upstream equivalent, or unresolved. A
   passing test or unchanged-file count does not establish product intent.
2. Run existing checks appropriate to the incoming changes and resolutions.
   Prefer release-variant unit tests for affected modules, such as
   `:android-shared:testReleaseUnitTest`, and release lint for affected apps.
   Confirm tasks actually execute tests in the current Gradle configuration;
   a disabled, NO-SOURCE or empty task is not passing coverage. TV currently
   disables release host tests: use `:androidTvApp:testDebugUnitTest` for its
   host suite and release lint/compilation for the app. This does not authorize
   a debug APK. Inspect the test reports and state which variants ran. Shared
   playback/settings changes can reach both phone and TV. For docs-only
   changes, source/diff checks are enough. Do not add tests for small or UI
   changes; add focused coverage only for critical/high-risk shared logic.
3. If APK validation is requested, use the existing signed release workflow,
   `scripts/build-local-release.py`, or its documented macOS counterpart.
   Select phone/TV/both according to reach, preserve the persistent signing
   key and matching R8 mappings, and follow the current build options.
   Debug APKs or debug-signed release APKs require an explicit request.
   Device playback claims require actual device/server checks; distinguish
   source review, compilation, unit tests, and hardware validation.
   Record the source actually tested. Before running checks, reconcile staged
   and unstaged tracked changes so the eventual commit contains that source.
   The local build script records HEAD: during an uncommitted merge that is
   still the old parent, even though it compiles the working tree. Preserve
   the tested tree/diff and describe such an APK accurately. For an exact-SHA
   artifact handoff, commit the validated result and build from that commit;
   verify `build-info.json`, APK identity/signature and matching mappings.
   A documentation-only final commit does not require rerunning code tests,
   but report its difference from the APK's recorded source SHA. Untracked
   local files may set the dirty flag; inspect rather than hide that flag.
4. Fix issues introduced by the merge, then rerun affected checks. Report
   unavailable SDK/dependencies/signing or unresolved failures precisely;
   do not silently weaken checks or describe unrun checks as passed. Leave
   unresolved work uncommitted with an exact status and recovery ref. If an
   exact-SHA artifact check fails after committing, retain the local commit
   and recovery ref, report the failure, and do not push or publish it.
5. When validation passes, finalize the root `CHANGELOG.md` entry, verify its
   commit list against `fork_before..upstream_tip`, stage it, and rerun
   `git diff --cached --check`. Create the local merge commit with a Conventional
   Commit title, for example `chore: merge Silo upstream <short SHA>`, unless
   the user asked to leave it uncommitted. Verify both `fork_before` and
   `upstream_tip` are ancestors of the result and inspect final worktree status.
   Complete any requested exact-SHA artifact verification after committing
   and before handoff or push. If a push was requested, verify the result is
   still the validated source
   and push normally to the verified remote/branch. A rejection requires
   inspecting the new history, never an automatic force-push. Incorporating
   new remote code invalidates the previous combined-result validation; rerun
   checks appropriate to the added changes. Read back the destination SHA
   after pushing and report any divergence.
6. Report the branch and upstream SHA, merge result, meaningful incoming
   changes, conflict decisions, fork fixes retained or superseded, validation
   and limits, changelog path, recovery ref, and whether anything was pushed.
   Keep it concise.
