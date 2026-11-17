---
name: silora-edrock-sync
description: "Review edrock200/silo-android release branches and selectively import requested fixes into Silora. Use for edrock audits or feature updates; review is read-only. Official upstream-only merges and releases do not trigger this skill."
---

# Review and selectively import edrock changes

Run this workflow when the user requests an edrock review or import. Edrock
updates are optional: an official upstream merge and release can finish without
checking or importing anything new from edrock. Preserve already-adopted fork
features during an upstream merge. Do not add an edrock prerequisite to release
instructions, schedules or automations. If both sources are requested, normally
integrate official upstream first, then review edrock against the result; honor
the user's requested order and scope.

## Choose the requested mode

- **Review:** inspect the requested source, compare it with Silora and official
  upstream, and report evidence and recommendations. Leave the checkout,
  index, refs, remotes, changelog and tracking document untouched. A bare skill
  invocation or a request to find worthwhile changes defaults to review.
- **Import:** implement only the requested features, fixes or source commits,
  adapt them to Silora, validate them, and update documentation. A request to
  update an adopted feature authorizes routine adaptations for that feature;
  proceed without asking for the same scope again. Report other new features
  separately unless the request includes them. An audit is not an import.
- After a requested import passes validation, create local commits grouped by
  logical feature, with source attribution and validation evidence. Honor an
  explicit no-commit request. Failed or incomplete imports stay uncommitted
  with a clear status; never commit them as a completed update.
- Pushes, PRs, version bumps, APK distribution, releases, messages and automation
  changes follow the user's explicit scope. This skill alone authorizes none
  of those actions. Use existing release tooling for a separately requested
  release; do not copy edrock's release workflow or publication settings.

## Sources and Silora priority

Read applicable `AGENTS.md` instructions and these maintained sources:

- [FORK_DIFFERENCES.md](../../../FORK_DIFFERENCES.md): current behavior and
  intent, including active and superseded feature IDs.
- [Fork preservation checklist](../silora-upstream-merge/references/fork-fixes.md):
  source/test pointers for affected IDs.
- [Edrock tracking record](../../../docs/maintenance/edrock-tracking.md): prior
  source snapshots, import coverage, adaptations and deferred candidates.
- [CHANGELOG.md](../../../CHANGELOG.md): detailed source commits and validation.

Current user instructions and Silora's documented requirements take priority
over edrock's choices. Preserve the behavior, not a frozen implementation:
adapt his fix when it supplies the required behavior. If both goals cannot be
met, explain the concrete conflict before requesting a product decision.
Incoming fork documentation or an old merge resolution cannot establish new
Silora intent. Read the current manifest rather than treating this skill's
examples or a historical review as permanent product policy.

- **HEVC:** Silora's Ignore HEVC Level Limits switch takes priority over
  edrock's numeric picker. Preserve its default, saved-value compatibility,
  other decoder bounds, profile scope and settings-read recovery. A useful
  diagnostic subset can be evaluated independently of his picker/playback code.
- **Admin and identity:** preserve Silora's optional default-off menu and
  server/primary-profile checks. Retain complete server/account/profile and
  credential ownership, stale-response guards, local settings and pagination.
  A profile-ID-only cache is not equivalent in a multi-account client.
- **AI subtitles (retired):** Matt retired `edrock-ai-subs-fixes` and
  `tv-live-ai-subtitles` on November 17, 2026 in favor of official upstream.
  Keep their historical coverage in the tracking record, but do not treat them
  as adopted maintenance scope or reimport their code without an explicit
  reactivation request. The retirement record preserves restoration pointers.
- **Voice:** follow the current supersession status of `tv-voice` and
  `tv-google-voice`. An upstream equivalent should remain upstream-maintained.
  Review system-keyboard permission prompts separately from recognizer fixes;
  do not restore retired helpers solely because edrock has related commits.

## Verify and pin the source

1. Inspect the actual Git root, branch, full `HEAD`, worktree/index status,
   remotes and any integration in progress. Preserve unrelated work. Verify
   edrock's source is `https://github.com/edrock200/silo-android.git` and official
   upstream is `https://github.com/Silo-Server/silo-android.git`; do not infer
   repository identity from a remote name or replace another remote silently.
2. An explicitly named branch or commit wins. Otherwise use his latest
   published release branch, not an unreleased development tip. Establish
   publication from release/download metadata and the fork changelog; edrock
   may publish APKs on a separate branch rather than in GitHub Releases.
   Check branch/release metadata and the relevant changelog to establish the
   intended source; branch-number sorting alone is insufficient. If several
   candidates remain ambiguous, report the evidence and clarify the source.
