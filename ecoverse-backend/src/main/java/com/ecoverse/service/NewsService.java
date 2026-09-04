package com.ecoverse.service;

import com.ecoverse.dto.news.NewsResponse;
import com.ecoverse.dto.news.NewsResponse.Article;
import com.ecoverse.util.InputSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * EcoVerse — News Service (Phase 4: Real Eco News).
 *
 * Features:
 * - GNews API for real environment/climate news by country + search
 * - 10+ RSS feeds from India, UK, US, global sources
 * - Eco tips engine — keyword-based actionable tips per article
 * - Sentiment detection — good/bad/neutral tagging
 * - Category + country + search filtering
 * - Deduplication, XSS prevention, URL validation
 * - 15-minute Caffeine cache
 * - Graceful fallback to RSS-only if GNews key missing
 */
@Service
public class NewsService {

    private static final Logger log = LoggerFactory.getLogger(NewsService.class);

    private final WebClient webClient;

    @Value("${rss2json.base-url:https://api.rss2json.com/v1/api.json}")
    private String rssBaseUrl;

    @Value("${gnews.api-key:}")
    private String gnewsApiKey;

    @Value("${gnews.base-url:https://gnews.io/api/v4}")
    private String gnewsBaseUrl;

    // ============================================================
    // RSS FEEDS — 15 sources across India, UK, US, Global
    // ============================================================

    private static final List<RssFeed> RSS_FEEDS = Arrays.asList(
            // India (8 feeds)
            new RssFeed("The Hindu", "https://www.thehindu.com/sci-tech/energy-environment/rssfeed.xml", "Environment"),
            new RssFeed("Down To Earth", "https://www.downtoearth.org.in/rss/news.xml", "Environment"),
            new RssFeed("Mongabay India", "https://india.mongabay.com/feed/", "Wildlife"),
            new RssFeed("Times of India", "https://timesofindia.indiatimes.com/rssfeeds/2647163.cms", "Environment"),
            new RssFeed("Hindustan Times", "https://www.hindustantimes.com/rss/environment/rssfeed.xml", "Environment"),
            new RssFeed("Indian Express", "https://indianexpress.com/section/environment/feed/", "Environment"),
            new RssFeed("NDTV", "https://www.ndtv.com/rss/Environment", "Environment"),
            new RssFeed("India Today", "https://www.indiatoday.in/rss/1206578", "Environment"),
            // UK
            new RssFeed("BBC", "https://feeds.bbci.co.uk/news/science_and_environment/rss.xml", "Environment"),
            new RssFeed("Guardian", "https://www.theguardian.com/environment/climate-crisis/rss.xml", "Climate"),
            // US
            new RssFeed("Reuters", "https://www.reuters.com/rssFeed/sustainabilityNews", "Sustainability"),
            new RssFeed("NYT Climate", "https://rss.nytimes.com/services/xml/rss/nyt/Climate.xml", "Climate"),
            // Global
            new RssFeed("Carbon Brief", "https://www.carbonbrief.org/feed/", "Climate"),
            new RssFeed("UN Environment", "https://www.unep.org/rss/news.xml", "Environment"),
            new RssFeed("Mongabay", "https://news.mongabay.com/feed/", "Wildlife")
    );

    // GNews category → search query mapping
    private static final Map<String, String> CATEGORY_QUERIES = new HashMap<>();
    static {
        CATEGORY_QUERIES.put("climate", "climate change global warming");
        CATEGORY_QUERIES.put("pollution", "air pollution water pollution toxic");
        CATEGORY_QUERIES.put("wildlife", "wildlife conservation biodiversity endangered");
        CATEGORY_QUERIES.put("renewable", "renewable energy solar wind clean energy");
        CATEGORY_QUERIES.put("technology", "green technology sustainable innovation electric");
        CATEGORY_QUERIES.put("environment", "environment ecology green sustainability");
    }

    // Country code → language mapping for GNews
    private static final Map<String, String> COUNTRY_LANG = new HashMap<>();
    static {
        COUNTRY_LANG.put("IN", "en");
        COUNTRY_LANG.put("US", "en");
        COUNTRY_LANG.put("GB", "en");
        COUNTRY_LANG.put("AU", "en");
        COUNTRY_LANG.put("CA", "en");
        COUNTRY_LANG.put("DE", "de");
        COUNTRY_LANG.put("FR", "fr");
        COUNTRY_LANG.put("JP", "ja");
        COUNTRY_LANG.put("BR", "pt");
        COUNTRY_LANG.put("CN", "zh");
    }

