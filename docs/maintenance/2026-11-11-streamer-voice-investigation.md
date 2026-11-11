# TV voice-search device check — November 11, 2026

Upstream voice search works on Matt's NVIDIA Shield running Android 11, but
fails on his Google TV Streamer running Android 14. A live comparison shows
that TiviMate uses the recognition service directly, while upstream Silo opens
Google's separate recognition activity. The same speech engine produces final
recognition events in both tests, but Google's activity stays open with no text
in the Silo test.

The initial investigation narrowed the failure to the activity-based recognition
path. The approved direct-service trial below then returned spoken text and
search results. A subsequent inspection of the installed Google APK found that
its final-result callback sets an activity result without finishing the activity.
This explains the stalled handoff; it is not evidence of a device-wide Android 14
speech failure.

## Source and device

- Google TV Streamer, Android 14 / API 34.
- Installed application: `org.siloserver.silo`, signed release, version name
  `0.0.0`, version code `3`.
- Its APK SHA-256 is
  `12cf3ebff9d66b8797c69e419883be1db7befa608c862c3441aadc09edf537cf`.
  This matches the archived, unmodified upstream comparison build at
  `dist/upstream-main-09f43a215-20261111/`, from source
  `09f43a2158326afca9864d00788b7019c18fa8fe`.
- TiviMate version `5.1.0`, target SDK 33, with microphone permission granted.
- Google TV activity provider: `com.google.android.katniss`, installed version
  `7.55.87+1001226628.05`.
- Configured recognition service: `com.google.android.tts` /
  `GoogleTTSRecognitionService`, installed version
  `googletts.google-speech-apk_20261014.00_p0-tv.999754440`.

## What was observed

The supplied diagnostic archive has 42 app records in the focus, lifecycle and
network categories, with no speech-result records. System logcat supplies the
speech evidence missing from that archive.

During the live Silo tests, Google opened
`com.google.android.apps.tvsearch.voice.voiceinput.VoiceInputActivity`.
Android's activity record identifies Silo as its result recipient. Matt spoke
the requested test phrase, “Star Wars.” Two speech sessions produced these
events, at 10:32 and 10:33 Pacific:

```text
RecognitionService#logStartListening: callingApp: com.google.android.katniss, locale: en-US
RecognitionService#onStartOfSpeech
RecognitionClient: #onResults withSpeech: true
FinalS3RespProcessor: Received the final result for the recognition
```

The Google activity remained resumed afterward. Its editor still displayed
“Speak a title,” and Silo's underlying search field remained empty. The actual
recognized strings and result bundle are not exposed in the collected logs;
these events establish recognition activity, not the exact transcript or a
successful activity result delivered to Silo.

A separate typed control entered “Star Wars” into Google's editor. Enter hid
the keyboard but left Google's activity open and Silo's query empty. This is
an observed control, not proof that Google's activity supports typed submission
through that key path.

Matt then tested TiviMate on the same Streamer and confirmed that voice search
entered text. Its 10:37 Pacific speech session logged:

```text
RecognitionService#logStartListening: callingApp: ar.tvplayer.tv, locale: en-US
RecognitionService#onStartOfSpeech
RecognitionClient: #onResults withSpeech: true
FinalS3RespProcessor: Received the final result for the recognition
```

There was no external `RECOGNIZE_SPEECH` activity launch in that comparison
window. TiviMate calls the service directly. Its similar-looking search bar
does not establish that it uses Google's separate activity.

## Why the Shield takes a different path

Upstream PR #860 (`484b55da5`) added `LegacyTvVoiceSearch.kt` for Android 11
with Google's activity provider and an available recognition service.
`TvVoiceSearch.kt` selects it only when `SDK_INT == R` (API 30).
The Android 14 Streamer therefore keeps the separate activity path. The TV
manifest also caps Silo's own `RECORD_AUDIO` permission at API 30.

The initial recommendation was to test upstream's existing service helper on
the Streamer with microphone permission available on API 34. Matt approved that
test. The old Silora controller and overlay have not been restored.

## Follow-up: Google's result callback does not finish its activity

At Matt's request, the installed Google TV provider was inspected without
modifying it or changing device settings. Its version remains
`7.55.87+1001226628.05`, version code `15040529`. The pulled base APK has SHA-256
`0bd136519790666234a89bdce0457df466424a9e3df16797c226183bb223a372`.
Apktool 2.9.3 disassembled its DEX bytecode locally. The APK and disassembly remain
private under the evidence directory's `provider-analysis/` subdirectory.

The installed bytecode establishes this call path:

1. `VoiceInputActivity.onResume()` creates a standard `SpeechRecognizer`, sets
   listener `kiv`, and passes the incoming activity intent to `startListening()`.
2. `kiv.onResults(Bundle)` reads `results_recognition`, stops listening, clears
   the listener and resets the microphone animation. It creates an intent with
   `android.speech.extra.RESULTS` and calls `setResult(-1, intent)` on the activity
   (`-1` is `RESULT_OK`).
