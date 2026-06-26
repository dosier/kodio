[//]: # (title: Logging)

<show-structure for="chapter" depth="2"/>
<primary-label ref="core"/>
<secondary-label ref="multiplatform"/>

<tldr>
<p><b>Silent by default</b>: Kodio produces no log output until you configure a minimum level and at least one writer.</p>
</tldr>

Kodio includes a lightweight, multiplatform logging facade in `space.kodio.core.logging`. As a library, it stays **silent by default** so your app controls when and where logs appear — nothing is printed to the console unless you opt in.

## Enable logging {id="enable"}

Call `Kodio.configureLogging { }` at application startup (for example in `main()`, `Application.onCreate`, or your Compose entry point):

```kotlin
import space.kodio.core.Kodio
import space.kodio.core.logging.LogLevel
import space.kodio.core.logging.platformLogWriter

Kodio.configureLogging {
    minLevel = LogLevel.Debug
    addWriter(platformLogWriter())
}
```

[`platformLogWriter()`](API-Reference.md) is a built-in writer that routes output to the platform console:

| Platform | Output |
|----------|--------|
| Android | Logcat |
| iOS / macOS | NSLog |
| JVM / Desktop | stdout / stderr |
| JS / Wasm | Browser console |

You can also configure logging directly via [`KodioLogging`](API-Reference.md):

```kotlin
import space.kodio.core.logging.KodioLogging
import space.kodio.core.logging.LogLevel
import space.kodio.core.logging.platformLogWriter

KodioLogging.configure {
    minLevel = LogLevel.Info
    addWriter(platformLogWriter())
}
```

## Disable logging {id="disable"}

Logging is off by default — if you never call `configureLogging`, Kodio stays silent. To explicitly disable output:

```kotlin
Kodio.configureLogging {
    minLevel = LogLevel.None
}
```

Or clear writers:

```kotlin
KodioLogging.clearWriters()
```

## Log levels {id="levels"}

Levels are defined by the `LogLevel` enum. A message is emitted when its level is **at or above** the configured `minLevel`:

<deflist type="medium">
<def title="Trace">
Very verbose diagnostics — per-sample or per-chunk detail. Use sparingly.
</def>
<def title="Debug">
Routine internal diagnostics useful during development.
</def>
<def title="Info">
Lifecycle milestones — recording started, device selected, session released.
</def>
<def title="Warn">
Recoverable problems — fallback paths, deprecated usage, missing optional features.
</def>
<def title="Error">
Failures and caught exceptions. Prefer <code>error(throwable) { }</code> to attach the cause.
</def>
<def title="None">
Disables all output when set as <code>minLevel</code>.
</def>
</deflist>

## Bridge to a custom backend {id="custom-writer"}

Implement [`KodioLogWriter`](API-Reference.md) — a single-method functional interface — to forward Kodio logs to any backend (Kermit, SLF4J, Crashlytics, your own logger):

```kotlin
import space.kodio.core.Kodio
import space.kodio.core.logging.LogLevel

Kodio.configureLogging {
    minLevel = LogLevel.Warn
    addWriter { level, tag, message, throwable ->
        MyLogger.log(level.name, tag, message, throwable)
    }
}
```

You can register multiple writers; each log event is forwarded to every registered writer. Use `setWriters()` to replace the full set, `addWriter()` to append, and `clearWriters()` to remove all.

> [`NoOpLogWriter`](API-Reference.md) discards all events and is the implicit default when no writers are configured.
>
{style="note"}

## Create loggers {id="loggers"}

Use `kodioLogger(name)` to obtain a tagged logger with lazy message evaluation:

```kotlin
import space.kodio.core.logging.kodioLogger

private val logger = kodioLogger("MyComponent")

fun doWork() {
    logger.debug { "Starting work" }
    logger.info { "Work complete" }
    logger.warn { "Recoverable issue: ${details()}" }
    logger.error(exception) { "Operation failed" }
}
```

Messages are evaluated only when the configured `minLevel` allows the event through. Check whether a level would emit with `KodioLogging.isLoggable(level)`.

## Platform coverage {id="platforms"}

The logging facade works uniformly across all Kodio targets — Android, iOS, macOS, JVM, JS, and Wasm. No platform-specific setup is required beyond calling `configureLogging` in your shared or platform entry point.

<seealso style="cards">
    <category ref="core-api">
        <a href="Error-Handling.md" summary="Typed error hierarchy">Error Handling</a>
        <a href="Platform-Setup.md" summary="Per-platform setup">Platform Setup</a>
    </category>
</seealso>
