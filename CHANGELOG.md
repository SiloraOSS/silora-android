# Changelog — Silora Android

Silora tracks [Silo Android](https://github.com/Silo-Server/silo-android).
This log records upstream merges, the commits they introduce, Silora merge
decisions, and validation results. Entries appear newest first.

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
