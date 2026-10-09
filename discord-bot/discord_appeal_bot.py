#!/usr/bin/env python3
"""
DTEmpire Watchdog Discord Appeal Bot
Allows banned players to submit interactive ban appeal forms.
Staff review appeals with [Approve & Unban] and [Reject] buttons,
which automatically execute instant unbans on the Paper Minecraft server.
"""

import os
import sys
import json
import logging
import urllib.request
import urllib.error
import discord
from discord.ext import commands

logging.basicConfig(level=logging.INFO, format="[%(asctime)s] %(levelname)s: %(message)s")

# ─────────────────────────────────────────────────────────────────────────────
# CONFIGURATION
# Set these via environment variables or edit directly:
# ─────────────────────────────────────────────────────────────────────────────
DISCORD_TOKEN = os.getenv("DISCORD_BOT_TOKEN", "")
APPEALS_CHANNEL_ID = int(os.getenv("APPEALS_CHANNEL_ID", "0"))      # Channel where players click [Submit Appeal]
STAFF_CHANNEL_ID = int(os.getenv("STAFF_CHANNEL_ID", "0"))          # Channel where Staff review appeals
WATCHDOG_API_URL = os.getenv("WATCHDOG_API_URL", "http://127.0.0.1:25609")
WATCHDOG_API_KEY = os.getenv("WATCHDOG_API_KEY", "dtempire-watchdog-secret-key")

intents = discord.Intents.default()
intents.message_content = True
bot = commands.Bot(command_prefix="!", intents=intents)


def unban_player_api(player_name: str, staff_name: str, reason: str = "Discord Appeal Approved") -> dict:
    """Send an unban request to the DTEmpire Paper Minecraft server API."""
    url = f"{WATCHDOG_API_URL}/api/unban"
    payload = json.dumps({
        "player": player_name,
        "staff": staff_name,
        "reason": reason
    }).encode("utf-8")

    req = urllib.request.Request(
        url,
        data=payload,
        headers={
            "Content-Type": "application/json",
            "Authorization": f"Bearer {WATCHDOG_API_KEY}"
        },
        method="POST"
    )

    try:
        with urllib.request.urlopen(req, timeout=5) as response:
            return json.loads(response.read().decode("utf-8"))
    except Exception as e:
        logging.error(f"Failed to communicate with Watchdog API at {url}: {e}")
        return {"success": False, "error": str(e)}


