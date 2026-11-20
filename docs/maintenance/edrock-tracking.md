# Edrock review and import history

This record tracks reviewed edrock snapshots, imported patches, adaptations and
skipped or deferred work. [FORK_DIFFERENCES.md](../../FORK_DIFFERENCES.md) remains
the authority for intended Silora behavior. This record does not approve a new
feature or make edrock updates a prerequisite for an upstream merge or release.

Use the [silora-edrock-sync skill](../../.agents/skills/silora-edrock-sync/SKILL.md)
for a requested review or import. Its review mode leaves this file untouched;
report the review record in the response unless saving it is also requested.

## Reviewed snapshot: v42, November 15, 2026

Seeded on November 16 from the completed source review, existing changelog and
verified local Git objects. Creating this record did not check edrock's remote
for a newer release or repeat device validation.

- Repository: `https://github.com/edrock200/silo-android.git`.
- Branch: `feat/hevc-level-override-v42`.
- Full source SHA: `c934aaf7ac0aef17881070c0a54ab4cdc56add36`.
- Official upstream comparison: `40d34e6d9d0c7f3aae82787ece12eb3093ccf6e7`.
- Full-branch review compared all 101 non-merge fork commits outside that
  upstream snapshot. All of that upstream snapshot was included in v42.
- The review used Silora `2e491b04e14cd02fcfbd317fadfcd387f15168ba`; the
  subsequently requested Home removal import is recorded below.

### Adopted features

These historical rows describe coverage at the pinned v42 snapshot, including
adaptations.
The two subtitle entries were retired on November 17, 2026; see the retirement
record below and the current manifest before considering another import.
They do not claim that whole feature files match edrock or cover later releases.
Detailed source-commit lists and validation are in [CHANGELOG.md](../../CHANGELOG.md).

| Feature ID | Coverage and Silora destination | Adaptations or preserved behavior |
| --- | --- | --- |
| `admin-console` | Missing v42 Admin updates after edrock `8b75007d7`, through `38cbed941`, in Silora `6b4355aa8ed933451fd83f6be9fb12d7c743c646`. | Retained the default-off menu preference and server/primary-profile gates. Access caching uses the complete captured auth scope rather than profile ID alone; retained Silora pagination and stale-reply guards. |
| `trailers` | V42 follow-ups `12a80264b`, `863f52f2f`, `f653570cd`, `b1aedaaab` in Silora `6b4355aa8ed933451fd83f6be9fb12d7c743c646`. | Kept Silora identity, resource conventions and existing local-trailer behavior. |
| `edrock-ai-subs-fixes` | Requested v42 subtitle updates, including `37874ceaf`, `5dc3f0e10`, `e5d9b430e` and `641a8993b`, in Silora `d99301d725a84ae1603e1335bc6e5766a970e98a`. | Preserved auth/session ownership, audio ordinals, uncertain-job adoption and finished-track handling. The former `ai-subtitles` ID was renamed in `2e491b04e14cd02fcfbd317fadfcd387f15168ba`. |
| `tv-live-ai-subtitles` | V42 phone/TV updates in the same Silora subtitle commit `d99301d725a84ae1603e1335bc6e5766a970e98a`. | Retained the stable ID despite expanded phone support. Waiting/cancel resources use Silora names; cue cleanup, subtitle selection and lifecycle guards remain active. |
| `home-explore-removal` | Edrock `d3d39cec329486198cf914e88bb0b608fa971926` in Silora `13ce47d05c98de777b20e27c14ac598df7c828ed`. | Added seven integration tests for the imported behavior. Restricted removal to Home progress pages and preserved profile ownership, rollback and Home refresh. |

### Other changes reviewed

These are review dispositions, not permanent prohibitions. Reassess them if
edrock changes the implementation, upstream supplies an equivalent, or Matt
requests a different choice. Preserve current manifest requirements meanwhile.

| Source commit or group | Disposition and reason |
| --- | --- |
| `c8cfcfc538e1684e2c947bc35a9d2e3c1e599e4b` | Main playback fix does not apply: Silora clears HEVC level bounds rather than appending numeric levels. The raw-hardware diagnostic subset was recommended as an optional adaptation and remains deferred, not imported. |
| `9d5b4632ff5db388fe61117ee13d58e52d9a2078` | Skipped the numeric HEVC picker update. Silora's **Ignore HEVC Level Limits** switch takes priority. |
| `1f4bf22b1d0b08f6a14b66c1d9354a9d925c5e9a` and `4da6dc05fed4d134f078ac5c404468b729e8903b` | Deferred the proactive Search-entry microphone permission prompt. It targets system-keyboard voice typing; no corresponding device failure was reproduced in this audit. Silora already requests permission for its dedicated mic action. |
| `abac67b21728f46e0d94eaa608576a333942e1a3` | Manifest deduplication is already covered. The dedicated voice recognizer helpers matched Silora and official upstream at this snapshot; this was not another missing Google TV recognizer fix. |
| Fork release skill, versioning, publishing, README and build-environment notes | Skipped edrock-specific policy and environment instructions. Silora retains its own signing, release workflows, identity and publication scope. |

