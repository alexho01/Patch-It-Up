package com.hundefined.Commands;

import com.hundefined.Database.DatabaseManager;
import com.hundefined.services.RiotApiService;
import com.hundefined.listeners.ButtonInteractionHandler;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class LatestPatchCommand implements Command {
    private static final Logger logger = LoggerFactory.getLogger(LatestPatchCommand.class);
    private static final int MAX_MESSAGE_LENGTH = 2000;

    private final DatabaseManager dbManager;
    private final RiotApiService riotApi;

    public LatestPatchCommand() {
        this.dbManager = DatabaseManager.getInstance();
        this.riotApi = new RiotApiService();
    }

    @Override
    public String getName() {
        return "latestpatch";
    }

    @Override
    public String getDescription() {
        return "Shows information about the latest League of Legends patch";
    }

    @Override
    public void executeSlash(SlashCommandInteractionEvent event) {
        // Defer reply immediately to prevent timeout
        event.deferReply().queue();
        logger.info("LatestPatchCommand executed by {}", event.getUser().getAsTag());

        try {
            String patchVersion = riotApi.getCurrentPatchVersion();

            if (patchVersion == null) {
                event.getHook().editOriginal("❌ Unable to fetch the latest patch information. Please try again later.").queue();
                return;
            }

            logger.info("Retrieved current patch version: {}", patchVersion);

            // Fetch patch content
            RiotApiService.PatchContent content = riotApi.fetchPatchContent(patchVersion);

            if (content != null && hasContent(content)) {
                sendCompactPatchSummary(event, content);
            } else {
                sendBasicPatchInfo(event, patchVersion);
            }

        } catch (Exception e) {
            logger.error("Error executing latest patch command", e);
            event.getHook().editOriginal("❌ An error occurred while fetching patch information. Please try again later.").queue();
        }
    }

    private String preprocessPatchNotes(String patchNotes) {
        if (patchNotes == null) return "";

        // Look for exact case-sensitive "Runes"
        int idx = patchNotes.indexOf("Runes");
        if (idx != -1) {
            // Keep everything from the start up to the end of "Runes"
            return patchNotes.substring(0, idx + "Runes".length());
        }
        return patchNotes; // fallback if no "Runes" section is found
    }


    private void sendCompactPatchSummary(SlashCommandInteractionEvent event, RiotApiService.PatchContent content) {
        try {
            StringBuilder message = new StringBuilder();

            // Title
            message.append("**🎮 ").append(content.title != null ? content.title : "Patch " + content.version + " Notes").append("**\n");
            message.append("═══════════════════════════════════════════════════════════════════════\n\n");

            // Champion Changes - COMPACT FORMAT with enhanced classification
            if (content.championChanges != null && !content.championChanges.isEmpty()) {
                message.append("**⚔️ CHAMPION CHANGES (").append(content.championChanges.size()).append(")**\n");
                message.append("─────────────────────────────────────────────────────────────────────\n");

                List<String> buffedChamps = new ArrayList<>();
                List<String> nerfedChamps = new ArrayList<>();
                List<String> adjustedChamps = new ArrayList<>();

                for (RiotApiService.ChampionChange champion : content.championChanges) {
                    String changeType = determineChangeType(champion);
                    String champName = champion.name;

                    switch (changeType) {
                        case "BUFF":
                            buffedChamps.add("📈 " + champName);
                            break;
                        case "NERF":
                            nerfedChamps.add("📉 " + champName);
                            break;
                        default:
                            adjustedChamps.add("⚖️ " + champName);
                            break;
                    }
                }

                // Display categorized champions
                if (!buffedChamps.isEmpty()) {
                    message.append("**BUFFS:** ").append(String.join(", ", buffedChamps)).append("\n\n");
                }
                if (!nerfedChamps.isEmpty()) {
                    message.append("**NERFS:** ").append(String.join(", ", nerfedChamps)).append("\n\n");
                }
                if (!adjustedChamps.isEmpty()) {
                    message.append("**ADJUSTMENTS:** ").append(String.join(", ", adjustedChamps)).append("\n\n");
                }
            }

            // Item Changes - COMPACT FORMAT
            if (content.itemChanges != null && !content.itemChanges.isEmpty()) {
                message.append("**🗡️ ITEM CHANGES (").append(content.itemChanges.size()).append(")**\n");
                message.append("─────────────────────────────────────────────────────────────────────\n");

                List<String> itemNames = new ArrayList<>();
                for (RiotApiService.ItemChange item : content.itemChanges) {
                    itemNames.add("• " + item.name);
                    if (itemNames.size() >= 10) {
                        itemNames.add("• ... and " + (content.itemChanges.size() - 10) + " more items");
                        break;
                    }
                }
                message.append(String.join(", ", itemNames)).append("\n\n");
            }

            // Bug Fixes - COUNT ONLY
            if (content.bugFixes != null && !content.bugFixes.isEmpty()) {
                message.append("**🐛 BUG FIXES:** ").append(content.bugFixes.size()).append(" issues resolved\n\n");
            }

            // System Changes - COUNT ONLY
            if (content.systemChanges != null && !content.systemChanges.isEmpty()) {
                message.append("**🔧 SYSTEM CHANGES:** ").append(content.systemChanges.size()).append(" gameplay updates\n\n");
            }

            message.append("📝 *Use the buttons below to see detailed changes*");

            // Send main message
            event.getHook().editOriginal(message.toString()).queue();

            // Cache the content for button interactions
            ButtonInteractionHandler.cachePatchContent(content.version, content);

            // Send detailed embed with buttons
            EmbedBuilder detailEmbed = createDetailedEmbed(content);

            List<Button> buttons = new ArrayList<>();
            if (content.championChanges != null && !content.championChanges.isEmpty()) {
                buttons.add(Button.primary("champ_details", "Champion Details"));
            }
            if (content.itemChanges != null && !content.itemChanges.isEmpty()) {
                buttons.add(Button.secondary("item_details", "Item Details"));
            }
            if (content.bugFixes != null && !content.bugFixes.isEmpty()) {
                buttons.add(Button.secondary("bug_details", "Bug Fixes"));
            }
            if (content.systemChanges != null && !content.systemChanges.isEmpty()) {
                buttons.add(Button.secondary("system_details", "System Changes"));
            }
            if (content.url != null) {
                buttons.add(Button.link(content.url, "📖 Full Patch Notes"));
            }

            if (!buttons.isEmpty()) {
                // Discord limits to 5 buttons per action row
                if (buttons.size() <= 5) {
                    event.getHook().sendMessageEmbeds(detailEmbed.build())
                            .setActionRow(buttons)
                            .queue();
                } else {
                    // Split into multiple rows if needed
                    event.getHook().sendMessageEmbeds(detailEmbed.build())
                            .setActionRow(buttons.subList(0, 5))
                            .queue();
                }
            } else {
                event.getHook().sendMessageEmbeds(detailEmbed.build()).queue();
            }

        } catch (Exception e) {
            logger.error("Error sending compact patch summary", e);
            event.getHook().editOriginal("❌ Error formatting patch notes. Please try again.").queue();
        }
    }

    /**
     * Enhanced buff/nerf classification with comprehensive stat analysis
     */
    private String determineChangeType(RiotApiService.ChampionChange champion) {
        if (champion.changes.isEmpty()) return "ADJUSTMENT";

        String allChanges = String.join(" ", champion.changes).toLowerCase();

        // Detailed analysis for better classification
        int buffScore = 0;
        int nerfScore = 0;

        // 1. Look for explicit buff/nerf language (highest priority)
        if (allChanges.contains("buff")) buffScore += 3;
        if (allChanges.contains("nerf")) nerfScore += 3;

        // 2. Analyze stat arrows for direction of change
        Pattern arrowPattern = Pattern.compile("(\\d+(?:\\.\\d+|/\\d+)*)\\s*[→⇒➔⟶▶]\\s*(\\d+(?:\\.\\d+|/\\d+)*)");
        java.util.regex.Matcher matcher = arrowPattern.matcher(allChanges);

        while (matcher.find()) {
            String oldValueStr = matcher.group(1);
            String newValueStr = matcher.group(2);

            try {
                // Handle simple numbers (like "68 → 63")
                if (oldValueStr.matches("\\d+(?:\\.\\d+)?") && newValueStr.matches("\\d+(?:\\.\\d+)?")) {
                    double oldValue = Double.parseDouble(oldValueStr);
                    double newValue = Double.parseDouble(newValueStr);

                    if (newValue > oldValue) {
                        buffScore += 2;
                    } else if (newValue < oldValue) {
                        nerfScore += 2;
                    }
                }
                // Handle ability scalings (like "55/80/105/130/155 → 50/75/100/125/150")
                else if (oldValueStr.contains("/") && newValueStr.contains("/")) {
                    String[] oldValues = oldValueStr.split("/");
                    String[] newValues = newValueStr.split("/");

                    if (oldValues.length == newValues.length) {
                        int increases = 0;
                        int decreases = 0;

                        for (int i = 0; i < oldValues.length; i++) {
                            try {
                                double oldVal = Double.parseDouble(oldValues[i].trim());
                                double newVal = Double.parseDouble(newValues[i].trim());

                                if (newVal > oldVal) increases++;
                                else if (newVal < oldVal) decreases++;
                            } catch (NumberFormatException e) {
                                // Skip non-numeric values
                            }
                        }

                        if (increases > decreases) {
                            buffScore += 2;
                        } else if (decreases > increases) {
                            nerfScore += 2;
                        }
                    }
                }
            } catch (NumberFormatException e) {
                // Skip if we can't parse the numbers
            }
        }

        // 3. Look for positive/negative change language
        String[] positiveWords = {"increase", "increased", "improve", "improved", "enhance", "enhanced",
                "boost", "boosted", "strengthen", "strengthened", "raise", "raised", "higher"};
        String[] negativeWords = {"decrease", "decreased", "reduce", "reduced", "lower", "lowered",
                "weaken", "weakened", "nerf", "nerfed", "diminish", "diminished"};

        for (String word : positiveWords) {
            if (allChanges.contains(word)) buffScore += 1;
        }

        for (String word : negativeWords) {
            if (allChanges.contains(word)) nerfScore += 1;
        }

        // 4. Context-based analysis for specific stat types
        // Damage increases are usually buffs
        if (allChanges.matches(".*damage.*\\d+.*[→⇒].*\\d+.*")) {
            Pattern damagePattern = Pattern.compile("damage.*?(\\d+(?:\\.\\d+)?)\\s*[→⇒]\\s*(\\d+(?:\\.\\d+)?)");
            java.util.regex.Matcher damageMatcher = damagePattern.matcher(allChanges);
            if (damageMatcher.find()) {
                try {
                    double oldDmg = Double.parseDouble(damageMatcher.group(1));
                    double newDmg = Double.parseDouble(damageMatcher.group(2));

                    if (newDmg > oldDmg) {
                        buffScore += 2; // Damage increase is usually a buff
                    } else if (newDmg < oldDmg) {
                        nerfScore += 2; // Damage decrease is usually a nerf
                    }
                } catch (NumberFormatException e) {
                    // Skip if parsing fails
                }
            }
        }

        // 5. Cooldown analysis (cooldown decreases are buffs, increases are nerfs)
        if (allChanges.matches(".*cooldown.*\\d+.*[→⇒].*\\d+.*")) {
            Pattern cooldownPattern = Pattern.compile("cooldown.*?(\\d+(?:\\.\\d+)?)\\s*[→⇒]\\s*(\\d+(?:\\.\\d+)?)");
            java.util.regex.Matcher cooldownMatcher = cooldownPattern.matcher(allChanges);
            if (cooldownMatcher.find()) {
                try {
                    double oldCd = Double.parseDouble(cooldownMatcher.group(1));
                    double newCd = Double.parseDouble(cooldownMatcher.group(2));

                    if (newCd < oldCd) {
                        buffScore += 2; // Cooldown reduction is a buff
                    } else if (newCd > oldCd) {
                        nerfScore += 2; // Cooldown increase is a nerf
                    }
                } catch (NumberFormatException e) {
                    // Skip if parsing fails
                }
            }
        }

        // 6. Range analysis (range increases are usually buffs)
        if (allChanges.matches(".*range.*\\d+.*[→⇒].*\\d+.*")) {
            Pattern rangePattern = Pattern.compile("range.*?(\\d+(?:\\.\\d+)?)\\s*[→⇒]\\s*(\\d+(?:\\.\\d+)?)");
            java.util.regex.Matcher rangeMatcher = rangePattern.matcher(allChanges);
            if (rangeMatcher.find()) {
                try {
                    double oldRange = Double.parseDouble(rangeMatcher.group(1));
                    double newRange = Double.parseDouble(rangeMatcher.group(2));

                    if (newRange > oldRange) {
                        buffScore += 1; // Range increase is usually a buff
                    } else if (newRange < oldRange) {
                        nerfScore += 1; // Range decrease is usually a nerf
                    }
                } catch (NumberFormatException e) {
                    // Skip if parsing fails
                }
            }
        }

        // 7. Base stat analysis (health, AD, AP, armor, MR increases are usually buffs)
        Pattern baseStatPattern = Pattern.compile("(base\\s+(?:ad|ap|health|hp|armor|mr|magic\\s+resist)).*?(\\d+(?:\\.\\d+)?)\\s*[→⇒]\\s*(\\d+(?:\\.\\d+)?)");
        java.util.regex.Matcher baseStatMatcher = baseStatPattern.matcher(allChanges);
        while (baseStatMatcher.find()) {
            try {
                double oldStat = Double.parseDouble(baseStatMatcher.group(2));
                double newStat = Double.parseDouble(baseStatMatcher.group(3));

                if (newStat > oldStat) {
                    buffScore += 2; // Base stat increase is usually a buff
                } else if (newStat < oldStat) {
                    nerfScore += 2; // Base stat decrease is usually a nerf
                }
            } catch (NumberFormatException e) {
                // Skip if parsing fails
            }
        }

        // 8. Cost analysis (mana cost decreases are buffs, increases are nerfs)
        if (allChanges.matches(".*(mana\\s+)?cost.*\\d+.*[→⇒].*\\d+.*")) {
            Pattern costPattern = Pattern.compile("(?:mana\\s+)?cost.*?(\\d+(?:\\.\\d+)?)\\s*[→⇒]\\s*(\\d+(?:\\.\\d+)?)");
            java.util.regex.Matcher costMatcher = costPattern.matcher(allChanges);
            if (costMatcher.find()) {
                try {
                    double oldCost = Double.parseDouble(costMatcher.group(1));
                    double newCost = Double.parseDouble(costMatcher.group(2));

                    if (newCost < oldCost) {
                        buffScore += 1; // Cost reduction is a buff
                    } else if (newCost > oldCost) {
                        nerfScore += 1; // Cost increase is a nerf
                    }
                } catch (NumberFormatException e) {
                    // Skip if parsing fails
                }
            }
        }

        // 9. Duration analysis for beneficial effects (shield, heal, buff durations)
        if (allChanges.matches(".*(shield|heal|duration).*\\d+.*[→⇒].*\\d+.*") &&
                !allChanges.contains("cooldown")) { // Exclude cooldown durations
            Pattern durationPattern = Pattern.compile("(?:shield|heal|duration).*?(\\d+(?:\\.\\d+)?)\\s*[→⇒]\\s*(\\d+(?:\\.\\d+)?)");
            java.util.regex.Matcher durationMatcher = durationPattern.matcher(allChanges);
            if (durationMatcher.find()) {
                try {
                    double oldDuration = Double.parseDouble(durationMatcher.group(1));
                    double newDuration = Double.parseDouble(durationMatcher.group(2));

                    if (newDuration > oldDuration) {
                        buffScore += 1; // Longer beneficial effect is usually a buff
                    } else if (newDuration < oldDuration) {
                        nerfScore += 1; // Shorter beneficial effect is usually a nerf
                    }
                } catch (NumberFormatException e) {
                    // Skip if parsing fails
                }
            }
        }

        // 10. Champion-specific context analysis
        // Look for champion reasoning text that might indicate intent
        String championReasoning = getChampionReasoningText(champion);
        if (!championReasoning.isEmpty()) {
            String reasoning = championReasoning.toLowerCase();

            // Positive reasoning indicators
            if (reasoning.matches(".*(weak|underperform|struggling|needs.*help|buff|strengthen).*")) {
                buffScore += 2;
            }
            // Negative reasoning indicators
            if (reasoning.matches(".*(strong|overperform|dominat|nerf|too.*powerful|oppressive).*")) {
                nerfScore += 2;
            }
            // Power-neutral indicators
            if (reasoning.matches(".*(adjust|rework|clarity|quality.*life|neutral).*")) {
                // Don't add to either score - these are adjustments
            }
        }

        // 11. Final scoring with thresholds
        int scoreDifference = buffScore - nerfScore;

        // Use a threshold system for more accurate classification
        if (scoreDifference >= 3) {
            return "BUFF";
        } else if (scoreDifference <= -3) {
            return "NERF";
        } else if (Math.abs(scoreDifference) <= 2 && (buffScore > 0 || nerfScore > 0)) {
            return "ADJUSTMENT"; // Minor changes or mixed changes
        } else {
            return "ADJUSTMENT"; // No clear direction
        }
    }

    /**
     * Extract champion reasoning text (usually the first change that explains why changes were made)
     */
    private String getChampionReasoningText(RiotApiService.ChampionChange champion) {
        if (champion.changes.isEmpty()) return "";

        // Filter out Veigar Doom game mode references
        if (champion.name.equalsIgnoreCase("Veigar")) {
            for (String change : champion.changes) {
                if (change.toLowerCase().contains("veigar's doom") ||
                        change.toLowerCase().contains("veigar doom") ||
                        change.toLowerCase().contains("doom bots") ||
                        change.toLowerCase().contains("trial of doom")) {
                    continue; // Skip game mode related content
                }

                // Return the first non-game mode change as reasoning
                if (change.length() > 50 && containsReasoningIndicators(change)) {
                    return change.toLowerCase();
                }
            }
        }

        // For other champions, look for the reasoning text
        for (String change : champion.changes) {
            String lowerChange = change.toLowerCase();

            // Check if it looks like reasoning text (longer, contains explanation words)
            if (containsReasoningIndicators(change)) {
                return lowerChange;
            }
        }

        return "";
    }

    /**
     * Check if a change text contains reasoning indicators
     */
    private boolean containsReasoningIndicators(String change) {
        if (change.length() < 50) return false;

        String lowerChange = change.toLowerCase();

        // Look for reasoning patterns commonly used in patch notes
        return lowerChange.matches(".*(is|has|we|this|currently|perform|weak|strong|domina|overpow|underpow|struggling|too|very|quite|rather|fairly|popular|unpopular|missing|absent|statistically|pro play|regular play|high mmr|low mmr|coordinated|teams).*");
    }

    private EmbedBuilder createDetailedEmbed(RiotApiService.PatchContent content) {
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("📊 Patch " + content.version + " Summary")
                .setColor(Color.CYAN)
                .setFooter("League Patch Tracker • Use buttons above for detailed changes", null)
                .setTimestamp(java.time.Instant.now());

        // Statistics
        StringBuilder stats = new StringBuilder();

        if (content.championChanges != null && !content.championChanges.isEmpty()) {
            stats.append("**Champions:** ").append(content.championChanges.size()).append("\n");
        }
        if (content.itemChanges != null && !content.itemChanges.isEmpty()) {
            stats.append("**Items:** ").append(content.itemChanges.size()).append("\n");
        }
        if (content.bugFixes != null && !content.bugFixes.isEmpty()) {
            stats.append("**Bug Fixes:** ").append(content.bugFixes.size()).append("\n");
        }
        if (content.systemChanges != null && !content.systemChanges.isEmpty()) {
            stats.append("**System Changes:** ").append(content.systemChanges.size()).append("\n");
        }

        if (stats.length() > 0) {
            embed.addField("📈 Changes Overview", stats.toString(), false);
        }

        // Overview if available
        if (content.overview != null && !content.overview.trim().isEmpty()) {
            String shortOverview = content.overview.length() > 500 ?
                    content.overview.substring(0, 497) + "..." : content.overview;
            embed.addField("📋 Patch Overview", shortOverview, false);
        }

        return embed;
    }

    private void sendBasicPatchInfo(SlashCommandInteractionEvent event, String patchVersion) {
        String message = String.format(
                "**🎮 League of Legends Patch %s**\n\n" +
                        "Patch %s is the current version!\n\n" +
                        "Unfortunately, detailed patch notes couldn't be loaded at this time.\n" +
                        "This might be because:\n" +
                        "• The patch notes website structure has changed\n" +
                        "• The patch is very new and content isn't fully available\n" +
                        "• Network connectivity issues\n\n" +
                        "You can view the official patch notes using the button below.",
                patchVersion, patchVersion
        );

        String officialUrl = "Enter Official Patch Notes URL Pattern Here" +
                patchVersion.replace(".", "-") + "-notes/";

        event.getHook().editOriginal(message)
                .setActionRow(Button.link(officialUrl, "📖 View Official Patch Notes"))
                .queue();
    }

    private boolean hasContent(RiotApiService.PatchContent content) {
        return content != null &&
                ((content.championChanges != null && !content.championChanges.isEmpty()) ||
                        (content.itemChanges != null && !content.itemChanges.isEmpty()) ||
                        (content.bugFixes != null && !content.bugFixes.isEmpty()) ||
                        (content.overview != null && !content.overview.trim().isEmpty()));
    }
}