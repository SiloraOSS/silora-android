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

## TV voice search (superseded `tv-voice`)

Matt requested retiring the fork implementation on November 11, 2026 after
confirming upstream voice search on his Android 11 Shield. Follow upstream here;
do not restore `TvSearchVoiceController.kt`, `TvSearchVoiceOverlay.kt` or their
session tests from older fork commits or merge resolutions.

`TvVoiceSearch.kt` and `LegacyTvVoiceSearch.kt` come from upstream `09f43a215`:
PR #830's recognizer activity, PR #860's Google service fallback and PR #869's
translated prompts/errors. Keep both manifest queries
(`android.speech.action.RECOGNIZE_SPEECH` and `android.speech.RecognitionService`)
and optional microphone hardware. The active `tv-google-voice` extension below
widens the Google service path and microphone permission to newer Android.

Preserve the `tv-keyboard` integration: close the selected keyboard before voice
launch, update its editable field/cursor on a nonempty spoken result, enforce
the normal query limit and submit library/request searches. Keep upstream's
availability-based mic visibility, unavailable message and in-flight
request-search deduplication. The November 6 failure and November 9/10 deferral
records are historical.

The later November 11 device check reproduced failure in the unchanged upstream
activity on Matt's Android 14 Streamer. The active `tv-google-voice` compatibility
change below addresses that path with upstream's service helper.

## Google TV speech-service compatibility (`tv-google-voice`)

Matt approved extending upstream's service helper on November 11, 2026 and
confirmed successful spoken-query entry and results on the Android 14 Streamer.
For Google's selected activity provider (`com.google.android.katniss`) on API 30
and later, use `LegacyTvVoiceSearch.kt` when a recognition service is available.
Keep the upstream activity fallback for other providers, older Android and
service-unavailable cases. The TV manifest must make `RECORD_AUDIO` available
on newer Android so upstream's runtime permission request works there.

Preserve upstream's recognition/session lifecycle and query submission, plus
the existing Silora translation for the microphone-permission message. This is
a small compatibility extension to upstream's helper, not a restoration of the
retired fork controller/overlay. Keep App/System keyboard integration and
in-flight request-search deduplication. Retire this extension when upstream
supplies the same behavior. Evidence and limits are in
`docs/maintenance/2026-11-11-streamer-voice-investigation.md`; the Shield's existing
API-30 path remains selected, but no fresh Shield device test was performed.

## Trailers (`trailers`)

Keep embedded YouTube playback, external fallback, and the detail-page Trailer
action. Follow `YoutubeTrailerActivity.kt`, `YoutubeTrailerLauncher.kt`, both
manifests, `TrailerRail.kt`/`primaryTrailer()`, phone `TrailersRail.kt` and detail
components, and `TvItemDetailScreen.kt`.

Preserve the HTTPS origin/referrer needed by embeds, upstream's external
handoff behavior, local-trailer playback, TV remote controls, close-on-end,
and the phone's More-menu placement when the action row is full. Keep Edrock's
attribution. Use an upstream equivalent if it preserves these behaviors.

## AI subtitles (`ai-subtitles`)

Review `5536474b3` and current code before resolving these files. Prefer a
verified upstream fix when it makes the local workaround unnecessary.

- TV **From audio** uses the playing file's server audio ordinals, not the
  remuxed/transcoded ExoPlayer group ordinal. Follow
  `TvPlayerViewModel.aiAudioSources()`, `TvAiTranslateDialog.kt`, and
  `TvPlayerScreen.kt`.
- `SubtitleAiCreateV2Api.kt` lifts an uncertain-create fence after 60 seconds
  so a lost response does not block requests until app restart. Preserve
  ownership/concurrency guards and upstream's duplicate-job semantics.
- TV AI completion replans after five seconds if the new subtitle row is
  still absent. Follow `TvPlayerViewModel.refreshSubtitles()` and preserve
  session/ownership protections.
- Phone `SubtitleAiSupport.transcribeKindFor()` normalizes language aliases
  such as `eng`/`en` and avoids unnecessary same-language translation.
  Unknown language codes must not silently count as English.

## Live AI subtitles on TV (`tv-live-ai-subtitles`)

Matt adopted edrock200’s `20e85217c927e76fe34edd820608403f4847452b` as local
`b812d6274` and requested recording it on November 8, 2026. This feature is
separate from the four `ai-subtitles` workarounds above. Retire it when a
verified upstream equivalent supplies the same live-subtitle behavior.

- `shared/.../playback/PlaybackLiveSubtitles.kt` decodes
  `subtitle_translation_started`, `subtitle_translation_cues`,
  `subtitle_translation_completed` and `subtitle_translation_failed` events.
  `LiveSubtitleTrack` sorts/deduplicates batches and looks up cues in source-file
  seconds. `PlaybackLiveSubtitlesTest` covers the decoder and cue-track logic.
- `TvPlaybackRealtimeController` routes the decoded events to
  `TvPlayerViewModel.applyLiveSubtitleEvent`. Keep its session/file/active-job
  checks, `liveSubtitle` state and source-time mapping. The first nonempty cue
  batch closes the AI dialog.
- `TvLiveSubtitleOverlay.kt` draws current cues, displays “Translating subtitles…”
  before any arrive, keeps lines clear of transport controls and hides under the HUD.
  `TvPlayerScreen.kt` hides Media3’s subtitle view while the live track is active
  and draws the overlay outside picture-in-picture.
- The AI job poll’s existing finished-track refresh/selection path takes over
  on completion; completion, failure and cancellation clear the live overlay
  through `clearLiveSubtitle`. A completed realtime event also schedules a
  15-second fallback clear. Preserve these handoff/cleanup hooks during merges.

The decoder is shared, but live display is TV-only; the phone has no live
subtitle overlay. It requires server realtime events. Decoder tests do not
establish on-device cue timing or finished-track handoff.

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
