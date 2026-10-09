#!/usr/bin/env python3
"""
DTEmpire Watchdog Discord Appeal Bot & Ban Broadcaster
Features:
  - Unified Single Ban Card: Ban record is posted directly by the bot with the [Submit Ban Appeal] button attached!
  - No two-way text duplication: Webhook posts are intercepted/replaced or sent via local bridge.
  - Local HTTP Ban Bridge (http://127.0.0.1:25608/ban) connecting Minecraft Watchdog to Discord instantly.
  - Slash Commands & Prefix Commands:
      /setbanchannel #channel (or !setbanchannel) - Sets the public ban logs channel.
      /setappeallog #channel  (or !setappeallog)  - Sets the private staff appeals channel.
      /banapeal #channel      (or !banapeal)      - Alias to configure the staff review channel.
      /postappealpanel        (or !postappealpanel)- Posts the permanent appeal station embed.
      /appealstatus           (or !appealstatus)  - Shows current channels and permission health.
  - Interactive Ban Appeal Modal pre-filled with player's IGN and Ban ID.
  - Staff Review Flow:
      [🟢 Accept Appeal]: Marks approved, sends DM to player, reminds staff of console command: watchdog unban <IGN>
      [🔴 Deny Appeal]: Opens modal for denial reason, marks denied, sends DM to player with reason.
"""

import os
import sys
import json
import logging
from pathlib import Path
import re
import asyncio
from typing import Optional

import discord
from discord import app_commands
from discord.ext import commands
from aiohttp import web

logging.basicConfig(level=logging.INFO, format="[%(asctime)s] [%(name)s/%(levelname)s]: %(message)s")
logger = logging.getLogger("WatchdogAppealBot")

CONFIG_FILE = Path(__file__).parent / "appeal_config.json"
PENDING_UNBANS_FILE = Path(__file__).parent / "pending_unbans.json"
LOCAL_API_PORT = 25608


# ─────────────────────────────────────────────────────────────────────────────
# CONFIG & AUTO-UNBAN PERSISTENCE
# ─────────────────────────────────────────────────────────────────────────────
def load_config() -> dict:
    try:
        if CONFIG_FILE.exists():
            return json.loads(CONFIG_FILE.read_text(encoding="utf-8"))
    except Exception as e:
        logger.error(f"Error reading {CONFIG_FILE}: {e}")
    return {}


def save_config(data: dict):
    try:
        CONFIG_FILE.parent.mkdir(parents=True, exist_ok=True)
        tmp = CONFIG_FILE.with_suffix(".tmp")
        tmp.write_text(json.dumps(data, indent=2), encoding="utf-8")
        tmp.replace(CONFIG_FILE)
    except Exception as e:
        logger.error(f"Error saving {CONFIG_FILE}: {e}")


def load_pending_unbans() -> list[str]:
    try:
        if PENDING_UNBANS_FILE.exists():
            return json.loads(PENDING_UNBANS_FILE.read_text(encoding="utf-8"))
    except Exception as e:
        logger.error(f"Error reading {PENDING_UNBANS_FILE}: {e}")
    return []


def save_pending_unbans(unbans: list[str]):
    try:
        PENDING_UNBANS_FILE.parent.mkdir(parents=True, exist_ok=True)
        tmp = PENDING_UNBANS_FILE.with_suffix(".tmp")
        tmp.write_text(json.dumps(unbans, indent=2), encoding="utf-8")
        tmp.replace(PENDING_UNBANS_FILE)
    except Exception as e:
        logger.error(f"Error saving {PENDING_UNBANS_FILE}: {e}")


def try_direct_sqlite_unban(player_name: str):
    """Directly removes player from any accessible tracking.db SQLite database."""
    import sqlite3
    db_candidates = [
        Path("/var/lib/dtempire/data/servers/4046c6fc975bf26b0e302b685c404b8b/plugins/DTEmpireAIChat/tracking.db"),
        Path("/home/dargo/DTEmpireAIChat/tracking.db"),
    ]
    try:
        for p in Path("/var/lib/dtempire/data/servers").glob("*/plugins/DTEmpireAIChat/tracking.db"):
            if p not in db_candidates:
                db_candidates.append(p)
    except Exception:
        pass

    for db_path in db_candidates:
        if db_path.exists():
            try:
                conn = sqlite3.connect(str(db_path))
                c = conn.cursor()
                c.execute("DELETE FROM watchdog_bans WHERE LOWER(player_name) = LOWER(?)", (player_name,))
                c.execute("DELETE FROM watchdog_violations WHERE LOWER(player_name) = LOWER(?)", (player_name,))
                conn.commit()
                conn.close()
                logger.info(f"[AutoUnban] Direct SQLite unban executed for {player_name} in {db_path}")
            except Exception as e:
                logger.warning(f"[AutoUnban] Direct SQLite unban notice for {db_path}: {e}")


def add_pending_unban(player_name: str):
    clean = player_name.strip()
    if not clean:
        return
    current = load_pending_unbans()
    if clean.lower() not in [p.lower() for p in current]:
        current.append(clean)
        save_pending_unbans(current)
        logger.info(f"[AutoUnban] Queued auto-unban for {clean}")
    try_direct_sqlite_unban(clean)