## Imported: raw HEVC decoder diagnostics, November 16, 2026

Matt approved the preceding review's recommended diagnostic adaptation. This
entry covers only the raw-diagnostics subset of `hevc-levels`; the whole-branch
review checkpoint and other feature coverage above remain unchanged.

- Source repository: `https://github.com/edrock200/silo-android.git`.
- Source branch: `feat/hevc-level-override-v42`.
- Source snapshot: `c934aaf7ac0aef17881070c0a54ab4cdc56add36`.
- Source commit: `c8cfcfc538e1684e2c947bc35a9d2e3c1e599e4b`, only the cached
  raw-probe extraction and diagnostic snapshot call; its numeric override,
  hardware-level picker helper and capability-evidence comment were excluded.
- Source upstream base and Silora's incorporated upstream:
  `40d34e6d9d0c7f3aae82787ece12eb3093ccf6e7`.
- Silora input: `6c82bcc71a216a9227e3b5901e9bab0862c7497a` on `main`.
- Official upstream comparison: `0867eacb14b5ee7a8c46cc2b22e52d1799e599a7`.
  Its ten newer commits were outside this import.
- Publication was verified live on November 16 in the preceding read-only
  review: `apk-builds` at `452b0183d493e8885158182c70ad8ad21847beca` identifies
  v42/build 41 and the source snapshot above. This import uses those pinned
  inputs and local full Git objects, rather than claiming another release check.
- Recovery: `refs/codex/edrock-hevc-diagnostics/20261117T073123Z`, pointing to
  the Silora input above.

**Adapted:** `MediaCodecCapabilitiesProbe.diagnosticsSnapshot()` reads the
cached hardware probe before the HEVC setting is applied. Diagnostic reports
retain the decoder's real levels with the switch either on or off. Playback
and download capability reports retain Silora's default-on Ignore HEVC Level
Limits behavior, other decoder bounds, saved values, profile scope and
settings-read recovery. Updated the binder comment and preservation documents
to distinguish hardware diagnostics from effective playback capabilities.

The decoder-level settings label and proactive system-keyboard microphone
prompt remain deferred. The numeric picker remains skipped. No other edrock
feature, release tooling or official upstream code was imported.

**Validation:** 168 affected android-shared release tests passed with zero
failures, errors or skips. Phone/TV release Kotlin compilation and full release
lint passed under JDK 21 with existing lint baselines unchanged. The combined
run completed in 1m 58s. The new Robolectric regression failed against the old implementation:
diagnostics reported an empty HEVC levels list instead of the hardware's
`[153]` while the switch was on. It exercises the actual codec probe and Android
diagnostic adapter while toggling on/off/on, with guards for other decoder
bounds and unchanged playback evidence after diagnostic reads.

Command (JDK 21):

```sh
./gradlew :android-shared:testReleaseUnitTest \
  --tests 'org.siloserver.silo.common.player.MediaCodecCapabilitiesProbe*' \
  --tests 'org.siloserver.silo.common.player.PlaybackCapabilityDetector*' \
  --tests 'org.siloserver.silo.common.player.ForceHdrPassthroughTest' \
  --tests 'org.siloserver.silo.common.diagnostics.DeviceSnapshotCollectorTest' \
  --tests 'org.siloserver.silo.common.settings.AndroidPlayerSettingsStoreTest' \
  :androidApp:compileReleaseKotlinAndroid :androidTvApp:compileReleaseKotlinAndroid \
  :androidApp:lintRelease :androidTvApp:lintRelease --parallel --console=plain
```

Supply-chain, derived Silora resource, pinned-input/runtime-scope and diff checks
passed. The destination is the local import commit containing this entry,
identified by `hevc-levels` and the source pin above.

Other clients: Apple, Roku and Web are N/A; this corrects Android MediaCodec
diagnostics for Silora's local HEVC switch. No APK packaging, installation,
device/server validation, push, PR or release is part of this import.

## Retired: AI subtitle fork changes, November 17, 2026

Matt requested retiring `edrock-ai-subs-fixes` and `tv-live-ai-subtitles` to
align the AI subtitle paths with incorporated official upstream
`0867eacb14b5ee7a8c46cc2b22e52d1799e599a7` and remove a troubleshooting variable.
Subtitle-related buffering reports had no confirmed cause. This retires the
original patches and all subsequent subtitle adaptations recorded above,
including phone live cues, job recovery/planning and TV inventory fallback.
The historical v42 coverage remains recorded; it is no longer active scope.

Pre-retirement Silora: `a4742bb151958836a8f8e9b741a59c000792b461`; local
recovery: `refs/codex/retired-ai-subtitles/2026-11-17`. The
[retirement and restoration record](2026-11-17-ai-subtitles-retirement.md)
preserves behavior, source references, validation and restoration guidance.
Do not reimport either entry without an explicit reactivation request.
`admin-console`, `trailers`, `home-explore-removal` and `hevc-levels` keep their
existing status and coverage.

