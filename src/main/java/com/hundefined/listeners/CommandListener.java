package com.hundefined.listeners;

import com.hundefined.Commands.*;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class CommandListener extends ListenerAdapter {
    private static final Logger logger = LoggerFactory.getLogger(CommandListener.class);
    private final Map<String, Command> commands; // Fixed: Changed from 'commands' to 'Command'

    public CommandListener() {
        commands = new HashMap<>();

        // Register all commands
        registerCommand(new PingCommand());
        registerCommand(new InfoCommand());
        registerCommand(new EchoCommand());
        registerCommand(new LatestPatchCommand());
        registerCommand(new SubscribeCommand());
        registerCommand(new UnsubscribeCommand());

        logger.info("Registered {} commands", commands.size());
    }

    private void registerCommand(Command command) {
        commands.put(command.getName().toLowerCase(), command); // Fixed: changed 'Command' to 'command'
        logger.debug("Registered command: {}", command.getName());
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        String commandName = event.getName().toLowerCase();
        Command command = commands.get(commandName);

        if (command != null) {
            try {
                logger.info("Executing command '{}' for user '{}' in guild '{}'",
                        commandName,
                        event.getUser().getAsTag(),
                        event.getGuild() != null ? event.getGuild().getName() : "DM");

                command.executeSlash(event);

            } catch (Exception e) {
                logger.error("Error executing command '{}': {}", commandName, e.getMessage(), e);

                // Try to respond with error message if the interaction hasn't been acknowledged yet
                if (!event.isAcknowledged()) {
                    event.reply("❌ An error occurred while executing this command. Please try again later.")
                            .setEphemeral(true)
                            .queue(
                                    success -> logger.debug("Sent error response for command '{}'", commandName),
                                    failure -> logger.error("Failed to send error response for command '{}': {}",
                                            commandName, failure.getMessage())
                            );
                } else {
                    // If already acknowledged, edit the original response
                    event.getHook().editOriginal("❌ An error occurred while processing this command.")
                            .queue(
                                    success -> logger.debug("Edited response with error for command '{}'", commandName),
                                    failure -> logger.error("Failed to edit response for command '{}': {}",
                                            commandName, failure.getMessage())
                            );
                }
            }
        } else {
            logger.warn("Unknown command received: {}", commandName);
            event.reply("❌ Unknown command: " + commandName)
                    .setEphemeral(true)
                    .queue();
        }
    }

    // Method to get all registered commands (useful for debugging)
    public Map<String, Command> getCommands() {
        return new HashMap<>(commands);
    }

    // Method to get command count
    public int getCommandCount() {
        return commands.size();
    }
}