def remove_pending_unban(player_name: str):
    current = load_pending_unbans()
    updated = [p for p in current if p.lower() != player_name.strip().lower()]
    save_pending_unbans(updated)
    logger.info(f"[AutoUnban] Completed and acknowledged unban for {player_name}")


def extract_field_value(embed: discord.Embed, field_name: str) -> str:
    for f in embed.fields:
        if f.name and field_name.lower() in f.name.lower():
            return f.value or ""
    return ""


def extract_discord_user_id(embed: discord.Embed) -> int:
    val = extract_field_value(embed, "Discord User")
    match = re.search(r"(\d{17,20})", val)
    if match:
        return int(match.group(1))
    return 0


def check_channel_perms(bot_member: discord.Member, channel: discord.TextChannel) -> list[str]:
    perms = channel.permissions_for(bot_member)
    missing = []
    if not perms.view_channel:
        missing.append("View Channel")
    if not perms.send_messages:
        missing.append("Send Messages")
    if not perms.embed_links:
        missing.append("Embed Links")
    return missing


def create_ban_embed(ign: str, ban_id: str, reason: str, status: str = "Permanent Ban", appeal_url: str = "http://dsc.gg/dtempire-server") -> discord.Embed:
    embed = discord.Embed(
        title="🛡️ WATCHDOG BAN ENFORCED",
        description="A player has been permanently banned by Watchdog Anti-Cheat.",
        color=0xE74C3C,  # Red
        timestamp=discord.utils.utcnow()
    )
    embed.set_thumbnail(url=f"https://mc-heads.net/avatar/{ign}/128")
    embed.add_field(name="👤 Player", value=f"`{ign}`", inline=True)
    embed.add_field(name="🆔 Ban ID", value=f"`{ban_id}`", inline=True)
    embed.add_field(name="⚖️ Reason", value=f"**{reason}**", inline=True)
    embed.add_field(name="📋 Status", value=f"🔴 **{status}**", inline=True)
    embed.add_field(
        name="📩 How to Appeal",
        value=f"Click the **Submit Ban Appeal** button below, or join [{appeal_url}]({appeal_url}) with your Ban ID.",
        inline=False
    )
    embed.set_footer(text="DTEmpire Watchdog Security Network • Player Ban Record")
    return embed


# ─────────────────────────────────────────────────────────────────────────────
# MODAL: Staff Denial Reason Modal
# ─────────────────────────────────────────────────────────────────────────────
class StaffDenyReasonModal(discord.ui.Modal, title="Deny Ban Appeal"):
    reason_input = discord.ui.TextInput(
        label="Reason for Denial",
        placeholder="Explain why this appeal is denied (e.g. Cheating confirmed, insufficient evidence)...",
        style=discord.TextStyle.paragraph,
        min_length=5,
        max_length=500,
        required=True
    )

    def __init__(self, target_user_id: int, ign: str):
        super().__init__()
        self.target_user_id = target_user_id
        self.ign = ign

    async def on_submit(self, interaction: discord.Interaction):
        await interaction.response.defer()

        # Update message embed
        if interaction.message:
            embed = interaction.message.embeds[0]
            embed.color = 0xE74C3C  # Red

            new_fields = []
            for f in embed.fields:
                if f.name and "status" in f.name.lower():
                    new_fields.append((f.name, f"❌ **DENIED** by {interaction.user.mention}", False))
                else:
                    new_fields.append((f.name, f.value, f.inline))

            embed.clear_fields()
            for name, val, inline in new_fields:
                embed.add_field(name=name, value=val, inline=inline)

            embed.add_field(name="📋 Staff Denial Reason", value=self.reason_input.value, inline=False)

            # Disable buttons
            view = discord.ui.View()
            btn_accept = discord.ui.Button(label="Accept Appeal", style=discord.ButtonStyle.success, emoji="🟢", disabled=True)
            btn_deny = discord.ui.Button(label="Denied", style=discord.ButtonStyle.danger, emoji="🔴", disabled=True)
            view.add_item(btn_accept)
            view.add_item(btn_deny)

            await interaction.message.edit(embed=embed, view=view)

        # DM notification to player
        dm_sent = False
        target_user = None
        if self.target_user_id:
            try:
                target_user = await interaction.client.fetch_user(self.target_user_id)
                if target_user:
                    dm_embed = discord.Embed(
                        title="⚖️ DTEmpire Ban Appeal Decision: DENIED",
                        description=(
                            f"Hello {target_user.mention},\n\n"
                            f"Your ban appeal for Minecraft account **`{self.ign}`** has been reviewed by staff and was **DENIED**.\n\n"
                            f"**Reason:** {self.reason_input.value}\n\n"
                            f"If you have further questions, you may contact server administration."
                        ),
                        color=0xE74C3C
                    )
                    dm_embed.set_footer(text="DTEmpire Server Management • Watchdog Anti-Cheat")
                    await target_user.send(embed=dm_embed)
                    dm_sent = True
                    logger.info(f"Delivered denial DM to {self.target_user_id} for IGN {self.ign}")
            except Exception as e:
                logger.warning(f"Could not send DM to {self.target_user_id}: {e}")

        # Fallback channel ping if user has DMs disabled
        if not dm_sent and self.target_user_id and interaction.guild:
            try:
                cfg = load_config()
                ban_ch_id = cfg.get(str(interaction.guild.id), {}).get("ban_channel_id")
                ban_ch = interaction.guild.get_channel(ban_ch_id) if ban_ch_id else None
                dest = ban_ch if isinstance(ban_ch, discord.TextChannel) else interaction.channel
                if isinstance(dest, discord.TextChannel):
                    await dest.send(
                        f"📢 <@{self.target_user_id}> **Ban Appeal Update:** Your appeal for Minecraft account **`{self.ign}`** was **DENIED**.\n"
                        f"**Reason:** {self.reason_input.value}"
                    )
            except Exception as e:
                logger.warning(f"Failed to send fallback denial channel ping: {e}")

        notif_status = "Player notified via DM." if dm_sent else "Player notified via Channel Ping (DMs were closed)."
        await interaction.followup.send(
            f"❌ Appeal for **`{self.ign}`** has been **DENIED**.\n"
            f"• Reason logged: *{self.reason_input.value}*\n"
            f"• {notif_status}",
            ephemeral=True
        )


