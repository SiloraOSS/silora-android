# Releases from the local merge automation

**Silora Automation Release** builds and releases one exact commit supplied by
the local upstream-merge automation or the manual merge-and-release skill. It
lives beside **Silora Manual Release** and **Silora Daily**. The release routes share signing secrets, versioning,
download filenames and a publication lock. The publishers now also share a
version-order check: an older or equal build cannot replace the current Latest
release. Notification handlers retain their existing scope.

The new workflow has only `workflow_dispatch`. It has no schedule or push
trigger. Adding these files does not redirect the saved local automation.
Publication defaults to **off** so the first GitHub run can validate the build
without creating a tag, a draft, or a public release.

## Handoff

The local task still runs `$silora-upstream-merge`, validates both signed
release builds with tests and full release lint, commits the merge and its
changelog, pushes to `silora main`, and checks the remote SHA. When upstream
is already included and main has unpublished source, the manual skill can
validate and hand off that existing commit through the same workflow.

The handoff supplies two full lowercase commit SHAs.

- `source_sha` is the validated Silora commit on GitHub main.
- `upstream_sha` is the official upstream snapshot included in that commit.

The source must equal the workflow's selected `main` commit and its checkout.
The upstream commit must be an ancestor of that source and of official upstream
`main`. A publishing run also checks that remote `main` still equals the source
after acquiring the release lock. A race or stale queued request fails before
building. Later branch changes do not change the selected source or its tag.

Use the helper from the validated checkout after verifying GitHub main.
Supply the two pinned SHAs.

```sh
python3 scripts/dispatch-automation-release.py \
  --source-sha FULL_VALIDATED_SOURCE_SHA \
  --upstream-sha FULL_PINNED_UPSTREAM_SHA \
  --receipt-dir /home/albinoman887/Workspace/SigmaStreams/Staging/silora-upstream-automation/handoffs
```