3. That callback then invokes `kiw.a()`. This helper only calls
   `overridePendingTransition(0, 0)` and returns. It does **not** call `finish()`
   or schedule another operation that finishes the activity.
4. The activity's own `finish()` override does call its superclass and then
   `kiw.a()`, but the result callback never invokes that override. The complete
   superclass chain was checked: it does not override `setResult()` or
   `overridePendingTransition()` to add a hidden finish operation.

Android's [Activity.finish() contract](https://developer.android.com/reference/android/app/Activity#finish())
states that finishing the activity propagates its result to the caller.
Setting the result alone does not close the activity. The missing finish in
Google's callback therefore explains the observed combination of completed
recognition, an open Google screen and no result delivered to Silo. The
10:33:07.942 system log also records a redundant `stopListening` immediately
after the engine's final result, consistent with the callback's stop call.
The actual callback bundle was not captured, and no modified Google APK was
installed to test a repair.

The empty Google text field has a related explanation. Its partial-result
callback updates the field, but its final-result callback does not. Upstream
Silo does not request `EXTRA_PARTIAL_RESULTS`, and Google forwards that intent
unchanged. Android documents partial results as
[disabled by default](https://developer.android.com/reference/android/speech/RecognizerIntent#EXTRA_PARTIAL_RESULTS).
Thus the blank editor does not establish that transcription failed. Requesting
partial results might improve that editor's feedback, but would not add the
missing activity finish and was not tested as a workaround.

The direct-service helper bypasses this defective result wrapper and receives
`RecognitionListener.onResults()` in Silora itself. Both paths use the supported
`SpeechRecognizer` API underneath; "Legacy" is the upstream helper's name, not
an indication that Android has deprecated that API. The defect is established
for this installed Google provider version. Its introduction date and behavior
in other provider versions or Android releases remain unverified.

## Approved direct-service trial

The trial changes Google's service-helper condition from `SDK_INT == R` to
`SDK_INT >= R` and removes the API-30 cap on `RECORD_AUDIO`. The selected provider
must still be `com.google.android.katniss`, with an available recognition service.
Other cases keep upstream's activity path. The runtime recognition logic remains
upstream's implementation; the permission message uses the existing Silora
translation.

The signed release trial, `dist/local-2026-11-11.184425/silora-tv.apk`, was installed
as `org.silora.app`. Its version code is `854394131`. The unchanged upstream
comparison app remains installed separately. Authentication used the dedicated
test account, whose username was verified against its saved configuration;
no personal-account playback was performed.

Denying microphone access produced upstream's explanation. After access was
granted, the 10:52 Pacific session logged:

```text
RecognitionService#logStartListening: callingApp: org.silora.app, locale: en-US
TvVoiceSearch: Recognition ready
RecognitionClient: #onResults withSpeech: true
```

The listening dialog closed, the editable query became “hello,” and library
results loaded. Matt explicitly confirmed that he said “hello” and voice search
worked. This validates recognition and submission on the Streamer. The working
Android 11 selection remains in the code, but no fresh Shield test was performed.

All 1,427 TV host tests passed. Full TV release lint and signed release assembly
passed, with signature, application/version and archive checksum verification.
The final build changes only the permission message from the upstream Silo name
to the existing Silora translation. It passed the same tests, full release lint,
signed assembly and APK verification, and was installed as an update preserving
the test account. Final artifact:
`dist/local-2026-11-11.185305/silora-tv.apk`, TV version code `854395171`.

On the final APK, a listening session at 10:57:43 Pacific was canceled with
Back. The dialog disappeared, focus returned to the mic button and the service
logged `RecognitionService#onCancel`, `RecognitionClient: #cancel` and
`RecognitionService#onMicrophoneCloseRequested` at 10:57:44.

Matt then tested “Star Wars” on the final APK and confirmed success. The
10:58:20–10:58:23 session identifies `callingApp: org.silora.app` and a final
speech result. The dialog closed, the editable query was “Star Wars” and results
loaded. The installed APK SHA-256 matches the final archive. Private screenshots,
selected logs and `validation.json` are in the evidence directory's `trial/`
subdirectory.

## Evidence and boundaries

Private raw logs, activity snapshots and screenshots are under
`/tmp/silo-voice-streamer-20261111-WX4xjD/`; `findings.json` and
`speech-evidence-redacted.txt` contain the selected findings. Raw system logs
may contain unrelated private data and must not be published as collected.

The initial investigation used transient navigation and a typed control query.
The later approved trial changed the Kotlin selector and TV manifest, installed
a signed Silora APK, signed into the dedicated account and exercised microphone
permission. Matt then requested committing the validated change locally.
No app-data clearing, production service operation, push or public post occurred.
Raw evidence remains private.
