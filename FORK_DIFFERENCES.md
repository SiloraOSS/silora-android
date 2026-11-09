# Silora fork differences

This is the maintained inventory of Silora differences from Silo Android. Matt
authorized creating the inventory and updating the merge skill on November 6,
2026. Entries remain subject to his review and corrections; implementation
references identify the code, not proof that he personally authored or requested
every historical change.

## Default and maintenance

Follow upstream behavior outside the active differences below, subject to the
current user request and applicable repository instructions. Requests navigation
has **no fork override**. Do not freeze its current upstream location; follow
upstream's navigation and server gating as they evolve.

Read `AGENTS.md` as development instructions. An old upstream description, old
merge resolution, test expectation, commit author, or changelog entry does not
establish a new intentional fork difference. Integrate upstream instruction
updates with the corresponding code instead of preserving stale descriptions.

- Update this file in the same change as explicitly requested new fork work.
  Record the behavior, reason, request/review establishing intent, implementation
  references, and relevant checks. Keep IDs stable across rebases.
- Routine API adaptations and new files supporting an active feature do not
  require a new product decision. Unexplained behavior differences need review;
  do not silently preserve, discard, or mark them approved.
- When upstream demonstrably replaces a workaround, record the replacement and
  validation, then mark that entry superseded. Keep retired/superseded entries
  with their reasons so later merges do not restore them.
- Formatting changes, test API adaptations, and inherited upstream features are
  not separate product customizations. Tests verify implementation, not intent.

Entries in the active section are the current preservation scope. Source/test pointers and migration
contracts live in the [technical checklist](.agents/skills/silora-upstream-merge/references/fork-fixes.md),
keyed by these IDs. Keep approval and required behavior here rather than adding
competing product requirements to that checklist.

## Active differences

