# Atmosphere Plugin for JavaClaw

[![CI](https://github.com/Atmosphere/javaclaw-atmosphere/actions/workflows/ci.yml/badge.svg)](https://github.com/Atmosphere/javaclaw-atmosphere/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/Atmosphere/javaclaw-atmosphere?label=release)](https://github.com/Atmosphere/javaclaw-atmosphere/releases)
[![Maven Central](https://img.shields.io/maven-central/v/org.atmosphere/javaclaw-atmosphere)](https://central.sonatype.com/artifact/org.atmosphere/javaclaw-atmosphere)

**Your JavaClaw agent's tools, available everywhere.**

Add one dependency and your JavaClaw agent becomes accessible from Claude Desktop, Cursor, VS Code, other AI agents, and messaging platforms — without changing a line of code.

## Quick Start

```groovy
// app/build.gradle
implementation 'org.atmosphere:javaclaw-atmosphere:0.1.2'
```

Run your app. That's it.

## What You Get

### Your tools work in Claude Desktop
JavaClaw's tools (web search, file system, shell, tasks) are automatically exposed via MCP. Configure Claude Desktop:
```json
{
  "mcpServers": {
    "javaclaw": { "url": "http://localhost:8080/atmosphere/mcp" }
  }
}
```
Now ask Claude: *"Search the web for latest AI agent frameworks"* — Claude calls your JavaClaw agent.

### Other agents can call yours
Your agent publishes an A2A Agent Card. Google ADK agents, CrewAI pipelines, and other Atmosphere agents discover and delegate tasks to your JavaClaw agent via JSON-RPC:
```
GET  http://localhost:8080/atmosphere/agent/javaclaw/a2a → Agent Card
POST http://localhost:8080/atmosphere/agent/javaclaw/a2a → message/send
```

### Streaming AI responses
Chat responses stream token-by-token like ChatGPT, instead of waiting for the full response. Multiple browser tabs work simultaneously.

### Transport resilience
Auto-reconnection, WebSocket → SSE → long-polling fallback. Connection drops recover transparently.

## Skills Exposed

When Atmosphere is added, these skills become discoverable via A2A and MCP:

| Skill | Description |
|-------|-------------|
| **ask** | Full agent interaction — uses ALL JavaClaw tools (web search, files, shell, tasks) |
| **search** | Web search via JavaClaw's configured provider |
| **task** | Create, list, or manage tasks |
| **files** | Read, write, edit workspace files |

## How It Works

```
JavaClaw boots
  → Spring Boot finds javaclaw-atmosphere on classpath
  → Auto-configures Atmosphere transport + A2A + MCP
  → Your agent's tools are exposed as discoverable skills
  → Claude Desktop / Cursor / VS Code / other agents can call them
```

Zero config. Zero code changes. One dependency.

## Requirements

- JavaClaw 1.0+
- Java 21+
- Spring Boot 4.0+
