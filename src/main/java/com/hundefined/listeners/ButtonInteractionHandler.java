package com.hundefined.listeners;

import com.hundefined.services.RiotApiService;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class ButtonInteractionHandler extends ListenerAdapter {
    private static final Logger logger = LoggerFactory.getLogger(ButtonInteractionHandler.class);
    private static final int MAX_MESSAGE_LENGTH = 2000;

    // Cache patch content temporarily with longer retention
    private static final ConcurrentHashMap<String, RiotApiService.PatchContent> contentCache = new ConcurrentHashMap<>();
    private static volatile String lastCachedVersion = null; // Track the most recent version

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String buttonId = event.getComponentId();
        logger.info("Button interaction received: {} from user: {}", buttonId, event.getUser().getAsTag());

        switch (buttonId) {
            case "champ_details":
                handleChampionDetails(event);
                break;
            case "item_details":
                handleItemDetails(event);
                break;
            case "bug_details":
                handleBugDetails(event);
                break;
            case "system_details":
                handleSystemDetails(event);
                break;
            default:
                logger.debug("Unknown button interaction: {}", buttonId);
                event.reply("Unknown button interaction. Please try again.").setEphemeral(true).queue();
                break;
        }
    }

    private void handleChampionDetails(ButtonInteractionEvent event) {
        logger.info("Handling champion details button click");
        event.deferReply(true).queue(); // Ephemeral reply

        try {
            RiotApiService.PatchContent content = getPatchContentFromContext(event);

            if (content == null) {
                logger.warn("No patch content found for champion details");
                event.getHook().editOriginal("❌ Could not load champion details. Please try running `/latestpatch` again to refresh the data.").queue();
                return;
            }

            if (content.championChanges == null || content.championChanges.isEmpty()) {
                logger.info("No champion changes found in content");
                event.getHook().editOriginal("No champion changes found for this patch.").queue();
                return;
            }

            logger.info("Found {} champion changes, formatting details", content.championChanges.size());
            List<String> messages = formatChampionDetails(content.championChanges);

            if (messages.isEmpty()) {
                event.getHook().editOriginal("No detailed champion changes available.").queue();
                return;
            }

            // Send the first message as edit
            event.getHook().editOriginal(messages.get(0)).queue();

            // Send additional messages if needed
            for (int i = 1; i < messages.size(); i++) {
                final String msg = messages.get(i);
                event.getHook().sendMessage(msg).setEphemeral(true).queue();
                try {
                    Thread.sleep(100); // Small delay to maintain order
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

            logger.info("Successfully sent {} champion detail messages", messages.size());

        } catch (Exception e) {
            logger.error("Error handling champion details button", e);
            event.getHook().editOriginal("❌ Error loading champion details: " + e.getMessage() + ". Please try again.").queue();
        }
    }

    private void handleItemDetails(ButtonInteractionEvent event) {
        event.deferReply(true).queue();

        try {
            RiotApiService.PatchContent content = getPatchContentFromContext(event);

            if (content == null || content.itemChanges == null || content.itemChanges.isEmpty()) {
                event.getHook().editOriginal("No item changes found for this patch.").queue();
                return;
            }

            List<String> messages = formatItemDetails(content.itemChanges);
            event.getHook().editOriginal(messages.get(0)).queue();

            for (int i = 1; i < messages.size(); i++) {
                final String msg = messages.get(i);
                event.getHook().sendMessage(msg).setEphemeral(true).queue();
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

        } catch (Exception e) {
            logger.error("Error handling item details button", e);
            event.getHook().editOriginal("Error loading item details. Please try again.").queue();
        }
    }

    private void handleBugDetails(ButtonInteractionEvent event) {
        event.deferReply(true).queue();

        try {
            RiotApiService.PatchContent content = getPatchContentFromContext(event);

            if (content == null || content.bugFixes == null || content.bugFixes.isEmpty()) {
                event.getHook().editOriginal("No bug fixes found for this patch.").queue();
                return;
            }

            List<String> messages = formatBugFixDetails(content.bugFixes);
            event.getHook().editOriginal(messages.get(0)).queue();

            for (int i = 1; i < messages.size(); i++) {
                final String msg = messages.get(i);
                event.getHook().sendMessage(msg).setEphemeral(true).queue();
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

        } catch (Exception e) {
            logger.error("Error handling bug fix details button", e);
            event.getHook().editOriginal("Error loading bug fix details. Please try again.").queue();
        }
    }

    private void handleSystemDetails(ButtonInteractionEvent event) {
        event.deferReply(true).queue();

        try {
            RiotApiService.PatchContent content = getPatchContentFromContext(event);

            if (content == null || content.systemChanges == null || content.systemChanges.isEmpty()) {
                event.getHook().editOriginal("No system changes found for this patch.").queue();
                return;
            }

            List<String> messages = formatSystemDetails(content.systemChanges);
            event.getHook().editOriginal(messages.get(0)).queue();

            for (int i = 1; i < messages.size(); i++) {
                final String msg = messages.get(i);
                event.getHook().sendMessage(msg).setEphemeral(true).queue();
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

        } catch (Exception e) {
            logger.error("Error handling system details button", e);
            event.getHook().editOriginal("Error loading system details. Please try again.").queue();
        }
    }

    private List<String> formatChampionDetails(List<RiotApiService.ChampionChange> championChanges) {
        List<String> messages = new ArrayList<>();
        StringBuilder currentMessage = new StringBuilder();

        currentMessage.append("**⚔️ DETAILED CHAMPION CHANGES**\n");
        currentMessage.append("═══════════════════════════════════════════════════════\n\n");

        for (RiotApiService.ChampionChange champion : championChanges) {
            StringBuilder championSection = new StringBuilder();

            championSection.append("**").append(champion.name).append("**\n");
            championSection.append("────────────────────────────────\n");

            if (champion.changes.isEmpty()) {
                championSection.append("• No specific changes listed\n\n");
            } else {
                for (String change : champion.changes) {
                    String cleanChange = cleanAndTruncateChange(change, 150);
                    championSection.append("• ").append(cleanChange).append("\n");
                }
                championSection.append("\n");
            }

            // Check if adding this champion would exceed message limit
            if (currentMessage.length() + championSection.length() > MAX_MESSAGE_LENGTH - 100) {
                messages.add(currentMessage.toString().trim());
                currentMessage = new StringBuilder();
                currentMessage.append("**⚔️ CHAMPION CHANGES (continued)**\n");
                currentMessage.append("═══════════════════════════════════════════════════════\n\n");
            }

            currentMessage.append(championSection);
        }

        if (currentMessage.length() > 0) {
            messages.add(currentMessage.toString().trim());
        }

        // Limit to prevent spam
        if (messages.size() > 5) {
            messages = messages.subList(0, 5);
            messages.set(4, messages.get(4) + "\n\n*[Results truncated - too many changes to display]*");
        }

        return messages;
    }

    private List<String> formatItemDetails(List<RiotApiService.ItemChange> itemChanges) {
        List<String> messages = new ArrayList<>();
        StringBuilder currentMessage = new StringBuilder();

        currentMessage.append("**🗡️ DETAILED ITEM CHANGES**\n");
        currentMessage.append("═══════════════════════════════════════════════════════\n\n");

        for (RiotApiService.ItemChange item : itemChanges) {
            StringBuilder itemSection = new StringBuilder();

            itemSection.append("**").append(item.name).append("**\n");
            itemSection.append("────────────────────────────────\n");

            if (item.changes.isEmpty()) {
                itemSection.append("• No specific changes listed\n\n");
            } else {
                for (String change : item.changes) {
                    String cleanChange = cleanAndTruncateChange(change, 120);
                    itemSection.append("• ").append(cleanChange).append("\n");
                }
                itemSection.append("\n");
            }

            // Check message length
            if (currentMessage.length() + itemSection.length() > MAX_MESSAGE_LENGTH - 100) {
                messages.add(currentMessage.toString().trim());
                currentMessage = new StringBuilder();
                currentMessage.append("**🗡️ ITEM CHANGES (continued)**\n");
                currentMessage.append("═══════════════════════════════════════════════════════\n\n");
            }

            currentMessage.append(itemSection);
        }

        if (currentMessage.length() > 0) {
            messages.add(currentMessage.toString().trim());
        }

        if (messages.size() > 3) {
            messages = messages.subList(0, 3);
            messages.set(2, messages.get(2) + "\n\n*[Results truncated]*");
        }

        return messages;
    }

    private List<String> formatBugFixDetails(List<String> bugFixes) {
        List<String> messages = new ArrayList<>();
        StringBuilder currentMessage = new StringBuilder();

        currentMessage.append("**🐛 DETAILED BUG FIXES**\n");
        currentMessage.append("═══════════════════════════════════════════════════════\n\n");

        for (int i = 0; i < Math.min(bugFixes.size(), 20); i++) {
            String bugFix = cleanAndTruncateChange(bugFixes.get(i), 200);
            String bugLine = "• " + bugFix + "\n\n";

            if (currentMessage.length() + bugLine.length() > MAX_MESSAGE_LENGTH - 100) {
                messages.add(currentMessage.toString().trim());
                currentMessage = new StringBuilder();
                currentMessage.append("**🐛 BUG FIXES (continued)**\n");
                currentMessage.append("═══════════════════════════════════════════════════════\n\n");
            }

            currentMessage.append(bugLine);
        }

        if (bugFixes.size() > 20) {
            currentMessage.append("*... and ").append(bugFixes.size() - 20).append(" more bug fixes*\n");
        }

        if (currentMessage.length() > 0) {
            messages.add(currentMessage.toString().trim());
        }

        return messages;
    }

    private List<String> formatSystemDetails(List<String> systemChanges) {
        List<String> messages = new ArrayList<>();
        StringBuilder currentMessage = new StringBuilder();

        currentMessage.append("**🔧 DETAILED SYSTEM CHANGES**\n");
        currentMessage.append("═══════════════════════════════════════════════════════\n\n");

        for (String systemChange : systemChanges) {
            String cleanChange = cleanAndTruncateChange(systemChange, 180);
            String changeLine = "• " + cleanChange + "\n\n";

            if (currentMessage.length() + changeLine.length() > MAX_MESSAGE_LENGTH - 100) {
                messages.add(currentMessage.toString().trim());
                currentMessage = new StringBuilder();
                currentMessage.append("**🔧 SYSTEM CHANGES (continued)**\n");
                currentMessage.append("═══════════════════════════════════════════════════════\n\n");
            }

            currentMessage.append(changeLine);
        }

        if (currentMessage.length() > 0) {
            messages.add(currentMessage.toString().trim());
        }

        return messages;
    }

    private String cleanAndTruncateChange(String change, int maxLength) {
        if (change == null) return "No details available";

        String cleaned = change.trim()
                .replaceAll("\\s+", " ")
                .replaceAll("[\\r\\n]+", " ");

        if (cleaned.length() <= maxLength) return cleaned;
        return cleaned.substring(0, maxLength - 3) + "...";
    }

    private RiotApiService.PatchContent getPatchContentFromContext(ButtonInteractionEvent event) {
        // First try to get from cache using the last cached version
        if (lastCachedVersion != null) {
            RiotApiService.PatchContent cached = contentCache.get(lastCachedVersion);
            if (cached != null) {
                logger.info("Using cached content for latest version: {}", lastCachedVersion);
                return cached;
            }
        }

        // Try to extract version from the message content with enhanced patterns
        String messageContent = event.getMessage().getContentRaw();
        logger.debug("Analyzing message content for patch version: {}",
                messageContent.length() > 200 ? messageContent.substring(0, 200) + "..." : messageContent);

        String foundVersion = extractVersionFromMessage(messageContent);

        // Also try to extract from embeds if available
        if (foundVersion == null && !event.getMessage().getEmbeds().isEmpty()) {
            try {
                String embedContent = event.getMessage().getEmbeds().get(0).getDescription();
                if (embedContent != null) {
                    foundVersion = extractVersionFromMessage(embedContent);
                }

                // Also check embed title if description didn't work
                if (foundVersion == null) {
                    String embedTitle = event.getMessage().getEmbeds().get(0).getTitle();
                    if (embedTitle != null) {
                        foundVersion = extractVersionFromMessage(embedTitle);
                    }
                }
            } catch (Exception e) {
                logger.debug("Error extracting version from embed: {}", e.getMessage());
            }
        }

        if (foundVersion != null) {
            logger.info("Found patch version from message: {}", foundVersion);
            RiotApiService.PatchContent cached = contentCache.get(foundVersion);
            if (cached != null) {
                return cached;
            }
        }

        // If no version found or cached content missing, try to get the latest version
        logger.info("No cached content found, attempting to fetch latest patch data...");
        try {
            RiotApiService riotApi = new RiotApiService();
            String currentVersion = riotApi.getCurrentPatchVersion();

            if (currentVersion != null) {
                logger.info("Fetching fresh content for version: {}", currentVersion);
                RiotApiService.PatchContent content = riotApi.fetchPatchContent(currentVersion);
                if (content != null) {
                    cachePatchContent(currentVersion, content);
                    return content;
                }
            }
        } catch (Exception e) {
            logger.error("Error fetching fresh patch content", e);
        }

        logger.error("Could not retrieve patch content from any source");
        return null;
    }

    private String extractVersionFromMessage(String messageContent) {
        // Enhanced patterns to extract patch version
        String[] patterns = {
                "Patch\\s+(\\d+\\.\\d+)\\s+Notes",     // "Patch 14.24 Notes"
                "PATCH NOTES - (\\d+\\.\\d+)",         // "PATCH NOTES - 14.24"
                "patch[\\s-]+(\\d+\\.\\d+)",           // "patch 14.24" or "patch-14.24"
                "(\\d+\\.\\d+)\\s+Notes",              // "14.24 Notes"
                "version\\s+(\\d+\\.\\d+)",            // "version 14.24"
                "(\\d+\\.\\d+)",                       // Just the version number
                "CHAMPION CHANGES \\((\\d+)\\)",       // Extract from champion count context
                "Patch (\\d+\\.\\d+\\d*)"              // Flexible patch format
        };

        for (String patternStr : patterns) {
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(patternStr, java.util.regex.Pattern.CASE_INSENSITIVE);
            java.util.regex.Matcher matcher = pattern.matcher(messageContent);
            if (matcher.find()) {
                String version = matcher.group(1);
                // Validate version format (should be like "14.24")
                if (version.matches("\\d+\\.\\d+")) {
                    logger.debug("Found patch version '{}' using pattern: {}", version, patternStr);
                    return version;
                }
            }
        }

        logger.warn("Could not extract version from message content");
        return null;
    }

    // Enhanced caching method with better tracking
    public static void cachePatchContent(String version, RiotApiService.PatchContent content) {
        logger.info("Caching patch content for version: {}", version);

        if (content == null) {
            logger.warn("Attempted to cache null content for version: {}", version);
            return;
        }

        // Log what we're caching
        logger.debug("Caching content - Champions: {}, Items: {}, Bug fixes: {}, System changes: {}",
                content.championChanges != null ? content.championChanges.size() : 0,
                content.itemChanges != null ? content.itemChanges.size() : 0,
                content.bugFixes != null ? content.bugFixes.size() : 0,
                content.systemChanges != null ? content.systemChanges.size() : 0);

        contentCache.put(version, content);
        lastCachedVersion = version; // Track the most recent version

        logger.debug("Cache now contains {} entries: {}", contentCache.size(), contentCache.keySet());

        // Clean up old entries if cache gets too large
        if (contentCache.size() > 5) {
            // Remove older entries but keep the most recent ones
            List<String> versions = new ArrayList<>(contentCache.keySet());
            versions.remove(version); // Don't remove the one we just added

            while (contentCache.size() > 5 && !versions.isEmpty()) {
                String oldestKey = versions.remove(0);
                contentCache.remove(oldestKey);
                logger.debug("Removed old cache entry: {}", oldestKey);
            }
        }
    }

    // Method to manually clear cache if needed
    public static void clearCache() {
        contentCache.clear();
        lastCachedVersion = null;
        logger.info("Cache cleared");
    }

    // Method to get cache status for debugging
    public static String getCacheStatus() {
        return String.format("Cache contains %d entries: %s. Latest: %s",
                contentCache.size(), contentCache.keySet(), lastCachedVersion);
    }
}