## Imported: v49 Admin console, November 20, 2026

Matt requested the Admin update from the November 20 audit. This entry
advances import coverage for `admin-console`. The v42 whole-branch checkpoint
above remains the recorded review baseline.

- Source: `https://github.com/edrock200/silo-android.git`, branch
  `feat/hevc-level-override-v49`, pin `f640a73de80299c08ca3b1ff53d5f6e013dbcb30`.
- On November 20 the live branch heads matched the pinned inputs.
  The APK branch at `13cf2b064a6070a11f64d6b6cf5caf51f5188070` identifies
  the source branch and commit as build 48.
  [APK publication](https://github.com/edrock200/silo-android/blob/13cf2b064a6070a11f64d6b6cf5caf51f5188070/README.md)
- Edrock upstream base: `1b4732d90beb0f63832072a29c663205b9046a28`.
  Official upstream comparison: `63c8980846923db8bc96481bcf6bf000760286f7`.
- Silora input: `cc38f956a2c001bef54010b2e7822b8cbb7603f2` on `main`,
  with incorporated upstream `61a4bf1ea2d0f5c1cd9fcd41fd12c40e28fe9d07`.
  Recovery ref: `refs/codex/edrock-admin-v49/20261120T191654Z`.

**Adapted** Edrock `5ddea7d5d056ee40713c38eed229d01dc300ec00` for Now
Playing. The shared phone and TV model reads `series_title` and uses
`media_title` for the episode when the older `episode_name` is absent. It
also accepts the legacy `series_name`. The hand-built contract check still
requires current response fields and allows the legacy episode field.

**Imported** the cumulative asset changes from these Admin commits, including
the asset hunk in the Now Playing fix above:

- `ecbf63f3c08bf3650c2575bd8d5bca6140716d72` adds API key and section preview fields.
- `d1396718667ac725768e079607dc9a551d197771` updates settings check and rating schemas.
- `7ad67f9390dcac7889fd8043fb503f46244424e2` adds permission-gated catalog file paths.
- `6861da56b2b0ea70f81c254d819a96eef3ab07b5` adds environment flags and plugin schedule details.
- `f6c24a62558a6504f0400b0fcb94bc78a2d11373` adds plugin capabilities, provisioning status and sign-in binding fields.

The 474-operation asset was regenerated from official silo-server
`975bbf41b33ccdce5bdc1d14daa9742748042be9` OpenAPI blob
`bcedcb134aa795d22b5b25d5f18f82afd204db16` and matches Edrock byte for
byte. The asset records the contract's last-change stamp,
`2c2dc7c 2026-11-19`. The two new plugin operations are reads. Generic
forms use the existing Admin access and server permission checks.

### Validation

JDK 21 ran 40 release Admin tests, with 18 in `shared` and 22 in
`android-shared`. All passed with zero failures, errors or skips. The three
new tests exercised the console's JSON decode path for current and older
episode fields and standalone media. Phone and TV release Kotlin compilation
and lint passed with the existing baselines. Supply-chain, generated-resource
and diff checks passed. APK packaging, installation and live server checks
were outside this import.

```sh
./gradlew :shared:testReleaseUnitTest --tests 'org.siloserver.silo.admin.*' \
  :android-shared:testReleaseUnitTest --tests 'org.siloserver.silo.common.admin.*' \
  :androidApp:compileReleaseKotlinAndroid :androidTvApp:compileReleaseKotlinAndroid \
  :androidApp:lintRelease :androidTvApp:lintRelease --parallel --max-workers=4 --console=plain
```

The final shared-only rerun passed after aligning the regression test decoder
with `AdminService`.

Scope excludes Edrock's release tooling and the official upstream refreshes
carried on his branch. Trailers and Home removal were already covered in the
audit. The HEVC picker and retired subtitle patches were skipped, and the
keyboard microphone prompt remains deferred. The destination is the local
`admin-console` import commit containing this entry and source pin.

## Recording the next review or import

Record the date, verified source repository, branch and full SHA, Silora input
SHA, official upstream comparison SHA, and whether the review covered a whole
branch or named features only. Distinguish cached refs from a live remote check.

For each changed feature or candidate, record source commits or exact patch
scope, disposition (`imported`, `adapted`, `already covered`, `skipped`,
`deferred`, or `unresolved`), the reason, and evidence. Link the destination
Silora commit after it exists; label uncommitted or unvalidated work as pending.
When recording an import in its own commit, identify it by feature ID/source
pin and Git history instead of embedding its own SHA. Report the resulting
commit SHA after creation.
Record tests actually run and separate device observations from source checks.

Keep full-review checkpoints and per-feature import coverage separate. Updating
Admin alone does not mark trailers or a whole new branch reviewed. Rebased
source SHAs and patch IDs are comparison aids; confirm actual code and behavior.
Preserve earlier entries so deferred candidates and local adaptations remain
traceable. An unchanged review needs no duplicate entry.