This runs **validation only**. Add `--publish` to request a release after the
validation run has passed and the new workflow is ready to take over.
`gh workflow run` supports explicit inputs and branch selection; see the
[GitHub CLI reference](https://cli.github.com/manual/gh_workflow_run).

## Checks and publication

Every build requires the release-tooling tests, the full Gradle unit test
suite, full phone and TV release lint, dependency verification, and signed,
R8-optimized phone and TV release APKs. There is no skip-tests input.

The Gradle test and lint/build steps retry dependency downloads rejected with
HTTP 408, 429, 500, 502, 503 or 504. They also retry when Gradle reports a
module missing, lists its Maven Central POM among the locations it searched,
and the exact module version already has checksums in dependency verification
metadata. That retry uses `--refresh-dependencies` so Gradle looks up the
module again.

Each step allows three attempts total with two minutes between attempts.
Retries reuse completed Gradle work and appear as warnings in the job log.
Unknown dependencies, authentication failures, code or test failures, lint
findings, signing errors and dependency-verification errors stop the step.
The build job retains its 60-minute limit.

The APK verifier checks the signature against the persistent keystore's public
certificate, `org.silora.app`, the expected phone/TV version codes and version
name, and `debuggable=false`. The build keeps matching R8 mappings for 90 days.
Verified build artifacts, including checksums and a release receipt, remain
available for 30 days.

Publishing retains the existing Pacific-time `build-YYYY-MM-DD.HHMMSS.RUN_ID`
tags and version calculation. It reserves a source tag before building and
reuses that tag on a rerun of the same run. It creates or resumes an unpublished
draft, uploads both APKs and verification files, downloads them, checks their
contents, and only then publishes a regular **Latest** release. The final API
read confirms the public release exists with all required files.

The public assets remain `silora-phone.apk`, `silora-tv.apk`, `build-info.json`,
and `SHA256SUMS`, with `release-receipt.json` added. `SHA256SUMS` covers both APKs,
build info, and the receipt. Existing permanent APK download URLs keep working.

## Duplicates and recovery

The scheduled merge and manual release skill use the same [local release
coordinator](../.agents/skills/silora-merge-and-release/references/execution.md#coordinate-with-other-local-runs)
before sending start notifications, modifying a checkout, building, pushing,
or handing off to GitHub. A scheduled run skips an occurrence when the local
reservation is busy. GitHub's concurrency group covers the remote phase after
the verified handoff.

The local helper locks its private receipt directory and records dispatch
intent before contacting GitHub. It searches for a matching run by source,
upstream, and mode, then records its ID and URL. Repeating the command adopts
an active or successful run instead of dispatching another one. A network or
API error is a failure, never evidence that nothing changed.

If the dispatch response is uncertain, repeat the same command to reconcile it.
If no matching run is visible, the helper stops; it does not blindly dispatch
again. Inspect GitHub before resolving an uncertain receipt. Do not delete a
pending receipt merely to bypass that stop.
If main moved between the final check and dispatch, search recent workflow
runs without the expected-head filter. A run can have the requested inputs in
its title and a different dispatch commit. The workflow rejects that run at
preparation. Verify its outcome before resolving the missing match.

A failed or cancelled run needs review. After inspecting the failure, a new
dispatch can be requested with `--retry-reviewed RUN_ID`. The helper requires
that ID to match the latest failed run for the exact source, upstream, and mode.
An uncertain response to that retry is protected from another automatic send.
An already accepted newer run is adopted instead.

All three release workflows use the existing `silora-release` concurrency
group. Running releases are not automatically cancelled. GitHub allows one
running and one pending run per concurrency group; a newer pending run may
replace an older pending run. This is not a first-in, first-out queue. See
[GitHub's concurrency documentation](https://docs.github.com/en/actions/reference/workflows-and-actions/workflow-syntax#concurrency).

Under that lock, the cloud publisher skips a source SHA that already has a complete,
regular public release. Drafts, prereleases, failed builds, and incomplete
releases do not satisfy that check. Public assets are never replaced. Rerunning
an interrupted publish verifies an already public payload before reporting
success, or resumes its unpublished draft. Prefer **Re-run failed jobs** for a
failed publish; do not dispatch a new release automatically.

## Receipts and notifications

The [local automation milestones](local-automation-notifications.md) report the
start, merge preparation/validation, and validated merge handoff to the existing
staff log webhook. Unchanged or stopped runs receive a closing local status.

- The private local handoff receipt records both SHAs, dispatch intent, run ID,
  run URL, and the last observed status. It stays outside the source repository.
- `release-receipt.json` records the source and upstream SHAs, source tag, build
  run and attempt, signing certificate fingerprint, checks performed, and APK
  checksums. Its `verified` status describes the build, not publication.
- The `silora-automation-result` artifact records the final job outcomes and
  distinguishes validation, publication, an already published source, and
  failure/cancellation. It is retained for 90 days. An early manual cancellation
  can prevent the result job from running; the Actions run remains authoritative.

**Silora Automation Notifications** uses the existing Discord configuration and
verified delivery transport through a separate listener. It distinguishes
validation from publication and duplicate skips. Only failures mention the
configured users. Existing daily/manual listeners and handlers are unchanged.
Validation-only GitHub runs will still generate their configured staff status
notifications. No notification is sent by local checks.

The new listener serializes each triggering run/attempt/event and keeps intent
and delivery artifacts for 90 days. Repeated events skip a verified delivery.
An intent without a delivery receipt stops the listener for inspection instead
of sending again. This also covers an interrupted Discord read-back or receipt
upload. Do not remove an intent to bypass reconciliation.

## Activation and rollback

1. Finish local checks and publish these new files to `SiloraOSS/silora-android`
   `main` through the normal reviewed Git process. Do not include unrelated
   local commits or files. No pull request is required by this workflow.
2. Run the helper without `--publish` against that exact `main` commit and its
   included upstream SHA. Verify the GitHub tests, lint, signed APK checks,
   artifacts, result receipt, and validation-only notification.
3. Once that run passes, replace only the saved local automation's manual
   dispatch step with the helper command using the merge's pinned SHAs and
   `--publish`. Preserve its schedule, model, merge skill, local validation,
   push safeguards, no-change behavior, private receipts, and notification
   preferences. The helper's run matching replaces the old manual-workflow
   lookup. Reconcile existing incomplete manual dispatches before the switch.
4. Verify the first actual release's public receipt, APK checksums, and staff
   completion notification. Keep the original manual workflow available.

Before activation, rollback means leaving the saved automation unchanged.
After activation, restore its original manual dispatch and reconciliation
instructions. Do not delete or rewrite public releases or move source tags.

Local checks:

```sh
python3 scripts/test-automation-release.py
python3 scripts/test-fork-release.py
python3 scripts/test-build-local-release.py
python3 scripts/test-gradle-with-retry.py
python3 scripts/test-notify-fork-daily.py
scripts/check-build-supply-chain.sh
actionlint .github/workflows/fork-automation-release.yml .github/workflows/fork-automation-notifications.yml
```

Local checks exercise recovery and publication with fake GitHub responses.
They do not prove a real runner can build and publish; the validation-only
GitHub run is the remaining integration check before activation.
