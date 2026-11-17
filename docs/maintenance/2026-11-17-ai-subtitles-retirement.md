# AI subtitle fork retirement — November 17, 2026

Silora retired `edrock-ai-subs-fixes` and `tv-live-ai-subtitles` at Matt's
request to remove fork-specific subtitle behavior as a troubleshooting
variable. Users had reported unexplained buffering with some subtitles, but
the cause was unconfirmed. The purpose is to align the AI subtitle paths with
official Android upstream. This record does not establish a buffering cause
or claim a playback fix.

## Pinned inputs and recovery

- Silora before retirement: `a4742bb151958836a8f8e9b741a59c000792b461`.
- Local recovery ref: `refs/codex/retired-ai-subtitles/2026-11-17`, pointing to
  that same pre-retirement commit. This local ref is not published.
- Official upstream repository: `https://github.com/Silo-Server/silo-android.git`.
- Incorporated upstream baseline: `0867eacb14b5ee7a8c46cc2b22e52d1799e599a7`.
  It is an ancestor of the Silora input. This retirement uses that incorporated
  revision, not a new upstream merge or a claim about the latest remote tip.
- Original AI fixes: `5536474b32b91cdf8bd8f215924c76d314c3a9cf`.
- Original TV live cues: `b812d6274413e9c64ac52cf4c6c1f04cea297d46`, adapted
  from edrock `20e85217c927e76fe34edd820608403f4847452b`.
- Last combined phone/TV subtitle update:
  `d99301d725a84ae1603e1335bc6e5766a970e98a`.
- Edrock v42 source snapshot: `c934aaf7ac0aef17881070c0a54ab4cdc56add36`
  on `feat/hevc-level-override-v42`.

The pre-retirement tree contains the complete implementation, tests and former
manifest/checklist requirements. Git history retains it without leaving a
second implementation in the app. Earlier import details remain in
[CHANGELOG.md](../../CHANGELOG.md) and the
[edrock tracking record](edrock-tracking.md).

## Former behavior

### `edrock-ai-subs-fixes` (formerly `ai-subtitles`)

The original patch and v42 follow-ups changed the existing AI subtitle feature:

- TV's From audio list used the playing file's server audio ordinals instead
  of the remuxed/transcoded player's group ordinal.
- Uncertain AI creates stayed fenced for 60 seconds. A repeated intent then
  listed jobs under the captured auth scope, adopted a matching running or
  completed job, and resent only when no matching job existed. Unreadable
  lists remained fenced.
- Phone and TV shared transcription planning for language aliases, regional
  tags, languages outside the display-name table, and untagged-audio hints.
- TV applied a finished inventory row from `subtitle_translation_completed`,
  with a five-second metadata-replan fallback when inventory events were
  missing. That fallback could refresh playback metadata; no connection to
  the reported buffering was established.

V42 source references include `37874ceaf`, `5dc3f0e10`, `e5d9b430e` and
`641a8993b`. The main implementation paths were:

- `shared/.../network/apiv2/SubtitleAiCreateV2Api.kt`, `SubtitleAiReadsV2Api.kt`
  and `di/NetworkModule.kt`.
- `android-shared/.../player/AiTranscribePlan.kt` and `ui/LanguageNames.kt`.
- Phone `AiTranslateSheet.kt`, `SubtitleAiSupport.kt` and `PlayerViewModel.kt`.
- TV `TvAiTranslateDialog.kt`, `TvPlayerViewModel.kt`, `TvPlayerScreen.kt`
  and `TvPlaybackRealtimeController.kt`.

### `tv-live-ai-subtitles`

The stable ID retained the feature's TV origin after phone support was added:

- Shared decoding and storage handled streamed AI lifecycle/cue events,
  cleaned markup, sorted/deduplicated cues, mapped source-file time and
  tracked dismissed jobs.
- A frame-ticked phone/TV overlay displayed cues while generation ran,
  respected subtitle appearance and positioning, and hid ordinary subtitles
  only while live cues covered the current position.
- First cues closed the AI sheet/dialog. Selecting another track or Off,
  cancelling, finishing or changing playback dismissed live lines and
  rejected late batches. A manual choice stayed selected on completion.
- Session/file/job guards and a 15-second completion cleanup backstop limited
  stale live state. TV displayed failed-cancel feedback.

V42 source references include `e5d9b430e`, `867431510`, `0c541431a`,
`cc25ac25d` and `5dc3f0e10`. The main implementation paths were:

- `shared/.../playback/PlaybackLiveSubtitles.kt` and its test.
- `android-shared/.../player/LiveSubtitleOverlay.kt`.
- Both player models, screens and realtime controllers, plus phone
  `PlayerOverlay.kt` and the three retired live/cancel strings in
  `android-shared/src/androidMain/res/values/strings_silora.xml`.