# ─────────────────────────────────────────────────────────────────────────────
# VIEW: Staff Review Action Buttons (Persistent)
# ─────────────────────────────────────────────────────────────────────────────
class StaffAppealReviewView(discord.ui.View):
    def __init__(self):
        super().__init__(timeout=None)

    @discord.ui.button(label="Accept Appeal", style=discord.ButtonStyle.success, emoji="🟢", custom_id="dtempire_staff_btn_accept")
    async def accept_appeal(self, interaction: discord.Interaction, button: discord.ui.Button):
        await interaction.response.defer()

        if not interaction.message or not interaction.message.embeds:
            await interaction.followup.send("❌ Error reading appeal embed.", ephemeral=True)
            return

        embed = interaction.message.embeds[0]
        ign = extract_field_value(embed, "Minecraft IGN")
        ign = re.sub(r"[`*_\s]", "", ign) or "Player"
        target_user_id = extract_discord_user_id(embed)

        # Trigger Automated In-Game Unban
        add_pending_unban(ign)

        # Update Embed
        embed.color = 0x2ECC71  # Green
        new_fields = []
        for f in embed.fields:
            if f.name and "status" in f.name.lower():
                new_fields.append((f.name, f"✅ **APPROVED & AUTO-UNBANNED** by {interaction.user.mention}", False))
            else:
                new_fields.append((f.name, f.value, f.inline))

        embed.clear_fields()
        for name, val, inline in new_fields:
            embed.add_field(name=name, value=val, inline=inline)

        # Disable buttons
        view = discord.ui.View()
        btn_accept = discord.ui.Button(label="Approved & Unbanned", style=discord.ButtonStyle.success, emoji="✅", disabled=True)
        btn_deny = discord.ui.Button(label="Deny Appeal", style=discord.ButtonStyle.danger, emoji="🔴", disabled=True)
        view.add_item(btn_accept)
        view.add_item(btn_deny)

        await interaction.message.edit(embed=embed, view=view)

        # DM notification to player
        dm_sent = False
        if target_user_id:
            try:
                target_user = await interaction.client.fetch_user(target_user_id)
                if target_user:
                    dm_embed = discord.Embed(
                        title="🎉 DTEmpire Ban Appeal Decision: APPROVED",
                        description=(
                            f"Congratulations {target_user.mention}!\n\n"
                            f"Your ban appeal for Minecraft account **`{ign}`** has been **APPROVED** by DTEmpire staff.\n\n"
                            f"✅ **You have been automatically unbanned!** You may now connect back to the server.\n"
                            f"Please make sure to follow all server rules going forward.\n\n"
                            f"Welcome back to DTEmpire!"
                        ),
                        color=0x2ECC71
                    )
                    dm_embed.set_footer(text="DTEmpire Server Management • Watchdog Anti-Cheat")
                    await target_user.send(embed=dm_embed)
                    dm_sent = True
                    logger.info(f"Delivered approval DM to {target_user_id} for IGN {ign}")
            except Exception as e:
                logger.warning(f"Could not send approval DM to {target_user_id}: {e}")

        # Fallback channel ping if user has DMs disabled
        if not dm_sent and target_user_id and interaction.guild:
            try:
                cfg = load_config()
                ban_ch_id = cfg.get(str(interaction.guild.id), {}).get("ban_channel_id")
                ban_ch = interaction.guild.get_channel(ban_ch_id) if ban_ch_id else None
                dest = ban_ch if isinstance(ban_ch, discord.TextChannel) else interaction.channel
                if isinstance(dest, discord.TextChannel):
                    await dest.send(
                        f"📢 <@{target_user_id}> **Ban Appeal Update:** Your appeal for Minecraft account **`{ign}`** has been **APPROVED**! You are now unbanned in-game."
                    )
            except Exception as e:
                logger.warning(f"Failed to send fallback approval channel ping: {e}")

        notif_status = "Player notified via DM." if dm_sent else "Player notified via Channel Ping (DMs were closed)."
        await interaction.followup.send(
            f"✅ Appeal for **`{ign}`** has been **APPROVED**!\n"
            f"• **Auto-Unban:** Player has been automatically queued & unbanned in Minecraft (no console required!).\n"
            f"• {notif_status}",
            ephemeral=True
        )

    @discord.ui.button(label="Deny Appeal", style=discord.ButtonStyle.danger, emoji="🔴", custom_id="dtempire_staff_btn_deny")
    async def deny_appeal(self, interaction: discord.Interaction, button: discord.ui.Button):
        if not interaction.message or not interaction.message.embeds:
            await interaction.response.send_message("❌ Error reading appeal embed.", ephemeral=True)
            return

        embed = interaction.message.embeds[0]
        ign = extract_field_value(embed, "Minecraft IGN")
        ign = re.sub(r"[`*_\s]", "", ign) or "Player"
        target_user_id = extract_discord_user_id(embed)

        modal = StaffDenyReasonModal(target_user_id=target_user_id, ign=ign)
        await interaction.response.send_modal(modal)


