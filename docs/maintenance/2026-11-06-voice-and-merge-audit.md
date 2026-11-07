# Voice-search and upstream-merge audit — November 6, 2026

The audit found no omitted upstream voice file, callback or manifest requirement
in the final native-recognizer integration. It did not establish the cause of
the reported Shield failure. The earlier assurance of an unchanged upstream
implementation was too broad: Silora still adapted the surrounding search UI.
Keep the restored local implementation and the existing official-build
qualification condition in `FORK_DIFFERENCES.md`.

## Inputs and scope

- Upstream PR: [#830](https://github.com/Silo-Server/silo-android/pull/830), merged
  as `4fb8f1b59220942c0ee1123dcc6148a8a44c56d1`; three changed files.
- Initial rebased integration: `daf8dae00ae07956500224eca811185475b726ee`.
- Final native-recognizer integration, before fallback:
  `85d5137de3a4b85b49e24d13b57fde6f7b7bb1eb`.
- Current local-controller fallback:
  `3ed543631997284c3e6b649279dc6013ea474e34`.
- Archived APK examined: `dist/local-2026-11-07.013136/silora-tv.apk`, whose
  build metadata names `85d5137de`. Artifact directory timestamps use UTC.

This is a source, packaging and maintenance-instruction audit. No device voice
session was reproduced, and no new app implementation was introduced.

## Voice findings

| Boundary | Evidence and conclusion |
| --- | --- |
| Complete patch | Reviewed all three changed files: TV manifest, search screen and new voice helper. No additional file from the merged PR is missing. |
| Launch and recognition helper | `TvVoiceSearch.kt` in `85d5137de` is byte-identical to the merged upstream helper. The button calls its `start()` directly. |
| Manifest | Source permission lists and recognition queries match upstream. The TV manifest's extra fork activity is for trailers. There is no omitted upstream `RECORD_AUDIO` declaration: upstream's activity-based path did not add one. |
| Dependencies and caller | TV Gradle differences at this boundary are the application ID. `MainTvActivity.kt` and `TvSearchViewModel.kt` match upstream byte for byte; dependency definitions do not introduce a separate missing voice-manifest requirement. |
| Spoken-result callback | The shared submission block and voice-result callback match upstream exactly in `85d5137de`. They cap text, update the ViewModel and submit library/request searches. |
| Packaged implementation | Archived checksums and source metadata verify the examined APK. Its manifest includes the recognition-activity query once and no app recording permission; its R8 mapping contains the upstream voice implementation. |
| Surrounding UI | The screen is not byte-identical to upstream. Silora uses `TextFieldValue`, App/System keyboard modes, an input interceptor and custom focus/auto-open behavior. These remain integration boundaries requiring validation. |

The initial integration at `daf8dae00` also had a fork launch wrapper and a
custom result adapter; the follow-up at `85d5137de` removed those. Comparing the
helper alone would not distinguish these integrations.

In the native integration, successful nonempty recognition calls
`viewModel.onQueryChanged(query)` and `submitQuery(query)`. The ViewModel updates
its query synchronously. Upstream renders that string directly. Silora's field
renders `inputValue`, updated by `LaunchedEffect(state.query)`. The synchronization
is present; source inspection found no missing assignment. This is nevertheless
an extra asynchronous UI boundary, not proof that field rendering worked on the
affected device. No defect or race in it was reproduced in this audit.

The upstream helper silently ignores non-OK activity results and empty text.
It selects the first returned result, trims it, and only forwards nonempty text.
Without a trace, the reported symptom cannot distinguish a cancelled/empty
provider result from a delivery, lifecycle or UI integration failure. A red
microphone indicator does not establish that a usable result reached the app.
The PR author reports emulator launch/focus checks and explicitly excludes a
real spoken-result test. That is a validation gap, not proof that upstream is
broken. The reported tests used Silora's release APK; upstream's documented
checks used debug builds, so minified-release behavior is not established by
those upstream checks either.

For a future comparison, use the affected device and record the installed build,
recognizer package/version and relevant permission configuration. Trace activity
result code, result presence/count, callback delivery and ViewModel/displayed
query lengths, without recording spoken content or account credentials. Compare
with the released official build under the same conditions and complete the
spoken-query-to-search-result flow. Keep the current fallback until its recorded
qualification condition is satisfied.

## Skill findings and corrections

| Gap | Change to the skill |
| --- | --- |
| Helper parity could be treated as feature parity | Trace entry, platform declarations, callback, fork adapters and visible completion; report upstream validation limits. |
| Retirement could outrun evidence or a recorded deferral | Retain active behavior when replacement is unverified, honor current deferrals/reactivations, and distinguish an explicit user choice from a verified equivalent. |
| Incoming policy could appear to approve itself | Pin the pre-merge inventory and maintenance baseline; incoming edits do not establish product intent or erase qualification conditions. |
| Wrong checkout or stale destination | Check target branch, archived worktrees and live remote head; use explicit GitHub repository arguments. |
| Release-test success could mean no tests ran | Inspect executed tasks/reports. TV currently uses debug host tests because its release host suite is disabled; APK validation remains signed release. |
| Pre-commit APK could be attributed to the final merge SHA | Record the tested source/tree. The builder records HEAD during an uncommitted merge; exact-SHA handoff needs a build from the committed source and metadata verification. |
| Old history rationale could mislead after the rebase | Explain that normal merges accumulate merge history. A linear refresh follows a separately scoped rebase request; past force-push permission is not standing rewrite permission. |
| Remote changes after validation | Inspect push rejection, validate newly combined code, and read back the pushed SHA; never automatically force-push. |

The existing rule against inventing fork intent from stale AGENTS descriptions
remains. No new product requirements, publishing authority or automation schedule
changes were added. A review does not trigger a merge.

## Validation and limits

Re-ran deterministic comparisons of the native helper, activity, ViewModel,
submission block, source manifest and archived APK identity/checksums/query.
Reviewed the remaining search-screen differences, result-to-field path, build
metadata implementation and TV test configuration.

Validated skill frontmatter and local reference links, and checked the diff for
whitespace errors. Manually walked the revised decisions against the voice
replacement, stale Requests instructions, an incoming inventory status change,
a reactivated fix, a disabled TV test task, a pre-commit APK, a stale backup
checkout and a rejected push. These are inspection/scenario checks, not an
independent agent execution or device test. No app tests were repeated for this
documentation-only change, and the previous 1,434-test result is not presented as
a new run.
