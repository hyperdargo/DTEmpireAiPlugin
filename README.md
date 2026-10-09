# DTEmpire AI Game Master & Anti-Cheat Plugin

[![Release](https://img.shields.io/github/v/release/hyperdargo/DTEmpireAiPlugin?style=flat-square)](https://github.com/hyperdargo/DTEmpireAiPlugin/releases/latest)
[![Paper 1.21+](https://img.shields.io/badge/Paper-1.21%2B-blue?style=flat-square)](https://papermc.io)
[![License](https://img.shields.io/badge/license-MIT-green?style=flat-square)](LICENSE)

An intelligent, autonomous **Paper/Spigot Minecraft plugin (Java 17+, 1.21+)** powered by Hermes AI that transforms your server with:
- 🛡️ **Hypixel-Style Watchdog Anti-Cheat** — Invisible orbiting KillAura trap bot, movement/combat checks, auto-bans & Discord appeal screens
- 👑 **Autonomous AI Game Master (`/aiadmin`)** — Dynamic scheduled server events (Meteor Drops, Blood Moons, Golden Hours)
- 🧠 **Player Telemetry & Archetype Profiling** — Tracks playstyles (Miner, Builder, Warrior, Explorer, Survivor)
- 🎁 **Adaptive Sympathy Care Packages** — AI detects death streaks or lava loss and delivers survival relief
- 📜 **Tailored Daily Tasks (`/aidaily`)** — Daily quests and bounties customized to each player's playstyle
- 💬 **Public Chat AI Observer & Private Chat (`/aichat`)** — Hermes observes chat, answers questions when tagged (`@ai`) or needed, and stays quiet during banter
- 🎉 **Cinematic First-Join Welcomes** — Server broadcast, title banner, celebratory fireworks, starter kit & AI whispers
- 🔄 **GitHub Auto-Updater** — Automatically downloads new releases into `plugins/update/`
- 📊 **Live Discord Tracking & Leaderboards** — Real-time self-updating embed and SQLite persistence

---

## ✨ Key Features

### 🛡️ Hypixel-Style Watchdog Anti-Cheat
Keep your server clean and fair with automated heuristic detection and honeypot bot traps:
- **Orbiting KillAura Honeypot Bot:** When a player is reported or suspicious, Watchdog spawns an invisible rotating bot (`Watchdog`) around their head. Natural players never attack it, while automated KillAura/TriggerBot cheats lock onto it and get caught immediately.
- **Combat & Movement Checks:**
  - **Reach Check:** Detects combat hits exceeding 3.9 blocks in survival mode.
  - **Angle Check:** Flags impossible attack angles outside natural field-of-view (> 95°).
  - **AutoClicker / CPS:** Rolling click history detecting CPS exceeding 20 clicks/sec.
  - **Flight / Glide Check:** Detects prolonged mid-air hovering (> 40 ticks without descent).
  - **Speed / Bhop Check:** Flags unnatural horizontal velocity (> 0.72 blocks/tick).
  - **Jesus / Water Walk:** Detects walking across liquid surfaces without submerging.
- **Server-Wide Ban Announcements & Discord Appeals:**
  - Broadcasts authentic Hypixel-style ban wave announcements.
  - Generates unique `#WD-XXXXXXXX` ban IDs stored in SQLite.
  - Displays ban ID, reason, and a direct Discord appeal link on both kick and login screens.

### 👑 Autonomous AI Game Master (`/aiadmin`)
Hermes acts as an autonomous server director, scheduling and triggering world events:
- **Celestial Meteor Supply Drop:** Strikes lightning and spawns a supply chest loaded with diamonds, golden apples, and totems at random surface coordinates near active players.
- **Blood Moon:** Midnight thunderstorm empowering monsters with glowing and speed, granting surviving players double XP until sunrise.
- **Golden Hour:** Blesses active players with Haste and Regeneration buffs for 15 minutes.
- **Admin Control:** Staff can trigger events, send gifts, check player telemetry, or inspect stats anytime via `/aiadmin`.

### ⚔️ Anarchy & SMP Survival Systems
- **Combat Tagging & Anti-Combat-Log:**
  - Engaging in PvP triggers a 15-second combat tag with real-time actionbar countdown (`⚔ In Combat: 14s | Do not disconnect!`).
  - Blocks teleport/escape commands (`/spawn`, `/tpa`, `/home`, etc.) during combat.
  - If a player disconnects while combat-tagged, they are **instantly eliminated** and drop all items. The attacker gets the kill credit, head trophy, and any active bounty!
- **Decapitated Player Head Trophies:**
  - Slain players drop their custom player skull item with victim texture and engraved lore (killer name, date, and conquest inscription).
- **Diamond Bounty System (`/bounty`):**
  - Players can fund bounties using in-game Diamonds (`/bounty place <player> <amount>`).
  - View top wanted criminals anytime with `/bounty list`.
  - Killing a wanted player automatically deposits the Diamond bounty directly into the killer's inventory!
- **Autonomous AI Bounties & Killstreaks:**
  - Players on 3, 5, and 10 killstreaks trigger global server announcements and speed/resistance buffs.
  - When a player reaches a 5-kill rampage, **Hermes AI autonomously places a 5-Diamond bounty on their head**, turning them into a high-value wilderness target!
- **Hermes AI Death Roasts:**
  - Accidental or humiliating deaths (lava baths, falling, suffocation in walls, drowning, baby zombies) trigger hilarious 1-line roasts from Hermes in chat.
- **Anti-Crash Exploit Guard:**
  - Blocks oversized Unicode books and illegal NBT data to prevent book-ban and chunk-ban server crashes.

### 🧠 Adaptive Telemetry & Sympathy Care Packages
- Real-time logging of blocks mined, rare ores, placements, mob kills, PvP kills, deaths, and distance.
- Categorizes players into archetypes:
  - ⛏️ **Miner** — deep cave dwellers & ore hunters
  - 🔨 **Builder** — architects & structure builders
  - ⚔️ **Warrior** — PvE/PvP fighters
  - 🧭 **Explorer** — surface travelers
  - 🛡️ **Survivor** — balanced survivalists
- **Sympathy Gifts:** If a player dies 3+ times in rapid succession or falls into lava, Hermes delivers a sympathy care package (Golden Apples, Fire Resistance potions, tools, food) with a 30-minute cooldown.

### 📜 Personalized Daily Tasks (`/aidaily`)
- Each player receives 1 daily quest tailored specifically to their archetype.
- Command `/aidaily` shows a visual progress bar: `[■■■■■■□□□□] (6/10)`.
- Completing tasks awards bounties (diamonds, building materials, combat elixirs) via `/aidaily claim` with celebratory fireworks.

### 💬 Global Chat AI Helper & Private Chat
- **Global Chat AI Assistant (`hey aichat`, `message ai`, `/ai`):**
  - Players can ask Hermes to explain, translate, or answer questions directly in global chat!
  - **Natural Chat Triggers:** Type naturally in chat:
    - `hey aichat tell him how to make a nether portal`
    - `hey ai tell @Steve diamonds spawn at Y -58`
    - `message ai send msg in global chat how to craft anvil`
    - `hey ai translate to english: ma 5 min ma aauxu`
    - `ai how to breed villagers` or `@ai what is the server ip`
  - **Player-to-Player Translation & Assistance:** Perfect for players who want to help another player but don't want to type long explanations or aren't fluent in English — Hermes explains clearly, addresses the target player, and keeps answers under 2 lines of chat!
  - **Commands:** `/ai <message>`, `/messageai <message>`, or `/aichat global <message>`.
- **Private Chat Session:** Players can initiate a private 1-on-1 AI chat with `/aichat <message>` without other players seeing it. Use `/aiexit` to finish.
- **Server-Aware Lore:** Hermes knows server rules, features, commands, and Discord links, ensuring accurate answers rather than generic vanilla facts.

### 🎉 Cinematic First-Join Welcome Experience
- First-time players receive a server-wide welcome broadcast, title banner, celebratory fireworks, starter food and tools, and a personal welcome whisper from Hermes.
- Returning players receive a welcome back message with their daily quest status.

### 🔄 GitHub Auto-Updater
- Checks `hyperdargo/DTEmpireAiPlugin` GitHub releases periodically.
- Automatically downloads newer `.jar` releases into `plugins/update/DTEmpireAIChat.jar` to be safely applied on the next restart or reload.
- Manual check anytime via `/dtempireai update`.

---

## 📋 Commands & Permissions

| Command | Permission | Description |
|---|---|---|
| `/ai <question>` | `dtempire.aichat` | Ask AI to answer/explain/translate in global chat *(Aliases: /messageai, /heyai)* |
| `@ai <question>` | *(Public chat)* | Ask Hermes directly in public server chat *(or type: hey aichat, message ai)* |
| `/aichat <message>` | `dtempire.aichat` | Start or continue a private AI chat |
| `/aiexit` | `dtempire.aichat` | End private AI chat session |
| `/aihelp` | `dtempire.aichat` | Show AI commands and help guide |
| `/aidaily [claim]` | `dtempire.daily` | View or claim your tailored daily quest and bounty |
| `/bounty <place|list|check>` | *(Everyone)* | Place or view Diamond bounties on wanted outlaws |
| `/watchdog report <player>` | `dtempire.watchdog.report` | Report suspicious players to Watchdog |
| `/watchdog <test|inspect|ban|unban|stats>` | `dtempire.watchdog.staff` | Anti-cheat inspection, aura bot tests & bans *(Staff)* |
| `/aiadmin <event|gift|broadcast|stats>` | `dtempire.admin` | Game Master event director & gift manager *(Staff)* |
| `/dtempireai <update|reload|status>` | `dtempire.tracking.admin` | Check/download plugin updates, reload config *(Admin)* |
| `/dtstatus` | `dtempire.tracking.status` | Post Discord status embed immediately *(Admin)* |
| `/dttracking <on|off>` | `dtempire.tracking.toggle` | Toggle automatic Discord tracking *(Admin)* |

---

## 🚀 Installation & Quick Start

1. Download `DTEmpireAIChat.jar` from the [Latest Release](https://github.com/hyperdargo/DTEmpireAiPlugin/releases/latest).
2. Place the jar into your server's `plugins/` directory.
3. Start the server to generate `plugins/DTEmpireAIChat/config.yml`.
4. Configure your AI API key and settings:

```yaml
# API endpoint (OmniRoute or OpenAI compatible)
api:
  base-url: "https://route.ankitgupta.com.np/v1"
  model: "DiscordBot"
  api-key: "YOUR_API_KEY_HERE"

# Watchdog Discord Appeal URL
watchdog:
  enabled: true
  discord-appeal-url: "http://dsc.gg/dtempire-server"

# Server info injected into AI context
server-info:
  name: "DTEmpire"
  ip: "play.dtempire.com"
  gamemode: "Survival SMP"

# Discord status tracking embed (optional)
tracking:
  enabled: true
  discord:
    webhook-url: "https://discord.com/api/webhooks/..."
```

5. Run `/dtempireai reload` in-game or restart your server.

---

## 🛠️ Building from Source

Requirements: Java 17+ and Maven.

```bash
git clone https://github.com/hyperdargo/DTEmpireAiPlugin.git
cd DTEmpireAiPlugin
mvn clean package -DskipTests
```

The compiled shaded jar will be located at:
`target/DTEmpireAIChat.jar` (includes shaded `sqlite-jdbc` driver).