# ─────────────────────────────────────────────────────────────────────────────
# MODAL: Player Ban Appeal Form
# ─────────────────────────────────────────────────────────────────────────────
class PlayerBanAppealModal(discord.ui.Modal, title="DTEmpire Ban Appeal"):
    def __init__(self, default_ign: str = "", default_ban_id: str = ""):
        super().__init__()
        self.ign = discord.ui.TextInput(
            label="Minecraft In-Game Name (IGN)",
            placeholder="e.g. Steve",
            default=default_ign,
            min_length=3,
            max_length=16,
            required=True
        )
        self.ban_id = discord.ui.TextInput(
            label="Ban ID (found on ban screen)",
            placeholder="e.g. #WD-99245006",
            default=default_ban_id,
            min_length=3,
            max_length=32,
            required=False
        )
        self.activity = discord.ui.TextInput(
            label="What were you doing when banned?",
            placeholder="Describe your actions (mining diamonds, fighting, lagging, etc.)...",
            style=discord.TextStyle.paragraph,
            min_length=10,
            max_length=600,
            required=True
        )
        self.appeal_reason = discord.ui.TextInput(
            label="Why should this ban be lifted?",
            placeholder="Explain why this was a false detection or mistake...",
            style=discord.TextStyle.paragraph,
            min_length=10,
            max_length=600,
            required=True
        )

        self.add_item(self.ign)
        self.add_item(self.ban_id)
        self.add_item(self.activity)
        self.add_item(self.appeal_reason)

    async def on_submit(self, interaction: discord.Interaction):
        guild = interaction.guild
        if not guild:
            await interaction.response.send_message("❌ Appeals must be submitted inside the server.", ephemeral=True)
            return

        cfg = load_config()
        guild_cfg = cfg.get(str(guild.id), {})
        log_channel_id = guild_cfg.get("log_channel_id")

        log_channel = guild.get_channel(log_channel_id) if log_channel_id else None
        if not log_channel:
            await interaction.response.send_message(
                "⚠️ Staff appeals review channel is not configured yet. Please ask an administrator to run `/setappeallog #channel`.",
                ephemeral=True
            )
            return

        if not isinstance(log_channel, discord.TextChannel):
            await interaction.response.send_message(
                "❌ Configured staff appeals channel is not a standard text channel.",
                ephemeral=True
            )
            return

        bot_member = guild.me
        perms = log_channel.permissions_for(bot_member)
        if not perms.view_channel or not perms.send_messages or not perms.embed_links:
            await interaction.response.send_message(
                f"❌ **Delivery Error:** The bot cannot post in the staff review channel ({log_channel.mention}).\n"
                f"👉 The channel is restricted and the bot is missing **View Channel**, **Send Messages**, or **Embed Links** permissions.\n"
                f"Please ask an administrator to grant the bot access in that channel's permission settings.",
                ephemeral=True
            )
            return

        # Prepare Embed
        embed = discord.Embed(
            title=f"⚖️ Ban Appeal Submission: {self.ign.value}",
            color=0xF1C40F,  # Amber yellow
            timestamp=interaction.created_at
        )
        embed.set_thumbnail(url=f"https://mc-heads.net/avatar/{self.ign.value}/128")
        embed.add_field(name="👤 Minecraft IGN", value=f"`{self.ign.value}`", inline=True)
        embed.add_field(name="💬 Discord User", value=f"{interaction.user.mention} (`{interaction.user.id}`)", inline=True)
        embed.add_field(name="🆔 Ban ID", value=f"`{self.ban_id.value or 'None'}`", inline=True)
        embed.add_field(name="📍 What were they doing?", value=self.activity.value, inline=False)
        embed.add_field(name="📝 Why should they be unbanned?", value=self.appeal_reason.value, inline=False)
        embed.add_field(name="📊 Status", value="⏳ **PENDING STAFF REVIEW**", inline=False)
        embed.set_footer(text="DTEmpire Watchdog Anti-Cheat • Ban Appeals")

        view = StaffAppealReviewView()
        try:
            await log_channel.send(embed=embed, view=view)
        except discord.Forbidden:
            await interaction.response.send_message(
                f"❌ **Delivery Failed (403 Forbidden):** The bot was blocked from posting in {log_channel.mention}.\n"
                f"Please ensure the bot role has **View Channel**, **Send Messages**, and **Embed Links** permissions in that channel.",
                ephemeral=True
            )
            return
        except Exception as e:
            await interaction.response.send_message(
                f"❌ An error occurred while submitting your appeal: `{e}`",
                ephemeral=True
            )
            return

        # Success confirmation to user
        await interaction.response.send_message(
            f"✅ **Thank you, {interaction.user.mention}!**\n"
            f"Your appeal for Minecraft account **`{self.ign.value}`** has been submitted to DTEmpire staff.\n"
            f"You will receive a notification via Direct Message when a decision is made.",
            ephemeral=True
        )


