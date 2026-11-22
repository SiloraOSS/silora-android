# Changelog — Silora Android

Silora tracks [Silo Android](https://github.com/Silo-Server/silo-android).
This log records upstream merges, the commits they introduce, Silora merge
decisions, and validation results. Entries appear newest first.

## 2026-11-21 — Release notifications

Local APK releases now report the build, interactive approval wait, upload
and verified publication in #mod-log. The merge-and-release skill posts a
release-notes embed in #silo-daily-changelog after it verifies a new GitHub
release. Its single list identifies Silora and Silo upstream changes and links
to that build's APKs and full release notes.
After the SigmaUtils notification view is deployed, the same post pings the
opt-in Silora Updates role and includes buttons to subscribe or unsubscribe.

## 2026-11-21 15:17 PST — upstream 9f5de3107

**Merge inputs** — `main` at `938a94a1d87b5dc7a2939ea40be42c313d01d410`
and official upstream `9f5de310783ad1b542c9984b82449c46158942a4`. The upstream
translation sync keeps `/activate` in left-to-right order in the Arabic and
Hebrew TV sign-in instructions. Recovery ref
`refs/codex/upstream-merge/20261121T231745Z`.

**Upstream commits added**

- `9f5de3107` chore(i18n): sync translations 2026.11.21-f78acfd (#1018)

**Silora merge decisions**

The translation catalog and metadata merged without conflicts. The generated
Silora brand resources still match the catalog. The v51 Admin contract and
the temporary Android TV Watch History pagination fix are in the first parent.

**Validation**

The staged translation files matched upstream. The generated brand resource,
supply-chain, and staged diff checks passed. The release review records the
test results, full lint, and signed APKs and mappings from this exact commit.

## 2026-11-21 — Edrock v51 Admin API spec

Updated the bundled Admin API spec for phone and TV from edrock build 50
(`904a1d13b`). The console descriptions now explain why deployment-managed
AI providers reject edits and deletion. The spec also documents
deployment-managed transcode nodes, the server-wide Requests switch, episode
details in task backlog samples, and missing collection items in restore previews.

The asset matches the official silo-server contract at `ae610da1` and lists
475 operations. The new request image route is filtered by the generic console,
which still exposes 459 callable operations. The source pins and recovery ref
are in the [Edrock tracking record](docs/maintenance/edrock-tracking.md).

All 40 Admin release unit tests passed. Phone and TV release compilation and
lint passed with the existing lint baselines.

## 2026-11-20 15:50 PST — upstream c6dfd52a6

**Merge inputs** — `main` at `11bff4ef1b34886f9aba0ba65540b0d73eac6f4c` and official upstream
`c6dfd52a631ccc158c1f4580756b6d4148e9e985`. The TV hero backdrop blends artwork into
the page gradient with less banding on dark images. Recovery ref
`refs/codex/upstream-merge/20261120T235009Z`.

**Upstream commits added**

- `c6dfd52a6` fix(tv): stop the hero backdrop banding on dark art (#1015)

**Silora merge decisions**

The one changed source file matched upstream after the conflict-free merge.
The prepared merge below remains its first parent, with Silora's Home
removal, Admin console, and branded locale resources.

**Validation**

The staged source matched the upstream blob. Generated brand resources,
supply-chain checks and the staged diff passed. The execution review records
the full test and lint results, signed APKs, and matching mappings for this
exact commit. Device testing is outside this run.

## 2026-11-20 11:28 PST — upstream 63c898084

**Merge inputs** — `main` at `451bc2edc4eb874080c9bf5037d5905ac5573738` and official upstream
`63c8980846923db8bc96481bcf6bf000760286f7`. Home refreshes watch state on return and retries
transient section reads. Device sign-in approval now uses device-neutral copy
and drops the legacy match-word footnotes. Upstream adds 17 locales and
recognizes Hebrew and Indonesian under the codes devices report.
Recovery ref `refs/codex/upstream-merge/20261120T192747Z`.

**Upstream commits added**

- `1b4732d90` fix(home): refresh watch state when Android Home returns (#1009)
- `63c898084` fix(pairing): make device sign-in approval copy device-neutral and remove legacy match-word footnotes (#1014)

**Silora merge decisions**

- Git merged the three overlapping files without conflicts. The phone starts
  Home hidden and refreshes on resume. TV still defers pending refreshes until
  focus is restored. The shared Home model retains profile-scoped removal
  notifications from Explore all and uses the upstream refresh path after
  a successful dismissal (`home-explore-removal`).
- Regenerated Silora brand strings for all 17 added locales (`identity`).
  The generated Hebrew brand strings inherit the catalog's CLDR plural
  forms. The upstream lint exception now covers that generated file too.
- Retained the v49 Admin console and older-server episode labels, app IDs,
  and existing release tooling. The pairing and locale changes follow
  upstream behavior.

**Validation**

The fork overlap, upstream commit list, generated strings, supply-chain checks
and staged diff were reviewed before the merge commit. The exact-commit local
release build records the signed APKs and mappings. The execution review records
full tests, release lint and artifact results. Device testing is outside this run.

## 2026-11-20 — Edrock v49 Admin console update

Admin Now Playing shows the series name, episode number and episode title on
phone and TV with both generations of the server response. The app accepts
`series_title` alongside the legacy `series_name` and uses `media_title` when
the server omits `episode_name`.

Refreshed the bundled server Admin contract to 474 operations. The generic
console picks up plugin capabilities and provisioning status, plus updated
fields for plugin settings, catalog, playback history and downloads. Silora's
existing default-off Admin entry and account/profile access checks apply to
these operations.

Matt requested the Admin update from Edrock v49
(`f640a73de80299c08ca3b1ff53d5f6e013dbcb30`).
[Source commits, contract provenance and recovery ref](docs/maintenance/edrock-tracking.md#imported-v49-admin-console-november-20-2026)

All 40 focused release Admin tests passed. Phone and TV release Kotlin
compilation and lint passed with the existing baselines. Device and
live-server validation remain pending.

Other clients — Apple and Roku are outside the scope of Silora's Admin
console. Web Admin already reads the revised session fields from the server.
[Web Admin activity](https://github.com/Silo-Server/silo-server/blob/975bbf41b33ccdce5bdc1d14daa9742748042be9/web/src/pages/AdminActivity.tsx)

## 2026-11-19 12:04 PST — upstream 61a4bf1ea

**Merge inputs** — `main` at `29c9b3c8aa10634605863f2fa9fcbf733354bde9` and upstream
`61a4bf1ea2d0f5c1cd9fcd41fd12c40e28fe9d07`. Direct play gets a fresh retry window after sustained
progress, detail pages prefer the standard edition when there is no viewing
history to guide the choice, TV menu tabs respond to Down after Back closes
their menus, and the player hides online subtitle search when the server
reports no provider. Diagnostics record Media3 player and load error codes.
Recovery ref `refs/codex/upstream-merge/20261119T200412Z`.

**Upstream commits added**

- `bb59172f8` fix(player): renew direct play retries after sustained progress (#993)
- `e15879514` fix(detail): open the standard edition before comparing quality (#996)
- `ed99dff24` docs: send siloserver.org docs changes to a docs issue (#1008)
- `5d90cf32f` fix(tv): open top bar menus on Down every time, and sooner (#970)
- `2ee32f414` fix(player): hide subtitle search when the server has no provider (#608)
- `61a4bf1ea` fix(diagnostics): record Media3 player and load error codes (#635)

**Silora merge decisions**

- The merge applied without conflicts. All six overlapping files retained
  their fork edits. The new direct-play retry tracking runs alongside the
  Night Mode audio path (`night-mode`).
- Phone and TV subtitle-provider checks coexist with the existing Next Up
  and credits behavior (`up-next-setting`, `up-next-fixes`,
  `credits-auto-skip`), the extra-small subtitle preset (`tv-subtitle-size`)
  and TV remote shortcuts (`tv-remote`). The fork AI subtitle patches
  remain retired.
- The TV top bar keeps the Silora wordmark (`identity`) and uses upstream
  menu focus handling. Device diagnostics continue to report raw HEVC levels
  (`hevc-levels`) alongside the new Media3 error fields.

**Validation**

- The full rerun passed 12,342 Gradle unit tests
  and 88 release-tooling tests. Phone and TV
  ran debug host suites because their release host tests are disabled. Shared,
  android-shared and libass ran both variants. The existing TV collection-ordering
  test failed once during the initial parallel build and passed on the rerun
  with four Gradle workers. The source and test were unchanged between attempts.
- Full phone and TV release lint and dependency checks passed. Both signed,
  R8-optimized APKs passed signature, application ID, version and non-debuggable
  checks. The archive includes matching mappings and verified checksums.
- Validation used staged tree `1fc8f03b3cb8194f22b82ac78758b9ffffaffd7a` before this changelog entry was added.
  Local APK metadata records the pre-merge HEAD. No devices or live server
  playback were tested.

## 2026-11-17 15:03 PST — upstream db87f835f

**Merge inputs:** `main`; fork before `746b15350f3b8a4b576a4eadd64400efaa587955`;
upstream `db87f835ff908dd31a8473c66408ddeeb2e926ba`. One upstream commit adds collection
rows to Home and library pages on phone and TV, with collection-card navigation,
media filtering, Home row controls and TV focus/return handling.
Recovery: `refs/codex/upstream-merge/20261117T225300Z`.

**Upstream commits added:**

- `db87f835f` feat(home): show rows of collections on Home and library pages (#979)

**Silora merge decisions:**

- The merge had no conflicts. Reviewed all five overlapping fork paths:
  phone routes/MainScreen, TV Home editor/shell and HomeViewModel. Their
  fork additions/removals match the pre-merge deltas exactly; all 302 other
  fork paths remain unchanged. Kept Night Mode/Admin navigation, Home Explore
  all removal's section-type routing, the TV keyboard/remote shell integration
  and Silora's Home-editor error label alongside the new collection routes.
- Accepted upstream's `hasCards` handling so collection rows remain visible
  when Home removes progress items. Kept the fork's profile-owned dismissal
  observer and Home refresh. Accepted the collection cards, section decoding,
  hydration, navigation, media filters and translation catalog as upstream.
- Preserved all 17 active differences. The two AI subtitle entries remain
  retired, and requests-first retains its enabled default and one-time
  migration. No edrock patches were imported and no new fork override was
  added. Other-client parity/evidence belongs to upstream PR #979; this merge
  opens no PR or separate parity change.

**Validation:**

- The mandatory `./build.sh --release=both --tests --lint --signing-dir
  /home/albinoman887/Workspace/SigmaStreams/Silo/source/silora-signing` passed
  under JDK 21 in 5m 16s: signed phone/TV release compilation and packaging,
  R8, full release lint, dependency/supply-chain checks, persistent signatures,
  `org.silora.app`, version/non-debuggable checks and matching mappings/checksums.
- 12,308 Gradle variant cases passed with zero failures/errors/skips:
  shared 2,553 per debug/release variant; android-shared 2,375 per variant;
  phone 1,010 debug; TV 1,438 debug; libass 2 per debug/release variant.
  Phone/TV release host tests remain disabled by the existing upstream test
  configuration; both release APKs and full release lint ran. Lint baselines
  are unchanged. The local release build ran 88 release-tooling tests, all
  passing.
- A fresh verified install of translation version `2026.11.17-fe23d4e` matched;
  derived Silora strings, fork-preservation, conflict and whitespace checks
  passed. Unrelated untracked files remain unchanged. No device/server,
  playback, focus or visual interaction test occurred.
- Tested pre-changelog tree `360079f4b6430c73bb03e6579182fa03d84f8433`
  differs from the final merge source only in this log entry. The precommit
  archive records the old HEAD and dirty tracked tree because it compiled
  the uncommitted merge. Exact-commit signed verification is required before
  push/handoff; its evidence stays in the private execution receipt.

## 2026-11-17 — enable requests-first search by default

**Show available to request first** now defaults on for phone and TV. At Matt's
request, a one-time migration also enables every previously saved disabled
choice on the installation, including inactive servers, accounts and profiles.
The migration runs when the shared preference store first opens after updating.
A viewer can turn the setting off afterward; later launches and updates retain
that new choice.

The migration changes only saved requests-first values and writes its marker
in the same SharedPreferences edit. Unset/new profiles use the enabled default;
missing account/profile identities cannot save unscoped choices. Existing
account-data cleanup removes signed-out accounts' choices while retaining the
installation migration marker. Server request gating, result relevance,
section rendering and TV focus behavior keep their existing implementation.

Updated `request-search-order` in the fork inventory and its technical checklist.
Added focused regression coverage for old disabled values across identities,
fresh defaults, later opt-outs, reactive updates, missing identities and
account cleanup. The upgrade test failed against the old implementation because
the saved disabled value remained false.

**Validation:** 47 tests passed with zero failures/errors/skips: seven new
migration/preference tests, 11 existing phone search tests and 29 existing TV
search tests. Phone/TV release Kotlin compilation and full release lint passed
in 1m 36s under JDK 21; lint baselines were unchanged. The regression first
failed against the old implementation on the saved false value, then passed
with the migration. Whitespace checks passed.

```sh
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew \
  :android-shared:testReleaseUnitTest --tests '*RequestSearchPreferencesTest' \
  :androidApp:testDebugUnitTest --tests '*ui.screens.search.*' \
  :androidTvApp:testDebugUnitTest --tests '*ui.screens.search.*' \
  :androidApp:compileReleaseKotlinAndroid :androidTvApp:compileReleaseKotlinAndroid \
  :androidApp:lintRelease :androidTvApp:lintRelease --parallel --console=plain
```

No APK was packaged or installed, and no device/server interaction test was
performed. This entry accompanies the requests-first default/migration commit.
No PR, release, version bump or notification was performed.

Apple, Roku and Web: this migration concerns the Android fork's local preference.
The existing feature's parity follow-ups remain deferred until a PR opens under
`AGENTS.md`. No server/API or other-client code changes are part of this work.

## 2026-11-17 — retire fork AI subtitle changes in favor of upstream

Retired `edrock-ai-subs-fixes` and `tv-live-ai-subtitles` at Matt's request to
remove fork-specific AI subtitle behavior as a troubleshooting variable.
Reported subtitle-related buffering remains unconfirmed. Restored AI APIs,
transcription planning, realtime handling and player subtitle paths to the
official upstream revision already incorporated into Silora:
`0867eacb14b5ee7a8c46cc2b22e52d1799e599a7`.

Removed streamed live cue rendering/decoding, job cooldown/adoption,
server-ordinal transcription override and TV completed-track/replan fallback.
Upstream AI generation, polling and finished-subtitle behavior remain.
The independent Extra small subtitle size and other active fork differences
remain unchanged. No new upstream commits were merged.

The pre-retirement implementation is preserved at
`a4742bb151958836a8f8e9b741a59c000792b461` and local recovery ref
`refs/codex/retired-ai-subtitles/2026-11-17`. Moved both IDs to the retired
manifest section and updated the checklist and edrock maintenance scope.
[History and restoration guidance](docs/maintenance/2026-11-17-ai-subtitles-retirement.md)
retain the original/v42 source references and previous behavior.

**Validation:** 1,278 affected subtitle/player tests passed with zero
failures/errors/skips: shared release 240, android-shared release 231, phone
debug 297 and TV debug 510. Phone/TV release Kotlin compilation and full release
lint passed in 2m 50s under JDK 21; lint baselines were unchanged. Eleven
restored feature files and both complete player-model subtitle suites match
the pinned upstream source exactly. Remaining fork deltas in all 22 changed
runtime/test/resource files are unchanged subsets of their pre-retirement
deltas. Retired helper/event/overlay/string references are absent.
Supply-chain, derived Silora strings and diff checks passed. The linked record
contains the exact Gradle command and restoration pointers. No APK packaging,
installation or device/server playback test occurred. This entry accompanies
the local retirement commit. No push, PR, release, version bump or notification
was performed. Apple, Roku and Web are
N/A: this removes Android fork additions and restores official Android paths.

## 2026-11-16 23:50 PST — upstream 0867eacb1

**Merge inputs:** `main`; fork before `45d115e18a71fcb240a48f5967b7739a31d00690`;
upstream `0867eacb14b5ee7a8c46cc2b22e52d1799e599a7`. Ten upstream commits update audiobook
playback and detail/player screens, offline listening sync, PCM capability
reports, card-to-detail transitions, the phone library dock and collapsing
navigation, long profile names, audiobook covers and the A–Z rail.
Recovery: `refs/codex/upstream-merge/20261117T073922Z`.

**Upstream commits added:**

- `34cef244f` fix(profiles): show long profile names in full on Who's watching (#965)
- `8d7b756be` fix(browse): drop the capsule behind the A–Z rail while dragging (#964)
- `62dc95035` feat(detail): open detail pages from the tapped card on phone and TV (#971)
- `08213c1f2` fix(audiobooks): send offline listening on downloaded books to the server (#961)
- `fe3d9c8af` fix(playback): advertise PCM so WAV audiobooks can play (#707)
- `efe85a83a` fix(cards): show audiobook covers square on phone cards (#974)
- `35f7839ce` feat(libraries): switch library pages from a dock above the nav bar on phones (#973)
- `c8846ee92` feat(audiobooks): keep audiobooks playing behind a mini player on phones (#976)
- `f66681ed8` feat(audiobooks): match the iOS and tvOS detail and player screens (#975)
- `0867eacb1` feat(navigation): collapse the phone bottom bar on scroll like iOS (#977)

**Silora merge decisions:**

- Resolved the one `HeroActionStack` conflict by combining upstream's audiobook
  toggle labels, resume progress and connected-TV controls with Silora's Trailer
  action and narrow-phone overflow placement (`trailers`). Retained the TV
  Trailer action alongside upstream's audiobook hero changes.
- Reviewed all 13 overlapping paths. Their added/removed fork lines match the
  pre-merge deltas, and all 308 other fork paths remain unchanged. Preserved
  scoped Admin and Night Mode navigation, request-search/input preferences,
  Home Explore all removal, Night Mode audio binding, HEVC/raw diagnostics,
  live AI subtitles, Up Next/Skip Credits, identity and signed release tooling.
  The superseded voice helpers remain upstream implementations. No active
  difference was retired and no new product override was introduced.
- Accepted upstream's audiobook playback/session ownership, offline-progress
  delivery, PCM capability report and phone navigation behavior. Other clients'
  implementation and evidence remain owned by those upstream changes; this
  fork merge creates no PR or separate parity change.

**Validation:**

- The required `./build.sh --release=both --tests --lint --signing-dir
  /home/albinoman887/Workspace/SigmaStreams/Silo/source/silora-signing` passed
  with JDK 21: signed phone/TV release compilation, R8, full release lint,
  strict dependency/supply-chain checks, persistent signatures, `org.silora.app`
  identity, version/non-debuggable checks and matching mappings/checksums.
- 12,306 Gradle variant cases passed with zero failures, errors or skips:
  shared 2,556 per debug/release variant; android-shared 2,373 per variant;
  phone 1,009 debug; TV 1,435 debug; libass 2 per debug/release variant.
  Phone/TV release host tests remain disabled by upstream's existing
  debug-only Compose test-manifest configuration; both release APKs and full
  release lint ran. Lint reported no errors; baselines were unchanged.
- 149 release/automation/notification script tests passed. A fresh install of
  translation version `2026.11.16-a0142c1` matched, and derived Silora resources,
  conflict/diff checks and fork-preservation audit passed.
- The sandbox's read-only Gradle cache lock was resolved by granting host-cache
  access and rerunning the unchanged required command. Precommit tested tree
  `0d9aabf6d37b59f3613979328a2965d58ee2f31d` differs from this final source only in
  `CHANGELOG.md`. Its archive records the old HEAD and dirty tracked tree
  because it compiled the uncommitted merge. Exact-commit signed verification
  is required before push/handoff; its evidence stays in the private execution
  receipt. No device, server, playback, focus or voice test was performed.

## 2026-11-16 — retain hardware HEVC levels in diagnostics

Diagnostic reports now retain the decoder's real HEVC levels even when
**Ignore HEVC Level Limits** is on. Previously, diagnostics used the adjusted
playback probe and reported an empty levels list. Matt approved importing this
maintenance recommendation from the read-only edrock review.

Adapted only the raw-probe/diagnostic subset of edrock
`c8cfcfc538e1684e2c947bc35a9d2e3c1e599e4b` from published v42
`c934aaf7ac0aef17881070c0a54ab4cdc56add36`. Silora input:
`6c82bcc71a216a9227e3b5901e9bab0862c7497a`; recovery:
`refs/codex/edrock-hevc-diagnostics/20261117T073123Z`. Reused the existing
hardware cache for diagnostics; playback/download reports still apply Silora's
switch. Its default, persistence, other decoder bounds and settings-read
recovery remain unchanged. No numeric picker, decoder-level settings label,
microphone prompt or newer official upstream commits were imported.

Updated the `hevc-levels` manifest/checklist entry, binder documentation and
edrock tracking record. Added a focused Robolectric regression covering actual
hardware/diagnostic reads with the switch on/off/on, other decoder bounds and
playback evidence after diagnostic reads. It failed against the old code
because diagnostics lost the hardware's `[153]` level.

Validation passed under JDK 21: 168 affected android-shared release tests, zero
failures/errors/skips, phone/TV release Kotlin compilation and full release
lint with unchanged baselines. The combined command completed in 1m 58s;
`docs/maintenance/edrock-tracking.md` records the exact command and pinned
inputs. Supply-chain, derived Silora resource, runtime-scope and diff checks
passed. No APK was packaged or installed and no device/server test occurred.
Other clients: Apple, Roku and Web are N/A because this is Android MediaCodec
diagnostics for Silora's device-local switch. No push, PR or release was requested.

## 2026-11-16 22:45 PST — optional edrock maintenance skill

Created `silora-edrock-sync` at Matt's request, with read-only review and
requested-import modes. It tracks maintenance of adopted edrock features and
reports occasional new features separately for Matt to choose. The default
source is the latest published release branch; an explicitly named branch or
commit takes precedence. Validated imports create local commits unless Matt
requests otherwise. Pushes and releases retain their separate requested scope.

Silora's documented requirements take priority, including Ignore HEVC Level
Limits over edrock's numeric picker, Admin access/settings and identity
isolation, and the current upstream voice-helper policy. The helper verifies
full source pins, compares actual patches across rebased release branches,
adapts only requested changes and records coverage per feature.

Seeded `docs/maintenance/edrock-tracking.md` from the completed November 15 v42
review and verified import commits, including skipped/deferred candidates.
The manifest remains the authority for intended behavior. Added a pointer from
the upstream-merge skill explicitly stating that an upstream merge or release
does not require an edrock review or sync. Existing automations are unchanged.

Both skills passed metadata validation. Relative-link checks, all 12 full
commit references, the four recorded Silora destinations and the v42 review
count passed verification against local Git history. `git diff --check`
passed. This adds maintenance instructions and history; it does not import
new application changes or check for a newer edrock release. No Android build
or device test was needed for these documentation-only changes.

## 2026-11-15 21:15 PST — remove Home progress cards from Explore all

Imported edrock's `d3d39cec329486198cf914e88bb0b608fa971926` at Matt's request.
Phone and TV now offer Remove from Continue Watching / Next Up on the full
Explore all pages opened from Home progress rows, including In Progress.
The card leaves immediately, episode removal drops the matching show, and a
server refusal restores the cards. Successful removal updates Home and
requests a refresh. Library rows and unrelated Home sections are unaffected.

Added the `home-explore-removal` preservation entry, technical merge pointers
and a Silora README feature row. Retained edrock's eligibility tests and added
transport-level checks for duplicate requests, failure rollback, reload/profile
ownership, whole-show dismissal, Home refresh and single-book removal.

Validation passed: 41 focused shared release tests (including seven new
removal integration cases), phone/TV release Kotlin compilation and release
lint, merge-skill validation and `git diff --check`. No APK was packaged or
installed, and TV focus/menu interaction has not been verified on a device.
No push or release was requested.

## 2026-11-15 20:03 PST — rename edrock AI subtitle fixes

Renamed the fork preservation ID `ai-subtitles` to `edrock-ai-subs-fixes` at
Matt's request. The entry groups fixes imported from edrock's fork for the
existing AI subtitle feature. Updated the manifest, merge-skill preservation
checklist and feature-ID references in older changelog prose. The manifest
records the former name so historical commits remain traceable.

This is a documentation-only rename. The `tv-live-ai-subtitles` ID, source
commit lists and runtime behavior are unchanged. Reference checks, skill
validation and `git diff --check` passed; no code tests were needed.

## 2026-11-15 19:47 PST — edrock v42 AI subtitle updates

Matt requested all current edrock updates for `edrock-ai-subs-fixes` and
`tv-live-ai-subtitles`, including phone live display. Imported the seven source
commits below from `edrock/feat/hevc-level-override-v42`, pinned to
`c934aaf7ac0aef17881070c0a54ab4cdc56add36`, into Silora `main` from
`6b4355aa8ed933451fd83f6be9fb12d7c743c646`. Recovery:
`refs/codex/edrock-subtitles/20261116T033632Z`.

**Source commits incorporated:**

- `e5d9b430e` feat(subtitles): show live AI subtitles on the phone, and transcribe untagged audio without translating
- `641a8993b` fix(subtitles): translate audio in languages the name table does not list
- `867431510` fix(subtitles): strip subtitle markup from live AI lines
- `0c541431a` fix(subtitles): let a subtitle pick or cancel dismiss the live AI lines
- `cc25ac25d` fix(subtitles): hide player subtitles only while live AI lines show
- `5dc3f0e10` fix(tv): add the finished AI track from the completed event
- `37874ceaf` fix(subtitles): adopt the lost AI job instead of creating a second one

**Integration decisions:**

- `tv-live-ai-subtitles` now covers phone and TV; its stable ID keeps its TV
  origin. Moved the old TV-only overlay into shared Android code and added
  phone realtime/model/screen integration. Live lines follow subtitle appearance,
  lift above controls, clean source markup and hide ordinary subtitles only
  while live cues cover the subtitle slot. Disposal restores ordinary subtitles
  for PiP/cast; waiting text occupies a small chip outside that slot.
- Imported dismissed-job tracking: another subtitle selection or Off hides the
  job's live lines and preserves that manual choice on completion. Cancel and
  terminal outcomes reject late batches; an explicitly reused job is restored.
  TV reports a failed cancel. Existing session/file/job and timeline guards stay.
- `edrock-ai-subs-fixes` retains the server audio-ordinal fix and missing-row fallback.
  The completed event now supplies its inventory row through the normal ready
  path before the five-second TV replan fallback is needed. Kept the completed
  live-track cleanup backstop.
- Replaced the old phone-only language helper with edrock's shared phone/TV
  `AiTranscribePlan`: aliases and region tags match; tagged languages outside
  the display-name table remain translatable; untagged audio uses transcription
  with the selected language as a hint. Translation-disabled servers keep plain
  transcription. Imported the planner's tests and existing phone test update.
- Uncertain-create recovery now lists jobs after the 60-second cooldown and
  adopts a matching active/completed result before another create. A failed list
  stays fenced. Kept the captured auth scope on job reads and wired the reads
  dependency into `SubtitleAiCreateV2Api` through Koin. Source helpers, decoder,
  recovery API and tests match pinned v42.
- Adapted only the three subtitle messages to `silora_` keys in
  `strings_silora.xml`. Excluded the unrelated HEVC strings sharing edrock's
  resource file. The shared overlay matches v42 apart from those resource names;
  TV Extra small remains supported by Silora's existing point-size mapping.
  No HEVC, Admin, trailer, release-tooling, Night Mode, remote-shortcut, Up Next
  or credits-setting implementation was changed. Reviewed overlapping player
  files and preserved the remaining fork behavior.
- Updated `FORK_DIFFERENCES.md`, the technical preservation checklist,
  `README-SILORA.md` and FEATURES. Both subtitle IDs remain active because these
  behaviors still differ from official upstream.
- Other clients: Apple already has live AI cue coordination/rendering in
  `LiveSubtitleCoordinator.swift` and `AetherSubtitleOverlay.swift` at
  `7bb8fe3d78947c20266442463c0fe72bc0f979e0`; Web has its live-event/finished-track
  path in `VideoPlayer.tsx`; Roku's current `docs/spec.md` defers subtitle
  translation/sync (section 3.3). The Android adapter/renderer fixes remain
  specific to this implementation. No cross-client PR or issue was opened.

**Validation:**

- Full command passed with JDK 21:
  `./gradlew test :androidApp:lintRelease :androidTvApp:lintRelease :androidApp:compileReleaseKotlinAndroid :androidTvApp:compileReleaseKotlinAndroid --parallel --console=plain`.
  The final run completed in 4m 10s. XML reports contain 12,229 variant cases,
  zero failures/errors/skips: shared 2,535 each debug/release; android-shared
  2,369 each debug/release; phone 982 debug host; TV 1,435 debug host; libass
  two each debug/release. Phone/TV release host tasks remain disabled.
- Both release apps compiled and passed full release lint with unchanged
  baselines. Supply-chain, derived Silora resource, pinned-source, scope,
  conflict-index and staged-diff checks passed. Imported tests cover cue
  markup/coverage/dismissal, transcription planning and lost-job matching;
  existing playback/settings/ownership suites ran as part of the full command.
- The initial full pass failed in the unchanged
  `CatalogLetterIndexViewModelTest` while setting `Dispatchers.Main` concurrently
  with a dispatcher read. The same full command passed on the final staged tree
  without modifying Browse code, that test or its assertions. An early staging
  issue around the removed overlay/resource file was corrected before this run.
- Runtime source tested: staged tree `b974957ba51d708a72623b376a0f5c8764211709`.
  Only the manifest, maintenance docs and this log changed afterward. No new APK,
  device/server AI job or real provider/quota operation was exercised. Changes
  were committed locally; nothing was pushed, released or messaged externally.

## 2026-11-15 19:21 PST — edrock v42 Admin and trailer updates

Imported the missing Admin console and trailer updates from
`edrock/feat/hevc-level-override-v42`, pinned to
`c934aaf7ac0aef17881070c0a54ab4cdc56add36`, into Silora `main` from
`bbac432277cedf17df935ed4ee9efb8759d8c8d7`. Applied the cumulative Admin patch after
edrock `8b75007d7` and the four missing trailer patches, with the adaptations
below. Existing upstream and unrelated fork behavior remain as before.
Recovery: `refs/codex/edrock-admin-trailers/20261116T031052Z`.

**Admin source commits incorporated:**

- `654860bd0` feat(admin): refresh the admin spec from silo-server 7695787c
- `d66aa6795` feat(admin): refresh the admin spec from silo-server df9f5501 (descriptions only)
- `5d0f638ee` feat(admin): refresh the admin spec from silo-server 30a6abd1
- `160dd7441` feat(admin): refresh the admin spec from silo-server 4a9e527a
- `39f88ffbd` feat(admin): refresh the admin spec from silo-server aa827602 (descriptions only)
- `4677da5f8` feat(admin): refresh the admin spec from silo-server 0b579aa6
- `4bd1b22fa` fix(admin): offer only operations the console can run, and bind item actions by position
- `b37f880fd` fix(admin): confirm every admin change before sending it
- `22b166a3f` fix(admin): let forms clear values, mask credentials by name and edit maps
- `2fb56fcb2` fix(admin): recover from stale version tags and keep list state in the console
- `4bf956e9d` fix(admin): make the hand-built admin screens safe to revisit
- `c45d02e0e` fix(admin): retry the admin menu check and stop repeating it on TV
- `337e79e55` test(admin): check the bundled spec's filtering, item binding and guards
- `ef9078888` feat(admin): refresh the admin spec from silo-server 4448e401
- `343389a2a` feat(admin): refresh the admin spec from silo-server 2cc78e4c and show when a running task started
- `857f1d00a` feat(admin): refresh the admin spec from silo-server e12193d6
- `2c5873c66` feat(admin): show why each stream is transcoded on the Dashboard
- `38cbed941` chore(admin): refresh the bundled admin API spec from silo-server 699f84ec

**Trailer source commits incorporated:**

- `12a80264b` fix(detail): label the phone Trailer button from the translation catalog
- `863f52f2f` fix(trailers): keep the trailer playing when a remote or display setting changes
- `f653570cd` fix(trailers): hand stuck or offline trailers to YouTube and track playback state
- `b1aedaaab` fix(tv): localize the Trailer button on the detail page

**Integration decisions:**

- Admin (`admin-console`): imported supported-operation filtering, path-position
  item binding, confirmations for generic mutations, form clearing/maps and
  credential masking, command-identity/stale-version recovery, list-state and
  pagination fixes, notice handling, stable task rows and retryable menu checks.
  Included server-provided task start times and dashboard transcode reasons.
- Retained Silora's default-off, local per-server/account/profile Admin menu
  setting and primary-profile/server permission gates. Adapted edrock's
  profile-ID-only access cache to a captured `AuthScopeSnapshot`, pinned the
  account read, discarded replies after identity switches and preserved retry
  cancellation. Nine focused access tests cover caching, server/account/profile
  and login changes, unavailable profiles, stale replies, retry and cancellation.
  Existing Silora pagination tests and the non-JSON form rejection remain.
- The 472-operation bundled asset and updated extractor match pinned v42.
  Regenerated the asset from official silo-server `699f84ec`'s OpenAPI blob
  `aaacde49b1dc322e17ff1922959e18fb255c6b12` and compared the full parsed JSON.
  Its source stamp is `3e256e30 2026-11-15`, the contract file's last-change
  stamp recorded by the extractor, rather than the server checkout's HEAD.
  Generic forms exclude unsupported upload/download/raw-protocol operations.
- Trailers (`trailers`): imported the activity's stalled/offline start fallback,
  foreground-only handoff, loading indicator, playback-state screen-on handling,
  guarded remote commands and tokenized JavaScript bridge. Both manifests keep
  playback across the additional remote/display configuration changes. Phone
  and TV actions use the translated catalog label. Existing local-trailer
  priority, phone More placement, TV controls and upstream Show Series Info stay.
- Updated the fork manifest and Admin documentation. These are Android fork
  console/WebView/resource changes; Apple, Roku and Web have no code path through
  them. No cross-client PR or issue was opened. Subtitle and HEVC code is unchanged.

**Validation:**

- Release Admin suites passed: 15 shared engine tests and 22 android-shared
  access/asset/contract/pagination tests; 37 total, zero failures/errors/skips.
  The final focused rerun passed after making the mock network dispatcher
  explicit. No new UI tests were added.
- Release Kotlin compilation and full release lint passed for phone and TV,
  with existing lint baselines unchanged. The combined command was
  `./gradlew :shared:testReleaseUnitTest --tests 'org.siloserver.silo.admin.*' :android-shared:testReleaseUnitTest --tests 'org.siloserver.silo.common.admin.*' :androidApp:lintRelease :androidTvApp:lintRelease :androidApp:compileReleaseKotlinAndroid :androidTvApp:compileReleaseKotlinAndroid --parallel --console=plain`
  under JDK 21; it completed in 2m 8s. The final Admin rerun completed in 8s.
- Supply-chain and derived Silora resource checks, full asset regeneration,
  pinned-edrock source comparisons, scope checks and staged diff checks passed.
  No dependency verification, release tooling or unrelated source was changed.
- No new APK build, device trailer playback or authenticated real-server Admin
  operation was tested. This update was committed locally; no push, release or
  external message was performed for it.

## 2026-11-15 18:33 PST — upstream 40d34e6d9

**Merge inputs:** `main`; fork before `34b5be69a25030931ec8f22d6df43eeade701de1`;
upstream `40d34e6d9d0c7f3aae82787ece12eb3093ccf6e7`. These 22 commits fix Google TV voice results,
stack simultaneous subtitle cues, sync downloaded playback progress, refresh TV
resume/episode data after playback stops, close Requests when the server turns
it off, improve TV season/detail navigation and skip-pill seeking, fix phone
remote-control behavior, and expand/update translations. Recovery:
`refs/codex/upstream-merge/20261116T022619Z`.

**Upstream commits added:**

- `cc35bac84` fix(tv): let Left and Right seek while a skip pill has focus (#928)
- `69e39ad45` fix(catalog): list Specials first as season zero (#929)
- `d902e961b` fix(catalog): keep the Specials chip in view and label season zero Specials (#931)
- `d9983125f` fix(player): mark every credits range on the phone and TV seek bars (#933)
- `c24555c70` fix(player): mark every marker occurrence above the played fill on phone and TV (#934)
- `3632eda4e` feat(tv): move Show to the series More menu as Show Series Info (#932)
- `5613b278e` fix(tv): keep Play off for the previous season while a new season loads (#936)
- `064b8d0b7` fix(tv): keep Show Series Info on the series after an entry episode (#937)
- `c1371178b` fix(tv): keep Show Series Info on the series after returning to a season page (#938)
- `0c0f8f834` feat(i18n): ship Dutch from translations 2026.11.15-277265f (#943)
- `712ddb547` fix(tv): fill the search field from voice on Google TV with Android 12+ (#945)
- `0718a96ec` fix(cast): name the connected TV on Play and in the Remote menu on phones (#947)
- `93927d29e` fix(cast): stop the phone app crashing when Remote Control and local playback overlap (#949)
- `f410d220b` feat(i18n): ship 13 more languages from translations 2026.11.15-8dbdfe4 (#950)
- `3afd7006f` fix(player): stack subtitles that are on screen at the same time (#862)
- `d1c36e7be` fix(home): drop Explore all from a Continue Watching row that shows its whole list (#908)
- `9a6a8cdcc` chore(i18n): sync translations 2026.11.15-0ba1666 (#952)
- `376446317` fix(tv): open the Continue Listening row's full page instead of a missing section (#953)
- `b7cc29ec8` fix(tv): re-read the episode rail after the player's stop lands (#954)
- `a4045779b` fix(progress): send download progress to the server and stop stale local resume (#847)
- `98d077170` fix(requests): close request screens whenever the server turns requests off (#855)
- `40d34e6d9` fix(tv): show the server's resume point once the player's stop lands (#955)

**Silora merge decisions:**

- Superseded `tv-google-voice` at Matt's explicit request with upstream PR #945
  (`712ddb547`, fixes #887). Both `TvVoiceSearch.kt` and
  `LegacyTvVoiceSearch.kt` now match the pinned upstream tree byte for byte,
  including its resources, lifecycle, permission/error handling and fallback.
  The TV manifest's speech queries, permission and optional microphone match
  upstream. Removed the unused fork microphone-permission resource override.
  Updated `FORK_DIFFERENCES.md` and the technical preservation checklist;
  the earlier `tv-voice` controller/overlay remains retired.
- Resolved the TV detail-screen conflict by retaining `trailers` alongside
  upstream's Show Series Info callback and More action. Resolved FEATURES
  conflicts by accepting Requests refresh/exit behavior while retaining
  `admin-console` as a separate administrator surface. The other three
  conflicted files were the voice helpers and manifest comments; those now
  use upstream wording.
- Preserved the other active fork IDs: `identity`, `hevc-levels`, `night-mode`,
  `tv-display`, `tv-subtitle-size`, `tv-remote`, `tv-keyboard`, `input-scope`,
  `trailers`, `edrock-ai-subs-fixes`, `tv-live-ai-subtitles`, `up-next-setting`,
  `up-next-fixes`, `credits-auto-skip`, `admin-console`, `release-tooling` and
  `fork-maintenance`. Reviewed all overlapping files, including automatic
  merges. Playback/marker changes retain remote shortcuts, credits skipping,
  live AI subtitles, Up Next triggers and end artwork. Requests route exits
  leave Admin separate. Settings keys, defaults, scope, migrations and startup
  bindings are unchanged; TV Settings only receives upstream casing changes.
- Kept upstream's generated translations byte-identical and regenerated
  separate Silora identity resources for all shipped languages, including the
  14 newly shipped locales. README retains upstream text plus the Silora link.
  No unexplained product difference was introduced. Apple, Roku and Web need
  no new parity issue for retiring an Android speech-provider workaround;
  inherited upstream features retain their existing contribution ownership.

**Validation:**

- `./gradlew test :androidApp:lintRelease :androidTvApp:lintRelease :androidApp:compileReleaseKotlinAndroid :androidTvApp:compileReleaseKotlinAndroid --parallel --console=plain`
  passed with JDK 21 in 4m 6s. Reports contain 12,170 variant test cases,
  zero failures/errors/skips. Shared ran 2,522 cases in each debug/release
  variant; android-shared ran 2,352 in each; phone ran 983 debug host cases;
  TV ran 1,435 debug host cases; libass ran two in each debug/release variant.
  Both apps disable release host suites; their stale reports were excluded.
  Both release apps compiled, and full release lint passed with the existing
  baselines and warnings. No baseline or verification safeguard changed.
- `scripts/check-translations` matched `2026.11.15-0ba1666` against a fresh
  catalog installation. `python3 scripts/sync-silora-strings.py --check`,
  `scripts/check-build-supply-chain.sh`, voice-helper/declaration equivalence,
  the active fork-ID audit, conflict-index checks and staged diff checks passed.
- Validated staged tree `52c63f2b92391556c4d7ead04af2d82a534fc45b` while HEAD
  still named the pre-merge parent. This changelog entry is the only subsequent
  tree change. No APK, signing, device/server playback or fresh voice test was
  performed. Nothing was pushed or published.

## 2026-11-14 17:36 PST — upstream 73e55b96b

**Merge inputs:** `main`; fork before `5fd18307fd09eddd4f69249c44a2970bfe102969`;
upstream `73e55b96bcbc8e75d98d81657cc4c7db7fc79ad2`. Six upstream commits add web-aligned
browse sorts, scale card badges with text/poster size, group large counts,
fix invitation token transport and claim retries, and resolve the Compose
Gradle plugin from Maven Central without changing dependency verification.
Recovery: `refs/codex/upstream-merge/20261115T011740Z`.

**Upstream commits added:**

- `cc4fd6662` fix(overlays): scale card badges with text size and poster size (#912)
- `a0391bf5c` build: take the Compose Gradle plugin from Maven Central only (#918)
- `a60dac2d0` feat(browse): match the web client's sort options on phone and TV (#843)
- `b2b0fb565` fix(i18n): group thousands in collection, Browse and person counts (#916)
- `a9c8fa4cf` fix(auth): send the invitation token in the request body (#920)
- `73e55b96b` fix(auth): let an invitee retry an invitation claim the server refused (#923)

**Silora merge decisions:**

- No textual conflicts. Accepted upstream sort fields, media relevance,
  rating-source visibility, per-field directions and saved-list/collection
  ordering. Kept server/account/profile refresh and stale-response guards.
- Retained Silora application identity (`identity`), opt-in/scoped Admin menu
  and API binding (`admin-console`), and TV search keyboard/voice ownership
  (`tv-keyboard`, `tv-google-voice`, `input-scope`) in the overlapping files.
  Upstream rating capability refresh runs alongside the existing feature stores.
- Preserved all four overlapping fork deltas and 278 other fork paths.
  Night Mode, HEVC/display/subtitle controls, remote shortcuts, trailers,
  AI/live subtitles, Up Next, Skip Credits and signed release/notification
  tooling remain intact. The retired fork voice controller stays removed.
  No active difference was retired and no new product override was added.
- Kept upstream's generated translation catalog byte-identical, together with
  Silora's separate derived identity resources and i18n documentation.
- Corrected one existing Android test harness after validation found a
  `ConcurrentModificationException`: the main-thread-only letter pager was
  called from IO while an unconfined dispatcher could resume on other threads.
  Tests now keep pager calls and ViewModel resumptions on one real main thread;
  the cached-index assertion still precedes release of the blocked fresh read.
  Production pager code and assertions remain intact. This test-only correction
  has no Apple, Roku or Web implementation work; inherited features retain
  upstream contribution ownership.

**Validation:**

- Fork/source audit, conflict-index check, staged/working-tree parity and
  `git diff --cached --check` passed. Fresh translation installation matched
  `2026.11.14-d36b68d`; Silora derived-resource check passed.
- `./build.sh --release=both --tests --lint --signing-dir /home/albinoman887/Workspace/SigmaStreams/Silo/source/silora-signing`
  passed with JDK 21: 12,056 Gradle variant cases, zero failures/errors/skips,
  plus 149 release/notification script tests. Shared, android-shared and
  libass debug/release suites ran. Phone (982) and TV (1,428) used debug host
  suites; upstream disables phone release host tests for its debug-only Compose
  test manifest, matching TV's existing configuration. Both release apps compiled.
- Full release lint passed with unchanged baselines. Strict supply-chain and
  dependency verification, R8, persistent release signatures, `org.silora.app`,
  version/non-debuggable checks, and APK/matching-mapping checksums passed.
- The first sandbox attempt stopped at the read-only Gradle cache lock; host
  cache access resolved it. The first full run found the test-harness error
  above. All seven focused letter-index cases and the full required command
  passed after correction; no checks or signing safeguards were weakened.
- Pre-commit APKs compiled staged tree `aaf0812e1080323b2345572fe16d1e95366a6496`
  while HEAD still named the old parent. This entry is the only later tree
  change. Exact committed-source artifacts are verified separately before push.
  No device, server, playback or voice validation was performed.

## 2026-11-13 13:07 PST — upstream 4686d6dd8

**Merge inputs:** `main`; fork before `628082ce60f67eb048045bc270a15bbfcc02a62d`;
upstream `4686d6dd81c3185bdf4e38b589dedb84086246a4`. Six commits keep Version Auto
within the chosen edition on phone/TV, improve watch-progress contrast on bright
artwork, add TV keyboard Space-bar pause/resume, change the Version icon, and
update cross-client parity guidance and the addressable lock.

**Upstream commits added:**

- `9ce12edf1` docs: require parity issues in the other Silo clients (#897)
- `466c31968` feat(detail): keep Version Auto inside the chosen edition on phone and TV (#899)
- `e1d8f4e94` build(deps): bump addressable from 2.9.0 to 2.9.1 (#904)
- `f65fe6917` feat(android): make watch-progress bars visible on bright artwork (#902)
- `a09cd3e66` fix(tv): let the keyboard Space bar pause and resume playback (#844)
- `4686d6dd8` fix(detail): use a several-videos icon for the Version selector (#905)

**Silora merge decisions:**

- No textual conflicts. Accepted upstream edition-scoped Auto selection,
  quality/history/multipart handling, file-specific track resets and matching
  Play/Download selection. Retained phone/TV trailer actions (`trailers`).
- Accepted the shared outlined progress pill, badge clearance and Version icon.
  Preserved next-item artwork and end-countdown cancellation while updating
  the phone Up Next card's progress bar (`up-next-setting`, `up-next-fixes`).
- Accepted TV Space-bar pause/resume with repeat/key-up consumption and
  Ctrl/Alt/Meta shortcut guards. Retained remote long-press behavior, modal and
  Up Next ownership, automatic credits skipping, and live AI subtitle overlays
  (`tv-remote`, `credits-auto-skip`, `tv-live-ai-subtitles`, `edrock-ai-subs-fixes`).
- Integrated upstream parity instructions while retaining fork product exposure
  and maintenance rules (`fork-maintenance`). This merge adds no new product
  override or cross-client implementation; the upstream changes retain their
  original contribution ownership.
- Preserved all seven overlapping fork deltas and all 275 nonoverlapping fork
  paths, including intentional deletions. Night Mode, HEVC/display/subtitle
  controls, App/System keyboard and scoped input preferences, Google TV voice,
  opt-in/scoped Admin, Silora identity and signed release/notification tooling
  remain intact. No active difference was retired; the superseded fork voice
  controller and overlay remain removed.

**Validation:**

- Source/fork audit, clean conflict index, staged/working-tree parity and
  `git diff --cached --check` passed. Silora derived-resource check passed.
- `./build.sh --release=both --tests --lint --signing-dir /home/albinoman887/Workspace/SigmaStreams/Silo/source/silora-signing`
  passed with JDK 21. All 12,992 Gradle variant cases passed with zero failures,
  errors or skips, plus 149 release/notification script tests. Shared,
  android-shared, phone and libass debug/release host suites ran; TV used its
  debug host suite (1,428 cases), as release host tests remain disabled by the
  existing configuration.
- Full phone/TV release lint passed with existing baselines unchanged. Strict
  dependency verification/supply-chain checks, R8 optimization, persistent
  release signatures, package/version/non-debuggable checks, and archived
  APK/matching-mapping SHA-256 checks passed.
- The pre-commit build compiled staged tree `247e4d4be06a8eca0e19f4433e706ed4858ba550`;
  its provenance records the pre-merge HEAD and a dirty tracked tree. This log
  is the only subsequent source change; exact-commit build/provenance checks
  are required before push. Unrelated untracked files remain preserved.
- No device, server, playback or voice testing was performed.

## 2026-11-12 13:14 PST — upstream 7afdf3d69

**Merge inputs:** `main`; fork before `f389ca4227c402493a9c1dc1f5844de20c21b695`;
upstream `7afdf3d69c9fc85dabe5d0d67a22d1af62699140`. Five commits add title-order
letter jumps on phone/tablet, moving end-time labels on phone/TV, deliberate
viewer activity resets for TV's still-watching count, contributor evidence rules
and public translation checks.

**Upstream commits added:**

- `69e142cd6` fix(tv): reset the still-watching count when the viewer pauses or seeks (#876)
- `2b523311f` feat(browse): jump to a letter in title-ordered library grids on phone and tablet (#888)
- `e8c10f1db` fix(player): keep the end time moving while paused and add it to phones (#875)
- `8bad0f006` ci: require evidence on every pull request and close contributors' drafts (#894)
- `7afdf3d69` ci: check translations without a silo-translations token (#895)

**Silora merge decisions:**

- No textual conflicts. Accepted upstream library paging/letter rails and
  server-unsupported fallback, shared end-time clock and Watch Party 1x label,
  viewer-action resets and auto-advance handoff counting.
- Adapted `credits-auto-skip` to upstream's non-viewer seek path so automatic
  credit skips do not reset the still-watching count. Manual seeks still count
  as viewer activity. Preserved custom trigger/timing, next-item artwork,
  dismissal/end-countdown guards and manual TV Up Next (`up-next-setting`,
  `up-next-fixes`), plus TV AI workarounds and live cues (`edrock-ai-subs-fixes`,
  `tv-live-ai-subtitles`).
- Retained Night Mode, HEVC controls, independent display matching, Extra small
  TV subtitles, remote shortcuts, App/System keyboard and scoped input settings,
  trailers, opt-in/scoped Admin, Silora identity and the Google TV voice-service
  extension. The superseded voice controller/overlay remain removed. All 275
  nonoverlapping fork paths retain their pre-merge content/deletion state.
- Integrated upstream contributor/evidence instructions and public-catalog
  checks while retaining fork maintenance/product exposure and Silora resources.
  Excluded two upstream-generated Python cache files under `scripts/__pycache__`
  to keep generated output out of the source tree. No new product override or
  feature retirement; signed release tooling and notifications are unchanged.

**Validation:**

- Source/fork audit, clean conflict index, staged/working-tree parity and
  `git diff --cached --check` passed. Complete translation regeneration from
  pinned `local-bb5a6783406c` matched; temporary Node 22.18 supplied the required
  runtime. `scripts/sync-silora-strings.py --check` passed.
- `./build.sh --release=both --tests --lint --signing-dir /home/albinoman887/Workspace/SigmaStreams/Silo/source/silora-signing`
  passed with JDK 21. All 12,965 Gradle variant cases passed with zero failures,
  errors or skips: shared, android-shared, phone and libass debug/release host
  tests, plus 1,427 TV debug host cases. Existing configuration disables TV
  release host tests; no debug APK was built.
- 176 release, notification and CI-routing/result script tests passed. Full
  phone/TV release lint passed with no errors and unchanged baselines. Strict
  dependency/supply-chain checks, R8, signed phone/TV assembly, package/version/
  non-debuggable/signature checks and archived APK/mapping checksums passed.
- Pre-commit APKs compiled staged tree `350d550ceeadc859e182954f5efe09ef84e239af`;
  their metadata records the pre-merge parent. Exact-commit artifact verification
  remains a separate required gate before pushing or handoff. The initial
  sandbox cache-lock failure was repaired through reviewed host cache access;
  no checks were weakened. Unrelated untracked files remain intact and are not
  build inputs. `actionlint` was unavailable; workflow YAML/supply-chain checks
  passed.
- No device, server, playback or voice testing occurred in this automation run.

## 2026-11-11 14:34 PST — upstream c2ad874cd

**Merge inputs:** `main`; fork before `74795fb930d361cd6d962f404ef6156f00ea8c4c`;
upstream `c2ad874cd296e2e017cbb26baef07eeada6d91ec`. Six upstream commits align poster overlays
with the web, open Change Account when launch policy asks with several saved
accounts, choose Add/Switch Profile by profile count, refine the TV profile menu,
and correct the embedded-subtitle mount deadline.

**Upstream commits added:**

- `48015d4ee` docs(agents): tell contributor agents to treat the code as someone else's (#878)
- `2d3620d50` feat(overlays): match the web's poster overlay layout on phone and TV (#882)
- `ce24a6b5c` feat(accounts): open Change Account at launch when several accounts are saved (#880)
- `8b9b3f017` feat(profiles): offer Switch Profile or Add Profile by profile count (#881)
- `84ea05a6b` fix(tv): match the Apple TV profile menu's header line, Sign Out colour and divider (#883)
- `c2ad874cd` fix(tv): start the embedded subtitle mount deadline on the new mount's tracks (#879)

**Silora merge decisions:**

- Resolved two navigation conflicts (`admin-console`): provide both phone
  composition locals and both TV callbacks, keeping the gated Admin console
  alongside upstream's Add Profile flow. Retained the default-off, device-local
  visibility setting and administrator/primary-profile access checks.
- Adopted upstream poster layouts, watched/progress badges, overlay settings,
  profile-count/limit handling, verified primary-profile Add flow, account launch
  policy, TV menu order/header/divider/red Sign Out and mount-deadline gate.
  Requests keeps upstream server gating; Watch Party remains experimental.
- Retained Silora identity/resources (`identity`), the newer Google TV direct
  speech-service extension (`tv-google-voice`), App/System keyboard and scoped
  input preferences (`tv-keyboard`, `input-scope`). The superseded `tv-voice`
  controller and overlay remain removed.
- Preserved Night Mode, HEVC level controls, independent TV display matching,
  Extra small subtitles, remote shortcuts, trailers, AI workarounds/live cues,
  custom Up Next/artwork and Skip Credits. The only incoming TV player change
  waits for the new transport mount before starting its subtitle deadline.
- Retained signed release tooling, strict dependency verification, R8/mappings,
  fork maintenance rules and the upstream README with its Silora link. Integrated
  upstream's contributor-scope instructions. No fork feature retired or new
  product override introduced.

**Validation:**

- `git diff --cached --check`, clean conflict index, staged/working-tree parity,
  affected-fork source review and `scripts/sync-silora-strings.py --check` passed.
  All ten changed official catalog/metadata files match pinned upstream.
- `./build.sh --release=both --tests --lint --signing-dir /home/albinoman887/Workspace/SigmaStreams/Silo/source/silora-signing`
  passed with JDK 21. All 12,915 Gradle variant cases passed with zero failures,
  errors or skips: shared, android-shared, phone and libass debug/release host
  tests, plus 1,427 TV debug host cases. Existing configuration disables TV
  release host tests; no debug APK was built.
- 149 release/notification script tests passed. Full phone/TV release lint
  passed with warnings and no errors; lint baselines were unchanged. Strict
  supply-chain checks, optimized signed phone/TV assembly, application/version/
  non-debuggable/signature checks and archived APK/mapping checksums passed.
- Pre-commit APKs compiled staged tree `87d523928ea3f4faafa67b851d8df07652d5a819`;
  their build metadata records the pre-merge parent. Exact-commit artifact
  verification remains a separate required gate before pushing or handoff.
  Unrelated untracked files were preserved and are not build inputs.
- No device, server, playback or voice testing occurred in this automation run.

## 2026-11-11 — use upstream voice service on newer Google TVs

Voice search now returns spoken text on the Android 14 Google TV Streamer.
Matt approved extending upstream's direct recognition-service helper after the
unchanged upstream activity failed there, then confirmed the trial worked.

- Select upstream's service helper for Google's TV recognizer on Android 11 and
  later when the service is available. Keep upstream's activity path for other
  providers, older Android and service-unavailable cases.
- Remove the API-30 cap from TV microphone permission so the helper can request
  it on Android 14. Keep the optional microphone feature and both speech queries.
- Reuse upstream's dialog, recognition callbacks and cleanup. The removed Silora
  controller and overlay stay retired. Use the existing Silora translation for
  the permission-denial message.
- The signed trial at `dist/local-2026-11-11.184425/` was installed as
  `org.silora.app` alongside the unchanged upstream comparison app. The dedicated
  test account signed in successfully. Permission denial showed the explanation;
  after granting access, Matt said “hello,” the dialog closed, “hello” appeared
  in the editable search field and results loaded. System logs identify
  `callingApp: org.silora.app` and a final recognition result.

Validation: all 1,427 TV host tests passed; full release lint, signed assembly,
signature/application/version checks and archive checksums passed for the trial.
The final signed APK with the permission-message name correction passed the
same checks and was installed from `dist/local-2026-11-11.185305/silora-tv.apk`
(TV version code `854395171`). On that exact APK, Back canceled the listening
dialog and the speech service closed its microphone. Matt then confirmed
“Star Wars” worked: the dialog closed, the editable query became “Star Wars,”
results loaded and the system logs identify Silora as the direct service caller.
No fresh Shield test has been performed; its existing Android 11 service path
remains selected. Phone code is unchanged. Matt requested committing the validated
change locally; no push or publication was requested.

See the [device investigation](docs/maintenance/2026-11-11-streamer-voice-investigation.md)
for the baseline failure, successful service-path trial and evidence limits.

## 2026-11-11 — adopt upstream TV voice search

Retired the fork's `tv-voice` implementation at Matt's request after he tested
upstream and confirmed the microphone-button search works.

- Restored `TvVoiceSearch.kt` and `LegacyTvVoiceSearch.kt` unchanged from upstream
  `09f43a215`, including PR #830's activity launch/result handling, PR #860's
  Android 11 Google recognition-service fallback and PR #869's translated text.
- Removed the fork speech controller, overlay, permission/settings flow and
  eight tests tied to that controller. Restored upstream's recognition-activity
  query and API-30 cap on `RECORD_AUDIO`, retaining its service query and optional
  microphone feature for Android 11.
- Adopted upstream's mic button, availability check and translated unavailable
  message. Spoken text updates the editable field and submits library/request
  searches through the existing keyboard integration and query-length limit.
  App/System keyboard preferences and request-search deduplication remain.
- Marked `tv-voice` superseded in the fork inventory and maintenance checklist.

Validation: all 1,427 TV host tests passed with no failures, errors or skips.
The existing configuration disables TV release host tests, so the supported
`:androidTvApp:testDebugUnitTest` task ran without building a debug APK.
Full TV release lint passed with no errors, and the optimized, signed TV APK
built successfully. Signature, application ID, version and archived checksums
passed verification. Both voice helpers match upstream byte for byte; the
packaged manifest has both speech queries and the API-30 microphone permission
cap. The R8 mapping includes both upstream paths and no retired fork controller
or overlay.

APK, matching R8 mapping and source provenance:
`dist/local-2026-11-11.181516/`. Visible version: `1.2.0 (31) (silora:11112026)`;
TV version code: `854390633`. The build includes these uncommitted source
changes and records that state. Matt's upstream test is owner-reported; this
Silora APK has not been microphone-tested on a device. No commit, push or
publication was performed.

Later device check: Matt clarified that upstream works on his Android 11 Shield
but fails on his Android 14 Google TV Streamer. A live test on the unchanged
upstream APK reproduced the failure: the speech engine reports final results
while Google's recognition activity remains open with no text. TiviMate works
on that Streamer and calls the recognition service directly. Upstream's existing
direct-service helper is restricted to Android 11. The adoption remains local
and uncommitted; no new source workaround was applied. See the
[device investigation](docs/maintenance/2026-11-11-streamer-voice-investigation.md)
for evidence, limitations and the next implementation test.

## 2026-11-10 12:55 PST — upstream 09f43a215

**Merge inputs:** `main`; fork before `4fb46a09a37a8fe81542033df2701f3f6df0489e`;
upstream `09f43a2158326afca9864d00788b7019c18fa8fe`. Seven incoming commits add profile app
languages, the translation catalog, translated screens/shared messages,
locale-aware formatting, debug pseudo-locales and Arabic TV layout fixes.

**Upstream commits added:**

- `a57894556` feat(i18n): apply the profile's app language and ship the translation catalog (#868)
- `59f15e534` feat(i18n): translate settings, playback, browse, account and shell screens (#869)
- `6af97fe0c` feat(i18n): offer pseudo-locales in the debug App language picker (#870)
- `edfa7681b` feat(i18n): translate text built in the shared module (#871)
- `c0fd4ff85` feat(i18n): format dates, numbers and durations in the app language (#872)
- `d2e639476` fix(i18n): fix layouts and gaps found in translation QA (#873)
- `09f43a215` fix(i18n): keep Android TV text beside its labels and digits consistent in Arabic (#874)

**Silora merge decisions:**

- Accepted upstream's app-language settings, identity-scoped store/startup
  bindings, translated UI/shared message types, formatting and TV label/digit
  fixes. Adapted the fork's affected call sites to resource IDs and indexed
  pickers without changing stored choices, preference keys or wire values.
- Resolved 42 conflicted files. Retained `identity` through distinct `silora_`
  resources derived from the catalog's translated strings and plural forms;
  the 11 upstream catalog/metadata files remain byte-identical. Kept Silo
  server, hosted diagnostics, Roku and protocol identities. Fork-only controls
  and phone-versus-server sign-in help have English fallback resources.
  `scripts/sync-silora-strings.py` and `docs/i18n.md` document regeneration.
- Retained `hevc-levels`, `night-mode`, `tv-display` and `tv-subtitle-size`:
  existing storage/runtime behavior, Night Mode controls/focus, independent
  display switches and Extra small TV subtitles remain. Phone's subtitle
  picker still starts at Small.
- Retained `tv-remote`, `tv-keyboard`, `tv-voice` and `input-scope`: existing
  App/System keyboard/editing behavior, profile/account isolation, long-press
  mappings, controller/overlay voice search, microphone permission and service
  query remain. Supplied resources to the remote subtitle shortcut; the unused
  upstream legacy voice helper stays deleted. The deferred device-confirmation
  condition has not changed.
- Retained `up-next-setting`, `up-next-fixes` and `credits-auto-skip`: custom
  trigger/timing choices, end-state artwork, cancellation/countdown behavior,
  four Skip Credits choices, profile reset, local scope and warning remain.
  Retained `edrock-ai-subs-fixes`, `tv-live-ai-subtitles` and `trailers`, including
  server audio ordinals, bounded create/replan guards, language aliases, live
  cue/job cleanup, embedded/local trailer preference and external fallback.
- Retained `admin-console`, ordinary Requests scope/server gating, and the
  Watch Party experimental gate. No fork feature was retired or replaced and
  no unexplained product override was adopted.
- `release-tooling`: Matt explicitly requested disabling the inherited Android
  Builds workflow during this run. GitHub confirms `android-build.yml` is
  `disabled_manually`; Silora Automation Release remains active. Kept the
  upstream catalog-check source and every Silora signing, test, lint, mapping,
  dependency-verification and publication safeguard. No notification workflow
  or desktop schedule was changed.

**Validation:**

- Required JDK 21 command passed: `./build.sh --release=both --tests --lint
  --signing-dir /home/albinoman887/Workspace/SigmaStreams/Silo/source/silora-signing`.
  Both R8-optimized release APKs passed persistent-key signature, package,
  version and non-debuggable checks; archived checksums and matching R8
  mappings verified.
- Gradle XML reports contain 12,749 variant cases, zero failures/errors/skips:
  shared 2,409 debug + 2,409 release; android-shared 2,293 debug + 2,293 release;
  phone 953 debug + 953 release; TV 1,435 debug; libass bridge 2 debug + 2
  release. TV release host tests remain disabled by the existing configuration;
  its debug host suite ran. Full phone/TV release lint passed with the existing
  baselines unchanged.
- All 88 local release-tooling tests passed, plus 34 automation-release,
  17 local-notification and 10 daily-notification tests. Build supply-chain,
  branded-resource consistency, staged whitespace and source/resource checks
  passed. `actionlint` is unavailable.
- Fresh upstream catalog regeneration could not run: the installed GitHub login
  cannot access the private `Silo-Server/silo-translations` repository. Verified
  committed catalog bytes against the pinned official upstream instead; Matt's
  explicit workflow-disable decision above removes the inherited push gate.
- Repaired merge adapter compilation errors and updated the upstream push test
  to expect the retained Silora identity, then reran the full required command.
  The initial sandbox cache-lock failure was resolved with reviewed access to
  the existing Gradle cache, without changing build/signing/check requirements.
- The MPV text guard has unchanged `rampVolume` substring and attribution-comment
  false positives. Reviewed both baseline files and checked APK archive contents
  for MPV artifacts; none are present.
- The pre-commit build compiled staged tree `0d43b8f07bb38a9e9e711d54329ab901fdeda30a`.
  Its provenance records the old parent HEAD and a dirty merged tree. Only
  inventory/changelog documentation follows those checks. Exact-SHA artifact
  verification follows the merge commit before push.
- No device, server, microphone or playback testing was performed.

## 2026-11-09 13:04 PST — upstream 075e37d6a

**Merge inputs:** `main`; fork before `27193f9df9d35c586dd145dd97f816e74d9c1f8a`;
upstream `075e37d6a75bff84cd5c5f07e918cd41d6c06601`. Incoming changes add bounded
loading for stalled Dolby Vision starts, an Android 11 Google voice fallback,
and feature-idea/contribution-policy updates.

**Upstream commits added:**

- `484b55da5` fix(tv): restore Google voice search on Android 11 (#860)
- `58d9aac6e` fix(playback): keep loading when a Dolby Vision start stalls on passthrough audio (#861)
- `075e37d6a` ci: move feature requests to Discussions and close out-of-policy pull requests (#866)

**Silora merge decisions:**

- Retained (`night-mode`): resolved the player-factory conflict by keeping
  Night Mode's audio binding alongside upstream's new load-control playing
  listener. PCM/output policy, libass initialization and release cleanup remain.
- Retained (`tv-voice`): kept Silora's in-app speech controller, overlay,
  recognition-service query and microphone permission on newer Android versions.
  PR #860 limits its service fallback to Android 11; it does not satisfy the
  deferred replacement's device-confirmation condition for Android 14. Resolved
  the manifest and modify/delete conflicts without restoring the unused upstream
  activity controller or its new legacy helper. Updated the inventory/checklist.
- Accepted upstream's stalled-start guard and its four focused tests unchanged,
  plus its contribution workflow, templates, policy and README updates; retained
  the README's Silora link. The guard extends loading only during a playing
  position stall, bounded by time and memory limits.
- Other active fork differences have no source changes in this merge, including
  Admin opt-in/access controls, live AI subtitles, Up Next, credits skipping,
  display/input preferences, identity and release tooling. No workaround was
  declared superseded and no unexplained product difference was adopted.

**Validation:**

- Required JDK 21 command passed: `./build.sh --release=both --tests --lint
  --signing-dir /home/albinoman887/Workspace/SigmaStreams/Silo/source/silora-signing`.
  Both R8-optimized release APKs passed persistent-key signature, package,
  version and non-debuggable checks; matching mappings and checksums verified.
- Gradle XML reports contain 12,572 variant test cases, with zero failures,
  errors or skips: shared 2,369 debug + 2,369 release; android-shared 2,251
  debug + 2,251 release; phone 947 debug + 947 release; TV 1,434 debug;
  libass bridge 2 debug + 2 release. TV release host tests are disabled by the
  existing configuration; its debug host suite ran, with release compilation
  and full release lint. Both app release-lint tasks passed with existing
  baselines; baselines were not changed.
- All 88 local release-tooling tests passed, plus 34 automation-release,
  17 local-notification and 10 daily-notification tests. Build supply-chain
  checks and staged whitespace checks passed. `actionlint` is unavailable.
- The MPV text guard reports pre-existing `rampVolume` substring matches and
  an attribution comment, all byte-identical to the pre-merge source. Manual
  review found no added runtime reference; neither APK contains MPV artifacts.
- The initial sandbox attempt could not write the existing Gradle cache lock.
  The identical required command passed with cache access; no build,
  signing, lint or dependency-verification requirement was weakened.
- The pre-commit build compiled staged merge tree
  `59dac55a5720157b3dc09c3dbe80876c9e540732`; its build-info records the
  pre-merge parent HEAD and a dirty tracked tree. Only this changelog entry is
  added after those checks. Exact-SHA artifact verification follows the merge
  commit before push. The unrelated untracked release wrapper is preserved.
- No device, microphone, server or playback testing was performed.

## 2026-11-08 — one-command local build and release

Added `./helper.sh` to run `./build.sh --lint --tests`, then upload the exact
newly verified archive and queue its publication through `release.sh local
<archive> --publish`. It uses a fresh temporary build-path receipt rather than
selecting a dated directory or parsing logs. Failed builds/checks cannot publish;
upload failures retain the archive for an explicit retry. Source must already
be committed and pushed; the helper does not commit or push it.

The local builder's new optional `--output-file` writes the absolute archive path
only after APK verification and packaging succeed. Tests cover the exact archive
handoff, misleading existing archives/logs, paths containing spaces, failures,
receipt cleanup and help/argument handling. Verification uses simulated build and
publish commands; no new public release was created for this change.

## 2026-11-08 — prevent older release jobs from replacing Latest

Reviewed version compatibility between Manual Release, Automation Release and
`release.sh` at Matt's request. `release.sh github` dispatches Manual Release;
all build routes already use the same timestamp-based Android version calculation.
Workflow counters do not determine update ordering.

Added a shared publication-order check to local, manual, automation and legacy
daily publishers. It rejects a candidate whose Android version is older than or
equal to the current Latest release before publication, including a failed job
resumed after a newer release ships. First releases can bootstrap; failed API
or metadata reads stop publication. Existing public releases remain immutable.
Historical runs retain their original code and should be replaced with fresh
runs on current main rather than replayed after newer releases.

Validation: shared ordering tests cover cross-workflow counters, repeated visible
labels, older/equal versions, first-release bootstrap and failed GitHub reads.
Manual and daily publication recovery tests exercise a stale publish retry;
automation and local recovery tests confirm the guard blocks publication.
No Android app code, APK version formula, signing configuration or schedule changed.

## 2026-11-08 — choose GitHub or PC release builds

Added `./release.sh github` to start the existing manual GitHub build and
`./release.sh local <archive> --publish` to publish the exact signed phone/TV
APKs built and tested on the PC. Local verification checks signatures, package
identity, versions, source provenance, full-lint evidence, matching mappings
and checksums. The default local command previews without writing to GitHub.

A small publish-only workflow verifies uploaded bytes under the existing release
lock. It prevents older builds from replacing a newer Latest release, resumes
matching drafts, and preserves published tags/assets. Existing GitHub build
workflows, schedules, signing secrets and notification scope stay unchanged.
Local builds now retain publication evidence alongside their existing archive.
Older archives without that evidence need rebuilding with full lint.

Validation: 23 local-publisher tests, ten local-build tests and 31 existing
fork-release tests passed, including simulated upload/read-back/publication,
partial-draft recovery, byte preservation and stale-build rejection. Workflow
YAML, shell syntax and the shared lock were checked. No app code changed and
no new public release was created for verification.

## 2026-11-08 — align Skip Credits with Skip Intros

Phone and TV **Skip Credits** now offers **Use profile setting**, **Never**,
**Ask to skip**, and **Skip automatically**, in that order. Removed the option
descriptions and explanatory footer; the existing automatic-skip warning stays
under the setting when automatic skipping is active. Matt requested profile
inheritance, matching labels/order and removal of the descriptions.

Explicit choices stay local to the account, profile, server and device, with
their existing saved values preserved. **Use profile setting** clears the local
mode and any device override of the server's credits boolean. The profile's
boolean maps enabled to automatic skipping and disabled to asking. Existing
local choices appear as overrides after an update, and resetting a legacy
choice on one device leaves other devices' choices intact.

Validation: all 2,369 shared release unit tests and all 90 player-settings store
release tests passed, including profile inheritance, later profile changes,
local persistence and legacy reset isolation. `git diff --check` passed.
Full phone/TV release lint passed with no unsuppressed errors (phone: 93
warnings, 4 hints; TV: 98 warnings, 8 hints). Signed, minified release builds
passed signature, application ID, non-debuggable flag and version verification.
APKs, matching R8 mappings, metadata and checksums are in
`dist/local-2026-11-09.025209/`. Matt tested the changes and confirmed they work
on November 8, 2026. No push, release or PR occurred.

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
Final validation at source `20c87d9e1`: all **12,556 host test executions**
passed with zero failures, errors or skips (shared: 2,369 per variant; Android
shared: 2,245 per variant; phone: 947 per variant; TV: 1,434 debug host tests,
with release host tests disabled by existing configuration). The 39 release
script tests passed. Full phone/TV release lint passed with no unsuppressed
errors (phone: 93 warnings, 4 hints; TV: 98 warnings, 8 hints). Signed, minified
phone and TV release assembly passed, as did certificate, application ID,
non-debuggable flag, version and packaged checksum verification. The build
used an invocation-only 12 GB heap; repository build settings are unchanged.

Final APKs, matching R8 mappings, metadata and checksums are in
`dist/local-2026-11-08.212639/`. Version: `1.0.0 (29) (silora:11082026)`;
phone code `853895198`, TV code `853895199`. All tracked source files matched
the recorded source commit during this final build. Its dirty flag reflects
the preserved unrelated untracked `.codex-attachments/` and
`run-manual-release-workflow.sh`. This follow-up changes only the validation
record. No push, release, PR, agent-performed device installation or
production-admin action occurred.

**TV device test passed:** Matt installed the signed TV APK from
`dist/local-2026-11-08.212639/` and reported on November 8, 2026 that testing
passed and the feature is good to go. This is Matt’s device-test result;
individual admin operations and phone-device testing are not separately
claimed. The dedicated agent test account’s login and identity were verified,
but its non-administrator role prevents agent-performed live admin API
validation.

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
  `tv-display`, `edrock-ai-subs-fixes`, `tv-live-ai-subtitles`, `up-next-setting`,
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
