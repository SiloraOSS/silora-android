# Publish the signed local APKs

`--local` and `--release-only` use `./release.sh local` and **Silora Publish
Local Build** (`fork-local-release.yml`) in `SiloraOSS/silora-android`. GitHub verifies the
uploaded payload and publishes under the shared `silora-release` lock. The
publisher checks Android version ordering and keeps both public APK filenames.
The same archive checks apply to interactive and invocation approval.

## Prepare the exact archive

1. For an upstream merge, follow the merge skill through the fork audit,
   conflict resolution, staged diff checks and changelog. Create the local merge commit
   before the full release build so the build records the exact release SHA.
   Record the checks already performed in `CHANGELOG.md` and state where the
   remaining results will be recorded (the execution review and build
   metadata). Keep the full signed build, lint and tests as gates before
   approval. Attach their actual results to the receipt and review after the
   build. An amendment, including a changelog-only amendment, changes the
   source SHA and requires an archive built from the new commit. For
   release-only, start with the [committed branch source](release-only.md#pin-and-build).
2. Verify the pinned inputs are ancestors of HEAD. Require a clean tracked
   tree and index and no untracked build inputs. Run the skill's mandatory
   build from this checkout with the same persistent signing directory and
   add `--output-file` pointing to a fresh path in the private execution
   directory. Pass the same private `--highlights-file` prepared for the build
   and compare its hash with `release_highlights_sha256` in build metadata.
   Use the reported archive under this checkout's `dist/local-*`.
   A failed build leaves its local commit and recovery ref available for
   repair. After repair, rerun validation on the exact corrected commit.
3. Validate the archive with `scripts/local-release.py`'s `validate_archive`
   before review. Compare `source_sha` and `upstream_checked_sha` in
   `build-info.json` with the prepared commit and included upstream. For a merge,
   the included upstream must match the pinned merge input. For release-only,
   record and verify the included upstream ancestor. Require
   both targets, clean-source flags, lint and unit-test evidence, both APKs,
   matching R8 mappings, the expected signing certificate and verified
   checksums. Preserve the actual test reports. Build metadata comes from the
   build invocation and stays intact.
4. Record the absolute archive path, the SHA-256 of each archive file
   including `SHA256SUMS` and the release notes, both APK version codes and
   the planned public tag (from the publisher's `public_tag(info)`). Use the
   publisher's `stage_files` in a private scratch directory to record the
   expected public `SHA256SUMS` hash. Staging adds publication metadata and
   covers the release notes in the public checksums. The staged APKs and
   mappings retain their original hashes. Keep the archive and this evidence
   for review and resumption.
5. Check the selected publisher and workflow identity. Read back the planned
   tag, draft and any existing release for this source before treating it as
   a new handoff. For a new release, check ordering against current Latest
   using `python3 scripts/release-order.py "$archive/build-info.json"` before
   review and again before pushing. If the build is stale, rebuild the same
   clean commit and review its new artifact identity before proceeding.

Show the planned tag, reviewed workflow, exact source SHA, archive path, phone
and TV versions and hashes in the approval review. Use
[the shared approval rules](../SKILL.md#apply-live-approval) to bind the
approved push and publication to these APKs. Before pushing, verify HEAD, both
pinned inputs, all archive file hashes, and remote main against the review.
Reconcile the exact tag before applying the ordering check to a release
already attempted. An older cloud preview can use this route
only after an explicit route change, a clean exact-commit build and a new review.

## Publish and verify

After the shared approval and verified push, recheck GitHub main equals the
approved SHA. From the prepared checkout, set `archive` to the reviewed
absolute path and use the same signing directory as the build:

```sh
./release.sh local "$archive" --publish \
  --reviewed-manifest-sha256 "$reviewed_manifest_sha256" \
  --dispatch-receipt "$local_dispatch_receipt" \
  --signing-dir /home/albinoman887/Workspace/SigmaStreams/Silo/source/silora-signing
```

Use the staged checksum manifest hash saved at review and a stable receipt
path under `handoffs/` named `<planned-tag>-local.json`. Use the same path
across chats for this tag. Include `--reject-published-source` for merges and
unpublished-main releases. A direct `--release-only` request can explicitly
publish a new build of the same source. Verify the prepared checkout has the
reviewed publisher and workflow support before pushing or uploading.

Before invoking it, save the push receipt, approved archive identity,
planned tag, dispatch time window and existing publish-only run IDs for
the approved SHA in the private execution receipt. Send `release_uploading`
before the approved upload. Mark the handoff pending
before the first mutation. The publisher verifies the binaries and source,
creates or resumes the exact draft, verifies the uploaded bytes and queues
the publish-only job. Record the command outcome. `./helper.sh` would run
another build, so use the archive handoff above.

Find the accepted run on the selected workflow in the verified repository.
Check branch `main`, event `workflow_dispatch`, approved head SHA, and the
run title `Silora local TAG MANIFEST_SHA256`. Use the pre-dispatch snapshot
and receipt timestamp when several attempts appear. The workflow rejects a
different head for this reviewed handoff. After a correctly pinned dispatch,
a normal advance of `main` can leave the approved source eligible if it is
still an ancestor and passes publication checks. Follow progress with bounded
waits and use the
exact public tag and asset read-back as the publication authority even if
run correlation is incomplete.

Confirm the tag resolves to the approved source, the release is public and
regular, and its downloaded payload contains both APKs, both mappings,
`build-info.json`, `SHA256SUMS` and `RELEASE_NOTES.md`. Use `validate_archive`
with the approved source, planned tag and recorded staged manifest hash.
Compare the APK and mapping hashes with the reviewed archive.
Check the published metadata for the exact source, upstream, local archive
tag and versions. Read back GitHub Latest and report if a newer release has
since superseded this build. Save the actual release URL and run evidence
in the receipt. A queued job or matching draft is progress only. Send
`release_published` after verification, then use the
[standing changelog announcement](release-announcement.md) for the new
release. Resume any outstanding Discord delivery from the same execution
receipt.

On interruption, inspect the exact tag, draft assets and matching runs before
calling the publisher again. The publisher checks the reviewed manifest before
uploading and writes the dispatch intent before submitting the workflow. With
no intent and no matching job, resume a matching partial or complete draft
with the original archive. With an intent, adopt an accepted job or verify the
public release. If the intent is present and no job is visible, keep the
outcome uncertain and inspect the unfiltered workflow history and exact run
title. An interrupted invocation may have stopped either side of the send.
Keep the original intent. If absence of a dispatch is established and a new
attempt is approved, record the evidence and use a separate retry receipt.
For a failed run, inspect the failure and seek the same reviewed retry approval
as for cloud. Prefer a failed-job rerun when the archive is still eligible.
Verify any same-source Latest release before reporting it or attributing an
announcement to this execution. A stale archive or changed source, route or
artifact identity requires a new review.

## Staff milestones

For any live local APK release, use the checkout's
[repository notifier](../../../../docs/local-automation-notifications.md#local-apk-release-milestones)
with the existing #📋・mod-log webhook, the same execution ID and the pinned
`source_sha`. The older installed merge sender remains the entry point for
`started` and early upstream checks on merge invocations.

- Send `release_building` before the local build.
- Send `release_ready` when an interactive approval is pending. With
  `--unattended`, proceed to the approved publication steps.
- Send `release_uploading` after verifying the approved SHA on GitHub main and
  before the upload. Send `release_published` after public asset verification.
- Use `release_stopped` for a conclusive stop after the candidate is pinned.
  Reconcile pending deliveries and uncertain releases before treating them as
  stops. If Latest already contains the same source, report the actual result.

Dry-run preparation sends no milestones. A preview promoted to live begins
with the next applicable publication milestone.
