# Changelog — Silora Android

Silora tracks [Silo Android](https://github.com/Silo-Server/silo-android).
This log records upstream merges, the commits they introduce, Silora merge
decisions, and validation results. Entries appear newest first.

## 2026-11-08 — import Edrock’s admin console

Cherry-picked edrock200’s ten commits from `3cb8886ed` through `3792ac473`,
as requested by Matt. Phone and TV gain **Admin** in the profile menu for
administrator accounts, a bundled OpenAPI catalog, generic operation forms,
and dashboard, request moderation, tasks/scans and logs screens. Admin changes
require the account’s primary profile. The ordinary user Requests screens
retain their existing scope.

Resolved documentation conflicts by keeping Silora’s identity, release tooling
and existing changelog. Ported the admin maintenance instructions into
`docs/admin-console.md` instead of restoring Edrock’s deleted release skill.
Integration fixes keep the console blocked while administrator access is
unknown, require a selected primary profile, cancel stale request/log pages
when their filter changes, and preserve failed-page cursors for retries. The
nine multipart/binary upload operations report that they require web admin;
the imported engine has no file picker. Updated Silora’s feature documentation.

Focused validation: all 17 admin engine, bundled-spec, contract and pagination
tests passed. All three new pagination regressions fail against the imported
implementation and pass with the fixes. The imported baseline passed 12,548
host test executions, full phone/TV release lint and signed release assembly.
Final full validation of the integration fixes is recorded below when complete.
The dedicated test account’s login and identity were verified, but its
non-administrator role prevents live admin API validation. Device validation
remains pending.

## 2026-11-08 13:08 PST — upstream 93de7e0c4

**Merge inputs:** Local `main` at `42607c165b909e18eb23d8a7397fd5361c277db1` merged
`upstream/main` at `93de7e0c4713c1d09551964bd4e5f8fbe14334b9`. Six upstream commits
improve TV startup and marquee rendering, center the episode rail on the
next-up episode, refresh watched/resume state after playback, and keep the
English TV layout left to right while allowing metadata its own text direction.

**Upstream commits added:**

- 3112d0671 fix(tv): draw the top-bar Silo wordmark as a vector (#849)
- d9dc3f3de fix(tv): open the series episode rail on the next-up episode (#851)
- 1c572eb3a perf(tv): cut startup and marquee fade jank on Android TV (#853)
- 906e1cfa8 test(tv): match the episode rail guard to its new centering effect (#852)
- a6453471b fix(tv): show Play, not Resume, after a title is marked watched (#845)
- 93de7e0c4 fix(tv): keep the TV layout left to right in right-to-left languages (#833)

**Silora merge decisions:**

- Retained `identity`: resolved the wordmark PNG deletion and two layout-comment
  conflicts by converting the existing purple Silora mark and outlined lettering
  to upstream's vector format. The drawable keeps the 1200:400 canvas and existing
  layout sizes. Updated the branding generator to reproduce that TV vector;
  phone artwork, launcher icons, banners, splash videos and application IDs stay
  intact. This adapts the upstream rendering improvement without changing brands.
- Retained upstream's bare-SurfaceView splash, draw-phase marquee animations,
  baseline profile, episode-rail centering/reveal guards, watched/resume refresh,
  fresh catalog reads, final-progress cleanup and text-direction fixes.
- Reviewed automatically merged playback and DI code: retained `night-mode`,
  `tv-display`, `ai-subtitles`, `tv-live-ai-subtitles`, `up-next-setting`,
  `up-next-fixes` and `credits-auto-skip` alongside upstream's session-exit changes.
  Account-scoped input bindings (`input-scope`, `tv-keyboard`, `tv-remote`),
  in-app voice permission/service declarations (`tv-voice`) and embedded trailer
  activity (`trailers`) remain present. Settings keys, defaults, persistence,
  profile scope and startup bindings are unchanged by this merge.
- Other active differences, including `hevc-levels`, `tv-subtitle-size`,
  `release-tooling` and `fork-maintenance`, are unchanged. No active fork feature
  was superseded and no unexplained product difference required a decision.
  Existing live-AI-subtitle documentation was committed separately as
  `42607c165` before pinning the merge inputs, as requested.

**Validation:** Passed all 6,978 host tests with zero failures, errors or skips:

- `:shared:testReleaseUnitTest`: 2,362 tests.
- `:android-shared:testReleaseUnitTest`: 2,235 tests.
- `:androidApp:testReleaseUnitTest`: 947 tests.
- `:androidTvApp:testDebugUnitTest`: 1,434 tests. TV release host tests are
  disabled by the existing Gradle configuration; this ran no debug APK build.
- `:androidApp:lintRelease` and `:androidTvApp:lintRelease`: passed, including
  release Kotlin compilation and resource processing. Lint reports contain
  warnings/hints but no errors or fatal issues (phone: 93 warnings, 6 hints;
  TV: 98 warnings, 10 hints). Dependency verification remained enabled.
- Reviewed the full incoming/staged diff and remaining fork overlaps; no
  unmerged entries or unstaged tracked changes. `git diff --cached --check`
  passed. The tested staged patch is retained locally under
  `/tmp/silora-upstream-merge-20261108/tested-source.patch`; only this log entry
  was added after tests.
- Branding generator syntax and byte-for-byte vector regeneration passed.
  Visually reviewed a render of the vector geometry against the prior PNG:
  lettering pixels match, proportions/colors remain intact, and mean channel
  differences on charcoal are under 0.05/255 from mark edge rendering.
  Android resource compilation passed; this does not establish device rendering.

No APK build, device/server playback, microphone or RTL-device validation was
performed. No push, release, PR, workflow dispatch or publication occurred.


## 2026-11-08 — show live AI subtitles on TV

**TV shows AI subtitles while they are being made.** The TV player now draws
translation or transcription lines as the server streams them for the playing
session, instead of waiting for the finished subtitle file. It shows
“Translating subtitles…” before the first cues arrive and closes the AI dialog
when the first nonempty cue batch arrives. Media3’s subtitle view is hidden
while the live overlay is active so the two renderers do not draw together.
The phone app does not have this live-subtitle UI.

Matt cherry-picked [edrock200’s
`20e85217c927e76fe34edd820608403f4847452b`](https://github.com/edrock200/silo-android/commit/20e85217c927e76fe34edd820608403f4847452b)
as local commit `b812d6274`. The shared decoder handles
`subtitle_translation_started`, `subtitle_translation_cues`,
`subtitle_translation_completed` and `subtitle_translation_failed` events.
TV routes them to a growing cue track and draws the lines at the current
source-file position. The existing AI job poll refreshes/selects the finished
track and clears the live overlay on completion; failure and cancellation
also clear it. A completed realtime event schedules a 15-second fallback
clear if the overlay has not already been cleared.

Recorded the feature as `tv-live-ai-subtitles` in the fork inventory and
maintenance checklist, separate from the four existing AI-subtitle fixes.
The cherry-pick includes three `PlaybackLiveSubtitlesTest` cases covering
event decoding, malformed cues, batch ordering/deduplication and cue lookup.
This documentation update reviewed the local implementation and source commit;
no tests, builds or device playback were run for it. Earlier Up Next validation
predates this cherry-pick and does not validate the live-subtitle feature.

## 2026-11-08 — remove the duplicate end-only Up Next trigger

At Matt’s request, removed **After playback ends** from the phone and TV
**Up Next Trigger** choices. Keep **Credits markers** and **Time remaining**;
**Time remaining → At end** provides the same end-only behavior. The timing
row is now available under either trigger choice.

Matt confirmed the retired choice had not shipped and only his installation
could have it saved. No migration is needed: the old `off` value falls back to
**Credits markers**. Select **Time remaining → At end** again for end-only
prompts. Existing `credits` and `time_remaining` choices retain their values.

The live-video/next-item-artwork panes, spoiler-safe artwork fallbacks, end
screen, autoplay, skip controls and manual TV Up Next action are unchanged.
Updated the existing policy/preference tests and fork-maintenance records.

All 1,554 selected tests passed: seven trigger-policy tests, 88 preference-store
tests, 25 phone player load/transition tests and all 1,434 TV host tests.
Signed phone/TV release assembly and full release lint passed with no
unsuppressed errors (93 phone warnings; 95 TV warnings). The initial 4 GB heap
ran out during optimization/lint; an invocation-only 12 GB heap retry passed.
Signature, application ID, version and checksum verification passed. APKs,
matching R8 mappings and source provenance: `dist/local-2026-11-08.201449/`.
Visible version: `1.0.0 (29) (silora:11082026)`; phone code `853886578`, TV
code `853886579`. Device testing remains pending. No push, public release or
device installation was performed.

## 2026-11-06 — confirm upstream-only TV voice failure

Matt reproduced the missing voice-search text on his onn 4K Pro (Android 14)
using the unchanged upstream comparison APK at `f13a2652a`. Its search screen,
voice helper and manifest match PR #830 byte for byte, with no Silora source
patches. He reports that the microphone works in other apps and with the prior
Silora implementation. The failure therefore reproduces independently of our
merge and keyboard adaptations. Its exact recognizer/provider cause remains
undiagnosed.

Verified that the prior controller, overlay and eight session tests are already
restored byte for byte; the search-screen integration differs only by retained
upstream request-search deduplication. Reworded the restoration commit with
Matt’s permission to record this later evidence and verified an identical Git
tree before and after. Updated the inventory, technical checklist and audit.
No app source changed. Keep the fallback until an official build completes a
spoken search on the affected device.

Comparison-build validation: 1,395 TV host tests passed, release lint completed
with zero errors and 93 warnings, and signed release assembly, source identity
and artifact checks passed. Matt performed the device comparison. Per-build
validation results accompany each APK.

## 2026-11-06 — restore in-app TV voice search

Restored `tv-voice` at Matt’s request after repeat Shield testing opened the
system recognizer, including its listening indicator, but returned no text to
the search field. Defer upstream PR #830 until an official build is confirmed
to recognize speech, return its final text and submit a search on the affected
device. Its merge remains in history; its recognizer-activity path is unused.

Before restoring, compared against upstream `4fb8f1b59`: the recognizer helper,
voice result/submission callback, MainTvActivity and search ViewModel matched
exactly. Manifest permission lists and recognition queries matched, and the
recognition query was present in the tested signed APK. No omitted upstream
voice permission or callback was found. The full search screen retains fork
keyboard/cursor adaptations; source parity in the audited paths does not
establish the cause of the runtime failure. The upstream PR’s validation notes
explicitly exclude an actual spoken result.

Restored the previous local controller, overlay, eight session tests, runtime
microphone permission, recognition-service visibility, lifecycle cleanup,
cancellation and focus return. Final recognition updates the editable field
and submits through the existing typed-search path. Kept upstream’s in-flight
request-search deduplication. Updated the inventory and technical checklist so
future syncs preserve this temporary fallback and its re-evaluation condition.

Validation: all 1,434 TV host tests passed with zero failures, errors or skips,
including the eight restored voice-session tests. Full TV release lint and
release Kotlin compilation passed with existing warnings/baselines. Source
checks verified the controller, overlay and session tests are exact restorations
and the previous search integration differs only by retained request-search
deduplication. Actual speech capture/result return on the affected Shield still
requires device testing.

## 2026-11-06 — rebuild the fork as a clean patch series

Prepared local branch `rebase-2026-11-06` on upstream
`f13a2652a12ad59680708aa50b7d19aed23a67e8` at Matt's request. The fork's
53 carried-forward commits follow upstream directly, with no merge commits in
that range. This changelog record is a separate documentation commit.
The previous local `main` (`78a9ec27d`) and `rebase-2026-11-05` (`f91330a2f`)
remain intact, with recovery refs under `refs/codex/rebase/`.

Upstream changes since the previous rebase base (`ee178d887`):

- `095079433` — fix(phone): tell TalkBack which For You list pill is chosen (#834)
- `f13a2652a` — fix(phone): stop sort and filter pills looking switched on (#835)

Rebuilt the search-keyboard integration around upstream's native voice helper
from the start. The retired local voice controller, overlay and recording
permission never enter this new fork series. Folded the later search-clearing
and focus corrections into their corresponding keyboard commits, omitted a
canceling test-fix/revert pair, and retained the maintained changelog instead of
the old experiment entry. Up Next Trigger and PR #2's timing/dismissal fixes
remain. Requests follows upstream, without the disowned top-bar override.
The reviewed manifest retains 14 active IDs and marks `tv-voice` superseded.

Compared the final source with an independent integration of the previously
validated rebase and the pinned upstream commit. Only comments and the order of
independent shell state declarations differ; there is no additional runtime
change. Scanned every commit in the new fork range for the retired voice code
and permission, and verified the voice helper matches upstream byte for byte.
Existing settings keys, profile/account isolation and release workflows remain.

Validation on source commit `cf20fb92f`: all 12,484 configured unit-test
executions passed (shared: 4,700; Android shared: 4,464; phone: 1,894; TV: 1,426),
with zero failures, errors or skips. The shared and phone totals include debug
and release variants; TV's configured host tests use the debug variant. The 39
release-script tests and full phone/TV release lint passed with existing
warnings and baselines. Signed phone and TV release builds
passed persistent-signature, application-ID, version and non-debuggable checks.
APK/R8 mapping pairs, metadata and checksums are archived under
`dist/local-2026-11-06.185758/`; all packaged checksums were rechecked. The TV APK
retains the system-recognition activity query. Its upstream voice path delegates
audio capture to the recognizer and has no app-owned recording permission.
The APK metadata records the clean tested source commit; this subsequent commit
changes only the changelog. A local old-to-new commit map and the validation log
are stored beside the APKs.

Neither `main` nor any remote branch was updated. No release was published.
Device playback, native voice capture/focus return and visual checks were not
performed.

## 2026-11-06 — adopt upstream TV voice search

Retired the fork's `tv-voice` implementation at Matt's request after reviewing
upstream PR #830 (`4fb8f1b59`). The TV mic now launches upstream's system
recognizer and submits its result through the shared typed/spoken search path.
The mic is hidden without a recognizer, an unavailable launch reports a message,
and the row follows upstream's field/mic/clear order. App/System keyboard modes,
automatic opening preferences, physical typing and keyboard focus gates remain.

Copied upstream's `TvVoiceSearch.kt` helper without modification. Removed the
fork's local recording controller, custom voice overlay, recording permission,
recognition-service query and their eight dedicated controller tests. Retained
the upstream recognition-activity query and in-flight request-search deduplication.
This adopts PR #830's voice behavior without integrating unrelated newer upstream
commits into main.

Moved `tv-voice` to the manifest's superseded section and updated the technical
checklist so future integrations do not resurrect the old implementation.
Clarified `input-scope`: it is storage for App/System keyboard choice, automatic
keyboard opening and remote long-press mappings per server/login/profile, not
a standalone setting. Its implementation and eight existing migration/isolation
tests were not changed. The inventory introduction records ongoing user review
without claiming personal authorship of every historical change.

Validation on local main: all 1,424 remaining TV host tests passed with zero failures, errors
or skips, including all eight preference-scope tests. Full TV release lint and
release Kotlin compilation passed with existing warnings/baselines. Verified
the helper matches upstream byte for byte and that input preference source/test
files are unchanged. The legacy controller-only tests were removed with their
implementation. Skill validation and manifest/whitespace checks passed.
Device voice capture, native recognizer focus return and visual verification
remain unperformed. Nothing was pushed or published.

## 2026-11-06 — record intentional fork differences

Added `FORK_DIFFERENCES.md` as the maintained baseline authorized by Matt in
this chat. Its stable IDs describe required behavior and implementation
references; they do not attribute every historical change to him. Requests has
no fork override and follows upstream as it evolves.

Updated the upstream-merge skill to use active manifest IDs and the current
request when resolving conflicts. Removed its rule that froze AGENTS.md product
exposure descriptions as fork requirements. Repository instructions still
apply; inherited descriptions, old merge logs and passing tests are not proof
that a difference was intentional. Meaningful decisions now identify upstream
behavior, the applicable fork ID or authorization, and the resulting choice.
Unexplained product differences are surfaced rather than silently preserved,
discarded or added to the approved list.

The technical checklist now maps those IDs to source, migrations and tests,
including Up Next and account-isolated input preferences. Normal fork-development
instructions and the feature overview point to the manifest. New intentional
work updates it in the same change; verified upstream replacements are recorded
as superseded so old workarounds are not reintroduced. Merge mode, schedules,
models, push boundaries, signing and publication permissions are unchanged.

Validation: skill frontmatter validation, relative-link/feature-ID checks,
whitespace review, and review against the Requests move, a known-feature API
adaptation, an unexplained merge-only difference and an upstream replacement.
These are instruction/source checks, not an independent agent execution test.
The Requests code in `03e76a364` passed all 1,432 TV host tests, full TV release
lint, release compilation and the signed TV build. The APK passed persistent
signature, app-ID, version and non-debuggable verification; matching R8 mapping
and checksums were archived. The existing untracked release helper was left
unchanged. Phone/shared source did not change; device playback and visual
captures remain unverified. Nothing was pushed or published.

## 2026-11-06 — follow upstream Requests navigation

Restored upstream's server-gated TV Requests entry in the profile menu. Removed
its fork-only top-bar destination and corresponding routing/focus and test
expectations. Updated the inherited AGENTS.md navigation description with the
code; no newer upstream commits were integrated in this correction.

The old top-bar preservation in merge `020174afd` and subsequent records was
an assistant interpretation, not a customization requested by Matt. Older log
entries describe what happened, not authorization to restore that override.
Requests navigation follows upstream as it evolves.

Validation: all 1,432 TV host tests passed with zero failures, errors or skips;
full TV release lint and release Kotlin compilation passed. Requests screens,
roots and navigation guards match the included upstream snapshot `fee491916`.
The repository disables TV release host tests, so its configured debug host
suite was used. Device playback and visual captures were not performed. Nothing
was pushed or published.

## 2026-11-05 — local automation progress notifications

Added local start, merge-preparation/validation, and validated merge/GitHub
handoff updates to the existing SiloraOSS webhook in #📋・mod-log. Unchanged or
stopped runs receive a closing status. The handoff includes the actual summary,
SHAs, checks, recovery ref, changelog link at the merge commit, and verified
GitHub run link/state. Local updates suppress mentions; existing GitHub
notifications continue handling remote build and release results.

The sender records private intents, saves confirmed message IDs before
read-back, skips delivered events, and reconciles interrupted sends without
posting again. Its installed runtime and private webhook configuration live
outside the checkout so it works with development branches and merge worktrees.
The automation's schedule, model, merge/release checks, and push safeguards are
preserved. GitHub workflows and shared notification scripts are unchanged.

Validation: 17 focused local-notification tests, 33 automation-release tests,
and 10 existing notification tests passed, along with supply-chain and
whitespace checks. A read-only live webhook check and a handoff preview using
real GitHub run metadata passed. No Android application code changed.

## 2026-11-05 13:05 PST — upstream fee491916

Merged upstream `main` into Silora `main` to keep TV focus on a Continue Watching
show after its episode advances, and align phone headers and first content rows.

### Merge inputs

- Branch: `main`
- Fork before: `73c46323fe70f4e2f65b286bdb5ac5de2b9daca4`
- Upstream tip: `fee491916c593ee36d2a6d6c655d109622e6e622`
- Recovery ref: `refs/codex/upstream-merge/20261105T205830Z`

### Upstream commits added

- `1c9f4bfd1` fix(tv): return focus to a Continue Watching show after its episode advances (#814)
- `fee491916` fix(phone): give every tab the same header and first-row position (#815)

### Silora merge decisions

- The merge had no conflicts. Reviewed the automatically merged phone header;
  retained Silora's wordmark width, aspect ratio, artwork and accessibility
  label while adopting upstream's shared header geometry, remote-control
  actions, scroll-responsive glass, and content alignment.
- Adopted upstream's TV focus restoration keyed by show identity for progress
  rows. Episode identities remain distinct when a show has multiple cards and
  outside progress rows. Included upstream's two regression tests.
- Compared all 207 fork paths outside the incoming changes with the pre-merge
  tree, including deletions; they match. Retained HEVC defaults and recovery,
  Night Mode tuning and controls, independent display matching, Extra small
  subtitles, account/profile-scoped keyboard and remote preferences, voice
  input, embedded trailers, AI subtitle fixes, and the three Up Next modes.
- Preserved current navigation and product exposure, Requests and experimental
  Watch Party gates, Silora application IDs and Kotlin namespaces, README
  separation, and release/signing/dependency-verification tooling. No fork
  workaround was superseded.

### Validation

- Passed the mandatory signed release build for both apps using JDK 21, the
  existing Android SDK, and the persistent release key:
  `./build.sh --release=both --tests --lint --signing-dir /home/albinoman887/Workspace/SigmaStreams/Silo/source/silora-signing`.
- Passed the full configured Gradle unit-test suite: 12,450 executions with
  zero failures, errors, or skips, including 1,432 TV debug JVM tests. TV's
  release unit-test task remains disabled by the existing configuration.
- Passed full phone and TV release lint using existing baselines; existing
  warnings, baseline notices, and Gradle deprecations remain. Both R8-optimized
  APKs passed persistent-signature, application-ID, version, and non-debuggable
  verification. Packaged matching R8 mappings and verified all output checksums.
- Passed 31 fork-release tests, eight local-build tests, 33 automation-release
  tests, the build supply-chain check, whitespace checks, and inspection for
  unmerged entries. Reviewed the staged result against both pinned inputs and
  verified the 207 unaffected fork paths against the pre-merge tree.
- Used a temporary local 12 GiB Gradle heap for the existing parallel release
  validation. No persistent build configuration or checks were weakened.
- No device, UI, or hardware playback validation was performed. Build outputs,
  private receipts, signing material, and logs remain outside the source commit.
  Release publication is a separate authorized exact-commit workflow step.

## 2026-11-04 22:40 PST — upstream 670f5ba0a

Merged upstream `main` into Silora `main`, adding multiple saved accounts per
server, phone-assisted TV sign-in across servers/accounts, combined poster
badges, and playback, navigation, and focus fixes.

### Merge inputs

- Branch: `main`
- Fork before: `f1c84b8bc4e352fbccf88c1e927011378320ddf3`
- Upstream tip: `670f5ba0ae2ed8065bb3d6a23c7317a09b5bc666`
- Recovery ref: `refs/codex/upstream-merge/20261105T062318Z`

### Upstream commits added

- `1df14386a` fix(tv): keep the marquee detail line whole while it fades (#805)
- `45a869871` fix(tv): keep focus on a Continue Watching card when its next episode replaces it (#803)
- `623bb3513` feat(accounts): sign in to more than one account per server (#795)
- `e727c9af5` fix(tv): move the series page to the next episode after you finish one (#802)
- `61e4d3254` fix(playback): allow audio passthrough on Android 9 and older HDMI devices (#778)
- `64208824c` test(tv): wait for view model work to stop before resetting Main (#806)
- `969c0a812` fix(tv): make the remote's Stop key leave the player (#809)
- `d65b27cec` refactor(phone): show Watchlist, Favorites and History in the library gr (#808)
- `2b2af2794` feat(pairing): sign an Android TV in to several servers and accounts from a phone (#810)
- `44d313854` fix(phone): move the cast bar beside the minimized Home control (#811)
- `e57fbf3f6` fix(tv): stop Home flashing the text title before its logo (#813)
- `670f5ba0a` feat(overlays): draw the combined resolution, HDR and audio poster badge (#816)

### Silora merge decisions

- Resolved the settings import conflict by keeping Night Mode parameters and
  upstream's account-scoped keys. The existing local playback preferences now
  use upstream's account-specific settings files and adoption lifecycle.
- Resolved the TV profile-menu conflict by adopting Change Account while
  retaining the existing server-enabled Requests top-bar tab. Preserved the
  existing Watch Party Experimental toggle and server capability checks;
  this merge adds no new toggle or product-exposure rule.
- Adapted Silora's TV keyboard and remote preferences to isolate accounts
  sharing a server/profile ID. Only the account adopting pre-account data
  inherits saved choices; account removal purges its tokenized keys. Kept
  System keyboard and automatic opening off as defaults, saved choices,
  reactive switching, and the older TV-wide preference migration.
- Updated fork settings test factories for upstream's account/profile
  signature. Added two focused TV tests covering account isolation, observers,
  legacy adoption, and reset isolation. Kept Silora's accessibility label on
  the new TV Change Account wordmark.
- Reviewed all overlapping fork paths. Before this changelog entry, 178
  unaffected fork paths matched the pre-merge tree; four fork-only TV
  preference/test paths were adapted for account isolation. Retained HEVC
  defaults, integer-key compatibility and read recovery; Night Mode tuning,
  PCM restrictions and controls; independent display matching; Extra small
  subtitles; keyboard, voice and remote shortcuts; embedded trailers; AI
  subtitle fixes; and the three Up Next modes with their saved keys.
- Preserved Silora branding/application IDs, Kotlin namespaces, current phone
  and TV product exposure, release/signing tooling, dependency verification,
  and the upstream-aligned README plus its Silora link. No fork workaround
  was superseded by these upstream changes.
- Accepted upstream's HDMI passthrough evidence handling for older Android,
  multi-account/pairing changes, combined poster badge, personal-list grid and
  cast-bar updates, and TV focus, Stop-key, series-return, and title-logo fixes.

### Validation

- Passed the mandatory signed build for both apps with JDK 21 and the existing
  Android SDK and persistent signing key:
  `./build.sh --release=both --tests --lint --signing-dir /home/albinoman887/Workspace/SigmaStreams/Silo/source/silora-signing`.
- Passed all configured Gradle unit tests: 12,448 executions, zero failures,
  errors, or skips, including 1,430 TV debug JVM tests and all eight TV input
  preference tests. The project's TV release unit-test task remains disabled.
- Passed full phone and TV release lint, both R8-optimized release APK builds,
  signature/application-ID/version/non-debuggable verification, and packaging
  of both matching R8 mappings with checksums.
- Passed 31 fork-release tests, eight local-build tests, 33 automation-release
  tests, the build supply-chain check, staged whitespace checks, and inspection
  for unmerged entries. Reviewed the staged result against both pinned inputs.
- The first build attempt could not write the sandboxed Gradle cache. With
  cache access, a new test assertion incorrectly matched the migration marker;
  it was corrected to check account switching/reset behavior. Parallel lint
  also exhausted the existing 4 GiB Gradle heap. The complete retry passed
  with a temporary local 12 GiB heap; no persistent build configuration,
  dependency checks, signing, tests, lint, or R8 settings were weakened.
- No device, UI, or hardware playback validation was performed. Local build
  outputs and logs remain outside the source commit. Release publication is
  handled separately by the authorized exact-commit workflow.

## 2026-11-04 — separate automation release workflow

Added **Silora Automation Release** beside the existing daily and manual
workflows and switched the saved local automation to its exact-commit handoff.
The original workflows and shared scripts are unchanged. The local schedule,
model, project paths, execution setting, merge skill, pre-push release
validation, push safeguards, and no-change policy are preserved.

The new workflow accepts the validated source and included upstream SHAs,
defaults to validation without publication, and requires tests, full release
lint, and signed phone/TV builds. APK verification checks the persistent signing
certificate, application ID, version, and non-debuggable flag. Publishing
reserves the source tag, downloads and verifies uploaded files, and confirms
the completed public release. Private dispatch receipts, reviewed retries,
build/publication receipts, and retained R8 mappings support recovery.

A separate notification listener uses the existing verified Discord transport.
GitHub validation exposed a guard that compared the custom run name with the
workflow name; it now verifies workflow ID and path, with a regression test.
Rejected events stopped before Discord calls. Intent/delivery artifacts and
per-event concurrency suppress repeated events and stop uncertain sends for
inspection. Setup and rollback: `docs/automation-releases.md`.

Validation: all 102 release-tooling tests passed, along with Actionlint, the
build supply-chain check, Python compilation, and whitespace checks.
[GitHub run 41973021671](https://github.com/SiloraOSS/silora-android/actions/runs/41973021671)
passed on source `91dc233ba`: full Gradle tests, release lint, both signed APK
builds, APK verification, and artifact/result uploads. Downloaded checksums
passed, both R8 mappings are present, and the signing fingerprint matches the
local persistent release keystore. Start and completion notifications were
delivered and read back; repeated start events skipped the saved delivery.
The earlier [run 41971674039](https://github.com/SiloraOSS/silora-android/actions/runs/41971674039)
passed the same build checks. Both runs disabled publication and created no
release tag or public release. No device testing was performed. Publication
will first run after the next eligible, validated upstream merge.

## 2026-11-04 01:17 PST — upstream 64ae5f08c

Merged upstream `main` into Silora's `main`. Phone and TV calendars now show
DIGITAL and PHYSICAL movie-release badges. Library grids list individual movies
rather than grouping them into box sets; box sets remain available through
search, movie details, and their own pages. TV search gains a clear button.

### Merge inputs

- Branch: `main`
- Fork before: `020174afd275b4d3787f63afc28a6651729840ed`
- Upstream tip: `64ae5f08ccb3efa2f82ebce0d200be3bc01d9b83`
- Recovery ref: `refs/codex/upstream-merge/20261104T032000Z`

### Upstream commits added

- `6f3e36820` fix(box-sets): stop grouping box sets in library grids (#796)
- `548e3cb77` feat(tv): add a clear button to the search field (#798)
- `64ae5f08c` feat(calendar): show DIGITAL and PHYSICAL badges on movie releases (#722)

### Silora merge decisions

- Resolved the TV search conflict by integrating upstream's clear button with
  Silora's existing text/cursor state, app/system keyboard selection, voice
  button, physical typing, and focus handling. Right moves from the field to
  Clear when text exists, then to Voice; clearing returns focus to the field
  before removing Clear and opens the selected keyboard. System IME opening
  retains the existing wait for the editable field to compose.
- Reviewed the automatic merges in TV dependency injection and the main shell.
  Retained profile-scoped keyboard/remote stores and server-gated Requests in
  the top bar, plus For You and experimental Watch Party behavior. Adopted
  upstream's removal of box-set grouping parameters and library-grid callbacks.
- Compared all 195 fork-specific file entries outside the three overlapping
  paths with the pre-merge tree, including deletions; they were unchanged before
  this log update. Preserved HEVC setting keys/defaults and read recovery,
  Night Mode and tuning, independent TV resolution/frame-rate matching, Extra
  small subtitles, remote shortcuts, keyboard/voice preferences and migration,
  embedded trailers, AI subtitle fixes, and all three Up Next trigger choices
  with their existing persistence, timing, and controls.
- Preserved Silora branding/application IDs and Kotlin namespaces, fork release
  tooling, dependency verification, and the product exposure rules. The README
  still matches upstream plus its Silora link. No fork workaround was
  superseded by these incoming changes.

### Validation

- Passed 36 selected release unit tests: `CalendarBadgeLabelTest` (2),
  `BoxSetsV2Test` (3), `RoomCatalogCacheRepositoryTest` (13), and
  `LibrariesViewModelTest` (18), with no failures, errors, or skips.
- Passed phone and TV release Kotlin compilation, including shared and
  Android-shared compilation, and `:androidApp:lintRelease` plus
  `:androidTvApp:lintRelease`. Lint used the existing baselines and reported
  warnings/hints; compiler and Gradle deprecation warnings remain.
- Confirmed Gradle task availability. An initial invocation requested
  `:androidTvApp:testReleaseUnitTest`, which this repository disables because
  its Robolectric manifest dependency is debug-only; that invocation stopped
  before validation tasks ran. The corrected release validation passed. TV
  unit tests and the full test suites were not run.
- Passed `scripts/check-build-supply-chain.sh`, staged whitespace checks, and
  inspection for unmerged entries. Reviewed the staged changes against both
  pinned inputs and verified the remaining fork paths against the pre-merge
  diff.
- No device/UI or playback validation was performed. No APK was packaged or
  installed. Release metadata was unchanged, no release was published, and
  nothing was pushed.

## 2026-11-03 03:31 PST — upstream 65664d7ce

Merged upstream `main` into Silora's `main`, adding temporary-password
setup on phone, actionable temporary-password guidance on TV, and audio-output
reporting based on the actual Media3 sink. The TV profile menu no longer
duplicates Watchlist and Favorites, which remain under For You.

### Merge inputs

- Fork before (after consolidation): `eb13f70331f1ac9b69c1274d1edc3e4074158805`
- Original fork before: `09cbafbcfe94215e5e15f67a176a5ba48460fd6f`
- Upstream tip: `65664d7ce4a2c37e402a2c4094186f15a5fccacd`
- Recovery ref: `refs/codex/upstream-merge/20261103T112657Z`
- History-rewrite recovery ref: `refs/codex/up-next-rewrite/20261104T020612Z`

This merge was replayed locally after the original Up Next change and its
three-choice revision were consolidated into the feature commit above. The
original merge, `80ddbc4ec08781e1c0501737c360e4f07bb22223`, remains recoverable.
The upstream changes and prior conflict resolutions were retained.

### Upstream commits added

- `4c0a2178c` fix(tv): move Requests from the top bar to the profile menu (#782)
- `136de70ad` fix(tv): drop Watchlist and Favorites from the profile menu (#786)
- `5b51528a8` test(tv): hold the replan answer until the subtitle switch mounts (#788)
- `7e4580db1` fix(player): show the audio output the sink chose, not the plan's guess (#789)
- `65664d7ce` fix(auth): let an account with a temporary password choose its own (#790)

### Silora merge decisions

- Kept Requests as a server-gated TV top-bar tab plus search, following the
  current `AGENTS.md` product rules. Adapted #782 rather than moving Requests
  into the profile menu; retained its existing focus handoff, route handling,
  capability gating, and navigation guards. Adopted #786's removal of duplicate
  Watchlist/Favorites profile-menu entries. Updated the root-destination comment
  to describe the resulting navigation.
- Resolved four player conflicts by retaining Night Mode's state, controls, and
  forced PCM processing while adopting upstream's observed audio-output path.
  Actual PCM/passthrough/offload determines audio-delay availability; before the
  first AudioTrack exists, Night Mode supplies the PCM fallback. No fork feature
  was replaced by an upstream equivalent.
- Retained the Up Next feature as one consolidated commit. Phone and TV now
  offer Credits markers, Time remaining, and Disabled, with descriptions of each
  choice and the timer's marker-free fallback. Preserved the local mode key,
  profile/server/device scope, startup guard, normal end screen, skip controls,
  autoplay, and manual TV action. The timing picker and marker-free timer fallback
  are available on both clients.
- Reviewed all 11 overlapping fork files. Retained HEVC level-limit settings
  and read recovery, Night Mode tuning and focus behavior, independent TV
  resolution/frame-rate matching, Extra small subtitles, remote shortcuts,
  keyboard/voice preferences and focus handling, embedded trailers, and AI
  subtitle fixes. Preserved Silora branding/application IDs, release tooling,
  dependency verification, and experimental/product gates.
- Applied Silora app names to the new password-flow messages. Kept `README.md`
  aligned with upstream plus its Silora link; fork navigation remains documented
  in `README-SILORA.md`.

### Validation

- Passed `:androidApp:compileReleaseKotlinAndroid` and
  `:androidTvApp:compileReleaseKotlinAndroid`, including shared and Android-shared
  release compilation. Gradle reported deprecation warnings.
- Passed `scripts/check-build-supply-chain.sh`, staged whitespace checks, and
  inspection for unmerged entries. Compared the staged result with both pinned
  merge inputs and reviewed TV navigation and Up Next wiring.
- The original merge's source audit checked 180 fork file entries outside
  upstream's changed paths, including deleted files. During consolidation,
  compared the replay's source diff with the original merge and verified that
  only the Up Next revision and this log changed in the resulting tree.
- The original merge skipped tests and lint. The consolidated Up Next revision
  passed seven trigger-policy tests, one persistence/scope/reset test, and both
  phone and TV release compilation. Lint and full test suites were not run.
- No device playback, password-flow, or UI validation was performed for these
  revisions. No APK was packaged or installed, no release metadata was bumped,
  and nothing was pushed.

## 2026-11-01 17:41 PST — upstream 51d4f9013

Merged upstream `main` into Silora's `main`, adding movie box sets, embedded
subtitle playback and switching, subtitle availability fixes, TV Watch Next
query improvements, and TV focus styling.

- Fork before: `cccf091083c63247a5bdd1d604a609c31b0b6bd2`
- Upstream tip: `51d4f90130ade94ca51e7a9fc8df440036756fff`
- Existing merge commit: `10206284d783415c405d763acc093d4f60de85cf`

### Upstream commits added

- `67e6e224a` feat(box-sets): browse, open and search movie box sets (#646)
- `6b61cc55f` fix(tv): read only the Watch Next rows instead of all of Home (#775)
- `d4ff20673` feat(playback): draw embedded subtitles from the stream and switch them without a remount (#770)
- `461240c6d` fix(player): don't offer or auto-pick subtitles the server can't show (#776)
- `51d4f9013` fix(tv): outline focused cards in white with a small lift, as on tvOS (#781)

### Historical record limits

This initial entry was reconstructed from the existing merge commit and its
parents. Conflict decisions and validation results were not recorded in its
commit message and are not asserted here. Earlier merges have not been
backfilled.
