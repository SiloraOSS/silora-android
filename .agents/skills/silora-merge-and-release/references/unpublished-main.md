# Release unpublished main commits

Use this path on either merge-and-release route when the fetched official
upstream tip is an ancestor of Silora's selected `main` commit. Reconcile
unfinished executions before building.

## Select the candidate

1. Pin the committed `main` candidate and the fetched `silora/main` SHA. Use
   the fetched remote commit when local `main` is missing or merely behind.
   Otherwise, use the clean local `main` commit if it equals or descends from
   remote main. Inspect local-only commits. Apply the usual source checks for
   divergence, tracked edits, in-progress operations and untracked build
   inputs. A dry run selects the candidate without moving local `main`.
2. Read GitHub Latest with
   `gh api repos/SiloraOSS/silora-android/releases/latest`. Verify it is
   a regular public release with both APKs and `build-info.json` as nonempty
   assets. Download that exact tag's `build-info.json` with
   `gh release download`, using `--repo SiloraOSS/silora-android` and a private
   scratch directory. Resolve the tag to its commit, peeling an annotated tag
   if needed, and require it to match the full `source_sha` in the metadata.
   Fetch the exact published commit if needed. Stop this fallback if Latest
   cannot be verified.
3. If the released and candidate SHAs or tracked trees match, report that
   GitHub Latest already contains the same source and stop before building.
   Otherwise, require the released commit to be a strict ancestor of the
   candidate. Use `git log --oneline PUBLISHED_SHA..CANDIDATE_SHA` to show what
   would ship, including both pushed and local commits. Stop if the candidate
   is behind or diverged from the released source.

## Prepare and publish

Keep the execution ID, selected route and any invocation approval. Set
`operation: release_unpublished_main` and save the candidate SHA, upstream
tip, reviewed remote main SHA, Latest tag and Latest source SHA. Resume
earlier local fallbacks recorded as `release_only` with their original pins
and receipts.

- On the **GitHub route**, run the [same local signed validation](../SKILL.md#merge-validate-and-commit-locally)
  from the pinned commit, including tests, lint and both signed APKs. Use the
  existing commit for the review, approved push if needed, and
  [GitHub handoff](../SKILL.md#push-dispatch-and-verify-the-approved-release).
  Dispatch **Silora Automation Release** with the approved source SHA and the
  official upstream SHA already included in it. GitHub builds the final APKs
  from that source.
- On the **local route**, use the [current-branch build and publication steps](release-only.md#pin-and-build)
  for the pinned commit and exact signed archive.

Keep the existing commit as the release source. The review records the
unpublished range since Latest and the checks actually run. Follow the usual
approval rules for either route. In a dry run, build only in the preview
checkout and wait for the explicit release choice.

Before pushing or handing off, recheck Latest along with the normal checks
for the selected route. If the same source has since been released, report
that result and stop. Continue against a newer baseline only if the candidate
still qualifies and the approved source and workflow match. Report progress
in chat. For local APK builds, use the
[staff milestones](local-release.md#staff-milestones). For GitHub builds,
use the existing cloud notifications and the standing changelog announcement
after verified publication.