# ─────────────────────────────────────────────────────────────────────────────
# MODAL: Ban Appeal Form
# ─────────────────────────────────────────────────────────────────────────────
class BanAppealModal(discord.ui.Modal, title="🛡️ DTEmpire Ban Appeal Form"):
    ign = discord.ui.TextInput(
        label="Minecraft In-Game Name (IGN)",
        placeholder="e.g. Steve",
        min_length=3,
        max_length=16,
        required=True
    )
    ban_id = discord.ui.TextInput(
        label="Ban ID (Shown on Kick Screen)",
        placeholder="e.g. #WD-94820194",
        required=False,
        max_length=32
    )
    activity = discord.ui.TextInput(
        label="What were you doing when you were banned?",
        style=discord.TextStyle.paragraph,
        placeholder="Mining in cave, sprint-jumping, fighting mobs, high ping...",
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
        await interaction.response.send_message(
            f"✅ **Thank you, {interaction.user.mention}!**\n"
            f"Your appeal for Minecraft account **`{self.ign.value}`** has been submitted to DTEmpire staff.\n"
            f"You will receive a notification when staff review your case.",
            ephemeral=True
        )

        # Post to Staff Review Channel
        staff_channel = bot.get_channel(STAFF_CHANNEL_ID)
        if not staff_channel or not hasattr(staff_channel, "send"):
            logging.error(f"STAFF_CHANNEL_ID ({STAFF_CHANNEL_ID}) not found or cannot send messages!")
            return

        embed = discord.Embed(
            title=f"⚖️ Ban Appeal Submission: {self.ign.value}",
            color=0xF1C40F, # Amber
            timestamp=interaction.created_at
        )
        embed.set_thumbnail(url=f"https://mc-heads.net/avatar/{self.ign.value}/128")
        embed.add_field(name="👤 Minecraft IGN", value=f"`{self.ign.value}`", inline=True)
        embed.add_field(name="💬 Discord User", value=interaction.user.mention, inline=True)
        embed.add_field(name="🆔 Ban ID", value=f"`{self.ban_id.value or 'N/A'}`", inline=True)
        embed.add_field(name="📍 What happened?", value=self.activity.value, inline=False)
        embed.add_field(name="📝 Why unban?", value=self.appeal_reason.value, inline=False)
        embed.add_field(name="📊 Status", value="⏳ **PENDING REVIEW**", inline=False)
        embed.set_footer(text="DTEmpire Watchdog Anti-Cheat • Ban Appeals")

        view = StaffAppealReviewView(
            ign=self.ign.value,
            discord_user_id=interaction.user.id
        )
        await staff_channel.send(embed=embed, view=view)


# ─────────────────────────────────────────────────────────────────────────────
# VIEW: Staff Review Buttons [Approve & Unban] / [Reject]
# ─────────────────────────────────────────────────────────────────────────────
class StaffAppealReviewView(discord.ui.View):
    def __init__(self, ign: str, discord_user_id: int):
        super().__init__(timeout=None) # Persistent
        self.ign = ign
        self.discord_user_id = discord_user_id

    @discord.ui.button(label="Approve & Unban", style=discord.ButtonStyle.success, emoji="🟢", custom_id="btn_approve_unban")
    async def approve_unban(self, interaction: discord.Interaction, button: discord.ui.Button):
        # Check staff permissions
        if not isinstance(interaction.user, discord.Member) or (
            not interaction.user.guild_permissions.kick_members and not interaction.user.guild_permissions.administrator
        ):
            await interaction.response.send_message("❌ Only server staff can approve appeals.", ephemeral=True)
            return

        await interaction.response.defer()

        # Send API request to Paper Minecraft Server
        res = unban_player_api(self.ign, interaction.user.display_name, reason="Discord Appeal Approved")

        # Disable all buttons
        for child in self.children:
            if isinstance(child, discord.ui.Button):
                child.disabled = True

        if interaction.message:
            embed = interaction.message.embeds[0]
            embed.color = 0x2ECC71 # Green
            for i, field in enumerate(embed.fields):
                if field.name == "📊 Status":
                    embed.set_field_at(i, name="📊 Status", value=f"✅ **APPROVED & UNBANNED** by {interaction.user.mention}", inline=False)
                    break

            await interaction.message.edit(embed=embed, view=self)

        # Notify the player via DM if possible
        try:
            user = await bot.fetch_user(self.discord_user_id)
            if user:
                dm_embed = discord.Embed(
                    title="🎉 Ban Appeal Approved!",
                    description=f"Your appeal for Minecraft account **`{self.ign}`** has been **APPROVED** by DTEmpire Staff!\n\n"
                                f"You have been unbanned and may rejoin the server immediately.\n\n"
                                f"**Server IP:** `play.dtempire.com`\n"
                                f"Welcome back, and thank you for playing on DTEmpire!",
                    color=0x2ECC71
                )
                await user.send(embed=dm_embed)
        except Exception as e:
            logging.warning(f"Could not send DM to user {self.discord_user_id}: {e}")

        await interaction.followup.send(f"✅ Successfully unbanned **`{self.ign}`** in Minecraft and closed appeal.", ephemeral=True)

    @discord.ui.button(label="Reject Appeal", style=discord.ButtonStyle.danger, emoji="🔴", custom_id="btn_reject_appeal")
    async def reject_appeal(self, interaction: discord.Interaction, button: discord.ui.Button):
        if not isinstance(interaction.user, discord.Member) or (
            not interaction.user.guild_permissions.kick_members and not interaction.user.guild_permissions.administrator
        ):
            await interaction.response.send_message("❌ Only server staff can reject appeals.", ephemeral=True)
            return

        await interaction.response.defer()

        # Disable all buttons
        for child in self.children:
            if isinstance(child, discord.ui.Button):
                child.disabled = True

        if interaction.message:
            embed = interaction.message.embeds[0]
            embed.color = 0xE74C3C # Red
            for i, field in enumerate(embed.fields):
                if field.name == "📊 Status":
                    embed.set_field_at(i, name="📊 Status", value=f"❌ **REJECTED** by {interaction.user.mention}", inline=False)
                    break

            await interaction.message.edit(embed=embed, view=self)

        # Notify the player via DM
        try:
            user = await bot.fetch_user(self.discord_user_id)
            if user:
                dm_embed = discord.Embed(
                    title="❌ Ban Appeal Update",
                    description=f"Your appeal for Minecraft account **`{self.ign}`** was reviewed and **REJECTED** by DTEmpire Staff.\n\n"
                                f"Your ban remains in effect.",
                    color=0xE74C3C
                )
                await user.send(embed=dm_embed)
        except Exception as e:
            logging.warning(f"Could not send DM to user {self.discord_user_id}: {e}")

        await interaction.followup.send(f"❌ Appeal for **`{self.ign}`** marked as rejected.", ephemeral=True)


# ─────────────────────────────────────────────────────────────────────────────
# VIEW: Public Persistent Button [Submit Ban Appeal]
# ─────────────────────────────────────────────────────────────────────────────
class PublicAppealLaunchView(discord.ui.View):
    def __init__(self):
        super().__init__(timeout=None) # Persistent

    @discord.ui.button(label="Submit Ban Appeal", style=discord.ButtonStyle.primary, emoji="📩", custom_id="btn_launch_appeal_modal")
    async def launch_appeal(self, interaction: discord.Interaction, button: discord.ui.Button):
        await interaction.response.send_modal(BanAppealModal())


# ─────────────────────────────────────────────────────────────────────────────
# BOT COMMANDS & LIFECYCLE
# ─────────────────────────────────────────────────────────────────────────────
@bot.event
async def on_ready():
    logging.info(f"DTEmpire Appeal Bot logged in as {bot.user} (ID: {bot.user.id})")
    bot.add_view(PublicAppealLaunchView())
    logging.info("Registered persistent PublicAppealLaunchView")


@bot.command(name="setup_appeals")
@commands.has_permissions(administrator=True)
async def setup_appeals(ctx: commands.Context):
    """Admin command to post the permanent Ban Appeal embed with button into the current channel."""
    embed = discord.Embed(
        title="🛡️ DTEmpire Network • Ban Appeals",
        description=(
            "If you were banned by **Watchdog Anti-Cheat** or staff and believe it was a false positive, "
            "you can submit a formal ban appeal here.\n\n"
            "**Guidelines:**\n"
            "• Please answer honestly with as much context as possible.\n"
            "• Provide your in-game name and the Ban ID shown on your kick screen.\n"
            "• Our staff team will review your case and you will receive a notification.\n\n"
            "Click the button below to fill out your appeal form:"
        ),
        color=0x3498DB # Blue
    )
    embed.set_thumbnail(url="https://mc-heads.net/avatar/Watchdog/128")
    embed.set_footer(text="DTEmpire Watchdog Security Network")

    view = PublicAppealLaunchView()
    await ctx.send(embed=embed, view=view)
    await ctx.message.delete()


@bot.command(name="test_unban")
@commands.has_permissions(administrator=True)
async def test_unban(ctx: commands.Context, player: str):
    """Test the unban API connection with the Minecraft server."""
    res = unban_player_api(player, ctx.author.display_name, reason="Manual Test Unban")
    await ctx.send(f"Result for unbanning `{player}`: ```json\n{json.dumps(res, indent=2)}\n```")


if __name__ == "__main__":
    if not DISCORD_TOKEN:
        print("ERROR: DISCORD_BOT_TOKEN environment variable is not set!")
        print("Usage: DISCORD_BOT_TOKEN='...' APPEALS_CHANNEL_ID='...' STAFF_CHANNEL_ID='...' python3 discord_appeal_bot.py")
        sys.exit(1)

    bot.run(DISCORD_TOKEN)
