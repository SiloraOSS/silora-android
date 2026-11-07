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
shell/settings integration. Preserve the in-app voice integration described
below alongside those keyboard controls.

`input-scope` is the storage detail for keyboard mode, automatic opening and
remote mappings, not a separate product feature. These values are local per
server/account/profile. `TvProfilePreferences.kt` migrates old TV-wide/profile
values only to the adopting account and responds to identity switches. Follow its account-token
integration in `AndroidTvModule.kt` and `AccountLocalData.kt`; retain the account
isolation/adoption cases in `TvProfileInputPreferencesTest.kt`.

## TV voice search (`tv-voice`)

Retain `TvSearchVoiceController.kt`, `TvSearchVoiceOverlay.kt` and their
`TvSearchScreen.kt` integration while upstream PR #830 is deferred. The local
`SpeechRecognizer` delivers final text directly to the editable field and the
normal search submission path. Partial results stay in the overlay. Preserve
the runtime `RECORD_AUDIO` request, denial/settings flow, optional microphone
feature and `android.speech.RecognitionService` package-visibility query.

Keep session cancellation on pause/stop/disposal (except the permission-dialog
pause), late-callback fencing, timeouts, empty-result handling, query length
limits, keyboard closure, overlay Back ownership and microphone focus return.
The eight existing `TvSearchVoiceControllerTest` cases cover the session logic;
they do not prove microphone capture on a real TV. Retain upstream’s identical
in-flight request-search deduplication when voice submits immediately.

The inventory records Matt’s November 6 fallback decision. Do not re-adopt the
recognizer-activity helper merely because it is present upstream: revisit it
after an official build completes a spoken search on the affected device.

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

## Up Next (`up-next-setting`, `up-next-fixes`)

Follow `NextUpPromptMode.kt`, `MarkerTimeline.kt`, `NextUpDismissal.kt`, both
player ViewModels, phone `PlayerNextUpScreen.kt`, TV `TvPlayerScreen.kt`, settings
stores, and phone/TV playback settings. `docs/playback/up-next.md` records the
three trigger modes, eligible-credit filtering, fallback timing, saved wire
values, and dismissal/end-countdown behavior.

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
