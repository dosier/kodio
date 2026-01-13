[//]: # (title: Migration)

<show-structure for="chapter" depth="2"/>

Guide for upgrading between Kodio versions.

## 0.0.5 to 0.0.6 {id="0-0-5-to-0-0-6"}

Error types are now classes for proper stack traces:

<tabs>
<tab title="Before">

```kotlin
when (error) {
    AudioError.PermissionDenied -> ...
    AudioError.NotInitialized -> ...
}
```

</tab>
<tab title="After">

```kotlin
when (error) {
    is AudioError.PermissionDenied -> ...
    is AudioError.NotInitialized -> ...
}
```

</tab>
</tabs>

> Use `is` for pattern matching. Direct equality (`==`) no longer works.
>
{style="warning"}

## Session API to high-level API {id="session-to-high-level"}

<tabs>
<tab title="Session API">

```kotlin
val session = SystemAudioSystem.createRecordingSession()
session.start()
delay(5000)
session.stop()
val audioFlow = session.audioFlow.value!!
```

</tab>
<tab title="High-level API">

```kotlin
val recording = Kodio.record(duration = 5.seconds)
recording.play()
```

</tab>
</tabs>

## Compose migration {id="compose-migration"}

<tabs>
<tab title="Manual state">

```kotlin
var session by remember { ... }
var isRecording by remember { ... }
DisposableEffect(Unit) { ... }
```

</tab>
<tab title="RecorderState">

```kotlin
val recorderState = rememberRecorderState()
Button(onClick = { recorderState.toggle() }) { ... }
```

</tab>
</tabs>

## Accessing sessions {id="escape-hatch"}

The session API is still available for advanced use:

```kotlin
@Suppress("DEPRECATION")
val session = recorder.underlyingSession()
```

<seealso style="cards">
    <category ref="core-api">
        <a href="Recording.md"/>
        <a href="Error-Handling.md"/>
    </category>
</seealso>
