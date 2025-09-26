package com.hundefined.tasks;

import com.hundefined.Database.DatabaseManager;
import com.hundefined.services.RiotApiService;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class PatchNotificationTask implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(PatchNotificationTask.class);
    private static final int MAX_MESSAGE_LENGTH = 2000;

    private final JDA jda;
    private final DatabaseManager dbManager;
    private final RiotApiService riotApi;
    private volatile boolean running = true;

    public PatchNotificationTask(JDA jda, DatabaseManager dbManager) {
        this.jda = jda;
        this.dbManager = dbManager;
        this.riotApi = new RiotApiService();
    }

    @Override
    public void run() {
        if (!running) {
            logger.debug("Patch notification task is shutting down, skipping execution");
            return;
        }

        try {
            logger.info("Starting patch notification check...");
            checkForNewPatches();
            logger.info("Patch notification check completed");

        } catch (Exception e) {
            logger.error("Error during patch notification check", e);
        }
    }

    private void checkForNewPatches() {
        try {
            // Use the improved method to get current patch version
            String patchVersion = riotApi.getCurrentPatchVersion();

            if (patchVersion == null) {
                logger.warn("Could not determine current patch version");
                return;
            }

            logger.info("Checking for patch version: {}", patchVersion);

            // Check if patch already exists in database
            DatabaseManager.PatchInfo existingPatch = dbManager.getPatch(patchVersion);

            if (existingPatch == null) {
                logger.info("New patch detected: {}", patchVersion);

                // Fetch full content
                RiotApiService.PatchContent content = riotApi.fetchPatchContent(patchVersion);

                if (content != null && hasValidContent(content)) {
                    // Save to database
                    boolean saved = dbManager.savePatch(
                            patchVersion,
                            content.title != null ? content.title : "Patch " + patchVersion + " Notes",
                            LocalDateTime.now(),
                            content.url,
                            content.overview != null ? truncateText(content.overview, 500) : "New patch released!"
                    );

                    if (saved) {
                        logger.info("Successfully saved new patch: {}", patchVersion);

                        // Get the saved patch info (with ID)
                        DatabaseManager.PatchInfo newPatch = dbManager.getPatch(patchVersion);

                        if (newPatch != null) {
                            // Send notifications with full content
                            sendFullPatchNotifications(newPatch, content);
                        }
                    } else {
                        logger.warn("Failed to save patch {} to database", patchVersion);
                    }
                } else {
                    logger.warn("No valid content found for patch {}, creating basic entry", patchVersion);

                    // Create basic patch entry even if content extraction failed
                    boolean saved = dbManager.savePatch(
                            patchVersion,
                            "Patch " + patchVersion + " Notes",
                            LocalDateTime.now(),
                            "ENTER URL" +
                                    patchVersion.replace(".", "-") + "-notes/",
                            "New patch is now live! Check the official patch notes for details."
                    );

                    if (saved) {
                        DatabaseManager.PatchInfo basicPatch = dbManager.getPatch(patchVersion);
                        if (basicPatch != null) {
                            sendBasicPatchNotifications(basicPatch);
                        }
                    }
                }
            } else {
                logger.debug("Patch {} already exists in database", patchVersion);
            }

        } catch (Exception e) {
            logger.error("Error checking for new patches", e);
        }
    }

    private boolean hasValidContent(RiotApiService.PatchContent content) {
        return content != null &&
                ((content.championChanges != null && !content.championChanges.isEmpty()) ||
                        (content.itemChanges != null && !content.itemChanges.isEmpty()) ||
                        (content.bugFixes != null && !content.bugFixes.isEmpty()) ||
                        (content.overview != null && !content.overview.trim().isEmpty()));
    }

    private void sendFullPatchNotifications(DatabaseManager.PatchInfo patch, RiotApiService.PatchContent content) {
        try {
            List<DatabaseManager.ServerSubscription> subscriptions = dbManager.getSubscribedServers();

            if (subscriptions.isEmpty()) {
                logger.info("No servers subscribed to patch notifications");
                return;
            }

            logger.info("Sending full patch notifications for {} to {} servers", patch.version, subscriptions.size());

            int successCount = 0;
            int failCount = 0;

            for (DatabaseManager.ServerSubscription subscription : subscriptions) {
                try {
                    if (dbManager.wasNotificationSent(subscription.guildId, patch.id)) {
                        logger.debug("Notification already sent to server {} for patch {}",
                                subscription.guildId, patch.version);
                        continue;
                    }

                    TextChannel channel = jda.getTextChannelById(subscription.channelId);

                    if (channel != null) {
                        // Send announcement embed first
                        EmbedBuilder announcementEmbed = createAnnouncementEmbed(patch, content);
                        channel.sendMessageEmbeds(announcementEmbed.build()).queue(
                                success -> {
                                    logger.debug("Sent announcement embed to server {}", subscription.guildId);
                                },
                                failure -> logger.warn("Failed to send announcement to server {}: {}",
                                        subscription.guildId, failure.getMessage())
                        );

                        // Send patch content messages
                        List<String> messages = buildPatchNotesMessages(content);
                        for (int i = 0; i < messages.size(); i++) {
                            final String msg = messages.get(i);
                            final int messageIndex = i;

                            // Add delay between messages to avoid rate limits
                            Thread.sleep(500 * (i + 1));

                            channel.sendMessage(msg).queue(
                                    msgSuccess -> logger.debug("Sent message {} to server {}",
                                            messageIndex, subscription.guildId),
                                    msgFailure -> logger.warn("Failed to send message {} to server {}: {}",
                                            messageIndex, subscription.guildId, msgFailure.getMessage())
                            );
                        }

                        // Send summary embed with button
                        Thread.sleep(1000);
                        EmbedBuilder summaryEmbed = createContentSummaryEmbed(content);
                        channel.sendMessageEmbeds(summaryEmbed.build())
                                .setActionRow(
                                        content.url != null ?
                                                Button.link(content.url, "📖 View Official Patch Notes") :
                                                Button.link("ENTER URL",
                                                        "📖 League Patch Notes")
                                )
                                .queue(
                                        summarySuccess -> {
                                            // Mark notification as sent only after all messages are sent
                                            dbManager.markNotificationSent(subscription.guildId, patch.id);
                                            logger.info("Successfully sent full patch notification to server {} in channel {}",
                                                    subscription.guildId, subscription.channelId);
                                        },
                                        summaryFailure -> logger.warn("Failed to send summary to server {}: {}",
                                                subscription.guildId, summaryFailure.getMessage())
                                );

                        successCount++;
                    } else {
                        logger.warn("Channel {} not found or bot lacks access in server {}",
                                subscription.channelId, subscription.guildId);
                        failCount++;
                    }

                    // Rate limiting delay between servers
                    Thread.sleep(2000);

                } catch (Exception e) {
                    logger.error("Error sending notification to server {}: {}",
                            subscription.guildId, e.getMessage());
                    failCount++;
                }
            }

            logger.info("Patch notification summary: {} successful, {} failed", successCount, failCount);

        } catch (Exception e) {
            logger.error("Error sending patch notifications", e);
        }
    }

    private void sendBasicPatchNotifications(DatabaseManager.PatchInfo patch) {
        try {
            List<DatabaseManager.ServerSubscription> subscriptions = dbManager.getSubscribedServers();

            if (subscriptions.isEmpty()) {
                logger.info("No servers subscribed to patch notifications");
                return;
            }

            logger.info("Sending basic patch notifications for {} to {} servers", patch.version, subscriptions.size());

            for (DatabaseManager.ServerSubscription subscription : subscriptions) {
                try {
                    if (dbManager.wasNotificationSent(subscription.guildId, patch.id)) {
                        continue;
                    }

                    TextChannel channel = jda.getTextChannelById(subscription.channelId);

                    if (channel != null) {
                        EmbedBuilder embed = new EmbedBuilder()
                                .setTitle("🎮 New League of Legends Patch Released!")
                                .setDescription("**" + patch.title + "**")
                                .addField("📊 Version", patch.version, true)
                                .addField("📅 Release Date",
                                        patch.releaseDate.format(DateTimeFormatter.ofPattern("MMM dd, yyyy")), true)
                                .addField("📝 Summary", patch.summary != null ? patch.summary :
                                        "New patch is now live! Check the official patch notes for details.", false)
                                .setColor(Color.GREEN)
                                .setFooter("League Patch Tracker • Use /unsubscribe to stop notifications", null)
                                .setTimestamp(java.time.Instant.now());

                        channel.sendMessageEmbeds(embed.build())
                                .setActionRow(
                                        patch.url != null ?
                                                Button.link(patch.url, "📖 View Official Patch Notes") :
                                                Button.link("ENTER URL\n",
                                                        "📖 League Patch Notes")
                                )
                                .queue(
                                        success -> {
                                            dbManager.markNotificationSent(subscription.guildId, patch.id);
                                            logger.info("Sent basic patch notification to server {}", subscription.guildId);
                                        },
                                        failure -> logger.warn("Failed to send basic notification to server {}: {}",
                                                subscription.guildId, failure.getMessage())
                                );

                        Thread.sleep(1000);
                    }

                } catch (Exception e) {
                    logger.error("Error sending basic notification to server {}: {}",
                            subscription.guildId, e.getMessage());
                }
            }

        } catch (Exception e) {
            logger.error("Error sending basic patch notifications", e);
        }
    }

    private List<String> buildPatchNotesMessages(RiotApiService.PatchContent content) {
        StringBuilder patchNotes = new StringBuilder();

        // Title
        patchNotes.append("**📋 PATCH NOTES - ").append(content.version).append("**\n");
        patchNotes.append("═══════════════════════════════════\n\n");

        // Overview
        if (content.overview != null && !content.overview.trim().isEmpty()) {
            patchNotes.append("**OVERVIEW**\n");
            patchNotes.append(content.overview).append("\n\n");
        }

        // Champion Changes
        if (content.championChanges != null && !content.championChanges.isEmpty()) {
            patchNotes.append("**⚔️ CHAMPION CHANGES**\n");
            patchNotes.append("───────────────────────\n");

            int champCount = 0;
            for (RiotApiService.ChampionChange champion : content.championChanges) {
                if (champCount >= 10) break; // Limit champions to avoid spam

                patchNotes.append("\n**").append(champion.name).append("**\n");
                int changeCount = 0;
                for (String change : champion.changes) {
                    if (changeCount >= 3) break; // Limit changes per champion
                    patchNotes.append("• ").append(truncateText(change, 150)).append("\n");
                    changeCount++;
                }
                champCount++;
            }

            if (content.championChanges.size() > 10) {
                patchNotes.append("\n*... and ").append(content.championChanges.size() - 10)
                        .append(" more champions changed*\n");
            }
            patchNotes.append("\n");
        }

        // Item Changes
        if (content.itemChanges != null && !content.itemChanges.isEmpty()) {
            patchNotes.append("**🗡️ ITEM CHANGES**\n");
            patchNotes.append("───────────────────────\n");

            int itemCount = 0;
            for (RiotApiService.ItemChange item : content.itemChanges) {
                if (itemCount >= 8) break; // Limit items

                patchNotes.append("\n**").append(item.name).append("**\n");
                int changeCount = 0;
                for (String change : item.changes) {
                    if (changeCount >= 2) break; // Limit changes per item
                    patchNotes.append("• ").append(truncateText(change, 120)).append("\n");
                    changeCount++;
                }
                itemCount++;
            }

            if (content.itemChanges.size() > 8) {
                patchNotes.append("\n*... and ").append(content.itemChanges.size() - 8)
                        .append(" more items updated*\n");
            }
            patchNotes.append("\n");
        }

        // Bug Fixes (limited)
        if (content.bugFixes != null && !content.bugFixes.isEmpty()) {
            patchNotes.append("**🐛 BUG FIXES** (").append(content.bugFixes.size()).append(" total)\n");
            patchNotes.append("───────────────────────\n");

            int fixCount = 0;
            for (String fix : content.bugFixes) {
                if (fixCount >= 5) break; // Show only first 5 bug fixes
                patchNotes.append("• ").append(truncateText(fix, 120)).append("\n");
                fixCount++;
            }
            if (content.bugFixes.size() > 5) {
                patchNotes.append("• ... and ").append(content.bugFixes.size() - 5).append(" more fixes\n");
            }
        }

        return splitIntoMessages(patchNotes.toString());
    }

    private List<String> splitIntoMessages(String fullText) {
        List<String> messages = new ArrayList<>();
        String[] lines = fullText.split("\n");
        StringBuilder currentMessage = new StringBuilder();

        for (String line : lines) {
            if (currentMessage.length() + line.length() + 1 > MAX_MESSAGE_LENGTH - 100) {
                if (currentMessage.length() > 0) {
                    messages.add(currentMessage.toString().trim());
                    currentMessage = new StringBuilder();
                }
            }
            currentMessage.append(line).append("\n");
        }

        if (currentMessage.length() > 0) {
            messages.add(currentMessage.toString().trim());
        }

        // Limit messages to avoid spam (max 4 messages)
        if (messages.size() > 4) {
            messages = messages.subList(0, 4);
            messages.set(3, messages.get(3) + "\n\n*[Content truncated for notifications...]*");
        }

        return messages;
    }

    private EmbedBuilder createAnnouncementEmbed(DatabaseManager.PatchInfo patch, RiotApiService.PatchContent content) {
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("🎮 New League of Legends Patch Released!")
                .setDescription("**" + patch.title + "**")
                .addField("📊 Version", patch.version, true)
                .addField("📅 Release Date", patch.releaseDate.format(DateTimeFormatter.ofPattern("MMM dd, yyyy")), true)
                .setColor(Color.GREEN)
                .setFooter("League Patch Tracker • Use /unsubscribe to stop notifications", null)
                .setTimestamp(java.time.Instant.now());

        // Add quick stats if content is available
        if (content != null) {
            int totalChanges = 0;
            StringBuilder quickStats = new StringBuilder();

            if (content.championChanges != null && !content.championChanges.isEmpty()) {
                totalChanges += content.championChanges.size();
                quickStats.append("Champions: ").append(content.championChanges.size()).append(" | ");
            }

            if (content.itemChanges != null && !content.itemChanges.isEmpty()) {
                totalChanges += content.itemChanges.size();
                quickStats.append("Items: ").append(content.itemChanges.size()).append(" | ");
            }

            if (content.bugFixes != null && !content.bugFixes.isEmpty()) {
                quickStats.append("Bug Fixes: ").append(content.bugFixes.size());
            }

            if (quickStats.length() > 0) {
                embed.addField("⚡ Quick Stats", quickStats.toString().replaceAll(" \\| $", ""), false);
            }
        }

        return embed;
    }

    private EmbedBuilder createContentSummaryEmbed(RiotApiService.PatchContent content) {
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("📊 Patch " + content.version + " Summary")
                .setColor(Color.CYAN);

        int totalChanges = 0;
        StringBuilder stats = new StringBuilder();

        if (content.championChanges != null && !content.championChanges.isEmpty()) {
            stats.append("• **Champions Changed:** ").append(content.championChanges.size()).append("\n");
            totalChanges += content.championChanges.size();
        }

        if (content.itemChanges != null && !content.itemChanges.isEmpty()) {
            stats.append("• **Items Updated:** ").append(content.itemChanges.size()).append("\n");
            totalChanges += content.itemChanges.size();
        }

        if (content.bugFixes != null && !content.bugFixes.isEmpty()) {
            stats.append("• **Bug Fixes:** ").append(content.bugFixes.size()).append("\n");
        }

        embed.setDescription("**Total changes in this patch:** " + totalChanges + "\n\n" + stats.toString());

        // Add top changed champions
        if (content.championChanges != null && !content.championChanges.isEmpty()) {
            StringBuilder topChamps = new StringBuilder();
            int count = 0;
            for (RiotApiService.ChampionChange champ : content.championChanges) {
                if (count >= 3) break;
                topChamps.append("• ").append(champ.name).append(" (")
                        .append(champ.changes.size()).append(" changes)\n");
                count++;
            }
            if (topChamps.length() > 0) {
                embed.addField("🏆 Most Changed Champions", topChamps.toString(), true);
            }
        }

        return embed;
    }

    private String truncateText(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength - 3) + "...";
    }

    public void shutdown() {
        running = false;
        if (riotApi != null) {
            riotApi.shutdown();
        }
        logger.info("Patch notification task shutdown completed");
    }
}