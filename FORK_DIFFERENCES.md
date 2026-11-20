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

On November 15, 2026, Matt renamed `ai-subtitles` to
`edrock-ai-subs-fixes` to identify the imported fixes and their source. Older
commits may use the former ID. Both this entry and `tv-live-ai-subtitles` were
retired on November 17, 2026; their stable IDs remain in the history below.

## Active differences

| ID | Behavior and purpose | Implementation references |
| --- | --- | --- |
| `identity` | Silora's purple/charcoal identity, launcher/startup artwork, visible labels and `org.silora.app` distinguish this fork from official Silo. Retain upstream Kotlin namespaces and separate phone/TV module namespaces. | `5b8503ea5`, `6365ccaa8`; `scripts/sync-silora-strings.py`, distinct `silora_` resources and `docs/i18n.md` preserve app identity alongside the upstream translation catalog |
| `hevc-levels` | **Ignore HEVC Level Limits** defaults on to allow a decode attempt despite level tags. Omit only HEVC level bounds in playback/download capability reports; retain other capability checks, saved integer-key compatibility, Off behavior and read-failure recovery. Diagnostics always retain the device's reported HEVC levels, independent of this switch. Matt requested this diagnostic subset from edrock v42 on November 16, 2026. | `9eda2ac76`, `ec68d4ecd`; edrock `c8cfcfc538` (raw diagnostics only); `MediaCodecCapabilitiesProbe.kt`, `MediaCodecCapabilitiesProbeDiagnosticsTest` |
| `night-mode` | Phone/TV Night Mode reduces loud peaks through the compressor/limiter and retains tuning controls. Preserve preference scope, PCM processing, resource cleanup and disabled-control focus behavior. | `ddc5961b6`, `8f5435e58`, `9ee5c033a`, `bbc7b4117` |
| `tv-display` | Independent resolution and frame-rate matching let viewers choose either adjustment. Preserve the untouched display dimension when only one switch is enabled, alongside upstream selection fixes. | `a4903819f` |
| `tv-subtitle-size` | TV has an Extra small subtitle choice. Preserve local persistence, server-compatible Small wire fallback and existing phone sizes. | `eae382b4b` |
| `tv-remote` | Customizable long presses provide playback shortcuts while ordinary short presses and contextual action limits remain intact. Preserve playback-options focus handling; a Stats shortcut opens playback information, not an admin dashboard. | `04ef9c483`, `64a6d3b14` |
| `tv-keyboard` | Search offers App/System keyboards with System as default and automatic opening off by default. Preserve physical typing, explicit keyboard opening, cursor state, clearing and focus handoffs. | `1d8fc7a5f`, `a42991501`, `660758013`, `bcf487826` |
| `request-search-order` | Phone Interface and TV General settings offer **Show available to request first**, default on. Matt selected the requests-first option on November 15, 2026, then requested the default-on behavior and a one-time reset of existing disabled choices on November 17. When the shared preference store first opens after that update, enable all saved disabled choices across servers/accounts/profiles on this installation, including inactive identities; record the migration alongside those writes. Users may turn it off afterward, and later launches/updates must preserve that new choice. When on, search shows Available to request before library titles and people; TV search submission and filter navigation follow that order. Save choices per device/server/account/profile and remove them with account-local data. Preserve server request gating, media filters, library-duplicate suppression and each search's existing result order. | `RequestSearchPreferences.kt` and `RequestSearchPreferencesTest`; phone `SearchScreen.kt`/`SearchResults.kt`/`SettingsPages.kt`, TV `TvSearchScreen.kt`/`TvSearchKeyboardSettings.kt`/`TvCatalogGrid.kt`. See the November 17 changelog entry for default/migration validation. The earlier signed Shield installation predates this default change; no new device validation is implied. Apple, Roku and Web parity issues remain deferred until a PR opens, as required by `AGENTS.md`. |
| `input-scope` | Supporting storage for `tv-keyboard` and `tv-remote`: save only App/System keyboard choice, automatic keyboard opening, and long-press mappings separately per server/login/profile. This adds no menu or standalone setting. Preserve migration, switching and account-removal cleanup. | `a19729d21`; account integration in `73c46323f` |
| `home-explore-removal` | Phone and TV offer Remove from Continue Watching / Next Up on the full **Explore all** pages opened from Home progress rows, including In Progress. Matt requested edrock's patch on November 15, 2026 to make removal available beyond the Home row. Preserve immediate removal, whole-show dismissal for episodes, restoration on server refusal, captured profile ownership and Home refresh after success. Library rows and unrelated Home sections keep their existing menus. | Edrock [`d3d39cec329486198cf914e88bb0b608fa971926`](https://github.com/edrock200/silo-android/commit/d3d39cec329486198cf914e88bb0b608fa971926); `HomeDismissal.kt`, `SectionBrowseViewModel.kt`, `HomeViewModel.kt`, phone/TV section routes and catalog grids; `SectionBrowseHomeRemovalTest`, `SectionBrowseHomeRemovalIntegrationTest` |
| `trailers` | Embedded YouTube previews, external fallback and TV controls keep trailers accessible from movie/series details. Prefer local trailers and retain attribution. Matt requested edrock v42 updates on November 15, 2026: preserve startup/buffering fallback, foreground-only handoff, playback-state screen-on handling, guarded remote controls/JavaScript bridge, configuration-change continuity and translated phone/TV labels. | `bac06a7c7`; edrock `12a80264b`, `863f52f2f`, `f653570cd`, `b1aedaaab`; `YoutubeTrailerActivity.kt`, both manifests and detail action views |
| `up-next-setting` | **Up Next Trigger** offers Credits markers and Time remaining. **Time remaining → At end** waits until playback finishes; Matt requested removing the duplicate After playback ends choice on November 8, 2026. He confirmed that choice had not shipped, so no migration is needed; its old `off` value uses the default Credits markers. Preserve local trigger scope, timing fallback, normal end screen, autoplay and manual TV action. Phone/TV preview panes show live video during playback, then the next item's thumbnail after the video ends, with spoiler-safe series/season artwork or a standard placeholder as fallback. Matt approved the artwork behavior on November 7 and explicitly retained it during the November 8 simplification. | `eb13f7033`, `a10490adc`; `NextUpPromptMode.kt`, `NextUpArtwork.kt`, phone/TV Up Next views and player models |
| `up-next-fixes` | Eligible credits start in the final quarter; otherwise use timing. Keep Watching allows one guarded end countdown; cancellation preserves the end screen. Matt explicitly requested merging PR #2 after reviewing this behavior. | PR #2: `a7dce2fc6`, `7bd09d20e`, `ac56376af` |
| `credits-auto-skip` | **Skip Credits** in phone/TV Playback settings offers Use profile setting, Never (no credits skip button), Ask to skip (show a manual button), and Skip automatically (seek to each credits segment's end once per playback), in that order, without option descriptions. The explicit choice stays local to the profile/account/server/device. Use profile setting clears the local mode and any device override of the server boolean; the profile boolean maps true to Skip automatically and false to Ask to skip. Preserve existing saved modes and wire values. Warn when automatic skipping is active that incorrectly marked episodes may skip content or cause playback issues (use at your own risk). Preserve scenes between segments and allow rewinds. Automatic skips stay inactive during pauses, loading, scrubbing, content handoffs and Watch Party playback; automatic mode offers a room-gated manual button in Watch Party. Matt requested the feature, warning and local control on November 7, 2026, then profile inheritance, labels/order matching Skip Intros and removal of descriptions on November 8. | `CreditsAutoSkipController.kt` and focused tests; `CreditsSkipMode.kt`, phone/TV player observers and Playback settings; `AndroidPlayerSettingsStore.kt` and credits persistence/reset tests |
| `admin-console` | Phone and TV offer an optional **Admin** entry in the profile menu for administrator accounts, with dashboard/now playing, request moderation, tasks/scans, logs and generic forms from the bundled server admin API. The administrator's primary profile has a **Show Admin in profile menu** setting (default off), saved locally per server/account/profile; hiding the menu entry keeps the setting available. The server enforces administrator and primary-profile access; ordinary user Requests screens retain their existing scope. Matt explicitly requested cherry-picking edrock200’s complete admin range on November 8, 2026, overriding upstream’s unexposed admin surfaces. Matt requested the missing edrock v42 Admin updates on November 15, 2026 and the v49 Admin updates on November 20. Now Playing accepts the current and older server episode-title fields. The bundled Admin contract includes plugin capabilities and provisioning status. Preserve supported-JSON operation filtering, generic mutation confirmations, explicit form clearing and credential masking, version-tag/command retry handling, list ownership and pagination, TV focus and API contract checks. Menu-access retries cache only within the complete server/account/profile identity and discard stale replies; preserve the default-off visibility preference and server-enforced permissions. | [Source range](https://github.com/edrock200/silo-android/compare/3cb8886ed5b5cb96f3140b8d7786d323143f2814%5E...3792ac473b58d283dc5bdd67dc05ceccb638f084); [implementation and maintenance](docs/admin-console.md); v42 `c934aaf7a`, v49 `f640a73de`; `AdminPlaybackSessionTest`; `AdminEngineTest`, `AdminBundledSpecTest`, `AdminHandBuiltContractTest`, `AdminAccessTest`, `AdminPaginationTest` |
| `release-tooling` | Matt requested disabling the inherited Android Builds push/PR workflow on November 10, 2026; its GitHub state is `disabled_manually`. Keep the separate Silora release workflows and their required checks. Preserve persistent signed phone/TV builds, version metadata, APK verification, matching R8 mappings, release workflows and configured notification behavior. Matt requested a per-release choice of GitHub compilation or publishing the exact verified PC APKs on November 8, 2026; retain both routes, the shared release lock and rejection of stale builds across all publication routes. Matt also requested `./helper.sh` to run full local lint/tests and publish that invocation’s exact build without entering its dated path. The local publisher waits briefly for a newly created draft to appear in GitHub’s release list, retrying only missing reads without repeating creation. Matt requested recovery from dependency rate limits on November 17, 2026 and broader transient Maven failures on November 19. Automation Release's Gradle test and lint/build steps wait two minutes before retrying transient HTTP failures and allow three attempts total. A missing module with a Maven Central search result and an exact version in the verification metadata retries with a dependency refresh. Other failures stop immediately. The repository skill's `--release-only` mode builds the current committed branch without merging upstream, sends staff progress through the existing webhook, and pauses for approval before pushing or publishing the verified local APKs. This entry does not authorize publishing, messaging, changing schedules/destinations or exposing signing material. | `bf8304d5b`, `5f68d3c69`, `74ea34c8a`, `8005d02c1`, `7d7b9bb72`, `2b958c5c2`, `198f651f4`; `release.sh`, `scripts/local-release.py`, `fork-local-release.yml`, `test-local-release.py`, `scripts/release-order.py`, `helper.sh`, `scripts/test-build-and-publish.py`, `scripts/gradle-with-retry.py`, `scripts/test-gradle-with-retry.py`, `.github/workflows/fork-automation-release.yml` |
| `fork-maintenance` | Maintain this reviewed inventory and the upstream-merge skill. Matt requested the optional `silora-edrock-sync` helper on November 16, 2026 for maintenance of adopted edrock features and review of occasional new candidates. Review is read-only; requested imports use Silora's documented behavior, validate changes and create local commits unless instructed otherwise. Use the latest published release branch unless Matt names another source. Edrock sync is independent of upstream merges and releases. Keep intended behavior here and snapshot/import history in `docs/maintenance/edrock-tracking.md`. Explain meaningful conflict decisions by feature ID; follow upstream outside intentional differences and do not treat inherited instructions as proof of a user customization. | November 6 and November 16, 2026 authorization in this chat; `AGENTS.md`, `.agents/skills/silora-upstream-merge/SKILL.md`, `.agents/skills/silora-edrock-sync/SKILL.md`, `docs/maintenance/edrock-tracking.md` |

## Retired differences

Matt requested retiring both subtitle entries on November 17, 2026 to remove
fork-specific subtitle behavior as a troubleshooting variable. Subtitle-related
buffering had been reported; the cause was unconfirmed. This is an explicit
retirement in favor of upstream, not a claim that upstream supplies every
retired behavior or that the retirement fixes buffering.

AI subtitle creation, transcription planning, finished-track handling and
realtime events now follow the official upstream version incorporated into
Silora. Do not preserve or reimport these patches during upstream merges or
edrock maintenance unless Matt explicitly requests reactivation. The separate
`tv-subtitle-size` entry remains active.

| ID | Former behavior | History and recovery |
| --- | --- | --- |
| `edrock-ai-subs-fixes` | Server audio ordinals for TV transcription, uncertain-create cooldown/job adoption, language-aware phone/TV transcription planning, and finished-track event/fallback handling. Formerly `ai-subtitles`. | Original `5536474b3`; v42 update `d99301d725a84ae1603e1335bc6e5766a970e98a`; [retirement and restoration record](docs/maintenance/2026-11-17-ai-subtitles-retirement.md). |
| `tv-live-ai-subtitles` | Streamed AI translation/transcription cues on phone and TV while generation ran, with appearance, subtitle visibility and lifecycle/selection guards. | Original `b812d6274` from edrock `20e85217c`; v42 update `d99301d725a84ae1603e1335bc6e5766a970e98a`; [retirement and restoration record](docs/maintenance/2026-11-17-ai-subtitles-retirement.md). |

## Superseded differences

| ID | Replacement and reason | Implementation references |
| --- | --- | --- |
| `tv-voice` | Superseded on November 11, 2026 at Matt's request after he confirmed upstream voice search on his Android 11 Shield. Use upstream's recognizer helpers, including the Google TV fix that now supersedes `tv-google-voice`. The fork controller, overlay and session tests are removed. Keep spoken-query submission integrated with the App/System keyboard controls. | Upstream PR #830 (`4fb8f1b59`), PR #860 (`484b55da5`) and translated helpers from PR #869 (`59f15e534`); `TvVoiceSearch.kt`, `LegacyTvVoiceSearch.kt`, `TvSearchScreen.kt` and the TV manifest |
| `tv-google-voice` | Superseded on November 15, 2026 at Matt's request by upstream PR #945 (`712ddb547f945222aa45fef473bc873db516f492`, fixes #887). Upstream now uses its direct recognition-service helper for Google's TV recognizer on Android 11 and later when the service is available, with `RECORD_AUDIO` available on newer Android. This replaces Silora's identical compatibility extension and permission-copy override. Follow upstream's voice helpers and resources unchanged; keep the separate `tv-keyboard` integration. | `TvVoiceSearch.kt` and `LegacyTvVoiceSearch.kt` match upstream exactly; TV manifest voice declarations match upstream. Source equivalence, TV host tests and release lint/compilation validate integration; no fresh device voice test was performed. |

The November 6 fallback and November 9/10 merge decisions are historical.
Matt confirmed upstream voice search on his Android 11 Shield on November 11.
A later check that day reproduced the unchanged upstream activity's failure on
his Android 14 Google TV Streamer. Silora extended upstream's direct-service
path to Google on Android 11 and later, and Matt confirmed spoken-query entry
and results on the Streamer. Upstream PR #945 now supplies that same behavior;
neither voice entry above remains an active fork difference.

Both upstream helpers, including dialog, cancellation, permission/error handling,
provider fallback and query delivery, are maintained upstream. The manifest keeps
recognition-activity and service queries, optional microphone hardware and
`RECORD_AUDIO` without an SDK ceiling. App/System keyboard ownership and spoken
query submission remain part of `tv-keyboard`, not a voice-helper customization.
Do not restore the retired fork controller/overlay or compatibility patch.
Earlier evidence remains in `CHANGELOG.md`, the
[November 6 audit](docs/maintenance/2026-11-06-voice-and-merge-audit.md) and the
[November 11 device investigation](docs/maintenance/2026-11-11-streamer-voice-investigation.md).

Requests navigation has no fork override and must not be reintroduced from old
merge logs or instruction text.
