package com.hundefined;

import com.hundefined.config.BotConfig;
import com.hundefined.Database.DatabaseManager;
import com.hundefined.listeners.CommandListener;
import com.hundefined.listeners.ButtonInteractionHandler; // ADD THIS LINE
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumSet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.hundefined.tasks.PatchNotificationTask;

public class PatchNews {
    private static final Logger logger = LoggerFactory.getLogger(PatchNews.class);
    private static JDA jda;
    private static DatabaseManager dbManager;
    private static ScheduledExecutorService scheduler;
    private static PatchNotificationTask patchTask;

    public static void main(String[] args) {
        String botToken = BotConfig.getBotToken();
        String riotToken = BotConfig.getRiotToken();


        if (botToken == null || riotToken == null || botToken.isEmpty() || riotToken.isEmpty()) {
            logger.error("Bot token or Riot token not found in config.properties. Please provide valid tokens.");
            return;
        }

        try {
            // Initialize database first
            logger.info("Initializing database...");
            dbManager = DatabaseManager.getInstance();
            logger.info("Database initialized successfully!");

            // Build JDA instance
            logger.info("Starting Discord bot...");
            jda = JDABuilder.createDefault(botToken)
                    .enableIntents(EnumSet.allOf(GatewayIntent.class))
                    .addEventListeners(new CommandListener())
                    .addEventListeners(new ButtonInteractionHandler()) // ADD THIS LINE
                    .setActivity(Activity.playing("How to search things up"))
                    .build();

            jda.awaitReady();
            logger.info("Bot is online and ready!");

            // Register slash commands
            registerSlashCommands();

            // Start the patch notification task
            startPatchNotificationTask();

            // Add shutdown hook for graceful cleanup
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("Shutting down bot...");
                cleanup();
            }));

        } catch (Exception e) {
            logger.error("Error starting the bot: ", e);
            cleanup();
            System.exit(1);
        }

    }

    private static void registerSlashCommands() {
        if (jda == null) {
            logger.error("JDA instance is not initialized. Cannot register slash commands.");
            return;
        }

        logger.info("Registering Slash Commands...");
        jda.updateCommands()
                .addCommands(
                        // Basic commands
                        Commands.slash("ping", "Checks the bot's latency to Discord's gateway."),
                        Commands.slash("info", "Displays information about the bot."),
                        Commands.slash("echo", "Responds back with your message")
                                .addOption(OptionType.STRING, "text", "The text to echo", true),

                        // Patch-related commands
                        Commands.slash("latestpatch", "Shows information about the latest League of Legends patch."),
                        Commands.slash("subscribe", "Subscribe this channel to receive patch notifications."),
                        Commands.slash("unsubscribe", "Unsubscribe this channel from patch notifications.")
                )
                .queue(
                        success -> logger.info("Successfully registered {} slash commands!", success.size()),
                        failure -> logger.error("Failed to register slash commands: ", failure)
                );
    }

    private static void startPatchNotificationTask() {
        logger.info("Starting patch notification task...");

        scheduler = Executors.newScheduledThreadPool(1);
        patchTask = new PatchNotificationTask(jda, dbManager);

        // Check for new patches every 30 minutes
        scheduler.scheduleAtFixedRate(patchTask, 0, 30, TimeUnit.MINUTES);

        logger.info("Patch notification task started - checking every 30 minutes");
    }

    private static void cleanup() {
        logger.info("Performing cleanup...");

        try {
            // Shutdown patch notification task
            if (patchTask != null) {
                patchTask.shutdown();
            }

            if (scheduler != null && !scheduler.isShutdown()) {
                scheduler.shutdown();
                try {
                    if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                        scheduler.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    scheduler.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }

            // Close database connections
            if (dbManager != null) {
                dbManager.close();
            }

            // Shutdown JDA
            if (jda != null) {
                jda.shutdown();
                try {
                    if (!jda.awaitShutdown(10, TimeUnit.SECONDS)) {
                        jda.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    jda.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }

            logger.info("Cleanup completed successfully!");

        } catch (Exception e) {
            logger.error("Error during cleanup: ", e);
        }
    }

    // Getter methods for other classes to access these instances
    public static JDA getJDA() {
        return jda;
    }

    public static DatabaseManager getDatabaseManager() {
        return dbManager;
    }

}