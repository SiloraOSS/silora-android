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
| `input-scope` | Supporting storage for `tv-keyboard` and `tv-remote`: save only App/System keyboard choice, automatic keyboard opening, and long-press mappings separately per server/login/profile. This adds no menu or standalone setting. Preserve migration, switching and account-removal cleanup. | `a19729d21`; account integration in `73c46323f` |
| `trailers` | Embedded YouTube previews, external fallback and TV controls keep trailers accessible from movie/series details. Prefer local trailers and retain attribution. | `bac06a7c7` |
| `ai-subtitles` | Preserve the four fixes for server audio ordinals, bounded uncertain-create fencing, missing-row replanning and language-alias normalization. Retire individual workarounds when a verified upstream replacement supplies the same behavior. | `5536474b3` |
| `up-next-setting` | **Up Next Trigger** offers Credits markers, Time remaining and Disabled. Preserve local trigger scope, timing fallback, normal end screen, autoplay and manual TV action. Matt identified this setting as his addition in the November 5–6 chat. | `eb13f7033` |
| `up-next-fixes` | Eligible credits start in the final quarter; otherwise use timing. Keep Watching allows one guarded end countdown; cancellation preserves the end screen. Matt explicitly requested merging PR #2 after reviewing this behavior. | PR #2: `a7dce2fc6`, `7bd09d20e`, `ac56376af` |
| `release-tooling` | Preserve persistent signed phone/TV builds, version metadata, APK verification, matching R8 mappings, release workflows and configured notification behavior. This entry does not authorize publishing, messaging, changing schedules/destinations or exposing signing material. | `bf8304d5b`, `5f68d3c69`, `74ea34c8a`, `8005d02c1`, `7d7b9bb72`, `2b958c5c2`, `198f651f4` |
| `fork-maintenance` | Maintain this reviewed inventory and the upstream-merge skill. Explain meaningful conflict decisions by feature ID; follow upstream outside intentional differences and do not treat inherited instructions as proof of a user customization. | November 6, 2026 authorization in this chat; `AGENTS.md`, `.agents/skills/silora-upstream-merge/SKILL.md` |

## Superseded or retired

| ID | Replacement and reason | Validation |
| --- | --- | --- |
| `tv-voice` | Superseded by [upstream PR #830](https://github.com/Silo-Server/silo-android/pull/830), commit `4fb8f1b59`. Matt requested retiring the fork implementation on November 6, 2026. Use upstream's system-recognizer mic button and result handling. Do not restore the fork's local recording controller, permission flow or voice overlay from old commits. | Exact upstream `TvVoiceSearch.kt` helper adopted; TV tests/build and on-device limits are recorded in `CHANGELOG.md`. |

Requests navigation has no fork override and must not be reintroduced from old
merge logs or instruction text.
