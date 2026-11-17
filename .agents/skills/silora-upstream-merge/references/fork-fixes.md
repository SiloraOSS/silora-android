# Silora fork preservation checklist

Updated November 6, 2026. This is the source/test map for active IDs in
[FORK_DIFFERENCES.md](../../../../FORK_DIFFERENCES.md), not a separate approval
list. Required behavior and status belong in that manifest. Recheck these
pointers against current code when integrating upstream; do not use an old
implementation or merge log to create a new product requirement.

For Kotlin paths below:

- Shared models/APIs: `shared/src/commonMain/kotlin/org/siloserver/silo/`.
- Android helpers: `android-shared/src/androidMain/kotlin/org/siloserver/silo/common/`.
- Phone UI: `androidApp/src/androidMain/kotlin/org/siloserver/silo/android/`.
- TV UI: `androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/`.

## HEVC level limits (`hevc-levels`)

Preserve **Ignore HEVC Level Limits** as an On/Off setting, enabled by default.
On omits HEVC level bounds in capability reports; Off reports the decoder's
levels. Other capability checks remain in force. This differs from Edrock's
numeric level picker and must not become a fixed 6.2 override.

Keep the integer key `player.hevc_level_override`: saved zero means Off,
positive legacy numeric levels mean On, and new writes use 0/1. Keep it
local-only and scoped to the profile. Follow `PlaybackSettingsKeys.kt`,
`PlayerSettingsStore.kt`, `AndroidPlayerSettingsStore.kt`,
`MediaCodecCapabilitiesProbe.kt`, and `IgnoreHevcLevelLimitsBinder.kt`, plus
both application startup bindings and settings screens.

The binder retains the last applied value and resubscribes after transient
`IOException`s with exponential delay capped at 30 seconds. Preserve that
recovery behavior from `ec68d4ecd`, including later preference/profile changes.

Diagnostics always retain the hardware's reported HEVC levels, independent of
this switch. Keep the cached `rawProbe()` path used by
`MediaCodecCapabilitiesProbe.diagnosticsSnapshot()` separate from `probe()`'s
playback/download adjustment. Matt approved this subset of edrock `c8cfcfc538`
on November 16, 2026; it does not adopt the numeric override or picker.
`MediaCodecCapabilitiesProbeDiagnosticsTest` exercises the real probe and
Android diagnostic adapter across on/off/on changes, preserving other decoder
bounds and playback evidence after diagnostic reads.

## Night Mode (`night-mode`)

Preserve the local per-server/profile preference, player and settings controls,
and compressor/limiter tuning. Disabled tuning rows must be visibly disabled
and skipped during TV focus navigation. Follow `NightModeParameters.kt`,
`AudioNormalizationController.kt`, `AudioNormalizationEffect.kt`, player factory
and service integration, settings stores, and phone/TV settings and player UI.

The effect follows the audio session and channel count, releases its resources,
and falls back on devices without `DynamicsProcessing`. While enabled, it uses
decoded PCM and blocks passthrough, offload, and tunneling; turning it off
restores current output preferences. Preserve the source attribution and
`THIRD_PARTY_NOTICES.md`. Use the observed sink path for audio delay, with the
existing Night Mode PCM fallback before that path is available. Read `docs/playback/night-mode.md` for behavior, but
verify referenced guard scripts exist before trying to run them: at this
snapshot it mentions `scripts/check-night-mode.py`, which is absent.

## TV display and subtitle sizing (`tv-display`, `tv-subtitle-size`)

- Keep separate **Match resolution** and **Match frame rate** settings and
  runtime handling in `DisplayModeSelector.kt`, `HdrDisplayController.kt`,
  `RefreshRateSelector.kt`, settings storage, and TV settings UI. Preserve
  upstream selection/tie-tolerance fixes as well as the fork controls.
- Keep TV's **Extra small** (`SubtitleFontSizePreset.XSmall`) option in the HUD
  and settings picker, persistence, previews, and renderer. Follow
  `SubtitleAppearance.kt`, `AndroidSubtitleTextSizePolicy.kt`, and
  `TvSubtitleAppearanceOptions.kt`. Keep the phone's minimum size unchanged.
- Preserve the TV playback-options panel's focus handling and the skip over
  disabled Night Mode controls. Review `TvPlayerHud.kt` and related settings
  controls when upstream changes focus or layout.

## TV input settings (`tv-remote`, `tv-keyboard`, `input-scope`)

Keep customizable long-press shortcuts and ordinary short-press behavior,
including contextual limits on actions such as Next Episode. Follow
`TvRemoteShortcutsStore.kt`, `TvRemoteShortcutPressHandler.kt`,
`TvRemoteShortcutsSettingsPane.kt`, and player integration.

