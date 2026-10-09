# 🛡️ DTEmpire Discord Ban Appeal Bot

This standalone Discord bot connects directly with the **DTEmpireAIChat Watchdog Anti-Cheat** plugin running on your Paper Minecraft server.

### Features
1. **Interactive Appeal Button (`[📩 Submit Ban Appeal]`):** Players click a single button to open a native Discord Modal popup.
2. **Custom Modal Questions:**
   - Minecraft In-Game Name (IGN)
   - Ban ID (found on kick screen, e.g. `#WD-84729103`)
   - What were you doing when banned?
   - Why should you be unbanned?
3. **Staff Review Channel:**
   - Sends the player's head avatar, Discord mention, and appeal answers into your private `#staff-appeals` channel.
   - Interactive **`[🟢 Approve & Unban]`** and **`[🔴 Reject Appeal]`** buttons.
4. **Instant In-Game Unban:**
   - Clicking `[Approve & Unban]` instantly executes an unban on the Paper server via HTTP API (`POST /api/unban`).
   - Watchdog broadcasts the pardon in Minecraft chat: `[Watchdog] Player1 was unbanned via approved Discord appeal!`.
   - Sends a direct DM to the player letting them know their appeal was approved and giving them the server IP (`play.dtempire.com`)!

---

### Setup Instructions

1. Copy `.env.example` to `.env`:
   ```bash
   cp .env.example .env
   ```

2. Configure your values in `.env`:
   ```ini
   DISCORD_BOT_TOKEN="your_bot_token_here"
   APPEALS_CHANNEL_ID="123456789012345678"      # Channel where players click [Submit Ban Appeal]
   STAFF_CHANNEL_ID="123456789012345678"        # Private channel where staff review appeals
   WATCHDOG_API_URL="http://127.0.0.1:25609"
   WATCHDOG_API_KEY="dtempire-watchdog-secret-key"
   ```

3. Launch the bot:
   ```bash
   python3 discord_appeal_bot.py
   ```

4. In Discord:
   - Go to your public `#ban-appeals` channel and type:
     ```text
     !setup_appeals
     ```
   - The bot will post the permanent embed with the **`[📩 Submit Ban Appeal]`** button!
