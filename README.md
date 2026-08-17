# DTEmpire AI Chat Plugin

A **Paper/Spigot Minecraft plugin** that adds:
- **Private AI Chat** — players talk 1-on-1 with an AI via `/aichat`
- **Live Discord Server Status** — one embed in your Discord channel that auto-updates (no spam)
- **Join/Leave Tracking + Playtime Leaderboards** — persisted in SQLite (survives restarts)
- **Minecraft-Only AI Filter** — AI refuses non-Minecraft topics

---

## Features

### 🤖 Private AI Chat
| Command | Description |
|---------|-------------|
| `/aichat <message>` | Start or continue a private AI chat |
| `/aiexit` | End your AI chat session |
| `/aihelp` | List AI commands |

Each player gets their own private conversation. Messages are invisible to others.

### 📊 Discord Server Status (Auto-updating Embed)
A **single embed** in your Discord channel that edits itself — never posts duplicates.

Shows:
- **Online** — `X/Y` players
- **RAM** — Used / Allocated + %
- **CPU** — %
- **Storage** — Used / Allocated + %
- **Private AI Chat** — Active sessions count
- **Top Online** — 🥇🥈🥉 by accumulated playtime (persisted)
- **Recently Joined** — Last 3 unique players
- **Recently Left** — Last 3 unique players
- **Game Mode** — survival / creative / etc.
- **Server Version** — Paper version
- **Server IP** — Copyable code block (manual config)

### ⏱ Tracking Interval
- Default **1 minute** (configurable)
- **Instant updates** on player join/leave

### 🗃 SQLite Persistence
`plugins/DTEmpireAIChat/tracking.db` stores:
- `playtime` table — accumulated minutes per player (never resets)
- `recent_joins` / `recent_leaves` — last 10 unique events
- Survives restarts, crashes, redeploys

### 🛡 Minecraft-Only AI Filter
When enabled (default), AI **refuses** non-Minecraft questions (e.g. coding, real life, other games) with a polite message.

---

## Installation

1. Drop `DTEmpireAIChat.jar` into `plugins/`
2. Start server → generates `config.yml`
3. Edit `plugins/DTEmpireAIChat/config.yml`:

```yaml
# Required for Discord tracking
tracking:
  enabled: true
  discord:
    webhook-url: "https://discord.com/api/webhooks/..."   # or bot-token + channel-id
  resources:
    ram-max-mb: 11344         # How much RAM the server WAS GIVEN (from panel/-Xmx)
    storage-max-gb: 193       # Storage allocation in panel
  server:
    name: "WarmBrew SMP"
    motd: "Sit back, relax & enjoy the survival vibe."
    gamemode: "survival"
    ip: "play.warmbrew.example:25565"
```

4. Run `/dtempireai restart` (or restart server)

---

## Commands & Permissions

| Command | Permission | Description |
|---------|------------|-------------|
| `/aichat` | `dtempire.aichat` | Private AI chat |
| `/aiexit` | `dtempire.aichat` | Exit AI chat |
| `/aihelp` | `dtempire.aichat` | Show AI help |
| `/dtstatus` | `dtempire.tracking.status` | Post status to Discord now |
| `/dttracking <on\|off>` | `dtempire.tracking.toggle` | Toggle auto-updates |
| `/dtempireai <restart\|reload\|status>` | `dtempire.tracking.admin` | Reload config & restart tracking |

---

## Configuration Reference

```yaml
api:
  base-url: "https://your-llm-endpoint/v1"
  model: "YourModelName"
  api-key: "sk-..."
  timeout-ms: 30000
  system-prompt: "You are a helpful Minecraft AI..."

session:
  inactivity-timeout: 300
  max-history: 20

messages:
  ai-prefix: "&8[&bAI&8] &r"
  player-prefix: "&8[&aYou&8] &r"
  # ... (all messages customizable)

ai-filter:
  minecraft-only: true
  refusal: "&cI can only help with Minecraft and DTEmpire server topics."

tracking:
  enabled: false
  interval-minutes: 1
  discord:
    webhook-url: ""
    bot-token: ""
    channel-id: ""
  server:
    name: "DTEmpire"
    motd: "Welcome to DTEmpire!"
    gamemode: "survival"
    ip: ""
  embed:
    show-online: true
    show-ram: true
    show-cpu: true
    show-storage: true
    show-ai-sessions: true
    show-top-online: true
    show-recent-joins: true
    show-recent-leaves: true
    show-gamemode: true
    show-server-version: true
    show-ip: true
  thresholds:
    ram-warning-percent: 80
    cpu-warning-percent: 80
    storage-warning-percent: 90
  resources:
    ram-max-mb: 0          # 0 = auto-detect JVM max
    storage-max-gb: 0      # 0 = auto-detect disk total

welcome:
  enabled: true
  message: "&8[&bDTEmpire&8] &rWelcome &f{player}&r! Type &e/aihelp&r for AI features."
  delay-ticks: 40
  show-aihelp: true
```

---

## Build from Source

```bash
# Requires Java 17 + Maven
mvn -q -DskipTests package
# Output: target/DTEmpireAIChat.jar (includes shaded sqlite-jdbc)
```

---

## Notes

- **Discord embed single-message** — if you see duplicates, delete the old ones; the plugin will only edit the latest.
- **RAM/Storage %** uses your `resources.*` config. Set to your panel allocation for accurate %.
- **Playtime leaderboards** accumulate forever. A player who left weeks ago stays on the board until someone overtakes them.
- **AI filter** is strict by design — turn off `ai-filter.minecraft-only` if you want open-ended chat.