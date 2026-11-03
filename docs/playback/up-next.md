# Up Next trigger

On phone and TV, open **Settings → Playback → Episodes → Up Next Trigger**.
The descriptions under the setting explain all three choices:

- **Credits markers** (default): open Up Next when playback crosses the start
  of the last credits range. If this episode has no credits markers, use the
  **Time remaining** setting as a fallback.
- **Time remaining**: ignore credits markers and open Up Next at the selected
  time before the end. Use this when credits markers are unreliable.
- **Disabled**: suppress automatic Up Next pop-ups during playback. The normal
  end screen, intro/recap/credits skip controls, and TV's manual Up Next action
  remain available.

The **Time remaining** row offers **At end**, **10 seconds**, **30 seconds**,
**1 minute**, or **2 minutes** before the end. Its description changes with the
trigger: under **Credits markers** it explicitly says this is a fallback used
only when the episode has no credits markers; under **Time remaining** it says
markers are ignored. **At end** waits until playback finishes when timing is
used. A credits marker still takes priority in **Credits markers** mode.
The timing row is hidden while the trigger is **Disabled**.

The trigger choice is local to the active profile, server, and device. It does
not change server markers or sync the mode to other devices. Timing retains its
existing profile value and device override; **Use profile setting** restores
that value. **Use Profile Settings** clears the local trigger choice too.

**Auto-Play Next Episode** remains separate. Disabling pop-ups does not disable
autoplay. The countdown keeps its existing behavior, and **Play Now** can start
the next episode immediately. Seeking and resuming beyond a trigger do not
open Up Next immediately; Watch Party keeps ownership of episode transitions.
Timers wait for the saved settings and a known runtime before opening a prompt.

## Saved choices

The existing key, `silora.next_up_prompt_mode`, stays unchanged. Saved
`credits` (previously **Enabled**) selects **Credits markers**, `off`
(previously **Disabled** or **Off**) stays **Disabled**, and `time_remaining`
selects **Time remaining**. A missing or unknown value uses **Credits markers**.

## Validation

Phone and TV release compilation passed, along with seven focused trigger tests
and the profile/server/device persistence test. Lint and the full test suites
were not run. No APK was packaged or installed for this revision; device
playback and UI verification remain outstanding.

Related upstream report:
[comment on server PR #2971](https://github.com/Silo-Server/silo-server/pull/2971#issuecomment-6600721172).