Keep App/System search keyboard selection, System as the default, and optional
automatic keyboard opening (default off). Follow `TvSearchKeyboardStore.kt`,
`TvSearchKeyboard.kt`, `TvSearchScreen.kt`, `TvSearchTextEditing.kt`, and their
shell/settings integration. Keep upstream voice search integrated with those
keyboard controls, as described below.

`input-scope` is the storage detail for keyboard mode, automatic opening and
remote mappings, not a separate product feature. These values are local per
server/account/profile. `TvProfilePreferences.kt` migrates old TV-wide/profile
values only to the adopting account and responds to identity switches. Follow its account-token
integration in `AndroidTvModule.kt` and `AccountLocalData.kt`; retain the account
isolation/adoption cases in `TvProfileInputPreferencesTest.kt`.

## Requests-first search (`request-search-order`)

Phone and TV default **Show available to request first** to on. Matt requested
this default and a one-time reset of saved off values on November 17, 2026.
`RequestSearchPreferences.kt` migrates all saved `.requests_first` false values
when its store first opens after updating, including inactive identities, and
writes the installation marker in the same edit. Keep the stable migration
marker: subsequent launches/updates must preserve a viewer's later opt-out.
New/unset profiles use true; missing identities must not write unscoped values.

Keep device/server/account/profile isolation, reactive preference/profile
updates and the `AccountLocalData` cleanup registration. The installation
marker has no account token and survives account removal. Follow phone
`SearchScreen.kt`/`SearchResults.kt`/`SettingsPages.kt`, TV
`TvSearchScreen.kt`/`TvSearchKeyboardSettings.kt`/`TvCatalogGrid.kt`, and
`RequestSearchPreferencesTest`. Preserve server request gating, media filters,
library-duplicate suppression, each section's relevance order and TV focus
navigation for both on/off choices.

## TV voice search (superseded `tv-voice`)

Matt requested retiring the fork implementation on November 11, 2026 after
confirming upstream voice search on his Android 11 Shield. Follow upstream here;
do not restore `TvSearchVoiceController.kt`, `TvSearchVoiceOverlay.kt` or their
session tests from older fork commits or merge resolutions.

`TvVoiceSearch.kt` and `LegacyTvVoiceSearch.kt` now follow upstream unchanged,
including PR #945 (`712ddb547f945222aa45fef473bc873db516f492`, fixes #887).
Keep both manifest queries (`android.speech.action.RECOGNIZE_SPEECH` and
`android.speech.RecognitionService`), optional microphone hardware and upstream's
`RECORD_AUDIO` declaration without an SDK ceiling.

Preserve the `tv-keyboard` integration: close the selected keyboard before voice
launch, update its editable field/cursor on a nonempty spoken result, enforce
the normal query limit and submit library/request searches. Keep upstream's
availability-based mic visibility, unavailable message and in-flight
request-search deduplication. This integration does not require changes to the
upstream recognizer helpers or their permission-message resources.

## Google TV speech-service compatibility (superseded `tv-google-voice`)

Matt requested retiring this extension on November 15, 2026 because upstream
PR #945 supplies the same behavior: Google's selected activity provider
(`com.google.android.katniss`) uses `LegacyTvVoiceSearch.kt` on API 30 and later
when a recognition service is available. Other providers, older Android and
service-unavailable cases retain upstream's activity fallback. Microphone
permission is available on newer Android. The fork's permission-copy override
is removed along with its helper differences.

Compare both helper files and the manifest's speech declarations with the
pinned upstream tree. Follow upstream's lifecycle, dialog, cancellation,
permission/error handling and query delivery; do not restore the retired
controller/overlay or the former compatibility patch. The earlier Android 14
Streamer success and investigation limits remain in
`docs/maintenance/2026-11-11-streamer-voice-investigation.md`; they are historical
evidence, not validation of a later APK.

## Trailers (`trailers`)

Keep embedded YouTube playback, external fallback, and the detail-page Trailer
action. Follow `YoutubeTrailerActivity.kt`, `YoutubeTrailerLauncher.kt`, both
manifests, `TrailerRail.kt`/`primaryTrailer()`, phone `TrailersRail.kt` and detail
components, and `TvItemDetailScreen.kt`.

Preserve the HTTPS origin/referrer needed by embeds, upstream's external
handoff behavior, local-trailer playback, TV remote controls, close-on-end,
and the phone's More-menu placement when the action row is full. Keep Edrock's
attribution. Use an upstream equivalent if it preserves these behaviors.

## Home progress pages (`home-explore-removal`)

Matt requested edrock `d3d39cec329486198cf914e88bb0b608fa971926` on November 15,
2026. Follow `HomeDismissal.kt`, `SectionBrowseViewModel.kt`, `HomeViewModel.kt`,
phone/TV section-browse routes and screens, and both catalog grids. Carry the
originating Home row's `sectionType` through navigation; library rows and
unrelated sections do not gain a removal action.

