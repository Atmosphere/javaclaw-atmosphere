# Atmosphere Chat Transport for JavaClaw

[![CI](https://github.com/Atmosphere/javaclaw-atmosphere/actions/workflows/ci.yml/badge.svg)](https://github.com/Atmosphere/javaclaw-atmosphere/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/Atmosphere/javaclaw-atmosphere?label=release)](https://github.com/Atmosphere/javaclaw-atmosphere/releases)
[![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0)

Drop-in replacement for [JavaClaw](https://github.com/jobrunr/JavaClaw)'s Spring WebSocket chat with [Atmosphere](https://github.com/Atmosphere/atmosphere).

## What you get

### Streaming AI responses

The default JavaClaw chat blocks for 10-30 seconds showing typing dots, then dumps the full response at once. With Atmosphere, responses stream **word by word** — the same experience as ChatGPT, Claude, and other modern AI chats.

Uses the same `ChatClient` bean (same advisors, tools, memory) — just calls `.stream()` instead of `.call()`.

### Live markdown rendering

AI responses are rendered as rich markdown in real-time as tokens arrive: **bold**, `code`, code blocks, lists, tables, blockquotes — all formatted live during streaming via [marked.js](https://marked.js.org/).

### Tool call visualization

When the agent invokes tools (shell, file system, web fetch, skills), the UI shows what it's doing in real-time instead of just typing dots.

### Multi-client support

The default `ChatChannel` tracks a single `WebSocketSession` via `AtomicReference`. Open two browser tabs and the second one steals the connection — messages sent from tab A get responses delivered to tab B.

Atmosphere's `Broadcaster` handles any number of simultaneous clients natively.

### Transport fallback and reconnection

- **Automatic fallback** — WebSocket → SSE → long-polling, transparent to the UI
- **Auto-reconnection** — configurable backoff, no manual refresh needed
- **Proxy-friendly** — works behind corporate firewalls that block WebSocket

### Conversation history on reconnect

With Atmosphere's `UUIDBroadcasterCache`, messages sent while a client is disconnected are replayed on reconnect. Refresh the page and you don't lose the conversation.

## What gets replaced

| Original (Spring WebSocket) | Atmosphere replacement |
|------------------------------|------------------------|
| `ChatChannel.java` | `AtmosphereChatChannel` — uses `Broadcaster` for multi-client fan-out |
| `ChatWebSocketHandler.java` | `AtmosphereChatHandler` — streams tokens via `ChatClient.stream()` |
| `WebSocketConfig.java` | Auto-configured by `AtmosphereChannelAutoConfiguration` |
| `htmx-ext-ws` (client) | `atmosphere.js` v5 + `javaclaw-atmosphere.js` |

## Integration

### Prerequisites

Once Atmosphere is released to Maven Central, skip this step. Until then, build from source:

```bash
# Build Atmosphere (includes the Spring Boot starter)
cd /path/to/atmosphere
./mvnw install -Pfastinstall
```

### Step 1: Enable pluggable chat transport

JavaClaw needs [this 6-line PR](https://github.com/jobrunr/JavaClaw/pull/15) merged — it adds `@ConditionalOnProperty` to the three default chat classes so they can be swapped out via a property. Default behavior is unchanged.

### Step 2: Add the Atmosphere plugin

Copy `plugins/atmosphere/` into the JavaClaw repo, or add as a dependency:

```gradle
// settings.gradle
include 'plugins:atmosphere'

// app/build.gradle
implementation project(':plugins:atmosphere')
```

### Step 3: Configure

In `application.yaml`:

```yaml
javaclaw:
  chat:
    transport: atmosphere

atmosphere:
  servlet-path: /atmosphere/*
  broadcaster-cache-class: org.atmosphere.cache.UUIDBroadcasterCache
```

### Step 4: Replace the chat template

Copy the provided template:

```bash
cp src/main/resources/templates/chat.html.peb \
   /path/to/JavaClaw/app/src/main/resources/templates/chat.html.peb
```

The template changes:
- Removes `hx-ext="ws" ws-connect="/ws/chat"` — Atmosphere handles the connection
- Replaces `htmx-ext-ws` with `atmosphere.js` v5 (bundled) + `marked.js` (CDN)
- Adds CSS for markdown rendering and tool call visualization
- Form submission handled by atmosphere.js instead of htmx

### Step 5: Run

```bash
./gradlew bootRun
```

Open `http://localhost:8080/chat` — responses stream word by word with live markdown. Open multiple tabs to verify multi-client works.

## Architecture

```
Browser                         Server
  |                               |
  |--- atmosphere.js (v5) ------->|  AtmosphereServlet (/atmosphere/*)
  |    WebSocket / SSE / LP       |       |
  |                               |  AtmosphereChatHandler
  |                               |       |
  |                               |       |--- ChatClient.stream()  (same bean, same advisors)
  |<-- {"token": "Hello"} --------|       |      |
  |<-- {"token": " world"} -------|       |      +-- tokens via Reactor Flux
  |<-- {"tool": "WebSearch"} -----|       |      +-- tool call events
  |<-- {"done": true} ------------|       |
  |                               |  AtmosphereChatChannel
  |<-- OOB HTML (background) -----|       |--- Broadcaster.broadcast()
  |                               |       |--- UUIDBroadcasterCache (reconnect replay)
  |                               |
  |                               |  ChannelRegistry (unchanged)
```

**Protocol:**
- OOB HTML for structural changes (user bubble, typing indicator, background messages)
- `{"token": "text"}` for streaming AI response tokens (rendered as markdown)
- `{"tool": "name"}` for tool call visualization
- `{"done": true}` signals stream completion

## Configuration

All standard `atmosphere.*` Spring Boot properties are supported:

```yaml
atmosphere:
  servlet-path: /atmosphere/*
  heartbeat-interval: 30s
  broadcaster-cache-class: org.atmosphere.cache.UUIDBroadcasterCache
  session-support: false
  websocket-support: true
```

## License

Apache License 2.0 — same as Atmosphere and JavaClaw.