# ─────────────────────────────────────────────────────────────────────────────
# VIEW: Public Launch Button on Standalone Panel
# ─────────────────────────────────────────────────────────────────────────────
class PublicAppealLaunchView(discord.ui.View):
    def __init__(self):
        super().__init__(timeout=None)

    @discord.ui.button(label="Submit Ban Appeal", style=discord.ButtonStyle.primary, emoji="📩", custom_id="dtempire_btn_launch_appeal")
    async def launch_appeal(self, interaction: discord.Interaction, button: discord.ui.Button):
        await interaction.response.send_modal(PlayerBanAppealModal())


# ─────────────────────────────────────────────────────────────────────────────
# VIEW: Appeal Button Attached Directly to a Ban Card
# ─────────────────────────────────────────────────────────────────────────────
class SpecificBanAppealView(discord.ui.View):
    def __init__(self, ign: str = "", ban_id: str = ""):
        super().__init__(timeout=None)
        self.ign = ign
        self.ban_id = ban_id

    @discord.ui.button(label="Submit Ban Appeal", style=discord.ButtonStyle.danger, emoji="📩", custom_id="dtempire_btn_specific_ban_appeal")
    async def appeal_specific(self, interaction: discord.Interaction, button: discord.ui.Button):
        ign = self.ign
        ban_id = self.ban_id
        if (not ign or not ban_id) and interaction.message and interaction.message.embeds:
            for emb in interaction.message.embeds:
                if not ign:
                    raw_player = extract_field_value(emb, "Player")
                    ign = re.sub(r"[`*_\s]", "", raw_player)
                if not ban_id:
                    raw_ban_id = extract_field_value(emb, "Ban ID")
                    ban_id = re.sub(r"[`*_\s]", "", raw_ban_id)
        await interaction.response.send_modal(PlayerBanAppealModal(default_ign=ign, default_ban_id=ban_id))


# ─────────────────────────────────────────────────────────────────────────────
# SHARED COMMAND HANDLERS (Used by Slash and Prefix Commands)
# ─────────────────────────────────────────────────────────────────────────────
async def handle_set_ban_channel(ctx_or_interaction, channel: discord.TextChannel, is_slash: bool):
    guild = ctx_or_interaction.guild
    if not guild:
        msg = "❌ This command must be executed within a server."
        if is_slash:
            await ctx_or_interaction.response.send_message(msg, ephemeral=True)
        else:
            await ctx_or_interaction.send(msg)
        return

    bot_member = guild.me
    missing = check_channel_perms(bot_member, channel)
    if missing:
        msg = (
            f"❌ **Cannot set {channel.mention} as ban announcement channel!**\n"
            f"The bot is missing required permissions: **{', '.join(missing)}**.\n\n"
            f"🔧 **How to fix:**\n"
            f"1. In Discord, right-click `{channel.name}` → **Edit Channel** → **Permissions**\n"
            f"2. Add role `{bot_member.name}`\n"
            f"3. Turn ON ✅ **View Channel**, ✅ **Send Messages**, and ✅ **Embed Links**\n"
            f"4. Run `/setbanchannel {channel.mention}` again."
        )
        if is_slash:
            await ctx_or_interaction.response.send_message(msg, ephemeral=True)
        else:
            await ctx_or_interaction.send(msg)
        return

    cfg = load_config()
    gid = str(guild.id)
    if gid not in cfg:
        cfg[gid] = {}
    cfg[gid]["ban_channel_id"] = channel.id
    save_config(cfg)

    msg = (
        f"✅ **Public Ban Announcement Channel Set!**\n"
        f"👉 Channel: {channel.mention}\n"
        f"Whenever Watchdog bans a player, a single unified ban card with an interactive **[Submit Ban Appeal]** button will be posted directly here."
    )
    if is_slash:
        await ctx_or_interaction.response.send_message(msg, ephemeral=False)
    else:
        await ctx_or_interaction.send(msg)


async def handle_set_appeal_log(ctx_or_interaction, channel: discord.TextChannel, is_slash: bool):
    guild = ctx_or_interaction.guild
    if not guild:
        msg = "❌ This command must be executed within a server."
        if is_slash:
            await ctx_or_interaction.response.send_message(msg, ephemeral=True)
        else:
            await ctx_or_interaction.send(msg)
        return

    bot_member = guild.me
    missing = check_channel_perms(bot_member, channel)
    if missing:
        msg = (
            f"❌ **Cannot set {channel.mention} as staff appeals review channel!**\n"
            f"The bot is missing required permissions in that channel: **{', '.join(missing)}**.\n\n"
            f"🔧 **How to fix:**\n"
            f"1. In Discord, right-click `{channel.name}` → **Edit Channel** → **Permissions**\n"
            f"2. Add role `{bot_member.name}`\n"
            f"3. Turn ON ✅ **View Channel**, ✅ **Send Messages**, and ✅ **Embed Links**\n"
            f"4. Run `/setappeallog {channel.mention}` again."
        )
        if is_slash:
            await ctx_or_interaction.response.send_message(msg, ephemeral=True)
        else:
            await ctx_or_interaction.send(msg)
        return

    cfg = load_config()
    gid = str(guild.id)
    if gid not in cfg:
        cfg[gid] = {}
    cfg[gid]["log_channel_id"] = channel.id
    save_config(cfg)

    msg = (
        f"✅ **Staff Ban Appeals Review Channel Set!**\n"
        f"👉 Channel: {channel.mention}\n"
        f"When players submit appeals, staff cards with **[🟢 Accept Appeal]** and **[🔴 Deny Appeal]** buttons will appear here."
    )
    if is_slash:
        await ctx_or_interaction.response.send_message(msg, ephemeral=False)
    else:
        await ctx_or_interaction.send(msg)


