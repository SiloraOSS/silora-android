# Up Next trigger

On phone and TV, open **Settings → Playback → Episodes → Up Next Trigger**.
The descriptions under the setting explain all three choices:

- **Credits markers** (default): open Up Next when playback crosses the start
  of the last eligible credits range. A range is eligible when it starts in the
  final 25% of the known runtime and ends no more than one second after it.
  If no eligible range exists, use the
  **Time remaining** setting as a fallback.
- **Time remaining**: ignore credits markers and open Up Next at the selected
  time before the end. Use this when credits markers are unreliable.
- **Disabled**: suppress automatic Up Next pop-ups during playback. The normal
  end screen, intro/recap/credits skip controls, and TV's manual Up Next action
  remain available.

The **Time remaining** row offers **At end**, **10 seconds**, **30 seconds**,
**1 minute**, or **2 minutes** before the end. Its description changes with the
trigger: under **Credits markers** it explicitly says this is a fallback used
when the episode has no eligible credits markers; under **Time remaining** it says
markers are ignored. **At end** waits until playback finishes when timing is
used. An eligible credits marker still takes priority in **Credits markers** mode.
The timing row is hidden while the trigger is **Disabled**.

The trigger choice is local to the active profile, server, and device. It does
not change server markers or sync the mode to other devices. Timing retains its
existing profile value and device override; **Use profile setting** restores
that value. **Use Profile Settings** clears the local trigger choice too.

**Auto-Play Next Episode** remains separate. Disabling pop-ups does not disable
autoplay. The countdown keeps its existing behavior, and **Play Now** can start
the next episode immediately. Seeking and resuming beyond a trigger do not
open Up Next immediately; Watch Party keeps ownership of episode transitions.
Automatic pre-end prompts wait for saved settings and a finite, positive runtime.
Keep Watching cancels a pre-end countdown and suppresses the card through seeks
for that episode. At actual playback end, a fresh countdown is offered once if
autoplay and the existing safety gates allow it. Cancel autoplay at the end
stops that countdown and keeps the end screen visible. Opening and dismissing
a manual TV card does not erase a pending automatic end countdown. Raw markers and skip controls are unchanged.

## Saved choices

The existing key, `silora.next_up_prompt_mode`, stays unchanged. Saved
`credits` (previously **Enabled**) selects **Credits markers**, `off`
(previously **Disabled** or **Off**) stays **Disabled**, and `time_remaining`
selects **Time remaining**. A missing or unknown value uses **Credits markers**.

## Validation

Focused Kotlin tests cover marker selection, runtime/window boundaries and dismissal
state. They pass under `:shared:testDebugUnitTest`. Restoring the old behavior
while retaining the compatible test/helper signatures produces assertion failures;
restoring the fix passes. The tests do not instantiate the phone/TV viewmodels,
so their wiring also requires independent source review and device validation.
Device playback verification remains outstanding.