    // ============================================================
    // ECO TIPS ENGINE — keyword-based actionable tips
    // ============================================================

    private static final List<EcoTipRule> ECO_TIPS = Arrays.asList(
            new EcoTipRule("pollution,smog,toxic,emission,hazardous", "Reduce vehicle use on high-pollution days. Check AQI before going outdoors. Use public transport or carpool."),
            new EcoTipRule("climate,warming,carbon,greenhouse,emissions", "Calculate your carbon footprint and set reduction goals. Switch to renewable energy where possible."),
            new EcoTipRule("wildlife,endangered,extinction,biodiversity,species", "Avoid products with palm oil. Support local conservation efforts. Plant native trees."),
            new EcoTipRule("renewable,solar,wind,clean energy,green energy", "Consider rooftop solar — subsidies available in many states. Compare green energy providers."),
            new EcoTipRule("flood,flooding,drought,water scarcity", "Reduce water waste. Harvest rainwater for garden use. Fix leaking taps — every drop counts."),
            new EcoTipRule("heatwave,heat wave,extreme heat", "Stay hydrated, avoid outdoor exercise during peak hours. Check on elderly neighbors."),
            new EcoTipRule("plastic,ocean,waste,recycle,landfill", "Carry reusable bags and bottles. Segregate waste at home. Compost organic waste."),
            new EcoTipRule("deforestation,forest,jungle,tree", "Go paperless where possible. Support reforestation projects. Avoid products linked to deforestation."),
            new EcoTipRule("electric,ev,vehicle,tesla,hybrid", "Consider an EV for your next vehicle — lower running costs and zero tailpipe emissions."),
            new EcoTipRule("organic,farming,pesticide,agriculture", "Buy local and organic produce when possible. Start a small kitchen garden.")
    );

    // ============================================================
    // SENTIMENT DETECTION — keyword-based
    // ============================================================

    private static final String[] NEGATIVE_KEYWORDS = {
            "crisis", "disaster", "extinction", "toxic", "flood", "drought",
            "heatwave", "dying", "threat", "danger", "collapse", "destroy",
            "pollution", "destruction", "loss", "death", "catastrophe", "emergency",
            "worst", "record high", "record low", "alarming", "warn", "failed"
    };