Keep the existing dismissal anchors (`progress_updated_at` for Continue
Watching; series ID for Next Up), whole-show removal for episodes and
single-item removal for movies/books. The page removes cards immediately and
restores them on refusal only if the same load still owns the list. Retain the
pending-request guard, captured auth scope and profile-scoped Home notification
and refresh. Reuse upstream's equivalent if it supplies this behavior.

`SectionBrowseHomeRemovalTest` covers menu eligibility and anchor selection.
`SectionBrowseHomeRemovalIntegrationTest` covers the real catalog/dismissal
transports, rollback after refusal/reload, duplicate requests, profile changes,
Home refresh and audiobook isolation. Existing `HomeDismissal*` tests cover the
Home-row contract. TV focus and menu interaction still need device validation.

## Retired AI subtitle entries (`edrock-ai-subs-fixes`, `tv-live-ai-subtitles`)

Matt requested retiring both entries on November 17, 2026 to remove
fork-specific subtitle behavior as a troubleshooting variable. Follow official
upstream for AI creation/recovery, transcription planning, finished-track
inventory handling and phone/TV realtime/player integration. Do not restore
the retired patches during a merge or edrock review without an explicit
reactivation request. This retirement does not depend on upstream providing
equivalents for every former fix or live-cue feature.

`AiTranscribePlan.kt`, `LiveSubtitleOverlay.kt`, `PlaybackLiveSubtitles.kt`
and their fork-only tests were removed. Do not restore the cooldown/job-adoption
API/DI changes, server-ordinal AI audio list, live event handlers/overlays,
completed-event track injection, TV inventory-replan fallback or live/cancel
strings. Use the current upstream subtitle code and tests. Preserve the
separate active `tv-subtitle-size` entry and unrelated player differences.

The [retirement and restoration record](../../../../docs/maintenance/2026-11-17-ai-subtitles-retirement.md)
records the former behavior, original/v42 source commits and validation.
Pre-retirement Silora `a4742bb151958836a8f8e9b741a59c000792b461` contains the
complete former implementation and checklist; local recovery ref:
`refs/codex/retired-ai-subtitles/2026-11-17`.

## Up Next (`up-next-setting`, `up-next-fixes`)

Follow `NextUpPromptMode.kt`, `MarkerTimeline.kt`, `NextUpDismissal.kt`, both
player ViewModels, phone `PlayerNextUpScreen.kt`, TV `TvPlayerScreen.kt`, settings
stores, and phone/TV playback settings. `docs/playback/up-next.md` records the
two trigger modes, eligible-credit filtering, fallback timing, saved wire
values, and dismissal/end-countdown behavior. Preserve Time remaining → At end
for end-only prompts; do not restore the removed After playback ends choice.
Its unreleased `off` value uses Credits markers without migration, per Matt’s
November 8 clarification. Keep the next-item artwork after playback ends.

Retain profile/server/device trigger scope, existing timing overrides, startup
settings readiness, skip controls, manual TV action, and Watch Party transition
ownership. Reset dismissal/manual-card state for each item alongside upstream's
completion state.

Existing coverage includes `MarkerTimelineTest`, `NextUpPromptModeTest`,
`NextUpDismissalTest`, `AndroidPlayerSettingsStoreTest`, and the phone
`PlayerViewModelLoadOwnershipIntegrationTest`. Check TV host-task availability;
the repository disables its release host tests. Device playback requires a real
server/device run and cannot be inferred from source or unit tests.

## Identity and builds (`identity`, `release-tooling`)

Keep Silora's purple/charcoal artwork, launcher icons, TV banner, wordmarks,
startup assets, visible labels, diagnostics/client identity, and
`org.silora.app` in both app modules. Keep existing `org.siloserver.silo`
Kotlin packages and module namespaces; do not perform another namespace cut.
Keep `README-SILORA.md` and the link to it from the upstream-aligned README.

Preserve existing fork workflows, signing/version metadata, APK verification,
and Linux/macOS build helpers when resolving overlaps. Local signed builds
retain matching R8 mappings beside their APKs. Relevant files include
`scripts/build-local-release.py`, `scripts/fork-release.py`, `build.sh`,
`build-mac.sh`, `.github/workflows/fork-*.yml`, and `docs/fork-releases.md`.
Preserving these files does not authorize running a release or changing its
schedule, destinations, secrets, or publishing behavior.

Retain dependency verification and test compatibility for the active features.
For a difference outside these IDs or the current user request, establish its
intent before choosing whether to preserve it; do not approve it here.

## Merge maintenance (`fork-maintenance`)

`FORK_DIFFERENCES.md` holds active behavior and its intent references; this file
holds technical pointers. `AGENTS.md` links normal fork development to the
manifest, and `SKILL.md` explains upstream integration decisions. Update the
manifest with explicitly requested new fork work rather than inferring approval
from a source diff, test, or old instruction paragraph. Keep existing automation
schedules, publication permissions, and push boundaries unchanged.
