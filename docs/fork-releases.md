# Silora releases

**Silora — your media, after dark.**

Silora is an unofficial fork of the Silo Android phone and TV clients. Its
fork changes are an OLED-focused visual identity and an HEVC compatibility
patch for NVIDIA Shield setups. The rest of the client feature set comes
from Silo.

The visual identity uses a purple split-prism mark, charcoal launcher
backgrounds, and matching wordmarks and TV artwork. The playback patch is
**Ignore HEVC Level Limits**, an On/Off switch enabled by default. On removes
HEVC level bounds from capability reports; Off uses the decoder's reported
levels. Other compatibility checks still apply. This does not increase the
hardware decoder's actual capabilities. Existing saved numeric levels become
On; a saved Off stays Off.

The release workflow publishes optimized, signed phone and TV APKs on
[GitHub Releases](https://github.com/SiloraOSS/silora-android/releases).

## Schedule and manual builds

The **Silora Daily** workflow runs every day at 15:37 UTC:
08:37 Pacific during daylight saving time and 07:37 during standard time.
GitHub can delay scheduled jobs. The workflow must stay on the default branch,
`main`. It can also be started from Actions → Silora Daily →
Run workflow, selecting `main`. Leave **Force build** unchecked to use the same
change check, or check it to compile and publish even when nothing changed.

Each run uses GitHub's branch-merge API to merge the exact current
`Silo-Server/silo-android` `main` commit into `SiloraOSS/silora-android` `main`.
The repository is a direct fork of official Silo Android. This API merge requires
both repositories to share a GitHub fork network. The merge preserves the fork's
commits and fails on conflicts, without resetting
or force-pushing the branch. After syncing, it compares the merged source's Git
tree (all tracked file contents) with the source tag of the most recently
published complete Silora release. Matching contents skip compilation
and publishing, even if commit history changed. The daily sync still completes,
and the run summary explains the decision. Drafts, prereleases and releases
missing either APK or the verification files do not advance this baseline.
Failed builds therefore retry on the next daily run until those changes ship.
Changes in this fork also trigger a build, even when upstream is unchanged.
API errors fail the check instead of silently skipping it. This comparison
includes documentation and workflow files as well as app code.

When a build is needed, it reserves the dated tag while that commit is the
head of `main`, then checks out the exact tagged commit and runs
release unit tests and lint, and builds both APKs with JDK 21 and R8 optimization.
The inherited Google Play and Discord workflows are disabled in the fork's
Actions settings; their source files stay unchanged to reduce merge conflicts.
The fork workflow never invokes Play publishing. A separate notification workflow
reports its status to Discord as described below.

### Release pushed changes without syncing upstream

After pushing changes to `main`, open **Actions → Silora Manual Release →
Run workflow** and select `main`. This workflow has only a manual trigger:
there is no schedule or push trigger. Every run builds and publishes, even if
the source matches the previous release.

The manual workflow builds the exact commit selected when the run starts. It
does not fetch upstream Git objects, merge upstream, or update `main`. Read-only
GitHub API calls identify the upstream snapshot already included in that commit
and list official version tags. Only tag commits already in the fork checkout
are used for version stamping. Newer upstream code is not included.

It uses the daily workflow's version format, signing key, tests, lint, APK
verification, GitHub release publication, and Discord webhook configuration.
Successful builds become regular **Latest** releases with the same APK names
and permanent download links. Tags use `manual-YYYY-MM-DD.RUN` to avoid collisions
with the daily workflow's separate run counter. Both workflows share a release
lock so their builds and publishing do not overlap.

The daily workflow recognizes complete manual releases as its comparison
baseline. If the next daily sync produces the same source files, it skips the
build as usual. The daily schedule and upstream sync behavior stay unchanged.

## Published releases

Each dated release includes:

- `silora-phone.apk`: universal phone/tablet build.
- `silora-tv.apk`: universal Android TV / Google TV build.
- `build-info.json`: exact source SHA, checked upstream SHA, timestamp, version
  code base, and workflow run counter.
- `SHA256SUMS`: checksums covering both APKs and the provenance file.

Each successful build is published as a regular GitHub release and
explicitly marked **Latest**. These permanent links follow that release:

- [Latest phone APK](https://github.com/SiloraOSS/silora-android/releases/latest/download/silora-phone.apk)
- [Latest TV APK](https://github.com/SiloraOSS/silora-android/releases/latest/download/silora-tv.apk)

The APKs are optimized release builds. Nothing is published until signature checks pass and downloaded
release assets match the locally calculated checksums. A failed run leaves the
previous public releases intact. A failed upload can leave an unpublished draft;
rerunning the failed publish job completes it without replacing a public release.
Published tags and assets are never overwritten by this workflow.

## Discord run notifications

**Silora Daily Notifications** listens for **Silora Daily** and
**Silora Manual Release** `in_progress` and `completed` events on `main`,
including reruns. It posts a start message and a final result to the configured
staff log channel. Failure messages mention the configured Discord users;
starts, successes, unchanged
source and cancellations have no pings. Every message links to the specific
run and identifies its run number and attempt.
Manual release messages identify the manual workflow and report build and
publication status without claiming an upstream sync.

Successful runs distinguish published APKs from an unchanged-source skip.
Failed runs identify the failing job and step when GitHub provides them. A
failure in **Merge upstream into main** explains that a merge conflict or API
error blocked the sync. A failure reading job details still sends the known
workflow result and run link.

Configure these on `SiloraOSS/silora-android` before enabling notifications:

- Actions secret `SILORA_DAILY_DISCORD_WEBHOOK_URL`: the staff log webhook URL.
  Keep the URL out of source files, Git history and logs.
- Actions variable `SILORA_DAILY_DISCORD_CHANNEL_ID`: the webhook's destination
  channel ID. Delivery checks this before posting, so moving the webhook to a
  different channel stops notifications instead of sending there.
- Actions variable `SILORA_DAILY_FAILURE_USER_IDS`: comma-separated Discord
  **user** IDs for Matt and Kyle. Only these users can be pinged; role and
  everyone mentions are disabled.

The notification workflow must be on the default branch to receive GitHub's
workflow events. GitHub queues it independently, so delivery can lag the daily
or manual run. It uses trusted default-branch source and a read-only GitHub token, without
downloading the triggering run's artifacts or caches. Notification failures
appear in **Silora Daily Notifications** and do not stop syncing, building or
publishing. A cancelled run sends a cancellation notice without pings.

Delivery waits for Discord's confirmation and reads the message back, checking
its channel, content, embed and mentions. It never retries an uncertain send
automatically; inspect the channel before rerunning a failed notification job.
Check changes with `python3 scripts/test-notify-fork-daily.py`,
`scripts/check-build-supply-chain.sh`, and
`actionlint .github/workflows/fork-daily-notifications.yml` when available.

## Persistent signing

### Local builds

Run `./build.sh` to build phone and TV APKs using the same release settings,
signing identity, version-name format, timestamp-based version codes, tests,
lint, and dependency checks as **Silora Daily** and **Silora Manual Release**.
The script verifies the APK signatures, application ID, version, and
non-debuggable flag before copying the universal APKs and checksums to
`dist/local-YYYY-MM-DD.HHMMSS/`. It does not publish a GitHub release.

Keep local signing files in `../silora-signing/`, beside this repository:

```text
source/
├── silo-android/
└── silora-signing/
    ├── release.jks
    └── signing.json
```

`signing.json` contains `password` and `alias` strings, plus `key_password`
only when the key password differs from the keystore password. Restrict the
directory to mode `700` and both files to mode `600`. These private files stay
outside the repository. To use a different folder, set `SILORA_SIGNING_DIR` or
pass `./build.sh --signing-dir /private/path`.

Use `./build.sh --check` to validate the saved signing certificate and calculate
the version without compiling. Both modes fetch official version tags and the
upstream main reference into private Git refs; neither merges upstream nor
changes your branch or working files. Uncommitted local changes are included
in a build and flagged in its `build-info.json`.

Newer local and GitHub builds can update each other on the same device type
when they use this signing key. Android still blocks a normal installation of
an older version code over a newer one. Version codes are assigned when each
build starts, so use their codes to compare overlapping builds. A release-key
build cannot update an existing installation signed with a different debug key;
that installation needs a one-time uninstall first.

### GitHub Actions secrets

Repository Actions secrets:

- `SILO_RELEASE_KEYSTORE_B64`: base64-encoded private signing keystore.
- `SILO_RELEASE_KEYSTORE_PASSWORD`: keystore password.
- `SILO_RELEASE_KEY_ALIAS`: signing alias.
- `SILO_RELEASE_KEY_PASSWORD`: optional separate key password; defaults to the
  keystore password.
- `SILO_GOOGLE_SERVICES_JSON_B64`: optional Firebase configuration for
  `org.silora.app`. Without it, phone push notifications are unavailable; the
  rest of the app still builds.

The first three secrets are required before compilation starts. Daily upstream
synchronization and the change check do not depend on signing secrets.
The key must remain the same for future APKs to install as updates. Keep a
private backup of the key and passwords outside Git. Signing material is decoded
only on the temporary build runner and removed after the build. The public
certificate fingerprint can be checked with `apksigner verify --print-certs`.

Both apps use the application ID `org.silora.app`, so they install separately
from Silo and previous Silune builds. Existing Silune settings, sign-ins, and
downloads do not transfer automatically; set up Silora as a new app. Later
Silora builds signed with the same persistent release key update in place.

The APK version, in-app version label, and GitHub release title preserve Silo's
version and build number, then append the Silora build date:
`1.0.0 (26) (silora:10212026)`. The date is **MMDDYYYY in Pacific time** and is
fixed when the workflow reserves its tag, even if compilation crosses midnight.
Multiple builds on the same day share this visible date.

The base version comes from the official Silo build tag with the highest
repository-wide build number included in the checked upstream snapshot, such as
`v1.0.0-beta.26`. Both workflows use official tags in a separate namespace;
the daily workflow fetches them, while the manual workflow reads tag metadata
without fetching upstream Git objects. Fork tags and newer upstream tags outside
the included snapshot cannot change its version. Main can contain
changes after that tagged release, so `build-info.json` and the release notes
retain both the base tag and exact checked source SHAs. A missing official build
tag stops publication rather than inventing a version. The upstream build number
is also preserved in the client build header reported to Silo.

Android's internal version codes still use elapsed UTC seconds since 2026 plus a
fork-specific base, remaining monotonic even if the workflow run counter resets.
They control update ordering independently of the visible date. Phone and TV
retain distinct version codes. The workflow counter remains in provenance and
the unique `daily-YYYY-MM-DD.RUN` or `manual-YYYY-MM-DD.RUN` Git tag used for
publication and recovery; previously published releases are unchanged.

## Branding sources

The app, accessibility descriptions, and HTTP client-name headers use **Silora**
(**Silora TV** for TV client headers). The mark uses two angular purple forms
separated by a diagonal cut, with three facets on the upper form. Violet
(`#5736B4`), lavender (`#B59AF4`), and muted purple (`#6856A3`) sit on a
charcoal (`#25252D`) launcher background. The wordmark pairs the mark with
white lettering; the brand line is "Your media, after dark."

The editable sources are `branding/silora-mark.svg` and
`branding/silora-wordmark.svg`. Regenerate the checked-in PNGs and startup videos
with `python3 scripts/generate-silora-branding.py` (requires `rsvg-convert`,
Pillow, and FFmpeg with `libx264`).
The DejaVu Sans Bold lettering is outlined for consistent rendering.
The in-app wordmark has a transparent background.
Android's adaptive launcher insets the transparent split-prism mark; the 320×180 TV
banner centers a 170-pixel-wide wordmark inside Fire TV's square crop.
Phone and TV share a 1.6-second Silora wordmark fade on pure black at cold launch.
The videos retain the 720p30 AVC 3.1 and 1080p60 AVC 4.2 decoder tiers.
Resource IDs and Kotlin symbol names remain stable to reduce merge conflicts.
The Silora artwork is licensed under AGPL-3.0-or-later.

New releases use `silora-phone.apk` and `silora-tv.apk`. Permanent download URLs
use those filenames once a Silora release is published. Previously published
releases retain their original asset names; links using `silune-*`, `prism-*`,
or earlier filenames will not follow new Silora releases. The build decision
recognizes complete releases with either Silora or Silune APK names.
The release repository is `SiloraOSS/silora-android`. Use the same persistent
signing key for future Silora releases. Builds with the same application ID and
signing key update in place with the matching phone or TV build.

The local `unbranded` branch restores the original Silo name, artwork, notices,
and logo sizing while retaining the fork's playback and release tooling.
It is prepared for use after written trademark permission is received.

## Failures and maintenance

- Merge conflict: fetch upstream and resolve it locally on a branch, review the
  retained fork behavior, and merge the fix into `main`. Never resolve by
  discarding the fork commits. Start a new daily workflow after resolving it.
- Failed test, lint, or build: inspect the failing job, fix the source, and start
  a new run. The sync may already have updated `main`; a merge is not evidence
  that a release was built successfully. A reserved tag can exist without a
  published release after a failed build; leave it in place and start a new run.
- Failed upload: use Re-run failed jobs. Draft assets can be replaced while the
  draft is unpublished; public releases stay immutable.
- Disabled schedule: re-enable the workflow in Actions. GitHub can disable
  scheduled workflows after 60 days without repository activity.
- Missing Firebase setup: configure your own Firebase app if phone push is
  needed. Do not copy another project's private service credentials.
- Re-run the checks after editing the workflow:
  `python3 scripts/test-fork-release.py`, `scripts/check-build-supply-chain.sh`,
  `python3 scripts/test-notify-fork-daily.py`, and
  `actionlint .github/workflows/fork-{daily,manual}-release.yml .github/workflows/fork-daily-notifications.yml`
  when available.

No personal access token is stored by this automation. The built-in repository
`GITHUB_TOKEN` performs fork synchronization and release publishing; signing
secrets are supplied only to the build job. GitHub suppresses follow-on push
workflows for changes made with this token, so the daily workflow performs its
own validation rather than depending on a second CI run.

Tag reservation avoids GitHub's workflow-scope guard when `main` advances during
compilation. Publication requires the existing tag to match the tested source;
it never retargets a built APK to whatever happens to be on `main` later.
The release call uses `--verify-tag` and omits `--target`, which is ignored for
existing tags but can still trigger GitHub's workflow-file permission guard.