    private static final String[] POSITIVE_KEYWORDS = {
            "recovery", "restore", "success", "protect", "save", "growth",
            "renewable", "clean", "milestone", "breakthrough", "improve",
            "reduce", "cut", "achieve", "progress", "innovation", "launch",
            "ban", "pledge", "commit", "invest", "plan", "agreement", "historic"
    };

    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]*>");
    private static final int MAX_PAGE_SIZE = 50;

    public NewsService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    /**
     * Get eco news — hybrid GNews API + RSS feeds.
     * Results cached for 15 minutes.
     */
    @Cacheable(value = "news", key = "#source + '-' + #category + '-' + #country + '-' + #city + '-' + #state + '-' + #q + '-' + #page + '-' + #size")
    @SuppressWarnings("unchecked")
    public NewsResponse getNews(String source, String category, String country, String city, String state, String q, int page, int size) {
        List<Article> allArticles = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        // 1. Try GNews API first (if API key available)
        if (gnewsApiKey != null && !gnewsApiKey.isBlank()) {
            try {
                List<Article> gnewsArticles = fetchGNews(category, country, city, state, q, page, size);
                for (Article a : gnewsArticles) {
                    String dedupKey = (a.getTitle() != null ? a.getTitle() : "") + "|" + a.getSource();
                    if (seen.add(dedupKey)) {
                        allArticles.add(a);
                    }
                }
            } catch (Exception e) {
                log.warn("GNews API failed, falling back to RSS: {}", e.getMessage());
            }
        }

        // 2. Always add RSS articles (supplementary, more sources)
        if (allArticles.size() < size) {
            // When searching, fetch ALL feeds (broadest pool to match against)
            // When browsing, use country-filtered feeds for relevance
            List<RssFeed> feeds = (q != null && !q.isBlank()) || (city != null && !city.isBlank())
                    ? RSS_FEEDS  // Search mode: all sources for maximum match potential
                    : filterFeeds(source, country);  // Browse mode: country-relevant sources
            for (RssFeed feed : feeds) {
                try {
                    List<Article> rssArticles = fetchRssFeed(feed);
                    for (Article a : rssArticles) {
                        String dedupKey = (a.getTitle() != null ? a.getTitle() : "") + "|" + a.getSource();
                        if (seen.add(dedupKey)) {
                            allArticles.add(a);
                        }
                    }
                } catch (Exception e) {
                    log.warn("RSS feed failed for {}: {}", feed.name, e.getMessage());
                }
            }
        }

        // 3. Apply category filter if specified
        if (category != null && !"all".equalsIgnoreCase(category) && !category.isEmpty()) {
            String catLower = category.toLowerCase();
            allArticles = allArticles.stream()
                    .filter(a -> a.getCategory() != null && a.getCategory().toLowerCase().contains(catLower)
                            || (a.getTitle() != null && a.getTitle().toLowerCase().contains(catLower))
                            || (a.getSummary() != null && a.getSummary().toLowerCase().contains(catLower)))
                    .collect(Collectors.toList());
        }

        // 4. Apply search query filter — combine q + city + state for location-aware search
        // Match ANY keyword, best matches first
        String combinedSearch = (q != null ? q : "");
        if (city != null && !city.isBlank()) combinedSearch = city + " " + combinedSearch;
        if (state != null && !state.isBlank() && !state.equalsIgnoreCase(city)) combinedSearch = state + " " + combinedSearch;

        if (!combinedSearch.isBlank()) {
            List<String> terms = new ArrayList<>();
            for (String t : combinedSearch.toLowerCase().split("[^a-z0-9]+")) {
                if (t.length() >= 2) terms.add(t);  // Lower threshold to 2 chars for city names like "Goa"
            }
            if (!terms.isEmpty()) {
                allArticles = allArticles.stream()
                        .filter(a -> {
                            String hay = ((a.getTitle() != null ? a.getTitle() : "") + " "
                                    + (a.getSummary() != null ? a.getSummary() : "")).toLowerCase();
                            for (String t : terms) {
                                if (hay.contains(t)) return true;
                            }
                            return false;
                        })
                        .sorted((a1, a2) -> relevanceScore(a2, terms) - relevanceScore(a1, terms))
                        .collect(Collectors.toList());
            }
        }

        // 5. Enrich with eco tips + sentiment
        for (Article a : allArticles) {
            String text = (a.getTitle() != null ? a.getTitle() : "") + " " + (a.getSummary() != null ? a.getSummary() : "");
            a.setEcoTip(generateEcoTip(text));
            a.setSentiment(detectSentiment(text));
        }

        // 6. Sort by date (newest first)
        allArticles.sort((a1, a2) -> {
            try {
                LocalDateTime d1 = parseDate(a1.getDate());
                LocalDateTime d2 = parseDate(a2.getDate());
                if (d1 == null && d2 == null) return 0;
                if (d1 == null) return 1;
                if (d2 == null) return -1;
                return d2.compareTo(d1);
            } catch (Exception e) {
                return 0;
            }
        });

        // 7. Pagination
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int totalArticles = allArticles.size();
        int totalPages = (int) Math.ceil((double) totalArticles / safeSize);
        int start = page * safeSize;
        int end = Math.min(start + safeSize, totalArticles);

        List<Article> pageContent = start < totalArticles
                ? allArticles.subList(start, end)
                : Collections.emptyList();

        return NewsResponse.builder()
                .articles(pageContent)
                .totalArticles(totalArticles)
                .page(page)
                .size(safeSize)
                .totalPages(totalPages)
                .build();
    }

    // ============================================================
    // GNEWS API — real news by country + search
    // ============================================================

    @SuppressWarnings("unchecked")
    private List<Article> fetchGNews(String category, String country, String city, String state, String q, int page, int size) {
        List<Article> articles = new ArrayList<>();

        String query = "environment OR climate OR pollution OR wildlife OR renewable OR sustainability";
        if (category != null && !"all".equalsIgnoreCase(category) && CATEGORY_QUERIES.containsKey(category.toLowerCase())) {
            query = CATEGORY_QUERIES.get(category.toLowerCase());
        }
        // Add city/state to search query for location-relevant results
        if (city != null && !city.isBlank()) {
            query = city + " " + query;
        }
        if (state != null && !state.isBlank() && !state.equalsIgnoreCase(city)) {
            query = state + " " + query;
        }
        if (q != null && !q.isBlank()) {
            query = q + " " + query;
        }

        String lang = "en";
        if (country != null && !country.isBlank() && !"all".equalsIgnoreCase(country)) {
            lang = COUNTRY_LANG.getOrDefault(country.toUpperCase(), "en");
        }

        final String finalQuery = query;
        final String finalLang = lang;

        try {
            Map<String, Object> response = webClient.get()
                    .uri(uriBuilder -> {
                        var builder = uriBuilder
                                .scheme("https")
                                .host("gnews.io")
                                .path("/api/v4/search")
                                .queryParam("q", finalQuery)
                                .queryParam("lang", finalLang)
                                .queryParam("max", Math.min(size, 10))
                                .queryParam("apikey", gnewsApiKey);
                        if (country != null && !country.isBlank() && !"all".equalsIgnoreCase(country)) {
                            builder.queryParam("country", country.toLowerCase());
                        }
                        return builder.build();
                    })
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response == null) return articles;

            List<Map<String, Object>> items = (List<Map<String, Object>>) response.get("articles");
            if (items == null) return articles;

            for (Map<String, Object> item : items) {
                try {
                    String title = InputSanitizer.sanitize((String) item.get("title"), 200);
                    String description = (String) item.get("description");
                    String summary = InputSanitizer.sanitize(stripHtml(description), 200);
                    if (summary != null && summary.length() > 200) {
                        summary = summary.substring(0, 197) + "...";
                    }
                    String link = validateUrl((String) item.get("url"));
                    String pubDate = (String) item.get("publishedAt");
                    String imageUrl = validateUrl((String) item.get("image"));

                    // Source
                    String sourceName = "GNews";
                    Object sourceObj = item.get("source");
                    if (sourceObj instanceof Map) {
                        sourceName = (String) ((Map<String, Object>) sourceObj).getOrDefault("name", "GNews");
                    }

                    // Detect category from content
                    String detectedCategory = detectCategory(title + " " + description);

                    articles.add(Article.builder()
                            .title(title)
                            .summary(summary)
                            .link(link)
                            .date(pubDate)
                            .source(sourceName)
                            .category(detectedCategory)
                            .image(imageUrl)
                            .build());
                } catch (Exception e) {
                    log.debug("Skipping malformed GNews item: {}", e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("GNews API error: {}", e.getMessage());
        }

        return articles;
    }

    // ============================================================
    // RSS FEEDS — expanded 10+ sources
    // ============================================================

    @SuppressWarnings("unchecked")
    private List<Article> fetchRssFeed(RssFeed feed) {
        List<Article> articles = new ArrayList<>();

        // Try rss2json API first (handles complex RSS formats)
        try {
            Map<String, Object> response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .scheme("https")
                            .host("api.rss2json.com")
                            .path("/v1/api.json")
                            .queryParam("rss_url", feed.url)
                            .build())
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response != null) {
                String status = (String) response.get("status");
                if ("ok".equalsIgnoreCase(status)) {
                    List<Map<String, Object>> items = (List<Map<String, Object>>) response.get("items");
                    if (items != null) {
                        for (Map<String, Object> item : items) {
                            try {
                                Article a = parseRssItem(item, feed);
                                if (a != null) articles.add(a);
                            } catch (Exception e) {
                                log.debug("Skipping malformed RSS item in {}: {}", feed.name, e.getMessage());
                            }
                        }
                        if (!articles.isEmpty()) return articles;
                    }
                }
            }
        } catch (Exception e) {
            log.debug("rss2json failed for {}, trying direct XML parse: {}", feed.name, e.getMessage());
        }

        // Fallback: parse RSS XML directly
        try {
            articles = parseRssXmlDirectly(feed);
        } catch (Exception e) {
            log.warn("RSS fetch failed for {}: {}", feed.name, e.getMessage());
        }

        return articles;
    }

    /** Parse a single RSS item from rss2json API response */
    private Article parseRssItem(Map<String, Object> item, RssFeed feed) {
        String title = InputSanitizer.sanitize((String) item.get("title"), 200);
        if (title == null || title.isBlank()) return null;
        String description = (String) item.get("description");
        String summary = InputSanitizer.sanitize(stripHtml(description), 200);
        if (summary != null && summary.length() > 200) {
            summary = summary.substring(0, 197) + "...";
        }
        String link = validateUrl((String) item.get("link"));
        String pubDate = (String) item.get("pubDate");

        String imageUrl = null;
        Object enclosure = item.get("enclosure");
        if (enclosure instanceof Map) {
            imageUrl = validateUrl((String) ((Map<String, Object>) enclosure).get("link"));
        }
        if (imageUrl == null) {
            imageUrl = validateUrl((String) item.get("thumbnail"));
        }

        String detectedCategory = detectCategory(title + " " + description);

        return Article.builder()
                .title(title)
                .summary(summary)
                .link(link)
                .date(pubDate)
                .source(feed.name)
                .category(detectedCategory != null ? detectedCategory : feed.category)
                .image(imageUrl)
                .build();
    }

    /** Direct RSS XML parsing — fallback when rss2json fails */
    private List<Article> parseRssXmlDirectly(RssFeed feed) {
        List<Article> articles = new ArrayList<>();
        try {
            String xml = webClient.get()
                    .uri(feed.url)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (xml == null || xml.isBlank()) return articles;

            javax.xml.parsers.DocumentBuilderFactory factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            javax.xml.parsers.DocumentBuilder builder = factory.newDocumentBuilder();
            org.w3c.dom.Document doc = builder.parse(new org.xml.sax.InputSource(new java.io.StringReader(xml)));

            // RSS 2.0: <rss><channel><item>
            org.w3c.dom.NodeList items = doc.getElementsByTagName("item");
            for (int i = 0; i < items.getLength() && i < 20; i++) {
                org.w3c.dom.Element item = (org.w3c.dom.Element) items.item(i);
                String title = getTextContent(item, "title");
                if (title == null || title.isBlank()) continue;
                title = InputSanitizer.sanitize(title, 200);

                String description = getTextContent(item, "description");
                String summary = InputSanitizer.sanitize(stripHtml(description), 200);
                if (summary != null && summary.length() > 200) {
                    summary = summary.substring(0, 197) + "...";
                }

                String link = validateUrl(getTextContent(item, "link"));
                String pubDate = getTextContent(item, "pubDate");

                // Try to get image from media:content or enclosure
                String imageUrl = null;
                org.w3c.dom.NodeList enclosures = item.getElementsByTagName("enclosure");
                if (enclosures.getLength() > 0) {
                    org.w3c.dom.Element enc = (org.w3c.dom.Element) enclosures.item(0);
                    String type = enc.getAttribute("type");
                    if (type != null && type.startsWith("image")) {
                        imageUrl = validateUrl(enc.getAttribute("url"));
                    }
                }
                if (imageUrl == null) {
                    org.w3c.dom.NodeList mediaContent = item.getElementsByTagNameNS("*", "content");
                    if (mediaContent.getLength() > 0) {
                        org.w3c.dom.Element mc = (org.w3c.dom.Element) mediaContent.item(0);
                        imageUrl = validateUrl(mc.getAttribute("url"));
                    }
                }

                String detectedCategory = detectCategory(title + " " + description);

                articles.add(Article.builder()
                        .title(title)
                        .summary(summary)
                        .link(link)
                        .date(pubDate)
                        .source(feed.name)
                        .category(detectedCategory != null ? detectedCategory : feed.category)
                        .image(imageUrl)
                        .build());
            }
        } catch (Exception e) {
            log.debug("Direct RSS parse failed for {}: {}", feed.name, e.getMessage());
        }
        return articles;
    }

    private String getTextContent(org.w3c.dom.Element parent, String tagName) {
        org.w3c.dom.NodeList nodes = parent.getElementsByTagName(tagName);
        if (nodes.getLength() > 0) {
            String text = nodes.item(0).getTextContent();
            return text != null ? text.trim() : null;
        }
        return null;
    }

    // ============================================================
    // CATEGORY DETECTION from article text
    // ============================================================

    private String detectCategory(String text) {
        if (text == null) return "Environment";
        String lower = text.toLowerCase();
        if (lower.contains("climate") || lower.contains("warming") || lower.contains("carbon") || lower.contains("greenhouse"))
            return "Climate";
        if (lower.contains("pollution") || lower.contains("smog") || lower.contains("toxic") || lower.contains("emission"))
            return "Pollution";
        if (lower.contains("wildlife") || lower.contains("species") || lower.contains("endangered") || lower.contains("biodiversity") || lower.contains("animal"))
            return "Wildlife";
        if (lower.contains("renewable") || lower.contains("solar") || lower.contains("wind energy") || lower.contains("clean energy"))
            return "Renewable";
        if (lower.contains("technology") || lower.contains("electric") || lower.contains("innovation") || lower.contains("green tech"))
            return "Technology";
        return "Environment";
    }

    // ============================================================
    // ECO TIPS ENGINE
    // ============================================================

    private String generateEcoTip(String text) {
        if (text == null) return null;
        String lower = text.toLowerCase();
        for (EcoTipRule rule : ECO_TIPS) {
            for (String keyword : rule.keywords.split(",")) {
                if (lower.contains(keyword.trim())) {
                    return rule.tip;
                }
            }
        }
        // Default eco tip
        return "Track your daily carbon footprint on EcoVerse and find ways to reduce it.";
    }

    // ============================================================
    // SENTIMENT DETECTION
    // ============================================================

    private String detectSentiment(String text) {
        if (text == null) return "neutral";
        String lower = text.toLowerCase();
        int posScore = 0, negScore = 0;
        for (String kw : POSITIVE_KEYWORDS) {
            if (lower.contains(kw)) posScore++;
        }
        for (String kw : NEGATIVE_KEYWORDS) {
            if (lower.contains(kw)) negScore++;
        }
        if (negScore > posScore) return "negative";
        if (posScore > negScore) return "positive";
        return "neutral";
    }

    /** Count how many search terms appear in an article — used to rank search results */
    private int relevanceScore(Article a, List<String> terms) {
        String hay = ((a.getTitle() != null ? a.getTitle() : "") + " "
                + (a.getSummary() != null ? a.getSummary() : "")).toLowerCase();
        int score = 0;
        for (String t : terms) {
            if (hay.contains(t)) score++;
        }
        return score;
    }

    // ============================================================
    // HELPERS
    // ============================================================

    private List<RssFeed> filterFeeds(String source, String country) {
        List<RssFeed> feeds = RSS_FEEDS;

        // Prioritize local + global feeds for the selected country,
        // but always include major international feeds as supplements
        if (country != null && !"all".equalsIgnoreCase(country) && !"world".equalsIgnoreCase(country)) {
            String cc = country.toUpperCase();
            feeds = feeds.stream()
                    .filter(f -> {
                        String n = f.name.toLowerCase();
                        // Global feeds always included
                        if (n.contains("carbon brief") || n.contains("un environment"))
                            return true;
                        // Major international feeds (BBC, Guardian, Reuters) always included for broad coverage
                        if (n.contains("bbc") || n.contains("guardian") || n.contains("reuters") || n.contains("nyt"))
                            return true;
                        // Country-specific feeds
                        switch (cc) {
                            case "IN":
                                return n.contains("hindu") || n.contains("down to earth") || n.contains("mongabay india")
                                        || n.contains("times of india") || n.contains("hindustan times")
                                        || n.contains("indian express") || n.contains("ndtv") || n.contains("india today");
                            case "US":
                                return true; // US users see all feeds
                            case "GB":
                                return true; // GB users see all feeds
                            default:
                                return true; // All other countries get everything
                        }
                    })
                    .collect(Collectors.toList());
        }

        // Then filter by source name if specified
        if (source == null || "all".equalsIgnoreCase(source) || source.isEmpty()) {
            return feeds;
        }
        String sourceLower = source.toLowerCase();
        return feeds.stream()
                .filter(f -> f.name.toLowerCase().contains(sourceLower))
                .collect(Collectors.toList());
    }

    private String stripHtml(String html) {
        if (html == null) return null;
        return HTML_TAG_PATTERN.matcher(html).replaceAll("").trim();
    }

    private String validateUrl(String url) {
        if (url == null || url.isBlank()) return null;
        String trimmed = url.trim();
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return trimmed;
        return null;
    }

    private LocalDateTime parseDate(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) return null;
        try {
            return ZonedDateTime.parse(dateStr, DateTimeFormatter.RFC_1123_DATE_TIME).toLocalDateTime();
        } catch (Exception e1) {
            try {
                return LocalDateTime.parse(dateStr, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            } catch (Exception e2) {
                try {
                    return LocalDateTime.parse(dateStr, DateTimeFormatter.ISO_ZONED_DATE_TIME);
                } catch (Exception e3) {
                    return null;
                }
            }
        }
    }

    // ============================================================
    // INNER CLASSES
    // ============================================================

    private static class RssFeed {
        final String name;
        final String url;
        final String category;
        RssFeed(String name, String url, String category) {
            this.name = name;
            this.url = url;
            this.category = category;
        }
    }

    private static class EcoTipRule {
        final String keywords;
        final String tip;
        EcoTipRule(String keywords, String tip) {
            this.keywords = keywords;
            this.tip = tip;
        }
    }
}