async def handle_post_appeal_panel(ctx_or_interaction, channel: discord.TextChannel, is_slash: bool):
    embed = discord.Embed(
        title="🛡️ DTEmpire Network • Ban Appeals",
        description=(
            "If you were banned by **Watchdog Anti-Cheat** or staff and believe it was a mistake or false detection, "
            "you can submit an official ban appeal here.\n\n"
            "**📌 Instructions:**\n"
            "• Click the **Submit Ban Appeal** button below.\n"
            "• Provide your Minecraft IGN, Ban ID (if available), and an honest explanation.\n"
            "• Staff will review your submission and you will be notified via Discord DM."
        ),
        color=0x3498DB
    )
    embed.set_thumbnail(url="https://mc-heads.net/avatar/Watchdog/128")
    embed.set_footer(text="DTEmpire Security Network • Appeals Department")

    view = PublicAppealLaunchView()
    try:
        await channel.send(embed=embed, view=view)
        confirm = f"✅ Appeal panel successfully posted in {channel.mention}!"
        if is_slash:
            await ctx_or_interaction.response.send_message(confirm, ephemeral=True)
        else:
            await ctx_or_interaction.send(confirm)
    except Exception as e:
        err = f"❌ Failed to post appeal panel: {e}"
        if is_slash:
            await ctx_or_interaction.response.send_message(err, ephemeral=True)
        else:
            await ctx_or_interaction.send(err)


async def handle_appeal_status(ctx_or_interaction, is_slash: bool):
    guild = ctx_or_interaction.guild
    if not guild:
        msg = "❌ This command must be executed within a server."
        if is_slash:
            await ctx_or_interaction.response.send_message(msg, ephemeral=True)
        else:
            await ctx_or_interaction.send(msg)
        return

    cfg = load_config()
    guild_cfg = cfg.get(str(guild.id), {})
    ban_ch_id = guild_cfg.get("ban_channel_id")
    log_ch_id = guild_cfg.get("log_channel_id")

    ban_ch = guild.get_channel(ban_ch_id) if ban_ch_id else None
    log_ch = guild.get_channel(log_ch_id) if log_ch_id else None

    embed = discord.Embed(
        title="🛡️ Watchdog Appeal System Status",
        color=0x2ECC71,
        timestamp=discord.utils.utcnow()
    )

    if ban_ch and isinstance(ban_ch, discord.TextChannel):
        missing = check_channel_perms(guild.me, ban_ch)
        status_text = "✅ Ready" if not missing else f"⚠️ Missing: {', '.join(missing)}"
        embed.add_field(name="📢 Public Ban Channel", value=f"{ban_ch.mention} ({status_text})", inline=False)
    else:
        embed.add_field(name="📢 Public Ban Channel", value="❌ Not set (Run `/setbanchannel #channel`)", inline=False)

    if log_ch and isinstance(log_ch, discord.TextChannel):
        missing = check_channel_perms(guild.me, log_ch)
        status_text = "✅ Ready" if not missing else f"⚠️ Missing: {', '.join(missing)}"
        embed.add_field(name="⚖️ Staff Review Channel", value=f"{log_ch.mention} ({status_text})", inline=False)
    else:
        embed.add_field(name="⚖️ Staff Review Channel", value="❌ Not set (Run `/setappeallog #channel`)", inline=False)

    embed.add_field(
        name="🌐 Local Ban Bridge",
        value=f"Active on `http://127.0.0.1:{LOCAL_API_PORT}/ban`",
        inline=False
    )
    embed.set_footer(text="DTEmpire Watchdog Security Network")

    if is_slash:
        await ctx_or_interaction.response.send_message(embed=embed, ephemeral=False)
    else:
        await ctx_or_interaction.send(embed=embed)


