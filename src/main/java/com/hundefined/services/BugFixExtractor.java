package com.hundefined.services;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class BugFixExtractor {
    private static final Logger logger = LoggerFactory.getLogger(BugFixExtractor.class);

    public List<String> extractBugFixes(Document doc) {
        logger.info("Starting bug fix extraction...");

        List<String> bugFixes = new ArrayList<>();

        // Strategy 1: Look for dedicated bug fix sections
        extractFromBugSections(doc, bugFixes);

        if (bugFixes.isEmpty()) {
            logger.info("No bug fixes found in dedicated sections, trying fallback extraction...");
            extractFromTextMatches(doc, bugFixes);
        }

        logger.info("Bug fix extraction complete: {} bug fixes found", bugFixes.size());
        return bugFixes;
    }

    private void extractFromBugSections(Document doc, List<String> bugFixes) {
        String[] selectors = {
                "h2:contains(Bug), h3:contains(Bug), h2:contains(QoL), h3:contains(QoL)",
                "h2:contains(Fix), h3:contains(Fix), h2:contains(Fixes), h3:contains(Fixes)",
                "h2:contains(Bugfix), h3:contains(Bugfix), h2:contains(Bugfixes), h3:contains(Bugfixes)",
                "[id*='bug'], [class*='bug'], [id*='fix'], [class*='fix']"
        };

        for (String selector : selectors) {
            Elements sections = doc.select(selector);
            if (!sections.isEmpty()) {
                logger.debug("Found {} bug fix sections with selector: {}", sections.size(), selector);
                processBugFixSection(sections.first(), bugFixes);
                if (!bugFixes.isEmpty()) {
                    logger.info("Found {} bug fixes using selector: {}", bugFixes.size(), selector);
                    return;
                }
            }
        }
    }

    private void processBugFixSection(Element section, List<String> bugFixes) {
        Element current = section.nextElementSibling();
        int elementsChecked = 0;
        final int MAX_ELEMENTS = 20;

        logger.debug("Processing bug fix section starting from: {}", section.tagName());

        while (current != null && elementsChecked < MAX_ELEMENTS && !isNewSection(current)) {
            if (current.tagName().equals("ul")) {
                // Extract from unordered lists
                Elements listItems = current.select("li");
                for (Element li : listItems) {
                    String text = cleanText(li.text());
                    if (!text.isEmpty() && isBugFixText(text)) {
                        bugFixes.add(text);
                        logger.debug("Found bug fix from list: {}",
                                text.length() > 80 ? text.substring(0, 80) + "..." : text);
                    }
                }
            } else if (current.tagName().equals("ol")) {
                // Extract from ordered lists
                Elements listItems = current.select("li");
                for (Element li : listItems) {
                    String text = cleanText(li.text());
                    if (!text.isEmpty() && isBugFixText(text)) {
                        bugFixes.add(text);
                        logger.debug("Found bug fix from ordered list: {}",
                                text.length() > 80 ? text.substring(0, 80) + "..." : text);
                    }
                }
            } else {
                // Extract from paragraphs or other elements
                String text = cleanText(current.text());
                if (!text.isEmpty() && isBugFixText(text)) {
                    bugFixes.add(text);
                    logger.debug("Found bug fix from paragraph: {}",
                            text.length() > 80 ? text.substring(0, 80) + "..." : text);
                }
            }

            current = current.nextElementSibling();
            elementsChecked++;
        }

        logger.debug("Processed {} elements in bug fix section", elementsChecked);
    }

    private void extractFromTextMatches(Document doc, List<String> bugFixes) {
        logger.debug("Starting fallback text-based bug fix extraction...");

        // Look for elements containing bug fix keywords
        String[] keywords = {"Fixed", "fixed", "Bug", "bug", "Resolved", "resolved"};

        for (String keyword : keywords) {
            Elements elements = doc.getElementsContainingText(keyword);

            for (Element element : elements) {
                String text = cleanText(element.text());
                if (text.length() > 15 && isBugFixText(text)) {
                    // Avoid duplicates
                    boolean exists = bugFixes.stream()
                            .anyMatch(fix -> fix.equalsIgnoreCase(text) ||
                                    (text.length() > 50 && fix.contains(text.substring(0, 50))));
                    if (!exists) {
                        bugFixes.add(text);
                        logger.debug("Found bug fix from text match: {}",
                                text.length() > 80 ? text.substring(0, 80) + "..." : text);
                    }
                }
            }
        }

        // Limit to prevent spam
        if (bugFixes.size() > 15) {
            logger.info("Limiting bug fixes to 15 (found {})", bugFixes.size());
            bugFixes = bugFixes.subList(0, 15);
        }
    }

    private boolean isBugFixText(String text) {
        if (text == null || text.length() < 10) return false;

        String lowerText = text.toLowerCase();

        // Must contain bug fix keywords
        boolean hasBugKeyword = lowerText.contains("fixed") || lowerText.contains("bug") ||
                lowerText.contains("resolved") || lowerText.contains("corrected") ||
                lowerText.contains("addressed") || lowerText.contains("patched") ||
                lowerText.contains("repair");

        // Exclude false positives
        boolean isFalsePositive = lowerText.contains("champion") && !lowerText.contains("fixed") ||
                lowerText.contains("item") && !lowerText.contains("fixed") ||
                lowerText.length() > 500; // Too long, probably not a bug fix

        return hasBugKeyword && !isFalsePositive;
    }

    private boolean isNewSection(Element element) {
        if (element == null) return true;

        String tagName = element.tagName().toLowerCase();
        if (tagName.equals("h1") || tagName.equals("h2") || tagName.equals("h3")) {
            String text = element.text().toLowerCase();
            return text.contains("champion") || text.contains("item") ||
                    text.contains("upcoming") || text.contains("related") ||
                    text.contains("patch highlights") || text.contains("system");
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
}