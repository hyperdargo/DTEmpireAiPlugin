#!/usr/bin/env python3
"""
DTEmpire Watchdog Discord Appeal Bot
Features:
  - Interactive [Submit Ban Appeal] button panel (persistent across restarts)
  - Ban Appeal Modal form asking: Minecraft IGN, Ban ID, what happened, why unban
  - Staff Review Channel (configured via !setappeallog #channel)
  - Staff buttons:
      [🟢 Accept Appeal]: Marks approved, sends DM to player, gives staff console command: /watchdog unban <IGN>
      [🔴 Deny Appeal]: Opens modal asking staff for denial reason, marks denied, sends DM to player with reason
  - Standalone manual console unban: No localhost server or open ports needed!
"""

import os
import sys
import json
import logging
from pathlib import Path
import re
import discord
from discord.ext import commands

logging.basicConfig(level=logging.INFO, format="[%(asctime)s] %(levelname)s: %(message)s")
logger = logging.getLogger("DTEmpireAppealBot")

CONFIG_FILE = Path(__file__).parent / "appeal_config.json"


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


def extract_field_value(embed: discord.Embed, field_name: str) -> str:
    for f in embed.fields:
        if f.name and field_name.lower() in f.name.lower():
            return f.value or ""
    return ""


# ─────────────────────────────────────────────────────────────────────────────
# MODAL: Staff Denial Reason Modal
# ─────────────────────────────────────────────────────────────────────────────
class StaffDenyReasonModal(discord.ui.Modal, title="Deny Ban Appeal"):
    reason_input = discord.ui.TextInput(
        label="Reason for Denial",
        placeholder="Explain why this appeal is denied (e.g. Cheating admitted, insufficient evidence)...",
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

        # Send DM to player
        try:
            bot = interaction.client
            user = await bot.fetch_user(self.target_user_id)
            if user:
                dm_embed = discord.Embed(
                    title="❌ DTEmpire Ban Appeal Denied",
                    description=(
                        f"Hello {user.name},\n\n"
                        f"Your ban appeal for Minecraft account **`{self.ign}`** has been reviewed and **DENIED** by DTEmpire Staff.\n\n"
                        f"**Reason:**\n> {self.reason_input.value}\n\n"
                        f"Your ban remains in effect on the server."
                    ),
                    color=0xE74C3C
                )
                dm_embed.set_footer(text="DTEmpire Watchdog Security Network")
                await user.send(embed=dm_embed)
        except Exception as e:
            logger.warning(f"Could not send denial DM to user {self.target_user_id}: {e}")

        await interaction.followup.send(f"❌ Appeal for **`{self.ign}`** was marked as DENIED and player was notified via DM.", ephemeral=True)


# ─────────────────────────────────────────────────────────────────────────────
# VIEW: Staff Review View (Persistent)
# ─────────────────────────────────────────────────────────────────────────────
class StaffAppealReviewView(discord.ui.View):
    def __init__(self):
        super().__init__(timeout=None)

    @discord.ui.button(label="Accept Appeal", style=discord.ButtonStyle.success, emoji="🟢", custom_id="dtempire_staff_accept_btn")
    async def accept_appeal(self, interaction: discord.Interaction, button: discord.ui.Button):
        perms = interaction.user.guild_permissions if isinstance(interaction.user, discord.Member) else None
        if not perms or (not perms.kick_members and not perms.administrator and not perms.manage_guild):
            await interaction.response.send_message("❌ Only server staff and moderators can review appeals.", ephemeral=True)
            return

        if not interaction.message or not interaction.message.embeds:
            await interaction.response.send_message("❌ Could not read appeal embed.", ephemeral=True)
            return

        embed = interaction.message.embeds[0]
        raw_ign = extract_field_value(embed, "IGN")
        ign = re.sub(r"[`*_\s]", "", raw_ign) or "Unknown"

        raw_user = extract_field_value(embed, "Discord User")
        user_match = re.search(r"(\d{17,20})", raw_user)
        target_user_id = int(user_match.group(1)) if user_match else None

        await interaction.response.defer()

        # Update Embed
        embed.color = 0x2ECC71  # Green
        new_fields = []
        for f in embed.fields:
            if f.name and "status" in f.name.lower():
                new_fields.append((f.name, f"✅ **ACCEPTED** by {interaction.user.mention}", False))
            else:
                new_fields.append((f.name, f.value, f.inline))

        embed.clear_fields()
        for name, val, inline in new_fields:
            embed.add_field(name=name, value=val, inline=inline)

        embed.add_field(
            name="💻 Console Action Required",
            value=f"Run in Minecraft console to unban:\n`/watchdog unban {ign}` or `pardon {ign}`",
            inline=False
        )

        # Disable buttons
        view = discord.ui.View()
        btn_accept = discord.ui.Button(label="Accepted", style=discord.ButtonStyle.success, emoji="🟢", disabled=True)
        btn_deny = discord.ui.Button(label="Deny Appeal", style=discord.ButtonStyle.danger, emoji="🔴", disabled=True)
        view.add_item(btn_accept)
        view.add_item(btn_deny)

        await interaction.message.edit(embed=embed, view=view)

        # Send DM to player
        if target_user_id:
            try:
                bot = interaction.client
                user = await bot.fetch_user(target_user_id)
                if user:
                    dm_embed = discord.Embed(
                        title="🎉 DTEmpire Ban Appeal Accepted!",
                        description=(
                            f"Great news {user.name}!\n\n"
                            f"Your ban appeal for Minecraft account **`{ign}`** has been **ACCEPTED** by DTEmpire Staff.\n\n"
                            f"Your account is being unbanned on the server. You may rejoin shortly.\n\n"
                            f"**Server IP:** `play.dtempire.com`\n"
                            f"**Discord:** http://dsc.gg/dtempire-server\n\n"
                            f"Welcome back to DTEmpire!"
                        ),
                        color=0x2ECC71
                    )
                    dm_embed.set_footer(text="DTEmpire Watchdog Security Network")
                    await user.send(embed=dm_embed)
            except Exception as e:
                logger.warning(f"Could not send approval DM to user {target_user_id}: {e}")

        await interaction.followup.send(
            f"✅ Appeal for **`{ign}`** approved! Player has been notified via DM.\n"
            f"⚠️ **Run in Minecraft console:** `watchdog unban {ign}`",
            ephemeral=True
        )

    @discord.ui.button(label="Deny Appeal", style=discord.ButtonStyle.danger, emoji="🔴", custom_id="dtempire_staff_deny_btn")
    async def deny_appeal(self, interaction: discord.Interaction, button: discord.ui.Button):
        perms = interaction.user.guild_permissions if isinstance(interaction.user, discord.Member) else None
        if not perms or (not perms.kick_members and not perms.administrator and not perms.manage_guild):
            await interaction.response.send_message("❌ Only server staff and moderators can review appeals.", ephemeral=True)
            return

        if not interaction.message or not interaction.message.embeds:
            await interaction.response.send_message("❌ Could not read appeal embed.", ephemeral=True)
            return

        embed = interaction.message.embeds[0]
        raw_ign = extract_field_value(embed, "IGN")
        ign = re.sub(r"[`*_\s]", "", raw_ign) or "Unknown"

        raw_user = extract_field_value(embed, "Discord User")
        user_match = re.search(r"(\d{17,20})", raw_user)
        target_user_id = int(user_match.group(1)) if user_match else 0

        modal = StaffDenyReasonModal(target_user_id=target_user_id, ign=ign)
        await interaction.response.send_modal(modal)


# ─────────────────────────────────────────────────────────────────────────────
# MODAL: Player Appeal Submission Modal
# ─────────────────────────────────────────────────────────────────────────────
class PlayerBanAppealModal(discord.ui.Modal, title="🛡️ DTEmpire Ban Appeal Form"):
    ign = discord.ui.TextInput(
        label="Minecraft In-Game Name (IGN)",
        placeholder="e.g. Steve",
        min_length=3,
        max_length=16,
        required=True
    )
    ban_id = discord.ui.TextInput(
        label="Ban ID (Shown on Kick/Ban Screen)",
        placeholder="e.g. #WD-94820194",
        max_length=32,
        required=False
    )
    activity = discord.ui.TextInput(
        label="What were you doing when banned?",
        style=discord.TextStyle.paragraph,
        placeholder="Mining in cave, sprint-jumping, fighting mobs, high ping lag...",
        max_length=1000,
        required=True
    )
    appeal_reason = discord.ui.TextInput(
        label="Why should you be unbanned?",
        style=discord.TextStyle.paragraph,
        placeholder="Explain why this detection was a mistake or why you deserve a second chance...",
        max_length=1000,
        required=True
    )

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
                "⚠️ Staff appeals review channel is not configured yet. Please ask an administrator to run `!setappeallog #channel`.",
                ephemeral=True
            )
            return

        # Reply to user
        await interaction.response.send_message(
            f"✅ **Thank you, {interaction.user.mention}!**\n"
            f"Your appeal for Minecraft account **`{self.ign.value}`** has been submitted to DTEmpire staff.\n"
            f"You will receive a notification via Direct Message when a decision is made.",
            ephemeral=True
        )

        # Post Embed in Staff Review Channel
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
        if isinstance(log_channel, discord.TextChannel):
            await log_channel.send(embed=embed, view=view)