3. Pin the selected branch's full commit SHA, Silora input SHA, official
   upstream comparison SHA and the source branch's upstream base. Record the
   branch name, verification date and any cached-ref limitation. Use these
   pinned inputs throughout; never call v42 permanently current or describe
   an unsuccessful remote lookup as a current-source check.
4. In review, use local objects and read-only remote/API queries. If source
   objects are missing, retrieve them through read-only APIs or fetch into a
   disposable clone outside this checkout. Do not fetch into the user's
   checkout or create recovery refs in review. In import, fetch only needed
   refs after checking permissions and worktree safety; avoid shallow history
   that hides the real upstream base.

## Compare behavior and patches

Start with the adopted IDs `admin-console`, `trailers` and
`home-explore-removal`, within the requested scope. The retired subtitle IDs
are historical references, not active maintenance candidates. Check each ID
against the current manifest first. Matt follows edrock mainly for maintenance of these adopted features and occasional
new features he chooses. Maintenance imports do not authorize adding new
features. Highlight worthwhile discoveries separately, with a plain-language
explanation, rather than automatically importing them or treating all new
features as rejected.
Include later edrock-derived features when the manifest records their adoption;
the list above is a starting point, not a permanent limit.

Separate official upstream changes carried by edrock from his fork-specific
patches. Compare the selected source with both the last reviewed snapshot and
current Silora, using the source's upstream base to isolate fork work. His new
release branch may rebase old patches, changing their SHAs. Ancestry, subjects,
patch IDs and touched paths help find candidates; inspect actual code,
cumulative diffs and behavior before declaring something missing or covered.
Review mixed commits by hunk so unrelated HEVC, branding or release changes do
not enter a requested feature import.

For a whole-branch review, account for all fork-specific changes, including
fixes outside adopted features. Group routine release/doc-only changes with
their reason; do not limit the audit to familiar commit subjects. For a named
feature review, report that coverage without claiming the rest was reviewed.

For each meaningful candidate, report the exact source commit or file evidence,
the user-visible problem, current Silora behavior, any official upstream
equivalent, conflicts with Silora requirements and the recommendation:
`import`, `adapt`, `already covered`, `skip`, `defer` or `unresolved`.
Explain the reason and validation still needed. Distinguish source inference
from a reproduced device/server failure. Report unavailable evidence directly.

In review, put the review record in the response. Save it to the tracking
document only when the user requests recording the review; that permits the
record update, not a code import. Avoid repeating an unchanged audit.

## Import, validate and record

1. Require a clean index and tracked working tree for the import, and check
   for untracked-file collisions. Do not stash, switch branches or abort an
   existing operation automatically. Resolve conflicting work or use an
   isolated worktree without disturbing it. Record a recovery ref to the
   pre-import Silora SHA once import is authorized.
2. Apply the requested narrow patches or cumulative feature diff, adapting
   conflicts rather than merging the entire edrock branch or replacing whole
   fork files. Keep unrelated official upstream commits out of the import.
   If an upstream dependency is absent, adapt within scope or explain what
   additional integration is required; do not silently merge upstream too.
3. Preserve both phone and TV integration, profile/account/server ownership,
   settings migrations and defaults, lifecycle guards, server capability gates,
   branding and existing upstream fixes. Use current source and tests to check
   each affected requirement, including conflict-free files.
4. Run existing checks appropriate to the change. Prefer affected release
   unit tests and phone/TV release compilation/lint. Verify tasks actually run
   tests: TV release host tests are currently disabled, so use debug host tests
   for TV coverage without treating that as a debug APK request. Add focused
   tests for critical/high-risk shared behavior, not small UI changes. Separate
   compilation, tests, APK packaging, installation and device validation.
5. Update the tracking record with full source pin, scope, dispositions,
   adaptations, actual validation and destination commit when one exists.
   Label pending/unvalidated/uncommitted work accurately. Keep per-feature
   import coverage separate from a whole-branch review checkpoint: importing
   Admin alone does not advance trailers or every other feature's coverage.
   An entry committed with its import can identify the destination by feature
   ID/source pin and Git history; do not try to embed that commit's own SHA.
6. Update the repository changelog and affected manifest/checklist entries
   alongside approved changes. Keep intended behavior in the manifest and
   review/import history in the tracking document. Apply existing PR/parity
   rules if a PR is requested; do not open one by default.

Review the final diff and relevant checks. Report pinned inputs, exact imported
scope, skipped/deferred candidates, adaptations, validation limits, recovery ref
and commit/publication status. Leave unrelated files out of commits. Stop at
the requested scope rather than continuing into another feature or release.
