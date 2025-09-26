package com.hundefined.services;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class ItemExtractor {
    private static final Logger logger = LoggerFactory.getLogger(ItemExtractor.class);

    // Known item names for better detection
    private static final String[] KNOWN_ITEMS = {
            "Redemption", "Celestial Opposition", "Locket of the Iron Solari", "Knight's Vow",
            "Sterak's Gage", "Maw of Malmortius", "Immortal Shieldbow", "Hexdrinker",
            "Seraph's Embrace", "Morellonomicon", "Rylai's Crystal Scepter", "Void Staff",
            "Infinity Edge", "Runaan's Hurricane", "Bloodthirster", "Guardian Angel",
            "Zhonya's Hourglass", "Rabadon's Deathcap", "Lich Bane", "Nashor's Tooth",
            "Thornmail", "Randuin's Omen", "Dead Man's Plate", "Spirit Visage",
            "Force of Nature", "Adaptive Helm", "Banshee's Veil", "Edge of Night",
            "Mercurial Scimitar", "Phantom Dancer", "Statikk Shiv", "Rapid Firecannon",
            "Galeforce", "Kraken Slayer", "Eclipse", "Prowler's Claw", "Duskblade",
            "Goredrinker", "Stridebreaker", "Trinity Force", "Divine Sunderer",
            "Frostfire Gauntlet", "Turbo Chemtank", "Sunfire Aegis", "Moonstone Renewer",
            "Battlesong", "Shurelya's", "Imperial Mandate", "Staff of Flowing Water",
            "Chemtech Putrifier", "Ardent Censer", "Mikael's Blessing", "Warmog's Armor",
            "Frozen Heart", "Abyssal Mask", "Gargoyle Stoneplate", "Anathema's Chains"
    };

    public List<ItemChange> extractItemChanges(Document doc) {
        logger.info("Starting item extraction...");

        List<ItemChange> itemChanges = new ArrayList<>();

        // Try different strategies for finding items
        extractItemsSections(doc, itemChanges);

        if (itemChanges.isEmpty()) {
            logger.info("No items found in sections, trying link-based extraction...");
            extractItemsFromLinks(doc, itemChanges);
        }

        if (itemChanges.isEmpty()) {
            logger.info("No items found via links, trying text-based extraction...");
            extractItemsFromKnownNames(doc, itemChanges);
        }

        logger.info("Item extraction complete: {} items found", itemChanges.size());
        return itemChanges;
    }

    private void extractItemsSections(Document doc, List<ItemChange> itemChanges) {
        String[] selectors = {
                "h2:contains(Items), h3:contains(Items)",
                "h2:contains(Item), h3:contains(Item)",
                "[id*='item'], [class*='item']"
        };

        for (String selector : selectors) {
            Elements sections = doc.select(selector);
            if (!sections.isEmpty()) {
                logger.debug("Found {} item sections with selector: {}", sections.size(), selector);
                processItemSection(sections.first(), itemChanges);
                if (!itemChanges.isEmpty()) {
                    logger.info("Found {} items using selector: {}", itemChanges.size(), selector);
                    return;
                }
            }
        }
    }

    private void processItemSection(Element section, List<ItemChange> itemChanges) {
        Element current = section.nextElementSibling();
        int elementsChecked = 0;

        while (current != null && elementsChecked < 30 && !isNewMajorSection(current)) {
            // Look for item links or mentions
            Elements itemLinks = current.select("a[href*='item'], a[href*='how-to-play']");

            for (Element link : itemLinks) {
                String itemName = cleanText(link.text());
                if (!itemName.isEmpty() && itemName.length() > 2) {
                    ItemChange change = new ItemChange();
                    change.name = itemName;

                    // Extract changes from nearby text
                    extractItemChangesFromElement(current, change);

                    if (!change.changes.isEmpty()) {
                        itemChanges.add(change);
                        logger.debug("Found item: {} with {} changes", itemName, change.changes.size());
                    }
                }
            }

            current = current.nextElementSibling();
            elementsChecked++;
        }
    }

    private void extractItemsFromLinks(Document doc, List<ItemChange> itemChanges) {
        Elements itemLinks = doc.select("a[href*='item'], a[href*='how-to-play'], a[title*='item']");

        for (Element link : itemLinks) {
            String itemName = cleanText(link.text());
            if (!itemName.isEmpty() && itemName.length() > 2) {
                ItemChange change = new ItemChange();
                change.name = itemName;

                extractItemChangesFromElement(link.parent(), change);

                if (!change.changes.isEmpty()) {
                    // Avoid duplicates
                    boolean exists = itemChanges.stream()
                            .anyMatch(c -> c.name.equalsIgnoreCase(change.name));
                    if (!exists) {
                        itemChanges.add(change);
                        logger.debug("Found item from link: {}", itemName);
                    }
                }
            }
        }
    }

    private void extractItemsFromKnownNames(Document doc, List<ItemChange> itemChanges) {
        String fullText = doc.text().toLowerCase();

        for (String itemName : KNOWN_ITEMS) {
            if (fullText.contains(itemName.toLowerCase())) {
                Elements elements = doc.getElementsContainingText(itemName);

                for (Element element : elements) {
                    ItemChange change = new ItemChange();
                    change.name = itemName;

                    extractItemChangesFromElement(element, change);

                    if (!change.changes.isEmpty()) {
                        // Avoid duplicates
                        boolean exists = itemChanges.stream()
                                .anyMatch(c -> c.name.equalsIgnoreCase(change.name));
                        if (!exists) {
                            itemChanges.add(change);
                            logger.debug("Found item from text: {}", itemName);
                            break; // Only add once per item
                        }
                    }
                }
            }
        }
    }

    private void extractItemChangesFromElement(Element element, ItemChange change) {
        // Check current element and nearby elements for changes
        List<Element> elementsToCheck = new ArrayList<>();
        elementsToCheck.add(element);

        if (element.parent() != null) {
            elementsToCheck.addAll(element.parent().children());
        }

        Element current = element.nextElementSibling();
        for (int i = 0; i < 3 && current != null; i++) {
            elementsToCheck.add(current);
            current = current.nextElementSibling();
        }

        for (Element elem : elementsToCheck) {
            String text = cleanText(elem.text());
            if (text.length() > 10 && isItemChangeText(text)) {
                change.changes.add(text);
            }
        }
    }

    private boolean isItemChangeText(String text) {
        String lowerText = text.toLowerCase();

        // Look for stat arrows
        if (text.contains("⇒") || text.contains("→") || text.contains("▶")) {
            return true;
        }

        // Look for stat labels
        if (text.matches(".*(Damage|Cooldown|Heal|Range|Cost|CD|Health|Shield|AD|AP|Armor|MR).*:.*")) {
            return true;
        }

        // Look for number changes
        if (text.matches(".*\\d+.*⇒.*\\d+.*") || text.matches(".*from\\s+\\d+.*to\\s+\\d+.*")) {
            return true;
        }

        // Look for buff/nerf language
        if (lowerText.contains("nerf") || lowerText.contains("buff") ||
                lowerText.contains("increased") || lowerText.contains("decreased") ||
                lowerText.contains("reduced") || lowerText.contains("improved")) {
            return true;
        }

        // Look for item-specific language
        if (lowerText.contains("passive") || lowerText.contains("active") ||
                lowerText.contains("ability haste") || lowerText.contains("magic resist") ||
                lowerText.contains("attack damage") || lowerText.contains("ability power")) {
            return true;
        }

        return false;
    }

    private boolean isNewMajorSection(Element element) {
        if (element == null) return true;

        String tagName = element.tagName().toLowerCase();
        if (tagName.equals("h1") || tagName.equals("h2")) {
            String text = element.text().toLowerCase();
            return text.contains("champions") || text.contains("runes") || text.contains("bugfix") ||
                    text.contains("arena") || text.contains("aram") || text.contains("upcoming") ||
                    text.contains("related") || text.contains("system");
        }

        return false;
    }

    private String cleanText(String text) {
        if (text == null) return "";
        return text.trim()
                .replaceAll("\\s+", " ")
                .replaceAll("[\\r\\n]+", " ")
                .trim();
    }

    public static class ItemChange {
        public String name;
        public List<String> changes = new ArrayList<>();
    }
}