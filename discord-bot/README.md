# 🛡️ DTEmpire Discord Ban Appeal Bot

Interactive Ban Appeal Bot for **DTEmpire Watchdog Anti-Cheat** on Paper 1.21.4+.

### Features
1. **Interactive Appeal Button (`[📩 Submit Ban Appeal]`):** Players click a single button to open a native Discord Modal popup.
2. **Custom Modal Questions:**
   - Minecraft In-Game Name (IGN)
   - Ban ID (found on kick screen or webhook card, e.g. `#WD-84729103`)
   - What were you doing when banned?
   - Why should you be unbanned?
3. **Staff Review Channel:**
   - Automatically posts the player's 3D skin head avatar, Discord mention, and appeal answers into your private staff review channel.
   - Interactive **`[🟢 Accept Appeal]`** and **`[🔴 Deny Appeal]`** buttons.
4. **Accept & Deny Workflow:**
   - **Accept:** Marks the card green as Accepted, automatically sends a Discord DM to the player notifying them they have been unbanned, and provides staff with the exact console command to run (`watchdog unban <IGN>` or `pardon <IGN>`).
   - **Deny:** Opens a modal for staff to input the denial reason, marks the card red as Denied, and automatically sends a Discord DM to the player with the staff's reason.
5. **No Localhost API Required:**
   - Functions 100% standalone without opening ports or configuring HTTP servers on your Minecraft host.

---

### Setup Instructions

1. Configure your bot token in `.env`:
   ```ini
   DISCORD_BOT_TOKEN="your_discord_bot_token"
   ```

2. Run the bot:
   ```bash
   python3 discord_appeal_bot.py
   ```

3. In Discord:
   - In your private staff channel, run:
     ```text
     !setappeallog #staff-review
     ```
   - In your public appeals channel (e.g. `#ban-appeals`), run:
     ```text
     !postappealpanel
     ```
   - The bot will post the permanent embed with the **`[📩 Submit Ban Appeal]`** button.
