# DTEmpire AI Chat Bot — Minecraft Plugin

**Private AI chat for your Minecraft server.** Talk to an AI directly from the game — start a session, chat privately, and only you and the AI see the conversation.

![Minecraft](https://img.shields.io/badge/Minecraft-1.21.4-blue)
![Paper](https://img.shields.io/badge/Paper-1.21.4%2B-white)
![Java](https://img.shields.io/badge/Java-17%2B-orange)
![License](https://img.shields.io/badge/License-MIT-green)
![Version](https://img.shields.io/badge/Version-1.0.0-purple)

---

## ✨ Features

- 🧠 **AI chat in-game** — `/aichat` puts you into a private AI chat session
- 💬 **Normal chat becomes private** — while in AI mode, everything you type goes to the AI, and the AI replies. **Nobody else on the server can see it**
- 🔒 **100% private** — messages and AI responses are only sent to you via `player.sendMessage()`
- 🧵 **Conversation memory** — the AI remembers the last 20 messages of your session
- ⏱️ **Auto-timeout** — sessions idle out after 5 minutes (configurable)
- 🎨 **Polished chat formatting** — color-coded `[AI]` and `You:` prefixes
- 🌐 **Any OpenAI-compatible endpoint** — works with OmniRoute, LocalAI, LiteLLM, or any `/v1/chat/completions` API

---

## 📥 Installation

1. **Download** the latest `DTEmpireAIChat.jar` from [Releases](https://github.com/hyperdargo/DTEmpireAiPlugin/releases)
2. Drop the jar into your server's `plugins/` folder
3. **Restart** the server (or `/reload`)
4. A `config.yml` is auto-generated at `plugins/DTEmpireAIChat/config.yml` — **open it and set your API key**
5. Restart again and you're ready

---

## ⚙️ Configuration

```yaml
# plugins/DTEmpireAIChat/config.yml
api:
  # Your AI endpoint (base URL without /chat/completions)
  base-url: "http://127.0.0.1:25607"
  # Model name — must match the model id on your router
  model: "DiscordBot"
  # Your API key (Bearer token). NEVER share this.
  api-key: ""
  timeout-ms: 30000
  system-prompt: "You are a helpful AI assistant in a Minecraft server. Keep responses concise."

session:
  # Seconds of inactivity before the session auto-ends
  inactivity-timeout: 300
  # Max conversation history kept per session
  max-history: 20
```

> ⚠️ **Never commit your `api-key` to git.** If your key leaks, regenerate it on the router side.

---

## 🎮 Commands

| Command | Description |
|---------|-------------|
| `/aichat` | Enter AI chat mode. Everything you type becomes a private AI conversation |
| `/aichat <message>` | Enter AI mode and send the first message right away |
| `/aiexit` | Leave AI chat mode |
| `/aic` | Alias for `/aichat` |
| `/aidone` | Alias for `/aiexit` |

### How it works

1. Type `/aichat` → you enter private AI mode
   ```
   [AI] You are now in AI chat mode. Just type normally to talk to the AI. Use /aiexit to leave.
   ```
2. Type any normal chat message → only you and the AI see it
   ```
   [You] what is the best way to make a netherite farm?
   [AI] The fastest method is a gold farm in the Nether — zombie piglin...
   ```
3. Type `/aiexit` → back to normal chat
   ```
   [AI] Ended your AI chat session.
   ```

While in AI mode, your chat is **completely hidden from other players** — no global chat broadcasts, no DiscordSRV relays, nothing. Command usage (`/`-commands) still works normally.

---

## 🔧 Building from source

Requires: **JDK 17+** and **Maven 3.9+**

```bash
git clone https://github.com/hyperdargo/DTEmpireAiPlugin.git
cd DTEmpireAiPlugin
mvn clean package
# Output: target/DTEmpireAIChat.jar
```

Built against [Paper 1.21.4 API](https://repo.papermc.io) — compatible with Paper, Purpur, Folia, and other Paper-fork servers running **1.21.x**.

---

## 📄 License

MIT — use it, modify it, share it.

Crafted by **DargoTamber** ⚡ for the [DTEmpire](https://route.ankitgupta.com.np) community.