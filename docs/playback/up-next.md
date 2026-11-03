# Up Next during playback

On phone and TV, open **Settings → Playback → Episodes → Up Next during playback**.
The choice is saved for the active profile on this device and server. It is
local to Silora and does not alter the server's markers.

- **Enabled** keeps the original behavior: Up Next opens when playback crosses
  the start of the last credits range. On phone, the existing **Without credits
  markers** setting controls the fallback when no credits range exists. TV
  waits until playback ends when there is no credits range.
- **Disabled** suppresses automatic Up Next prompts during playback. Intro,
  recap and credits skip controls remain available. Playback still uses the
  normal end screen when the video finishes. TV's manual Up Next action remains
  available.

**Auto-Play Next Episode** remains separate. Disabling prompts does not disable
autoplay. The countdown keeps its original behavior, and explicit **Play Now**
can start the next episode immediately. Seeking keeps its existing prompt
suppression, and Watch Party keeps ownership of episode transitions.

The default is **Enabled**. **Use Profile Settings** resets this local choice
along with the other playback overrides. For an episode whose credits marker
overlaps its intro, choose **Disabled** to prevent the early automatic interface.

When upgrading from the interim three-choice version, **Credits markers**
becomes **Enabled**. Both **Off** and **Time remaining** become **Disabled**, so
an existing choice to avoid credits-triggered prompts stays opted out. The
Time remaining mode and its TV timing picker have been removed. The phone's
original no-marker timing fallback remains available when Enabled.

## Validation

The two-choice revision compiled for phone and TV. The signed TV release APK
was built and update-installed on the Shield on 2026-11-03, preserving app data.
Tests and lint were skipped as requested. The Enabled/Disabled settings and
upgrade behavior still need user verification on the device.

Related upstream report:
[comment on server PR #2971](https://github.com/Silo-Server/silo-server/pull/2971#issuecomment-6600721172).
