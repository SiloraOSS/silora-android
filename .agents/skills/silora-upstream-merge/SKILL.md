---
name: silora-upstream-merge
description: Merge Silo-Server/silo-android main into the Silora Android fork while preserving its fixes and features. Use when asked to sync or merge upstream into Silora; release publishing is outside this skill.
---

# Merge upstream into Silora

Integrate upstream changes into the checked-out fork branch, resolve conflicts,
and verify that Silora's existing behavior survives. Use a merge, not a rebase:
this fork already has shared commits and upstream merge history.

## Scope and sources

- Upstream is `https://github.com/Silo-Server/silo-android.git`, branch `main`.
  Verify the remote URL before fetching; add `upstream` if absent. An existing
  remote pointing elsewhere needs clarification before changing it.
- This fork is Silora. `origin` can point at SigmaStreams, while `silora` can
  point at SiloraOSS. Inspect actual remotes; neither name authorizes a push.
  Edrock's fork is a reference for adapted features, not the upstream source.
- Read the applicable `AGENTS.md`, `README-SILORA.md`, and
  [fork checklist](references/fork-fixes.md). All source paths in the checklist
  are relative to the repository root. Read `docs/fork-releases.md` when build
  tooling is affected or APK validation is requested.
- A normal invocation authorizes the local merge, conflict fixes, relevant
  validation, and merge commit. Honor review-only or no-commit requests.
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
   report that upstream is already included and stop. Fork-only commits are
   expected and are not a reason to reset the branch to upstream.
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

   Review the actual fork diff, relevant merge resolutions and reverts, and
   incoming changes to the same files. Build a short behavior checklist using
   the bundled reference plus current source/history. The bundled list is a
   starting point: protect newer fixes too, and do not restore deliberately
   reverted experiments merely because an old commit mentions them.

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
- Keep the current product exposure rules from `AGENTS.md`, including TV's
  exclusions and experimental gates. An upstream merge is not authorization
  to change those product decisions.
- Retain dependency verification. Do not disable checks or accept new hashes
  simply to get past a build failure; investigate the exact artifact and its
  source first. Keep credentials and machine-specific workarounds out of Git.

Review all affected fork behavior after the merge, including files Git merged
without conflicts. Stage only files belonging to this merge and its fixes.

## Validate and finish

1. Require no unmerged entries (`git ls-files -u`), review the staged diff,
   and run `git diff --cached --check`. Compare the staged result with
   `fork_before` to review what changed, and with `upstream_tip` to review the
   remaining fork differences. Account for each behavior in the checklist as
   preserved, replaced by an upstream equivalent, or unresolved.
2. Run existing checks appropriate to the incoming changes and resolutions.
   Prefer release-variant unit tests for affected modules, such as
   `:android-shared:testReleaseUnitTest`, and release lint for affected apps.
   Confirm task names against the current Gradle configuration. Shared
   playback/settings changes can reach both phone and TV. For docs-only
   changes, source/diff checks are enough. Do not add tests for small or UI
   changes; add focused coverage only for critical/high-risk shared logic.
3. If APK validation is requested, use the existing signed release workflow,
   `scripts/build-local-release.py`, or its documented macOS counterpart.
   Select phone/TV/both according to reach, preserve the persistent signing
   key and matching R8 mappings, and follow the current build options.
   Debug builds or debug-signed release APKs require an explicit request.
   Device playback claims require actual device/server checks; distinguish
   source review, compilation, unit tests, and hardware validation.
4. Fix issues introduced by the merge, then rerun affected checks. Report
   unavailable SDK/dependencies/signing or unresolved failures precisely;
   do not silently weaken checks or describe unrun checks as passed. Leave
   unresolved work uncommitted with an exact status and recovery ref.
5. When validation passes, create the local merge commit with a Conventional
   Commit title, for example `chore: merge Silo upstream <short SHA>`, unless
   the user asked to leave it uncommitted. Verify both `fork_before` and
   `upstream_tip` are ancestors of the result and inspect final worktree status.
   If a push was requested, push normally to the verified remote/branch;
   a rejection requires inspecting the new history, never a force-push.
6. Report the branch and upstream SHA, merge result, meaningful incoming
   changes, conflict decisions, fork fixes retained or superseded, validation
   and limits, recovery ref, and whether anything was pushed. Keep it concise.