# ─────────────────────────────────────────────────────────────────────────────
# BOT CLIENT CLASS
# ─────────────────────────────────────────────────────────────────────────────
class WatchdogBotClient(commands.Bot):
    def __init__(self):
        intents = discord.Intents.default()
        intents.message_content = True
        super().__init__(command_prefix=["!", ">"], intents=intents)

    async def setup_hook(self):
        # Register persistent views so buttons work across bot restarts
        self.add_view(PublicAppealLaunchView())
        self.add_view(StaffAppealReviewView())
        self.add_view(SpecificBanAppealView())
        logger.info("Registered persistent Ban Appeal UI views.")

        # Start Local Ban Bridge HTTP Server
        asyncio.create_task(self.start_local_http_server())

        # Sync Application Slash Commands
        try:
            synced = await self.tree.sync()
            logger.info(f"Successfully synced {len(synced)} application slash commands.")
        except Exception as e:
            logger.error(f"Error syncing slash commands: {e}")

    async def start_local_http_server(self):
        app = web.Application()

        async def handle_health(request: web.Request) -> web.Response:
            return web.json_response({"status": "ok"})

        async def handle_get_unbans(request: web.Request) -> web.Response:
            """Minecraft plugin polls GET /unbans to receive players approved for unban."""
            unbans = load_pending_unbans()
            return web.json_response({"unbans": unbans})

        async def handle_ack_unban(request: web.Request) -> web.Response:
            """Minecraft plugin confirms POST /unbans/ack with {"player": "IGN"}."""
            try:
                data = await request.json()
                player = str(data.get("player", "")).strip()
                if player:
                    remove_pending_unban(player)
                    return web.json_response({"success": True})
            except Exception as e:
                logger.error(f"[Bridge] Error acknowledging unban: {e}")
            return web.json_response({"success": False, "error": "Invalid payload"}, status=400)

        app.router.add_post("/ban", self.handle_api_ban)
        app.router.add_get("/unbans", handle_get_unbans)
        app.router.add_post("/unbans/ack", handle_ack_unban)
        app.router.add_get("/health", handle_health)

        runner = web.AppRunner(app)
        await runner.setup()
        site = web.TCPSite(runner, "127.0.0.1", LOCAL_API_PORT)
        try:
            await site.start()
            logger.info(f"Local Watchdog HTTP Bridge listening on http://127.0.0.1:{LOCAL_API_PORT}")
        except Exception as e:
            logger.warning(f"Could not bind Local HTTP Bridge on port {LOCAL_API_PORT}: {e}")

    async def handle_api_ban(self, request: web.Request) -> web.Response:
        """Receives POST /ban from Minecraft Watchdog and posts ONE unified card with button."""
        try:
            data = await request.json()
            ign = str(data.get("player", "Unknown")).strip()
            ban_id = str(data.get("banId", "#WD-00000000")).strip()
            reason = str(data.get("reason", "Rule Violation")).strip()
            status = str(data.get("status", "Permanent Ban")).strip()
            appeal_url = str(data.get("appealUrl", "http://dsc.gg/dtempire-server")).strip()

            cfg = load_config()
            posted_any = False

            for gid_str, gcfg in cfg.items():
                ban_ch_id = gcfg.get("ban_channel_id")
                if not ban_ch_id:
                    continue
                ch = self.get_channel(ban_ch_id)
                if isinstance(ch, discord.TextChannel):
                    embed = create_ban_embed(ign, ban_id, reason, status, appeal_url)
                    view = SpecificBanAppealView(ign=ign, ban_id=ban_id)
                    await ch.send(embed=embed, view=view)
                    posted_any = True
                    logger.info(f"[Bridge] Posted unified ban card for {ign} ({ban_id}) in #{ch.name}")

            if posted_any:
                return web.json_response({"success": True})
            else:
                logger.warning(f"[Bridge] Received ban for {ign} but no ban_channel_id is configured!")
                return web.json_response({"success": False, "error": "No ban channel configured"}, status=400)
        except Exception as e:
            logger.error(f"[Bridge] Error handling /ban request: {e}")
            return web.json_response({"success": False, "error": str(e)}, status=500)


bot = WatchdogBotClient()


@bot.event
async def on_ready():
    bot_id = bot.user.id if bot.user else 0
    logger.info(f"Bot connected as {bot.user} (ID: {bot_id})")


@bot.event
async def on_message(message: discord.Message):
    await bot.process_commands(message)

    # If a message comes from a Webhook with the Watchdog ban card:
    # Delete the webhook post and replace it with ONE unified card from the bot with the button!
    if message.guild and message.webhook_id and message.embeds:
        for embed in message.embeds:
            if embed.title and "WATCHDOG BAN ENFORCED" in embed.title:
                # Auto-save ban channel if not already configured
                cfg = load_config()
                gid = str(message.guild.id)
                if gid not in cfg:
                    cfg[gid] = {}
                if not cfg[gid].get("ban_channel_id"):
                    cfg[gid]["ban_channel_id"] = message.channel.id
                    save_config(cfg)
                    channel_name = getattr(message.channel, "name", str(message.channel.id))
                    logger.info(f"Auto-configured ban_channel_id to {message.channel.id} (#{channel_name})")

                raw_player = extract_field_value(embed, "Player")
                ign = re.sub(r"[`*_\s]", "", raw_player) or "Player"
                raw_ban_id = extract_field_value(embed, "Ban ID")
                ban_id = re.sub(r"[`*_\s]", "", raw_ban_id) or "N/A"
                raw_reason = extract_field_value(embed, "Reason") or "Rule Violation"
                raw_status = extract_field_value(embed, "Status") or "Permanent Ban"

                # Attempt to delete the raw webhook post to avoid two separate messages
                try:
                    await message.delete()
                except Exception:
                    pass

                # Post ONE unified rich card directly from the bot WITH the button attached!
                unified_embed = create_ban_embed(ign, ban_id, raw_reason, raw_status)
                reply_view = SpecificBanAppealView(ign=ign, ban_id=ban_id)
                await message.channel.send(embed=unified_embed, view=reply_view)
                logger.info(f"[Webhook Intercept] Replaced webhook with unified ban card for {ign} ({ban_id})")
                break


