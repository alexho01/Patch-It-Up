package com.hundefined.services;

import com.hundefined.services.ChampionExtractor;
import com.hundefined.services.ItemExtractor;
import com.hundefined.services.BugFixExtractor;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RiotApiService {
    private static final Logger logger = LoggerFactory.getLogger(RiotApiService.class);
    private static final String BASE_URL = "Enter Riot API Base URL Here";
    private static final String PATCH_NOTES_INDEX_URL = "Enter Patch Notes Index URL Here";

    private final OkHttpClient client;
    private final Gson gson;

    // Composition with extractors
    private final ChampionExtractor championExtractor;
    private final ItemExtractor itemExtractor;
    private final BugFixExtractor bugFixExtractor;

    public RiotApiService() {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
        this.gson = new Gson();

        // Initialize extractors
        this.championExtractor = new ChampionExtractor();
        this.itemExtractor = new ItemExtractor();
        this.bugFixExtractor = new BugFixExtractor();
    }

    /** Get the latest game version from Riot's Data Dragon API */
    public String getLatestVersion() {
        String url = BASE_URL + "ENTER API JSON URL";
        logger.debug("Fetching latest version from: {}", url);

        try {
            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("User-Agent", "LeagueNews-Bot/1.0")
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    String responseBody = response.body().string();
                    JsonArray versions = gson.fromJson(responseBody, JsonArray.class);

                    if (versions.size() > 0) {
                        String latestVersion = versions.get(0).getAsString();
                        logger.info("Latest League version: {}", latestVersion);
                        return latestVersion;
                    } else {
                        logger.warn("No versions found in response");
                    }
                } else {
                    logger.warn("Failed to fetch versions: {} - {}", response.code(), response.message());
                }
            }
        } catch (Exception e) {
            logger.error("Error fetching latest version", e);
        }
        return null;
    }

    /** Get current patch version from the actual patch notes website */
    public String getCurrentPatchVersion() {
        logger.info("Fetching current patch version from website...");

        try {
            Document doc = Jsoup.connect(PATCH_NOTES_INDEX_URL)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .timeout(30000)
                    .followRedirects(true)
                    .get();

            logger.debug("Successfully loaded patch notes index page");

            // Look for the most recent patch notes link with multiple selectors
            Elements links = doc.select("a[href*='patch-'], a[href*='game-updates'], a[href*='patch']");
            logger.debug("Found {} potential patch links", links.size());

            for (Element link : links) {
                String href = link.attr("href");
                String linkText = link.text();
                logger.debug("Checking link: {} - {}", href, linkText);

                // Extract version from URL like "/news/game-updates/patch-25-19-notes/"
                Pattern pattern = Pattern.compile("patch[\\-_](\\d+)[\\-_](\\d+)");
                Matcher matcher = pattern.matcher(href);
                if (matcher.find()) {
                    String version = matcher.group(1) + "." + matcher.group(2);
                    logger.info("Found patch version from website: {}", version);
                    return version;
                }

                // Also try to extract from link text
                pattern = Pattern.compile("patch\\s+(\\d+\\.\\d+)", Pattern.CASE_INSENSITIVE);
                matcher = pattern.matcher(linkText);
                if (matcher.find()) {
                    String version = matcher.group(1);
                    logger.info("Found patch version from link text: {}", version);
                    return version;
                }
            }

            logger.warn("No patch version found on website, falling back to API");
        } catch (IOException e) {
            logger.error("Error fetching current patch version from website", e);
        }

        // Fallback to Data Dragon API
        String ddVersion = getLatestVersion();
        if (ddVersion != null) {
            return extractPatchVersion(ddVersion);
        }

        return null;
    }

    /** Get all available versions */
    public List<String> getAllVersions() {
        String url = BASE_URL + "BASE JSON URL";
        List<String> versionList = new ArrayList<>();

        try {
            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("User-Agent", "LeagueNews-Bot/1.0")
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    String responseBody = response.body().string();
                    JsonArray versions = gson.fromJson(responseBody, JsonArray.class);

                    for (JsonElement version : versions) {
                        versionList.add(version.getAsString());
                    }
                }
            }
        } catch (IOException e) {
            logger.error("Error fetching all versions", e);
        }

        return versionList;
    }

    /** Fetch actual patch notes content using the dedicated extractors */
    public PatchContent fetchPatchContent(String patchVersion) {
        logger.info("Starting to fetch patch content for version: {}", patchVersion);

        String url = resolvePatchNotesUrl(patchVersion);
        if (url == null) {
            logger.error("Could not resolve URL for patch {}", patchVersion);
            return null;
        }

        logger.info("Fetching patch content from: {}", url);

        try {
            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .timeout(30000)
                    .followRedirects(true)
                    .get();

            logger.info("Successfully loaded patch notes page, document size: {} characters", doc.text().length());

            // Debug: Print some of the HTML structure
            logger.debug("Document title: {}", doc.title());
            logger.debug("Main headings found: {}", doc.select("h1, h2, h3").size());

            PatchContent content = new PatchContent();
            content.version = patchVersion;
            content.url = url;

            // Extract title with multiple fallbacks
            Element titleElement = doc.selectFirst("h1, .article-title, [class*='title']");
            content.title = (titleElement != null) ? cleanText(titleElement.text()) : "Patch " + patchVersion + " Notes";
            logger.debug("Extracted title: {}", content.title);

            // Extract overview
            extractOverview(doc, content);

            // Use the dedicated extractors for each content type
            logger.info("Starting extraction using specialized extractors...");

            // Extract champions using ChampionExtractor
            List<ChampionExtractor.ChampionChange> championChanges = championExtractor.extractChampionChanges(doc);
            content.championChanges = convertChampionChanges(championChanges);
            logger.info("Champion extraction completed: {} champions found", content.championChanges.size());

            // Extract items using ItemExtractor
            List<ItemExtractor.ItemChange> itemChanges = itemExtractor.extractItemChanges(doc);
            content.itemChanges = convertItemChanges(itemChanges);
            logger.info("Item extraction completed: {} items found", content.itemChanges.size());

            // Extract bug fixes using BugFixExtractor
            content.bugFixes = bugFixExtractor.extractBugFixes(doc);
            logger.info("Bug fix extraction completed: {} bug fixes found", content.bugFixes.size());

            // Extract system changes (kept in this service as it's more general)
            extractSystemChanges(doc, content);
            logger.info("System changes extraction completed: {} changes found", content.systemChanges.size());

            logger.info("Total extraction complete - Champions: {}, Items: {}, Bug Fixes: {}, System Changes: {}",
                    content.championChanges.size(), content.itemChanges.size(),
                    content.bugFixes.size(), content.systemChanges.size());

            return content;

        } catch (IOException e) {
            logger.error("Error fetching patch content for version {}", patchVersion, e);
            return null;
        }
    }

    /** Convert ChampionExtractor.ChampionChange to RiotApiService.ChampionChange */
    private List<ChampionChange> convertChampionChanges(List<ChampionExtractor.ChampionChange> extractorChanges) {
        List<ChampionChange> converted = new ArrayList<>();
        for (ChampionExtractor.ChampionChange extractorChange : extractorChanges) {
            ChampionChange change = new ChampionChange();
            change.name = extractorChange.name;
            change.changes = new ArrayList<>(extractorChange.changes);
            converted.add(change);
        }
        logger.debug("Converted {} champion changes from extractor", converted.size());
        return converted;
    }

    /** Convert ItemExtractor.ItemChange to RiotApiService.ItemChange */
    private List<ItemChange> convertItemChanges(List<ItemExtractor.ItemChange> extractorChanges) {
        List<ItemChange> converted = new ArrayList<>();
        for (ItemExtractor.ItemChange extractorChange : extractorChanges) {
            ItemChange change = new ItemChange();
            change.name = extractorChange.name;
            change.changes = new ArrayList<>(extractorChange.changes);
            converted.add(change);
        }
        logger.debug("Converted {} item changes from extractor", converted.size());
        return converted;
    }

    /** Extract overview from document */
    private void extractOverview(Document doc, PatchContent content) {
        String[] overviewSelectors = {
                "p:contains(Welcome to Patch)",
                ".article-intro p",
                ".intro-text",
                "p:first-of-type",
                "[class*='intro'] p"
        };

        for (String selector : overviewSelectors) {
            Elements elements = doc.select(selector);
            if (!elements.isEmpty()) {
                StringBuilder overview = new StringBuilder();
                for (int i = 0; i < Math.min(3, elements.size()); i++) {
                    String text = cleanText(elements.get(i).text());
                    if (text.length() > 20) {
                        overview.append(text).append("\n\n");
                    }
                }
                if (overview.length() > 0) {
                    content.overview = overview.toString().trim();
                    logger.debug("Extracted overview using selector: {}", selector);
                    return;
                }
            }
        }
        logger.debug("No overview found");
    }

    /** Extract system changes (general changes not specific to champions/items) */
    private void extractSystemChanges(Document doc, PatchContent content) {
        // Look for system/gameplay change sections
        Elements systemSections = doc.select(
                "h2:contains(System), h3:contains(System), " +
                        "h2:contains(Gameplay), h3:contains(Gameplay), " +
                        "h2:contains(Game Systems), h3:contains(Game Systems), " +
                        "h2:contains(Jungle), h3:contains(Jungle), " +
                        "h2:contains(Arena), h3:contains(Arena), " +
                        "h2:contains(ARAM), h3:contains(ARAM)"
        );

        for (Element section : systemSections) {
            logger.debug("Processing system section: {}", section.text());

            Element current = section.nextElementSibling();
            int elementsChecked = 0;

            while (current != null && elementsChecked < 20 && !isNewMajorSection(current)) {
                String text = cleanText(current.text());

                if (text.length() > 15 && !text.isEmpty()) {
                    // Check if it's a list item or paragraph with system changes
                    if (current.tagName().equals("ul") || current.tagName().equals("ol")) {
                        Elements listItems = current.select("li");
                        for (Element li : listItems) {
                            String itemText = cleanText(li.text());
                            if (itemText.length() > 15) {
                                content.systemChanges.add(itemText);
                            }
                        }
                    } else if (current.tagName().equals("p") || current.tagName().equals("div")) {
                        content.systemChanges.add(text);
                    }
                }

                current = current.nextElementSibling();
                elementsChecked++;
            }
        }

        logger.debug("Extracted {} system changes", content.systemChanges.size());
    }

    /** Check if element represents a new major section */
    private boolean isNewMajorSection(Element element) {
        if (element == null) return true;

        String tagName = element.tagName().toLowerCase();
        if (tagName.equals("h1") || tagName.equals("h2")) {
            String text = element.text().toLowerCase();
            return text.contains("champion") || text.contains("item") || text.contains("bug") ||
                    text.contains("upcoming") || text.contains("related") || text.contains("tft") ||
                    text.contains("teamfight tactics");
        }

        return false;
    }

    /** Improved URL resolution with multiple strategies */
    private String resolvePatchNotesUrl(String patchVersion) {
        logger.debug("Resolving patch notes URL for version: {}", patchVersion);

        // Strategy 1: Try direct URL construction
        String directUrl = String.format("Enter Patch Notes URL Pattern Here",
                patchVersion.replace(".", "-"));

        if (urlExists(directUrl)) {
            logger.info("Found patch notes using direct URL: {}", directUrl);
            return directUrl;
        }

        // Strategy 2: Search the patch notes index page
        try {
            Document doc = Jsoup.connect(PATCH_NOTES_INDEX_URL)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .timeout(30000)
                    .get();

            // Look for links containing the patch version
            Elements links = doc.select("a[href*='/news/game-updates/']");
            for (Element link : links) {
                String href = link.attr("href");
                String linkText = link.text().toLowerCase();

                // Check if link contains our patch version
                if (href.contains(patchVersion.replace(".", "-")) ||
                        linkText.contains(patchVersion) ||
                        linkText.contains("patch " + patchVersion)) {

                    String fullUrl = href.startsWith("http") ? href : "Enter Base Website URL Here" + href;
                    logger.info("Found patch notes via search: {}", fullUrl);
                    return fullUrl;
                }
            }

            // Strategy 3: Get the most recent patch notes (fallback)
            if (!links.isEmpty()) {
                String href = links.first().attr("href");
                String fallbackUrl = href.startsWith("http") ? href : "Enter Base Website URL Here" + href;
                logger.info("Using most recent patch notes as fallback: {}", fallbackUrl);
                return fallbackUrl;
            }

        } catch (IOException e) {
            logger.error("Error resolving patch notes URL for {}", patchVersion, e);
        }

        return null;
    }

    /** Check if a URL exists */
    private boolean urlExists(String url) {
        try {
            Request request = new Request.Builder()
                    .url(url)
                    .head() // Use HEAD request for efficiency
                    .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .build();

            try (Response response = client.newCall(request).execute()) {
                return response.isSuccessful();
            }
        } catch (IOException e) {
            return false;
        }
    }

    /** Get champion data from Riot API */
    public JsonObject getChampionData(String version) {
        String url = BASE_URL + "/cdn/" + version + "/ENTER DATA URL JSON";
        try {
            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("User-Agent", "LeagueNews-Bot/1.0")
                    .build();
            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    return gson.fromJson(response.body().string(), JsonObject.class);
                }
            }
        } catch (IOException e) {
            logger.error("Error fetching champion data for version {}", version, e);
        }
        return null;
    }

    /** Shutdown all resources */
    public void shutdown() {
        client.dispatcher().executorService().shutdown();
        client.connectionPool().evictAll();
        logger.info("RiotApiService shutdown completed");
    }

    // Helper methods
    private String cleanText(String text) {
        if (text == null) return "";
        return text.trim()
                .replaceAll("\\s+", " ")
                .replaceAll("[\\r\\n]+", " ")
                .trim();
    }

    private String extractPatchVersion(String fullVersion) {
        if (fullVersion == null) return null;

        Pattern pattern = Pattern.compile("(\\d+\\.\\d+)");
        Matcher matcher = pattern.matcher(fullVersion);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return fullVersion;
    }

    // Data Classes (kept the same for compatibility)
    public static class PatchData {
        public final String version, title, url, summary;
        public final LocalDateTime releaseDate;
        public PatchContent content;

        public PatchData(String version, String title, LocalDateTime releaseDate, String url, String summary) {
            this.version = version;
            this.title = title;
            this.releaseDate = releaseDate;
            this.url = url;
            this.summary = summary;
        }

        @Override
        public String toString() {
            return String.format("Patch %s: %s (%s)", version, title, releaseDate.toLocalDate());
        }
    }

    public static class PatchContent {
        public String version, title, url, overview;
        public List<ChampionChange> championChanges = new ArrayList<>();
        public List<ItemChange> itemChanges = new ArrayList<>();
        public List<String> systemChanges = new ArrayList<>();
        public List<String> bugFixes = new ArrayList<>();
    }

    public static class ChampionChange {
        public String name;
        public List<String> changes = new ArrayList<>();
    }

    public static class ItemChange {
        public String name;
        public List<String> changes = new ArrayList<>();
    }
}