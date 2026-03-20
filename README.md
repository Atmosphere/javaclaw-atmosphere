# Atmosphere Chat Transport for JavaClaw

Drop-in replacement for [JavaClaw](https://github.com/jobrunr/JavaClaw)'s Spring WebSocket chat with [Atmosphere](https://github.com/Atmosphere/atmosphere), adding:

- **Multi-client support** — multiple browser tabs work simultaneously (the default `ChatChannel` only tracks one `WebSocketSession`)
- **Transport fallback** — WebSocket → SSE → long-polling, transparent to the UI
- **Auto-reconnection** — configurable backoff, no manual refresh needed
- **Proxy-friendly** — works behind corporate firewalls that block WebSocket

## What gets replaced

| Original (Spring WebSocket) | Atmosphere replacement |
|------------------------------|------------------------|
| `ChatChannel.java` | `AtmosphereChatChannel` — uses `Broadcaster` instead of `AtomicReference<WebSocketSession>` |
| `ChatWebSocketHandler.java` | `AtmosphereChatHandler` — implements `AtmosphereHandler` |
| `WebSocketConfig.java` | Auto-configured by `AtmosphereChannelAutoConfiguration` |
| `htmx-ext-ws` (client) | `atmosphere.js` + `javaclaw-atmosphere.js` |

## Quick start

### Prerequisites

Build and install both projects locally:

```bash
# 1. Build Atmosphere (includes the Spring Boot starter)
cd /path/to/atmosphere
./mvnw install -Pfastinstall

# 2. Build JavaClaw
cd /path/to/JavaClaw
./gradlew publishToMavenLocal

# 3. Build this plugin
cd /path/to/javaclaw-atmosphere
./gradlew build
```

### Integration

#### 1. Add the dependency

In JavaClaw's `app/build.gradle`:

```gradle
dependencies {
    implementation project(':plugins:atmosphere')
    // ... or if using published artifact:
    // implementation 'org.atmosphere:javaclaw-atmosphere:1.0.0-SNAPSHOT'
}
```

#### 2. Disable the default Spring WebSocket chat

In JavaClaw's `app/build.gradle`, remove or comment out:

```gradle
// implementation 'org.springframework.boot:spring-boot-starter-websocket'
```

And exclude the default chat classes. Add to `JavaClawApplication.java`:

```java
@SpringBootApplication(exclude = {
    // Prevents Spring's WebSocket auto-configuration
    org.springframework.boot.autoconfigure.websocket.servlet.WebSocketServletAutoConfiguration.class
})
```

#### 3. Configure Atmosphere

In JavaClaw's `application.yaml`:

```yaml
atmosphere:
  servlet-path: /ws/*
```

This maps the Atmosphere servlet to `/ws/*`, making the chat handler available at `/ws/chat` — the same path the original WebSocket used.

#### 4. Replace the chat template

Copy the provided template over JavaClaw's original:

```bash
cp src/main/resources/templates/chat.html.peb \
   /path/to/JavaClaw/app/src/main/resources/templates/chat.html.peb
```

The template changes are minimal:
- Removes `hx-ext="ws" ws-connect="/ws/chat"` from the outer div
- Replaces `htmx-ext-ws` script with `atmosphere.js` + `javaclaw-atmosphere.js`
- Form no longer uses `ws-send` — atmosphere.js handles submission

#### 5. Run

```bash
cd /path/to/JavaClaw
./gradlew bootRun
```

Open `http://localhost:8080/chat` — open multiple tabs to verify multi-client works.

## Architecture

```
Browser                    Server
  |                          |
  |--- atmosphere.js ------->|  AtmosphereServlet (/ws/*)
  |    (WebSocket/SSE/LP)    |       |
  |                          |  AtmosphereChatHandler (/chat)
  |                          |       |
  |                          |  AtmosphereChatChannel
  |                          |       |--- Agent.respondTo()
  |                          |       |--- Broadcaster.broadcast()
  |<-- htmx OOB HTML -------|       |
  |    (same format)         |  ChannelRegistry
```

The HTML payload format (htmx OOB swaps) is identical to the original — only the transport layer changes.

## Configuration

All standard `atmosphere.*` Spring Boot properties are supported:

```yaml
atmosphere:
  servlet-path: /ws/*
  heartbeat-interval: 30s
  session-support: false
  websocket-support: true
```

## As a JavaClaw plugin (PR approach)

To contribute this as a native JavaClaw plugin in `plugins/atmosphere/`:

1. Move the source into `plugins/atmosphere/` in the JavaClaw repo
2. Add to `settings.gradle`: `include 'plugins:atmosphere'`
3. Make the original `ChatChannel`, `ChatWebSocketHandler`, and `WebSocketConfig` conditional:
   ```java
   @ConditionalOnProperty(name = "javaclaw.chat.transport",
       havingValue = "spring-websocket", matchIfMissing = true)
   ```
4. The Atmosphere plugin activates with: `javaclaw.chat.transport=atmosphere`

## License

Apache License 2.0 — same as Atmosphere and JavaClaw.
