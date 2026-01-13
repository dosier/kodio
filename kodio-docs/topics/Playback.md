[//]: # (title: Playback)

<show-structure for="chapter" depth="2"/>
<primary-label ref="core"/>

<tldr>
<p><b>Play audio</b> with <code>recording.play()</code> for simple playback or <code>Kodio.player()</code> for full control.</p>
</tldr>

Kodio provides flexible audio playback APIs, from a simple one-liner to a full-featured `Player` class with pause, resume, and device selection.

## Simple playback {id="simple"}

The easiest way to play audio is directly on a recording. This suspends until playback completes:

```kotlin
recording.play()
```

## Playback with controls {id="with-controls"}

When you need to pause, resume, or monitor playback, use `Kodio.play()` with a lambda:

```kotlin
Kodio.play(recording) { player ->
    player.start()
    
    // Pause after 2 seconds
    delay(2.seconds)
    player.pause()
    
    // Resume after 1 second
    delay(1.seconds)
    player.resume()
    
    // Wait for playback to finish
    player.awaitComplete()
}
```

The lambda receives a `Player` instance that gives you full control over playback.

## Using Player directly {id="player"}

For maximum flexibility, create a `Player` instance directly. This is useful for:
- Loading different recordings into the same player
- Managing the player's lifecycle explicitly
- Building custom playback UIs

```kotlin
val player = Kodio.player()

player.use { p ->
    p.load(recording)
    p.start()
    p.awaitComplete()
}
```

## Output device selection {id="device"}

Play audio to a specific output device (headphones, speakers, etc.):

```kotlin
// List available output devices
val outputs = Kodio.listOutputDevices()
println(outputs.map { it.name })  // ["Built-in Speakers", "AirPods Pro", ...]

// Play to a specific device
val headphones = outputs.find { it.name.contains("AirPods") }
Kodio.play(recording, device = headphones)
```

> Device selection is fully supported on JVM, iOS, and macOS. On Android, the system manages audio routing. On Web, it depends on browser support.
>
{style="note"}

## Player API reference {id="api-reference"}

### Properties {id="properties" collapsible="true"}

<deflist type="medium">
<def title="isPlaying: Boolean">
<code>true</code> while audio is actively playing.
</def>
<def title="isPaused: Boolean">
<code>true</code> if playback was started and then paused.
</def>
<def title="isReady: Boolean">
<code>true</code> when audio is loaded and ready to play.
</def>
<def title="isFinished: Boolean">
<code>true</code> after playback has completed.
</def>
<def title="stateFlow: StateFlow<State>">
Observable state changes for reactive UIs.
</def>
</deflist>

### Methods {id="methods" collapsible="true"}

<deflist type="medium">
<def title="load(recording)">
Load an <code>AudioRecording</code> for playback.
</def>
<def title="start()">
Begin or resume playback.
</def>
<def title="pause()">
Pause playback. Use <code>resume()</code> to continue.
</def>
<def title="resume()">
Continue playback after pausing.
</def>
<def title="stop()">
Stop playback and reset to the beginning.
</def>
<def title="toggle()">
Play if stopped/paused, pause if playing. Convenient for single-button UIs.
</def>
<def title="release()">
Release all resources. Called automatically when using <code>use {}</code>.
</def>
<def title="awaitComplete()">
Suspend until playback finishes.
</def>
</deflist>

<seealso style="cards">
    <category ref="core-api">
        <a href="Recording.md" summary="Record audio">Recording</a>
        <a href="Device-Selection.md" summary="Choose input/output devices">Device Selection</a>
        <a href="Error-Handling.md" summary="Handle playback errors">Error Handling</a>
    </category>
</seealso>
