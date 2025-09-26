package com.hundefined.Commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

import java.awt.Color;

public class InfoCommand implements Command {
    @Override
    public String getName() {
        return "info";
    }

    @Override
    public String getDescription() {
        return "Displays information about the bot.";
    }

    @Override
    public void executeSlash(SlashCommandInteractionEvent event) {
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("🤖 League News Bot")
                .setDescription("A Discord bot that keeps you updated with the latest League of Legends patch notes!")
                .addField("📊 Version", "1.0.0", true)
                .addField("⚡ Library", "JDA 6.0.0", true)
                .addField("🔧 Language", "Java 17", true)
                .addField("🎮 Features",
                        "• Latest patch information\n" +
                                "• Automatic patch notifications\n" +
                                "• Database-powered tracking\n" +
                                "• Riot Games API integration", false)
                .addField("📝 Commands",
                        "`/ping` - Check bot latency\n" +
                                "`/latestpatch` - Get latest patch info\n" +
                                "`/subscribe` - Subscribe to notifications\n" +
                                "`/unsubscribe` - Unsubscribe from notifications", false)
                .setColor(Color.CYAN)
                .setFooter("Created by hundefined", null)
                .setTimestamp(java.time.Instant.now());

        event.replyEmbeds(embed.build()).queue();
    }
}
