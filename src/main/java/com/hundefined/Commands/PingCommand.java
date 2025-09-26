package com.hundefined.Commands;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

public class PingCommand implements Command {
    @Override
    public String getName() {
        return "ping";
    }

    @Override
    public String getDescription() {
        return "Checks the bot's latency to Discord's gateway.";
    }

    @Override
    public void executeSlash(SlashCommandInteractionEvent event) {
        long gatewayPing = event.getJDA().getGatewayPing();

        event.reply("🏓 Pong!\n" +
                        "**Gateway Ping:** " + gatewayPing + "ms\n" +
                        "**Bot Status:** Online and ready!")
                .queue();
    }
}