# Silora fork preservation checklist

Source-reviewed on 2026-10-31 at `7d7b9bb72`. Recheck current source and history
on every merge. This is a map of known differences, not a fixed patch series
or a reason to resurrect code removed from the active branch.

For Kotlin paths below:

- Shared models/APIs: `shared/src/commonMain/kotlin/org/siloserver/silo/`.
- Android helpers: `android-shared/src/androidMain/kotlin/org/siloserver/silo/common/`.
- Phone UI: `androidApp/src/androidMain/kotlin/org/siloserver/silo/android/`.
- TV UI: `androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/`.

## HEVC level limits, phone and TV

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

## Night Mode, phone and TV

Preserve the local per-server/profile preference, player and settings controls,
and compressor/limiter tuning. Disabled tuning rows must be visibly disabled
and skipped during TV focus navigation. Follow `NightModeParameters.kt`,
`AudioNormalizationController.kt`, `AudioNormalizationEffect.kt`, player factory
and service integration, settings stores, and phone/TV settings and player UI.

The effect follows the audio session and channel count, releases its resources,
and falls back on devices without `DynamicsProcessing`. While enabled, it uses
decoded PCM and blocks passthrough, offload, and tunneling; turning it off
restores current output preferences. Preserve the source attribution and
`THIRD_PARTY_NOTICES.md`. Read `docs/playback/night-mode.md` for behavior, but
verify referenced guard scripts exist before trying to run them: at this
snapshot it mentions `scripts/check-night-mode.py`, which is absent.

## TV display matching and subtitle sizing

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

## TV remote, keyboard, and voice controls

Keep customizable long-press shortcuts and ordinary short-press behavior,
including contextual limits on actions such as Next Episode. Follow
`TvRemoteShortcutsStore.kt`, `TvRemoteShortcutPressHandler.kt`,
`TvRemoteShortcutsSettingsPane.kt`, and player integration.

Keep App/System search keyboard selection, System as the default, optional
automatic keyboard opening (default off), and voice search/controller/overlay
integration. Follow `TvSearchKeyboardStore.kt`, `TvSearchKeyboard.kt`,
`TvSearchScreen.kt`, `TvSearchTextEditing.kt`, `TvSearchVoiceController.kt`, and
`TvSearchVoiceOverlay.kt` plus shell/settings integration.

Keyboard and remote preferences are local per-server/profile values.
`TvProfilePreferences.kt` migrates old TV-wide values to the first active
profile only and responds to profile switches. Preserve that isolation.

## Trailers, phone and TV

Keep embedded YouTube playback, external fallback, and the detail-page Trailer
action. Follow `YoutubeTrailerActivity.kt`, `YoutubeTrailerLauncher.kt`, both
manifests, `TrailerRail.kt`/`primaryTrailer()`, phone `TrailersRail.kt` and detail
components, and `TvItemDetailScreen.kt`.

Preserve the HTTPS origin/referrer needed by embeds, upstream's external
handoff behavior, local-trailer playback, TV remote controls, close-on-end,
and the phone's More-menu placement when the action row is full. Keep Edrock's
attribution. Use an upstream equivalent if it preserves these behaviors.

## AI subtitle fixes

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

## Identity, documentation, and build tooling

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

Retain dependency-verification metadata and existing build/test fixes unless
the incoming implementation demonstrably supersedes them. Use the current
diff and tests to find less prominent fixes beyond this checklist.