| ID | Behavior and purpose | Implementation references |
| --- | --- | --- |
| `identity` | Silora's purple/charcoal identity, launcher/startup artwork, visible labels and `org.silora.app` distinguish this fork from official Silo. Retain upstream Kotlin namespaces and separate phone/TV module namespaces. | `5b8503ea5`, `6365ccaa8` |
| `hevc-levels` | **Ignore HEVC Level Limits** defaults on to allow a decode attempt despite level tags. Omit only HEVC level bounds; retain other capability checks, saved integer-key compatibility, Off behavior and read-failure recovery. | `9eda2ac76`, `ec68d4ecd` |
| `night-mode` | Phone/TV Night Mode reduces loud peaks through the compressor/limiter and retains tuning controls. Preserve preference scope, PCM processing, resource cleanup and disabled-control focus behavior. | `ddc5961b6`, `8f5435e58`, `9ee5c033a`, `bbc7b4117` |
| `tv-display` | Independent resolution and frame-rate matching let viewers choose either adjustment. Preserve the untouched display dimension when only one switch is enabled, alongside upstream selection fixes. | `a4903819f` |
| `tv-subtitle-size` | TV has an Extra small subtitle choice. Preserve local persistence, server-compatible Small wire fallback and existing phone sizes. | `eae382b4b` |
| `tv-remote` | Customizable long presses provide playback shortcuts while ordinary short presses and contextual action limits remain intact. Preserve playback-options focus handling; a Stats shortcut opens playback information, not an admin dashboard. | `04ef9c483`, `64a6d3b14` |
| `tv-keyboard` | Search offers App/System keyboards with System as default and automatic opening off by default. Preserve physical typing, explicit keyboard opening, cursor state, clearing and focus handoffs. | `1d8fc7a5f`, `a42991501`, `660758013`, `bcf487826` |
| `tv-voice` | Retain Silora’s in-app speech controller and overlay after the upstream activity-based recognizer failed to return search text on Shield and onn devices. Matt reproduced the failure with unchanged upstream source on his onn 4K Pro and reports that the prior Silora implementation works. Preserve microphone permission handling, cancellation/lifecycle cleanup, final-query submission and mic-focus return. Matt requested this fallback on November 6, 2026; revisit upstream only after an official build is confirmed to complete spoken searches on the affected device. | `dc03dfaa7`, `a42991501`; restored controller/overlay and eight existing session tests; current request in this chat |
| `input-scope` | Supporting storage for `tv-keyboard` and `tv-remote`: save only App/System keyboard choice, automatic keyboard opening, and long-press mappings separately per server/login/profile. This adds no menu or standalone setting. Preserve migration, switching and account-removal cleanup. | `a19729d21`; account integration in `73c46323f` |
| `trailers` | Embedded YouTube previews, external fallback and TV controls keep trailers accessible from movie/series details. Prefer local trailers and retain attribution. | `bac06a7c7` |
| `ai-subtitles` | Preserve the four fixes for server audio ordinals, bounded uncertain-create fencing, missing-row replanning and language-alias normalization. Retire individual workarounds when a verified upstream replacement supplies the same behavior. | `5536474b3` |
| `tv-live-ai-subtitles` | TV displays AI translation/transcription cues as the server streams them while the job runs, rather than waiting for the finished subtitle file. Show “Translating subtitles…” before the first cues, hide Media3’s subtitle view while the live overlay is active, and return to the existing finished-track selection path when the job poll completes. Phone has no live-subtitle UI. Matt cherry-picked edrock200’s patch and requested recording it on November 8, 2026. Retire this fork implementation when a verified upstream equivalent supplies the same behavior. | Local `b812d6274`, from [edrock200 `20e85217c927e76fe34edd820608403f4847452b`](https://github.com/edrock200/silo-android/commit/20e85217c927e76fe34edd820608403f4847452b); `PlaybackLiveSubtitles.kt`, `PlaybackLiveSubtitlesTest`, `TvPlaybackRealtimeController`, `TvPlayerViewModel`, `TvLiveSubtitleOverlay.kt`, `TvPlayerScreen.kt` |
| `up-next-setting` | **Up Next Trigger** offers Credits markers and Time remaining. **Time remaining → At end** waits until playback finishes; Matt requested removing the duplicate After playback ends choice on November 8, 2026. He confirmed that choice had not shipped, so no migration is needed; its old `off` value uses the default Credits markers. Preserve local trigger scope, timing fallback, normal end screen, autoplay and manual TV action. Phone/TV preview panes show live video during playback, then the next item's thumbnail after the video ends, with spoiler-safe series/season artwork or a standard placeholder as fallback. Matt approved the artwork behavior on November 7 and explicitly retained it during the November 8 simplification. | `eb13f7033`, `a10490adc`; `NextUpPromptMode.kt`, `NextUpArtwork.kt`, phone/TV Up Next views and player models |
| `up-next-fixes` | Eligible credits start in the final quarter; otherwise use timing. Keep Watching allows one guarded end countdown; cancellation preserves the end screen. Matt explicitly requested merging PR #2 after reviewing this behavior. | PR #2: `a7dce2fc6`, `7bd09d20e`, `ac56376af` |
| `credits-auto-skip` | **Skip Credits** in phone/TV Playback settings offers Use profile setting, Never (no credits skip button), Ask to skip (show a manual button), and Skip automatically (seek to each credits segment's end once per playback), in that order, without option descriptions. The explicit choice stays local to the profile/account/server/device. Use profile setting clears the local mode and any device override of the server boolean; the profile boolean maps true to Skip automatically and false to Ask to skip. Preserve existing saved modes and wire values. Warn when automatic skipping is active that incorrectly marked episodes may skip content or cause playback issues (use at your own risk). Preserve scenes between segments and allow rewinds. Automatic skips stay inactive during pauses, loading, scrubbing, content handoffs and Watch Party playback; automatic mode offers a room-gated manual button in Watch Party. Matt requested the feature, warning and local control on November 7, 2026, then profile inheritance, labels/order matching Skip Intros and removal of descriptions on November 8. | `CreditsAutoSkipController.kt` and focused tests; `CreditsSkipMode.kt`, phone/TV player observers and Playback settings; `AndroidPlayerSettingsStore.kt` and credits persistence/reset tests |
| `admin-console` | Phone and TV offer an optional **Admin** entry in the profile menu for administrator accounts, with dashboard/now playing, request moderation, tasks/scans, logs and generic forms from the bundled server admin API. The administrator's primary profile has a **Show Admin in profile menu** setting (default off), saved locally per server/account/profile; hiding the menu entry keeps the setting available. The server enforces administrator and primary-profile access; ordinary user Requests screens retain their existing scope. Matt explicitly requested cherry-picking edrock200’s complete admin range on November 8, 2026, overriding upstream’s unexposed admin surfaces. Preserve role/profile gating, the visibility preference, destructive-action confirmation, edit prefill, TV focus and API contract checks. | [Source range](https://github.com/edrock200/silo-android/compare/3cb8886ed5b5cb96f3140b8d7786d323143f2814%5E...3792ac473b58d283dc5bdd67dc05ceccb638f084); [implementation and maintenance](docs/admin-console.md); `AdminEngineTest`, `AdminBundledSpecTest`, `AdminHandBuiltContractTest` |
| `release-tooling` | Preserve persistent signed phone/TV builds, version metadata, APK verification, matching R8 mappings, release workflows and configured notification behavior. Matt requested a per-release choice of GitHub compilation or publishing the exact verified PC APKs on November 8, 2026; retain both routes, the shared release lock and rejection of stale builds across all publication routes. Matt also requested `./helper.sh` to run full local lint/tests and publish that invocation’s exact build without entering its dated path. The local publisher waits briefly for a newly created draft to appear in GitHub’s release list, retrying only missing reads without repeating creation. This entry does not authorize publishing, messaging, changing schedules/destinations or exposing signing material. | `bf8304d5b`, `5f68d3c69`, `74ea34c8a`, `8005d02c1`, `7d7b9bb72`, `2b958c5c2`, `198f651f4`; `release.sh`, `scripts/local-release.py`, `fork-local-release.yml`, `test-local-release.py`, `scripts/release-order.py`, `helper.sh`, `scripts/test-build-and-publish.py` |
| `fork-maintenance` | Maintain this reviewed inventory and the upstream-merge skill. Explain meaningful conflict decisions by feature ID; follow upstream outside intentional differences and do not treat inherited instructions as proof of a user customization. | November 6, 2026 authorization in this chat; `AGENTS.md`, `.agents/skills/silora-upstream-merge/SKILL.md` |

## Deferred upstream replacement

The November 6 retirement of `tv-voice` was reversed later that day at Matt’s
request. [Upstream PR #830](https://github.com/Silo-Server/silo-android/pull/830)
(`4fb8f1b59`) remains in the ancestry, but its activity-based voice path is not
used. The source/manifest audit found no missing upstream voice code or
permission. Matt then reproduced the same failure on an onn 4K Pro (Android 14)
using the unchanged upstream comparison build at `f13a2652a`. He reports that
the microphone works in other apps and with the prior Silora implementation.
The failure therefore reproduces without Silora patches; our merge and keyboard
adaptations are not required to trigger it. The exact recognizer/provider
failure has not been traced. The earlier retirement and audit records remain
in `CHANGELOG.md`.

Revisit the replacement after an official build is available and confirmed to
recognize speech, return the final text to search and submit the query on the
affected device. Merely merging the PR, publishing a release, or opening its
recognizer does not establish that replacement works.

Requests navigation has no fork override and must not be reintroduced from old
merge logs or instruction text.