# ─────────────────────────────────────────────────────────────────────────────
# VIEW: Public Launch Button (Persistent)
# ─────────────────────────────────────────────────────────────────────────────
class PublicAppealLaunchView(discord.ui.View):
    def __init__(self):
        super().__init__(timeout=None)

    @discord.ui.button(label="Submit Ban Appeal", style=discord.ButtonStyle.primary, emoji="📩", custom_id="dtempire_btn_launch_appeal")
    async def launch_appeal(self, interaction: discord.Interaction, button: discord.ui.Button):
        await interaction.response.send_modal(PlayerBanAppealModal())


# ─────────────────────────────────────────────────────────────────────────────
# BOT INITIALIZATION
# ─────────────────────────────────────────────────────────────────────────────
intents = discord.Intents.default()
intents.message_content = True
bot = commands.Bot(command_prefix=["!", ">"], intents=intents)


@bot.event
async def on_ready():
    bot_id = bot.user.id if bot.user else 0
    logger.info(f"Bot connected as {bot.user} (ID: {bot_id})")
    bot.add_view(PublicAppealLaunchView())
    bot.add_view(StaffAppealReviewView())
    logger.info("Registered persistent Ban Appeal UI views")


@bot.command(name="setappeallog", aliases=["setappeals", "appeallog"])
@commands.has_permissions(administrator=True)
async def set_appeal_log(ctx: commands.Context, channel: discord.TextChannel):
    """Set the private channel where staff review submitted appeals."""
    if not ctx.guild:
        return
    cfg = load_config()
    gid = str(ctx.guild.id)
    if gid not in cfg:
        cfg[gid] = {}
    cfg[gid]["log_channel_id"] = channel.id
    save_config(cfg)

    await ctx.send(f"✅ Staff ban appeals review channel set to {channel.mention}!")


