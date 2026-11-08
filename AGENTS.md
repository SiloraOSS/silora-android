# Repository Guidelines

## Project Structure & Module Organization

This repository contains only the Silo Android clients. Shared Kotlin logic lives in `shared/`, Android-only playback and UI helpers live in `android-shared/`, the phone app lives in `androidApp/`, and the TV app lives in `androidTvApp/`. Android playback notes live in `docs/playback/`; utility scripts live in `scripts/`.

## Current Product Exposure

- Ebooks/Reading are phone-only. Do not expose ebooks or Reading on Android TV.
- Android mobile navigation is Home, Libraries, For You, Calendar, and Downloads only when the active profile has downloads. Video, Audio, and Reading are library modes reached through Libraries, not bottom-nav tabs.
- Android TV navigation is Home, available media-type tabs from server libraries, For You (with its Watchlist/Favorites dropdown, mirroring tvOS `.recommendations`), and Calendar, with search left of the tabs and the profile avatar on the right. The order is fixed, matching tvOS and Roku. Requests lives in the profile menu while the server enables it. Reading/ebooks are excluded.
- Requests is live on phone and TV, server-gated by `requests_enabled` (phone and TV: profile menu + search, matching Apple and Roku). The user Requests screens offer request, cancel of the user’s own pending request, and status; keep moderation out of those screens (a source guard test enforces this).
- Silora exposes an administrator-only Admin console from the profile menu on phone and TV. Matt explicitly requested importing edrock200’s admin commit range on November 8, 2026. Dashboard/now playing, request moderation, tasks/scans, logs, and the bundled admin API forms live in this separate console. The server enforces administrator and primary-profile permissions. Preserve this fork exception to upstream’s unexposed admin surfaces; see `FORK_DIFFERENCES.md` (`admin-console`). Personal session management remains unexposed outside admin operations.
- Watch Party (synchronized playback) is experimental on phone and TV. Its entry points (profile menu, the detail page's party action, and phone `silo://watch-party` invitations) appear only while Settings → Experimental → Watch Party is on, a device-local toggle that defaults on in debug builds and off in release builds, and it works only when the server advertises the room and playback capabilities. Keep it behind the toggle until the qualification matrix in `docs/watch-party-implementation-plan.md` passes.

## Fork maintenance

Use [FORK_DIFFERENCES.md](FORK_DIFFERENCES.md) for intentional Silora differences
and update it alongside explicitly requested new fork work. Inherited upstream
instructions and old merge resolutions do not establish a custom product
decision. When integrating upstream, update its instructions with its code and
preserve the active fork differences rather than freezing old product text.

## Build, Test, and Development Commands

- `./gradlew :androidApp:assembleDebug` builds the Android phone APK.
- `./gradlew :androidTvApp:assembleDebug` builds the Android TV APK.
- `./gradlew :androidApp:installDebug` installs the phone app on a connected emulator or device.
- `./gradlew :androidTvApp:installDebug` installs the TV app on a connected emulator or device.
- `./gradlew test` runs available Kotlin/JUnit tests.

## Coding Style & Naming Conventions

Use Kotlin 2.1, Java 21 targets, and Compose idioms. The Silo package root is `org.siloserver.silo`. Both the phone and TV apps share a single `applicationId`, `org.silora.app`, so they publish as one Google Play listing (Play routes each build by manifest feature filtering — the phone build requires `android.hardware.touchscreen`; the TV build requires `android.software.leanback`). The Gradle `namespace` stays distinct per module (`org.siloserver.silo.android`, `org.siloserver.silo.tv`) so generated `R`/`BuildConfig` classes don't collide. The two artifacts use distinct versionCodes (`base*2` for phone, `base*2+1` for TV), so each release bumps both by 2 with no reuse. `scripts/release-version.py` derives the base from the store version and a repository-wide build number (`base = 100_000_000 + (major*10000 + minor*100 + patch)*1000 + build`), so a version ships as many tester builds as it needs before its store release. See `docs/releasing.md`. This repo is on the full Silo namespace cut, so do not add legacy package IDs, legacy storage names, or old-brand symbols. Kotlin classes and composables use `PascalCase`; functions and properties use `camelCase`.

## Testing Guidelines

Android tests use Kotlin test/JUnit where present, especially under `android-shared/src/androidUnitTest`. Do not add tests for small changes or UI changes unless requested. For shared logic changes, add focused tests only for critical or high-risk behavior.

## Writing

Run a final readability pass on every human-facing issue, pull request,
document, or status update.

- Lead with the outcome.
- Use concrete, plain language and active voice.
- Cut filler, stock framing, repetition, and promotional claims.
- Preserve meaning, evidence, citations, uncertainty, and established
  terminology.
- Never rewrite exact quotations, commands, logs, identifiers, API names, or
  contractual language.
- Match the tone to the audience and use only formatting that improves
  readability.

## Pull requests

Never create a pull request unless the developer explicitly asks for one.

Use a Conventional Commit title in plain language. Start the body with the
problem, explain the solution next, and end with the required AI disclosure,
including the exact model identifier, agent harness, and any other AI tooling.
Include repository-required issue links, validation evidence, risks, and
follow-up work.

- Keep one concern per pull request. If an honest description needs the word
  "also," split the work.
- Every pull request that changes what a user sees must include evidence, as
  [Show visible changes](CONTRIBUTING.md#show-visible-changes) defines. That
  covers UI and UX changes and changes to which items appear or what they show,
  such as search results, home sections, recommendations, sorting, filtering,
  metadata, or artwork. Use before-and-after captures of the same screen with the
  same data, and a short recording when motion, timing, or focus matters.
  Capture each device the change can reach: an Android phone, an Android
  tablet, an Android foldable (folded and open), and Android TV, or say why a
  device is unaffected. Write
  `Evidence: none, no user-visible change` only when that is true.
- Attach evidence on GitHub under the PR body's Evidence heading, or publish it
  with `npx @silo-server/evidence publish <folder> --pr <number>` and put its
  `Evidence:` link there. Only Silo maintainers and the pull request's author can
  open a published page. GitHub has no API for attaching images to a pull
  request, so an agent either publishes with the CLI or gives the developer the
  captures to attach. Check media for private information before it goes on
  GitHub. When publishing exits 4, ask the developer to run
  `npx @silo-server/evidence login`; never approve that login or read the saved
  key. Never commit PR-only assets such as `.github/pr-assets/`.
- When babysitting a pull request, poll checks and review comments created
  after the last push. Verify bot findings against the source, fix real issues,
  and dismiss false positives with a written reason. Remain quiet when nothing
  new has appeared. Stop when the latest commit is green.

## Security & Configuration Tips

Do not commit local SDK overrides, signing material, logs, tool state, generated build output, or media fixtures. A running Silo server is required for realistic auth, browsing, and playback validation.
