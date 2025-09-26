package com.hundefined.Commands;

import com.hundefined.Database.DatabaseManager;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UnsubscribeCommand implements Command {
    private static final Logger logger = LoggerFactory.getLogger(UnsubscribeCommand.class);
    private final DatabaseManager dbManager;

    public UnsubscribeCommand() {
        this.dbManager = DatabaseManager.getInstance();
    }

    @Override
    public String getName() {
        return "unsubscribe";
    }

    @Override
    public String getDescription() {
        return "Unsubscribe this channel from League of Legends patch notifications";
    }

    @Override
    public void executeSlash(SlashCommandInteractionEvent event) {
        // Check if user has permission to manage channels
        if (!event.getMember().hasPermission(Permission.MANAGE_CHANNEL)) { // Fixed: MANAGE_CHANNEL not MANAGE_CHANNELS
            event.reply("You need the 'Manage Channels' permission to unsubscribe from patch notifications.")
                    .setEphemeral(true).queue();
            return;
        }

        String guildId = event.getGuild().getId();
        String channelId = event.getChannel().getId();

        try {
            boolean success = dbManager.unsubscribeServer(guildId, channelId);

            if (success) {
                event.reply("Successfully unsubscribed this channel from League of Legends patch notifications.\n" +
                                "Use `/subscribe` if you want to receive notifications again.")
                        .queue();

                logger.info("Server {} unsubscribed from patch notifications in channel {}", guildId, channelId);
            } else {
                event.reply("This channel was not subscribed to patch notifications, or an error occurred.")
                        .setEphemeral(true).queue();
            }

        } catch (Exception e) {
            logger.error("Error unsubscribing server {} from notifications", guildId, e);
            event.reply("An error occurred while unsubscribing from notifications. Please try again later.")
                    .setEphemeral(true).queue();
        }
    }
}