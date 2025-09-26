package com.hundefined.services;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChampionExtractor {
    private static final Logger logger = LoggerFactory.getLogger(ChampionExtractor.class);

    // Known champion names - comprehensive list
    private static final Set<String> CHAMPION_NAMES = new HashSet<>(Arrays.asList(
            "Aatrox", "Ahri", "Akali", "Akshan", "Alistar", "Ammu", "Anivia", "Annie", "Aphelios", "Ashe", "Aurelion Sol", "Azir",
            "Bard", "Blitzcrank", "Brand", "Braum", "Caitlyn", "Camille", "Cassiopeia", "Cho'Gath", "Corki", "Darius", "Diana",
            "Dr. Mundo", "Draven", "Ekko", "Elise", "Evelynn", "Ezreal", "Fiddlesticks", "Fiora", "Fizz", "Galio", "Gangplank",
            "Garen", "Gnar", "Gragas", "Graves", "Gwen", "Hecarim", "Heimerdinger", "Illaoi", "Irelia", "Ivern", "Janna",
            "Jarvan IV", "Jax", "Jayce", "Jhin", "Jinx", "Kai'Sa", "Kalista", "Karma", "Karthus", "Kassadin", "Katarina",
            "Kayle", "Kayn", "Kennen", "Kha'Zix", "Kindred", "Kled", "Kog'Maw", "LeBlanc", "Lee Sin", "Leona", "Lillia",
            "Lissandra", "Lucian", "Lulu", "Lux", "Malphite", "Malzahar", "Maokai", "Master Yi", "Mel", "Miss Fortune",
            "Mordekaiser", "Morgana", "Nami", "Nasus", "Nautilus", "Neeko", "Nidalee", "Nocturne", "Nunu", "Olaf",
            "Orianna", "Ornn", "Pantheon", "Poppy", "Pyke", "Qiyana", "Quinn", "Rakan", "Rammus", "Rek'Sai", "Rell",
            "Renekton", "Rengar", "Riven", "Rumble", "Ryze", "Samira", "Sejuani", "Senna", "Seraphine", "Sett", "Shaco",
            "Shen", "Shyvana", "Singed", "Sion", "Sivir", "Skarner", "Sona", "Soraka", "Swain", "Sylas", "Syndra",
            "Tahm Kench", "Taliyah", "Talon", "Taric", "Teemo", "Thresh", "Tristana", "Trundle", "Tryndamere", "Twisted Fate",
            "Twitch", "Udyr", "Urgot", "Varus", "Vayne", "Veigar", "Vel'Koz", "Vex", "Vi", "Viego", "Viktor", "Vladimir",
            "Volibear", "Warwick", "Wukong", "Xayah", "Xerath", "Xin Zhao", "Yasuo", "Yone", "Yorick", "Yuumi", "Zac", "Zed",
            "Ziggs", "Zilean", "Zoe", "Zyra"
    ));

    public List<ChampionChange> extractChampionChanges(Document doc) {
        logger.info("=== Starting Enhanced Champion Extraction ===");

        List<ChampionChange> championChanges = new ArrayList<>();

        // Strategy 1: Look for structured champion sections
        extractFromStructuredSections(doc, championChanges);

        // Strategy 2: Advanced text pattern matching with stat focus
        if (championChanges.isEmpty()) {
            logger.info("No champions found in sections, trying stat-focused extraction...");
            extractChampionsWithStatFocus(doc, championChanges);
        }

        // Strategy 3: Context-based extraction with enhanced stat detection
        if (championChanges.isEmpty()) {
            logger.info("Trying context-based extraction with stat detection...");
            extractFromContextWithStats(doc, championChanges);
        }

        logger.info("=== Champion extraction complete: {} champions found ===", championChanges.size());

        // Post-process to improve stat detection for all champions
        enhanceStatDetection(doc, championChanges);

        // Log detailed results
        for (ChampionChange change : championChanges) {
            logger.info("Champion {}: {} changes", change.name, change.changes.size());
            for (int i = 0; i < Math.min(3, change.changes.size()); i++) {
                logger.debug("  Change {}: {}", i + 1,
                        change.changes.get(i).length() > 100 ?
                                change.changes.get(i).substring(0, 100) + "..." :
                                change.changes.get(i));
            }
        }

        return championChanges;
    }

    private void extractFromStructuredSections(Document doc, List<ChampionChange> championChanges) {
        logger.debug("=== Strategy 1: Structured Sections ===");

        String[] sectionSelectors = {
                "section:has(h2:containsOwn(Champion)), section:has(h3:containsOwn(Champion))",
                "div:has(h2:containsOwn(Champion)), div:has(h3:containsOwn(Champion))",
                "h2:containsOwn(Champion), h3:containsOwn(Champion)",
                "*[class*='champion'], *[id*='champion']"
        };

        for (String selector : sectionSelectors) {
            try {
                Elements sections = doc.select(selector);
                logger.debug("Selector '{}' found {} elements", selector, sections.size());

                if (!sections.isEmpty()) {
                    for (Element section : sections) {
                        processStructuredSectionWithStats(section, championChanges);
                    }

                    if (!championChanges.isEmpty()) {
                        logger.info("Found {} champions using structured selector: {}", championChanges.size(), selector);
                        return;
                    }
                }
            } catch (Exception e) {
                logger.debug("Error with selector '{}': {}", selector, e.getMessage());
            }
        }
    }

    private void processStructuredSectionWithStats(Element section, List<ChampionChange> championChanges) {
        logger.debug("Processing structured section with enhanced stat detection");

        // Look for champion subsections or direct champion mentions
        Elements championElements = section.select("h3, h4, h5, .champion-name, [data-champion]");

        if (championElements.isEmpty()) {
            // No clear champion subsections, analyze the whole section for champions and their stats
            analyzeTextForChampionsWithStats(section, championChanges);
        } else {
            // Process each champion subsection with stat focus
            for (Element champElement : championElements) {
                String championName = identifyChampionName(champElement.text());
                if (championName != null) {
                    ChampionChange change = extractChangesForChampionWithStats(champElement, championName, section);
                    if (change != null && !change.changes.isEmpty()) {
                        addUniqueChampion(championChanges, change);
                    }
                }
            }
        }
    }

    private void extractChampionsWithStatFocus(Document doc, List<ChampionChange> championChanges) {
        logger.debug("=== Strategy 2: Stat-Focused Pattern Matching ===");

        String fullText = doc.text();

        // Enhanced patterns specifically for stat changes
        // Pattern 1: Champion name followed by stat arrows (Lee Sin Base AD: 68 → 63)
        Pattern statArrowPattern = Pattern.compile(
                "(" + buildChampionRegex() + ")\\s+" +
                        "(?:Base\\s+)?([A-Za-z\\s]+?)\\s*:?\\s*" +
                        "(\\d+(?:\\.\\d+)?)\\s*[→⇒➔⟶▶]\\s*(\\d+(?:\\.\\d+)?)",
                Pattern.CASE_INSENSITIVE | Pattern.MULTILINE
        );

        extractStatsWithPattern(statArrowPattern, fullText, championChanges, "stat arrow pattern");

        // Pattern 2: Champion ability with stat changes (Lee Sin Q - Damage: 55/80/105/130/155 → 50/75/100/125/150)
        Pattern abilityStatPattern = Pattern.compile(
                "(" + buildChampionRegex() + ")\\s+" +
                        "([QWER]|Passive)\\s*[-–—:]+\\s*" +
                        "([^\\n]+?)\\s*" +
                        "(\\d+(?:/\\d+)*(?:\\.\\d+)?)\\s*[→⇒➔⟶▶]\\s*(\\d+(?:/\\d+)*(?:\\.\\d+)?)",
                Pattern.CASE_INSENSITIVE | Pattern.MULTILINE
        );

        extractStatsWithPattern(abilityStatPattern, fullText, championChanges, "ability stat pattern");

        // Pattern 3: General stat increase/decrease mentions
        Pattern statChangePattern = Pattern.compile(
                "(" + buildChampionRegex() + ")\\s+" +
                        "([A-Za-z\\s]+?)\\s*" +
                        "(increased|decreased|reduced|improved|lowered|raised)\\s+" +
                        "(?:from\\s+)?(\\d+(?:\\.\\d+)?)\\s*(?:to\\s+)?(\\d+(?:\\.\\d+)?)",
                Pattern.CASE_INSENSITIVE | Pattern.MULTILINE
        );

        extractStatsWithPattern(statChangePattern, fullText, championChanges, "stat change pattern");
    }

    /**
     * Extract changes from paragraph text with enhanced filtering
     */
    private void extractChangesFromParagraphText(String paragraph, ChampionChange change) {
        // Special filtering for Veigar to exclude Veigar's Doom game mode content
        if (change.name.equalsIgnoreCase("Veigar")) {
            String lowerParagraph = paragraph.toLowerCase();
            if (lowerParagraph.contains("veigar's doom") ||
                    lowerParagraph.contains("veigar doom") ||
                    lowerParagraph.contains("doom bots") ||
                    lowerParagraph.contains("trial of doom")) {
                logger.debug("Filtering out Veigar Doom game mode content for Veigar");
                return; // Skip this paragraph entirely
            }
        }

        // Split by sentences and look for change patterns
        String[] sentences = paragraph.split("[\\.!?]+");

        for (String sentence : sentences) {
            String trimmed = sentence.trim();
            if (trimmed.length() < 10) continue;

            // Additional filtering for game mode content
            if (isGameModeContent(trimmed)) {
                continue;
            }

            // Look for ability patterns and stat changes
            if (isValidChampionChange(trimmed, change.name)) {
                change.changes.add(trimmed);
                logger.debug("  Added change for {}: {}", change.name,
                        trimmed.length() > 80 ? trimmed.substring(0, 80) + "..." : trimmed);

                if (change.changes.size() >= 8) break; // Limit changes per champion
            }
        }
    }

    /**
     * Check if content is related to game modes rather than champion changes
     */
    private boolean isGameModeContent(String text) {
        String lowerText = text.toLowerCase();

        // Filter out various game mode references
        return lowerText.contains("aram") ||
                lowerText.contains("arena") ||
                lowerText.contains("doom bots") ||
                lowerText.contains("trial of doom") ||
                lowerText.contains("veigar's doom") ||
                lowerText.contains("veigar doom") ||
                lowerText.contains("Veigar's Doom") ||
                lowerText.contains("teamfight tactics") ||
                lowerText.contains("tft") ||
                lowerText.contains("rotating game mode") ||
                lowerText.contains("featured game mode") ||
                lowerText.contains("brawl") ||
                lowerText.contains("urf") ||
                lowerText.contains("one for all") ||
                lowerText.contains("nexus blitz") ||
                (lowerText.contains("damage dealt") && lowerText.contains("%")) || // ARAM balance changes
                (lowerText.contains("damage taken") && lowerText.contains("%")) ||
                (lowerText.contains("healing done") && lowerText.contains("%"));
    }

    /**
     * Enhanced validation for champion changes with better reasoning detection
     */
    private boolean isValidChampionChange(String text, String championName) {
        if (text.length() < 15) return false;

        String lowerText = text.toLowerCase();
        String lowerChampName = championName.toLowerCase();

        // Ability patterns - Q, W, E, R, Passive
        if (lowerText.matches(".*\\b[qwer]\\s*[-–—:].*") ||
                lowerText.toLowerCase().contains("passive")) {
            return true;
        }

        // Base stat patterns
        if (lowerText.matches(".*base\\s+(stats?|ad|ap|health|hp|armor|mr|damage).*")) {
            return true;
        }

        // Stat arrows and changes with numbers
        if (text.contains("⇒") || text.contains("→") || text.contains("▶") ||
                lowerText.matches(".*\\d+.*⇒.*\\d+.*") ||
                lowerText.matches(".*\\d+.*→.*\\d+.*")) {
            return true;
        }

        // Stat labels with numbers
        if (lowerText.matches(".*(damage|cooldown|range|cost|health|mana|shield|heal).*:.*\\d+.*")) {
            return true;
        }

        // Champion reasoning text (developer explanations)
        if (isChampionReasoning(text, championName)) {
            return false;
        }


        // Buff/nerf language
        if (lowerText.matches(".*(buff|nerf|increas|decreas|improv|adjust|reduc|strengthen|weaken).*")) {
            return true;
        }

        return false;
    }

    /**
     * Detect champion reasoning text that explains why changes were made
     */
    private boolean isChampionReasoning(String text, String championName) {
        if (text.length() < 50) return false;

        String lowerText = text.toLowerCase();
        String lowerChampName = championName.toLowerCase();

        // Must mention the champion or use pronouns referring to them
        boolean mentionsChampion = lowerText.contains(lowerChampName) ||
                lowerText.matches(".*(she|he|they|this champion).*");

        if (!mentionsChampion) return false;

        // Look for reasoning patterns commonly used in patch notes
        boolean hasReasoningPattern = lowerText.matches(".*(" +
                // Performance indicators
                "is|has|currently|perform|statistically|" +
                // Strength/weakness indicators
                "weak|strong|powerful|domina|overpow|underpow|struggling|" +
                // Comparative language
                "too|very|quite|rather|fairly|slightly|" +
                // Context indicators
                "popular|unpopular|missing|absent|present|" +
                // Play context
                "pro play|regular play|high mmr|low mmr|coordinated|teams|solo queue|" +
                // Developer intent
                "we|our goal|hope|want|like|would|" +
                // Temporal context
                "recently|lately|now|since|after|before" +
                ").*");

        // Additional patterns for champion descriptions
        boolean hasDescriptivePattern = lowerText.matches(".*(" +
                "reasonably|little|bit|somewhat|especially|particularly|" +
                "continues to|has been|finds|makes|allows|" +
                "role|position|kit|abilities|playstyle|identity" +
                ").*");

        return hasReasoningPattern || hasDescriptivePattern;
    }

    private void extractFromContextWithStats(Document doc, List<ChampionChange> championChanges) {
        logger.debug("=== Strategy 3: Context-based with Stats ===");

        Elements textElements = doc.select("p, div, li, td, span");

        for (Element element : textElements) {
            String text = element.text();
            if (text.length() < 20) continue;

            for (String championName : CHAMPION_NAMES) {
                if (containsChampionName(text, championName)) {
                    ChampionChange change = extractContextualChangesWithStats(element, championName);
                    if (change != null && !change.changes.isEmpty()) {
                        addUniqueChampion(championChanges, change);
                    }
                }
            }

            if (championChanges.size() >= 20) break;
        }
    }

    private void enhanceStatDetection(Document doc, List<ChampionChange> championChanges) {
        logger.info("Enhancing stat detection for {} champions", championChanges.size());

        String fullDocText = doc.text();

        for (ChampionChange change : championChanges) {
            // Look for more stat changes around this champion's name
            List<String> additionalStats = findAdditionalStats(fullDocText, change.name);

            for (String stat : additionalStats) {
                if (!change.changes.contains(stat) && !isDuplicateStat(change.changes, stat)) {
                    change.changes.add(stat);
                    logger.debug("Added additional stat for {}: {}", change.name, stat);
                }
            }
        }
    }

    private List<String> findAdditionalStats(String fullText, String championName) {
        List<String> stats = new ArrayList<>();

        // Find all instances of the champion name
        Pattern championPattern = Pattern.compile(
                "(" + Pattern.quote(championName) + ")([^\\n\\.]{0,200})",
                Pattern.CASE_INSENSITIVE
        );

        Matcher matcher = championPattern.matcher(fullText);

        while (matcher.find()) {
            String context = matcher.group(2);

            // Look for stat patterns in the context
            List<String> contextStats = extractStatsFromText(context, championName);
            stats.addAll(contextStats);
        }

        return stats;
    }

    private List<String> extractStatsFromText(String text, String championName) {
        List<String> stats = new ArrayList<>();

        // Pattern for stat arrows
        Pattern arrowPattern = Pattern.compile(
                "([A-Za-z\\s]+?)\\s*:?\\s*(\\d+(?:\\.\\d+|/\\d+)*)\\s*[→⇒➔⟶▶]\\s*(\\d+(?:\\.\\d+|/\\d+)*)",
                Pattern.CASE_INSENSITIVE
        );

        Matcher matcher = arrowPattern.matcher(text);
        while (matcher.find()) {
            String statName = matcher.group(1).trim();
            String oldValue = matcher.group(2);
            String newValue = matcher.group(3);

            if (isValidStatName(statName)) {
                stats.add(statName + ": " + oldValue + " → " + newValue);
            }
        }

        // Pattern for ability stats
        Pattern abilityPattern = Pattern.compile(
                "([QWER]|Passive)\\s*[-–—:]+\\s*([^\\n]+?)\\s*(\\d+(?:/\\d+)*(?:\\.\\d+)?)\\s*[→⇒➔⟶▶]\\s*(\\d+(?:/\\d+)*(?:\\.\\d+)?)",
                Pattern.CASE_INSENSITIVE
        );

        matcher = abilityPattern.matcher(text);
        while (matcher.find()) {
            String ability = matcher.group(1);
            String description = matcher.group(2).trim();
            String oldValue = matcher.group(3);
            String newValue = matcher.group(4);

            if (description.length() < 50) { // Keep it concise
                stats.add(ability + " - " + description + ": " + oldValue + " → " + newValue);
            } else {
                stats.add(ability + ": " + oldValue + " → " + newValue);
            }
        }

        return stats;
    }

    private boolean isValidStatName(String statName) {
        String lower = statName.toLowerCase();
        return lower.contains("damage") || lower.contains("health") || lower.contains("mana") ||
                lower.contains("cooldown") || lower.contains("range") || lower.contains("armor") ||
                lower.contains("mr") || lower.contains("ad") || lower.contains("ap") ||
                lower.contains("base") || lower.contains("bonus") || lower.contains("speed") ||
                lower.contains("duration") || lower.contains("shield") || lower.contains("heal");
    }

    private boolean isDuplicateStat(List<String> existingChanges, String newStat) {
        for (String existing : existingChanges) {
            if (existing.contains(newStat) || newStat.contains(existing)) {
                return true;
            }
        }
        return false;
    }

    private ChampionChange extractChangesForChampionWithStats(Element startElement, String championName, Element parentSection) {
        ChampionChange change = new ChampionChange();
        change.name = championName;

        // Get a larger context around the champion
        StringBuilder contextText = new StringBuilder();

        // Add text from the start element and nearby elements
        Element current = startElement;
        int elementCount = 0;

        while (current != null && elementCount < 15) {
            String text = current.text();
            contextText.append(text).append(" ");

            current = current.nextElementSibling();
            if (current != null && isNewChampionOrSection(current)) {
                break;
            }
            elementCount++;
        }

        // Extract stats from the collected context
        String fullContext = contextText.toString();
        List<String> statChanges = extractStatsFromText(fullContext, championName);
        change.changes.addAll(statChanges);

        // Also look for descriptive changes
        List<String> descriptiveChanges = parseDescriptiveChanges(fullContext, championName);
        for (String desc : descriptiveChanges) {
            if (!change.changes.contains(desc)) {
                change.changes.add(desc);
            }
        }

        return change.changes.isEmpty() ? null : change;
    }

    private ChampionChange extractContextualChangesWithStats(Element element, String championName) {
        ChampionChange change = new ChampionChange();
        change.name = championName;

        // Get extended context
        List<Element> elementsToCheck = new ArrayList<>();
        elementsToCheck.add(element);

        // Add parent and siblings
        if (element.parent() != null) {
            elementsToCheck.add(element.parent());
            Elements siblings = element.parent().children();
            elementsToCheck.addAll(siblings);
        }

        // Build context text
        StringBuilder contextText = new StringBuilder();
        for (Element elem : elementsToCheck) {
            contextText.append(elem.text()).append(" ");
        }

        String fullContext = contextText.toString();
        List<String> statChanges = extractStatsFromText(fullContext, championName);
        change.changes.addAll(statChanges);

        // Add descriptive changes if no stats found
        if (change.changes.isEmpty()) {
            List<String> descriptiveChanges = parseDescriptiveChanges(fullContext, championName);
            change.changes.addAll(descriptiveChanges);
        }

        return change.changes.isEmpty() ? null : change;
    }

    private void extractStatsWithPattern(Pattern pattern, String text, List<ChampionChange> championChanges, String patternName) {
        Matcher matcher = pattern.matcher(text);
        Set<String> foundChampions = new HashSet<>();

        while (matcher.find() && championChanges.size() < 15) {
            String championName = matcher.group(1).trim();
            String normalizedName = normalizeChampionName(championName);

            if (normalizedName != null && !foundChampions.contains(normalizedName)) {
                foundChampions.add(normalizedName);

                ChampionChange change = new ChampionChange();
                change.name = normalizedName;

                // Build detailed stat change description
                StringBuilder statChange = new StringBuilder();

                try {
                    if (matcher.groupCount() >= 4) {
                        String statName = matcher.group(2) != null ? matcher.group(2).trim() : "";
                        String oldValue = matcher.group(3) != null ? matcher.group(3).trim() : "";
                        String newValue = matcher.group(4) != null ? matcher.group(4).trim() : "";

                        if (!statName.isEmpty() && !oldValue.isEmpty() && !newValue.isEmpty()) {
                            statChange.append(statName).append(": ").append(oldValue).append(" → ").append(newValue);
                        }
                    }

                    // If we couldn't build a stat change, use the full match
                    if (statChange.length() == 0) {
                        statChange.append(matcher.group(0).trim());
                    }
                } catch (Exception e) {
                    statChange.append(matcher.group(0).trim());
                }

                String changeText = statChange.toString().trim();
                if (!changeText.isEmpty()) {
                    change.changes.add(changeText);
                    championChanges.add(change);
                    logger.debug("Found champion {} using {}: {}", normalizedName, patternName, changeText);
                }
            }
        }
    }

    private void analyzeTextForChampionsWithStats(Element section, List<ChampionChange> championChanges) {
        String sectionText = section.text();

        for (String championName : CHAMPION_NAMES) {
            if (containsChampionName(sectionText, championName)) {
                ChampionChange change = new ChampionChange();
                change.name = championName;

                // Extract stat changes from this section
                List<String> statChanges = extractStatsFromText(sectionText, championName);
                change.changes.addAll(statChanges);

                // If no stats found, get descriptive changes
                if (change.changes.isEmpty()) {
                    List<String> descriptiveChanges = parseDescriptiveChanges(sectionText, championName);
                    change.changes.addAll(descriptiveChanges);
                }

                if (!change.changes.isEmpty()) {
                    addUniqueChampion(championChanges, change);
                }
            }
        }
    }

    private List<String> parseDescriptiveChanges(String text, String championName) {
        List<String> changes = new ArrayList<>();

        String[] segments = text.split("[.!?\\n]");

        for (String segment : segments) {
            String trimmed = segment.trim();
            if (trimmed.length() < 15) continue;

            // Only include segments with actual change information
            if (containsChangeIndicators(trimmed) && !isChampionReasoning(trimmed, championName)) {
                String cleaned = cleanText(trimmed);
                if (!cleaned.isEmpty() && !changes.contains(cleaned)) {
                    changes.add(cleaned);
                }
            }
        }

        return changes;
    }

    private boolean containsChangeIndicators(String text) {
        String lowerText = text.toLowerCase();

        // Look for stat arrows or changes
        if (text.contains("→") || text.contains("⇒") || text.contains("▶")) {
            return true;
        }

        // Look for ability indicators
        if (lowerText.matches(".*\\b[qwer]\\b.*[-–—:].*")) {
            return true;
        }

        // Look for explicit change language
        if (lowerText.matches(".*(buff|nerf|increas|decreas|improv|adjust|reduc).*")) {
            return true;
        }

        // Look for stat keywords
        if (lowerText.matches(".*(damage|heal|shield|cooldown|range|mana|cost|health|armor).*\\d+.*")) {
            return true;
        }

        return false;
    }

    // Helper methods (keeping existing ones)
    private String buildChampionRegex() {
        StringBuilder regex = new StringBuilder("(?:");
        boolean first = true;
        for (String champion : CHAMPION_NAMES) {
            if (!first) regex.append("|");
            String escaped = Pattern.quote(champion).replace("\\ ", "\\s+").replace("\\'", "['\u2019]?");
            regex.append(escaped);
            first = false;
        }
        regex.append(")");
        return regex.toString();
    }

    private boolean containsChampionName(String text, String championName) {
        if (text == null || championName == null) return false;

        // Build a regex like \bSona\b to only match "Sona" as a word, not "Sonic"
        String regex = "\\b" + Pattern.quote(championName) + "\\b";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(text);

        return matcher.find();
    }


    private String identifyChampionName(String text) {
        for (String championName : CHAMPION_NAMES) {
            if (containsChampionName(text, championName)) {
                return championName;
            }
        }
        return null;
    }

    private String normalizeChampionName(String name) {
        for (String championName : CHAMPION_NAMES) {
            if (championName.equalsIgnoreCase(name) ||
                    championName.toLowerCase().contains(name.toLowerCase()) ||
                    name.toLowerCase().contains(championName.toLowerCase())) {
                return championName;
            }
        }
        return null;
    }

    private boolean isNewChampionOrSection(Element element) {
        if (element == null) return false;

        String tagName = element.tagName().toLowerCase();
        String text = element.text().toLowerCase();

        if (tagName.matches("h[2-6]")) {
            for (String champion : CHAMPION_NAMES) {
                if (text.contains(champion.toLowerCase())) {
                    return true;
                }
            }

            if (text.contains("item") || text.contains("system") || text.contains("bug") || text.contains("jungle")) {
                return true;
            }
        }

        return false;
    }

    private void addUniqueChampion(List<ChampionChange> championChanges, ChampionChange newChange) {
        Optional<ChampionChange> existing = championChanges.stream()
                .filter(c -> c.name.equalsIgnoreCase(newChange.name))
                .findFirst();

        if (existing.isPresent()) {
            for (String change : newChange.changes) {
                if (!existing.get().changes.contains(change)) {
                    existing.get().changes.add(change);
                }
            }
        } else {
            championChanges.add(newChange);
        }
    }

    private String cleanText(String text) {
        if (text == null) return "";
        return text.trim()
                .replaceAll("\\s+", " ")
                .replaceAll("[\\r\\n]+", " ")
                .trim();
    }

    public static class ChampionChange {
        public String name;
        public List<String> changes = new ArrayList<>();
    }


}