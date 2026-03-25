# Atmosphere Plugin for JavaClaw

[![CI](https://github.com/Atmosphere/javaclaw-atmosphere/actions/workflows/ci.yml/badge.svg)](https://github.com/Atmosphere/javaclaw-atmosphere/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/Atmosphere/javaclaw-atmosphere?label=release)](https://github.com/Atmosphere/javaclaw-atmosphere/releases)
[![Maven Central](https://img.shields.io/maven-central/v/org.atmosphere/javaclaw-atmosphere)](https://central.sonatype.com/artifact/org.atmosphere/javaclaw-atmosphere)

## When You Need It

**"Can I use my agent from VS Code?"** Yes.

**"Can responses stream word-by-word like ChatGPT?"** Yes.

**"Can my team share the agent?"** Yes.

**"Can other AI agents call mine?"** Yes.

```groovy
implementation 'org.atmosphere:javaclaw-atmosphere:0.2.0'
```

## What It Does

Adds real-time transport and protocol support to your JavaClaw agent. Nothing changes in your agent code — Atmosphere plugs in alongside and exposes your existing skills through additional protocols.

### Streaming responses
JavaClaw's default `agent.respondTo()` returns the full response at once. With Atmosphere, tokens stream to the browser word-by-word. Your users see the answer forming in real-time.

### Use your agent from any IDE
Your agent's skills become available as MCP tools. Configure your IDE once:

```json
{
  "mcpServers": {
    "javaclaw": { "url": "http://localhost:8080/atmosphere/mcp" }
  }
}
```

Then use your JavaClaw agent's web search, file operations, and task management directly from Cursor, VS Code, Windsurf, or any MCP-compatible tool — without leaving your editor.

### Multiple users, same agent
JavaClaw's web chat supports one session. With Atmosphere, multiple browser tabs and multiple users connect simultaneously. Useful when a team shares an agent for research, planning, or daily standups.

### Agent-to-agent communication
Your agent publishes an A2A Agent Card. Other AI agents — Google ADK, Atmosphere multi-agent teams, or custom agents — can discover yours and delegate tasks via JSON-RPC. Your personal assistant becomes a participant in larger workflows.

### Auto-reconnection
Connection drops (laptop sleep, network switch, page refresh) recover automatically. No lost context, no manual reconnect.

## Setup

```groovy
// app/build.gradle
implementation 'org.atmosphere:javaclaw-atmosphere:0.2.0'
```

Run your app. Atmosphere auto-configures when it detects JavaClaw's `Agent` bean on the classpath.

## What Gets Exposed

All your existing JavaClaw skills and tools are automatically available through:

| Protocol | Endpoint | Used by |
|----------|----------|---------|
| WebSocket | `/atmosphere/chat` | Browser (streaming) |
| MCP | `/atmosphere/mcp` | Cursor, VS Code, Windsurf, Claude Desktop |
| A2A | `/atmosphere/agent/javaclaw/a2a` | Other AI agents |

## Requirements

- JavaClaw 1.0+
- Java 21+
- Spring Boot 4.0+
