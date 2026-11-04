# Changelog — Silora Android

Silora tracks [Silo Android](https://github.com/Silo-Server/silo-android).
This log records upstream merges, the commits they introduce, Silora merge
decisions, and validation results. Entries appear newest first.

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