@bot.command(name="postappealpanel", aliases=["setup_appeals", "appealpanel"])
@commands.has_permissions(administrator=True)
async def post_appeal_panel(ctx: commands.Context):
    """Post the public ban appeal embed with the [Submit Ban Appeal] button."""
    embed = discord.Embed(
        title="🛡️ DTEmpire Network • Ban Appeals",
        description=(
            "If you were banned by **Watchdog Anti-Cheat** or staff and believe it was a mistake or false detection, "
            "you can submit an official ban appeal here.\n\n"
            "**📌 Instructions:**\n"
            "• Click the **Submit Ban Appeal** button below.\n"
            "• Provide your exact Minecraft in-game username.\n"
            "• Enter the Ban ID shown on your kick/reconnect screen (`#WD-XXXXXXXX`).\n"
            "• Answer the questions honestly with as much context as possible.\n\n"
            "Our staff team will review your case. **You will receive a Direct Message on Discord when your appeal is reviewed.**"
        ),
        color=0x3498DB
    )
    embed.set_thumbnail(url="https://mc-heads.net/avatar/Watchdog/128")
    embed.set_footer(text="DTEmpire Watchdog Security Network • Ban Appeals")

    view = PublicAppealLaunchView()
    await ctx.send(embed=embed, view=view)
    try:
        await ctx.message.delete()
    except Exception:
        pass


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
