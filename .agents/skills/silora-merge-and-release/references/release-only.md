# Release the current branch

`$silora-merge-and-release --release-only` publishes the current committed
Silora source with the existing local APK publisher. It skips the upstream
merge and the upstream no-change decision. An invocation authorizes preparing
the release and sending the live staff milestones. A single approval covers
the exact commit, the archived APKs and the fast-forward of remote `main`
when needed.

## Pin and build

1. Read the repository instructions, [execution receipts](execution.md), and
   [local APK release checks](local-release.md). Reconcile competing release
   attempts read-only. Record `operation: release_only`, `release_route: local`,
   and the live or dry-run mode before preparing the source.
2. Record the current branch, full HEAD SHA, checkout and status. Require a
   named branch, a clean tracked tree and index, no in-progress Git operation
   and no untracked build inputs. Treat every committed change reachable from
   this HEAD and absent from remote main as a candidate for the review.
   Preserve the user's branch, files and history. Any desired source edits or
   commits must be handled before building the candidate.
3. Verify the `silora` fetch and push destination is
   `SiloraOSS/silora-android`. Read current GitHub `main` and fetch its commit
   if needed for the ancestry check. The candidate must equal or descend from
   remote main. If it is behind or diverged, explain which commits need to be
   integrated and stop. The publisher needs the approved source on main.
   Record the remote SHA as the review baseline.
4. In live mode build from the pinned clean checkout. In dry-run mode use the
   [preview checkout](dry-run.md#prepare-the-preview) starting at the pinned
   branch commit, and leave the original checkout alone. Send the live
   `release_building` milestone, then run the full release build once:

   ```sh
   ./build.sh --release=both --tests --lint --output-file "$build_receipt" \
     --signing-dir /home/albinoman887/Workspace/SigmaStreams/Silo/source/silora-signing
   ```

   Use a fresh receipt path in the private execution directory. The builder
   reads official upstream refs for version metadata. It builds the pinned
   commit without integrating those refs. Inspect the test and lint results
   and apply the archive, signature, mapping, checksum, version-order and
   planned-tag checks from [prepare the exact archive](local-release.md#prepare-the-exact-archive).
   The archive must identify the pinned source. Record its
   `upstream_checked_sha` and verify it is included in that source.

If Matt explicitly selects an existing archive, reuse it after the same
source and archive checks and review its actual validation evidence. Skip
`release_building` when no build runs.

## Review and publish

1. Recheck the remote baseline and reconcile the exact planned tag and any
   active release attempts. Show the branch and full SHA, the commits and
   diff awaiting a push, the actual checks, and the planned tag, APK versions
   and hashes. Name **Silora Publish Local Build**, which verifies and
   publishes these APKs. If the commit is already on main, ask for publication
   approval alone. Otherwise ask to fast-forward remote main to the exact SHA
   and publish the reviewed archive under the same approval.
2. Record the review and send the live `release_ready` milestone. Wait for
   explicit approval. For a dry run, present **Release this result**, **Keep
   for later** and **Discard** under the
   [shared preview rules](dry-run.md#release-this-result). Dry-run preparation
   sends no Discord messages. An approved preview starts with the first
   applicable publishing milestone.
3. Recheck the candidate and archive hashes, tracked tree, release eligibility,
   remote baseline and workflow. Before any push, record the approval. If needed,
   push the exact reviewed SHA using `git push silora FULL_APPROVED_RESULT_SHA:refs/heads/main`
   with no force flag. Verify GitHub main equals that SHA even if a push was
   unnecessary. A moved baseline or different candidate needs a new review.
   A push-only approval stops at the verified push.
4. Send `release_uploading` when the verified push or main check is complete
   and the approved upload is about to begin. Follow
   [publish and verify](local-release.md#publish-and-verify) to upload this
   archive, reconcile uncertain outcomes, monitor the job and verify the
   public release. Send `release_published` only after tag, public status and
   full asset read-back match the approved archive.
5. After a conclusive stop, send `release_stopped` with the known stage and
   reason. A pending approval or uncertain push, upload or dispatch is not a
   confirmed failure. Use the shared retry and reconciliation rules. Under a
   reviewed retry, keep the execution ID and send the verified publication
   when it succeeds. Report any notification delivery issue alongside the
   actual build or release outcome.

Use the [release-only Discord events](../../../../docs/local-automation-notifications.md#release-only-milestones)
and the same execution ID throughout. Report the commit, push status,
validation, and verified release link or exact stopping point in chat.
