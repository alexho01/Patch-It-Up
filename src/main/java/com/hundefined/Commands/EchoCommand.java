package com.hundefined.Commands;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

public class EchoCommand implements Command {
    @Override
    public String getName() {
        return "echo";
    }

    @Override
    public String getDescription() {
        return "Responds back with your message";
    }

    @Override
    public void executeSlash(SlashCommandInteractionEvent event) {
        String text = event.getOption("text").getAsString();

        // Simple validation
        if (text.length() > 2000) {
            event.reply("❌ Message is too long! Please keep it under 2000 characters.")
                    .setEphemeral(true).queue();
            return;
        }

        if (text.trim().isEmpty()) {
            event.reply("❌ You need to provide some text to echo!")
                    .setEphemeral(true).queue();
            return;
        }

        event.reply("🔄 **Echo:** " + text).queue();
    }
}