## Behavior after retirement

AI creation/read APIs, their DI constructor, both AI dialogs, phone AI
support, and both realtime controllers match the pinned upstream files.
Phone/TV subtitle model sections and the removed screen integrations follow
upstream too. Android ignores streamed live cues and uses the existing
persisted-job polling and finished-subtitle path. Upstream's uncertain-create
fence replaces the fork cooldown/adoption mechanism; the fork TV inventory
fallback and completed-event injection are removed.

The fork-only live overlay, cue model/decoder, transcription planner and their
feature-specific tests are removed. Existing upstream tests are restored
where the fork changed them.

`tv-subtitle-size` remains active: TV still offers Extra small, with its
existing appearance/persistence support. It is separate from these two AI
entries, so the entire subtitle subsystem is not byte-identical to upstream.
Other fork features remain outside this retirement.

## Restoring later

Reactivation requires Matt's request. Review the preserved requirements and
implementation against the upstream version in use at that time; restore
only the requested behavior and adapt it to current APIs and lifecycle rules.
The original imports and later merges changed player files shared with other
features. Reapply the requested subtitle behavior selectively rather than
replacing those files wholesale or reverting only the latest v42 update.

Inspect the preserved implementation with:

```sh
git show a4742bb151958836a8f8e9b741a59c000792b461:FORK_DIFFERENCES.md
git show d99301d725a84ae1603e1335bc6e5766a970e98a
git diff 0867eacb14b5ee7a8c46cc2b22e52d1799e599a7 \
  refs/codex/retired-ai-subtitles/2026-11-17 -- \
  shared/src/commonMain/kotlin/org/siloserver/silo/network/apiv2/SubtitleAiCreateV2Api.kt \
  shared/src/commonMain/kotlin/org/siloserver/silo/playback/PlaybackLiveSubtitles.kt \
  android-shared/src/androidMain/kotlin/org/siloserver/silo/common/player/AiTranscribePlan.kt \
  android-shared/src/androidMain/kotlin/org/siloserver/silo/common/player/LiveSubtitleOverlay.kt
```

The full pre-retirement SHA can replace the local ref in another clone.
Restore relevant tests and manifest/checklist status alongside code. Validate
phone and TV builds and subtitle ownership/lifecycle behavior, then test the
reported buffering scenario on an affected device/server before drawing a
performance conclusion.

## Validation and delivery

Validation passed with JDK 21 in 2m 50s:

| Test task | Cases | Failures/errors/skips |
| --- | --- | --- |
| `:shared:testReleaseUnitTest` (subtitle suites) | 240 | 0/0/0 |
| `:android-shared:testReleaseUnitTest` (subtitle/video player suites) | 231 | 0/0/0 |
| `:androidApp:testDebugUnitTest` (player suites) | 297 | 0/0/0 |
| `:androidTvApp:testDebugUnitTest` (player suites) | 510 | 0/0/0 |
| Total | 1,278 | 0/0/0 |

Both phone/TV release Kotlin compilations and full release lint passed.
Existing lint baselines were unchanged. Phone/TV host tests use debug because
the existing release host-test tasks are disabled; this did not build a debug APK.

```sh
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew \
  :shared:testReleaseUnitTest --tests '*Subtitle*' \
  :android-shared:testReleaseUnitTest --tests '*Subtitle*' --tests '*VideoPlayer*' \
  :androidApp:testDebugUnitTest --tests '*ui.screens.player.*' \
  :androidTvApp:testDebugUnitTest --tests '*ui.screens.player.*' \
  :androidApp:compileReleaseKotlinAndroid :androidTvApp:compileReleaseKotlinAndroid \
  :androidApp:lintRelease :androidTvApp:lintRelease --parallel --console=plain
```

Source checks confirmed byte-for-byte upstream equivalence for 11 restored
feature files and both complete player-model subtitle suites. All remaining
fork deltas in the 22 affected runtime/test/resource files are unchanged
subsets of the pre-retirement deltas. No retired helper/event/overlay/string
references remain in source. The Extra small size remains separately active.
Supply-chain, derived Silora strings and whitespace checks passed.

Updated the public feature overview to remove the live-cue claims, the
manifest to mark both IDs retired, and the checklist/edrock skill to prevent
unrequested reimports. The historical source tree and import records remain.
No APK was packaged or installed, and no device/server playback test was run.
This record accompanies the local retirement commit. No push, PR, release,
version bump or notification was performed.

Other clients: Apple, Roku and Web are N/A. This removes Android fork additions
and returns their paths to official Android behavior; it introduces no shared
server or cross-client contract. No parity PR or issue is required.