# ─────────────────────────────────────────────────────────────────────────────
# SLASH COMMANDS (Modern Discord /commands)
# ─────────────────────────────────────────────────────────────────────────────
@bot.tree.command(name="setbanchannel", description="Set the channel where Watchdog ban records with appeal buttons will appear")
@app_commands.describe(channel="Text channel for public Watchdog ban cards")
@app_commands.default_permissions(administrator=True)
async def slash_set_ban_channel(interaction: discord.Interaction, channel: discord.TextChannel):
    await handle_set_ban_channel(interaction, channel, is_slash=True)


@bot.tree.command(name="setappeallog", description="Set the private staff channel where submitted appeals are reviewed")
@app_commands.describe(channel="Staff channel for reviewing appeals with Accept/Deny buttons")
@app_commands.default_permissions(administrator=True)
async def slash_set_appeal_log(interaction: discord.Interaction, channel: discord.TextChannel):
    await handle_set_appeal_log(interaction, channel, is_slash=True)


@bot.tree.command(name="banapeal", description="Alias for /setappeallog - set the staff appeals review channel")
@app_commands.describe(channel="Staff channel for reviewing appeals")
@app_commands.default_permissions(administrator=True)
async def slash_banapeal(interaction: discord.Interaction, channel: discord.TextChannel):
    await handle_set_appeal_log(interaction, channel, is_slash=True)


@bot.tree.command(name="postappealpanel", description="Post the permanent ban appeal panel embed with an appeal button")
@app_commands.describe(channel="Channel to post the panel into (defaults to current channel)")
@app_commands.default_permissions(administrator=True)
async def slash_post_appeal_panel(interaction: discord.Interaction, channel: Optional[discord.TextChannel] = None):
    target = channel or interaction.channel
    if isinstance(target, discord.TextChannel):
        await handle_post_appeal_panel(interaction, target, is_slash=True)
    else:
        await interaction.response.send_message("❌ Target must be a text channel.", ephemeral=True)


@bot.tree.command(name="appealstatus", description="Show configured Watchdog channels and permission health")
@app_commands.default_permissions(administrator=True)
async def slash_appeal_status(interaction: discord.Interaction):
    await handle_appeal_status(interaction, is_slash=True)


# ─────────────────────────────────────────────────────────────────────────────
# PREFIX COMMANDS (!command or >command)
# ─────────────────────────────────────────────────────────────────────────────
@bot.command(name="setbanchannel", aliases=["banchannel", "setbans"])
@commands.has_permissions(administrator=True)
async def cmd_set_ban_channel(ctx: commands.Context, channel: discord.TextChannel):
    """Set the public channel where Watchdog ban records are published."""
    await handle_set_ban_channel(ctx, channel, is_slash=False)


@bot.command(name="setappeallog", aliases=["setappeals", "appeallog", "banapeal"])
@commands.has_permissions(administrator=True)
async def cmd_set_appeal_log(ctx: commands.Context, channel: discord.TextChannel):
    """Set the private channel where staff review submitted appeals."""
    await handle_set_appeal_log(ctx, channel, is_slash=False)


@bot.command(name="postappealpanel", aliases=["setup_appeals", "appealpanel"])
@commands.has_permissions(administrator=True)
async def cmd_post_appeal_panel(ctx: commands.Context, channel: Optional[discord.TextChannel] = None):
    """Post the public ban appeal embed with the [Submit Ban Appeal] button."""
    target = channel or ctx.channel
    if isinstance(target, discord.TextChannel):
        await handle_post_appeal_panel(ctx, target, is_slash=False)
    else:
        await ctx.send("❌ Target must be a text channel.")


@bot.command(name="appealstatus", aliases=["watchdogstatus"])
@commands.has_permissions(administrator=True)
async def cmd_appeal_status(ctx: commands.Context):
    """Check current configuration and bot channel permissions."""
    await handle_appeal_status(ctx, is_slash=False)


# ─────────────────────────────────────────────────────────────────────────────
# ENTRYPOINT
# ─────────────────────────────────────────────────────────────────────────────
def main():
    token = os.getenv("DISCORD_BOT_TOKEN") or os.getenv("DISCORD_TOKEN")
    if not token:
        env_paths = [
            Path(__file__).parent / ".env",
            Path("/home/dargo/discordai/.env"),
            Path("/home/dargo/hermesthegreathelper/HermesBot/.env"),
        ]
        for p in env_paths:
            if p.exists():
                for line in p.read_text(encoding="utf-8").splitlines():
                    if line.startswith("DISCORD_BOT_TOKEN=") or line.startswith("DISCORD_TOKEN="):
                        val = line.split("=", 1)[1].strip().strip('"').strip("'")
                        if val:
                            token = val
                            break
            if token:
                break

    if not token:
        logger.error("No valid Discord bot token found!")
        sys.exit(1)

    logger.info("Starting DTEmpire Watchdog Discord Appeal Bot...")
    bot.run(token)


if __name__ == "__main__":
    main()
