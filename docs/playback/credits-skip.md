# Skip Credits: Ask / Auto / Off

Phone and TV expose **Skip Credits** in Playback settings:

| Mode | Behavior |
| --- | --- |
| Ask | Show a Skip Credits button during a credits segment. |
| Auto | Seek to the end of each credits segment once per playback. |
| Off | Play credits without offering a credits skip button. |

Ask is the default. Until a local mode is saved, the existing
`playback.auto_skip_credits` boolean maps `true` to Auto and `false` to Ask.
The three-way choice is saved for the current account, profile, server and
device. Server refreshes cannot overwrite it, and Reset Device Settings clears
it. The choice never changes the server boolean.

Auto requires a known runtime and an active credits marker. It seeks only to
that segment's end, bounded by the runtime, so scenes between segments remain
playable. Rewinding into an already skipped segment does not skip it again.
Phone downloads use the same behavior when their metadata includes markers.

Automatic skips wait during pauses, loading, scrubbing and content handoffs.
In Watch Party, Auto offers the existing permission-gated manual action; Off
still hides it. Recap skipping is independent of this setting. Up Next keeps its
own trigger and autoplay settings.

When Auto is selected, both settings screens warn that incorrectly marked
episodes may skip content or cause playback issues, and to use Auto at the
viewer's own risk.

`CreditsSkipMode` defines the choices, `CreditsAutoSkipController` tracks skipped
segments, and `markerSkipTarget` controls manual credits actions. The phone and
TV player view models feed playback snapshots to the controller and use the
existing immediate-seek path for its targets.
