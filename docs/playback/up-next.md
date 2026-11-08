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
- **After playback ends**: wait until the video finishes before opening Up Next.
  The end screen keeps its normal autoplay countdown and Play Now controls.
  Intro/recap/credits skip controls and TV's manual Up Next action remain available.

The **Time remaining** row offers **At end**, **10 seconds**, **30 seconds**,
**1 minute**, or **2 minutes** before the end. Its description changes with the
trigger: under **Credits markers** it explicitly says this is a fallback used
when the episode has no eligible credits markers; under **Time remaining** it says
markers are ignored. **At end** waits until playback finishes when timing is
used. An eligible credits marker still takes priority in **Credits markers** mode.
The timing row is hidden while the trigger is **After playback ends**.

The trigger choice is local to the active profile, server, and device. It does
not change server markers or sync the mode to other devices. Timing retains its
existing profile value and device override; **Use profile setting** restores
that value. **Use Profile Settings** clears the local trigger choice too.

**Auto-Play Next Episode** remains separate. Waiting until playback ends does not disable
autoplay. The countdown keeps its existing behavior, and **Play Now** can start
the next episode immediately. Seeking and resuming beyond a trigger do not
open Up Next immediately; Watch Party keeps ownership of episode transitions.
Automatic pre-end prompts wait for saved settings and a finite, positive runtime.
Keep Watching cancels a pre-end countdown and suppresses the card through seeks
for that episode. At actual playback end, a fresh countdown is offered once if
autoplay and the existing safety gates allow it. Cancel autoplay at the end
stops that countdown and keeps the end screen visible. Opening and dismissing
a manual TV card does not erase a pending automatic end countdown. Raw markers and skip controls are unchanged.

## Artwork after playback ends

On phone and TV, the preview pane continues to show live video while the episode
is playing, including during credits. Once playback ends, the pane shows the
next episode's thumbnail instead of an empty video surface. This applies to all
trigger choices and to an Up Next screen that was already open before the end.
Compact tabletop phone layouts that omit the preview pane keep their existing layout.

If the thumbnail is missing, fails to load, or is hidden by spoiler settings,
use the next item's series/season artwork when available, then the standard
artwork placeholder. A hidden episode still is never loaded for this pane.
Optional series-art loading does not delay publishing the next item or starting
its countdown. The mounted player and its viewport remain available while the
next item prepares. Countdown, autoplay, cancellation and focus rules stay the same.

## Saved choices

The existing key, `silora.next_up_prompt_mode`, stays unchanged. Saved
`credits` (previously **Enabled**) selects **Credits markers**, `off`
(previously **Disabled** or **Off**) now displays **After playback ends** with the
same behavior, and `time_remaining` selects **Time remaining**. A missing or unknown value uses **Credits markers**.

## Validation

On November 7, 2026, phone and TV release compilation passed with the new label
and artwork panes. All 33 focused tests passed: seven trigger-policy tests,
one preference persistence/scope/reset test, and 25 phone player load/transition
integration tests. Existing tests were updated for the renamed enum choice;
no new UI tests were added.

Lint and full test suites were not run. No APK was packaged or installed for
this revision. Device playback and UI verification remain outstanding,
including artwork fallback, spoiler settings, focus and the 10-second end countdown.
