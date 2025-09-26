package com.hundefined.Commands;

import com.hundefined.Database.DatabaseManager;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SubscribeCommand implements Command {
    private static final Logger logger = LoggerFactory.getLogger(SubscribeCommand.class);
    private final DatabaseManager dbManager;

    public SubscribeCommand() {
        this.dbManager = DatabaseManager.getInstance();
    }

    @Override
    public String getName() {
        return "subscribe";
    }

    @Override
    public String getDescription() {
        return "Subscribe this channel to receive League of Legends patch notifications";
    }

    @Override
    public void executeSlash(SlashCommandInteractionEvent event) {
        // Check if user has permission to manage channels
        if (!event.getMember().hasPermission(Permission.MANAGE_CHANNEL)) { // Fixed: MANAGE_CHANNEL not MANAGE_CHANNELS
            event.reply("You need the 'Manage Channels' permission to subscribe to patch notifications.")
                    .setEphemeral(true).queue();
            return;
        }

        String guildId = event.getGuild().getId();
        String channelId = event.getChannel().getId();

        try {
            boolean success = dbManager.subscribeServer(guildId, channelId);

            if (success) {
                event.reply("Successfully subscribed this channel to League of Legends patch notifications!\n" +
                                "You'll now receive updates whenever a new patch is released.\n" +
                                "Use `/unsubscribe` if you want to stop receiving notifications.")
                        .queue();

                logger.info("Server {} subscribed to patch notifications in channel {}", guildId, channelId);
            } else {
                event.reply("This channel is already subscribed to patch notifications, or an error occurred.")
                        .setEphemeral(true).queue();
            }

        } catch (Exception e) {
            logger.error("Error subscribing server {} to notifications", guildId, e);
            event.reply("An error occurred while subscribing to notifications. Please try again later.")
                    .setEphemeral(true).queue();
        }
    }
}